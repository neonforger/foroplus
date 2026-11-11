package com.fcplus.forocoches

import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.SeekBar
import androidx.appcompat.app.AppCompatActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ReproductorFeedTest {
    private val sp = "https://open.spotify.com/track/4uLU6hMCjMI75M1A2tKUQC"
    private val yt = "https://www.youtube.com/watch?v=dQw4w9WgXcQ"

    private fun item(
        id: String, spotify: String? = null, youtube: String? = null, deezer: Long? = null,
        titulo: String = "Titulo", artista: String? = "Artista"
    ) = ItemFeed(id, "YoMismo", "frase", 0, 1, 0, false, false, CancionFeed("c$id", titulo, artista, null, spotify, youtube, deezer))

    private class FakeAudio : AudioFragmento {
        var url: String? = null
        var soltado = false
        var suena = false
        var alListo: () -> Unit = {}
        var alAcabar: () -> Unit = {}
        override fun preparar(url: String, alListo: () -> Unit, alAcabar: () -> Unit, alFallar: () -> Unit) {
            this.url = url; this.alListo = alListo; this.alAcabar = alAcabar
        }
        override fun reanudar() { suena = true }
        override fun pausar() { suena = false }
        override fun sonando() = suena
        override fun posicion() = 0
        override fun duracion() = 30000
        override fun ir(ms: Int) {}
        override fun soltar() { soltado = true }
    }

    private class FakeFoco(var concede: Boolean = true) : FocoAudio {
        var pedidos = 0
        var tiene = false
        var alPerder: () -> Unit = {}
        override fun pedir(alPerder: () -> Unit): Boolean { pedidos++; this.alPerder = alPerder; tiene = concede; return concede }
        override fun soltar() { tiene = false }
    }

    private class Montaje(val r: ReproductorFeed, val pausados: MutableList<EmbedView>, val recargados: MutableList<EmbedView>, val abiertas: MutableList<String>, val avisos: MutableList<String>,
                          val audios: MutableList<FakeAudio>, val act: AppCompatActivity, val foco: FakeFoco)

    private fun montar(foco: FakeFoco = FakeFoco(), fragmento: (Long) -> String? = { "https://cdnt-preview.dzcdn.net/x.mp3" }): Montaje {
        val ctrl = Robolectric.buildActivity(AppCompatActivity::class.java)
        ctrl.get().setTheme(R.style.Theme_FCForocoches)
        ctrl.setup()
        val ab = ArrayList<String>(); val av = ArrayList<String>(); val au = ArrayList<FakeAudio>()
        val pa = ArrayList<EmbedView>(); val re = ArrayList<EmbedView>()
        val r = ReproductorFeed(
            ctrl.get(), { ab.add(it) }, fragmento, segundoPlano = { it() },
            crearAudio = { FakeAudio().also { a -> au.add(a) } }, avisar = { av.add(it) }, foco = foco,
            pausarEmbed = { pa.add(it) }, recargar = { re.add(it) }
        )
        return Montaje(r, pa, re, ab, av, au, ctrl.get(), foco)
    }

    private fun hueco(m: Montaje) = FrameLayout(m.act).also { it.visibility = View.GONE }

    private fun todas(v: View, salida: MutableList<View> = ArrayList()): List<View> {
        salida.add(v)
        if (v is ViewGroup) for (i in 0 until v.childCount) todas(v.getChildAt(i), salida)
        return salida
    }

    private fun embeds(h: FrameLayout) = todas(h).filterIsInstance<EmbedView>()

    @Test
    fun `elige spotify si hay id`() {
        assertEquals(FuenteFeed.Spotify("4uLU6hMCjMI75M1A2tKUQC"), ReproductorFeed.fuente(item("1", spotify = sp, youtube = yt, deezer = 5)))
        // Un id suelto también vale
        assertEquals(FuenteFeed.Spotify("4uLU6hMCjMI75M1A2tKUQC"), ReproductorFeed.fuente(item("1", spotify = "4uLU6hMCjMI75M1A2tKUQC")))
    }

    @Test
    fun `elige youtube si no hay spotify`() {
        assertEquals(FuenteFeed.Youtube("dQw4w9WgXcQ"), ReproductorFeed.fuente(item("1", youtube = yt, deezer = 5)))
    }

    @Test
    fun `elige deezer si no hay ninguno`() {
        assertEquals(FuenteFeed.Deezer(5), ReproductorFeed.fuente(item("1", deezer = 5)))
        assertEquals(FuenteFeed.Ninguna, ReproductorFeed.fuente(item("1")))
        // Un enlace que no es una pista no se monta
        assertEquals(FuenteFeed.Ninguna, ReproductorFeed.fuente(item("1", spotify = "https://evil.example/x\"><script>")))
    }

    @Test
    fun `tocar monta youtube en el hueco`() {
        val m = montar()
        val h2 = hueco(m)
        m.r.tocar(item("2", youtube = yt), h2)
        assertEquals(1, embeds(h2).size)
        assertTrue(embeds(h2)[0].deMusica)
        val alto = embeds(h2)[0].layoutParams.height
        assertTrue(alto >= (ReproductorFeed.MINIMO_DP * m.act.resources.displayMetrics.density).toInt())
    }

    @Test
    fun `tocar otra para la anterior`() {
        val m = montar()
        val h1 = hueco(m); val h2 = hueco(m)
        m.r.tocar(item("1", youtube = yt), h1)
        m.r.tocar(item("2", deezer = 7), h2)
        assertEquals("2", m.r.sonando())
        assertEquals(0, h1.childCount)
        assertEquals(View.GONE, h1.visibility)
        // y el audio de la nueva suena; al volver a otra se suelta
        val h3 = hueco(m)
        m.r.tocar(item("3", youtube = yt), h3)
        assertTrue(m.audios[0].soltado)
    }

    @Test
    fun `pararSi de otra no para`() {
        val m = montar()
        val h = hueco(m)
        m.r.tocar(item("1", youtube = yt), h)
        m.r.pararSi("2")
        assertEquals("1", m.r.sonando())
        m.r.pararSi("1")
        assertNull(m.r.sonando())
        assertEquals(0, h.childCount)
    }

    @Test
    fun `deezer pide el fragmento por id y suena con barra`() {
        val pedidos = ArrayList<Long>()
        val m = montar { pedidos.add(it); "https://cdnt-preview.dzcdn.net/x.mp3" }
        val h = hueco(m)
        m.r.tocar(item("1", deezer = 99), h)
        assertEquals(listOf(99L), pedidos)
        assertEquals("https://cdnt-preview.dzcdn.net/x.mp3", m.audios[0].url)
        assertEquals(1, todas(h).filterIsInstance<SeekBar>().size)
    }

    @Test
    fun `fragmento null - aviso y no suena`() {
        val m = montar { null }
        val h = hueco(m)
        m.r.tocar(item("1", deezer = 99), h)
        assertEquals(1, m.avisos.size)
        assertNull(m.r.sonando())
        assertTrue(m.audios.isEmpty())
        assertEquals(0, h.childCount)
    }

    @Test
    fun `una respuesta tardia de otra tarjeta no monta nada`() {
        val pendientes = ArrayList<() -> Unit>()
        val ctrl = Robolectric.buildActivity(AppCompatActivity::class.java)
        ctrl.get().setTheme(R.style.Theme_FCForocoches)
        ctrl.setup()
        val audios = ArrayList<FakeAudio>()
        val r = ReproductorFeed(ctrl.get(), {}, { "https://cdnt-preview.dzcdn.net/x.mp3" },
            segundoPlano = { pendientes.add(it) }, crearAudio = { FakeAudio().also { a -> audios.add(a) } })
        r.tocar(item("1", deezer = 1), FrameLayout(ctrl.get()))
        r.parar()
        pendientes.forEach { it() }
        assertTrue(audios.isEmpty())
        assertNull(r.sonando())
    }

    @Test
    fun `entera en spotify en una de youtube abre la busqueda`() {
        val m = montar()
        val h = hueco(m)
        m.r.tocar(item("1", youtube = yt, titulo = "Solo", artista = null), h)
        todas(h).filterIsInstance<android.widget.TextView>().first { it.text == "Entera en Spotify" }.performClick()
        assertEquals(listOf("https://open.spotify.com/search/Solo"), m.abiertas)
    }

    @Test
    fun `sin id abre la busqueda con titulo y artista`() {
        val c = item("1", deezer = 3, titulo = "Ni Un Dia Mas", artista = "Los Chunguitos & Co").cancion
        assertEquals(
            "https://open.spotify.com/search/Ni%20Un%20Dia%20Mas%20Los%20Chunguitos%20%26%20Co",
            ReproductorFeed.urlEntera(c)
        )
        assertEquals("https://open.spotify.com/search/Solo", ReproductorFeed.urlEntera(item("1", titulo = "Solo", artista = null).cancion))
    }

    @Test
    fun `el boton entera en spotify mide 48 dp`() {
        val m = montar()
        val h = hueco(m)
        m.r.tocar(item("1", deezer = 4), h)
        val b = todas(h).filterIsInstance<android.widget.TextView>().first { it.text == "Entera en Spotify" }
        assertEquals((48 * m.act.resources.displayMetrics.density).toInt(), b.layoutParams.height)
    }

    @Test
    fun `sin fuente no marca sonando y avisa`() {
        val m = montar()
        val h = hueco(m)
        m.r.tocar(item("1"), h)
        assertNull(m.r.sonando())
        assertEquals(listOf(m.act.getString(R.string.pub_sin_reproduccion)), m.avisos)
        assertEquals(0, h.childCount)
        assertEquals(View.GONE, h.visibility)
    }

    @Test
    fun `al acabar el fragmento se para el pulso`() {
        val m = montar()
        m.r.tocar(item("1", deezer = 9), hueco(m))
        m.audios[0].alListo()
        assertTrue(m.r.pulsando())
        m.audios[0].alAcabar()
        assertTrue(!m.r.pulsando())
    }

    @Test
    fun `el fragmento pide el foco de audio, lo suelta al parar y se pausa si otra app se lo lleva`() {
        val m = montar()
        m.r.tocar(item("1", deezer = 9), hueco(m))
        assertEquals(1, m.foco.pedidos)
        assertTrue(m.foco.tiene)
        val a = m.audios[0]
        a.suena = true  // el MediaPlayer arranca solo al estar listo
        a.alListo()
        m.foco.alPerder()  // una llamada entrante
        org.robolectric.shadows.ShadowLooper.idleMainLooper()
        assertTrue("al perder el foco el fragmento se pausa", !a.suena)
        m.r.parar()
        assertTrue("parar suelta el foco", !m.foco.tiene)
    }

    @Test
    fun `sin el foco de audio el fragmento no empieza a sonar`() {
        val m = montar(foco = FakeFoco(concede = false))
        m.r.tocar(item("1", deezer = 9), hueco(m))
        assertTrue(m.audios.isEmpty())
        assertNull(m.r.sonando())
    }

    // ── La caja del vídeo de YouTube: 16:9 a todo el ancho y nunca menos de 200 dp (spec). ──
    // Medido en el Samsung de pruebas: 1080 × 2340 a 480 dpi (3 px por dp); un WebView de 1008 px
    // mide 336 px CSS y un px CSS es un dp (la página del embed usa width=device-width).

    @Test fun `en vertical la caja es 16 a 9 del ancho`() {
        assertEquals(607, ReproductorFeed.altoCaja(1080, 2340, 3.0f))
        assertTrue(607 / 3.0f >= ReproductorFeed.MINIMO_DP)
    }

    @Test fun `en un movil estrecho el 16 a 9 no llega y manda el minimo de YouTube`() {
        // 320 dp de ancho: el 16:9 serían 180 dp.
        assertEquals(400, ReproductorFeed.altoCaja(640, 1280, 2.0f))
    }

    @Test fun `en horizontal sube a 200 dp si el 55 por ciento de la pantalla se queda corto`() {
        // 594 px (198 dp) → 600 px = 200 dp.
        assertEquals(600, ReproductorFeed.altoCaja(2340, 1080, 3.0f))
    }

    // ── A9: la tarjeta de Spotify ES el reproductor de Spotify (un toque). ──

    @Test fun `que pinta la tarjeta segun la fuente`() {
        assertEquals(ReproductorFeed.TarjetaFeed.SPOTIFY_EMBED, ReproductorFeed.tarjeta(item("1", spotify = sp, youtube = yt, deezer = 5)))
        assertEquals(ReproductorFeed.TarjetaFeed.YOUTUBE_AL_TOCAR, ReproductorFeed.tarjeta(item("1", youtube = yt, deezer = 5)))
        assertEquals(ReproductorFeed.TarjetaFeed.DEEZER_NATIVA, ReproductorFeed.tarjeta(item("1", deezer = 5)))
        assertEquals(ReproductorFeed.TarjetaFeed.SIN_FUENTE, ReproductorFeed.tarjeta(item("1")))
        // Un enlace de Spotify que no es una pista no hace tarjeta de Spotify: cae a lo siguiente.
        assertEquals(ReproductorFeed.TarjetaFeed.DEEZER_NATIVA, ReproductorFeed.tarjeta(item("1", spotify = "https://open.spotify.com/track/x", deezer = 5)))
    }

    @Test fun `montar spotify pone un EmbedView en el contenedor y soltarlo lo quita`() {
        val m = montar()
        val c = FrameLayout(m.act)
        assertTrue(m.r.montarSpotify(item("1", spotify = sp), c) {})
        assertEquals(1, embeds(c).size)
        assertEquals(1, m.r.spotifysMontados())
        m.r.soltarSpotify(c)
        assertEquals(0, c.childCount)
        assertEquals(0, m.r.spotifysMontados())
        // Sin pista de Spotify no se monta nada.
        assertTrue(!m.r.montarSpotify(item("2", deezer = 3), c) {})
        assertEquals(0, c.childCount)
    }

    @Test fun `tocar spotify no monta nada en el hueco`() {
        val m = montar()
        val h = hueco(m)
        m.r.tocar(item("1", spotify = sp), h)
        assertEquals(0, h.childCount)
        assertNull(m.r.sonando())
    }

    @Test fun `cuando suena un spotify se pausan los demas y se para el fragmento`() {
        val m = montar()
        val a = FrameLayout(m.act); val b = FrameLayout(m.act)
        m.r.montarSpotify(item("A", spotify = sp), a) {}
        m.r.montarSpotify(item("B", spotify = sp), b) {}
        val ea = embeds(a)[0]; val eb = embeds(b)[0]
        // Suena un fragmento de Deezer...
        val h = hueco(m)
        m.r.tocar(item("D", deezer = 9), h)
        assertEquals("D", m.r.sonando())
        // ...y el usuario da al play del Spotify A: el fragmento se suelta. B no sonaba: no se le
        // toca (pausar desde fuera recarga la pista, ver EmbedView.pausarSpotify).
        ea.alSonar!!.invoke(true)
        assertTrue(m.audios[0].soltado)
        assertEquals(0, h.childCount)
        assertEquals("A", m.r.sonando())
        assertTrue(m.pausados.isEmpty())
        // Ahora suena B: A recibe la pausa, y B no.
        eb.alSonar!!.invoke(true)
        assertEquals("B", m.r.sonando())
        assertEquals(listOf(ea), m.pausados)
        // B se pausa desde su propio botón: ya no suena nada.
        eb.alSonar!!.invoke(false)
        assertNull(m.r.sonando())
    }

    @Test fun `parar pausa los spotify y tocar un youtube tambien`() {
        val m = montar()
        val a = FrameLayout(m.act)
        m.r.montarSpotify(item("A", spotify = sp), a) {}
        val ea = embeds(a)[0]
        ea.alSonar!!.invoke(true)
        m.r.parar()
        assertTrue(ea in m.pausados)
        assertNull(m.r.sonando())
        m.pausados.clear()
        ea.alSonar!!.invoke(true)
        m.r.tocar(item("Y", youtube = yt), hueco(m))
        assertTrue(ea in m.pausados)
        assertEquals("Y", m.r.sonando())
    }

    @Test fun `la tarjeta de spotify que sale de la pantalla se suelta sin recargar la pista por JS`() {
        val m = montar()
        val a = FrameLayout(m.act)
        val marcador = ArrayList<Boolean>()
        m.r.montarSpotify(item("A", spotify = sp), a) { marcador.add(it) }
        val ea = embeds(a)[0]
        ea.alSonar!!.invoke(true)
        m.r.pararSi("B")
        org.robolectric.shadows.ShadowLooper.idleMainLooper()
        assertTrue(m.pausados.isEmpty() && m.recargados.isEmpty())
        m.r.pararSi("A")
        assertNull(m.r.sonando())
        // En el frame siguiente (pararSi llega durante el layout de la lista).
        org.robolectric.shadows.ShadowLooper.idleMainLooper()
        assertTrue("no se pausa por JS: eso recarga la pista con red", m.pausados.isEmpty())
        assertEquals(listOf(ea), m.recargados)
        assertEquals("el marcador vuelve hasta que el nuevo se vea", listOf(true), marcador)
    }

    @Test fun `un spotify a pelo solo se calla si el usuario lo toco`() {
        val m = montar()
        val a = FrameLayout(m.act); val b = FrameLayout(m.act)
        m.r.montarSpotify(item("A", spotify = sp), a) {}
        m.r.montarSpotify(item("B", spotify = sp), b) {}
        val ea = embeds(a)[0]; val eb = embeds(b)[0]
        ea.enPlano = true; ea.enUso = true  // a pelo y tocado: puede estar sonando
        eb.enPlano = true                    // a pelo sin tocar: seguro que no
        m.r.parar()
        assertEquals(listOf(ea), m.pausados)
        // Y cuando suena otro Spotify con la API, también se calla al tocado.
        ea.enUso = true
        m.pausados.clear()
        val c = FrameLayout(m.act)
        m.r.montarSpotify(item("C", spotify = sp), c) {}
        embeds(c)[0].alSonar!!.invoke(true)
        assertEquals(listOf(ea), m.pausados)
    }

    @Test fun `la pagina de la tarjeta tiene plan B si la API de Spotify no carga`() {
        val m = montar()
        val e = EmbedView(m.act).apply { tarjetaSpotify = true }
        val html = e.paginaParaTest(EmbedSpec("spotify", sp, "4uLU6hMCjMI75M1A2tKUQC"))
        assertTrue("onerror del script de la API", html.contains("iframe-api/v1\" async onerror=\"plano()\""))
        assertTrue("tope de espera al ready", html.contains("setTimeout(function(){ if(!listo) plano(); }, 8000)"))
        assertTrue("el plan B es el reproductor a pelo de la misma pista",
            html.contains("https://open.spotify.com/embed/track/4uLU6hMCjMI75M1A2tKUQC"))
        assertTrue("y avisa de que ya se ve", html.contains("AndroidEmbed.onPlano()") && html.contains("AndroidEmbed.onListo()"))
    }

    @Test fun `soltar todo libera los spotify montados`() {
        val m = montar()
        val a = FrameLayout(m.act); val b = FrameLayout(m.act)
        m.r.montarSpotify(item("A", spotify = sp), a) {}
        m.r.montarSpotify(item("B", spotify = sp), b) {}
        m.r.soltarTodo()
        assertEquals(0, m.r.spotifysMontados())
        assertEquals(0, a.childCount + b.childCount)
    }
}
