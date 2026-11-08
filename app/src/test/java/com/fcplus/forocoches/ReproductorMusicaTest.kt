package com.fcplus.forocoches

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * La caja del vídeo de Música: 16:9 a todo el ancho (spec), salvo cuando no cabe (en horizontal
 * se comería el panel). En los dos casos sigue por encima de los 200×200 px que pide YouTube.
 */
class ReproductorMusicaTest {

    // reserva = la barra (56dp) y una fila de la lista (68dp); minimo = 90dp. A 2,625 px/dp.
    private val reserva = 325
    private val minimo = 236

    @Test
    fun `en vertical es 16 a 9 del ancho`() {
        // El Samsung de pruebas: 1080 × 2340, con el panel de unos 1730 px.
        assertEquals(607, ReproductorMusica.altoCaja(1080, 2340, 1730, reserva, minimo))
        assertEquals(607, ReproductorMusica.altoCaja(1080, 2340, null, reserva, minimo))
        assertEquals(405, ReproductorMusica.altoCaja(720, 1600, 1100, reserva, minimo))
    }

    @Test
    fun `en horizontal deja sitio a la barra y a una cancion, y el video no baja del minimo`() {
        // Sin límite del padre: el 55 % del alto de la pantalla.
        assertEquals(594, ReproductorMusica.altoCaja(2340, 1080, null, reserva, minimo))
        // Con el panel de 700 px: 700 - 325 = 375 para el vídeo.
        val alto = ReproductorMusica.altoCaja(2340, 1080, 700, reserva, minimo)
        assertEquals(375, alto)
        assertTrue(alto > 200)
        // Panel de 540: 215 se queda por debajo del mínimo, y manda el mínimo.
        assertEquals(minimo, ReproductorMusica.altoCaja(2340, 1080, 540, reserva, minimo))
    }

    @Test
    fun `con muy poco sitio el video se queda en lo que haya, nunca mas`() {
        assertEquals(200, ReproductorMusica.altoCaja(1080, 2340, 200, reserva, minimo))
        assertEquals(0, ReproductorMusica.altoCaja(1080, 2340, 0, reserva, minimo))
    }
}
