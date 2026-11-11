package com.fcplus.forocoches

import android.graphics.Bitmap
import android.widget.ImageView
import androidx.core.content.ContextCompat

/** La portada de una canción (la del servidor) o, mientras llega, el icono de música. */
object PortadasMusica {
    /**
     * Pinta `url` en `iv`. La vista se recicla (lista del feed, resultados de búsqueda): cuando la portada
     * llega tarde, la fila ya puede ser de otra canción, y sin comprobarlo una portada vieja pisaba la
     * nueva al desplazar. Por eso `iv.tag` guarda la URL que toca y el callback solo pinta si sigue siendo
     * esa. `obtener`/`cargar` son la caché de imágenes; los tests ponen las suyas.
     */
    fun pintar(
        ctx: android.content.Context, iv: ImageView, url: String?,
        obtener: (String) -> Bitmap? = PostImages::get,
        cargar: (String, () -> Unit) -> Unit = PostImages::load
    ) {
        iv.tag = url
        val bmp = url?.let(obtener)
        if (bmp != null) {
            ponerPortada(iv, bmp)
            return
        }
        val pad = (12 * ctx.resources.displayMetrics.density).toInt()
        iv.setImageResource(R.drawable.ic_musica)
        iv.setPadding(pad, pad, pad, pad)
        iv.imageTintList = ContextCompat.getColorStateList(ctx, R.color.fc_rojo)
        if (url != null) cargar(url) {
            if (iv.tag == url) obtener(url)?.let { ponerPortada(iv, it) }
        }
    }

    private fun ponerPortada(iv: ImageView, b: Bitmap) {
        iv.setPadding(0, 0, 0, 0)
        iv.imageTintList = null
        iv.setImageBitmap(b)
    }
}
