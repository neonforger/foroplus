package com.fcplus.forocoches

import android.content.Context
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

/**
 * Cálculo del pulgar de la barra de desplazamiento con **tamaño fijo**.
 *
 * Android saca el largo del pulgar de la proporción "lo que se ve / lo que hay", así que con
 * posts de alturas muy dispares —dos líneas o una foto a pantalla completa— el pulgar se encoge
 * y se estira sin parar, y encima **da saltos cuando las imágenes terminan de cargar** y cambian
 * el alto del post. La referencia deja de servir justo cuando más falta hace.
 *
 * La solución es declarar un recorrido y un pulgar CONSTANTES y limitarnos a mover el pulgar por
 * la fracción realmente recorrida.
 */
object ScrollThumb {

    /** Recorrido inventado. El valor da igual mientras sea grande: solo cuenta la proporción. */
    const val RANGE = 10_000

    /** Largo del pulgar: el 8 % del recorrido, ni más ni menos, siempre. */
    const val EXTENT = 800

    /**
     * Posición del pulgar para la fracción recorrida real.
     *
     * @param realOffset cuánto se lleva desplazado (lo que calcula el LayoutManager)
     * @param realRange  el total desplazable
     * @param realExtent lo que cabe en pantalla
     */
    fun offset(realOffset: Int, realRange: Int, realExtent: Int): Int {
        val recorrible = realRange - realExtent
        if (recorrible <= 0) return 0                    // todo cabe: pulgar al principio
        val fraccion = realOffset.toFloat() / recorrible
        return (fraccion.coerceIn(0f, 1f) * (RANGE - EXTENT)).toInt()
    }
}

/** [LinearLayoutManager] cuyo pulgar de scroll no cambia de tamaño. Ver [ScrollThumb]. */
class FixedThumbLayoutManager(context: Context) : LinearLayoutManager(context) {

    override fun computeVerticalScrollRange(state: RecyclerView.State): Int = ScrollThumb.RANGE

    override fun computeVerticalScrollExtent(state: RecyclerView.State): Int = ScrollThumb.EXTENT

    override fun computeVerticalScrollOffset(state: RecyclerView.State): Int = ScrollThumb.offset(
        super.computeVerticalScrollOffset(state),
        super.computeVerticalScrollRange(state),
        super.computeVerticalScrollExtent(state)
    )
}
