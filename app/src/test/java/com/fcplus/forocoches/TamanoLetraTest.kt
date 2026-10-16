package com.fcplus.forocoches

import android.text.SpannableStringBuilder
import android.text.style.RelativeSizeSpan
import android.text.style.TypefaceSpan
import androidx.core.text.HtmlCompat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * El [SIZE] de FC, de punta a punta por el `Html.fromHtml` real: el extractor deja
 * `<font face="fcsize-N">` (marcarTamanos.test.js) y [TamanoLetra] lo cambia por un tamaño.
 */
@RunWith(RobolectricTestRunner::class)
class TamanoLetraTest {

    private fun render(html: String) =
        SpannableStringBuilder(HtmlCompat.fromHtml(html, HtmlCompat.FROM_HTML_MODE_LEGACY))
            .also { TamanoLetra.aplicar(it) }

    private fun tamanos(sb: SpannableStringBuilder) =
        sb.getSpans(0, sb.length, RelativeSizeSpan::class.java)
            .sortedBy { sb.getSpanStart(it) }
            .map { sb.subSequence(sb.getSpanStart(it), sb.getSpanEnd(it)).toString() to it.sizeChange }

    @Test
    fun `un SIZE grande agranda solo su tramo`() {
        val sb = render("antes <font face=\"fcsize-5\">grande</font> despues")
        assertEquals("antes grande despues", sb.toString())
        assertEquals(listOf("grande" to TamanoLetra.factor(5)), tamanos(sb))
        assertTrue(TamanoLetra.factor(5) > 1f)
    }

    @Test
    fun `el 2 es el tamano normal, que es el que pone por defecto el editor de FC`() {
        assertEquals(1f, TamanoLetra.factor(2))
        assertTrue(TamanoLetra.factor(1) < 1f)
        // De menor a mayor, sin saltos hacia atrás.
        val f = (1..7).map { TamanoLetra.factor(it) }
        assertEquals(f.sorted(), f)
    }

    @Test
    fun `los anidados se cierran donde toca`() {
        val sb = render("<font face=\"fcsize-6\">uno <font face=\"fcsize-1\">dos</font> tres</font> cuatro")
        assertEquals(
            listOf("uno dos tres" to TamanoLetra.factor(6), "dos" to TamanoLetra.factor(1)),
            tamanos(sb)
        )
    }

    @Test
    fun `no queda el TypefaceSpan inventado, y un face de verdad no se toca`() {
        val sb = render("<font face=\"fcsize-4\">a</font><font face=\"serif\">b</font>")
        val familias = sb.getSpans(0, sb.length, TypefaceSpan::class.java).map { it.family }
        assertEquals(listOf("serif"), familias)
        assertEquals(1, tamanos(sb).size)
    }

    @Test
    fun `fuera de rango no revienta`() {
        val sb = render("<font face=\"fcsize-9\">x</font><font face=\"fcsize-\">y</font>")
        assertEquals(listOf("x" to TamanoLetra.factor(7)), tamanos(sb))
    }
}
