package com.fcplus.forocoches

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlaylistCompartidaTest {
    private val yt = RefCancion(FuenteMusica.YOUTUBE, "abc12345678")
    private val sp = RefCancion(FuenteMusica.SPOTIFY, "4cOdK2wGLETKBW3PvgPWqT")
    private fun c(r: RefCancion, t: String, a: String?) = Cancion(r, t, a, null, null, null, 0L)
    private val dos = listOf(c(yt, "Como Camarón", "Estopa"), c(sp, "So payaso", null))

    @Test fun textoExacto() {
        val esperado = "Playlist «Para la autovía» (2 canciones)\n\n" +
            "1. Estopa – Como Camarón\nhttps://www.youtube.com/watch?v=abc12345678\n" +
            "2. So payaso\nhttps://open.spotify.com/track/4cOdK2wGLETKBW3PvgPWqT\n\n" +
            "Hecha con ForoPlus Música"
        assertEquals(esperado, PlaylistCompartida.texto("Para la autovía", dos))
    }

    @Test fun singular() {
        val t = PlaylistCompartida.texto("X", dos.take(1))
        assertEquals("Playlist «X» (1 canción)", t.lines().first())
    }

    @Test fun idaYVuelta() {
        val i = PlaylistCompartida.leer(PlaylistCompartida.texto("Para la autovía", dos))
        assertEquals("Para la autovía", i.nombre)
        assertEquals(
            listOf(LineaImportada(yt, "Como Camarón", "Estopa"), LineaImportada(sp, "So payaso", null)),
            i.lineas
        )
    }

    @Test fun idaYVueltaConWindowsYEspacios() {
        val t = PlaylistCompartida.texto("Para la autovía", dos).lines().joinToString("\r\n") { "  $it  " }
        val i = PlaylistCompartida.leer(t)
        assertEquals("Para la autovía", i.nombre)
        assertEquals(2, i.lineas.size)
        assertEquals("Estopa", i.lineas[0].artista)
    }

    @Test fun textoCualquiera() {
        val i = PlaylistCompartida.leer(
            "mirad esto\nhttps://www.youtube.com/watch?v=abc12345678 y\nhttps://open.spotify.com/track/4cOdK2wGLETKBW3PvgPWqT\nhttps://youtu.be/zzz12345678"
        )
        assertNull(i.nombre)
        assertEquals(3, i.lineas.size)
        assertEquals(listOf(null, null, null), i.lineas.map { it.cancion })
    }

    @Test fun lineaSinEnlaceSeIgnora() {
        val i = PlaylistCompartida.leer("Playlist «A» (2 canciones)\n\n1. Sin enlace\n2. Otra\nhttps://www.youtube.com/watch?v=abc12345678")
        assertEquals(listOf(LineaImportada(yt, "Otra", null)), i.lineas)
    }

    @Test fun enlaceSinLineaNumeradaVaSinNombre() {
        val i = PlaylistCompartida.leer("Playlist «A» (1 canción)\n\nhola\nhttps://www.youtube.com/watch?v=abc12345678")
        assertEquals(listOf(LineaImportada(yt, null, null)), i.lineas)
    }

    @Test fun repetidaSeImportaUnaVez() {
        val i = PlaylistCompartida.leer("1. A – B\nhttps://www.youtube.com/watch?v=abc12345678\nhttps://youtu.be/abc12345678")
        assertEquals(listOf(LineaImportada(yt, "B", "A")), i.lineas)
    }
}
