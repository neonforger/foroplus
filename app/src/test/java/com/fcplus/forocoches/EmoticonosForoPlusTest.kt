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
    fun limpiar() {
        EmoticonosForoPlus.lector = null
        EmoticonosForoPlus.disco = null
        EmoticonosForoPlus.enDisco = null
        EmoticonosForoPlus.remotos = emptyList()
    }

    private fun cfg(s: String) = org.json.JSONObject(s)

    @Test
    fun `la config anuncia emoticonos nuevos`() {
        val l = EmoticonosForoPlus.deConfig(cfg(
            """{"version":2,"emoticonos":[{"nombre":"ROTO DE ORO","fichero":"roto_oro.gif","v":3}]}"""))
        assertEquals(listOf(EmoticonosForoPlus.Emoticono("ROTO DE ORO", "roto_oro.gif", 3)), l)
    }

    @Test
    fun `una entrada mala se salta sin romper las demas`() {
        val l = EmoticonosForoPlus.deConfig(cfg("""{"emoticonos":[
            {"nombre":"","fichero":"a.gif"},
            {"nombre":"X","fichero":"../a.gif"},
            {"nombre":"X","fichero":"A.GIF"},
            {"nombre":"X","fichero":"a.png"},
            {"nombre":"X","fichero":"a.gif","v":0},
            {"nombre":"${"N".repeat(41)}","fichero":"a.gif"},
            "texto suelto",
            {"nombre":"BUENO","fichero":"bueno.gif"},
            {"nombre":"REPETIDO","fichero":"bueno.gif"}
        ]}"""))
        assertEquals(listOf(EmoticonosForoPlus.Emoticono("BUENO", "bueno.gif", 1)), l)
    }

    @Test
    fun `sin config o sin lista no hay remotos`() {
        assertTrue(EmoticonosForoPlus.deConfig(null).isEmpty())
        assertTrue(EmoticonosForoPlus.deConfig(cfg("""{"version":2}""")).isEmpty())
        assertTrue(EmoticonosForoPlus.deConfig(cfg("""{"emoticonos":"no es una lista"}""")).isEmpty())
    }

    @Test
    fun `disponibles son los de dentro y luego los nuevos`() {
        EmoticonosForoPlus.remotos = listOf(EmoticonosForoPlus.Emoticono("NUEVO", "nuevo.gif", 1))
        assertEquals(listOf("senoradetodos.gif", "nuevo.gif"),
            EmoticonosForoPlus.disponibles().map { it.fichero })
    }

    @Test
    fun `la config puede subir la version de uno de dentro para desenvenenarlo`() {
        EmoticonosForoPlus.remotos = listOf(EmoticonosForoPlus.Emoticono("OTRO NOMBRE", "senoradetodos.gif", 5))
        val e = EmoticonosForoPlus.disponibles().single()
        assertEquals(5, e.version)
        assertEquals("el nombre es el de dentro", "SEÑORADETODOS", e.nombre)
        // Pero nunca la baja.
        EmoticonosForoPlus.remotos = listOf(EmoticonosForoPlus.Emoticono("X", "senoradetodos.gif", 1))
        assertEquals(2, EmoticonosForoPlus.disponibles().single().version)
    }

    @Test
    fun `solo salen en el selector los remotos ya bajados`() {
        EmoticonosForoPlus.remotos = listOf(
            EmoticonosForoPlus.Emoticono("BAJADO", "bajado.gif", 1),
            EmoticonosForoPlus.Emoticono("PENDIENTE", "pendiente.gif", 1))
        EmoticonosForoPlus.enDisco = { it == "bajado.gif" }
        assertEquals(listOf("senoradetodos.gif", "bajado.gif"), EmoticonosForoPlus.listos().map { it.fichero })
    }

    @Test
    fun `los bytes de un remoto salen del disco, los de dentro del APK`() {
        EmoticonosForoPlus.remotos = listOf(EmoticonosForoPlus.Emoticono("NUEVO", "nuevo.gif", 1))
        EmoticonosForoPlus.lector = { byteArrayOf(1) }
        EmoticonosForoPlus.disco = { f -> if (f == "nuevo.gif") byteArrayOf(2) else null }
        assertArrayEquals(byteArrayOf(2), EmoticonosForoPlus.bytesDe(EmoticonosForoPlus.CARPETA + "nuevo.gif?v=1"))
        assertArrayEquals(byteArrayOf(1), EmoticonosForoPlus.bytesDe(senora))
        // Uno de la carpeta que ni está dentro ni en la config: a la red (por el proxy de FC).
        assertNull(EmoticonosForoPlus.bytesDe(EmoticonosForoPlus.CARPETA + "desconocido.gif"))
    }

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
        assertEquals("[IMG]$senora?v=2[/IMG]", EmoticonosForoPlus.DENTRO.first().bbcode)
    }

    @Test
    fun `la version no estorba al reconocerla, ni directa ni por el proxy`() {
        val e = EmoticonosForoPlus.DENTRO.first()
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
