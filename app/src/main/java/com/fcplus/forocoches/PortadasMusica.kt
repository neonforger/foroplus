package com.fcplus.forocoches

import android.widget.ImageView
import androidx.core.content.ContextCompat

/** La portada de una canción (la del servidor) o, mientras llega, el icono de música. */
object PortadasMusica {
    fun pintar(ctx: android.content.Context, iv: ImageView, url: String?) {
        val bmp = url?.let { PostImages.get(it) }
        if (bmp != null) {
            iv.setImageBitmap(bmp)
            return
        }
        val pad = (12 * ctx.resources.displayMetrics.density).toInt()
        iv.setImageResource(R.drawable.ic_musica)
        iv.setPadding(pad, pad, pad, pad)
        iv.imageTintList = ContextCompat.getColorStateList(ctx, R.color.fc_rojo)
        if (url != null) PostImages.load(url) {
            PostImages.get(url)?.let { b ->
                iv.setPadding(0, 0, 0, 0)
                iv.imageTintList = null
                iv.setImageBitmap(b)
            }
        }
    }
}
