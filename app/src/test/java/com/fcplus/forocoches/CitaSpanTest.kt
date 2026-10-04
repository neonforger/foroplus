package com.fcplus.forocoches

import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.QuoteSpan
import androidx.core.text.HtmlCompat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * El adorno de las citas dentro de un mensaje.
 *
 * Lo reportaron tres testers el 2026-08-22: en modo oscuro no se distingue qué parte de un post
 * es cita. La causa está en el primer test: el `QuoteSpan` que monta `Html.fromHtml` pinta una
 * raya de un **azul fijo del framework**, que sobre el fondo `#121214` del modo oscuro no se ve.
 */
@RunWith(RobolectricTestRunner::class)
class CitaSpanTest {

    private fun citado(html: String) = SpannableStringBuilder(
        HtmlCompat.fromHtml(html, HtmlCompat.FROM_HTML_MODE_LEGACY)
    )

    private val barra = 0xFFC8102E.toInt()
    private val fondo = 0xFFF4F4F6.toInt()

    @Test
    fun `el QuoteSpan de fabrica es un azul fijo que no sabe nada de la paleta`() {
        // Esto es la causa raíz, no un detalle: el color viene escrito a mano dentro de Android
        // y el constructor que deja elegirlo es de API 28 (la app sale desde la 24).
        assertEquals(0xFF0000FF.toInt(), QuoteSpan().color)
    }

    @Test
    fun `un blockquote deja de llevar el span de fabrica y pasa a llevar el nuestro`() {
        val sb = citado("<p>hola</p><blockquote>lo que dijo el otro</blockquote><p>respuesta</p>")
        assertTrue(
            "fromHtml debería haber puesto un QuoteSpan de partida",
            sb.getSpans(0, sb.length, QuoteSpan::class.java).isNotEmpty()
        )

        CitaSpan.vestir(sb, barra, fondo, 3f)

        assertEquals(0, sb.getSpans(0, sb.length, QuoteSpan::class.java).size)
        assertEquals(1, sb.getSpans(0, sb.length, CitaSpan::class.java).size)
    }

    @Test
    fun `el tramo citado y sus flags de parrafo se conservan tal cual`() {
        val sb = citado("<p>hola</p><blockquote>lo que dijo el otro</blockquote><p>respuesta</p>")
        val original = sb.getSpans(0, sb.length, QuoteSpan::class.java).first()
        val ini = sb.getSpanStart(original)
        val fin = sb.getSpanEnd(original)
        val flags = sb.getSpanFlags(original)

        CitaSpan.vestir(sb, barra, fondo, 3f)

        val nuestro = sb.getSpans(0, sb.length, CitaSpan::class.java).first()
        assertEquals(ini, sb.getSpanStart(nuestro))
        assertEquals(fin, sb.getSpanEnd(nuestro))
        // Si los flags se cambiaran a EXCLUSIVE_EXCLUSIVE, el Layout se saltaría el adorno en
        // cuanto el tramo no empiece justo detrás de un salto de línea.
        // (La prioridad sí cambia, a propósito: ver el test de las viñetas.)
        val sinPrioridad = Spanned.SPAN_PRIORITY.inv()
        assertEquals(flags and sinPrioridad, sb.getSpanFlags(nuestro) and sinPrioridad)
        assertNotEquals(0, flags and Spanned.SPAN_PARAGRAPH)
    }

    @Test
    fun `una cita de varios parrafos se viste entera, span a span`() {
        val sb = citado("<blockquote><p>uno</p><p>dos</p><p>tres</p></blockquote><p>respuesta</p>")
        val cuantos = sb.getSpans(0, sb.length, QuoteSpan::class.java).size
        assertTrue("el fixture debería traer varios QuoteSpan", cuantos >= 1)

        CitaSpan.vestir(sb, barra, fondo, 3f)

        assertEquals(0, sb.getSpans(0, sb.length, QuoteSpan::class.java).size)
        assertEquals(cuantos, sb.getSpans(0, sb.length, CitaSpan::class.java).size)
    }

    @Test
    fun `un mensaje sin citas se queda exactamente igual`() {
        val sb = citado("<p>un mensaje normal y corriente</p>")
        val antes = sb.toString()

        CitaSpan.vestir(sb, barra, fondo, 3f)

        assertEquals(antes, sb.toString())
        assertEquals(0, sb.getSpans(0, sb.length, CitaSpan::class.java).size)
    }

    @Test
    fun `la barra nunca se queda por debajo de dos pixeles`() {
        // En un móvil de densidad baja, 3dp redondearían a 2 px o menos; por debajo de eso la
        // barra se pierde, que es justo el fallo que se está arreglando.
        val fino = CitaSpan(barra, fondo, 0.5f)
        assertTrue(fino.getLeadingMargin(true) >= 6)
    }

    @Test
    fun `en una cita con lista la raya va a la izquierda de las vinetas`() {
        // Juan, 2026-09-22: las viñetas salían FUERA de la raya. El Layout dibuja los márgenes
        // en el orden de getSpans, y la viñeta de fromHtml entraba antes que nuestra cita.
        val sb = citado("<blockquote>dijo:<ul><li>uno</li><li>dos</li></ul></blockquote><p>respuesta</p>")
        CitaSpan.vestir(sb, barra, fondo, 3f)
        val enUno = sb.indexOf("uno")
        val margenes = sb.getSpans(enUno, enUno + 1, android.text.style.LeadingMarginSpan::class.java)
        assertTrue("el fixture debería traer viñeta y cita", margenes.size >= 2)
        assertTrue(
            "la cita debe ir la primera, pero el orden es ${margenes.map { it.javaClass.simpleName }}",
            margenes.first() is CitaSpan
        )
    }
}
