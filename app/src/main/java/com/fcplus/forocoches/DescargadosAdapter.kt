package com.fcplus.forocoches

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** La lista de copias guardadas. Cada fila dice cuánto ocupa y deja borrarla. */
class DescargadosAdapter(
    private val onAbrir: (HiloDescargado) -> Unit,
    private val onBorrar: (HiloDescargado) -> Unit
) : RecyclerView.Adapter<DescargadosAdapter.Holder>() {

    private val items = ArrayList<HiloDescargado>()

    fun submit(lista: List<HiloDescargado>) {
        items.clear()
        items.addAll(lista)
        notifyDataSetChanged()
    }

    class Holder(v: View) : RecyclerView.ViewHolder(v) {
        val fila: View = v.findViewById(R.id.desc_fila)
        val titulo: TextView = v.findViewById(R.id.desc_titulo)
        val resumen: TextView = v.findViewById(R.id.desc_resumen)
        val meta: TextView = v.findViewById(R.id.desc_meta)
        val borrar: ImageButton = v.findViewById(R.id.desc_borrar)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder =
        Holder(LayoutInflater.from(parent.context).inflate(R.layout.item_descargado, parent, false))

    override fun getItemCount() = items.size

    override fun onBindViewHolder(h: Holder, pos: Int) {
        val d = items[pos]
        h.titulo.text = d.titulo.ifEmpty { "(hilo ${d.tid})" }
        h.resumen.text = Descargas.resumen(d.paginas, d.mensajes)
        // El tamaño sale también en cada fila, no solo el total: si alguien tiene que hacer
        // sitio, necesita saber CUÁL borrar, no solo que ocupa mucho.
        h.meta.text = "${fecha(d.guardadoEn)} · ${Descargas.tamanoLegible(d.bytes)}"
        h.fila.setOnClickListener { onAbrir(d) }
        h.itemView.setOnClickListener { onAbrir(d) }
        h.borrar.setOnClickListener { onBorrar(d) }
    }

    private fun fecha(ms: Long): String {
        if (ms <= 0) return "Guardado"
        val hoy = SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(Date())
        val suyo = SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(Date(ms))
        val f = if (hoy == suyo) SimpleDateFormat("'hoy' HH:mm", Locale.getDefault())
        else SimpleDateFormat("d 'de' MMMM", Locale.getDefault())
        return "Guardado " + f.format(Date(ms))
    }
}
