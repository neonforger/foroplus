package com.fcplus.forocoches

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScrollThumbTest {

    private val recorrido = ScrollThumb.RANGE - ScrollThumb.EXTENT

    @Test
    fun `arriba del todo el pulgar esta en el origen`() {
        assertEquals(0, ScrollThumb.offset(realOffset = 0, realRange = 50_000, realExtent = 2_000))
    }

    @Test
    fun `abajo del todo el pulgar llega al final justo`() {
        // El máximo es RANGE-EXTENT: si se pasara, el pulgar se saldría del carril.
        assertEquals(recorrido, ScrollThumb.offset(48_000, 50_000, 2_000))
    }

    @Test
    fun `a mitad de recorrido el pulgar va a la mitad`() {
        assertEquals(recorrido / 2, ScrollThumb.offset(24_000, 50_000, 2_000))
    }

    @Test
    fun `si todo cabe en pantalla el pulgar no se mueve`() {
        assertEquals(0, ScrollThumb.offset(0, 1_000, 1_000))
        assertEquals(0, ScrollThumb.offset(0, 800, 1_000))   // extent mayor que range
    }

    @Test
    fun `valores fuera de rango no sacan el pulgar del carril`() {
        assertEquals(recorrido, ScrollThumb.offset(99_999, 50_000, 2_000))
        assertEquals(0, ScrollThumb.offset(-500, 50_000, 2_000))
    }

    @Test
    fun `el tamano del pulgar es constante, pase lo que pase con el contenido`() {
        // Es TODO el sentido de esto: el pulgar mide igual en una página de posts cortos que
        // en una llena de fotos, y no cambia cuando las imágenes cargan y crecen los posts.
        assertEquals(800, ScrollThumb.EXTENT)
        assertTrue(ScrollThumb.EXTENT < ScrollThumb.RANGE)
    }

    @Test
    fun `el pulgar avanza de forma monotona al bajar`() {
        var previo = -1
        for (o in 0..48_000 step 4_000) {
            val actual = ScrollThumb.offset(o, 50_000, 2_000)
            assertTrue("retrocede en offset=$o", actual >= previo)
            previo = actual
        }
    }
}
