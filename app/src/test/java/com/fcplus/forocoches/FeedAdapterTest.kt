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
            alCorazon = { cor.add(it.id) }, alPlay = { _, _ -> }, alGuardar = {}, alMenu = { _, _ -> },
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

    @Test fun `guardar oculto sin spotify ni youtube`() {
        val m = montar()
        m.ad.poner(listOf(item("1", spotify = null, youtube = null), item("2")), ahora)
        m.medir()
        assertEquals(View.GONE, m.fila(0).findViewById<View>(R.id.pub_guardar).visibility)
        assertEquals(View.VISIBLE, m.fila(1).findViewById<View>(R.id.pub_guardar).visibility)
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
        val ad = FeedAdapter({}, { _, _ -> }, {}, { _, _ -> }, {}, alSalirDePantalla = { salidas.add(it) })
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
}
