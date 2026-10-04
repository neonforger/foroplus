package com.fcplus.forocoches

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SuscripcionesConocidasTest {

    @Test
    fun `ida y vuelta`() {
        val l = listOf("111", "222")
        assertEquals(l, SuscripcionesConocidas.leer(SuscripcionesConocidas.guardar(l)))
    }

    @Test
    fun `de una lista vacia no sale un tid fantasma`() {
        assertEquals(emptyList<String>(), SuscripcionesConocidas.leer(""))
    }

    @Test
    fun `anotar una pagina de suscripciones apunta sus hilos`() {
        val l = SuscripcionesConocidas.anotar(emptyList(), listOf("1", "2", "3"))
        assertTrue(SuscripcionesConocidas.esta(l, "2"))
    }

    @Test
    fun `anotar no duplica lo que ya estaba`() {
        var l = SuscripcionesConocidas.anotar(emptyList(), listOf("1", "2"))
        l = SuscripcionesConocidas.anotar(l, listOf("2", "3"))
        assertEquals(3, l.size)
    }

    @Test
    fun `anotar ignora los tids vacios`() {
        val l = SuscripcionesConocidas.anotar(emptyList(), listOf("", "  ", "7"))
        assertEquals(listOf("7"), l)
    }

    // Lo que NO se puede concluir: la lista de Suscripciones viene PAGINADA, así que de una
    // página solo se deduce "estos sí", jamás "el resto no". Por eso anotar solo SUMA.
    @Test
    fun `anotar una pagina no borra lo aprendido antes`() {
        var l = SuscripcionesConocidas.anotar(emptyList(), listOf("viejo"))
        l = SuscripcionesConocidas.anotar(l, listOf("nuevo"))
        assertTrue(SuscripcionesConocidas.esta(l, "viejo"))
    }

    // Quitar solo lo hace el toggle, que es el único que verifica contra FC.
    @Test
    fun `marcar da de alta y de baja`() {
        var l = SuscripcionesConocidas.marcar(emptyList(), "9", true)
        assertTrue(SuscripcionesConocidas.esta(l, "9"))
        l = SuscripcionesConocidas.marcar(l, "9", false)
        assertFalse(SuscripcionesConocidas.esta(l, "9"))
    }

    @Test
    fun `dar de baja lo que no estaba no rompe nada`() {
        val l = SuscripcionesConocidas.marcar(listOf("1"), "2", false)
        assertEquals(listOf("1"), l)
    }

    @Test
    fun `lo ultimo suscrito queda el primero`() {
        var l = SuscripcionesConocidas.marcar(emptyList(), "1", true)
        l = SuscripcionesConocidas.marcar(l, "2", true)
        assertEquals("2", l.first())
    }

    @Test
    fun `hay tope y se van los mas viejos`() {
        var l = emptyList<String>()
        for (i in 1..SuscripcionesConocidas.TOPE + 10) l = SuscripcionesConocidas.marcar(l, "t$i", true)
        assertEquals(SuscripcionesConocidas.TOPE, l.size)
        assertFalse(SuscripcionesConocidas.esta(l, "t1"))
        assertTrue(SuscripcionesConocidas.esta(l, "t${SuscripcionesConocidas.TOPE + 10}"))
    }

    @Test
    fun `un tid vacio nunca esta suscrito`() {
        assertFalse(SuscripcionesConocidas.esta(listOf("1"), ""))
    }
}
