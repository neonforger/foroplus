package com.fcplus.forocoches

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Reconocer un GIF por sus bytes.
 *
 * Se hace por la CABECERA del fichero y no por la extensión de la URL: en el foro se pegan
 * enlaces de imgur y compañía que no terminan en `.gif`, y al revés, una URL acabada en `.gif`
 * puede servir cualquier cosa.
 */
class GifAnimadoTest {

    private fun bytes(cabecera: String, relleno: Int = 20) =
        cabecera.toByteArray(Charsets.US_ASCII) + ByteArray(relleno)

    @Test
    fun `un GIF89a es un gif`() {
        assertTrue(GifAnimado.esGif(bytes("GIF89a")))
    }

    @Test
    fun `un GIF87a tambien`() {
        assertTrue(GifAnimado.esGif(bytes("GIF87a")))
    }

    @Test
    fun `un PNG no lo es`() {
        assertFalse(GifAnimado.esGif(byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte(), 13, 10, 26, 10)))
    }

    @Test
    fun `un JPEG no lo es`() {
        assertFalse(GifAnimado.esGif(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(), 0, 16)))
    }

    @Test
    fun `un fichero mas corto que la cabecera no revienta`() {
        assertFalse(GifAnimado.esGif(byteArrayOf()))
        assertFalse(GifAnimado.esGif("GIF".toByteArray(Charsets.US_ASCII)))
    }

    @Test
    fun `un fichero que empieza por GIF pero no es la firma no cuela`() {
        // "GIFT..." no es un GIF: el cuarto byte de la firma es siempre el 8.
        assertFalse(GifAnimado.esGif(bytes("GIFTED")))
    }
}
