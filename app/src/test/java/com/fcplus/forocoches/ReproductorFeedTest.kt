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
        override fun preparar(url: String, alListo: () -> Unit, alAcabar: () -> Unit, alFallar: () -> Unit) {
            this.url = url; this.alListo = alListo
        }
        override fun reanudar() { suena = true }
        override fun pausar() { suena = false }
        override fun sonando() = suena
        override fun posicion() = 0
        override fun duracion() = 30000
        override fun ir(ms: Int) {}
        override fun soltar() { soltado = true }
    }

    private class Montaje(val r: ReproductorFeed, val abiertas: MutableList<String>, val avisos: MutableList<String>,
                          val audios: MutableList<FakeAudio>, val act: AppCompatActivity)

    private fun montar(fragmento: (Long) -> String? = { "https://cdnt-preview.dzcdn.net/x.mp3" }): Montaje {
        val ctrl = Robolectric.buildActivity(AppCompatActivity::class.java)
        ctrl.get().setTheme(R.style.Theme_FCForocoches)
        ctrl.setup()
        val ab = ArrayList<String>(); val av = ArrayList<String>(); val au = ArrayList<FakeAudio>()
        val r = ReproductorFeed(
            ctrl.get(), { ab.add(it) }, fragmento, segundoPlano = { it() },
            crearAudio = { FakeAudio().also { a -> au.add(a) } }, avisar = { av.add(it) }
        )
        return Montaje(r, ab, av, au, ctrl.get())
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
    fun `tocar monta spotify y youtube en el hueco`() {
        val m = montar()
        val h = hueco(m)
        m.r.tocar(item("1", spotify = sp), h)
        assertEquals("1", m.r.sonando())
        assertEquals(1, embeds(h).size)
        assertEquals(View.VISIBLE, h.visibility)
        val h2 = hueco(m)
        m.r.tocar(item("2", youtube = yt), h2)
        assertEquals(1, embeds(h2).size)
        assertTrue(embeds(h2)[0].deMusica)
        val alto = embeds(h2)[0].layoutParams.height
        assertTrue(alto >= (ReproductorMusica.MINIMO_DP * m.act.resources.displayMetrics.density).toInt())
    }

    @Test
    fun `tocar otra para la anterior`() {
        val m = montar()
        val h1 = hueco(m); val h2 = hueco(m)
        m.r.tocar(item("1", spotify = sp), h1)
        m.r.tocar(item("2", deezer = 7), h2)
        assertEquals("2", m.r.sonando())
        assertEquals(0, h1.childCount)
        assertEquals(View.GONE, h1.visibility)
        // y el audio de la nueva suena; al volver a otra se suelta
        val h3 = hueco(m)
        m.r.tocar(item("3", spotify = sp), h3)
        assertTrue(m.audios[0].soltado)
    }

    @Test
    fun `pararSi de otra no para`() {
        val m = montar()
        val h = hueco(m)
        m.r.tocar(item("1", spotify = sp), h)
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
    fun `entera en spotify con id abre la pista`() {
        val m = montar()
        val h = hueco(m)
        m.r.tocar(item("1", spotify = sp), h)
        todas(h).filterIsInstance<android.widget.TextView>().first { it.text == "Entera en Spotify" }.performClick()
        assertEquals(listOf("https://open.spotify.com/track/4uLU6hMCjMI75M1A2tKUQC"), m.abiertas)
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
}
