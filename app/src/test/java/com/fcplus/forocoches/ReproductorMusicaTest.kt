package com.fcplus.forocoches

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * La caja del vídeo de Música: 16:9 a todo el ancho (spec), salvo cuando no cabe (en horizontal
 * se comería el panel). En los dos casos sigue por encima de los 200×200 px que pide YouTube.
 */
class ReproductorMusicaTest {

    @Test
    fun `en vertical es 16 a 9 del ancho`() {
        // El Samsung de pruebas: 1080 × 2340.
        assertEquals(607, ReproductorMusica.altoCaja(1080, 2340))
        assertEquals(405, ReproductorMusica.altoCaja(720, 1600))
    }

    @Test
    fun `en horizontal se queda en el 55 por ciento del alto y sigue pasando de 200`() {
        val alto = ReproductorMusica.altoCaja(2340, 1080)
        assertEquals(594, alto)
        assertTrue(alto > 200)
    }
}
