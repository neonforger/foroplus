package com.fcplus.forocoches

import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ProgressBar
import androidx.recyclerview.widget.RecyclerView

/**
 * La rueda al final de una lista que se va cargando por páginas: se pone detrás del adaptador de
 * verdad con un `ConcatAdapter`, así que no cambia las posiciones ni el `itemCount` de las filas.
 *
 * Hace falta porque la página siguiente puede tardar (el portero de search.php las espacia): sin
 * ella, quien llegaba al final antes de tiempo veía la lista parada y creía que se había acabado.
 * Solo se ve si llegas abajo; quien va leyendo por arriba no nota nada.
 */
class CargandoMasAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    var visible = false
        set(v) {
            if (field == v) return
            field = v
            if (v) notifyItemInserted(0) else notifyItemRemoved(0)
        }

    override fun getItemCount() = if (visible) 1 else 0

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val d = parent.resources.displayMetrics.density
        val caja = FrameLayout(parent.context).apply {
            layoutParams = RecyclerView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, (64 * d).toInt()
            )
        }
        val rueda = ProgressBar(parent.context).apply {
            isIndeterminate = true
            contentDescription = "Cargando más"
        }
        caja.addView(rueda, FrameLayout.LayoutParams((28 * d).toInt(), (28 * d).toInt(), Gravity.CENTER))
        return object : RecyclerView.ViewHolder(caja) {}
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {}
}
