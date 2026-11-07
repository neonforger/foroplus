package com.fcplus.forocoches

import com.fcplus.forocoches.Paso.Fin
import com.fcplus.forocoches.Paso.ParadaSpotify
import com.fcplus.forocoches.Paso.Reproducir
import org.junit.Assert.assertEquals
import org.junit.Test

class ColaMusicaTest {

    private val YT = FuenteMusica.YOUTUBE
    private val SP = FuenteMusica.SPOTIFY
    private val NADA = emptySet<Int>()

    private val lista = listOf(YT, YT, SP, YT)

    @Test
    fun siguienteSaltaAlSiguienteYouTube() {
        assertEquals(Reproducir(1), ColaMusica.siguiente(lista, 0, NADA))
    }

    @Test
    fun siguienteParaEnSpotify() {
        assertEquals(ParadaSpotify(2), ColaMusica.siguiente(lista, 1, NADA))
    }

    @Test
    fun siguienteSaltaLosIndicesMarcados() {
        assertEquals(ParadaSpotify(2), ColaMusica.siguiente(lista, 0, setOf(1)))
    }

    @Test
    fun siguienteDelUltimoEsFin() {
        assertEquals(Fin, ColaMusica.siguiente(lista, 3, NADA))
    }

    @Test
    fun anteriorParaEnSpotify() {
        assertEquals(ParadaSpotify(2), ColaMusica.anterior(lista, 3, NADA))
    }

    @Test
    fun anteriorDelPrimeroEsFin() {
        assertEquals(Fin, ColaMusica.anterior(lista, 0, NADA))
    }

    @Test
    fun listaVaciaEsFin() {
        assertEquals(Fin, ColaMusica.siguiente(emptyList(), 0, NADA))
        assertEquals(Fin, ColaMusica.anterior(emptyList(), 0, NADA))
    }

    @Test
    fun siguienteDesdeFueraDeRangoNoRompe() {
        // Desde -1 la primera es la 0; desde más allá del final no hay nada después.
        assertEquals(Reproducir(0), ColaMusica.siguiente(lista, -1, NADA))
        assertEquals(Fin, ColaMusica.siguiente(lista, 9, NADA))
    }

    @Test
    fun anteriorDesdeFueraDeRangoNoRompe() {
        assertEquals(Fin, ColaMusica.anterior(lista, -1, NADA))
        assertEquals(Reproducir(1), ColaMusica.anterior(lista, 9, setOf(3, 2)))
    }
}
