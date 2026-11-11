package com.fcplus.forocoches

import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.widget.ImageView
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PortadasMusicaTest {
    private val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val cache = HashMap<String, Bitmap>()
    private val pendientes = LinkedHashMap<String, () -> Unit>()

    private fun pintar(iv: ImageView, url: String?) =
        PortadasMusica.pintar(ctx, iv, url, { cache[it] }, { u, listo -> pendientes[u] = listo })

    private fun llega(url: String): Bitmap {
        val b = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888)
        cache[url] = b
        pendientes.remove(url)!!.invoke()
        return b
    }

    private fun bitmapDe(iv: ImageView): Bitmap? = (iv.drawable as? BitmapDrawable)?.bitmap

    @Test
    fun `una portada que llega tarde no pisa la de la cancion que ahora ocupa la fila`() {
        val iv = ImageView(ctx)
        pintar(iv, "https://x/a.jpg") // la fila era de A
        pintar(iv, "https://x/b.jpg") // al desplazar, la misma vista pasa a B
        llega("https://x/a.jpg") // la de A llega tarde
        assertFalse("la portada de A no debe pintarse sobre B", bitmapDe(iv) === cache["https://x/a.jpg"])
        val b = llega("https://x/b.jpg")
        assertSame(b, bitmapDe(iv))
        assertEquals(0, iv.paddingLeft)
    }

    @Test
    fun `sin url la fila se queda con el icono y no la pisa ninguna portada pendiente`() {
        val iv = ImageView(ctx)
        pintar(iv, "https://x/a.jpg")
        pintar(iv, null)
        llega("https://x/a.jpg")
        assertTrue(bitmapDe(iv) !== cache["https://x/a.jpg"])
        assertTrue(iv.paddingLeft > 0)
    }

    @Test
    fun `una portada ya en cache se pinta al momento y sin el relleno del icono`() {
        val iv = ImageView(ctx)
        pintar(iv, "https://x/a.jpg") // icono con relleno
        val b = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888)
        cache["https://x/c.jpg"] = b
        pintar(iv, "https://x/c.jpg")
        assertSame(b, bitmapDe(iv))
        assertEquals(0, iv.paddingLeft)
    }
}
