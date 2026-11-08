package com.fcplus.forocoches

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView

/**
 * El reproductor de una playlist de Música: arriba de la pantalla, a todo el ancho y 16:9
 * (`res/layout/reproductor_musica.xml`, que se mete en [hueco]). Qué suena después lo decide
 * [ColaMusica]; aquí solo se monta y se quita lo que toca.
 *
 * - **YouTube** suena en un [EmbedView] en modo Música ([EmbedView.deMusica]: arranca solo y
 *   llena la caja). Al acabar un vídeo pasa SOLO a la siguiente (aviso de "terminado", A8) y,
 *   si ya ha hablado por postMessage, carga la siguiente en el MISMO reproductor
 *   ([EmbedView.cargarVideo], medido en el spec): sin un WebView por canción.
 * - **Spotify** solo deja 30 s dentro de la app: su reproductor oficial y "Escuchar entera en
 *   Spotify". La cola se para ahí; la siguiente, con el botón de la barra o tocándola.
 * - **No incrustable, borrada o privada** (101/150/100, o el "auth" de `videoData.errorCode`):
 *   se apunta en [fallidas], se salta, y la tarjeta ofrece abrirla fuera. Los demás errores de
 *   YouTube solo la saltan esa vez (ver [EstadoYoutube.noSeOyeAqui]).
 *
 * Reglas que manda el spec, y por qué está hecho así:
 * - **YouTube a la vista y de más de 200×200 px mientras suena.** La caja va fuera de la lista
 *   (no se desplaza) y nada la tapa; un WebView tapado además lo frena Chromium (gotcha).
 * - **Nada suena fuera de esta pantalla.** Quien la quita (MusicaController: ocultar, pausar,
 *   atrás) llama a [parar], y es el ÚNICO sitio que lo apaga (gotcha "Cinco sitios bajaban
 *   isThreadVisible y solo uno apagaba el reproductor").
 * - **El EmbedView con avisos solo carga el iframe de YouTube**: su escucha de postMessage no
 *   mira el origen (A8), así que Spotify va en OTRO EmbedView, sin [EmbedView.alTerminar] ni
 *   [EmbedView.alFallar].
 *
 * @param abrirFuera abre una URL fuera de la app (regla de oro): "Escuchar entera en Spotify".
 */
class ReproductorMusica(
    private val hueco: FrameLayout,
    private val abrirFuera: (String) -> Unit
) {
    /** La canción cargada ha cambiado: su posición en la lista, o null si ya no suena nada. */
    var alCambiar: ((indice: Int?) -> Unit)? = null

    private val noSeOyen = LinkedHashSet<String>()

    /** Claves que YouTube no deja escuchar aquí (en esta sesión): se saltan y se ofrecen fuera. */
    val fallidas: Set<String> get() = noSeOyen

    private var cola: List<Cancion> = emptyList()

    /** La canción cargada; parado, la última que lo estuvo (para "Seguir con…"). -1: ninguna. */
    private var actual = -1

    /** ¿Hay algo montado (YouTube o el reproductor de Spotify)? */
    val enMarcha: Boolean get() = youtube != null || spotify != null

    /** Se llegó al final: el reposo ofrece volver a empezar. */
    private var acabada = false

    private var youtube: EmbedView? = null
    private var spotify: EmbedView? = null

    private val raiz: View =
        LayoutInflater.from(hueco.context).inflate(R.layout.reproductor_musica, hueco, false)
    private val caja: FrameLayout = raiz.findViewById(R.id.rep_caja)
    private val reposo: View = raiz.findViewById(R.id.rep_reposo)
    private val reposoIcono: View = raiz.findViewById(R.id.rep_reposo_icono)
    private val reposoTexto: TextView = raiz.findViewById(R.id.rep_reposo_texto)
    private val zonaSpotify: View = raiz.findViewById(R.id.rep_spotify)
    private val huecoSpotify: FrameLayout = raiz.findViewById(R.id.rep_spotify_hueco)
    private val barra: View = raiz.findViewById(R.id.rep_barra)
    private val titulo: TextView = raiz.findViewById(R.id.rep_titulo)
    private val artista: TextView = raiz.findViewById(R.id.rep_artista)
    private val anterior: ImageView = raiz.findViewById(R.id.rep_anterior)
    private val siguiente: ImageView = raiz.findViewById(R.id.rep_siguiente)

    init {
        hueco.removeAllViews()
        hueco.addView(raiz)
        reposo.setOnClickListener { seguir() }
        anterior.setOnClickListener { irA(ColaMusica.anterior(fuentes(), actual, saltar())) }
        siguiente.setOnClickListener { irA(ColaMusica.siguiente(fuentes(), actual, saltar())) }
        raiz.findViewById<View>(R.id.rep_spotify_fuera).setOnClickListener {
            cola.getOrNull(actual)?.let { abrirFuera(it.ref.url()) }
        }
        pintar()
    }

    /** Empieza en [indice] (o en la primera que se pueda oír a partir de ahí). */
    fun reproducir(canciones: List<Cancion>, indice: Int) {
        cola = canciones
        acabada = false
        irA(ColaMusica.siguiente(fuentes(), indice - 1, saltar()))
    }

    /**
     * Otra playlist en pantalla: lo que sonara se para y la cola nueva empieza de cero.
     */
    fun preparar(canciones: List<Cancion>) {
        parar()
        cola = canciones
        actual = -1
        acabada = false
        pintar()
    }

    /**
     * Calla y quita lo que suene. La cola y la posición se quedan, para ofrecer "Seguir con…".
     * Barato si no hay nada: MainActivity llama a ocultar (y este) en cada cambio de panel.
     */
    fun parar() {
        if (!enMarcha) return
        soltar(youtube)
        youtube = null
        soltar(spotify)
        spotify = null
        pintar()
        alCambiar?.invoke(null)
    }

    /**
     * La lista ha cambiado (reordenar, quitar, renombrar): la cola pasa a ser la nueva y lo que
     * suena sigue sonando. Si lo que se quita es justo lo que suena, entra la que ocupa su sitio.
     */
    fun actualizar(canciones: List<Cancion>) {
        val clave = cola.getOrNull(actual)?.ref?.clave
        val antes = actual
        cola = canciones
        when (val r = ColaMusica.reubicar(clave, antes, cola.map { it.ref.clave }, fuentes(), saltar(), enMarcha)) {
            is Reubicar.Sigue -> {
                actual = r.indice
                pintar()
                if (enMarcha) alCambiar?.invoke(actual)
            }
            is Reubicar.Sustituir -> irA(r.paso)
            is Reubicar.Parado -> {
                actual = r.indice
                pintar()
            }
        }
    }

    private fun fuentes() = cola.map { it.ref.fuente }

    private fun saltar(): Set<Int> = cola.indices.filter { cola[it].ref.clave in noSeOyen }.toSet()

    private fun irA(paso: Paso) {
        when (paso) {
            is Paso.Reproducir -> {
                actual = paso.indice
                ponerYoutube(cola[paso.indice])
            }
            is Paso.ParadaSpotify -> {
                actual = paso.indice
                ponerSpotify(cola[paso.indice])
            }
            Paso.Fin -> {
                // Se acabó (o lo que queda no se puede oír): en reposo, "volver a empezar".
                parar()
                actual = -1
                acabada = true
                pintar()
            }
        }
    }

    private fun ponerYoutube(c: Cancion) {
        soltar(spotify)
        spotify = null
        val yt = youtube
        if (yt != null && yt.respondio) {
            yt.cargarVideo(c.ref.id)
        } else {
            // Sin reproductor, o con uno que aún no escucha órdenes (se perderían): se monta.
            val e = yt ?: EmbedView(hueco.context).also { nuevo ->
                nuevo.deMusica = true
                nuevo.alTerminar = { if (youtube === nuevo) irA(ColaMusica.siguiente(fuentes(), actual, saltar())) }
                nuevo.alFallar = { codigo, video -> if (youtube === nuevo) fallo(codigo, video) }
                caja.addView(nuevo, FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT
                ))
                youtube = nuevo
            }
            e.bind(EmbedSpec("youtube", c.ref.url(), c.ref.id))
        }
        pintar()
        alCambiar?.invoke(actual)
    }

    private fun ponerSpotify(c: Cancion) {
        soltar(youtube)
        youtube = null
        soltar(spotify)
        // Sin avisos enganchados: ver el KDoc de la clase.
        val e = EmbedView(hueco.context)
        huecoSpotify.addView(e, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT
        ))
        e.bind(EmbedSpec("spotify", c.ref.url(), c.ref.id))
        spotify = e
        pintar()
        alCambiar?.invoke(actual)
    }

    /**
     * YouTube no puede con el vídeo [video]. `onFallo(0)` y compañía NO son errores (A8): solo
     * los códigos de la API. Un aviso que llega tarde, de un vídeo que ya no es el cargado, no
     * toca nada: si no, marcaría y saltaría la canción equivocada. Lo que no va a sonar aquí
     * nunca ([EstadoYoutube.noSeOyeAqui]) se marca para la sesión; lo demás solo se salta.
     */
    private fun fallo(codigo: Int, video: String) {
        if (!EstadoYoutube.esError(codigo)) return
        val c = cola.getOrNull(actual) ?: return
        if (c.ref.fuente != FuenteMusica.YOUTUBE || c.ref.id != video) return
        if (EstadoYoutube.noSeOyeAqui(codigo)) noSeOyen.add(c.ref.clave)
        irA(ColaMusica.siguiente(fuentes(), actual, saltar()))
    }

    /** El reposo: seguir por donde iba, o desde el principio. */
    private fun seguir() {
        ColaMusica.seguirDesde(cola.size, actual, acabada)?.let { reproducir(cola, it) }
    }

    /**
     * Quita un reproductor en el orden que exige Android (gotcha "Destruir un WebView antes de
     * sacarlo de la pantalla lo rompe"): [EmbedView.release] saca su WebView de la vista y
     * DESPUÉS lo destruye; aquí no corre dentro de un aviso del propio WebView (los avisos
     * llegan con `post`). Se suelta antes de quitar el EmbedView a propósito: con él ya fuera de
     * la ventana, su carga diferida (300 ms) no se podría cancelar y montaría un WebView suelto.
     */
    private fun soltar(e: EmbedView?) {
        e ?: return
        e.alTerminar = null
        e.alFallar = null
        e.release()
        (e.parent as? ViewGroup)?.removeView(e)
    }

    private fun pintar() {
        val c = cola.getOrNull(actual)
        val conSpotify = spotify != null
        caja.visibility = if (conSpotify) View.GONE else View.VISIBLE
        zonaSpotify.visibility = if (conSpotify) View.VISIBLE else View.GONE
        reposo.visibility = if (youtube == null) View.VISIBLE else View.GONE
        reposo.isEnabled = cola.isNotEmpty()
        reposoIcono.visibility = if (cola.isEmpty()) View.GONE else View.VISIBLE
        reposoTexto.text = when {
            cola.isEmpty() -> "Esta playlist aún no tiene canciones"
            acabada -> "Se acabó la playlist. Toca para volver a empezar"
            c != null -> "Seguir con «${c.titulo}»"
            else -> "Toca para escuchar la playlist"
        }
        reposo.contentDescription = reposoTexto.text
        barra.visibility = if (enMarcha && c != null) View.VISIBLE else View.GONE
        if (c != null) {
            titulo.text = c.titulo
            artista.text = c.artista.orEmpty()
            artista.visibility = if (c.artista.isNullOrBlank()) View.GONE else View.VISIBLE
        }
        activar(anterior, ColaMusica.anterior(fuentes(), actual, saltar()) != Paso.Fin)
        activar(siguiente, ColaMusica.siguiente(fuentes(), actual, saltar()) != Paso.Fin)
    }

    private fun activar(v: View, si: Boolean) {
        v.isEnabled = si
        v.alpha = if (si) 1f else 0.3f
    }

    companion object {
        /**
         * Alto de la caja del vídeo: 16:9 del ancho. En horizontal eso no cabe: como mucho el
         * 55 % del alto de la pantalla y, si el padre dice cuánto hay ([disponible]), lo que
         * quede tras [reserva] (la barra y una canción de la lista; sin eso la lista se quedaba
         * en 0 px). YouTube pone bandas a los lados. Pero el vídeo no baja de [minimo] mientras
         * quepa: antes que un vídeo invisible sonando, una lista corta.
         */
        fun altoCaja(ancho: Int, altoPantalla: Int, disponible: Int?, reserva: Int, minimo: Int): Int {
            var alto = minOf(ancho * 9 / 16, (altoPantalla * 0.55f).toInt())
            if (disponible != null) {
                alto = minOf(alto, disponible - reserva)
                alto = maxOf(alto, minOf(minimo, disponible))
            }
            return alto.coerceAtLeast(0)
        }
    }
}

/** La caja del vídeo de [ReproductorMusica]: mide su alto con [ReproductorMusica.altoCaja]. */
class CajaReproductor @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {

    override fun onMeasure(anchoSpec: Int, altoSpec: Int) {
        val ancho = MeasureSpec.getSize(anchoSpec)
        // Lo que deja el panel (AT_MOST/EXACTLY); sin límite, nada que recortar.
        val disponible = if (MeasureSpec.getMode(altoSpec) == MeasureSpec.UNSPECIFIED) null
            else MeasureSpec.getSize(altoSpec)
        val dp = resources.displayMetrics.density
        val alto = ReproductorMusica.altoCaja(
            ancho, resources.displayMetrics.heightPixels, disponible,
            reserva = ((BARRA_DP + FILA_DP) * dp).toInt(), minimo = (MINIMO_DP * dp).toInt()
        )
        super.onMeasure(
            MeasureSpec.makeMeasureSpec(ancho, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(alto, MeasureSpec.EXACTLY)
        )
    }

    private companion object {
        /** La barra de lo que suena (rep_barra, minHeight). */
        const val BARRA_DP = 56
        /** Una canción de la lista (item_cancion, minHeight). */
        const val FILA_DP = 68
        const val MINIMO_DP = 90
    }
}
