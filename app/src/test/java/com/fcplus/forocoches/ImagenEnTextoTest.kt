package com.fcplus.forocoches

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ImagenEnTextoTest {

    @Test
    fun `un smiley de FC se escala a su tamano real en dp`() {
        // Medido por CDP sobre el foro: qmeparto.gif son 20x15 px. En un movil de densidad 3
        // se pintaba a 20 px fisicos (6,7dp) y por eso "se veia muy pequeno".
        val (w, h) = ImagenEnTexto.medida(20, 15, 3f, 842)
        assertEquals(60, w)
        assertEquals(45, h)
    }

    @Test
    fun `una foto grande sigue cabiendo en la pantalla`() {
        val (w, h) = ImagenEnTexto.medida(1000, 500, 3f, 842)
        assertEquals(842, w)
        // La proporcion se respeta: 2:1 sigue siendo 2:1.
        assertEquals(421, h)
    }

    @Test
    fun `escalar no puede hacer desaparecer una imagen`() {
        // Un icono de 1 px con densidad baja redondearia a 0 y no se dibujaria nada.
        val (w, h) = ImagenEnTexto.medida(1, 1, 0.75f, 842)
        assertTrue(w >= 1 && h >= 1)
    }

    @Test
    fun `una imagen sin medidas no se dibuja`() {
        assertEquals(0 to 0, ImagenEnTexto.medida(0, 10, 3f, 842))
    }

    @Test
    fun `los smilies son iconos y las fotos no`() {
        assertTrue(ImagenEnTexto.esIcono(20, 15))
        // El mayor smiley de FC (medido) mide 75x57.
        assertTrue(ImagenEnTexto.esIcono(75, 57))
        assertFalse(ImagenEnTexto.esIcono(400, 300))
        // Una imagen sin medidas no es "un icono muy pequeno": no es nada.
        assertFalse(ImagenEnTexto.esIcono(0, 0))
    }

    @Test
    fun `un icono del alto del texto queda centrado en el renglon`() {
        // Metricas tipicas de 15sp a densidad 3: ascent -30, descent 10 (alto de linea 40).
        val alto = 40
        val top = ImagenEnTexto.topDelIcono(-30, 10, alto)
        val base = ImagenEnTexto.baseDelIcono(-30, 10, alto)
        // Ocupa exactamente lo mismo que el texto, ni cuelga por debajo (que es como lo
        // alineaba Html.fromHtml: pegado a la BASE, top = -40, base = 0).
        assertEquals(-30, top)
        assertEquals(10, base)
    }

    @Test
    fun `un icono mas alto que el texto crece por arriba y por abajo`() {
        val alto = 60
        assertEquals(-40, ImagenEnTexto.topDelIcono(-30, 10, alto))
        assertEquals(20, ImagenEnTexto.baseDelIcono(-30, 10, alto))
        // Y no se sale mas por un lado que por el otro.
        val sobraArriba = -30 - ImagenEnTexto.topDelIcono(-30, 10, alto)
        val sobraAbajo = ImagenEnTexto.baseDelIcono(-30, 10, alto) - 10
        assertEquals(sobraArriba, sobraAbajo)
    }
}
