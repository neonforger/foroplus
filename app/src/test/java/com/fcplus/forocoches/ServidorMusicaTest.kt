package com.fcplus.forocoches

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ServidorMusicaTest {
    @Test fun baseEsHttps() = assertTrue(ServidorMusica.BASE.startsWith("https://"))

    @Test fun urlPegaLaRutaALaBase() {
        assertEquals(ServidorMusica.BASE + "/v1/yo", ServidorMusica.url("/v1/yo"))
        assertEquals(ServidorMusica.BASE + "/v1/yo", ServidorMusica.url("v1/yo"))
    }

    @Test fun soloAceptaLaBaseConV1() {
        val b = ServidorMusica.BASE
        assertTrue(ServidorMusica.aceptada("$b/v1/semana"))
        assertTrue(ServidorMusica.aceptada("$b/v1/playlists?orden=recientes"))
        assertFalse(ServidorMusica.aceptada(b))
        assertFalse(ServidorMusica.aceptada("$b/"))
        assertFalse(ServidorMusica.aceptada("$b/otra"))
        assertFalse(ServidorMusica.aceptada("$b.evil.com/v1/semana"))
        assertFalse(ServidorMusica.aceptada("http://foroplus-api.PENDIENTE.workers.dev/v1/semana"))
        assertFalse(ServidorMusica.aceptada("https://evil.com/v1/semana"))
        assertFalse(ServidorMusica.aceptada("https://evil.com/?$b/v1/"))
    }
}
