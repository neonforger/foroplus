package com.fcplus.forocoches

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LimiteBusquedaTest {

    @Test fun `otros motivos no son el freno`() {
        assertNull(LimiteBusqueda.segundos("empty"))
        assertNull(LimiteBusqueda.segundos("cloudflare"))
        assertNull(LimiteBusqueda.mensaje("TypeError: Failed to fetch"))
    }

    @Test fun `lee los segundos que faltan`() {
        assertEquals(96, LimiteBusqueda.segundos("ratelimit:96"))
    }

    @Test fun `sin numero legible se cuenta un minuto, nunca cero`() {
        assertEquals(60, LimiteBusqueda.segundos("ratelimit:"))
        assertEquals(1, LimiteBusqueda.segundos("ratelimit:0"))
    }

    @Test fun `el tiempo se dice en palabras`() {
        assertEquals("en unos segundos", LimiteBusqueda.cuando(3))
        assertEquals("en 40 segundos", LimiteBusqueda.cuando(40))
        assertEquals("en un minuto", LimiteBusqueda.cuando(75))
        assertEquals("en 2 minutos", LimiteBusqueda.cuando(96))
    }

    @Test fun `el mensaje dice cuando volver`() {
        val m = LimiteBusqueda.mensaje("ratelimit:96")!!
        assertTrue(m.contains("en 2 minutos"))
    }
}
