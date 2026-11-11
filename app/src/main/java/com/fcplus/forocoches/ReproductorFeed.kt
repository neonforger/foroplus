package com.fcplus.forocoches

import android.media.AudioAttributes
import android.media.MediaPlayer
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
        m.setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build()
        )
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
}

/**
 * El reproductor de las tarjetas del feed de Música: UNO solo suena a la vez, montado en el
 * `pub_hueco` de la tarjeta que se tocó. Quien lo monta es quien lo quita: el adapter no limpia el
 * hueco (solo al reciclar), así que [parar] deja el hueco vacío y escondido.
 *
 * Qué suena ([fuente]): Spotify (reproductor oficial, 30 s dentro de la app) > YouTube (visible y
 * de al menos [ReproductorMusica.MINIMO_DP] mientras suena, como manda el spec) > fragmento de
 * Deezer con un [MediaPlayer] propio. Siempre debajo, "Entera en Spotify" (la pista exacta si hay
 * id; si no, una búsqueda con título y artista).
 *
 * Reglas heredadas de [ReproductorMusica]: los WebView se sueltan con `release()` ANTES de sacarlos
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
    private val avisar: (String) -> Unit = { Toast.makeText(act, it, Toast.LENGTH_SHORT).show() }
) {
    private var idActual: String? = null
    private var hueco: FrameLayout? = null
    private var embed: EmbedView? = null
    private var audio: AudioFragmento? = null

    /** Cada tocar/parar lo sube: una respuesta de red de un turno anterior no monta nada. */
    private var turno = 0

    private val handler = Handler(Looper.getMainLooper())
    private var pulso: Runnable? = null

    /** El id de la publicación que suena (o está cargando), o null. */
    fun sonando(): String? = idActual

    fun tocar(item: ItemFeed, hueco: FrameLayout) {
        // Tocar la que ya suena la para (el play de la tarjeta hace de interruptor).
        if (idActual == item.id) { parar(); return }
        parar()
        val mi = ++turno
        idActual = item.id
        this.hueco = hueco
        val raiz = LinearLayout(act).apply { orientation = LinearLayout.VERTICAL }
        hueco.removeAllViews()
        hueco.addView(raiz, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        hueco.visibility = View.VISIBLE
        when (val f = fuente(item)) {
            is FuenteFeed.Spotify -> {
                val e = EmbedView(act)
                raiz.addView(e, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
                e.bind(EmbedSpec("spotify", "https://open.spotify.com/track/${f.id}", f.id))
                embed = e
            }
            is FuenteFeed.Youtube -> {
                val e = EmbedView(act)
                e.deMusica = true
                val m = act.resources.displayMetrics
                val ancho = hueco.width.takeIf { it > 0 } ?: m.widthPixels
                val alto = ReproductorMusica.altoCaja(ancho, m.heightPixels, null, m.density)
                raiz.addView(e, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, alto))
                e.bind(EmbedSpec("youtube", "https://www.youtube.com/watch?v=${f.id}", f.id))
                embed = e
            }
            is FuenteFeed.Deezer -> montarDeezer(raiz, f.id, mi)
            FuenteFeed.Ninguna -> {}
        }
        raiz.addView(botonEntera(item.cancion), LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { gravity = Gravity.CENTER_HORIZONTAL; topMargin = dp(8); bottomMargin = dp(8) })
    }

    /** Calla y desmonta lo que suene. Barato si no hay nada: se llama en cada cambio de panel. */
    fun parar() {
        turno++
        if (idActual == null) return
        pulso?.let { handler.removeCallbacks(it) }
        pulso = null
        audio?.soltar()
        audio = null
        embed?.let { e ->
            e.alTerminar = null
            e.alFallar = null
            e.release()
            (e.parent as? ViewGroup)?.removeView(e)
        }
        embed = null
        hueco?.let { it.removeAllViews(); it.visibility = View.GONE }
        hueco = null
        idActual = null
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
        val a = crearAudio()
        audio = a
        boton.setOnClickListener {
            if (a.sonando()) a.pausar() else a.reanudar()
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
            alAcabar = { if (audio === a) { barra.progress = 0; pintarBoton(boton, a, acabado = true) } },
            alFallar = { if (audio === a) { avisar(act.getString(R.string.pub_sin_fragmento)); parar() } }
        )
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

    companion object {
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

        /** "Entera en Spotify": la pista exacta si hay id; si no, la búsqueda por título y artista. */
        fun urlEntera(c: CancionFeed): String {
            c.spotify?.let(::spotifyId)?.let { return "https://open.spotify.com/track/$it" }
            val q = listOfNotNull(c.titulo, c.artista).joinToString(" ").trim()
            return "https://open.spotify.com/search/" + URLEncoder.encode(q, "UTF-8").replace("+", "%20")
        }
    }
}
