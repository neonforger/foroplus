package com.fcplus.forocoches

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EstadoYoutubeTest {
    @Test fun `101 y 150 son vídeos que no se pueden incrustar`() {
        assertTrue(EstadoYoutube.noIncrustable(101))
        assertTrue(EstadoYoutube.noIncrustable(150))
    }

    @Test fun `otros errores si se pueden incrustar`() {
        assertFalse(EstadoYoutube.noIncrustable(2))
        assertFalse(EstadoYoutube.noIncrustable(100))
        assertFalse(EstadoYoutube.noIncrustable(0))
    }

    @Test fun `los codigos de error documentados de YouTube son error`() {
        for (c in listOf(2, 5, 100, 101, 150)) assertTrue("codigo $c", EstadoYoutube.esError(c))
    }

    @Test fun `estados normales no son error`() {
        for (c in listOf(-1, 0, 1, 2 + 1, 4)) assertFalse("codigo $c", EstadoYoutube.esError(c))
    }

    @Test fun `terminado es el estado 0`() {
        assertEquals(0, EstadoYoutube.TERMINADO)
    }

    @Test fun `ids de YouTube validos solo con su juego de caracteres`() {
        assertTrue(EstadoYoutube.idValido("dQw4w9WgXcQ"))
        assertTrue(EstadoYoutube.idValido("a-_B1234567"))
        assertFalse(EstadoYoutube.idValido(""))
        assertFalse(EstadoYoutube.idValido("dQw4w9WgXc"))
        assertFalse(EstadoYoutube.idValido("dQw4w9WgXcQ\"];alert(1)//"))
    }

    @Test fun `ids de Spotify son 22 caracteres base62`() {
        assertTrue(EstadoYoutube.idSpotifyValido("4uLU6hMCjMI75M1A2tKUQC"))
        assertFalse(EstadoYoutube.idSpotifyValido("4uLU6hMCjMI75M1A2tKUQ"))
        assertFalse(EstadoYoutube.idSpotifyValido("4uLU6hMCjMI75M1A2tKU-C"))
        assertFalse(EstadoYoutube.idSpotifyValido("4uLU6hMCjMI75M1A2tKUQC/x"))
    }
}
