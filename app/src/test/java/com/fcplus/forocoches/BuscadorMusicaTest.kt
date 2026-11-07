package com.fcplus.forocoches

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BuscadorMusicaTest {

    @Test fun aceptaSoloHttpsYHostExacto() {
        assertTrue(BuscadorMusica.aceptada("https://www.youtube.com/oembed?url=x&format=json"))
        assertTrue(BuscadorMusica.aceptada("https://open.spotify.com/track/4cOdK2wGLETKBW3PvgPWqT"))
        assertTrue(BuscadorMusica.aceptada("https://spotify.link/abc123"))
        assertFalse(BuscadorMusica.aceptada("http://www.youtube.com/oembed"))
        assertFalse(BuscadorMusica.aceptada("https://evil.com/?u=www.youtube.com"))
        assertFalse(BuscadorMusica.aceptada("https://www.youtube.com.evil.com/"))
        assertFalse(BuscadorMusica.aceptada("https://evil.www.youtube.com/"))
        assertFalse(BuscadorMusica.aceptada("https://www.youtube.com@evil.com/"))
        assertFalse(BuscadorMusica.aceptada("https://user@www.youtube.com/"))
        assertFalse(BuscadorMusica.aceptada("https://www.youtube.com:8443/"))
        assertFalse(BuscadorMusica.aceptada("no es una url"))
    }

    @Test fun portadaSoloDeHostsDeImagenes() {
        val ok = "https://i.ytimg.com/vi/x/hq.jpg"
        assertEquals(ok, BuscadorMusica.portadaAceptada(ok))
        assertEquals("https://i.scdn.co/image/abc", BuscadorMusica.portadaAceptada("https://i.scdn.co/image/abc"))
        assertNull(BuscadorMusica.portadaAceptada("http://i.ytimg.com/vi/x/hq.jpg"))
        assertNull(BuscadorMusica.portadaAceptada("https://evil.com/i.ytimg.com.jpg"))
        assertNull(BuscadorMusica.portadaAceptada("https://i.ytimg.com.evil.com/a.jpg"))
        assertNull(BuscadorMusica.portadaAceptada(null))
    }

    @Test fun urlOembed() {
        assertEquals(
            "https://www.youtube.com/oembed?url=https%3A%2F%2Fwww.youtube.com%2Fwatch%3Fv%3DdQw4w9WgXcQ&format=json",
            BuscadorMusica.urlOembed(RefCancion(FuenteMusica.YOUTUBE, "dQw4w9WgXcQ"))
        )
        assertEquals(
            "https://open.spotify.com/oembed?url=https%3A%2F%2Fopen.spotify.com%2Ftrack%2F4cOdK2wGLETKBW3PvgPWqT",
            BuscadorMusica.urlOembed(RefCancion(FuenteMusica.SPOTIFY, "4cOdK2wGLETKBW3PvgPWqT"))
        )
    }

    @Test fun urlPaginaSoloSpotify() {
        assertNull(BuscadorMusica.urlPagina(RefCancion(FuenteMusica.YOUTUBE, "dQw4w9WgXcQ")))
        assertEquals(
            "https://open.spotify.com/track/4cOdK2wGLETKBW3PvgPWqT",
            BuscadorMusica.urlPagina(RefCancion(FuenteMusica.SPOTIFY, "4cOdK2wGLETKBW3PvgPWqT"))
        )
    }

    @Test fun portadaDeLaPaginaSeFiltraPorHost() {
        val bueno = """<meta content="https://i.scdn.co/image/abc" property="og:image"/>"""
        assertEquals("https://i.scdn.co/image/abc", BuscadorMusica.portadaDePagina(bueno))
        assertNull(BuscadorMusica.portadaDePagina("""<meta property="og:image" content="https://evil.com/a.jpg">"""))
        assertNull(BuscadorMusica.portadaDePagina("<html></html>"))
    }
}
