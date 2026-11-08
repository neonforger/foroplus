package com.fcplus.forocoches

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * La caja del vídeo de Música: 16:9 a todo el ancho (spec) y, pase lo que pase con el sitio,
 * nunca menos de 200 de alto en px CSS, que es lo que pide YouTube. La página del embed usa
 * width=device-width, así que un px CSS es un dp: el suelo se comprueba en dp. Medido en el
 * Samsung de pruebas: 1080 × 2340 a 480 dpi (3 px por dp), y un WebView de 1008 px de ancho
 * mide 336 px CSS.
 */
class ReproductorMusicaTest {

    private val samsung = 3.0f

    /** El alto de la caja en dp (= px CSS del reproductor). */
    private fun enDp(px: Int, densidad: Float) = px / densidad

    @Test
    fun `en vertical es 16 a 9 del ancho`() {
        // Con el panel de unos 1730 px y sin límite del padre.
        assertEquals(607, ReproductorMusica.altoCaja(1080, 2340, 1730, samsung))
        assertEquals(607, ReproductorMusica.altoCaja(1080, 2340, null, samsung))
        assertTrue(enDp(607, samsung) >= ReproductorMusica.MINIMO_DP)
    }

    @Test
    fun `en un movil estrecho el 16 a 9 no llega y manda el minimo de YouTube`() {
        // 320 dp de ancho: el 16:9 serían 180 dp.
        val alto = ReproductorMusica.altoCaja(640, 1280, 1000, 2.0f)
        assertEquals(400, alto)
        assertEquals(200f, enDp(alto, 2.0f))
    }

    @Test
    fun `en horizontal el video no baja de 200 dp y la barra y la lista se quedan con lo que sobre`() {
        // Sin límite del padre: el 55 % del alto de la pantalla (594 px = 198 dp) sube al mínimo.
        assertEquals(600, ReproductorMusica.altoCaja(2340, 1080, null, samsung))
        // Con sitio de sobra para el vídeo, la barra y una fila: 16:9 recortado por la reserva.
        assertEquals(728, ReproductorMusica.altoCaja(2340, 1600, 1100, samsung))
        // Panel de 700 px: dejar sitio a la barra y a una fila (372 px) dejaría el vídeo en
        // 328 px = 109 dp. Manda YouTube: 200 dp.
        val alto = ReproductorMusica.altoCaja(2340, 1080, 700, samsung)
        assertEquals(600, alto)
        assertTrue(enDp(alto, samsung) >= ReproductorMusica.MINIMO_DP)
    }

    @Test
    fun `con menos sitio que el minimo el video se queda con todo lo que haya, nunca mas`() {
        assertEquals(450, ReproductorMusica.altoCaja(2340, 1080, 450, samsung))
        assertEquals(0, ReproductorMusica.altoCaja(1080, 2340, 0, samsung))
    }
}
