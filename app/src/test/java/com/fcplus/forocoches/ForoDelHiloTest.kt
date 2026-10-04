package com.fcplus.forocoches

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * El subforo del hilo viaja en el payload para pintar la miga de la cabecera ("General ›").
 * Lo pidió Juan (Telegram 2191): abriendo un hilo desde la lista de hilos de un usuario no había
 * forma de saber en qué subforo estabas, y se da por hecho que es el General.
 */
@RunWith(RobolectricTestRunner::class)
class ForoDelHiloTest {

    private fun payload(extra: String) =
        """{"url":"u","tid":"1","title":"t","page":1,"pageCount":1,"posts":[]$extra}"""

    @Test
    fun `el subforo llega con su fid y su nombre`() {
        val t = parseThreadPayload(payload(""","forum":{"fid":17,"name":"Electrónica / Informática"}"""))!!
        assertEquals(17, t.forumFid)
        assertEquals("Electrónica / Informática", t.forumName)
    }

    @Test
    fun `sin subforo en el payload queda vacio`() {
        // Una copia guardada antes de este cambio, o "sus mensajes", no lo traen.
        val t = parseThreadPayload(payload(""))!!
        assertEquals(0, t.forumFid)
        assertEquals("", t.forumName)
    }

    @Test
    fun `un subforo null no rompe el hilo`() {
        val t = parseThreadPayload(payload(""","forum":null"""))!!
        assertEquals(0, t.forumFid)
    }
}
