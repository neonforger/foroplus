package com.fcplus.forocoches

import android.widget.TextView
import androidx.core.content.ContextCompat

/**
 * Control segmentado de Opciones → Apariencia (tema y tamaños, fase 2 del rediseño): una fila
 * de opciones dentro de una cápsula gris, y la elegida en una pastilla ROJA con la tinta de
 * "sobre rojo". Roja y no blanca como en el mockup: blanco sobre el gris del modo oscuro casi
 * no se distingue, y la pastilla roja es la misma señal de "estás aquí" que las pestañas y la
 * página actual.
 */
object Segmentado {

    data class Aspecto(val fondo: Int?, val color: Int, val negrita: Boolean)

    fun aspecto(elegido: Boolean): Aspecto =
        if (elegido) Aspecto(R.drawable.bg_segmento_elegido, R.color.fc_sobre_rojo, true)
        else Aspecto(null, R.color.fc_texto_2, false)

    /** Pinta las opciones con [elegida] marcada (índice; fuera de rango = ninguna). */
    fun pintar(opciones: List<TextView>, elegida: Int) {
        opciones.forEachIndexed { i, tv ->
            val a = aspecto(i == elegida)
            if (a.fondo != null) tv.setBackgroundResource(a.fondo) else tv.background = null
            tv.setTextColor(ContextCompat.getColor(tv.context, a.color))
            tv.setTypeface(null, if (a.negrita) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)
            tv.isSelected = i == elegida
        }
    }
}
