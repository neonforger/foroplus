package com.fcplus.forocoches

import androidx.exifinterface.media.ExifInterface
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.ByteArrayInputStream

/**
 * Que sepamos LEER la etiqueta de un JPEG de verdad, no solo traducirla.
 *
 * `foto_vertical_exif6.jpg` es el caso exacto del bug de las fotos tumbadas: **120x90
 * guardados** —apaisado, como lo deja el sensor— con la **etiqueta 6** diciendo que en realidad
 * hay que verla vertical. Es lo que produce cualquier móvil al hacer una foto en vertical.
 *
 * Necesita Robolectric: `ExifInterface` de androidx usa el framework de Android y en una JVM
 * pelada revienta con `NoClassDefFoundError`.
 */
@RunWith(RobolectricTestRunner::class)
class ExifFotoTest {

    private fun fixture(): ByteArray =
        javaClass.classLoader!!.getResourceAsStream("foto_vertical_exif6.jpg")!!.readBytes()

    private fun giroDe(bytes: ByteArray): GiroFoto {
        val exif = ExifInterface(ByteArrayInputStream(bytes))
        return OrientacionFoto.de(
            exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, OrientacionFoto.NORMAL)
        )
    }

    @Test
    fun `la etiqueta se lee del fichero real`() {
        val exif = ExifInterface(ByteArrayInputStream(fixture()))
        assertEquals(6, exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, OrientacionFoto.NORMAL))
    }

    @Test
    fun `una foto de movil vertical pide 90 grados`() {
        val giro = giroDe(fixture())
        assertTrue(giro.hayQueGirar)
        assertEquals(90, giro.grados)
        assertFalse(giro.espejo)
    }

    /** Sin EXIF (una captura de pantalla, por ejemplo) no se toca la foto. */
    @Test
    fun `un fichero sin exif se deja en paz`() {
        assertFalse(giroDe(ByteArray(64)).hayQueGirar)
    }
}
