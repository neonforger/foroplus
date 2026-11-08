package com.fcplus.forocoches

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class AnadirMusicaTest {
    private val yt = RefCancion(FuenteMusica.YOUTUBE, "abc12345678")
    private val sp = RefCancion(FuenteMusica.SPOTIFY, "4cOdK2wGLETKBW3PvgPWqT")
    private fun c(r: RefCancion, t: String, a: String?) = Cancion(r, t, a, null, null, null, 0L)

    @Test fun deUnaPlaylistCompartidaSacaNombreYNombresPorClave() {
        val texto = PlaylistCompartida.texto("Para la autovía", listOf(c(yt, "Como Camarón", "Estopa"), c(sp, "So payaso", null)))
        val p = AnadirMusica.plan(texto)
        assertEquals(listOf(yt, sp), p.refs)
        assertEquals("Para la autovía", p.nombre)
        assertEquals("Como Camarón" to "Estopa", p.nombres[yt.clave]?.let { it.first to it.second })
        assertEquals("So payaso" to null, p.nombres[sp.clave]?.let { it.first to it.second })
        assertTrue(p.cortos.isEmpty())
    }

    @Test fun deUnEnlaceSueltoUnaRefYSinNombre() {
        val p = AnadirMusica.plan("mira esto https://youtu.be/dQw4w9WgXcQ qué grande")
        assertEquals(listOf(RefCancion(FuenteMusica.YOUTUBE, "dQw4w9WgXcQ")), p.refs)
        assertNull(p.nombre)
        assertTrue(p.nombres.isEmpty())
    }

    @Test fun sinCancionesRefsVacio() {
        val p = AnadirMusica.plan("hola, esto no lleva nada https://example.com/x")
        assertTrue(p.refs.isEmpty())
        assertTrue(p.cortos.isEmpty())
        assertNull(p.nombre)
        assertTrue(AnadirMusica.plan("").refs.isEmpty())
    }

    @Test fun detectaCortosDeSpotify() {
        val p = AnadirMusica.plan("https://spotify.link/AbC123xyz y https://youtu.be/dQw4w9WgXcQ")
        assertEquals(listOf("https://spotify.link/AbC123xyz"), p.cortos)
        assertEquals(1, p.refs.size)
    }

    // CARRY de A5: PlaylistCompartida no recorta el nombre y AlmacenMusica.importar devuelve null con más de 60.
    @Test fun nombreLargoSeRecortaAlTopeYImportarFunciona() {
        val largo = "N".repeat(Musica.MAX_NOMBRE + 25)
        val p = AnadirMusica.plan(PlaylistCompartida.texto(largo, listOf(c(yt, "T", "A"))))
        assertEquals(Musica.MAX_NOMBRE, p.nombre!!.length)
        val dir = File.createTempFile("musica", "").let { it.delete(); it.mkdirs(); it }
        try {
            val creada = AlmacenMusica(dir).importar(p.nombre!!, listOf(c(yt, "T", "A")))
            assertNotNull(creada)
        } finally { dir.deleteRecursively() }
    }

    @Test fun nombreSoloEspaciosNoCuenta() {
        assertNull(AnadirMusica.nombreParaGuardar("   "))
        assertEquals("a b", AnadirMusica.nombreParaGuardar("  a   b "))
    }

    @Test fun combinarPrefiereLosNombresDelTextoYSinNadaDevuelveNull() {
        val datos = Cancion(yt, "Título YT", "Canal", "Álbum", 200, "https://i.ytimg.com/x.jpg", 5L)
        val r = AnadirMusica.combinar(yt, datos, "Mi título", null, 9L)!!
        assertEquals("Mi título", r.titulo)
        assertEquals("Canal", r.artista)
        assertEquals("Álbum", r.album)
        val sinRed = AnadirMusica.combinar(yt, null, "Solo texto", "Estopa", 9L)!!
        assertEquals("Solo texto", sinRed.titulo)
        assertEquals("Estopa", sinRed.artista)
        assertEquals(9L, sinRed.anadida)
        // Sin red y sin nombres la canción NO se pierde: se guarda con un título provisional y sin artista.
        val vacia = AnadirMusica.combinar(yt, null, null, null, 9L)
        assertEquals("Vídeo de YouTube abc12345678", vacia.titulo)
        assertNull(vacia.artista)
        assertEquals("Canción de Spotify ${sp.id}", AnadirMusica.combinar(sp, null, "  ", null, 9L).titulo)
        assertTrue(AnadirMusica.sinDatos(null, null, null))
        assertTrue(AnadirMusica.sinDatos(null, " ", null))
        assertTrue(!AnadirMusica.sinDatos(null, "Solo texto", null))
        assertTrue(!AnadirMusica.sinDatos(datos, null, null))
    }

    @Test fun cuentaLasSinDatos() {
        assertEquals("", AnadirMusica.avisoSinDatos(0))
        assertEquals(" 1 ha entrado sin título ni artista (no se pudo leer).", AnadirMusica.avisoSinDatos(1))
        assertEquals(" 3 han entrado sin título ni artista (no se pudo leer).", AnadirMusica.avisoSinDatos(3))
    }

    @Test fun editarUsaLosCamposTalCualAunqueBorresElArtista() {
        val datos = Cancion(yt, "T", "Canal", null, null, null, 5L)
        val e = AnadirMusica.editar(yt, datos, "  Otro ", "  ", 9L)!!
        assertEquals("Otro", e.titulo)
        assertNull(e.artista)
        assertNull(AnadirMusica.editar(yt, datos, "   ", "x", 9L))
    }
}
