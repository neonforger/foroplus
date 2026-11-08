package com.fcplus.forocoches

import android.annotation.SuppressLint
import android.graphics.drawable.Drawable
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView

/**
 * La lista de la pantalla de una playlist de Música: la cabecera en la posición 0 (nombre,
 * resumen, Reproducir, el aviso de que se para al salir) y una tarjeta por canción, la misma
 * para YouTube y Spotify. Los índices que salen por los avisos son de CANCIÓN (sin la cabecera).
 *
 * El arrastre, como en [OrganizarAdapter]: solo desde el asa, durante el gesto se mueve la lista
 * en memoria y se guarda UNA vez al soltar ([alMover] con la posición de salida y la de llegada:
 * un arrastre mueve una sola canción, que es justo lo que hace `AlmacenMusica.mover`).
 */
class PlaylistAdapter(
    private val alTocar: (indice: Int) -> Unit,
    private val alMenu: (indice: Int, ancla: View) -> Unit,
    private val alMover: (desde: Int, hasta: Int) -> Unit,
    private val alVolver: () -> Unit,
    private val alReproducir: () -> Unit,
    private val alMenuPlaylist: (ancla: View) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private var nombre = ""
    private val canciones = ArrayList<Cancion>()
    private var sonando: String? = null
    private var fallidas: Set<String> = emptySet()

    /**
     * ¿Hay una canción agarrada? Se pone en cuanto se coge (onSelectedChanged), no en el
     * primer movimiento: entre coger y mover también puede llegar un repintado (una canción que
     * acaba, una portada) y rebindear la fila agarrada la descuadra.
     */
    private var arrastrando = false

    /** Posición (de canción) donde empezó el arrastre en curso; -1 si no se arrastra. */
    private var desdeArrastre = -1

    /** Lo que llegó durante el arrastre, para aplicarlo al soltar. */
    private var pinturaPendiente: Pair<String, List<Cancion>>? = null
    private var marcasPendientes: Pair<String?, Set<String>>? = null
    private var portadasPendientes = false

    lateinit var touchHelper: ItemTouchHelper

    /** Lo que se ve ahora mismo (con un arrastre a medias, el orden que lleva el dedo). */
    fun canciones(): List<Cancion> = canciones.toList()

    /** El nombre de la playlist que se está pintando. */
    fun nombre(): String = pinturaPendiente?.first ?: nombre

    @SuppressLint("NotifyDataSetChanged")
    fun pintar(nombre: String, nuevas: List<Cancion>) {
        // A media faena no: el arrastre lleva su propia copia y sustituirla lo descuadraría.
        if (arrastrando) { pinturaPendiente = nombre to nuevas; return }
        this.nombre = nombre
        canciones.clear()
        canciones.addAll(nuevas)
        notifyDataSetChanged()
    }

    /** La que suena (su clave) y las que YouTube no deja oír aquí. */
    fun marcar(sonandoAhora: String?, noSeOyen: Set<String>) {
        if (arrastrando) { marcasPendientes = sonandoAhora to noSeOyen.toSet(); return }
        if (sonandoAhora == sonando && noSeOyen == fallidas) return
        val antes = sonando
        sonando = sonandoAhora
        val cambiaronFallidas = noSeOyen != fallidas
        fallidas = noSeOyen.toSet()
        if (cambiaronFallidas) { notifyItemRangeChanged(1, canciones.size); return }
        posicionDe(antes)?.let { notifyItemChanged(it) }
        posicionDe(sonandoAhora)?.let { notifyItemChanged(it) }
    }

    private fun posicionDe(clave: String?): Int? {
        clave ?: return null
        val i = canciones.indexOfFirst { it.ref.clave == clave }
        return if (i < 0) null else i + 1
    }

    override fun getItemCount() = canciones.size + 1

    override fun getItemViewType(position: Int) = if (position == 0) CABECERA else CANCION

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inf = LayoutInflater.from(parent.context)
        return if (viewType == CABECERA) Cabecera(inf.inflate(R.layout.cabecera_playlist, parent, false))
        else Fila(inf.inflate(R.layout.item_cancion, parent, false))
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is Cabecera -> pintarCabecera(holder)
            is Fila -> pintarFila(holder, position - 1)
        }
    }

    private fun pintarCabecera(h: Cabecera) {
        h.nombre.text = nombre
        h.resumen.text = MusicaController.resumen(canciones)
        val hay = canciones.isNotEmpty()
        h.reproducir.isEnabled = hay
        h.reproducir.alpha = if (hay) 1f else 0.45f
        h.vacia.visibility = if (hay) View.GONE else View.VISIBLE
        h.volver.setOnClickListener { alVolver() }
        h.reproducir.setOnClickListener { alReproducir() }
        h.menu.setOnClickListener { alMenuPlaylist(it) }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun pintarFila(h: Fila, i: Int) {
        val c = canciones[i]
        val ctx = h.itemView.context
        val suena = c.ref.clave == sonando
        val fuera = c.ref.clave in fallidas
        h.titulo.text = c.titulo
        h.titulo.setTextColor(ContextCompat.getColor(ctx, if (suena) R.color.fc_rojo else R.color.fc_texto))
        val detalle = listOfNotNull(
            c.artista?.takeIf { it.isNotBlank() },
            c.duracionSeg?.let { MusicaController.duracion(it) }
        ).joinToString(" · ")
        h.detalle.text = detalle
        h.detalle.visibility = if (detalle.isEmpty()) View.GONE else View.VISIBLE
        h.fuera.visibility = if (fuera) View.VISIBLE else View.GONE
        h.sonando.visibility = if (suena) View.VISIBLE else View.GONE
        pintarPortada(h, c)
        // TalkBack lee la tarjeta de una vez; lo que suena y lo que no se oye aquí, también.
        h.itemView.contentDescription = listOfNotNull(
            if (suena) "Suena ahora" else null, c.titulo, detalle.ifEmpty { null },
            if (fuera) "no se puede escuchar aquí, toca para abrirla en YouTube" else null
        ).joinToString(", ")
        h.menu.contentDescription = "Opciones de «${c.titulo}»"
        h.asa.contentDescription = "Mover «${c.titulo}»"

        h.itemView.setOnClickListener { posicion(h)?.let(alTocar) }
        h.menu.setOnClickListener { v -> posicion(h)?.let { alMenu(it, v) } }
        h.asa.setOnTouchListener { _, ev ->
            if (ev.actionMasked == MotionEvent.ACTION_DOWN) touchHelper.startDrag(h)
            false
        }
        // Quien no puede arrastrar (TalkBack) sube y baja desde las acciones de la tarjeta.
        ViewCompat.removeAccessibilityAction(h.itemView, h.accionSubir)
        ViewCompat.removeAccessibilityAction(h.itemView, h.accionBajar)
        h.accionSubir = if (i > 0) ViewCompat.addAccessibilityAction(h.itemView, "Subir") { _, _ ->
            posicion(h)?.let { if (it > 0) moverYGuardar(it, it - 1) }; true
        } else View.NO_ID
        h.accionBajar = if (i < canciones.size - 1) ViewCompat.addAccessibilityAction(h.itemView, "Bajar") { _, _ ->
            posicion(h)?.let { if (it < canciones.size - 1) moverYGuardar(it, it + 1) }; true
        } else View.NO_ID
    }

    private fun moverYGuardar(desde: Int, hasta: Int) {
        canciones.add(hasta, canciones.removeAt(desde))
        notifyItemMoved(desde + 1, hasta + 1)
        alMover(desde, hasta)
    }

    /** La portada con el cargador de imágenes de la app (spec); mientras llega, la nota. */
    private fun pintarPortada(h: Fila, c: Cancion) {
        val url = c.portada
        val bmp = url?.let { PostImages.get(it) }
        if (bmp != null) {
            h.portada.setImageBitmap(bmp)
            h.portada.setPadding(0, 0, 0, 0)
            h.portada.imageTintList = null
            return
        }
        val pad = (12 * h.itemView.resources.displayMetrics.density).toInt()
        h.portada.setImageResource(R.drawable.ic_musica)
        h.portada.setPadding(pad, pad, pad, pad)
        h.portada.imageTintList = ContextCompat.getColorStateList(h.itemView.context, R.color.fc_rojo)
        if (url != null) PostImages.load(url) {
            // Llega en el hilo de la interfaz. Se repinta la fila de ESA canción, esté donde esté
            // (con una agarrada, al soltar).
            if (arrastrando) portadasPendientes = true
            else posicionDe(c.ref.clave)?.let { notifyItemChanged(it) }
        }
    }

    private fun posicion(h: RecyclerView.ViewHolder): Int? {
        val p = h.bindingAdapterPosition
        return if (p == RecyclerView.NO_POSITION || p == 0) null else p - 1
    }

    fun callbackDeArrastre(): ItemTouchHelper.Callback = object : ItemTouchHelper.SimpleCallback(
        ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0
    ) {
        override fun getMovementFlags(rv: RecyclerView, vh: RecyclerView.ViewHolder): Int =
            if (vh is Fila) super.getMovementFlags(rv, vh) else 0

        override fun canDropOver(rv: RecyclerView, actual: RecyclerView.ViewHolder, destino: RecyclerView.ViewHolder) =
            destino is Fila

        override fun onMove(rv: RecyclerView, vh: RecyclerView.ViewHolder, destino: RecyclerView.ViewHolder): Boolean {
            val de = posicion(vh) ?: return false
            val a = posicion(destino) ?: return false
            canciones.add(a, canciones.removeAt(de))
            notifyItemMoved(de + 1, a + 1)
            return true
        }

        override fun onSwiped(vh: RecyclerView.ViewHolder, direction: Int) = Unit

        override fun isLongPressDragEnabled() = false

        override fun onSelectedChanged(vh: RecyclerView.ViewHolder?, estado: Int) {
            super.onSelectedChanged(vh, estado)
            if (estado != ItemTouchHelper.ACTION_STATE_DRAG || vh !is Fila) return
            arrastrando = true
            desdeArrastre = posicion(vh) ?: -1
            // En la Compacta la fila es transparente (solo la onda): arrastrada por encima de las
            // otras se leerían los dos textos a la vez. En Tarjetas la tarjeta ya es opaca.
            val v = vh.itemView
            if (!AtributosTema.bool(v.context, R.attr.fcRecortarFila)) {
                vh.fondoAntes = v.background
                v.setBackgroundColor(ContextCompat.getColor(v.context, R.color.fc_fondo))
            }
            v.translationZ = 6 * v.resources.displayMetrics.density
        }

        override fun clearView(rv: RecyclerView, vh: RecyclerView.ViewHolder) {
            super.clearView(rv, vh)
            if (vh is Fila) {
                vh.itemView.translationZ = 0f
                vh.fondoAntes?.let { vh.itemView.background = it }
                vh.fondoAntes = null
            }
            if (!arrastrando || desdeArrastre == SOLTANDO) return
            val desde = desdeArrastre
            desdeArrastre = SOLTANDO
            val hasta = posicion(vh)
            // En el frame siguiente: clearView puede correr dentro de la animación de soltar, y
            // ahí la lista no admite avisos de cambios. Hasta entonces lo que llegue se sigue
            // guardando para después.
            rv.post {
                arrastrando = false
                desdeArrastre = -1
                val pintura = pinturaPendiente
                val marcas = marcasPendientes
                val portadas = portadasPendientes
                pinturaPendiente = null
                marcasPendientes = null
                portadasPendientes = false
                if (desde >= 0 && hasta != null && desde != hasta) {
                    // Lo que llegó a medias ya es viejo (el orden de antes): quien escucha guarda
                    // el nuevo y vuelve a pintar con lo guardado.
                    alMover(desde, hasta)
                } else if (pintura != null) {
                    pintar(pintura.first, pintura.second)
                }
                marcas?.let { marcar(it.first, it.second) }
                if (portadas) notifyItemRangeChanged(1, canciones.size)
            }
        }
    }

    class Cabecera(v: View) : RecyclerView.ViewHolder(v) {
        val volver: View = v.findViewById(R.id.pl_volver)
        val nombre: TextView = v.findViewById(R.id.pl_nombre)
        val menu: View = v.findViewById(R.id.pl_menu)
        val resumen: TextView = v.findViewById(R.id.pl_resumen)
        val reproducir: TextView = v.findViewById(R.id.pl_reproducir)
        val vacia: View = v.findViewById(R.id.pl_vacia)
    }

    class Fila(v: View) : RecyclerView.ViewHolder(v) {
        val portada: ImageView = v.findViewById(R.id.cancion_portada)
        val sonando: View = v.findViewById(R.id.cancion_sonando)
        val titulo: TextView = v.findViewById(R.id.cancion_titulo)
        val detalle: TextView = v.findViewById(R.id.cancion_detalle)
        val fuera: View = v.findViewById(R.id.cancion_fuera)
        val menu: View = v.findViewById(R.id.cancion_menu)
        val asa: View = v.findViewById(R.id.cancion_asa)
        var fondoAntes: Drawable? = null
        var accionSubir = View.NO_ID
        var accionBajar = View.NO_ID

        init {
            // Las esquinas del fondo de la portada (bg_icono_musica) recortan también la foto.
            portada.clipToOutline = true
        }
    }

    private companion object {
        const val CABECERA = 0
        const val CANCION = 1

        /** desdeArrastre entre soltar y el frame en que se aplica lo pendiente. */
        const val SOLTANDO = -2
    }
}
