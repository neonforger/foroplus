package com.fcplus.forocoches

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NavStackTest {

    private val inicio = Screen.ThreadList("home", 2)
    private val citas = Screen.Notices("quotes")
    private val hilo = Screen.Thread("https://forocoches.com/foro/showthread.php?t=1", 1)
    private val perfil = Screen.Member("908941")

    @Test
    fun `recien creada no tiene nada y el atras sale de la app`() {
        val nav = NavStack()
        assertNull(nav.current)
        assertNull(nav.pop())
        assertTrue(nav.atRoot)
    }

    @Test
    fun `el caso del tester - de citas a un hilo y atras vuelve a CITAS, no al listado`() {
        val nav = NavStack()
        nav.root(citas)
        nav.push(hilo)
        assertEquals(hilo, nav.current)
        assertEquals(citas, nav.pop())
    }

    @Test
    fun `cadena larga - citas a hilo a perfil deshace paso a paso`() {
        val nav = NavStack()
        nav.root(citas)
        nav.push(hilo)
        nav.push(perfil)
        assertEquals(hilo, nav.pop())
        assertEquals(citas, nav.pop())
        assertNull(nav.pop())   // en la raíz: salir de la app
    }

    @Test
    fun `cambiar de pestana REINICIA la pila`() {
        // Si no, el atrás recorrería hacia atrás todas las pestañas que has tocado, que es
        // justo lo que nadie espera de una barra de pestañas.
        val nav = NavStack()
        nav.root(citas)
        nav.push(hilo)
        nav.root(inicio)
        assertEquals(listOf(inicio), nav.snapshot())
        assertTrue(nav.atRoot)
        assertNull(nav.pop())
    }

    @Test
    fun `entrar dos veces en la misma pantalla no obliga a dar dos veces atras`() {
        val nav = NavStack()
        nav.root(inicio)
        nav.push(hilo)
        nav.push(hilo)
        assertEquals(2, nav.size)
        assertEquals(inicio, nav.pop())
    }

    @Test
    fun `un hilo distinto si apila aunque sea del mismo tipo`() {
        val otro = Screen.Thread("https://forocoches.com/foro/showthread.php?t=2", 1)
        val nav = NavStack()
        nav.root(inicio)
        nav.push(hilo)
        nav.push(otro)
        assertEquals(hilo, nav.pop())
    }

    @Test
    fun `replaceTop recuerda por donde ibas leyendo, sin apilar`() {
        // Hilo → cambias a la página 4 y bajas al post 99 → entras a un perfil → atrás:
        // tienes que volver a la página 4 y a ese post, no al principio del hilo.
        val nav = NavStack()
        nav.root(citas)
        nav.push(hilo)
        nav.replaceTop(hilo.copy(page = 4, anchorPid = "99"))
        nav.push(perfil)
        assertEquals(2, nav.pop().let { nav.size })
        assertEquals(Screen.Thread(hilo.url, 4, "99"), nav.current)
    }

    @Test
    fun `replaceTop sobre una pila vacia se comporta como entrar`() {
        val nav = NavStack()
        nav.replaceTop(inicio)
        assertEquals(inicio, nav.current)
    }

    @Test
    fun `navegar en circulos no hace crecer la pila sin limite`() {
        val nav = NavStack(maxDepth = 5)
        nav.root(inicio)
        repeat(20) { i ->
            nav.push(Screen.Thread("hilo$i"))
            nav.push(Screen.Member("u$i"))
        }
        assertTrue("la pila se desbocó: ${nav.size}", nav.size <= 5)
        // Y lo más viejo es lo que se cae, no lo reciente.
        assertEquals(Screen.Member("u19"), nav.current)
    }

    @Test
    fun `los MP encadenan bandeja y detalle`() {
        val nav = NavStack()
        nav.root(Screen.Profile)
        nav.push(Screen.PmInbox)
        nav.push(Screen.PmDetail("52979940"))
        assertEquals(Screen.PmInbox, nav.pop())
        assertEquals(Screen.Profile, nav.pop())
        assertNull(nav.pop())
    }

    @Test
    fun `la raiz nunca se saca de la pila`() {
        val nav = NavStack()
        nav.root(inicio)
        assertNull(nav.pop())
        assertEquals(inicio, nav.current)   // sigue ahí para cuando el sistema no cierre la app
        assertFalse(nav.snapshot().isEmpty())
    }
}
