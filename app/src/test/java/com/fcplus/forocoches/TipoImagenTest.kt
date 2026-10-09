package com.fcplus.forocoches

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TipoImagenTest {

    private fun bytes(vararg b: Int) = ByteArray(b.size) { b[it].toByte() }
    private fun ascii(s: String) = s.toByteArray(Charsets.US_ASCII)

    @Test fun `un GIF se reconoce por su cabecera, sea 87a u 89a`() {
        assertEquals(TipoImagen.GIF, TipoImagen.de(ascii("GIF89a") + ByteArray(20)))
        assertEquals(TipoImagen.GIF, TipoImagen.de(ascii("GIF87a") + ByteArray(20)))
    }

    @Test fun `PNG y JPEG por sus bytes magicos`() {
        assertEquals(TipoImagen.PNG, TipoImagen.de(bytes(0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0, 0)))
        assertEquals(TipoImagen.JPEG, TipoImagen.de(bytes(0xFF, 0xD8, 0xFF, 0xE0, 0, 0x10)))
    }

    @Test fun `WEBP exige RIFF y WEBP en su sitio, no basta con RIFF`() {
        assertEquals(TipoImagen.WEBP, TipoImagen.de(ascii("RIFF") + bytes(0, 0, 0, 0) + ascii("WEBPVP8 ")))
        // Un WAV también empieza por RIFF: no es una imagen.
        assertNull(TipoImagen.de(ascii("RIFF") + bytes(0, 0, 0, 0) + ascii("WAVEfmt ")))
    }

    @Test fun `lo que no es imagen da null, y no revienta con ficheros cortos`() {
        assertNull(TipoImagen.de(ascii("<!DOCTYPE html><html>")))   // una página de error de FC
        assertNull(TipoImagen.de(ByteArray(0)))
        assertNull(TipoImagen.de(ascii("GIF")))
        assertNull(TipoImagen.de(bytes(0xFF, 0xD8)))
    }

    @Test fun `cada tipo lleva su mime y su extension`() {
        assertEquals("image/gif" to "gif", TipoImagen.GIF.mime to TipoImagen.GIF.extension)
        assertEquals("image/jpeg" to "jpg", TipoImagen.JPEG.mime to TipoImagen.JPEG.extension)
        assertEquals("image/png" to "png", TipoImagen.PNG.mime to TipoImagen.PNG.extension)
        assertEquals("image/webp" to "webp", TipoImagen.WEBP.mime to TipoImagen.WEBP.extension)
    }

    @Test fun `el nombre lleva la marca, la fecha con segundos y la extension del tipo real`() {
        // 2026-10-10 18:05:09 en la zona que se pase: el nombre no depende de la del móvil del test.
        val zona = java.util.TimeZone.getTimeZone("Europe/Madrid")
        val cal = java.util.Calendar.getInstance(zona).apply {
            clear(); set(2026, java.util.Calendar.OCTOBER, 10, 18, 5, 9)
        }
        assertEquals("ForoPlus_20261010_180509.gif", TipoImagen.GIF.nombre(cal.timeInMillis, zona))
        assertEquals("ForoPlus_20261010_180509.jpg", TipoImagen.JPEG.nombre(cal.timeInMillis, zona))
    }
}
