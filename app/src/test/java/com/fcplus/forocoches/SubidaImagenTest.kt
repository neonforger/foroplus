package com.fcplus.forocoches

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SubidaImagenTest {

    @Test
    fun `sin config se usa catbox`() {
        assertEquals(SubidaImagen.CATBOX, SubidaImagen.host(null))
        assertEquals(SubidaImagen.CATBOX, SubidaImagen.host(JSONObject("{}")))
    }

    @Test
    fun `la config remota manda sobre el host`() {
        // El dia que catbox cierre o banee, esto es lo que evita compilar y pasar por Play.
        val h = SubidaImagen.host(
            JSONObject(
                """{"imagenes":{"url":"https://otro.example/api","campoFichero":"archivo",
                   "campos":{"tipo":"subida"},"ladoMax":1200}}"""
            )
        )
        assertEquals("https://otro.example/api", h.url)
        assertEquals("archivo", h.campoFichero)
        assertEquals(mapOf("tipo" to "subida"), h.campos)
        assertEquals(1200, h.ladoMax)
    }

    @Test
    fun `un bloque a medias no deja la subida coja`() {
        val h = SubidaImagen.host(JSONObject("""{"imagenes":{"url":"https://otro.example/api"}}"""))
        assertEquals(SubidaImagen.CATBOX.campoFichero, h.campoFichero)
        assertEquals(SubidaImagen.CATBOX.campos, h.campos)
        assertEquals(SubidaImagen.CATBOX.ladoMax, h.ladoMax)
    }

    @Test
    fun `un host sin https se ignora y se usa catbox`() {
        // La config vive en un repo público: no se sube la foto de nadie en claro ni a un
        // esquema raro, aunque el fichero remoto lo pida.
        assertEquals(
            SubidaImagen.CATBOX,
            SubidaImagen.host(JSONObject("""{"imagenes":{"url":"http://otro.example/api"}}"""))
        )
        assertEquals(
            SubidaImagen.CATBOX,
            SubidaImagen.host(JSONObject("""{"imagenes":{"url":"otro.example/api"}}"""))
        )
    }

    @Test
    fun `un lado absurdo se acota`() {
        val h = SubidaImagen.host(JSONObject("""{"imagenes":{"url":"https://x.example","ladoMax":10}}"""))
        assertEquals(320, h.ladoMax)
    }

    @Test
    fun `la respuesta buena es el enlace`() {
        assertEquals(
            "https://files.catbox.moe/zqoynk.png",
            SubidaImagen.enlaceDeRespuesta("https://files.catbox.moe/zqoynk.png\n")
        )
    }

    @Test
    fun `un error de catbox NO se cuela como enlace`() {
        // Catbox contesta 200 con el error en texto plano: sin esto se publicaria el churro.
        assertEquals("", SubidaImagen.enlaceDeRespuesta("File too large"))
        assertEquals("", SubidaImagen.enlaceDeRespuesta(""))
        assertEquals("", SubidaImagen.enlaceDeRespuesta("Error: https://files.catbox.moe/x.png"))
    }

    @Test
    fun `el bbcode es el que entiende FC`() {
        assertEquals("[IMG]https://x.example/a.png[/IMG]", SubidaImagen.bbcode("https://x.example/a.png"))
    }
}
