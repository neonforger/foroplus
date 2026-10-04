package com.fcplus.forocoches

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * El bug: **las fotos subidas desde la app salían tumbadas.**
 *
 * La cámara del móvil no gira los píxeles: los guarda como los leyó el sensor y anota una
 * etiqueta EXIF diciendo cómo hay que verlos. La galería y el navegador la respetan, así que la
 * foto se ve bien... hasta que la app la toca:
 *
 *   1. `BitmapFactory` **ignora** el EXIF y devuelve los píxeles tal cual (tumbados).
 *   2. `Bitmap.compress()` **no escribe** EXIF en la salida.
 *
 * Resultado: la copia que sube a catbox tiene los píxeles tumbados **y ya no lleva la etiqueta
 * que decía cómo enderezarlos**, así que se queda tumbada para siempre. Por eso se ve bien en
 * la galería del que la sube y torcida en el foro.
 *
 * El arreglo es girar los píxeles ANTES de comprimir. Aquí está la tabla de qué giro toca.
 */
class OrientacionFotoTest {

    @Test
    fun `sin etiqueta no se gira nada`() {
        val t = OrientacionFoto.de(OrientacionFoto.NORMAL)
        assertEquals(0, t.grados)
        assertFalse(t.espejo)
        assertFalse("no hay nada que hacer", t.hayQueGirar)
    }

    /** El caso de todos los días: foto vertical con el móvil en la mano. */
    @Test
    fun `orientacion 6 son 90 grados`() {
        val t = OrientacionFoto.de(6)
        assertEquals(90, t.grados)
        assertFalse(t.espejo)
        assertTrue(t.hayQueGirar)
    }

    @Test
    fun `orientacion 3 son 180 grados`() {
        assertEquals(180, OrientacionFoto.de(3).grados)
    }

    /** Vertical al revés, típico de la cámara frontal según cómo agarres el móvil. */
    @Test
    fun `orientacion 8 son 270 grados`() {
        assertEquals(270, OrientacionFoto.de(8).grados)
    }

    @Test
    fun `las orientaciones con espejo se reconocen`() {
        assertTrue(OrientacionFoto.de(2).espejo)
        assertTrue(OrientacionFoto.de(4).espejo)
        assertTrue(OrientacionFoto.de(5).espejo)
        assertTrue(OrientacionFoto.de(7).espejo)
        assertFalse(OrientacionFoto.de(1).espejo)
        assertFalse(OrientacionFoto.de(6).espejo)
    }

    @Test
    fun `el espejo vertical es un espejo horizontal mas media vuelta`() {
        // Asi lo expresa la matriz: no hay un flip vertical aparte.
        val t = OrientacionFoto.de(4)
        assertEquals(180, t.grados)
        assertTrue(t.espejo)
    }

    @Test
    fun `las diagonales combinan giro y espejo`() {
        assertEquals(90, OrientacionFoto.de(5).grados)
        assertEquals(270, OrientacionFoto.de(7).grados)
    }

    /** Una etiqueta que no entendemos no puede estropear una foto que estaba bien. */
    @Test
    fun `un valor desconocido se trata como normal`() {
        assertFalse(OrientacionFoto.de(0).hayQueGirar)
        assertFalse(OrientacionFoto.de(99).hayQueGirar)
        assertFalse(OrientacionFoto.de(-1).hayQueGirar)
    }

    /** El espejo solo, sin giro, también obliga a rehacer el bitmap. */
    @Test
    fun `solo espejo tambien cuenta como girar`() {
        assertTrue(OrientacionFoto.de(2).hayQueGirar)
    }
}
