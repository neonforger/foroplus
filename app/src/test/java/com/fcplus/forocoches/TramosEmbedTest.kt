package com.fcplus.forocoches

import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.StyleSpan
import androidx.core.text.HtmlCompat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Los reproductores en su sitio: el texto del mensaje se parte por las marcas que deja
 * `extractor.js` (ver [TramosEmbed]).
 */
@RunWith(RobolectricTestRunner::class)
class TramosEmbedTest {

    private fun m(n: Int) = "${TramosEmbed.ABRE}$n${TramosEmbed.CIERRA}"

    @Test
    fun `el caso de Juan - texto, video, texto, video`() {
        val p = TramosEmbed.partir("A ver, que lo sabemos todos pero...\n\n${m(0)}\n\nY de propina\n\n${m(1)}\n\n", 2)
        assertEquals("A ver, que lo sabemos todos pero...", p.cabeza.toString())
        assertEquals(listOf(0, 1), p.tras.keys.toList())
        assertEquals("Y de propina", p.tras[0].toString())
        // Lo que queda tras el último son solo saltos: vacío, y el adaptador lo esconde.
        assertEquals("", p.tras[1].toString())
    }

    @Test
    fun `sin marcas el texto se queda tal cual`() {
        val p = TramosEmbed.partir("hola\n", 0)
        assertEquals("hola\n", p.cabeza.toString())
        assertTrue(p.tras.isEmpty())
    }

    @Test
    fun `un mensaje que es solo un video deja la cabeza vacia`() {
        val p = TramosEmbed.partir("\n${m(0)}\n", 1)
        assertEquals("", p.cabeza.toString())
        assertEquals("", p.tras[0].toString())
    }

    @Test
    fun `el orden es el del texto, no el de la lista de embeds`() {
        // collectEmbeds recoge por tipos (tweets antes que YouTube), así que el índice no
        // dice dónde va: lo dice la marca.
        val p = TramosEmbed.partir("uno ${m(1)} dos ${m(0)} tres", 2)
        assertEquals(listOf(1, 0), p.tras.keys.toList())
        assertEquals("uno", p.cabeza.toString())
        assertEquals("dos", p.tras[1].toString())
        assertEquals("tres", p.tras[0].toString())
    }

    @Test
    fun `una marca de un embed que no existe o repetida se borra sin partir`() {
        val p = TramosEmbed.partir("a ${m(7)}b ${m(0)} c ${m(0)}d", 1)
        assertEquals("a b", p.cabeza.toString())
        assertEquals("c d", p.tras[0].toString())
    }

    @Test
    fun `los estilos sobreviven al corte`() {
        val sb = SpannableStringBuilder("negrita ${m(0)} otra")
        sb.setSpan(StyleSpan(android.graphics.Typeface.BOLD), 0, 7, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        sb.setSpan(StyleSpan(android.graphics.Typeface.BOLD), sb.length - 4, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        val p = TramosEmbed.partir(sb, 1)
        assertEquals(1, (p.cabeza as Spanned).getSpans(0, p.cabeza.length, StyleSpan::class.java).size)
        val tras = p.tras[0] as Spanned
        assertEquals(1, tras.getSpans(0, tras.length, StyleSpan::class.java).size)
    }

    @Test
    fun `las marcas sobreviven a fromHtml`() {
        val html = "texto<div style=\"margin-top: 8px;\">${m(0)}</div>Y de propina"
        val texto = HtmlCompat.fromHtml(html, HtmlCompat.FROM_HTML_MODE_LEGACY)
        val p = TramosEmbed.partir(texto, 1)
        assertEquals("texto", p.cabeza.toString())
        assertEquals("Y de propina", p.tras[0].toString().trimEnd())
    }

    @Test
    fun `orden pone delante los marcados y detras los que no tienen marca`() {
        assertEquals(listOf(2, 0, 1), TramosEmbed.orden("x ${m(2)} y ${m(0)}", 3))
        assertEquals(listOf(0, 1), TramosEmbed.orden("guardado sin marcas", 2))
        assertEquals(listOf(0), TramosEmbed.orden("${m(5)}${m(0)}", 1))
    }

    @Test
    fun `sinMarcas limpia el texto que se publica al citar`() {
        assertEquals("a  b", TramosEmbed.sinMarcas("a ${m(0)} b"))
    }
}
