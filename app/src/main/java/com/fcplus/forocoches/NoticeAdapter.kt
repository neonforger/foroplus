package com.fcplus.forocoches

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import org.json.JSONObject

/**
 * Una fila del panel de avisos.
 *
 * Los cuatro primeros campos los llenan TODOS los caminos que usan este panel (citas,
 * menciones, "sus mensajes" y la búsqueda por mensajes). Los demás solo los traen las citas y
 * menciones, que es donde el motor sabe separar los datos: [tipo] vacío significa "pinta la
 * fila sencilla de siempre".
 */
data class NoticeItem(
    val url: String,
    val title: String,
    val who: String,
    val text: String,
    /** "cita" | "mencion", o "" cuando esta fila no es ni una cosa ni la otra. */
    val tipo: String = "",
    /** El sello tal cual lo manda FC: SOLO la hora ("14:22"), aunque sea de otro día. */
    val sello: String = "",
    /** El trozo del mensaje. FC ya lo trunca él mismo. */
    val extracto: String = ""
)

/**
 * "@usuario te citó", con el nombre en rojo y peso normal (propuesta de Márquez, 2026-09-25):
 * de un vistazo se ve QUIÉN, que es lo primero que se busca en esta lista. Solo el nombre: el
 * verbo sigue en el gris de la línea.
 */
fun lineaAutorAviso(who: String, verbo: String, rojo: Int): CharSequence {
    if (who.isEmpty()) return verbo
    val nombre = "@$who"
    return android.text.SpannableString("$nombre $verbo").apply {
        setSpan(android.text.style.ForegroundColorSpan(rojo), 0, nombre.length,
            android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    }
}

data class NoticesPayload(
    val kind: String,
    val items: List<NoticeItem>,
    val error: String,
    /** URL final de la búsqueda (`search.php?searchid=N`): de ahí salen las páginas siguientes. */
    val url: String = ""
)

fun parseNoticesPayload(json: String): NoticesPayload? = try {
    val root = JSONObject(json)
    val err = root.optString("error", "")
    val arr = root.optJSONArray("items")
    val items = ArrayList<NoticeItem>()
    if (arr != null) {
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val url = o.optString("url").trim()
            if (url.isEmpty()) continue
            items.add(
                NoticeItem(
                    url = url,
                    title = o.optString("title").trim(),
                    who = o.optString("who").trim(),
                    text = o.optString("text").trim(),
                    tipo = o.optString("tipo").trim(),
                    sello = o.optString("sello").trim(),
                    extracto = o.optString("extracto").trim()
                )
            )
        }
    }
    NoticesPayload(root.optString("kind"), items, err, root.optString("url").trim())
} catch (_: Exception) {
    null
}

/** Lista nativa de citas o menciones (Bloque B), aisladas de todo lo demás. */
class NoticeAdapter(
    private val onClick: (NoticeItem) -> Unit
) : RecyclerView.Adapter<NoticeAdapter.Holder>() {

    private val items = ArrayList<NoticeItem>()

    /** Ids (pid) de las que se pintan como nuevas. Lo decide [NoticiasVistas], no FC. */
    private var nuevas: Set<String> = emptySet()

    /** Lo que hay pintado ahora mismo. Lo usa "Marcar todo leído" para repintarlo apagado. */
    fun items(): List<NoticeItem> = items.toList()

    /** Las urls de lo que hay pintado ahora mismo. La usa "Marcar todo leído". */
    fun urls(): List<String> = items.map { it.url }

    fun submit(list: List<NoticeItem>, nuevas: Set<String> = emptySet()) {
        items.clear()
        items.addAll(list)
        this.nuevas = nuevas
        notifyDataSetChanged()
    }

    /** Una página más de mensajes, debajo de lo que ya hay (ver [PaginacionMensajes]). */
    fun append(list: List<NoticeItem>) {
        if (list.isEmpty()) return
        val desde = items.size
        items.addAll(list)
        notifyItemRangeInserted(desde, list.size)
    }

    /** Qué hora es al pintar. Lo pone quien pinta la lista. */
    var ahora: Long = 0L

    /** Fecha REAL de cada mensaje (pid → ms), la que se ha podido averiguar. Ver [SelloNoticia]. */
    var fechas: Map<String, Long> = emptyMap()

    class Holder(v: View) : RecyclerView.ViewHolder(v) {
        val row: LinearLayout = v.findViewById(R.id.notice_row)
        val bar: View = v.findViewById(R.id.notice_unread_bar)
        val avatar: ImageView = v.findViewById(R.id.notice_avatar)
        val meta: LinearLayout = v.findViewById(R.id.notice_meta)
        val chip: TextView = v.findViewById(R.id.notice_chip)
        val time: TextView = v.findViewById(R.id.notice_time)
        val dot: View = v.findViewById(R.id.notice_dot)
        val title: TextView = v.findViewById(R.id.notice_title)
        val sub: TextView = v.findViewById(R.id.notice_sub)
        val extract: TextView = v.findViewById(R.id.notice_extract)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_notice, parent, false)
        // Tarjetas: la barra roja de "sin leer" y el rosa de "nueva" se recortan a la forma
        // redondeada de la tarjeta, o asomarían por las esquinas.
        if (AtributosTema.bool(parent.context, R.attr.fcRecortarFila)) v.clipToOutline = true
        return Holder(v)
    }

    override fun getItemCount() = items.size

    override fun onBindViewHolder(h: Holder, pos: Int) {
        val n = items[pos]
        val nueva = NoticiasVistas.idDe(n.url) in nuevas
        val ctx = h.title.context
        // Antes que nada y en un solo sitio: la rama sencilla hace `return` y se lo saltaría.
        h.itemView.setOnClickListener { onClick(n) }

        // LAS TRES SEÑALES DE "SIN LEER" VAN JUNTAS: fondo, barra y punto. Se encienden aquí y
        // en ningún otro sitio — misma regla que la barra de avisos y la de páginas. Media
        // señal (el punto sin el fondo, pongamos) se lee como un adorno y no como un estado.
        // Leída: el color de la fila según el estilo (fondo en la Compacta, tarjeta en Tarjetas).
        h.row.setBackgroundColor(
            if (nueva) ContextCompat.getColor(ctx, R.color.fc_resaltado)
            else AtributosTema.color(ctx, R.attr.fcFilaColor)
        )
        h.bar.visibility = if (nueva) View.VISIBLE else View.GONE
        h.dot.visibility = if (nueva) View.VISIBLE else View.GONE

        if (n.tipo.isEmpty()) {
            // "Sus mensajes" y la búsqueda por mensajes: la fila de siempre.
            h.avatar.visibility = View.GONE
            h.meta.visibility = View.GONE
            h.extract.visibility = View.GONE
            h.title.text = n.title.ifEmpty { "(hilo)" }
            h.title.setTypeface(null, if (nueva) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)
            h.title.setTextColor(
                ContextCompat.getColor(ctx, if (nueva) R.color.fc_texto else R.color.fc_texto_2)
            )
            val extra = n.text.replace(n.title, "").replace(Regex("\\s+"), " ").trim()
            h.sub.text = when {
                n.who.isNotEmpty() && extra.isNotEmpty() -> "@${n.who} · $extra"
                n.who.isNotEmpty() -> "@${n.who}"
                else -> extra
            }
            h.sub.visibility = if (h.sub.text.isNullOrEmpty()) View.GONE else View.VISIBLE
            return
        }

        // Cita o mención: cada dato en su sitio.
        h.avatar.visibility = View.VISIBLE
        h.avatar.setImageBitmap(
            AvatarInicial.bitmap(n.who, (40 * ctx.resources.displayMetrics.density).toInt())
        )
        // Las leídas pierden color, no forma: el avatar se queda pero apagado, para que la
        // columna no baile entre una fila y la siguiente.
        h.avatar.alpha = if (nueva) 1f else 0.45f

        h.meta.visibility = View.VISIBLE
        h.chip.text = if (n.tipo == "cita") "Cita" else "Mención"
        h.time.text = SelloNoticia.texto(n.sello, fechas[replyPidFromUrl(n.url)], ahora)

        h.title.text = n.title.ifEmpty { "(hilo)" }
        h.title.setTypeface(null, if (nueva) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)
        h.title.setTextColor(
            ContextCompat.getColor(ctx, if (nueva) R.color.fc_texto else R.color.fc_texto_2)
        )

        val verbo = if (n.tipo == "cita") "te citó" else "te mencionó"
        h.sub.visibility = View.VISIBLE
        h.sub.text = lineaAutorAviso(n.who, verbo, ContextCompat.getColor(ctx, R.color.fc_rojo))

        h.extract.visibility = if (n.extracto.isEmpty()) View.GONE else View.VISIBLE
        h.extract.text = n.extracto
    }
}
