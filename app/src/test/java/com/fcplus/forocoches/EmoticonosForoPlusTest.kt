package com.fcplus.forocoches

import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EmoticonosForoPlusTest {

    private val senora = "https://neonforger.github.io/foroplus/emoticonos/senoradetodos.gif"

    /** Tal cual sale en el HTML de FC (medido en la vista previa el 2026-10-18): firma sin `&`. */
    private val porProxy = "https://images.forocoches.com?url=https%3A%2F%2Fneonforger.github.io" +
        "%2Fforoplus%2Femoticonos%2Fsenoradetodos.giffcsigsigsigsig=064cf0f35a6480e766313c50f9e6"

    @After
    fun limpiar() { EmoticonosForoPlus.lector = null }

    @Test
    fun `la URL directa es nuestra`() {
        assertEquals("senoradetodos.gif", EmoticonosForoPlus.ficheroDe(senora))
        assertEquals("SEÑORADETODOS", EmoticonosForoPlus.deUrl(senora)?.nombre)
    }

    @Test
    fun `la URL del proxy de FC se desenvuelve`() {
        assertEquals("senoradetodos.gif", EmoticonosForoPlus.ficheroDe(porProxy))
        assertEquals("SEÑORADETODOS", EmoticonosForoPlus.deUrl(porProxy)?.nombre)
    }

    @Test
    fun `el bbcode es la URL versionada en IMG`() {
        assertEquals("[IMG]$senora?v=2[/IMG]", EmoticonosForoPlus.TODOS.first().bbcode)
    }

    @Test
    fun `la version no estorba al reconocerla, ni directa ni por el proxy`() {
        val e = EmoticonosForoPlus.TODOS.first()
        assertEquals(e, EmoticonosForoPlus.deUrl(e.url))
        val proxy = "https://images.forocoches.com/?url=" +
            java.net.URLEncoder.encode(e.url, "UTF-8") + "fcsigsigsigsig=ab12"
        assertEquals(e, EmoticonosForoPlus.deUrl(proxy))
    }

    @Test
    fun `uno que esta version no lleva sigue siendo de ForoPlus pero no tiene bytes`() {
        val nuevo = "https://neonforger.github.io/foroplus/emoticonos/otro_nuevo.gif"
        assertTrue(EmoticonosForoPlus.esDeForoPlus(nuevo))
        assertNull(EmoticonosForoPlus.deUrl(nuevo))
        EmoticonosForoPlus.lector = { byteArrayOf(1) }
        assertNull(EmoticonosForoPlus.bytesDe(nuevo))
    }

    @Test
    fun `lo que no es de la carpeta no es nuestro`() {
        assertFalse(EmoticonosForoPlus.esDeForoPlus("https://files.catbox.moe/3mifac.gif"))
        assertFalse(EmoticonosForoPlus.esDeForoPlus("https://neonforger.github.io/foroplus/capturas/hilo.jpg"))
        assertFalse(EmoticonosForoPlus.esDeForoPlus("https://neonforger.github.io/foroplus/emoticonos/../x.gif"))
        assertFalse(EmoticonosForoPlus.esDeForoPlus("https://forocoches.com/foro/images/smilies/goofy.gif"))
        assertFalse(EmoticonosForoPlus.esDeForoPlus(
            "https://images.forocoches.com?url=https%3A%2F%2Fi.imgur.com%2Fabc.pngfcsigsigsigsig=00"))
    }

    @Test
    fun `los bytes salen del lector, sin red`() {
        EmoticonosForoPlus.lector = { f -> if (f == "senoradetodos.gif") byteArrayOf(7, 8) else null }
        assertArrayEquals(byteArrayOf(7, 8), EmoticonosForoPlus.bytesDe(porProxy))
        assertNull(EmoticonosForoPlus.bytesDe("https://i.imgur.com/abc.png"))
    }

    @Test
    fun `sin lector o si falla, a la red`() {
        assertNull(EmoticonosForoPlus.bytesDe(senora))
        EmoticonosForoPlus.lector = { throw java.io.IOException("no está") }
        assertNull(EmoticonosForoPlus.bytesDe(senora))
    }

    @Test
    fun `el fichero en assets existe y es el GIF de 88x20`() {
        val f = java.io.File("src/main/assets/emoticonos/senoradetodos.gif")
        val b = f.readBytes()
        assertTrue(GifAnimado.esGif(b))
        val ancho = (b[6].toInt() and 0xFF) or ((b[7].toInt() and 0xFF) shl 8)
        val alto = (b[8].toInt() and 0xFF) or ((b[9].toInt() and 0xFF) shl 8)
        assertEquals(88 to 20, ancho to alto)
        // El de la web (GitHub Pages) tiene que ser el mismo fichero, byte a byte.
        assertArrayEquals(b, java.io.File("../docs/emoticonos/senoradetodos.gif").readBytes())
    }
}
