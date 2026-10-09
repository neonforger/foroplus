package com.fcplus.forocoches

import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

data class HiloMas18(
    val tid: Long,
    val titulo: String,
    val etiquetas: Set<String>,
    val autor: String,
    val respuestas: Int,
    /** Último mensaje (ms UTC); null en los hilos del histórico, que no tienen fecha. */
    val ultimoFecha: Long?,
    val ultimoAutor: String,
    val ultimoPid: Long,
    /** "AAAA-MM" aproximado de creación (sale del tid); "" si no viene. */
    val creadoAprox: String,
)

data class PaginaMas18(
    val v: Int,
    val generado: Long,
    val pagina: Int,
    val totalPaginas: Int,
    /** Hasta qué mes ha llegado la carga del histórico ("AAAA-MM"), "" si no viene. */
    val historicoHasta: String,
    val hilos: List<HiloMas18>,
)

sealed class LecturaMas18 {
    data class Bien(val pagina: PaginaMas18) : LecturaMas18()
    data class Mal(val motivo: String) : LecturaMas18()
}

/**
 * El contrato `paginas/N.json` que publica el lector (spec, "Contrato de una página"). Lo que no
 * se entiende no se pinta, pero NUNCA tira la app: llega HTML de error, un 404, una versión nueva…
 */
object PaginaMas18Parser {
    const val VERSION = 1
    private val MESES = listOf("ene", "feb", "mar", "abr", "may", "jun", "jul", "ago", "sep", "oct", "nov", "dic")

    fun leer(json: String): LecturaMas18 {
        val o = try { JSONObject(json) } catch (_: Exception) {
            return LecturaMas18.Mal("La lista no se ha podido leer")
        }
        val v = o.optInt("v", 0)
        if (v > VERSION) return LecturaMas18.Mal("Hay una versión nueva de la lista: actualiza la app para verla")
        if (v < 1) return LecturaMas18.Mal("La lista no se ha podido leer")
        val pagina = o.optInt("pagina", 1).coerceAtLeast(1)
        val arr = o.optJSONArray("hilos")
        val hilos = ArrayList<HiloMas18>()
        for (i in 0 until (arr?.length() ?: 0)) {
            val h = arr!!.optJSONObject(i) ?: continue
            val tid = h.optLong("tid", 0)
            val titulo = h.optString("titulo").trim()
            if (tid <= 0 || titulo.isEmpty()) continue
            val et = h.optJSONArray("etiquetas")
            val etiquetas = (0 until (et?.length() ?: 0)).map { et!!.optString(it) }.filter { it.isNotEmpty() }.toSet()
                .ifEmpty { EtiquetasHilo.de(titulo) }
            val ult = h.optJSONObject("ultimo")
            hilos.add(HiloMas18(
                tid = tid, titulo = titulo, etiquetas = etiquetas,
                autor = h.optString("autor").trim(),
                respuestas = h.optInt("respuestas", 0),
                ultimoFecha = ult?.optString("fecha")?.let { iso(it) },
                ultimoAutor = ult?.optString("autor")?.trim() ?: "",
                ultimoPid = ult?.optLong("pid", 0) ?: 0,
                creadoAprox = h.optString("creado_aprox").trim(),
            ))
        }
        return LecturaMas18.Bien(PaginaMas18(
            v = v,
            generado = iso(o.optString("generado")) ?: 0,
            pagina = pagina,
            totalPaginas = o.optInt("total_paginas", pagina).coerceAtLeast(pagina),
            historicoHasta = o.optString("historico_hasta").trim(),
            hilos = hilos,
        ))
    }

    private fun iso(s: String): Long? = try {
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
            .apply { timeZone = TimeZone.getTimeZone("UTC") }.parse(s)?.time
    } catch (_: Exception) { null }

    /** "AAAA-MM" → "abr 2024"; "" si no se entiende. */
    fun mesLegible(aaaaMm: String): String {
        val m = Regex("""^(\d{4})-(\d{2})$""").find(aaaaMm) ?: return ""
        val mes = m.groupValues[2].toInt()
        if (mes !in 1..12) return ""
        return "${MESES[mes - 1]} ${m.groupValues[1]}"
    }

    /**
     * La hora de la fila con las MISMAS tres formas que FC ("Hoy 19:55", "Ayer 13:32",
     * "01-jul-2026 14:24"), para que todo lo que ya entiende la lista (Popurri.momento) la entienda.
     * Los hilos del histórico, sin fecha, dicen el mes de creación.
     */
    fun horaFila(h: HiloMas18, ahora: Long): String {
        val f = h.ultimoFecha ?: return mesLegible(h.creadoAprox)
        val zona = TimeZone.getTimeZone("Europe/Madrid")
        val c = Calendar.getInstance(zona).apply { timeInMillis = f }
        val hoy = Calendar.getInstance(zona).apply { timeInMillis = ahora }
        val hm = String.format(Locale.US, "%02d:%02d", c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE))
        val mismoDia = { a: Calendar, b: Calendar ->
            a.get(Calendar.YEAR) == b.get(Calendar.YEAR) && a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)
        }
        if (mismoDia(c, hoy)) return "Hoy $hm"
        val ayer = (hoy.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -1) }
        if (mismoDia(c, ayer)) return "Ayer $hm"
        return String.format(Locale.US, "%02d-%s-%d %s",
            c.get(Calendar.DAY_OF_MONTH), MESES[c.get(Calendar.MONTH)], c.get(Calendar.YEAR), hm)
    }

    fun aThreadItem(h: HiloMas18, ahora: Long): ThreadItem = ThreadItem(
        tid = h.tid.toString(),
        title = h.titulo,
        author = h.autor,
        replies = h.respuestas.toString(),
        time = horaFila(h, ahora),
        url = "https://forocoches.com/foro/showthread.php?t=${h.tid}",
        lastPostUrl = if (h.ultimoPid > 0)
            "https://forocoches.com/foro/showthread.php?p=${h.ultimoPid}#post${h.ultimoPid}" else "",
    )
}
