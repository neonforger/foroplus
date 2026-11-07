package com.fcplus.forocoches

import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EnlaceMusicaTest {

    @Test
    fun contratoDeEnlaces() {
        val txt = javaClass.classLoader!!.getResource("contrato/musica-enlaces.json").readText()
        val arr = JSONArray(txt)
        for (i in 0 until arr.length()) {
            val c = arr.getJSONObject(i)
            val url = c.getString("url")
            val ref = EnlaceMusica.de(url)
            if (c.isNull("fuente")) {
                assertNull("url: $url", ref)
            } else {
                assertEquals("url: $url", c.getString("fuente"), ref?.fuente?.name?.lowercase())
                assertEquals("url: $url", c.getString("id"), ref?.id)
            }
        }
    }

    @Test
    fun todasEnTextoMezclado() {
        val t = "mira https://youtu.be/dQw4w9WgXcQ y el disco https://open.spotify.com/album/5Z9iiGl2FcIfa3BMiv6OIw\n" +
            "https://open.spotify.com/track/4cOdK2wGLETKBW3PvgPWqT?si=1 otra vez " +
            "https://www.youtube.com/watch?v=dQw4w9WgXcQ https://spotify.link/AbCd"
        assertEquals(
            listOf("youtube:dQw4w9WgXcQ", "spotify:4cOdK2wGLETKBW3PvgPWqT"),
            EnlaceMusica.todas(t).map { it.clave }
        )
        assertEquals(listOf("https://spotify.link/AbCd"), EnlaceMusica.cortosSpotify(t))
    }

    @Test
    fun urlCanonica() {
        assertEquals(
            "https://open.spotify.com/track/4cOdK2wGLETKBW3PvgPWqT",
            RefCancion(FuenteMusica.SPOTIFY, "4cOdK2wGLETKBW3PvgPWqT").url()
        )
        assertEquals(
            "https://www.youtube.com/watch?v=dQw4w9WgXcQ",
            RefCancion(FuenteMusica.YOUTUBE, "dQw4w9WgXcQ").url()
        )
        val r = RefCancion(FuenteMusica.YOUTUBE, "dQw4w9WgXcQ")
        assertEquals(r, RefCancion.deClave(r.clave))
        assertNull(RefCancion.deClave("x"))
        assertNull(RefCancion.deClave("tiktok:123"))
    }

    @Test
    fun idDeSpotifyEsBase62De22() {
        assertNull(EnlaceMusica.de("https://open.spotify.com/track/4cOdK2wGLETKBW3PvgPWqTX"))
        assertNull(EnlaceMusica.de("spotify:track:corto"))
        assertNull(RefCancion.deClave("spotify:corto"))
    }
}
