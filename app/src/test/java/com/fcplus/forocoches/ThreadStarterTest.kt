package com.fcplus.forocoches

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regla de a quién se le recuadra el mensaje. El recuadro en sí es visual (se comprueba en el
 * dispositivo por los `bounds` del volcado de UI), pero la decisión de QUIÉN es el autor del
 * hilo es lógica y se prueba aquí.
 */
class ThreadStarterTest {

    private fun post(author: String, pid: String = "1") =
        PostItem(pid = pid, author = author, uid = "", avatar = "", date = "", html = "")

    @Test
    fun `en la pagina 1 el autor del hilo es el del primer post`() {
        val posts = listOf(post("Pepito", "1"), post("Otro", "2"), post("Pepito", "3"))
        assertEquals("Pepito", ThreadStarter.of(posts, page = 1))
    }

    @Test
    fun `en cualquier otra pagina no se puede saber`() {
        // El primer post de la página 7 NO es el creador: sería marcar a quien no toca.
        val posts = listOf(post("Otro"), post("Pepito"))
        assertEquals("", ThreadStarter.of(posts, page = 2))
        assertEquals("", ThreadStarter.of(posts, page = 7))
    }

    @Test
    fun `sin posts no hay autor de hilo`() {
        assertEquals("", ThreadStarter.of(emptyList(), page = 1))
    }

    @Test
    fun `primer post sin autor (anonimo o baneado) no marca a nadie`() {
        assertEquals("", ThreadStarter.of(listOf(post(""), post("Pepito")), page = 1))
    }

    @Test
    fun `el nombre viene limpio de espacios`() {
        assertEquals("Pepito", ThreadStarter.of(listOf(post("  Pepito  ")), page = 1))
    }

    @Test
    fun `un post del autor del hilo se marca, sin importar mayusculas`() {
        assertTrue(ThreadStarter.isStarter("Pepito", "Pepito"))
        assertTrue(ThreadStarter.isStarter("PEPITO", "pepito"))
    }

    @Test
    fun `un post de otro no se marca`() {
        assertFalse(ThreadStarter.isStarter("Otro", "Pepito"))
    }

    @Test
    fun `con el autor del hilo desconocido no se marca nada`() {
        // Caso real: entras por una notificación de cita a la página 7 de un hilo nunca
        // abierto. Sin recuadro es correcto; recuadrar al primero que salga, no.
        assertFalse(ThreadStarter.isStarter("Pepito", ""))
    }

    @Test
    fun `un post sin autor nunca se marca`() {
        assertFalse(ThreadStarter.isStarter("", "Pepito"))
        assertFalse(ThreadStarter.isStarter("", ""))
    }
}
