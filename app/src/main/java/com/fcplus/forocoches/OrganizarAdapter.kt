package com.fcplus.forocoches

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView

/**
 * Una fila de la pantalla de organizar: qué es, si se ve, y si no se puede quitar.
 *
 * En la barra de abajo `visible` es "va abajo" (lo demás va al panel del avatar, ver
 * [BarraAbajo]) y [seccion], si viene, es el encabezado que se pinta encima de la fila: la
 * primera de la barra y la primera del panel.
 */
data class FilaOrganizar(
    val clave: String,
    val nombre: String,
    val visible: Boolean,
    val fijo: Boolean,
    val seccion: String? = null
)

/**
 * La lista de la pantalla de organizar barras.
 *
 * El arrastre se agarra **solo desde el asa**, no desde toda la fila: si la fila entera fuera
 * agarrable no se podría tocar la casilla sin que la lista creyera que la estás moviendo, y
 * una pulsación larga como disparador tampoco vale (no la encuentra nadie).
 *
 * Mientras arrastras solo se mueve la lista en memoria; **lo guardado se escribe al soltar**
 * ([alSoltar]). Escribir en cada `onMove` repintaría la barra decenas de veces por gesto.
 */
class OrganizarAdapter(
    private val alTocarCasilla: (String) -> Unit,
    private val alSoltar: (List<String>) -> Unit
) : RecyclerView.Adapter<OrganizarAdapter.VH>() {

    private val filas = ArrayList<FilaOrganizar>()
    private var arrastrando = false

    lateinit var touchHelper: ItemTouchHelper

    @SuppressLint("NotifyDataSetChanged")
    fun submit(nuevas: List<FilaOrganizar>) {
        // No se repinta a media faena: el arrastre lleva su propia copia de la lista y
        // sustituirla debajo lo dejaría descuadrado.
        if (arrastrando) return
        filas.clear()
        filas.addAll(nuevas)
        notifyDataSetChanged()
    }

    override fun getItemCount() = filas.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(LayoutInflater.from(parent.context).inflate(R.layout.item_organizar, parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) {
        val f = filas[position]
        holder.label.text = f.nombre
        holder.check.text = if (f.visible) "☑" else "☐"
        holder.check.setTextColor(color(holder, if (f.visible) R.color.fc_rojo else R.color.fc_texto_3))
        // Lo que no se ve en la barra se apaga también aquí: si la fila se leyera igual que
        // las demás, la casilla sería la única pista y hay que mirarla de cerca.
        holder.label.setTextColor(color(holder, if (f.visible) R.color.fc_texto else R.color.fc_texto_3))
        holder.fijo.visibility = if (f.fijo) View.VISIBLE else View.GONE
        holder.seccion.text = f.seccion ?: ""
        holder.seccion.visibility = if (f.seccion != null) View.VISIBLE else View.GONE

        holder.check.setOnClickListener { alTocarCasilla(f.clave) }
        // La fila, no la vista entera: el encabezado de sección no es parte de lo que se toca.
        (holder.label.parent as View).setOnClickListener { alTocarCasilla(f.clave) }
        holder.drag.setOnTouchListener { _, ev ->
            if (ev.actionMasked == MotionEvent.ACTION_DOWN) touchHelper.startDrag(holder)
            false
        }
    }

    private fun color(h: VH, id: Int) = ContextCompat.getColor(h.itemView.context, id)

    /** El orden que se ve ahora mismo. */
    fun ordenActual(): List<String> = filas.map { it.clave }

    fun callbackDeArrastre(): ItemTouchHelper.Callback = object : ItemTouchHelper.SimpleCallback(
        ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0
    ) {
        override fun onMove(
            rv: RecyclerView, vh: RecyclerView.ViewHolder, destino: RecyclerView.ViewHolder
        ): Boolean {
            val de = vh.bindingAdapterPosition
            val a = destino.bindingAdapterPosition
            if (de == RecyclerView.NO_POSITION || a == RecyclerView.NO_POSITION) return false
            arrastrando = true
            filas.add(a, filas.removeAt(de))
            notifyItemMoved(de, a)
            return true
        }

        override fun onSwiped(vh: RecyclerView.ViewHolder, direction: Int) = Unit

        override fun isLongPressDragEnabled() = false

        override fun clearView(rv: RecyclerView, vh: RecyclerView.ViewHolder) {
            super.clearView(rv, vh)
            if (!arrastrando) return
            arrastrando = false
            alSoltar(ordenActual())
        }
    }

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val check: TextView = v.findViewById(R.id.org_check)
        val label: TextView = v.findViewById(R.id.org_label)
        val fijo: TextView = v.findViewById(R.id.org_fijo)
        val drag: TextView = v.findViewById(R.id.org_drag)
        val seccion: TextView = v.findViewById(R.id.org_seccion)
    }
}
