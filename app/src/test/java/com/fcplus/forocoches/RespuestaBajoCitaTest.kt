package com.fcplus.forocoches

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RespuestaBajoCitaTest {

    private val a = "[QUOTE=Pepito;1]El Ibiza es un tanque[/QUOTE]\n"
    private val b = "[QUOTE=Manolo;2]Mejor un Golf[/QUOTE]\n"

    @Test
    fun `solo con el ajuste y dos citas o más`() {
        assertFalse(RespuestaBajoCita.activo(false, 3))
        assertFalse(RespuestaBajoCita.activo(true, 1))
        assertFalse(RespuestaBajoCita.activo(true, 0))
        assertTrue(RespuestaBajoCita.activo(true, 2))
    }

    @Test
    fun `cada respuesta va debajo de su cita y el cierre al final`() {
        assertEquals(
            "[QUOTE=Pepito;1]El Ibiza es un tanque[/QUOTE]\nHasta el embrague\n\n" +
                "[QUOTE=Manolo;2]Mejor un Golf[/QUOTE]\nY se revende\n\nSaludos",
            RespuestaBajoCita.armar(listOf(a to "Hasta el embrague", b to "Y se revende"), "Saludos")
        )
    }

    @Test
    fun `una cita sin respuesta se publica sola, sin huecos`() {
        assertEquals(
            "[QUOTE=Pepito;1]El Ibiza es un tanque[/QUOTE]\n[QUOTE=Manolo;2]Mejor un Golf[/QUOTE]\nSolo esto",
            RespuestaBajoCita.armar(listOf(a to "", b to "  \n"), "Solo esto")
        )
    }

    @Test
    fun `sin cierre no queda nada colgando al final`() {
        assertEquals(
            "[QUOTE=Pepito;1]El Ibiza es un tanque[/QUOTE]\nUno\n\n[QUOTE=Manolo;2]Mejor un Golf[/QUOTE]\nDos",
            RespuestaBajoCita.armar(listOf(a to "Uno\n", b to "Dos"), "   ")
        )
    }

    @Test
    fun `lo escrito bajo las citas pasa a la caja sin perderse`() {
        assertEquals("Uno\n\nDos\n\nCierre", RespuestaBajoCita.aLaCaja(listOf("Uno", " Dos "), "Cierre"))
        assertEquals("Dos", RespuestaBajoCita.aLaCaja(listOf("", "Dos"), ""))
        assertEquals("", RespuestaBajoCita.aLaCaja(emptyList(), ""))
    }
}
