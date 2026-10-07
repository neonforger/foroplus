package com.fcplus.forocoches

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * El punto verde de "conectado ahora" en el avatar de cada mensaje. FC lo da SOLO ahí (medido
 * 2026-10-07): la ficha no lo dice y `online.php` está desactivada.
 */
@RunWith(RobolectricTestRunner::class)
class PresenciaTest {

    private fun hilo(posts: String) =
        """{"url":"u","tid":"1","title":"t","page":1,"pageCount":1,"posts":[$posts]}"""

    private fun post(pid: String, online: String = "") =
        """{"pid":"$pid","author":"a","uid":"","avatar":"","date":"","html":""$online}"""

    @Test
    fun `el motor marca quien esta conectado`() {
        val t = parseThreadPayload(hilo(post("1", ""","online":true""") + "," + post("2", ""","online":false""")))!!
        assertTrue(t.posts[0].conectado)
        assertFalse(t.posts[1].conectado)
    }

    @Test
    fun `sin la marca, desconocido es no conectado`() {
        // Las copias guardadas antes de esto no la traen: sin punto, como hoy.
        val t = parseThreadPayload(hilo(post("1")))!!
        assertFalse(t.posts[0].conectado)
    }

    private fun item(pid: String, conectado: Boolean) =
        PostItem(pid = pid, author = "a", uid = "", avatar = "", date = "", html = "", conectado = conectado)

    @Test
    fun `en vivo se ensena tal cual`() {
        val posts = listOf(item("1", true), item("2", false))
        assertSame(posts, Presencia.paraMostrar(posts, esCopia = false))
    }

    @Test
    fun `en una copia descargada no se ensena`() {
        // Estar conectado hace días, cuando se bajó el hilo, no dice nada de ahora.
        val r = Presencia.paraMostrar(listOf(item("1", true), item("2", false)), esCopia = true)
        assertEquals(listOf(false, false), r.map { it.conectado })
        assertEquals(listOf("1", "2"), r.map { it.pid })
    }
}
