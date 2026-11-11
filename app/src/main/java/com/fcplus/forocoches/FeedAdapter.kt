package com.fcplus.forocoches

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

/**
 * La lista del feed de Música: una tarjeta por publicación (item_publicacion). Solo pinta y avisa;
 * quien la usa decide qué hacer con cada toque (corazón, play, guardar, ⋮, ver quién dio corazón).
 *
 * [alPlay] entrega el `pub_hueco` de la tarjeta, que es donde se monta el reproductor. Nada se
 * toca desde onScrolled; quien monte reproductores se apoya en el reciclado de las filas.
 */
class FeedAdapter(
    private val alCorazon: (ItemFeed) -> Unit,
    private val alPlay: (ItemFeed, hueco: FrameLayout) -> Unit,
    private val alGuardar: (ItemFeed) -> Unit,
    private val alMenu: (ItemFeed, View) -> Unit,
    private val alAutorCorazones: (ItemFeed) -> Unit
) : RecyclerView.Adapter<FeedAdapter.Fila>() {

    private val items = ArrayList<ItemFeed>()
    private var ahora = 0L

    fun poner(items: List<ItemFeed>, ahora: Long) {
        this.items.clear()
        this.items.addAll(items)
        this.ahora = ahora
        notifyDataSetChanged()
    }

    fun quitar(id: String) {
        val i = items.indexOfFirst { it.id == id }
        if (i < 0) return
        items.removeAt(i)
        notifyItemRemoved(i)
    }

    fun cambiarCorazon(id: String, e: EstadoCorazon) {
        val i = items.indexOfFirst { it.id == id }
        if (i < 0) return
        items[i] = items[i].copy(corazones = e.corazones, corazon = e.corazon)
        notifyItemChanged(i)
    }

    override fun getItemCount() = items.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Fila =
        Fila(LayoutInflater.from(parent.context).inflate(R.layout.item_publicacion, parent, false))

    override fun onBindViewHolder(h: Fila, position: Int) {
        val pub = items[position]
        val ctx = h.itemView.context
        h.autor.text = pub.autor
        h.hace.text = VistaFeed.hace(pub.creada, ahora)
        val q = VistaFeed.queda(pub.caduca, ahora)
        h.queda.text = q.texto
        h.queda.setBackgroundResource(if (q.urgente) R.drawable.bg_chip_urgente else R.drawable.bg_chip_queda)
        h.queda.setTextColor(ctx.getColor(if (q.urgente) R.color.fc_texto else R.color.fc_texto_2))
        h.texto.text = pub.texto
        h.titulo.text = pub.cancion.titulo
        h.artista.text = pub.cancion.artista.orEmpty()
        h.artista.visibility = if (pub.cancion.artista.isNullOrBlank()) View.GONE else View.VISIBLE
        PortadasMusica.pintar(ctx, h.portada, pub.cancion.portada)
        h.hueco.removeAllViews()
        h.hueco.visibility = View.GONE
        val ico = if (pub.corazon) R.drawable.ic_corazon_on else R.drawable.ic_corazon
        h.corazonIco.setImageResource(ico)
        h.corazonIco.tag = ico  // para los tests: un vector no se puede comparar por dentro
        h.corazonIco.contentDescription = if (pub.corazon) "Quitar corazón" else "Dar corazón"
        h.corazones.text = pub.corazones.toString()
        h.guardar.visibility =
            if (pub.cancion.spotify == null && pub.cancion.youtube == null) View.GONE else View.VISIBLE

        h.corazonIco.setOnClickListener { alCorazon(pub) }
        // El número de MI publicación abre quién dio corazón (solo lo ve el autor); en las demás
        // es otra zona de toque del corazón.
        h.corazones.setOnClickListener { if (pub.mia) alAutorCorazones(pub) else alCorazon(pub) }
        h.play.setOnClickListener { alPlay(pub, h.hueco) }
        h.guardar.setOnClickListener { alGuardar(pub) }
        h.menu.setOnClickListener { v -> alMenu(pub, v) }
    }

    class Fila(v: View) : RecyclerView.ViewHolder(v) {
        val autor: TextView = v.findViewById(R.id.pub_autor)
        val hace: TextView = v.findViewById(R.id.pub_hace)
        val queda: TextView = v.findViewById(R.id.pub_queda)
        val texto: TextView = v.findViewById(R.id.pub_texto)
        val portada: ImageView = v.findViewById(R.id.pub_portada)
        val titulo: TextView = v.findViewById(R.id.pub_titulo)
        val artista: TextView = v.findViewById(R.id.pub_artista)
        val play: ImageView = v.findViewById(R.id.pub_play)
        val hueco: FrameLayout = v.findViewById(R.id.pub_hueco)
        val corazonIco: ImageView = v.findViewById(R.id.pub_corazon_ico)
        val corazones: TextView = v.findViewById(R.id.pub_corazones)
        val guardar: TextView = v.findViewById(R.id.pub_guardar)
        val menu: ImageView = v.findViewById(R.id.pub_menu)
    }
}
