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

    @Test
    fun `una playlist vacia lo dice sin poner cero canciones`() {
        assertEquals("Sin canciones", Musica.cuantas(0))
        assertEquals("1 canción", Musica.cuantas(1))
        assertEquals("200 canciones", Musica.cuantas(200))
    }

    @Test
    fun `la duracion de una cancion va en minutos y segundos, y con horas si las hay`() {
        assertEquals("0:00", Musica.duracion(0))
        assertEquals("0:07", Musica.duracion(7))
        assertEquals("3:25", Musica.duracion(205))
        assertEquals("1:02:03", Musica.duracion(3723))
    }

    @Test
    fun `el resumen solo lleva la duracion total si todas la tienen`() {
        fun con(seg: Int?) = Cancion(RefCancion(FuenteMusica.YOUTUBE, "id%08d".format(seg ?: 0)), "Tema", null, null, seg, null, 0L)
        assertEquals("Sin canciones", Musica.resumen(emptyList()))
        assertEquals("2 canciones · 7 min", Musica.resumen(listOf(con(200), con(220))))
        // Una sin saber: el total sería mentira.
        assertEquals("2 canciones", Musica.resumen(listOf(con(200), con(null))))
        // Menos de medio minuto no se queda en "0 min".
        assertEquals("1 canción · 1 min", Musica.resumen(listOf(con(10))))
        assertEquals("20 canciones · 1 h 05 min", Musica.resumen(List(20) { con(195) }))
    }

}
