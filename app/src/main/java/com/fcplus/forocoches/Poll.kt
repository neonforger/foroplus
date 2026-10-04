package com.fcplus.forocoches

import org.json.JSONObject

/**
 * Encuesta de un hilo. El modelo es el CONTRATO de la app, no el markup de FC: extractor.js
 * (`parsePoll`) traduce el HTML del foro a este JSON y aquí solo se valida y se da formato.
 * Sin dependencias de Android → testeable en la JVM (mismo enfoque que [sampleSizeFor]).
 */
data class PollOption(
    /** Número de opción tal cual lo espera FC al votar (1..n). "" si no se pudo leer. */
    val num: String,
    val text: String,
    val votes: Int,
    /** Porcentaje ya resuelto (0..100): el de FC si lo trajo, si no calculado. */
    val pct: Double,
    /** La opción que votó el usuario. FC la marca con <em> en los resultados. */
    val mine: Boolean = false
)

data class Poll(
    val id: String,
    val question: String,
    /** Multi-respuesta: se marcan varias opciones (checkbox) en vez de una (radio). */
    val multiple: Boolean,
    val closed: Boolean,
    /** El usuario ya votó en esta encuesta. */
    val voted: Boolean,
    /** Se puede emitir un voto ahora mismo (hay formulario y conocemos el id). */
    val canVote: Boolean,
    /** FC nos ha dado los resultados. Falso en las que los ocultan hasta votar. */
    val hasResults: Boolean,
    /** Total de votantes según FC (en las multi-respuesta NO es la suma de opciones). */
    val totalVotes: Int,
    val options: List<PollOption>
)

/**
 * Convierte el objeto `poll` del payload del hilo en [Poll], o null si no hay encuesta
 * utilizable. Tolerante a campos ausentes: una encuesta rara nunca puede tumbar el hilo.
 */
fun parsePoll(o: JSONObject?): Poll? {
    if (o == null) return null
    return try {
        val arr = o.optJSONArray("options") ?: return null
        val raw = ArrayList<PollOption>(arr.length())
        val rawPct = ArrayList<Double>(arr.length())
        var sum = 0
        for (i in 0 until arr.length()) {
            val e = arr.optJSONObject(i) ?: continue
            val text = e.optString("text").trim()
            if (text.isEmpty()) continue
            val votes = e.optInt("votes", 0).coerceAtLeast(0)
            sum += votes
            raw.add(
                PollOption(
                    num = e.optString("num").trim(),
                    text = text,
                    votes = votes,
                    pct = 0.0, // se resuelve abajo, cuando se conoce la base
                    mine = e.optBoolean("mine", false)
                )
            )
            // -1 = FC no imprimió porcentaje; se calcula abajo.
            rawPct.add(e.optDouble("pct", -1.0))
        }
        if (raw.isEmpty()) return null

        val total = o.optInt("totalVotes", 0).coerceAtLeast(0)
        // Base del porcentaje: los votantes que dice FC. En las multi-respuesta la suma de
        // opciones supera a los votantes (cada uno marca varias), por eso NO se usa la suma
        // salvo que FC no diera total.
        val base = if (total > 0) total else sum
        val options = raw.mapIndexed { i, o ->
            val p = rawPct[i]
            o.copy(
                pct = when {
                    p >= 0.0 -> p
                    base > 0 -> o.votes * 100.0 / base
                    else -> 0.0
                }
            )
        }

        val id = o.optString("id").trim()
        val closed = o.optBoolean("closed", false)
        Poll(
            id = id,
            question = o.optString("question").trim(),
            multiple = o.optBoolean("multiple", false),
            closed = closed,
            voted = o.optBoolean("voted", false),
            // Sin id no hay forma de montar el POST, por muy abierta que esté.
            canVote = o.optBoolean("canVote", false) && !closed && id.isNotEmpty(),
            hasResults = o.optBoolean("hasResults", sum > 0 || total > 0),
            totalVotes = total,
            options = options
        )
    } catch (_: Exception) {
        null
    }
}

/** Miles con punto, como escribe FC ("1.204"). Manual para no depender del Locale de la JVM. */
fun formatVotes(n: Int): String {
    if (n <= 0) return "0"
    val s = n.toString()
    val sb = StringBuilder(s.length + s.length / 3)
    for (i in s.indices) {
        if (i > 0 && (s.length - i) % 3 == 0) sb.append('.')
        sb.append(s[i])
    }
    return sb.toString()
}

/** "sin votos" / "1 voto" / "1.204 votos". */
fun votesLabel(n: Int): String = when {
    n <= 0 -> "sin votos"
    n == 1 -> "1 voto"
    else -> "${formatVotes(n)} votos"
}

/**
 * Texto de la barra bajo la cabecera del hilo: "Encuesta · 1.204 votos".
 *
 * OJO con el recuento: FC OCULTA los resultados hasta que votas, así que en ese estado no
 * sabemos cuánta gente ha votado. Poner "sin votos" ahí sería mentir (la encuesta puede tener
 * cientos) → cuando no hay resultados se invita a votar y no se da ninguna cifra.
 */
fun pollSummary(poll: Poll): String = when {
    poll.closed -> "Encuesta cerrada · ${votesLabel(poll.totalVotes)}"
    !poll.hasResults && poll.canVote -> "Encuesta · toca para votar"
    !poll.hasResults -> "Encuesta"
    else -> "Encuesta · ${votesLabel(poll.totalVotes)}"
}

/** Porcentaje corto para la fila de resultados; evita que un 0,4% se pinte como "0%". */
fun pctText(pct: Double): String = when {
    pct <= 0.0 -> "0%"
    pct < 1.0 -> "<1%"
    else -> "${Math.round(pct)}%"
}

/** Fracción 0..1 para el ancho de la barra de una opción. */
fun barFraction(pct: Double): Float = (pct / 100.0).coerceIn(0.0, 1.0).toFloat()

/** Pie de la hoja: qué puede hacer el usuario y cuánta gente ha votado. */
fun pollFooter(poll: Poll): String = when {
    poll.closed -> "Encuesta cerrada · ${votesLabel(poll.totalVotes)}"
    !poll.hasResults && poll.canVote -> "Vota para ver los resultados"
    poll.voted -> "Ya has votado · ${votesLabel(poll.totalVotes)}"
    else -> votesLabel(poll.totalVotes)
}
