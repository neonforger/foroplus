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

    // ── Cuando la lista cambia debajo del reproductor (reordenar, quitar) ──────

    private val claves = listOf("youtube:a", "youtube:b", "spotify:c", "youtube:d")

    @Test
    fun reubicarConLaCargadaTodaviaEnLaListaLaSigue() {
        // Sonando "youtube:b" (estaba en 1) y se ha movido al final.
        val nuevas = listOf("youtube:a", "spotify:c", "youtube:d", "youtube:b")
        assertEquals(Reubicar.Sigue(3), ColaMusica.reubicar("youtube:b", 1, nuevas, listOf(YT, SP, YT, YT), NADA, sonando = true))
        assertEquals(Reubicar.Sigue(3), ColaMusica.reubicar("youtube:b", 1, nuevas, listOf(YT, SP, YT, YT), NADA, sonando = false))
    }

    @Test
    fun reubicarSiSeQuitaLaQueSuenaEntraLaQueOcupaSuSitio() {
        // Sonando "youtube:b" en 1; se quita: en 1 queda Spotify, que es una parada.
        val nuevas = listOf("youtube:a", "spotify:c", "youtube:d")
        assertEquals(Reubicar.Sustituir(ParadaSpotify(1)),
            ColaMusica.reubicar("youtube:b", 1, nuevas, listOf(YT, SP, YT), NADA, sonando = true))
        // La que ocupa su sitio no se puede oír aquí: la siguiente.
        assertEquals(Reubicar.Sustituir(Reproducir(2)),
            ColaMusica.reubicar("youtube:b", 1, nuevas, listOf(YT, SP, YT), setOf(1), sonando = true))
        // Era la última: se acabó.
        assertEquals(Reubicar.Sustituir(Fin),
            ColaMusica.reubicar("youtube:d", 3, claves.dropLast(1), listOf(YT, YT, SP), NADA, sonando = true))
    }

    @Test
    fun reubicarParadoSeQuedaEnElMismoSitioSinSalirseDeLaLista() {
        // Parado con "Seguir con youtube:d" (3) y se quita: apunta a la que quede en su sitio o antes.
        assertEquals(Reubicar.Parado(2), ColaMusica.reubicar("youtube:d", 3, claves.dropLast(1), listOf(YT, YT, SP), NADA, sonando = false))
        assertEquals(Reubicar.Parado(1), ColaMusica.reubicar("youtube:b", 1, listOf("youtube:a", "spotify:c", "youtube:d"), listOf(YT, SP, YT), NADA, sonando = false))
        // Sin ninguna cargada, ninguna; y una lista vaciada se queda sin posición.
        assertEquals(Reubicar.Parado(-1), ColaMusica.reubicar(null, -1, claves, lista, NADA, sonando = false))
        assertEquals(Reubicar.Parado(-1), ColaMusica.reubicar("youtube:a", 0, emptyList(), emptyList(), NADA, sonando = false))
    }

    @Test
    fun seguirDesdeDondeIbaOEmpezarDeNuevo() {
        assertEquals(2, ColaMusica.seguirDesde(total = 4, actual = 2, acabada = false))
        assertEquals(0, ColaMusica.seguirDesde(total = 4, actual = -1, acabada = false))
        // Se acabó la playlist: volver a empezar.
        assertEquals(0, ColaMusica.seguirDesde(total = 4, actual = 3, acabada = true))
        // Una posición que ya no existe no se sale de la lista.
        assertEquals(1, ColaMusica.seguirDesde(total = 2, actual = 5, acabada = false))
        assertEquals(null, ColaMusica.seguirDesde(total = 0, actual = 0, acabada = false))
    }
}
