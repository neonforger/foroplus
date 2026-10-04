package com.fcplus.forocoches

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NoticiasVistasTest {

    private val cita = "https://forocoches.com/foro/showthread.php?p=518956743#post518956743"

    @Test
    fun `la identidad es el pid del enlace`() {
        assertEquals("518956743", NoticiasVistas.idDe(cita))
        assertEquals("", NoticiasVistas.idDe("https://forocoches.com/foro/showthread.php?t=123"))
        assertEquals("", NoticiasVistas.idDe(""))
    }

    @Test
    fun `la primera visita no resalta nada`() {
        // Sin esto, al estrenar el panel saldrian en negrita todas las del historial.
        assertFalse(NoticiasVistas.esNueva(cita, emptySet(), primeraVisita = true))
    }

    @Test
    fun `lo no visto sale como nuevo`() {
        assertTrue(NoticiasVistas.esNueva(cita, setOf("111"), primeraVisita = false))
    }

    @Test
    fun `lo ya visto deja de resaltarse`() {
        assertFalse(NoticiasVistas.esNueva(cita, setOf("518956743"), primeraVisita = false))
    }

    @Test
    fun `una cita sin pid no se resalta nunca`() {
        // Mejor no resaltar que resaltar algo que no sabemos identificar: volveria cada vez.
        val sinPid = "https://forocoches.com/foro/showthread.php?t=10802213"
        assertFalse(NoticiasVistas.esNueva(sinPid, emptySet(), primeraVisita = false))
    }

    @Test
    fun `mirar el panel las recuerda todas`() {
        val visto = NoticiasVistas.recordar(emptySet(), listOf(cita, "?p=222"))
        assertEquals(setOf("518956743", "222"), visto)
    }

    @Test
    fun `el tope tira las mas viejas`() {
        val viejas = (1..5).map { it.toString() }.toSet()
        val visto = NoticiasVistas.recordar(viejas, listOf("?p=99"), tope = 3)
        assertEquals(3, visto.size)
        assertTrue("la nueva se queda", "99" in visto)
        assertFalse("la mas vieja se va", "1" in visto)
    }

    @Test
    fun `recordar dos veces no duplica ni crece`() {
        val una = NoticiasVistas.recordar(emptySet(), listOf(cita))
        assertEquals(una, NoticiasVistas.recordar(una, listOf(cita)))
    }
}

class NoticiasVistasGuardadoTest {

    @Test
    fun `ida y vuelta conservando el orden`() {
        val vistas = NoticiasVistas.leer("111,222,333")
        assertEquals(listOf("111", "222", "333"), vistas.toList())
        assertEquals("111,222,333", NoticiasVistas.guardar(vistas))
    }

    @Test
    fun `una cadena vacia o con basura no rompe nada`() {
        assertTrue(NoticiasVistas.leer("").isEmpty())
        assertEquals(setOf("111", "222"), NoticiasVistas.leer(" 111 ,, 222 ,"))
    }
}
