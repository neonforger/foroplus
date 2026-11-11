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

    @Test fun `el errorCode de videoData es un error real y auth es no incrustable`() {
        // Medido el 2026-11-08: un vídeo de incrustar prohibido cargado con loadVideoById solo
        // avisa con videoData.errorCode = "auth".
        val auth = EstadoYoutube.codigoDeError("auth")
        assertTrue(EstadoYoutube.esError(auth))
        assertTrue(EstadoYoutube.noIncrustable(auth))
        val otro = EstadoYoutube.codigoDeError("net.badstatus")
        assertTrue(EstadoYoutube.esError(otro))
        assertFalse(EstadoYoutube.noIncrustable(otro))
    }

    @Test fun `solo lo que no se puede oir aqui queda marcado para la sesion`() {
        // 100 (borrado o privado), 101/150 (no deja incrustarlo) y "auth": por mucho que se
        // reintente no va a sonar aquí, así que la tarjeta ofrece abrirla fuera.
        for (c in listOf(100, 101, 150, EstadoYoutube.codigoDeError("auth")))
            assertTrue("codigo $c", EstadoYoutube.noSeOyeAqui(c))
        // 2, 5 y cualquier otro errorCode pueden ser de un momento: se salta sin marcarla.
        for (c in listOf(2, 5, EstadoYoutube.codigoDeError("net.badstatus")))
            assertFalse("codigo $c", EstadoYoutube.noSeOyeAqui(c))
    }

    @Test fun `ids de Spotify son 22 caracteres base62`() {
        assertTrue(EstadoYoutube.idSpotifyValido("4uLU6hMCjMI75M1A2tKUQC"))
        assertFalse(EstadoYoutube.idSpotifyValido("4uLU6hMCjMI75M1A2tKUQ"))
        assertFalse(EstadoYoutube.idSpotifyValido("4uLU6hMCjMI75M1A2tKU-C"))
        assertFalse(EstadoYoutube.idSpotifyValido("4uLU6hMCjMI75M1A2tKUQC/x"))
    }
}
