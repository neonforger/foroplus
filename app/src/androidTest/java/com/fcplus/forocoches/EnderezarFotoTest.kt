package com.fcplus.forocoches

import android.graphics.BitmapFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Que los píxeles se giren DE VERDAD, en un Android de verdad.
 *
 * Los tests de JVM cubren la tabla EXIF y la lectura de la etiqueta, pero no pueden cubrir
 * esto: `BitmapFactory`, `Matrix` y `Bitmap.createBitmap` bajo Robolectric son sombras que no
 * mueven un solo píxel. Y el proyecto ya se ha comido una vez el castigo de dar por bueno lo
 * que solo funcionaba en una réplica.
 *
 * `foto_vertical_exif6.jpg` son **120x90 guardados** (apaisado, como lo deja el sensor) con la
 * etiqueta **6**: al enderezarla tiene que quedar **90x120**, vertical.
 */
@RunWith(AndroidJUnit4::class)
class EnderezarFotoTest {

    private fun bytes(nombre: String): ByteArray =
        InstrumentationRegistry.getInstrumentation().context.assets.open(nombre).use { it.readBytes() }

    @Test
    fun una_foto_con_exif_6_acaba_vertical() {
        val crudo = bytes("foto_vertical_exif6.jpg")
        val original = BitmapFactory.decodeByteArray(crudo, 0, crudo.size)

        // Así la decodifica la app hoy: BitmapFactory IGNORA la etiqueta.
        assertEquals("el sensor la guarda apaisada", 120, original.width)
        assertEquals(90, original.height)

        val derecha = SubidorFotos.enderezar(original, crudo)

        // Y así tiene que quedar: girada 90 grados.
        assertEquals("tras enderezar debe quedar vertical", 90, derecha.width)
        assertEquals(120, derecha.height)
        assertTrue("debe ser un bitmap nuevo", derecha !== original)
    }

    /** Sin etiqueta no se toca nada, y se devuelve el MISMO objeto (no se gasta memoria). */
    @Test
    fun una_foto_sin_exif_no_se_toca() {
        val crudo = bytes("foto_vertical_exif6.jpg")
        val bmp = BitmapFactory.decodeByteArray(crudo, 0, crudo.size)
        val igual = SubidorFotos.enderezar(bmp, ByteArray(64))
        assertTrue("sin etiqueta no se rehace el bitmap", igual === bmp)
        assertEquals(120, igual.width)
    }
}
