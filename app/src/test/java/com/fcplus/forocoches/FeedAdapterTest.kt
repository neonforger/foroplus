package com.fcplus.forocoches

import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class FeedAdapterTest {
    private val ahora = 10_000_000L

    private fun item(
        id: String, corazones: Int = 3, corazon: Boolean = false, mia: Boolean = false,
        spotify: String? = "https://open.spotify.com/track/x", youtube: String? = null
    ) = ItemFeed(
        id, "YoMismo", "Para el domingo $id", ahora - 300_000, ahora + 22 * 3_600_000L, corazones,
        corazon, mia, CancionFeed("c$id", "Titulo $id", "Artista", null, spotify, youtube, 1L)
    )

    private class Montaje(
        val rv: RecyclerView, val ad: FeedAdapter,
        val corazones: MutableList<String>, val autor: MutableList<String>
    )

    private fun montar(): Montaje {
        val ctrl = Robolectric.buildActivity(AppCompatActivity::class.java)
        ctrl.get().setTheme(R.style.Theme_FCForocoches)
        ctrl.setup()
        val a = ctrl.get()
        val cor = ArrayList<String>()
        val aut = ArrayList<String>()
        val ad = FeedAdapter(
            alCorazon = { cor.add(it.id) }, alPlay = { _, _ -> }, alMenu = { _, _ -> },
            alAutorCorazones = { aut.add(it.id) }
        )
        val rv = RecyclerView(a)
        rv.layoutManager = LinearLayoutManager(a)
        rv.adapter = ad
        a.setContentView(rv)
        return Montaje(rv, ad, cor, aut)
    }

    private fun Montaje.medir() {
        rv.measure(
            View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(4000, View.MeasureSpec.EXACTLY)
        )
        rv.layout(0, 0, 1080, 4000)
    }

    private fun Montaje.fila(i: Int): View = rv.findViewHolderForAdapterPosition(i)!!.itemView

    @Test fun `pinta autor, frase y corazones`() {
        val m = montar()
        m.ad.poner(listOf(item("1", corazones = 7)), ahora)
        m.medir()
        val f = m.fila(0)
        assertTrue(f.findViewById<TextView>(R.id.pub_autor).text.contains("YoMismo"))
        assertEquals("Para el domingo 1", f.findViewById<TextView>(R.id.pub_texto).text.toString())
        assertEquals("7", f.findViewById<TextView>(R.id.pub_corazones).text.toString())
        assertEquals("hace 5 min", f.findViewById<TextView>(R.id.pub_hace).text.toString())
        assertEquals("22 h", f.findViewById<TextView>(R.id.pub_queda).text.toString())
        assertEquals("Titulo 1", f.findViewById<TextView>(R.id.pub_titulo).text.toString())
        assertNotNull(f.findViewById<View>(R.id.pub_hueco))
    }

    @Test fun `corazon lleno si corazon es true`() {
        val m = montar()
        m.ad.poner(listOf(item("1", corazon = true), item("2", corazon = false)), ahora)
        m.medir()
        val lleno = m.fila(0).findViewById<ImageView>(R.id.pub_corazon_ico).tag
        val vacio = m.fila(1).findViewById<ImageView>(R.id.pub_corazon_ico).tag
        assertEquals(R.drawable.ic_corazon_on, lleno)
        assertEquals(R.drawable.ic_corazon, vacio)
    }

    @Test fun `cambiarCorazon actualiza numero y estado`() {
        val m = montar()
        m.ad.poner(listOf(item("1", corazones = 3)), ahora)
        m.medir()
        m.ad.cambiarCorazon("1", EstadoCorazon(4, true))
        m.medir()
        val f = m.fila(0)
        assertEquals("4", f.findViewById<TextView>(R.id.pub_corazones).text.toString())
        assertEquals(R.drawable.ic_corazon_on, f.findViewById<ImageView>(R.id.pub_corazon_ico).tag)
    }

    private fun destacada() = MasQuerida(
        "2026-W45", "Pepe", "La de la semana", 12,
        CancionFeed("top", "Titulo top", "Artista", null, "https://open.spotify.com/track/x", null, 1L)
    ).comoItem()

    @Test fun `la destacada lleva su etiqueta y no enseña hace, caduca ni menu`() {
        val m = montar()
        m.ad.poner(listOf(destacada(), item("2")), ahora)
        m.medir()
        val d = m.fila(0)
        assertEquals(View.VISIBLE, d.findViewById<View>(R.id.pub_etiqueta).visibility)
        assertEquals("Top de la semana pasada", d.findViewById<TextView>(R.id.pub_etiqueta).text.toString())
        assertEquals(View.GONE, d.findViewById<View>(R.id.pub_hace).visibility)
        assertEquals(View.GONE, d.findViewById<View>(R.id.pub_queda).visibility)
        assertEquals(View.GONE, d.findViewById<View>(R.id.pub_menu).visibility)
        assertEquals("12", d.findViewById<TextView>(R.id.pub_corazones).text.toString())
        // Una normal no arrastra nada de lo de la destacada.
        val n = m.fila(1)
        assertEquals(View.GONE, n.findViewById<View>(R.id.pub_etiqueta).visibility)
        assertEquals(View.VISIBLE, n.findViewById<View>(R.id.pub_menu).visibility)
        assertEquals(View.VISIBLE, n.findViewById<View>(R.id.pub_queda).visibility)
    }

    @Test fun `la destacada no da corazon ni abre quien ni menu`() {
        val menus = ArrayList<String>()
        val ctrl = Robolectric.buildActivity(AppCompatActivity::class.java)
        ctrl.get().setTheme(R.style.Theme_FCForocoches)
        ctrl.setup()
        val cor = ArrayList<String>(); val aut = ArrayList<String>()
        val ad = FeedAdapter({ cor.add(it.id) }, { _, _ -> }, { it, _ -> menus.add(it.id) }, { aut.add(it.id) })
        val rv = RecyclerView(ctrl.get())
        rv.layoutManager = LinearLayoutManager(ctrl.get())
        rv.adapter = ad
        ctrl.get().setContentView(rv)
        ad.poner(listOf(destacada()), ahora)
        rv.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(4000, View.MeasureSpec.EXACTLY))
        rv.layout(0, 0, 1080, 4000)
        val f = rv.findViewHolderForAdapterPosition(0)!!.itemView
        f.findViewById<View>(R.id.pub_corazon_ico).performClick()
        f.findViewById<View>(R.id.pub_corazones).performClick()
        f.findViewById<View>(R.id.pub_menu).performClick()
        assertTrue(cor.isEmpty() && aut.isEmpty() && menus.isEmpty())
    }

    @Test fun `quitar elimina la fila`() {
        val m = montar()
        m.ad.poner(listOf(item("1"), item("2")), ahora)
        m.ad.quitar("1")
        m.medir()
        assertEquals(1, m.ad.itemCount)
        assertEquals("Para el domingo 2", m.fila(0).findViewById<TextView>(R.id.pub_texto).text.toString())
    }

    @Test fun `tocar el numero en una publicacion mia avisa al autor`() {
        val m = montar()
        m.ad.poner(listOf(item("1", mia = true), item("2", mia = false)), ahora)
        m.medir()
        m.fila(0).findViewById<View>(R.id.pub_corazones).performClick()
        m.fila(1).findViewById<View>(R.id.pub_corazones).performClick()
        assertEquals(listOf("1"), m.autor)
        assertEquals(listOf("2"), m.corazones)
    }

    private fun Montaje.montarReproductor(i: Int): View {
        val dummy = View(rv.context)
        m_hueco(i).addView(dummy)
        return dummy
    }

    private fun Montaje.m_hueco(i: Int) = fila(i).findViewById<android.widget.FrameLayout>(R.id.pub_hueco)

    @Test fun `cambiarCorazon no desmonta el reproductor del hueco`() {
        val m = montar()
        m.ad.poner(listOf(item("1")), ahora)
        m.medir()
        val dummy = m.montarReproductor(0)
        m.ad.cambiarCorazon("1", EstadoCorazon(4, true))
        m.medir()
        assertEquals(m.m_hueco(0), dummy.parent)
    }

    @Test fun `poner la misma lista con otro ahora mantiene el hueco y actualiza queda`() {
        val m = montar()
        m.ad.poner(listOf(item("1")), ahora)
        m.medir()
        val dummy = m.montarReproductor(0)
        m.ad.poner(listOf(item("1")), ahora + 3_600_000L)
        m.medir()
        assertEquals(m.m_hueco(0), dummy.parent)
        assertEquals("21 h", m.fila(0).findViewById<TextView>(R.id.pub_queda).text.toString())
    }

    @Test fun `poner sin una publicacion la quita`() {
        val m = montar()
        m.ad.poner(listOf(item("1"), item("2")), ahora)
        m.ad.poner(listOf(item("2")), ahora)
        m.medir()
        assertEquals(1, m.ad.itemCount)
        assertEquals("Para el domingo 2", m.fila(0).findViewById<TextView>(R.id.pub_texto).text.toString())
    }

    @Test fun `salir de la ventana avisa con el id`() {
        val salidas = ArrayList<String>()
        val ctrl = Robolectric.buildActivity(AppCompatActivity::class.java)
        ctrl.get().setTheme(R.style.Theme_FCForocoches)
        ctrl.setup()
        val ad = FeedAdapter({}, { _, _ -> }, { _, _ -> }, {}, alSalirDePantalla = { salidas.add(it) })
        val rv = RecyclerView(ctrl.get())
        rv.layoutManager = LinearLayoutManager(ctrl.get())
        rv.adapter = ad
        ctrl.get().setContentView(rv)
        ad.poner(listOf(item("7")), ahora)
        rv.measure(
            View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(4000, View.MeasureSpec.EXACTLY)
        )
        rv.layout(0, 0, 1080, 4000)
        ad.onViewDetachedFromWindow(rv.findViewHolderForAdapterPosition(0) as FeedAdapter.Fila)
        assertEquals(listOf("7"), salidas)
    }

    // ── A9: con Spotify, la tarjeta es el reproductor de Spotify (sin nuestro play). ──

    private val pista = "https://open.spotify.com/track/4uLU6hMCjMI75M1A2tKUQC"

    private class ConSpotify(
        val rv: RecyclerView, val ad: FeedAdapter,
        val montados: MutableList<String>, val soltados: MutableList<android.widget.FrameLayout>,
        val listos: MutableList<() -> Unit>
    )

    private fun montarConSpotify(): ConSpotify {
        val ctrl = Robolectric.buildActivity(AppCompatActivity::class.java)
        ctrl.get().setTheme(R.style.Theme_FCForocoches)
        ctrl.setup()
        val a = ctrl.get()
        val mon = ArrayList<String>(); val sol = ArrayList<android.widget.FrameLayout>()
        val lis = ArrayList<() -> Unit>()
        val ad = FeedAdapter(
            alCorazon = {}, alPlay = { _, _ -> }, alMenu = { _, _ -> }, alAutorCorazones = {},
            montarSpotify = { item, c, alListo -> mon.add(item.id); c.addView(View(a)); lis.add(alListo) },
            soltarSpotify = { c -> sol.add(c); c.removeAllViews() }
        )
        val rv = RecyclerView(a)
        rv.layoutManager = LinearLayoutManager(a)
        rv.adapter = ad
        a.setContentView(rv)
        return ConSpotify(rv, ad, mon, sol, lis)
    }

    private fun ConSpotify.medir() {
        rv.measure(
            View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(4000, View.MeasureSpec.EXACTLY)
        )
        rv.layout(0, 0, 1080, 4000)
    }

    private fun ConSpotify.fila(i: Int): View = rv.findViewHolderForAdapterPosition(i)!!.itemView

    @Test fun `con spotify no hay play nuestro y se monta el reproductor de spotify`() {
        val m = montarConSpotify()
        m.ad.poner(listOf(item("1", spotify = pista), item("2", spotify = null, youtube = "https://www.youtube.com/watch?v=dQw4w9WgXcQ")), ahora)
        m.medir()
        val s = m.fila(0)
        assertEquals(View.GONE, s.findViewById<View>(R.id.pub_play).visibility)
        val c = s.findViewById<android.widget.FrameLayout>(R.id.pub_spotify)
        assertEquals(View.VISIBLE, c.visibility)
        assertEquals(1, c.childCount)
        assertEquals(listOf("1"), m.montados)
        // El marcador (título y artista) se ve mientras carga.
        assertEquals(View.VISIBLE, s.findViewById<View>(R.id.pub_titulo).visibility)
        // La de YouTube sigue como hoy: play nuestro y sin reproductor de Spotify.
        val y = m.fila(1)
        assertEquals(View.VISIBLE, y.findViewById<View>(R.id.pub_play).visibility)
        assertEquals(View.GONE, y.findViewById<View>(R.id.pub_spotify).visibility)
    }

    @Test fun `al cargar el reproductor de spotify el marcador se esconde sin cambiar de alto`() {
        val m = montarConSpotify()
        m.ad.poner(listOf(item("1", spotify = pista)), ahora)
        m.medir()
        val f = m.fila(0)
        val alto = f.findViewById<View>(R.id.pub_marcador).height
        m.listos[0]()
        m.medir()
        assertEquals(View.INVISIBLE, f.findViewById<View>(R.id.pub_marcador).visibility)
        assertEquals(alto, f.findViewById<View>(R.id.pub_marcador).height)
    }

    @Test fun `un corazon o un refresco no desmontan el reproductor de spotify`() {
        val m = montarConSpotify()
        m.ad.poner(listOf(item("1", spotify = pista)), ahora)
        m.medir()
        val c = m.fila(0).findViewById<android.widget.FrameLayout>(R.id.pub_spotify)
        val hijo = c.getChildAt(0)
        m.ad.cambiarCorazon("1", EstadoCorazon(4, true))
        m.medir()
        m.ad.poner(listOf(item("1", spotify = pista)), ahora + 60_000L)
        m.medir()
        assertEquals(c, hijo.parent)
        assertEquals(listOf("1"), m.montados)
        assertTrue(m.soltados.isEmpty())
    }

    @Test fun `reciclar la fila suelta el reproductor de spotify`() {
        val m = montarConSpotify()
        m.ad.poner(listOf(item("1", spotify = pista)), ahora)
        m.medir()
        val h = m.rv.findViewHolderForAdapterPosition(0) as FeedAdapter.Fila
        val c = h.itemView.findViewById<android.widget.FrameLayout>(R.id.pub_spotify)
        m.ad.onViewRecycled(h)
        assertEquals(listOf(c), m.soltados)
        assertEquals(0, c.childCount)
        assertEquals(View.GONE, c.visibility)
        assertEquals(View.VISIBLE, h.itemView.findViewById<View>(R.id.pub_marcador).visibility)
    }

    @Test fun `la destacada con spotify tambien es el reproductor de spotify`() {
        val m = montarConSpotify()
        val d = MasQuerida(
            "2026-W45", "Pepe", "La de la semana", 12,
            CancionFeed("top", "Titulo top", "Artista", null, pista, null, 1L)
        ).comoItem()
        m.ad.poner(listOf(d), ahora)
        m.medir()
        assertEquals(View.GONE, m.fila(0).findViewById<View>(R.id.pub_play).visibility)
        assertEquals(listOf(d.id), m.montados)
    }
}
