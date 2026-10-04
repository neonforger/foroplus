package com.fcplus.forocoches

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HilosIgnoradosTest {

    @Test
    fun `ida y vuelta con titulo`() {
        val l = listOf(HilosIgnorados.Hilo("123", "Hilo del pisito"))
        assertEquals(l, HilosIgnorados.leer(HilosIgnorados.guardar(l)))
    }

    @Test
    fun `el ultimo ignorado queda el primero`() {
        var l = HilosIgnorados.ignorar(emptyList(), "1", "uno")
        l = HilosIgnorados.ignorar(l, "2", "dos")
        assertEquals(listOf("2", "1"), l.map { it.tid })
    }

    @Test
    fun `ignorar dos veces el mismo no duplica`() {
        var l = HilosIgnorados.ignorar(emptyList(), "1", "uno")
        l = HilosIgnorados.ignorar(l, "1", "uno con otro titulo")
        assertEquals(1, l.size)
        assertEquals("uno con otro titulo", l.first().titulo)
    }

    @Test
    fun `se puede desocultar`() {
        val l = HilosIgnorados.ignorar(emptyList(), "7", "algo")
        assertTrue(HilosIgnorados.estaIgnorado(l, "7"))
        assertFalse(HilosIgnorados.estaIgnorado(HilosIgnorados.olvidar(l, "7"), "7"))
    }

    @Test
    fun `un titulo con saltos o barras no rompe el guardado`() {
        // Si el titulo colara un salto de linea, al leer saldrian entradas fantasma.
        val l = listOf(HilosIgnorados.Hilo("9", "titulo\ncon salto | y barra"))
        val vuelta = HilosIgnorados.leer(HilosIgnorados.guardar(l))
        assertEquals(1, vuelta.size)
        assertEquals("9", vuelta.first().tid)
    }

    @Test
    fun `una cadena vacia o con basura no da entradas`() {
        assertTrue(HilosIgnorados.leer("").isEmpty())
        assertTrue(HilosIgnorados.leer("\n  \n").isEmpty())
        assertEquals(listOf("5"), HilosIgnorados.leer("5").map { it.tid })
    }

    @Test
    fun `el tope tira el mas viejo`() {
        var l = emptyList<HilosIgnorados.Hilo>()
        for (i in 1..5) l = HilosIgnorados.ignorar(l, "$i", "h$i", tope = 3)
        assertEquals(listOf("5", "4", "3"), l.map { it.tid })
    }

    @Test
    fun `sin tid no se guarda nada`() {
        assertTrue(HilosIgnorados.ignorar(emptyList(), "  ", "x").isEmpty())
    }
}
