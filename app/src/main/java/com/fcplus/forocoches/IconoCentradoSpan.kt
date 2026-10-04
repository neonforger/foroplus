package com.fcplus.forocoches

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.drawable.Drawable
import android.text.style.ImageSpan

/**
 * Un smiley (o cualquier icono pequeño) centrado en el renglón.
 *
 * `Html.fromHtml` monta sus imágenes con `ALIGN_BOTTOM`: el borde de abajo del dibujo se pega a
 * la **base** del texto, así que un smiley más alto que las letras cuelga por debajo del
 * renglón y se ve torcido — el "descuadrado" que reportaron los testers.
 *
 * `ImageSpan.ALIGN_CENTER` haría esto solo, pero es de API 29 y la app sale desde la 24.
 *
 * La aritmética vive en [ImagenEnTexto] y está cubierta por tests; aquí queda solo el dibujo.
 */
open class IconoCentradoSpan(dibujo: Drawable, origen: String) : ImageSpan(dibujo, origen) {

    override fun getSize(
        paint: Paint,
        text: CharSequence?,
        start: Int,
        end: Int,
        fm: Paint.FontMetricsInt?
    ): Int {
        val rect = drawable.bounds
        if (fm != null) {
            val metricas = paint.fontMetricsInt
            fm.ascent = ImagenEnTexto.topDelIcono(metricas.ascent, metricas.descent, rect.height())
            fm.descent = ImagenEnTexto.baseDelIcono(metricas.ascent, metricas.descent, rect.height())
            // top/bottom marcan el alto de la línea: sin ellos el icono se recorta al crecer.
            fm.top = fm.ascent
            fm.bottom = fm.descent
        }
        return rect.width()
    }

    override fun draw(
        canvas: Canvas,
        text: CharSequence?,
        start: Int,
        end: Int,
        x: Float,
        top: Int,
        y: Int,
        bottom: Int,
        paint: Paint
    ) {
        val metricas = paint.fontMetricsInt
        val desplazamiento =
            ImagenEnTexto.topDelIcono(metricas.ascent, metricas.descent, drawable.bounds.height())
        canvas.save()
        // `y` es la base del texto, que es justo la referencia de topDelIcono.
        canvas.translate(x, (y + desplazamiento).toFloat())
        drawable.draw(canvas)
        canvas.restore()
    }
}
