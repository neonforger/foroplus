package com.fcplus.forocoches

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UltimosPosteadoresTest {

    @Test
    fun `el numero de mensaje sale del enlace de la hora`() {
        assertEquals("520085495", UltimosPosteadores.pidDe("https://forocoches.com/foro/showthread.php?p=520085495#post520085495"))
        assertEquals("", UltimosPosteadores.pidDe(""))
        assertEquals("", UltimosPosteadores.pidDe("https://forocoches.com/foro/showthread.php?t=123"))
    }

    @Test
    fun `sin respuestas el ultimo es el que lo abrio y no se pregunta`() {
        assertEquals("Juan", UltimosPosteadores.sinPreguntar("0", "Juan"))
        assertNull(UltimosPosteadores.sinPreguntar("3", "Juan"))
        assertNull(UltimosPosteadores.sinPreguntar("0", ""))
    }

    @Test
    fun `como mucho dos peticiones a la vez`() {
        val u = UltimosPosteadores(enVuelo = 2)
        assertEquals(listOf("1"), u.quiero("1"))
        assertEquals(listOf("2"), u.quiero("2"))
        assertTrue(u.quiero("3").isEmpty())          // espera turno
        assertEquals(listOf("3"), u.llego("1", "Ana"))
        assertEquals("Ana", u.autor("1"))
    }

    @Test
    fun `lo ya sabido no se vuelve a pedir nunca`() {
        val u = UltimosPosteadores()
        u.quiero("1"); u.llego("1", "Ana")
        assertTrue(u.quiero("1").isEmpty())
    }

    @Test
    fun `lo que ya va de camino no se pide dos veces`() {
        val u = UltimosPosteadores()
        assertEquals(listOf("1"), u.quiero("1"))
        assertTrue(u.quiero("1").isEmpty())
    }

    @Test
    fun `al deslizar de golpe solo se piden las filas mas recientes`() {
        val u = UltimosPosteadores(enVuelo = 1, colaMax = 3)
        u.quiero("ocupa")                            // la unica plaza
        for (i in 1..10) u.quiero("$i")             // pasan de largo 1..7
        val salen = ArrayList<String>()
        var siguiente = u.llego("ocupa", "X")
        while (siguiente.isNotEmpty()) {
            salen += siguiente
            siguiente = u.llego(siguiente.first(), "Y")
        }
        assertEquals(listOf("10", "9", "8"), salen)
    }

    @Test
    fun `un fallo no se reintenta en bucle, solo al recargar`() {
        val u = UltimosPosteadores()
        u.quiero("1"); u.llego("1", "")
        assertNull(u.autor("1"))
        assertTrue(u.quiero("1").isEmpty())
        u.reintentarFallidos()
        assertEquals(listOf("1"), u.quiero("1"))
    }

    @Test
    fun `otra lista vacia la cola pero no lo sabido`() {
        val u = UltimosPosteadores(enVuelo = 1)
        u.quiero("1"); u.quiero("2")
        u.olvidarCola()
        assertTrue(u.llego("1", "Ana").isEmpty())    // el 2 ya no se pide
        assertEquals("Ana", u.autor("1"))
    }

    @Test
    fun `una peticion que nunca contesta no bloquea la lista siguiente`() {
        val u = UltimosPosteadores(enVuelo = 1)
        assertEquals(listOf("1"), u.quiero("1"))      // el motor se recarga: no contesta nunca
        assertTrue(u.quiero("2").isEmpty())
        u.reiniciar()
        assertEquals(listOf("3"), u.quiero("3"))
        u.llego("1", "Tarde")                          // si llega tarde, se apunta igual
        assertEquals("Tarde", u.autor("1"))
    }

    @Test
    fun `la memoria tiene tope`() {
        val u = UltimosPosteadores(enVuelo = 1, maxRecordados = 2)
        for (i in 1..3) { u.quiero("$i"); u.llego("$i", "u$i") }
        assertNull(u.autor("1"))
        assertEquals("u3", u.autor("3"))
    }
}
