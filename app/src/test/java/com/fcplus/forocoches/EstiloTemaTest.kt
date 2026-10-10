package com.fcplus.forocoches

import android.content.Context
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * El estilo Tarjetas vive en atributos de tema (EstiloApp, attrs.xml). Este test fija las DOS
 * cosas que importan: que la Compacta sigue dando exactamente los valores de antes (si no, la
 * app de siempre cambia sin que nadie lo pida) y que el overlay de Tarjetas de verdad se aplica.
 *
 * Que los colores nuevos existan también en values-night ya lo vigila ColoresFijosTest.
 */
@RunWith(RobolectricTestRunner::class)
class EstiloTemaTest {

    private val app: Context = ApplicationProvider.getApplicationContext()
    private fun px(dp: Int) = (dp * app.resources.displayMetrics.density + 0.5f).toInt()

    private fun ctx(tarjetas: Boolean): Context {
        val c = ContextThemeWrapper(app, R.style.Theme_FCForocoches)
        if (tarjetas) c.theme.applyStyle(R.style.ThemeOverlay_FC_Tarjetas, true)
        return c
    }

    /** Infla como lo hace el adaptador: con un RecyclerView de padre, para que lleve márgenes. */
    private fun inflar(layout: Int, tarjetas: Boolean): View {
        val c = ctx(tarjetas)
        val padre = RecyclerView(c).apply { layoutManager = LinearLayoutManager(c) }
        return LayoutInflater.from(c).inflate(layout, padre, false)
    }

    private fun margenes(v: View) = (v.layoutParams as ViewGroup.MarginLayoutParams)
        .let { listOf(it.leftMargin, it.topMargin, it.rightMargin, it.bottomMargin) }

    @Test
    fun `fila de hilo compacta como siempre`() {
        val v = inflar(R.layout.item_thread, tarjetas = false)
        assertEquals(listOf(0, 0, 0, 0), margenes(v))
        assertEquals(px(1), v.findViewById<View>(R.id.thread_divider).layoutParams.height)
        assertEquals(px(30), v.findViewById<View>(R.id.thread_sobre_caja).layoutParams.width)
        assertEquals(px(3), v.findViewById<View>(R.id.thread_sobre).paddingLeft)
    }

    @Test
    fun `fila de hilo en tarjetas`() {
        val v = inflar(R.layout.item_thread, tarjetas = true)
        assertEquals(listOf(px(12), px(5), px(12), px(5)), margenes(v))
        assertEquals(0, v.findViewById<View>(R.id.thread_divider).layoutParams.height)
        assertEquals(px(40), v.findViewById<View>(R.id.thread_sobre_caja).layoutParams.width)
        assertNotNull(v.background)
        assertNotNull(AtributosTema.drawable(ctx(true), R.attr.fcSobreFondoNuevo))
    }

    @Test
    fun `fila de aviso compacta como siempre`() {
        val v = inflar(R.layout.item_notice, tarjetas = false)
        assertEquals(listOf(0, 0, 0, 0), margenes(v))
        assertEquals(null, v.background)
        assertEquals(false, AtributosTema.bool(ctx(false), R.attr.fcRecortarFila))
    }

    @Test
    fun `fila de privado compacta como siempre`() {
        val v = inflar(R.layout.item_pm, tarjetas = false)
        assertEquals(listOf(0, 0, 0, 0), margenes(v))
    }

    @Test
    fun `fila de aviso y de privado en tarjetas`() {
        for (layout in listOf(R.layout.item_notice, R.layout.item_pm)) {
            val v = inflar(layout, tarjetas = true)
            assertEquals(listOf(px(12), px(5), px(12), px(5)), margenes(v))
            assertNotNull(v.background)
        }
        assertEquals(true, AtributosTema.bool(ctx(true), R.attr.fcRecortarFila))
    }

    @Test
    fun `mensaje compacto como siempre`() {
        val v = inflar(R.layout.item_post, tarjetas = false)
        assertEquals(px(6), v.paddingTop)
        assertEquals(px(1), v.findViewById<View>(R.id.post_divider).layoutParams.height)
        val m = PostAdapter.marco(ctx(false))
        assertEquals(0, m.margenH)
        assertEquals(0, m.margenV)
        assertEquals(px(8), m.opMargenH)
        assertEquals(px(4), m.opMargenV)
        assertEquals(R.drawable.bg_post_op, m.op)
        assertEquals(R.drawable.bg_post_op_highlight, m.opResaltado)
        assertEquals(R.color.fc_resaltado, m.resaltado)
    }

    @Test
    fun `mensaje en tarjetas`() {
        val v = inflar(R.layout.item_post, tarjetas = true)
        assertEquals(px(12), v.paddingTop)
        assertEquals(0, v.findViewById<View>(R.id.post_divider).layoutParams.height)
        val m = PostAdapter.marco(ctx(true))
        assertEquals(px(12), m.margenH)
        assertEquals(px(5), m.margenV)
        assertEquals(R.drawable.bg_tarjeta, m.fondo)
        assertEquals(R.drawable.bg_tarjeta_op, m.op)
        assertEquals(R.drawable.bg_tarjeta_resaltada, m.resaltado)
    }

    @Test
    fun `cuadrito del sobre solo en tarjetas`() {
        assertEquals(null, AtributosTema.drawable(ctx(false), R.attr.fcSobreFondoNuevo))
        assertEquals(null, AtributosTema.drawable(ctx(false), R.attr.fcSobreFondoLeido))
    }
}
