package com.fcplus.forocoches

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BusquedasRecientesTest {

    @Test
    fun `la ultima buscada va primera`() {
        val l = BusquedasRecientes.recordar(listOf("renault", "seat"), "bmw")
        assertEquals(listOf("bmw", "renault", "seat"), l)
    }

    @Test
    fun `repetir una busqueda la sube y no la duplica`() {
        val l = BusquedasRecientes.recordar(listOf("renault", "seat"), "seat")
        assertEquals(listOf("seat", "renault"), l)
    }

    @Test
    fun `no distingue mayusculas`() {
        // "Renault" y "renault" son la misma busqueda: ocupar dos huecos seria tirar el atajo.
        val l = BusquedasRecientes.recordar(listOf("renault"), "Renault")
        assertEquals(listOf("Renault"), l)
    }

    @Test
    fun `no se guarda lo vacio`() {
        assertEquals(listOf("seat"), BusquedasRecientes.recordar(listOf("seat"), "   "))
    }

    @Test
    fun `el tope tira las mas viejas`() {
        var l = emptyList<String>()
        for (i in 1..12) l = BusquedasRecientes.recordar(l, "busqueda$i", tope = 8)
        assertEquals(8, l.size)
        assertEquals("busqueda12", l.first())
        assertTrue("la mas vieja se va", l.none { it == "busqueda1" })
    }

    @Test
    fun `ida y vuelta al guardarlas`() {
        val l = listOf("seat leon", "bmw, serie 3", "renault")
        assertEquals(l, BusquedasRecientes.leer(BusquedasRecientes.guardar(l)))
    }

    @Test
    fun `una cadena vacia no da basura`() {
        assertTrue(BusquedasRecientes.leer("").isEmpty())
    }

    @Test
    fun `se puede olvidar una`() {
        assertEquals(listOf("seat"), BusquedasRecientes.olvidar(listOf("seat", "bmw"), "BMW"))
    }
}
