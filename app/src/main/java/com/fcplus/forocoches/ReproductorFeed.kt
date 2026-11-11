package com.fcplus.forocoches

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.net.URLEncoder

/** De dónde suena una publicación del feed. Lo decide [ReproductorFeed.fuente]. */
sealed class FuenteFeed {
    data class Spotify(val id: String) : FuenteFeed()
    data class Youtube(val id: String) : FuenteFeed()
    data class Deezer(val id: Long) : FuenteFeed()
    object Ninguna : FuenteFeed()
}

/** Lo mínimo de un [MediaPlayer] que usa el reproductor: así los tests no necesitan audio real. */
interface AudioFragmento {
    fun preparar(url: String, alListo: () -> Unit, alAcabar: () -> Unit, alFallar: () -> Unit)
    fun reanudar()
    fun pausar()
    fun sonando(): Boolean
    fun posicion(): Int
    fun duracion(): Int
    fun ir(ms: Int)
    fun soltar()
}

/** El de verdad: `USAGE_MEDIA` y `prepareAsync` (la URL es de la red; nunca bloquear el hilo principal). */
class AudioMediaPlayer : AudioFragmento {
    private var mp: MediaPlayer? = MediaPlayer()
    private var listo = false

    override fun preparar(url: String, alListo: () -> Unit, alAcabar: () -> Unit, alFallar: () -> Unit) {
        val m = mp ?: return
        m.setAudioAttributes(ATRIBUTOS)
        m.setOnPreparedListener { listo = true; it.start(); alListo() }
        m.setOnCompletionListener { alAcabar() }
        m.setOnErrorListener { _, _, _ -> alFallar(); true }
        try {
            m.setDataSource(url)
            m.prepareAsync()
        } catch (e: Exception) {
            alFallar()
        }
    }

    private inline fun <T> seguro(otro: T, f: MediaPlayer.() -> T): T =
        if (!listo) otro else try { mp?.f() ?: otro } catch (_: IllegalStateException) { otro }

    override fun reanudar() = seguro(Unit) { start() }
    override fun pausar() = seguro(Unit) { pause() }
    override fun sonando() = seguro(false) { isPlaying }
    override fun posicion() = seguro(0) { currentPosition }
    override fun duracion() = seguro(0) { duration }
    override fun ir(ms: Int) = seguro(Unit) { seekTo(ms) }
    override fun soltar() {
        val m = mp ?: return
        mp = null
        listo = false
        m.setOnPreparedListener(null)
        m.setOnCompletionListener(null)
        m.setOnErrorListener(null)
        try { m.release() } catch (_: Exception) {}
    }

    companion object {
        /** Música: los mismos para el MediaPlayer y para pedir el foco ([FocoAudioSistema]). */
        val ATRIBUTOS: AudioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()
    }
}

/**
 * El foco de audio del sistema para el fragmento de Deezer (el único audio propio: Spotify y YouTube
 * van en su WebView). Sin pedirlo, el fragmento sonaba encima de otra música o de una llamada, y no
 * se callaba cuando otra app empezaba a sonar.
 */
interface FocoAudio {
    /** Pide el foco; [alPerder] si otra app se lo lleva (llamada, otra música). false = denegado. */
    fun pedir(alPerder: () -> Unit): Boolean
    fun soltar()
}

/** [FocoAudio] con el AudioManager. API 26+ con AudioFocusRequest; Android 7 con la llamada vieja. */
class FocoAudioSistema(ctx: Context) : FocoAudio {
    private val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var peticion: AudioFocusRequest? = null
    private var oyente: AudioManager.OnAudioFocusChangeListener? = null

    override fun pedir(alPerder: () -> Unit): Boolean {
        soltar()
        // Perderlo del todo o por un rato (una llamada) pausa; "bajar el volumen" lo hace el sistema solo.
        val o = AudioManager.OnAudioFocusChangeListener { c ->
            if (c == AudioManager.AUDIOFOCUS_LOSS || c == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) alPerder()
        }
        oyente = o
        val r = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val p = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(AudioMediaPlayer.ATRIBUTOS)
                .setOnAudioFocusChangeListener(o, Handler(Looper.getMainLooper()))
                .build()
            peticion = p
            am.requestAudioFocus(p)
        } else {
            @Suppress("DEPRECATION")
            am.requestAudioFocus(o, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN)
        }
        return r == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }

    override fun soltar() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) peticion?.let { am.abandonAudioFocusRequest(it) }
        else oyente?.let { @Suppress("DEPRECATION") am.abandonAudioFocus(it) }
        peticion = null
        oyente = null
    }
}

/**
 * El reproductor de las tarjetas del feed de Música: UNO solo suena a la vez.
 *
 * Qué pinta cada tarjeta ([tarjeta]):
 * - **Con Spotify, la tarjeta ES el reproductor de Spotify** (A9, dueño: "darle al play dos veces y
 *   que aparezcan dos tarjetas es redundante"): el adapter lo monta en el bind con [montarSpotify]
 *   y lo suelta al reciclar con [soltarSpotify]; un toque en SU play suena (30 s dentro de la app).
 *   Un WebView por tarjeta a la vista, no por publicación: se crea 300 ms después de engancharse a
 *   la ventana (si pasa volando, ni se crea) y se destruye al reciclar la fila.
 * - YouTube y Deezer, como antes: nuestro play monta en el `pub_hueco` el vídeo (visible y de al
 *   menos [MINIMO_DP], como manda el spec) o el fragmento de Deezer con un [MediaPlayer] propio,
 *   y debajo "Entera en Spotify" (una búsqueda con título y artista). Quien lo monta es quien lo
 *   quita: el adapter no limpia el hueco (solo al reciclar), así que [parar] lo deja vacío y escondido.
 *
 * Uno solo sonando: el reproductor de Spotify avisa por la iFrame API cuando empieza a sonar
 * ([EmbedView.alSonar]); entonces se callan el YouTube/Deezer propio y se pausa el Spotify que
 * sonaba ([pausarEmbed]); tocar un YouTube/Deezer o [parar] pausan el Spotify que suene.
 *
 * Reglas heredadas del antiguo reproductor de playlists (ya retirado): los WebView se sueltan con `release()` ANTES de sacarlos
 * de la vista (gotcha "Destruir un WebView antes de sacarlo..."), y quien saca la pantalla
 * (MusicaController: ocultar, pausar, atrás; hojas y diálogos) llama a [parar]; la tarjeta que sale
 * de la ventana, a [pararSi] (solo si es la que suena: gotcha "Apagar el reproductor aparcado no
 * calla el que se ve en pantalla").
 *
 * El fragmento de Deezer se pide en segundo plano justo al dar al play (la URL va firmada y caduca)
 * y vuelve al hilo principal; si entre medias se tocó otra tarjeta, la respuesta se tira ([turno]).
 */
class ReproductorFeed(
    private val act: AppCompatActivity,
    private val abrirFuera: (String) -> Unit,
    private val fragmento: (Long) -> String? = { BuscadorDeezer.fragmento(it) },
    private val segundoPlano: (() -> Unit) -> Unit = { Thread(it).start() },
    private val crearAudio: () -> AudioFragmento = { AudioMediaPlayer() },
    private val avisar: (String) -> Unit = { Toast.makeText(act, it, Toast.LENGTH_SHORT).show() },
    private val foco: FocoAudio = FocoAudioSistema(act),
    /** Pausa un reproductor de Spotify sin desmontarlo. Los tests miran a quién se le pide. */
    private val pausarEmbed: (EmbedView) -> Unit = { it.pausarSpotify() }
) {
    private var idActual: String? = null
    private var hueco: FrameLayout? = null
    private var embed: EmbedView? = null
    private var audio: AudioFragmento? = null

    /** Los reproductores de Spotify montados en tarjetas, con el id de su publicación. */
    private val spotifys = LinkedHashMap<EmbedView, String>()

    /** El de Spotify que suena (lo dijo su página), o null. */
    private var spotifySonando: EmbedView? = null

    /** Cada tocar/parar lo sube: una respuesta de red de un turno anterior no monta nada. */
    private var turno = 0

    private val handler = Handler(Looper.getMainLooper())
    private var pulso: Runnable? = null

    /** El id de la publicación que suena (o está cargando), o null. */
    fun sonando(): String? = idActual

    /** Solo para los tests: ¿sigue el pulso de 500 ms de la barra? */
    internal fun pulsando(): Boolean = pulso != null

    /** Solo para los tests: cuántos reproductores de Spotify hay montados. */
    internal fun spotifysMontados(): Int = spotifys.size

    /**
     * Nuestro play (YouTube y Deezer). Las tarjetas de Spotify no lo tienen: suenan con el play del
     * propio reproductor de Spotify ([montarSpotify]), así que aquí no hacen nada.
     */
    fun tocar(item: ItemFeed, hueco: FrameLayout) {
        val fuenteElegida = fuente(item)
        if (fuenteElegida is FuenteFeed.Spotify) return
        // Tocar la que ya suena la para (el play de la tarjeta hace de interruptor).
        if (idActual == item.id) { parar(); return }
        parar()
        // Sin nada que reproducir (ni Spotify, ni YouTube, ni Deezer): no se marca como sonando ni se
        // monta el hueco con solo el botón de Spotify; se dice por qué no pasa nada.
        if (fuenteElegida == FuenteFeed.Ninguna) { avisar(act.getString(R.string.pub_sin_reproduccion)); return }
        val mi = ++turno
        idActual = item.id
        this.hueco = hueco
        val raiz = LinearLayout(act).apply { orientation = LinearLayout.VERTICAL }
        hueco.removeAllViews()
        hueco.addView(raiz, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        hueco.visibility = View.VISIBLE
        when (val f = fuenteElegida) {
            is FuenteFeed.Youtube -> {
                val e = EmbedView(act)
                e.deMusica = true
                val m = act.resources.displayMetrics
                val ancho = hueco.width.takeIf { it > 0 } ?: m.widthPixels
                val alto = altoCaja(ancho, m.heightPixels, m.density)
                raiz.addView(e, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, alto))
                e.bind(EmbedSpec("youtube", "https://www.youtube.com/watch?v=${f.id}", f.id))
                embed = e
            }
            is FuenteFeed.Deezer -> montarDeezer(raiz, f.id, mi)
            is FuenteFeed.Spotify, FuenteFeed.Ninguna -> {}  // ya atendidos arriba
        }
        raiz.addView(botonEntera(item.cancion), LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, dp(48)  // 48 dp: zona de toque mínima
        ).apply { gravity = Gravity.CENTER_HORIZONTAL; topMargin = dp(8); bottomMargin = dp(8) })
    }

    /**
     * Monta en [contenedor] el reproductor de Spotify de [item] (la tarjeta entera, ver la clase).
     * [alListo] cuando ya se ve, para que el adapter esconda el marcador. false si [item] no tiene
     * una pista de Spotify válida (entonces no se monta nada).
     */
    fun montarSpotify(item: ItemFeed, contenedor: FrameLayout, alListo: () -> Unit): Boolean {
        val f = fuente(item) as? FuenteFeed.Spotify ?: return false
        soltarSpotify(contenedor)
        val e = EmbedView(act)
        e.tarjetaSpotify = true
        e.alListo = alListo
        val id = item.id
        e.alSonar = { suena -> if (spotifys.containsKey(e)) suenaSpotify(e, id, suena) }
        contenedor.addView(e, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        contenedor.visibility = View.VISIBLE
        spotifys[e] = id
        e.bind(EmbedSpec("spotify", "https://open.spotify.com/track/${f.id}", f.id))
        return true
    }

    /** Desmonta el reproductor de Spotify de [contenedor] (la fila se recicla o pasa a otra publicación). */
    fun soltarSpotify(contenedor: FrameLayout) {
        for (i in contenedor.childCount - 1 downTo 0) (contenedor.getChildAt(i) as? EmbedView)?.let(::soltarEmbed)
        contenedor.removeAllViews()
    }

    /** `onDestroy`: lo que suene y todos los reproductores de Spotify, fuera (sus WebView, destruidos). */
    fun soltarTodo() {
        parar()
        spotifys.keys.toList().forEach(::soltarEmbed)
    }

    private fun soltarEmbed(e: EmbedView) {
        spotifys.remove(e)
        if (spotifySonando === e) { spotifySonando = null; idActual = null }
        e.alSonar = null
        e.alListo = null
        // release() saca el WebView ANTES de destruirlo (gotcha "Destruir un WebView antes de sacarlo...").
        e.release()
        (e.parent as? ViewGroup)?.removeView(e)
    }

    /** La página del reproductor de Spotify [e] dice que empieza (o deja) de sonar. */
    private fun suenaSpotify(e: EmbedView, id: String, suena: Boolean) {
        if (!suena) {
            if (spotifySonando === e) { spotifySonando = null; idActual = null }
            return
        }
        // Uno solo sonando: fuera el YouTube/Deezer propio y pausa al Spotify que sonaba. Solo a ese:
        // pausar desde fuera RECARGA la pista (ver EmbedView.pausarSpotify), y hacerlo con todos
        // los de la pantalla en cada cambio de panel sería red tirada.
        pararPropio()
        spotifySonando?.takeIf { it !== e }?.let(pausarEmbed)
        spotifySonando = e
        idActual = id
    }

    /**
     * Calla lo que suene: desmonta el YouTube/Deezer propio y pausa (sin desmontar) el Spotify que
     * suena. Barato si no hay nada: se llama en cada cambio de panel.
     */
    fun parar() {
        spotifySonando?.let(pausarEmbed)
        spotifySonando = null
        pararPropio()
        idActual = null
    }

    /** Desmonta el YouTube o el fragmento de Deezer del `pub_hueco`, si hay. */
    private fun pararPropio() {
        turno++
        if (hueco == null) return
        pulso?.let { handler.removeCallbacks(it) }
        pulso = null
        audio?.soltar()
        audio = null
        foco.soltar()
        embed?.let { e ->
            e.alTerminar = null
            e.alFallar = null
            e.release()
            (e.parent as? ViewGroup)?.removeView(e)
        }
        embed = null
        hueco?.let { it.removeAllViews(); it.visibility = View.GONE }
        hueco = null
    }

    /** La tarjeta [id] sale de la ventana: solo para si es la que suena (no callar la que se ve). */
    fun pararSi(id: String) {
        if (idActual == id) parar()
    }

    private fun montarDeezer(raiz: LinearLayout, id: Long, mi: Int) {
        val fila = LinearLayout(act).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val boton = ImageView(act).apply {
            setImageResource(R.drawable.ic_pausa)
            contentDescription = act.getString(R.string.pub_pausar)
            setPadding(dp(10), dp(10), dp(10), dp(10))
            visibility = View.INVISIBLE
        }
        val barra = SeekBar(act).apply { isEnabled = false }
        fila.addView(boton, LinearLayout.LayoutParams(dp(48), dp(48)))
        fila.addView(barra, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        raiz.addView(fila, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        segundoPlano {
            val url = fragmento(id)
            act.runOnUiThread { if (turno == mi) conUrl(url, boton, barra) }
        }
    }

    private fun conUrl(url: String?, boton: ImageView, barra: SeekBar) {
        if (url == null) {
            avisar(act.getString(R.string.pub_sin_fragmento))
            parar()
            return
        }
        // Sin el foco (una llamada en curso, otra app que no lo suelta) no se empieza a sonar encima.
        if (!pedirFoco(boton)) { parar(); return }
        val a = crearAudio()
        audio = a
        boton.setOnClickListener {
            if (a.sonando()) a.pausar() else {
                if (!pedirFoco(boton)) return@setOnClickListener
                a.reanudar()
                if (pulso == null) empezarPulso(a, barra)  // volver a darle tras acabar: la barra se mueve otra vez
            }
            pintarBoton(boton, a)
        }
        barra.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar, p: Int, delUsuario: Boolean) { if (delUsuario) a.ir(p) }
            override fun onStartTrackingTouch(s: SeekBar) {}
            override fun onStopTrackingTouch(s: SeekBar) {}
        })
        a.preparar(
            url,
            alListo = {
                if (audio === a) {
                    barra.max = a.duracion().coerceAtLeast(1)
                    barra.isEnabled = true
                    boton.visibility = View.VISIBLE
                    pintarBoton(boton, a)
                    empezarPulso(a, barra)
                }
            },
            alAcabar = {
                if (audio === a) {
                    // Acabado el fragmento el pulso de 500 ms no tiene nada que medir: fuera.
                    pulso?.let { handler.removeCallbacks(it) }
                    pulso = null
                    barra.progress = 0
                    pintarBoton(boton, a, acabado = true)
                }
            },
            alFallar = { if (audio === a) { avisar(act.getString(R.string.pub_sin_fragmento)); parar() } }
        )
    }

    /**
     * Pide el foco de audio. Si otra app se lo lleva (llamada, otra música), el fragmento se pausa y el
     * botón vuelve a "escuchar"; darle otra vez lo pide de nuevo. Se suelta en [parar].
     */
    private fun pedirFoco(boton: ImageView): Boolean = foco.pedir {
        handler.post {
            val a = audio ?: return@post
            if (a.sonando()) a.pausar()
            pintarBoton(boton, a)
        }
    }

    private fun pintarBoton(boton: ImageView, a: AudioFragmento, acabado: Boolean = false) {
        val suena = !acabado && a.sonando()
        boton.setImageResource(if (suena) R.drawable.ic_pausa else R.drawable.ic_reproducir)
        boton.contentDescription = act.getString(if (suena) R.string.pub_pausar else R.string.pub_escuchar)
    }

    private fun empezarPulso(a: AudioFragmento, barra: SeekBar) {
        val r = object : Runnable {
            override fun run() {
                if (audio !== a) return
                barra.progress = a.posicion()
                handler.postDelayed(this, 500)
            }
        }
        pulso = r
        handler.post(r)
    }

    private fun botonEntera(c: CancionFeed): View =
        TextView(act, null, 0, R.style.BotonPrimario).apply {
            text = act.getString(R.string.pub_entera_spotify)
            setOnClickListener { abrirFuera(urlEntera(c)) }
        }

    private fun dp(v: Int) = (v * act.resources.displayMetrics.density).toInt()

    /** Qué pinta la tarjeta de una publicación del feed. Lo decide [tarjeta]. */
    enum class TarjetaFeed {
        /** La tarjeta ES el reproductor de Spotify: un toque en su play suena. */
        SPOTIFY_EMBED,
        /** Nuestra caja con play; al tocarlo, el reproductor de YouTube debajo. */
        YOUTUBE_AL_TOCAR,
        /** Nuestra caja con play, que suena a la primera (fragmento de Deezer con MediaPlayer). */
        DEEZER_NATIVA,
        /** Nuestra caja con play, que solo avisa de que no hay nada que oír. */
        SIN_FUENTE
    }

    companion object {
        /**
         * Alto mínimo del vídeo: los 200 px CSS que pide YouTube. La página del embed usa
         * width=device-width, así que un px CSS es un dp (medido: 480 dpi, 3 px por dp, y un
         * WebView de 1008 px mide 336 px CSS).
         */
        const val MINIMO_DP = 200

        /**
         * Alto de la caja del vídeo de YouTube, en px: 16:9 del ancho; en horizontal eso no cabe y
         * se queda en el 55 % del alto de la pantalla (YouTube pone bandas a los lados). Pero NUNCA
         * baja de [MINIMO_DP]: es requisito de YouTube.
         */
        fun altoCaja(ancho: Int, altoPantalla: Int, densidad: Float): Int {
            val minimo = (MINIMO_DP * densidad).toInt()
            return maxOf(minOf(ancho * 9 / 16, (altoPantalla * 0.55f).toInt()), minimo)
        }

        private fun spotifyId(s: String): String? =
            EnlaceMusica.de(s)?.takeIf { it.fuente == FuenteMusica.SPOTIFY }?.id
                ?: s.takeIf { EstadoYoutube.idSpotifyValido(it) }

        private fun youtubeId(s: String): String? =
            EnlaceMusica.de(s)?.takeIf { it.fuente == FuenteMusica.YOUTUBE }?.id
                ?: s.takeIf { EstadoYoutube.idValido(it) }

        /**
         * Qué suena: Spotify, si no YouTube, si no Deezer. El servidor manda ENLACES
         * (`https://open.spotify.com/track/<id>`); un id suelto también vale. Lo que no se
         * reconoce como pista válida no se monta (el id acaba en un iframe).
         */
        fun fuente(item: ItemFeed): FuenteFeed {
            val c = item.cancion
            c.spotify?.let(::spotifyId)?.let { return FuenteFeed.Spotify(it) }
            c.youtube?.let(::youtubeId)?.let { return FuenteFeed.Youtube(it) }
            c.deezer?.let { return FuenteFeed.Deezer(it) }
            return FuenteFeed.Ninguna
        }

        fun tarjeta(item: ItemFeed): TarjetaFeed = when (fuente(item)) {
            is FuenteFeed.Spotify -> TarjetaFeed.SPOTIFY_EMBED
            is FuenteFeed.Youtube -> TarjetaFeed.YOUTUBE_AL_TOCAR
            is FuenteFeed.Deezer -> TarjetaFeed.DEEZER_NATIVA
            FuenteFeed.Ninguna -> TarjetaFeed.SIN_FUENTE
        }

        /**
         * Alto (dp, que en la página del embed es px CSS) del reproductor de Spotify de la tarjeta:
         * el compacto de 80, que cabe en el sitio de nuestra caja (portada, título, artista y play)
         * casi sin crecer. El de 152 repetía la portada en grande y doblaba el alto de cada fila
         * del feed por la misma información.
         */
        const val ALTO_TARJETA_SPOTIFY = 80

        /** "Entera en Spotify": la pista exacta si hay id; si no, la búsqueda por título y artista. */
        fun urlEntera(c: CancionFeed): String {
            c.spotify?.let(::spotifyId)?.let { return "https://open.spotify.com/track/$it" }
            val q = listOfNotNull(c.titulo, c.artista).joinToString(" ").trim()
            return "https://open.spotify.com/search/" + URLEncoder.encode(q, "UTF-8").replace("+", "%20")
        }
    }
}
