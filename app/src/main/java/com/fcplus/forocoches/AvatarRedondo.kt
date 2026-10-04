package com.fcplus.forocoches

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Shader
import android.util.LruCache

/**
 * Los avatares se pintan **redondos** en toda la app.
 *
 * No es capricho: el relleno de FC para quien no tiene foto (`ic_avatar_fc`) es un círculo, así
 * que dejar cuadrados los avatares de verdad hacía que en un mismo hilo convivieran dos formas
 * distintas para lo mismo.
 *
 * El recorte se **guarda hecho** porque esto lo llama el bind de un RecyclerView: sin caché,
 * cada fila creaba tres bitmaps (recorte, escalado y máscara) en el hilo de UI, y una lista se
 * recorre entera muchas veces. Son de 128x128, así que 200 caben de sobra.
 */
object AvatarRedondo {

    /** Lado del recorte. Cubre de sobra los 40dp del listado y los 34dp de los mensajes. */
    const val LADO = 128

    private val cache = object : LruCache<String, Bitmap>(200) {}

    /** Avatar redondo de [url] si la imagen ya está descargada; null si todavía no. */
    fun de(url: String): Bitmap? {
        if (url.isEmpty()) return null
        cache.get(url)?.let { return it }
        val bmp = PostImages.get(url) ?: return null
        return recorte(bmp, LADO).also { cache.put(url, it) }
    }

    /** Recorta al cuadrado centrado y lo enmascara en círculo, **sin deformar** la foto. */
    fun recorte(src: Bitmap, lado: Int = LADO): Bitmap {
        val corte = minOf(src.width, src.height).coerceAtLeast(1)
        val cuadrado = Bitmap.createBitmap(
            src, (src.width - corte) / 2, (src.height - corte) / 2, corte, corte
        )
        val escalado = Bitmap.createScaledBitmap(cuadrado, lado, lado, true)
        val out = Bitmap.createBitmap(lado, lado, Bitmap.Config.ARGB_8888)
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = BitmapShader(escalado, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
        }
        Canvas(out).drawCircle(lado / 2f, lado / 2f, lado / 2f, p)
        return out
    }
}
