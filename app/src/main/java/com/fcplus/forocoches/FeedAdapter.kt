package com.fcplus.forocoches

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView

/**
 * La lista del feed de Música: una tarjeta por publicación (item_publicacion). Solo pinta y avisa;
 * quien la usa decide qué hacer con cada toque (corazón, play, ⋮, ver quién dio corazón).
 *
 * [alPlay] entrega el `pub_hueco` de la tarjeta, que es donde se monta el reproductor. Ese hueco
 * NO se toca en un repintado parcial (corazón nuevo, otra hora, refresco de la lista): el
 * reproductor montado debe seguir sonando. Solo se vacía cuando la fila se recicla
 * ([onViewRecycled]) o cuando un bind completo le pone otra publicación.
 *
 * [alSalirDePantalla] avisa con el id de la publicación cuando su fila sale de la ventana; no
 * toques vistas desde ahí (es para que A5 pare el reproductor).
 */
class FeedAdapter(
    private val alCorazon: (ItemFeed) -> Unit,
    private val alPlay: (ItemFeed, hueco: FrameLayout) -> Unit,
    private val alMenu: (ItemFeed, View) -> Unit,
    private val alAutorCorazones: (ItemFeed) -> Unit,
    private val alSalirDePantalla: (id: String) -> Unit = {}
) : RecyclerView.Adapter<FeedAdapter.Fila>() {

    private var items: List<ItemFeed> = emptyList()
    private var ahora = 0L

    init { setHasStableIds(true) }

    fun poner(nuevos: List<ItemFeed>, ahora: Long) {
        val viejos = items
        val ahoraViejo = this.ahora
        val d = DiffUtil.calculateDiff(object : DiffUtil.Callback() {
            override fun getOldListSize() = viejos.size
            override fun getNewListSize() = nuevos.size
            override fun areItemsTheSame(o: Int, n: Int) = viejos[o].id == nuevos[n].id
            override fun areContentsTheSame(o: Int, n: Int) = ahoraViejo == ahora && viejos[o] == nuevos[n]
            override fun getChangePayload(o: Int, n: Int): Any = PAYLOAD_DATOS
        })
        items = nuevos.toList()
        this.ahora = ahora
        d.dispatchUpdatesTo(this)
    }

    fun quitar(id: String) {
        val i = items.indexOfFirst { it.id == id }
        if (i < 0) return
        items = items.filterIndexed { k, _ -> k != i }
        notifyItemRemoved(i)
    }

    fun cambiarCorazon(id: String, e: EstadoCorazon) {
        val i = items.indexOfFirst { it.id == id }
        if (i < 0) return
        items = items.mapIndexed { k, p -> if (k == i) p.copy(corazones = e.corazones, corazon = e.corazon) else p }
        notifyItemChanged(i, PAYLOAD_CORAZON)
    }

    override fun getItemCount() = items.size

    override fun getItemId(position: Int): Long = items[position].id.hashCode().toLong()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Fila {
        val h = Fila(LayoutInflater.from(parent.context).inflate(R.layout.item_publicacion, parent, false))
        // Los avisos leen la publicación ACTUAL de la fila, no la de cuando se pintó.
        fun actual(): ItemFeed? =
            h.bindingAdapterPosition.takeIf { it != RecyclerView.NO_POSITION }?.let { items.getOrNull(it) }
        // La destacada ("Top de la semana pasada") es histórica: ni corazón, ni menú, ni "quién".
        h.corazonIco.setOnClickListener { actual()?.takeIf { !it.destacada }?.let(alCorazon) }
        // El número de MI publicación abre quién dio corazón (solo lo ve el autor); en las demás
        // es otra zona de toque del corazón.
        h.corazones.setOnClickListener { actual()?.takeIf { !it.destacada }?.let { if (it.mia) alAutorCorazones(it) else alCorazon(it) } }
        h.play.setOnClickListener { actual()?.let { alPlay(it, h.hueco) } }
        h.menu.setOnClickListener { v -> actual()?.takeIf { !it.destacada }?.let { alMenu(it, v) } }
        return h
    }

    override fun onBindViewHolder(h: Fila, position: Int) {
        val pub = items[position]
        val ctx = h.itemView.context
        if (h.idPintado != pub.id) {
            h.hueco.removeAllViews()
            h.hueco.visibility = View.GONE
        }
        h.idPintado = pub.id
        h.autor.text = pub.autor
        h.texto.text = pub.texto
        h.titulo.text = pub.cancion.titulo
        h.artista.text = pub.cancion.artista.orEmpty()
        h.artista.visibility = if (pub.cancion.artista.isNullOrBlank()) View.GONE else View.VISIBLE
        PortadasMusica.pintar(ctx, h.portada, pub.cancion.portada)
        pintarVivo(h, pub)
    }

    override fun onBindViewHolder(h: Fila, position: Int, payloads: MutableList<Any>) {
        if (payloads.isEmpty()) return onBindViewHolder(h, position)
        val pub = items[position]
        if (payloads.all { it == PAYLOAD_CORAZON }) pintarCorazon(h, pub) else {
            // Cambio de datos de una fila que ya estaba: solo textos. El hueco no se toca.
            h.texto.text = pub.texto
            pintarVivo(h, pub)
        }
    }

    /** Lo que cambia con el tiempo o con los corazones: hace, lo que queda, número y corazón. */
    private fun pintarVivo(h: Fila, pub: ItemFeed) {
        val ctx = h.itemView.context
        // La destacada es de la semana pasada: sin "hace", sin "se va en", sin menú, con su etiqueta.
        // Se pone en cada pintado (no solo en el completo): una fila reciclada no arrastra lo de otra.
        val d = pub.destacada
        h.etiqueta.visibility = if (d) View.VISIBLE else View.GONE
        h.hace.visibility = if (d) View.GONE else View.VISIBLE
        h.queda.visibility = if (d) View.GONE else View.VISIBLE
        h.menu.visibility = if (d) View.GONE else View.VISIBLE
        h.hace.text = VistaFeed.hace(pub.creada, ahora)
        val q = VistaFeed.queda(pub.caduca, ahora)
        h.queda.text = q.texto
        h.queda.setBackgroundResource(if (q.urgente) R.drawable.bg_chip_urgente else R.drawable.bg_chip_queda)
        h.queda.setTextColor(ctx.getColor(if (q.urgente) R.color.fc_texto else R.color.fc_texto_2))
        // Negrita en el urgente: sobre el naranja en oscuro el contraste es de 4,6:1.
        h.queda.setTypeface(null, if (q.urgente) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)
        pintarCorazon(h, pub)
    }

    private fun pintarCorazon(h: Fila, pub: ItemFeed) {
        val ico = if (pub.corazon) R.drawable.ic_corazon_on else R.drawable.ic_corazon
        h.corazonIco.setImageResource(ico)
        h.corazonIco.tag = ico  // para los tests: un vector no se puede comparar por dentro
        h.corazonIco.contentDescription = h.itemView.context.getString(
            if (pub.corazon) R.string.pub_quitar_corazon else R.string.pub_dar_corazon
        )
        h.corazones.text = pub.corazones.toString()
        if (pub.destacada) {
            // Solo el número: el corazón de una fila histórica no se toca ni se lee como botón.
            h.corazonIco.contentDescription = null
            h.corazonIco.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        } else h.corazonIco.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_AUTO
    }

    override fun onViewRecycled(h: Fila) {
        h.hueco.removeAllViews()
        h.hueco.visibility = View.GONE
        h.idPintado = null
    }

    override fun onViewDetachedFromWindow(h: Fila) {
        // No se toca ninguna vista: solo se avisa (A5 para el reproductor de esa publicación).
        h.idPintado?.let(alSalirDePantalla)
    }

    class Fila(v: View) : RecyclerView.ViewHolder(v) {
        var idPintado: String? = null
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
        val menu: ImageView = v.findViewById(R.id.pub_menu)
        val etiqueta: TextView = v.findViewById(R.id.pub_etiqueta)
    }

    private companion object {
        const val PAYLOAD_CORAZON = "corazon"
        const val PAYLOAD_DATOS = "datos"
    }
}
