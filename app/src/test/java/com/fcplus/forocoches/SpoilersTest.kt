package com.fcplus.forocoches

import android.text.SpannableStringBuilder
import androidx.core.text.HtmlCompat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * El marcado de los spoilers al pasar de HTML a texto nativo.
 *
 * El test que de verdad importa es el último: que las marcas **sobreviven a `Html.fromHtml`**.
 * El plan original era una etiqueta `<spoiler>`, y se cayó porque TagSoup ignora el cierre de
 * las etiquetas que no conoce y las anida (ver la explicación en [Spoilers]).
 */
@RunWith(RobolectricTestRunner::class)
class SpoilersTest {

    private val A = Spoilers.ABRE
    private val C = Spoilers.CIERRA

    private fun sb(s: String) = SpannableStringBuilder(s)

    private fun tramos(sb: SpannableStringBuilder) =
        sb.getSpans(0, sb.length, SpoilerSpan::class.java)
            .map { sb.getSpanStart(it) to sb.getSpanEnd(it) }
            .sortedBy { it.first }

    private fun ordenes(sb: SpannableStringBuilder) =
        sb.getSpans(0, sb.length, SpoilerSpan::class.java)
            .sortedBy { sb.getSpanStart(it) }
            .map { it.orden }

    private fun textos(sb: SpannableStringBuilder) =
        tramos(sb).map { sb.subSequence(it.first, it.second).toString() }

    @Test
    fun `un spoiler deja el tramo marcado y el texto sin marcas`() {
        val s = sb("antes ${A}secreto$C despues")
        assertEquals(1, Spoilers.marcar(s))
        assertEquals("antes secreto despues", s.toString())
        assertEquals(listOf("secreto"), textos(s))
    }

    @Test
    fun `varios spoilers en el mismo mensaje se marcan por separado`() {
        val s = sb("${A}uno$C y ${A}dos$C")
        assertEquals(2, Spoilers.marcar(s))
        assertEquals("uno y dos", s.toString())
        assertEquals(listOf("uno", "dos"), textos(s))
    }

    @Test
    fun `un spoiler dentro de otro se empareja por dentro, no cruzado`() {
        val s = sb("${A}fuera ${A}dentro$C fin$C")
        Spoilers.marcar(s)
        assertEquals("fuera dentro fin", s.toString())
        assertTrue(textos(s).contains("dentro"))
        assertTrue(textos(s).contains("fuera dentro fin"))
    }

    @Test
    fun `una marca suelta se limpia y no se queda dibujada`() {
        // Un mensaje cortado o un HTML raro no puede dejar una cajita vacía en mitad del texto.
        val s = sb("texto ${A}sin cerrar")
        assertEquals(0, Spoilers.marcar(s))
        assertEquals("texto sin cerrar", s.toString())
        assertFalse(s.toString().contains(A))
    }

    @Test
    fun `un cierre sin apertura tambien se limpia`() {
        val s = sb("texto ${C}suelto")
        assertEquals(0, Spoilers.marcar(s))
        assertEquals("texto suelto", s.toString())
        assertFalse(s.toString().contains(C))
    }

    @Test
    fun `cada spoiler lleva su numero de orden, para no confundir cual esta destapado`() {
        val s = sb("${A}uno$C y ${A}dos$C y ${A}tres$C")
        Spoilers.marcar(s)
        assertEquals(listOf(0, 1, 2), ordenes(s))
    }

    @Test
    fun `un mensaje sin spoilers se queda intacto`() {
        val s = sb("un mensaje normal")
        assertEquals(0, Spoilers.marcar(s))
        assertEquals("un mensaje normal", s.toString())
        assertEquals(0, tramos(s).size)
    }

    @Test
    fun `un spoiler vacio no deja un span degenerado`() {
        val s = sb("antes $A$C despues")
        Spoilers.marcar(s)
        assertEquals("antes  despues", s.toString())
        assertEquals(0, tramos(s).size)
    }

    @Test
    fun `las marcas sobreviven a fromHtml, que es de lo que depende todo esto`() {
        // Así llega el HTML del extractor: el <div> original (para conservar el corte de línea)
        // con las marcas alrededor del contenido.
        val html = "<p>uno</p><div>${A}el asesino es <b>el mayordomo</b>$C</div><p>dos</p>"
        val s = SpannableStringBuilder(
            HtmlCompat.fromHtml(html, HtmlCompat.FROM_HTML_MODE_LEGACY)
        )
        assertTrue("las marcas deben llegar enteras", s.contains(A) && s.contains(C))

        assertEquals(1, Spoilers.marcar(s))
        assertEquals(listOf("el asesino es el mayordomo"), textos(s))
        assertFalse(s.toString().contains(A))
        assertFalse(s.toString().contains(C))
        // Y el spoiler sigue empezando en su propia línea, porque el <div> se conserva.
        assertTrue(s[tramos(s).first().first - 1] == '\n')
    }
}
