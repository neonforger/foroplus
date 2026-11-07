package com.fcplus.forocoches

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Fixtures medidos el 2026-11-07 (oEmbed públicos y `<meta>` de la página pública de Spotify). */
class MetadatosMusicaTest {
    private fun recurso(n: String) =
        javaClass.classLoader!!.getResource("musica/$n").readText()

    @Test fun oembedYoutube() {
        val d = MetadatosMusica.oembed(recurso("yt_oembed.json"))!!
        assertEquals("Rick Astley - Never Gonna Give You Up (Official Video) (4K Remaster)", d.titulo)
        assertEquals("Rick Astley", d.autor)
        assertEquals("https://i.ytimg.com/vi/dQw4w9WgXcQ/hqdefault.jpg", d.portada)
    }

    @Test fun oembedSpotify() {
        val d = MetadatosMusica.oembed(recurso("sp_oembed.json"))!!
        assertEquals("Never Gonna Give You Up", d.titulo)
        assertNull(d.autor)
        assertTrue(d.portada!!.startsWith("https://image-cdn-fa.spotifycdn.com/"))
    }

    @Test fun oembedSinTituloOInvalido() {
        assertNull(MetadatosMusica.oembed("{}"))
        assertNull(MetadatosMusica.oembed("no es json"))
        assertNull(MetadatosMusica.oembed("""{"title":"  "}"""))
    }

    @Test fun paginaSpotify() {
        val d = MetadatosMusica.paginaSpotify(recurso("sp_track_meta.html"))
        assertEquals("Rick Astley", d.artista)
        assertEquals(214, d.duracionSeg)
        assertEquals("Whenever You Need Somebody", d.album)
    }

    @Test fun paginaSpotifyVariosArtistas() {
        val d = MetadatosMusica.paginaSpotify(recurso("sp_track_varios_meta.html"))
        assertEquals("Bizarrap, Quevedo", d.artista)
        assertEquals(199, d.duracionSeg)
        assertNotNull(d.album)
    }

    @Test fun paginaRotaNoLanza() {
        val d = MetadatosMusica.paginaSpotify("<html>roto")
        assertNull(d.artista); assertNull(d.duracionSeg); assertNull(d.album)
    }

    @Test fun entidadesYOrdenDeAtributos() {
        val html = """<meta content="Guns &amp; Roses &#x27;n&quot; Co" name="music:musician_description"/>
            <meta content="120" property='music:duration'>
            <meta name="og:description" content="Guns &amp; Roses &middot; Album &#183; Song"/>"""
        val d = MetadatosMusica.paginaSpotify(html)
        assertEquals("Guns & Roses 'n\" Co", d.artista)
        assertEquals(120, d.duracionSeg)
        assertEquals("Album", d.album)
    }
}
