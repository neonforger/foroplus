package com.fcplus.forocoches

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import org.json.JSONObject

data class ThreadItem(
    val tid: String,
    val title: String,
    val author: String,
    val replies: String,
    val time: String,
    val url: String,
    /** Lo que dice FC: hilo con mensajes sin leer. Sin sesión llega siempre false. */
    val unread: Boolean = false,
    /**
     * Enlace al ÚLTIMO mensaje del hilo (`showthread.php?p=NNN`). Viene gratis en la fila, es
     * de donde sale la hora. "" si FC no lo trae: entonces la hora no se puede tocar.
     */
    val lastPostUrl: String = "",
    /** Lo que FC dice del hilo en su fila: si has escrito en él, si está cerrado. */
    val estado: EstadoHilo = EstadoHilo()
)

data class MenuLinks(
    val pm: String?,
    val mentions: String?,
    val quotes: String?,
    val favs: String?,
    val profile: String?
)

data class ForumTab(val fid: Int, val name: String)

/** Parsea el payload de fcLoadForumList. Lista vacía si el JSON no es válido. */
fun parseForumListPayload(json: String): List<ForumTab> {
    return try {
        val arr = JSONObject(json).getJSONArray("forums")
        val list = ArrayList<ForumTab>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val fid = o.optString("fid").toIntOrNull() ?: continue
            val name = o.optString("name").trim()
            if (name.isEmpty()) continue
            list.add(ForumTab(fid, name))
        }
        list
    } catch (_: Exception) {
        emptyList()
    }
}

data class ShellPayload(
    val url: String,
    val finalUrl: String,
    val menu: MenuLinks,
    val threads: List<ThreadItem>,
    val pmCount: Int = 0,
    val quotesCount: Int = 0,
    val mentionsCount: Int = 0
) {
    /** Página del listado que representa este payload (forumdisplay ...&page=N). */
    val page: Int
        get() = Regex("[?&]page=(\\d+)").find(url)?.groupValues?.get(1)?.toIntOrNull() ?: 1
}

/** Parsea el payload de extractor.js. Devuelve null si el JSON no es válido. */
/**
 * Las filas de hilo de un array del motor. Está aparte porque lo usan el listado de siempre y
 * el Popurrí, que trae varias listas de golpe (ver [Popurri]).
 */
fun parseThreadItems(arr: org.json.JSONArray?): List<ThreadItem> {
    if (arr == null) return emptyList()
    val list = ArrayList<ThreadItem>(arr.length())
    for (i in 0 until arr.length()) {
        val o = arr.optJSONObject(i) ?: continue
        val title = o.optString("title").trim()
        val tid = o.optString("tid").trim()
        if (title.isEmpty() || tid.isEmpty()) continue
        list.add(
            ThreadItem(
                tid = tid,
                title = title,
                author = o.optString("author").trim(),
                replies = o.optString("replies").trim(),
                time = o.optString("time").trim(),
                url = o.optString("url").trim(),
                unread = o.optBoolean("unread", false),
                lastPostUrl = o.optString("lastUrl").trim(),
                estado = EstadoHilo.de(o.optString("estado"))
            )
        )
    }
    return list
}

fun parseThreadListPayload(json: String): ShellPayload? {
    return try {
        val root = JSONObject(json)
        val menuObj = root.optJSONObject("menu")
        val menu = MenuLinks(
            pm = menuObj?.optString("pm")?.ifBlank { null },
            mentions = menuObj?.optString("mentions")?.ifBlank { null },
            quotes = menuObj?.optString("quotes")?.ifBlank { null },
            favs = menuObj?.optString("favs")?.ifBlank { null },
            profile = menuObj?.optString("profile")?.ifBlank { null }
        )
        val list = parseThreadItems(root.optJSONArray("threads"))
        val counts = root.optJSONObject("counts")
        ShellPayload(
            root.optString("url"), root.optString("finalUrl"), menu, list,
            pmCount = counts?.optInt("pm") ?: 0,
            quotesCount = counts?.optInt("quotes") ?: 0,
            mentionsCount = counts?.optInt("mentions") ?: 0
        )
    } catch (_: Exception) {
        null
    }
}

/**
 * Parte izquierda de la línea: quien ABRIÓ el hilo (medido 2026-09-25, gotcha 18: el `@` de la
 * fila de FC no es el último que postea, como se creía). La hora ya NO va aquí — vive en su
 * propia vista porque es un enlace al último mensaje (ver [threadTimeLabel]).
 */
fun threadMetaLine(author: String): String = if (author.isNotEmpty()) "@$author" else ""

/**
 * Hora del último mensaje. Lleva un `›` cuando se puede tocar, que es la única pista de que
 * lleva a algún sitio: en la web de FC es texto gris idéntico y nadie lo descubre.
 */
fun threadTimeLabel(time: String, tienEnlace: Boolean): String = when {
    time.isEmpty() -> ""
    tienEnlace -> "$time  ›"
    else -> time
}

private const val PAYLOAD_ULTIMO = "ultimo"

class ThreadListAdapter(
    private val onClick: (ThreadItem) -> Unit,
    /** Tocar la hora: abrir el hilo directamente en su último mensaje. */
    private val onLastPost: (ThreadItem) -> Unit = {},
    // null en tests/pantallas sin memoria local: entonces manda solo lo que diga FC.
    private val readThreads: ReadThreadsRepository? = null,
    /** Pulsación larga en una fila: acciones del hilo (ignorarlo, ignorar a su autor). */
    private val onLongClick: (ThreadItem) -> Unit = {}
) : RecyclerView.Adapter<ThreadListAdapter.Holder>() {

    private val items = ArrayList<ThreadItem>()

    /**
     * "Último que escribe" bajo la hora (Opciones → Apariencia). Quién es lo sabe
     * [UltimosPosteadores]; el adaptador solo pregunta al pintar la fila ([pedirUltimo]) y se
     * repinta cuando llega ([ultimoLlego]).
     */
    var mostrarUltimo: Boolean = true
        set(v) {
            if (field == v) return
            field = v
            notifyDataSetChanged()
        }
    /** El autor del mensaje `pid` si ya se sabe; null si no; "" si se sabe pero no se enseña. */
    var autorUltimo: (String) -> String? = { null }
    /** Esta fila se está viendo y le falta el autor del mensaje `pid`. */
    var pedirUltimo: (String) -> Unit = {}

    /** Ha llegado el autor del mensaje `pid`: se repinta SOLO esa línea en las filas que lo usan. */
    fun ultimoLlego(pid: String) {
        for (i in items.indices) {
            if (UltimosPosteadores.pidDe(items[i].lastPostUrl) == pid) notifyItemChanged(i, PAYLOAD_ULTIMO)
        }
    }

    /** Tamaño de los títulos, en sp (Opciones → Tamaño de los títulos). */
    var tituloSp: Float = 15f
        set(v) {
            if (field == v) return
            field = v
            notifyDataSetChanged()
        }
    // vBulletin ordena por último post y el foro se mueve entre páginas: un hilo de la
    // página 1 puede reaparecer en la 2. Dedupe por tid al hacer append.
    private val seenTids = HashSet<String>()

    fun submit(list: List<ThreadItem>) {
        items.clear()
        seenTids.clear()
        for (t in list) if (seenTids.add(t.tid)) items.add(t)
        notifyDataSetChanged()
    }

    /** Añade una página más (scroll infinito), saltando hilos ya mostrados. */
    fun append(list: List<ThreadItem>) {
        var added = 0
        for (t in list) {
            if (seenTids.add(t.tid)) { items.add(t); added++ }
        }
        if (added > 0) notifyItemRangeInserted(items.size - added, added)
    }

    class Holder(v: View) : RecyclerView.ViewHolder(v) {
        val sobre: ImageView = v.findViewById(R.id.thread_sobre)
        val participado: View = v.findViewById(R.id.thread_participado)
        val candado: View = v.findViewById(R.id.thread_candado)
        val title: TextView = v.findViewById(R.id.thread_title)
        val meta: TextView = v.findViewById(R.id.thread_meta)
        val time: TextView = v.findViewById(R.id.thread_time)
        val ultimo: TextView = v.findViewById(R.id.thread_ultimo)
        val zonaUltimo: View = v.findViewById(R.id.thread_ultimo_zona)
        val replies: TextView = v.findViewById(R.id.thread_replies)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_thread, parent, false)
        return Holder(v)
    }

    override fun getItemCount() = items.size

    // Negrita = hay algo sin leer (FC + memoria local). Ver isThreadUnread. Se separa del
    // bind para poder recalcularla justo tras el click (ver setOnClickListener más abajo):
    // los paneles nativos (hilo) son overlays sobre esta misma lista, no la recargan al
    // cerrarse, así que sin este recálculo el título seguía en negrita hasta el próximo
    // scroll/rebind en vez de pasar a normal "al instante" al volver.
    private fun applyTitleStyle(h: Holder, item: ThreadItem) {
        val bold = isThreadUnread(readThreads?.readReplies(item.tid), item.replies)
        h.title.setTypeface(null, if (bold) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)
        // No leído = texto principal; leído = secundario. Va por la paleta y NO con un
        // 0xFF… a pelo: escrito así se aplicaba en tiempo de ejecución, se saltaba el
        // values-night y en modo oscuro los NO LEÍDOS salían casi negros sobre negro —
        // justo los que más se tienen que ver.
        h.title.setTextColor(col(h.title, if (bold) R.color.fc_texto else R.color.fc_texto_2))
        pintarEstado(h, item, bold)
    }

    /**
     * El sobre de FC en lugar de la cara del autor: rojo si hay algo sin leer, gris si no; un
     * punto turquesa si has escrito en el hilo y un candado si está cerrado.
     *
     * El color sale del MISMO `bold` que la negrita del título, a propósito: el comentario de
     * FC trae su propio `_new`, pero solo sabe lo que has leído en el foro, no en la app, y dos
     * señales de "sin leer" que se contradicen son peor que una.
     */
    private fun pintarEstado(h: Holder, item: ThreadItem, sinLeer: Boolean) {
        h.sobre.setColorFilter(col(h.sobre, if (sinLeer) R.color.fc_rojo else R.color.fc_texto_off))
        h.participado.visibility = if (item.estado.participado) View.VISIBLE else View.GONE
        h.candado.visibility = if (item.estado.cerrado) View.VISIBLE else View.GONE
        h.sobre.contentDescription = listOfNotNull(
            if (sinLeer) "Sin leer" else "Leído",
            if (item.estado.participado) "has participado" else null,
            if (item.estado.cerrado) "cerrado" else null
        ).joinToString(", ")
    }

    override fun onBindViewHolder(h: Holder, pos: Int) {
        val item = items[pos]
        h.title.text = item.title
        h.title.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, tituloSp)
        applyTitleStyle(h, item)
        h.meta.text = threadMetaLine(item.author)
        val saltaAlUltimo = item.lastPostUrl.isNotEmpty()
        h.time.text = threadTimeLabel(item.time, saltaAlUltimo)
        h.time.visibility = if (item.time.isEmpty()) View.GONE else View.VISIBLE
        // Sin enlace no se puede tocar: mejor texto muerto que un gesto que no hace nada. La
        // zona es la hora más el nombre de debajo: los dos hablan del último mensaje.
        h.zonaUltimo.isClickable = saltaAlUltimo
        h.zonaUltimo.setOnClickListener(if (saltaAlUltimo) View.OnClickListener { onLastPost(item) } else null)
        pintarUltimo(h, item, pedir = true)
        // Sin número se esconde entero: si no, quedaría el icono suelto.
        h.replies.text = item.replies
        h.replies.visibility = if (item.replies.isNotEmpty()) View.VISIBLE else View.GONE
        // Pulsación larga = acciones del hilo, mismo gesto que en los mensajes.
        h.itemView.setOnLongClickListener { onLongClick(item); true }
        h.itemView.setOnClickListener {
            onClick(item)
            // onClick (MainActivity) llama a readThreads.markRead(...) de forma síncrona
            // antes de volver aquí, así que el repo ya está al día: repinta el título ya
            // mismo en vez de esperar a un rebind que puede no llegar nunca.
            applyTitleStyle(h, item)
        }
    }

    override fun onBindViewHolder(h: Holder, pos: Int, payloads: MutableList<Any>) {
        if (payloads.isNotEmpty() && payloads.all { it == PAYLOAD_ULTIMO }) {
            pintarUltimo(h, items[pos], pedir = false)
            return
        }
        super.onBindViewHolder(h, pos, payloads)
    }

    /**
     * La línea del último que escribe. GONE cuando no se va a saber nunca (apagado, sin enlace al
     * último mensaje, o sin hora con la que alinearlo); INVISIBLE mientras se pregunta, para que
     * la fila no salte al llegar el nombre.
     */
    private fun pintarUltimo(h: Holder, item: ThreadItem, pedir: Boolean) {
        val pid = UltimosPosteadores.pidDe(item.lastPostUrl)
        if (!mostrarUltimo || item.time.isEmpty() || (pid.isEmpty() && item.replies.trim() != "0")) {
            h.ultimo.visibility = View.GONE
            return
        }
        val quien = UltimosPosteadores.sinPreguntar(item.replies, item.author)
            ?: autorUltimo(pid)
        if (quien != null && quien.isEmpty()) {
            // Se sabe, pero no se enseña (alguien a quien ignoras): hueco, y sin volver a pedirlo.
            h.ultimo.text = ""
            h.ultimo.visibility = View.INVISIBLE
            h.zonaUltimo.contentDescription = "Ir al último mensaje"
        } else if (quien != null) {
            h.ultimo.text = "@$quien"
            h.ultimo.visibility = View.VISIBLE
            h.zonaUltimo.contentDescription = "Ir al último mensaje, de $quien"
        } else {
            h.ultimo.text = ""
            h.ultimo.visibility = View.INVISIBLE
            h.zonaUltimo.contentDescription = "Ir al último mensaje"
            if (pedir) pedirUltimo(pid)
        }
    }

    /** Color de la paleta (respeta el modo oscuro; un 0xFF… a pelo NO). */
    private fun col(v: android.view.View, id: Int) =
        androidx.core.content.ContextCompat.getColor(v.context, id)

}
