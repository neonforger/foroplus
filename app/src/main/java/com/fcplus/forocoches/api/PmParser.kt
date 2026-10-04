package com.fcplus.forocoches.api

import org.jsoup.Jsoup

/** Una conversación de la bandeja de MPs. */
data class PrivateMessage(
    val pmid: Long,
    val subject: String,
    val sender: String,
    val senderId: Long,
    val dateText: String
)

/** Preview lista para una notificación estilo WhatsApp. */
data class PmPreview(
    val sender: String,
    val subject: String,
    val snippet: String,
    val url: String
)

/**
 * Parsea la bandeja de MPs (`private.php`) y el detalle de un MP (`do=showpm`).
 *
 * Estructura real del skin móvil de FC:
 *  - cada fila: `<a href="...do=showpm&pmid=X"><strong>ASUNTO</strong></a>`
 *  - remitente: `<span onclick="...member.php?u=UID">NOMBRE</span>` (mismo contenedor)
 *  - fecha: el `<div>` hermano siguiente (spans con fecha y hora)
 *  - cuerpo (en showpm): `<div id="post_message_">…texto…</div>`
 */
object PmParser {

    private val PMID = Regex("""pmid=(\d+)""")
    private val UID = Regex("""u=(\d+)""")

    fun parseInbox(html: String): List<PrivateMessage> {
        val doc = Jsoup.parse(html, "https://forocoches.com/foro/")
        // Un MP por cada ancla de asunto; su remitente se busca DENTRO de su propia fila
        // (ver rowOf). Los MP leídos y sin leer se maquetan distinto, así que el emparejamiento
        // no puede depender del orden ni de la distancia entre nodos.
        val seen = HashSet<Long>()
        val result = mutableListOf<PrivateMessage>()

        for (a in doc.select("a[href*=do=showpm]")) {
            if (!isShowpmAnchor(a)) continue
            val pmid = PMID.find(a.attr("href"))?.groupValues?.get(1)?.toLongOrNull() ?: continue
            if (!seen.add(pmid)) continue
            val subject = (a.selectFirst("strong") ?: a).text().trim()
            if (subject.isEmpty()) continue

            // Remitente: SOLO el que vive dentro de la propia fila de este asunto. Antes se
            // escaneaba por índices hacia delante y, si fallaba, hacia atrás — y ese respaldo
            // aterriza siempre en el remitente de la fila ANTERIOR (en la bandeja, ordenada de
            // nuevo a viejo, eso es "quien te mandó el privado anterior", justo lo que reportó
            // un tester el 2026-08-12). Si la fila no tiene remitente se devuelve vacío: la
            // notificación ya sabe degradar a "Mensaje privado", que es mejor que atribuirle
            // el mensaje a otra persona.
            val senderEl = senderIn(rowOf(a))
            val sender = senderEl?.text()?.trim().orEmpty()
            val senderId = senderEl?.let {
                UID.find(it.attr("onclick"))?.groupValues?.get(1)
                    ?: UID.find(it.attr("href"))?.groupValues?.get(1)
            }?.toLongOrNull() ?: 0L

            val dateText = a.parent()?.nextElementSibling()
                ?.select("span")?.joinToString(" ") { it.text().trim() }?.trim().orEmpty()

            result.add(PrivateMessage(pmid, subject, sender, senderId, dateText))
        }
        return result
    }

    private fun isShowpmAnchor(e: org.jsoup.nodes.Element): Boolean =
        e.normalName() == "a" && e.attr("href").contains("do=showpm")

    /** ¿Es un elemento que identifica al remitente (con texto no vacío)? */
    private fun isSender(e: org.jsoup.nodes.Element): Boolean {
        if (e.text().trim().isEmpty()) return false
        return e.attr("onclick").contains("member.php") ||
            (e.normalName() == "a" && e.attr("href").contains("member.php"))
    }

    /**
     * "Fila" de un asunto: el mayor ancestro que sigue conteniendo **solo ese** asunto. No
     * depende del maquetado — vale para el `<li>` del skin viejo (`remitente: asunto`) y para
     * los `div` anidados del moderno (`asunto`, luego `remitente`) — y es lo que hace
     * imposible atribuirle a un mensaje el remitente del de al lado.
     */
    private fun rowOf(subject: org.jsoup.nodes.Element): org.jsoup.nodes.Element {
        var row = subject
        var p = row.parent()
        while (p != null && p.select("a[href*=do=showpm]").size <= 1) {
            row = p
            p = row.parent()
        }
        return row
    }

    /** Primer elemento de la fila que identifica a una persona, o null si no hay ninguno. */
    private fun senderIn(row: org.jsoup.nodes.Element): org.jsoup.nodes.Element? =
        row.select("[onclick*=member.php], a[href*=member.php]").firstOrNull { isSender(it) }

    /** Texto plano del cuerpo de un MP (página `do=showpm`). */
    fun parseBody(html: String): String =
        Jsoup.parse(html).getElementById("post_message_")?.text()?.trim().orEmpty()
}
