package com.fcplus.forocoches

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VigiaVerificacionTest {
    private fun vigia() = VigiaVerificacion(inicio = 0L)

    @Test fun `al empezar la tapa esta puesta`() = assertTrue(vigia().tapaPuesta(0L))

    @Test fun `con el challenge a la vista se quita para que se pueda tocar la casilla`() {
        val v = vigia()
        val p = v.siguientePregunta(250L)!!
        v.respuesta(p, 300L, cf = true, form = false)
        assertFalse(v.tapaPuesta(300L))
    }

    @Test fun `con el formulario la tapa vuelve aunque siga oliendo a challenge`() {
        val v = vigia()
        v.respuesta(v.siguientePregunta(250L)!!, 300L, cf = true, form = true)
        assertTrue(v.tapaPuesta(300L))
    }

    @Test fun `sin challenge la tapa sigue puesta`() {
        val v = vigia()
        v.respuesta(v.siguientePregunta(250L)!!, 300L, cf = false, form = false)
        assertTrue(v.tapaPuesta(300L))
    }

    // Lo de Juan (2026-11-08/09), reproducido en el Samsung el 2026-11-13: el callback de una
    // pregunta lanzada justo cuando el challenge da paso a report.php no vuelve nunca. La tapa se
    // quedaba quitada con el formulario de FC a la vista y el reporte sin enviar.
    @Test fun `si las respuestas dejan de llegar la tapa vuelve sola`() {
        val v = vigia()
        v.respuesta(v.siguientePregunta(250L)!!, 300L, cf = true, form = false)
        v.siguientePregunta(450L) // esta no contesta nunca
        assertFalse(v.tapaPuesta(1_000L))
        assertTrue(v.tapaPuesta(1_400L))
    }

    @Test fun `mientras una pregunta espera no se lanza otra`() {
        val v = vigia()
        assertNotNull(v.siguientePregunta(250L))
        assertNull(v.siguientePregunta(400L))
    }

    @Test fun `una pregunta sin respuesta se da por perdida y se lanza otra`() {
        val v = vigia()
        assertNotNull(v.siguientePregunta(250L))
        assertNotNull(v.siguientePregunta(1_300L))
    }

    @Test fun `se cuentan las preguntas perdidas`() {
        val v = vigia()
        v.siguientePregunta(250L)
        assertEquals(0, v.perdidas)
        v.siguientePregunta(1_300L)
        assertEquals(1, v.perdidas)
        v.respuesta(2, 1_350L, cf = false, form = false)
        v.siguientePregunta(1_500L)
        assertEquals(1, v.perdidas)
    }

    @Test fun `tras una respuesta se puede preguntar de nuevo`() {
        val v = vigia()
        val p = v.siguientePregunta(250L)!!
        v.respuesta(p, 300L, cf = true, form = false)
        assertNotNull(v.siguientePregunta(450L))
    }

    @Test fun `la respuesta de una pregunta perdida no cuenta`() {
        val v = vigia()
        val vieja = v.siguientePregunta(250L)!!
        val nueva = v.siguientePregunta(1_300L)!!
        assertFalse(v.respuesta(vieja, 1_350L, cf = true, form = false))
        assertTrue(v.tapaPuesta(1_350L))
        assertTrue(v.respuesta(nueva, 1_400L, cf = false, form = true))
        assertTrue(v.formulario)
    }

    @Test fun `el formulario se recuerda para enviarlo`() {
        val v = vigia()
        assertFalse(v.formulario)
        v.respuesta(v.siguientePregunta(250L)!!, 300L, cf = false, form = true)
        assertTrue(v.formulario)
    }

    @Test fun `el plazo se agota aunque no llegue ninguna respuesta`() {
        val v = vigia()
        v.siguientePregunta(250L)
        assertFalse(v.agotado(90_000L))
        assertTrue(v.agotado(90_001L))
    }

    @Test fun `cada pregunta lleva un numero distinto`() {
        val v = vigia()
        val a = v.siguientePregunta(250L)!!
        v.respuesta(a, 300L, cf = false, form = false)
        val b = v.siguientePregunta(450L)!!
        assertEquals(a + 1, b)
    }
}
