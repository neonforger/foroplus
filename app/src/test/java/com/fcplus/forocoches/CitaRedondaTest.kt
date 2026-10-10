package com.fcplus.forocoches

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Fase 2 del rediseño: el fondo de una cita lleva las esquinas DERECHAS redondeadas (la barra
 * roja va a la izquierda), y su cabecera "X dijo:" deja de ir subrayada.
 */
class CitaRedondaTest {

    @Test
    fun `una cita de una sola linea redondea arriba y abajo`() =
        assertEquals(true to true, CitaSpan.esquinas(10, 30, 10, 30))

    @Test
    fun `primera linea de una cita larga solo arriba`() =
        assertEquals(true to false, CitaSpan.esquinas(10, 30, 10, 90))

    @Test
    fun `linea del medio sin esquinas`() =
        assertEquals(false to false, CitaSpan.esquinas(30, 60, 10, 90))

    @Test
    fun `ultima linea solo abajo`() =
        assertEquals(false to true, CitaSpan.esquinas(60, 90, 10, 90))

    @Test
    fun `la cabecera de una cita es el enlace que acaba en dijo`() {
        assertTrue(CabeceraCita.es("Phantasmilla dijo:"))
        assertTrue(CabeceraCita.es("  Kantauri dijo: "))
        assertFalse(CabeceraCita.es("https://forocoches.com/foro/showthread.php?t=1"))
        assertFalse(CabeceraCita.es("lo que dijo ayer"))
    }
}
