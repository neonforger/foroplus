package com.fcplus.forocoches

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MusicaTest {
    @Test fun nombreColapsaEspacios() =
        assertEquals("Para la autovía", Musica.nombreLimpio("  Para   la autovía "))

    @Test fun nombreDe60Vale() = assertEquals("a".repeat(60), Musica.nombreLimpio("a".repeat(60)))

    @Test fun nombreDe61NoVale() = assertNull(Musica.nombreLimpio("a".repeat(61)))

    @Test fun nombreVacioNoVale() {
        assertNull(Musica.nombreLimpio(""))
        assertNull(Musica.nombreLimpio("   "))
    }

    @Test fun nombreLibreSinChoque() = assertEquals("Rock", Musica.nombreLibre("Rock", listOf("Pop")))

    @Test fun nombreLibreSaltaLosOcupados() =
        assertEquals("Rock (3)", Musica.nombreLibre("Rock", listOf("Rock", "Rock (2)")))

    @Test fun nombreLibreRespetaElTope() {
        val largo = "a".repeat(60)
        val libre = Musica.nombreLibre(largo, listOf(largo))
        assertTrue(libre.length <= Musica.MAX_NOMBRE)
        assertTrue(libre.endsWith(" (2)"))
    }
}
