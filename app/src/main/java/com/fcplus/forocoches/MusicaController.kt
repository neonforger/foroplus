package com.fcplus.forocoches

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.RecyclerView
import java.util.concurrent.Executors

/**
 * La sección Música (spec del 2026-11-07): lo que hay DENTRO de su panel. Molde de
 * [CuentaPanelController]: MainActivity solo pone los enganches (la pestaña "Música" tras el
 * Popurrí, `listSource = "musica"`, apagarlo cuando se va a otra fuente o panel y
 * `onPause`).
 *
 * El panel (`res/layout/panel_musica.xml`) vive dentro del FrameLayout de la lista, encima de
 * `list_refresh`, para que la cabecera y la tira de pestañas sigan a la vista y se pueda
 * deslizar a los subforos como desde el Popurrí o +18.
 *
 * Música es SOLO el feed ([FeedController]): ni playlists ni "Lo tuyo" ni guardar canciones en el
 * móvil (decisión del dueño, 2026-11-11: "que solo se puedan compartir canciones, es más simple y
 * no lía a la gente"). Aquí vive lo que lo rodea: la pantalla de compartir ([HojaCompartirCancion]),
 * la verificación, el borrado de la cuenta (que se pide desde Opciones) y lo que llega de Compartir
 * de Android. Con el servidor apagado la pestaña ni existe (MainActivity) y, si aun así se llegara
 * aquí, el feed no pide nada.
 *
 * Lo que suena se para en [ocultar], [pausar] y al abrirse una hoja o un diálogo: nada suena fuera
 * de esta pantalla ni con la app en segundo plano.
 *
 * @param raiz el panel ya metido en la jerarquía (su raíz, `R.id.panel_musica`).
 * @param enganches lo del servidor de Música (cuenta, config, verificación).
 * @param abrirFuera abre una URL fuera de la app, nunca en la capa web (regla de oro): "Entera en
 *   Spotify". MainActivity le pasa [abrirEnSuApp], que deja elegir la app de YouTube o de Spotify
 *   en vez de fijar el navegador.
 */
class MusicaController(
    private val act: AppCompatActivity,
    private val raiz: View,
    private val enganches: EnganchesMusica,
    /** Solo los tests lo cambian: el cliente del feed (por defecto, el de [enganches]). */
    apiFeed: (() -> ApiMusica)? = null,
    private val abrirFuera: (String) -> Unit
) {

    /** ¿Se está viendo el panel? (El atrás de MainActivity solo le pregunta entonces.) */
    var visible = false
        private set

    private val reproductorFeed = ReproductorFeed(act, abrirFuera)
    private val feed = FeedController(
        act, raiz, enganches, reproductorFeed,
        api = apiFeed ?: { enganches.api() },
        abrirVerificacion = { abrirVerificacion() },
        abrirCompartir = { alCompartir() }
    )

    /** "Compartir canción" (el botón del feed): abre la hoja de A6 (con verificación antes si falta el token). */
    var alCompartir: () -> Unit = { abrirCompartir(null) }

    /** Borrar la cuenta de Música (se pide desde Opciones). Tras borrarla el feed se pide de nuevo. */
    private val flujos = FlujosMusica(
        act, enganches, abrirVerificacion = { abrirVerificacion() },
        alCambiar = { feed.olvidarCuenta(); feed.refrescar(true) }
    )

    init {
        // Una sola vez y fuera del hilo de la interfaz: es disco.
        val prefs = act.getSharedPreferences(LimpiezaMusica.PREFS, Context.MODE_PRIVATE)
        disco.execute { LimpiezaMusica.carpetaDePlaylists(act.filesDir, prefs) }
    }

    /** Enseña el panel (el feed). */
    fun mostrar() {
        visible = true
        raiz.visibility = View.VISIBLE
        feed.mostrar()
    }

    /**
     * Quita el panel. MainActivity lo llama al ir a cualquier otra fuente de la lista o a
     * cualquier otro panel (hilo, Opciones, MPs…): nada suena con el panel fuera de la vista.
     */
    fun ocultar() {
        visible = false
        raiz.visibility = View.GONE
        feed.ocultar()
    }

    /** `onDestroy` de la actividad: el reloj del feed y lo que suene, fuera. */
    fun destruir() {
        feed.ocultar()
        pausar()
    }

    /** `onPause` de la actividad y todo lo que tape el panel: "La música se para al salir de la app". */
    fun pausar() {
        reproductorFeed.parar()
    }

    /** `onPause` de la actividad (no las hojas: esas solo callan lo que suena): además el reloj del feed se apaga. */
    fun alSegundoPlano() {
        pausar()
        feed.pausar()
    }

    /** `onResume` de la actividad: el reloj del feed vuelve solo si el feed sigue a la vista. */
    fun reanudar() {
        if (visible) feed.reanudar()
    }


    /**
     * ¿Puede empezar en este punto de la pantalla ([x], [y] en crudo) el gesto de deslizar a otra
     * pestaña? MainActivity lo pregunta al apoyar el dedo. En todas partes menos sobre el
     * reproductor mientras suena algo: la barra de Spotify y la del fragmento se arrastran de lado.
     */
    fun dejaDeslizarDesde(x: Int, y: Int): Boolean {
        if (reproductorFeed.sonando() == null) return true
        val rv = raiz.findViewById<RecyclerView>(R.id.musica_feed)
        for (i in 0 until rv.childCount) {
            val hueco = rv.getChildAt(i).findViewById<View>(R.id.pub_hueco) ?: continue
            if (dentro(hueco, x, y)) return false
        }
        return true
    }

    private fun dentro(v: View, x: Int, y: Int): Boolean {
        if (!v.isShown) return false
        val loc = IntArray(2)
        v.getLocationOnScreen(loc)
        return x in loc[0]..(loc[0] + v.width) && y in loc[1]..(loc[1] + v.height)
    }

    /**
     * La hoja de verificación (C2). Solo con el servidor encendido en la config; al verificarse,
     * el feed se pide de nuevo (ahora con token: trae los corazones y las publicaciones propias).
     */
    fun abrirVerificacion(despues: (() -> Unit)? = null) {
        val e = enganches
        if (!e.servidorActivo()) return
        pausar()  // la hoja tapa el reproductor, y YouTube no puede sonar tapado
        HojaVerificarMusica(act, e) {
            feed.olvidarCuenta()
            feed.refrescar(true)
            despues?.invoke()
        }.mostrar()
    }

    /** "Borrar mi cuenta de Música" de Opciones: pregunta y, si se confirma, borra. */
    fun borrarMiCuenta() = flujos.borrarCuenta()

    /**
     * ¿Hay token de Música para la cuenta activa? Se mira en el hilo del disco (la primera lectura
     * abre las preferencias cifradas: el keystore) y se contesta ([alSaber]) en el de la interfaz.
     * Con el servidor apagado, sin cuenta o con error: no.
     */
    fun tieneCuenta(alSaber: (Boolean) -> Unit) {
        val e = enganches
        if (!e.servidorActivo()) { alSaber(false); return }
        val uid = e.uidActivo()
        hiloToken {
            val tiene = uid.isNotEmpty() && try { e.tokens.de(uid) != null } catch (_: Exception) { false }
            act.runOnUiThread { alSaber(tiene) }
        }
    }

    /**
     * La pantalla de compartir una canción (A6), con [enlace] ya puesto si llega de otra app.
     * Sin token, antes la verificación y, al verificarse, esta pantalla. NO se mira el tope diario
     * (no se anuncia): solo el 429 del servidor lo dice. El token se mira fuera del hilo de la
     * interfaz (keystore). Lo que suena se calla: la pantalla tapa el reproductor.
     */
    fun abrirCompartir(enlace: String?) {
        val e = enganches
        if (!e.servidorActivo()) return
        // Dos toques seguidos (o dos compartidos) no abren dos pantallas: las dos podrían publicar.
        if (hojaCompartir?.abierta == true || abriendoCompartir) return
        abriendoCompartir = true
        pausar()
        val uid = e.uidActivo()
        hiloToken {
            val tiene = uid.isNotEmpty() && try { e.tokens.de(uid) != null } catch (_: Exception) { false }
            act.runOnUiThread {
                abriendoCompartir = false
                if (act.isFinishing || act.isDestroyed) return@runOnUiThread
                if (tiene) mostrarCompartir(e, enlace) else abrirVerificacion { mostrarCompartir(e, enlace) }
            }
        }
    }

    /** La pantalla de compartir viva (si la hay) y si se está abriendo (mirando el token). */
    internal var hojaCompartir: HojaCompartirCancion? = null
    private var abriendoCompartir = false

    /** Dónde se mira el token (keystore): el hilo del disco; los tests lo cambian. */
    internal var hiloToken: (() -> Unit) -> Unit = { disco.execute(it) }

    private fun mostrarCompartir(e: EnganchesMusica, enlace: String?) {
        // Único sitio por donde se abre: con la verificación de por medio también pueden llegar dos.
        if (hojaCompartir?.abierta == true) return
        // Publicada: el feed se pide de nuevo (forzado) y la nueva sale arriba, con su sitio entre
        // las recientes. Es la vía más corta y la misma que usa tras verificarse; evita meter a mano
        // en la lista una tarjeta que el servidor puede tener ya con otra hora de caducidad.
        hojaCompartir = HojaCompartirCancion(act, e, { feed.refrescar(true) }).also { it.mostrar(enlace) }
    }

    /**
     * Algo compartido desde otra app hacia Música: SIEMPRE la pantalla de compartir con el enlace
     * puesto. Con varias canciones va la primera que se reconozca (una publicación lleva una); si
     * no se reconoce ninguna (un `spotify.link` corto sin resolver, texto suelto), la pantalla sale
     * con eso pegado en el buscador y que decida quien compartió. Con el servidor apagado: se avisa
     * y no se hace nada más.
     */
    fun recibirCompartido(texto: String) {
        if (!enganches.servidorActivo()) {
            Toast.makeText(act, R.string.musica_no_disponible, Toast.LENGTH_LONG).show()
            return
        }
        val enlace = HojaCompartirCancion.enlaceDe(texto)
            ?: EnlaceMusica.cortosSpotify(texto).firstOrNull()
            ?: texto.trim().take(MAX_PEGADO).ifEmpty { null }
        abrirCompartir(enlace)
    }

    companion object {
        /** Lo que se pega en el buscador cuando lo compartido no trae ninguna canción reconocible. */
        private const val MAX_PEGADO = 200

        /** Un solo hilo para el disco (el token, la limpieza de la carpeta vieja), compartido por todas las instancias. */
        private val disco = Executors.newSingleThreadExecutor()

        /**
         * Abre una canción FUERA, en la app que reclame el enlace (YouTube, Spotify) o, si no hay,
         * en el navegador. No pasa por el `openExternal` de MainActivity, que fija el navegador
         * por defecto (bien para los enlaces de un hilo, mal aquí: "Escuchar entera en Spotify"
         * acabaría en Chrome aunque la app de Spotify estuviera instalada). Sin bucle posible:
         * ForoPlus solo reclama enlaces de FC. Y nunca la capa web (regla de oro).
         */
        fun abrirEnSuApp(ctx: Context, url: String) {
            if (!url.startsWith("https://")) return
            val i = Intent(Intent.ACTION_VIEW, Uri.parse(url)).addCategory(Intent.CATEGORY_BROWSABLE)
            if (ctx !is Activity) i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try {
                ctx.startActivity(i)
            } catch (_: ActivityNotFoundException) {
                Toast.makeText(ctx, "No hay ninguna app para abrir este enlace", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
