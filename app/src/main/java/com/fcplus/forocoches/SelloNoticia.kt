package com.fcplus.forocoches

import java.util.Calendar

/**
 * Cuándo llegó una cita o una mención, en lenguaje humano.
 *
 * **La lista de FC sella cada fila SOLO con la hora** ("21:38"), sea de hoy o de hace tres días.
 * Medido por CDP el 2026-09-24: tres filas selladas "10:58", "21:50" y "21:38" eran, abriendo
 * cada mensaje, **Hoy 10:58**, **Ayer 21:50** y **22-sep-2026 21:38**. Hasta ese día la app daba
 * por hecho que toda hora era de hoy (las del primer sondeo, el 21, lo eran) y un tester vio
 * "hace 1 h" en citas de AYER a las 9:30.
 *
 * Tampoco vale deducir el día por el orden de la lista: la de las 21:38 habría salido "ayer" y
 * era de anteayer. La fecha buena solo está en el propio mensaje, así que la pide el motor
 * (`fcNoticeDate`) una vez por cita y se guarda para siempre: la fecha de un mensaje no cambia.
 * Mientras no llega, la hora tal cual — nunca un "hace X" inventado.
 *
 * Puro a propósito: el reloj entra por parámetro.
 */
object SelloNoticia {

    private val MESES = listOf(
        "ene", "feb", "mar", "abr", "may", "jun",
        "jul", "ago", "sep", "oct", "nov", "dic"
    )

    /**
     * Lo que se pinta en la fila.
     *
     * @param sello lo que manda la lista de FC ("21:38").
     * @param momento la fecha REAL del mensaje (ms), o null si todavía no se sabe.
     */
    fun texto(sello: String, momento: Long?, ahora: Long): String =
        if (momento != null) relativo(momento, ahora) else sello.trim()

    /** "ahora", "hace 20 min", "hace 3 h", "ayer 21:50", "22 sep" o "3 dic 2025". */
    fun relativo(momento: Long, ahora: Long): String {
        val minutos = (ahora - momento) / 60_000L
        // Un reloj del móvil un poco adelantado dejaría el mensaje "en el futuro".
        if (minutos < 1) return "ahora"
        if (minutos < 60) return "hace $minutos min"

        val m = Calendar.getInstance().apply { timeInMillis = momento }
        val a = Calendar.getInstance().apply { timeInMillis = ahora }
        val mismoDia = m.get(Calendar.YEAR) == a.get(Calendar.YEAR) &&
            m.get(Calendar.DAY_OF_YEAR) == a.get(Calendar.DAY_OF_YEAR)
        // De hoy, o de anoche pero hace poco: en horas se entiende mejor.
        if (mismoDia || minutos < 6 * 60) return "hace ${minutos / 60} h"

        val ayer = Calendar.getInstance().apply { timeInMillis = ahora; add(Calendar.DAY_OF_YEAR, -1) }
        val hora = "${m.get(Calendar.HOUR_OF_DAY)}:${"%02d".format(m.get(Calendar.MINUTE))}"
        if (m.get(Calendar.YEAR) == ayer.get(Calendar.YEAR) &&
            m.get(Calendar.DAY_OF_YEAR) == ayer.get(Calendar.DAY_OF_YEAR)
        ) return "ayer $hora"

        val dia = "${m.get(Calendar.DAY_OF_MONTH)} ${MESES[m.get(Calendar.MONTH)]}"
        return if (m.get(Calendar.YEAR) == a.get(Calendar.YEAR)) dia else "$dia ${m.get(Calendar.YEAR)}"
    }

    // ── Caché pid → fecha (ms) ───────────────────────────────────────────────
    // Formato "pid:ms,pid:ms". Con tope: se quedan los pid MÁS ALTOS, que en FC son los mensajes
    // más recientes — los que siguen apareciendo en la lista de citas.

    fun leerCache(s: String): Map<String, Long> {
        val out = HashMap<String, Long>()
        for (par in s.split(',')) {
            val i = par.indexOf(':')
            if (i <= 0) continue
            val pid = par.substring(0, i)
            val ms = par.substring(i + 1).toLongOrNull() ?: continue
            if (pid.all { it.isDigit() }) out[pid] = ms
        }
        return out
    }

    fun guardarCache(m: Map<String, Long>, tope: Int = 400): String =
        m.entries
            .sortedByDescending { it.key.toLongOrNull() ?: 0L }
            .take(tope)
            .joinToString(",") { "${it.key}:${it.value}" }
}
