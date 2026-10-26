package com.fcplus.forocoches

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AvisoListaVaciaTest {

    @Test
    fun `los hilos de alguien ignorado lo dicen`() {
        assertEquals(
            "No hay hilos que mostrar: son de @Fulano, que tienes en ignorados",
            AvisoListaVacia.porIgnorados(listOf("Fulano", "fulano"), listOf("FULANO"))
        )
    }

    @Test
    fun `varios ignorados`() {
        assertEquals(
            "No hay hilos que mostrar: son de usuarios que tienes en ignorados",
            AvisoListaVacia.porIgnorados(listOf("A", "B"), listOf("a", "b"))
        )
    }

    @Test
    fun `si alguno no es de un ignorado lo ha tapado otro filtro`() {
        assertNull(AvisoListaVacia.porIgnorados(listOf("A", "C"), listOf("a")))
    }

    @Test
    fun `sin hilos de origen no hay nada que explicar`() {
        assertNull(AvisoListaVacia.porIgnorados(emptyList(), listOf("a")))
    }

    @Test
    fun `un hilo sin autor conocido no cuenta como ignorado`() {
        assertNull(AvisoListaVacia.porIgnorados(listOf(""), listOf("a")))
    }
}
