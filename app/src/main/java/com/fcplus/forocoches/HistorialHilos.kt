package com.fcplus.forocoches

import org.json.JSONArray
import org.json.JSONObject

/**
 * Historial de hilos visitados: los últimos que has abierto, el más reciente arriba.
 *
 * Lo pidió javier hg el 2026-11-16: *"a veces entro a ver un hilo y cuando vuelvo al general lo
 * he perdido porque la lista se ha actualizado y no aparece"*. El listado de un subforo se
 * reordena solo por último mensaje; un hilo que miraste hace diez minutos puede estar ya en la
 * página 3, y sin su título exacto ni el buscador lo encuentra (gotcha 62).
 *
 * Reglas, y por qué:
 * 1. **Sin duplicados**: volver a un hilo lo SUBE arriba, no lo repite. Es un "dónde estaba",
 *    no un registro de cada visita.
 * 2. **Tope de [TOPE]**. Más que eso deja de ser un atajo y pasa a ser una lista que leer.
 * 3. **Al volver a un hilo se conserva lo que ya se sabía** (autor, subforo) si la visita nueva
 *    no lo trae: el autor solo se conoce en la página 1 y una cita te puede dejar en la 7.
 * 4. **Leer es tolerante**: lo guardado por una versión vieja o un fichero roto da una lista
 *    vacía o parcial, nunca un cierre de la app.
 *
 * Es puro (sin Android) a propósito; dónde se guarda y por cuenta lo decide [AlmacenHistorial].
 */
object HistorialHilos {

    const val TOPE = 30

    data class Visita(
        val tid: String,
        val titulo: String,
        /** Subforo del hilo; 0 si no se sabe. */
        val fid: Int = 0,
        /** Nombre del subforo tal y como lo dio la página del hilo ("" si no se sabe). */
        val foro: String = "",
        /** Quien abrió el hilo ("" si no se sabe). */
        val autor: String = "",
        /** Cuándo se abrió, en milisegundos. */
        val cuando: Long = 0L
    )

    /** Deja [v] la primera. Si el hilo ya estaba, sube (y hereda lo que la visita nueva no sepa). */
    fun apuntar(previas: List<Visita>, v: Visita, tope: Int = TOPE): List<Visita> {
        val tid = v.tid.trim()
        val titulo = v.titulo.trim()
        if (tid.isEmpty() || titulo.isEmpty()) return previas
        val antes = previas.firstOrNull { it.tid == tid }
        val nueva = v.copy(
            tid = tid,
            titulo = titulo,
            fid = if (v.fid > 0) v.fid else antes?.fid ?: 0,
            foro = v.foro.trim().ifEmpty { antes?.foro.orEmpty() },
            autor = v.autor.trim().ifEmpty { antes?.autor.orEmpty() }
        )
        return (listOf(nueva) + previas.filter { it.tid != tid }).take(tope.coerceAtLeast(0))
    }

    fun quitar(previas: List<Visita>, tid: String): List<Visita> = previas.filter { it.tid != tid }

    fun guardar(lista: List<Visita>): String {
        val arr = JSONArray()
        for (v in lista) {
            arr.put(JSONObject().apply {
                put("t", v.tid)
                put("n", v.titulo)
                if (v.fid > 0) put("f", v.fid)
                if (v.foro.isNotEmpty()) put("fn", v.foro)
                if (v.autor.isNotEmpty()) put("a", v.autor)
                put("c", v.cuando)
            })
        }
        return arr.toString()
    }

    /** Lo guardado, en orden. Lo que no se entienda se salta; basura entera = lista vacía. */
    fun leer(guardado: String?): List<Visita> {
        if (guardado.isNullOrBlank()) return emptyList()
        val arr = try { JSONArray(guardado) } catch (_: Exception) { return emptyList() }
        val out = ArrayList<Visita>(arr.length())
        val vistos = HashSet<String>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val tid = o.optString("t").trim()
            val titulo = o.optString("n").trim()
            if (tid.isEmpty() || titulo.isEmpty() || !tid.all { it.isDigit() }) continue
            if (!vistos.add(tid)) continue
            out.add(
                Visita(
                    tid = tid,
                    titulo = titulo,
                    fid = o.optInt("f", 0).coerceAtLeast(0),
                    foro = o.optString("fn").trim(),
                    autor = o.optString("a").trim(),
                    cuando = o.optLong("c", 0L).coerceAtLeast(0L)
                )
            )
            if (out.size >= TOPE) break
        }
        return out
    }

    /**
     * "visto hace X". Un reloj que va hacia atrás (hora del móvil cambiada) no da "hace -3 min":
     * se queda en "ahora mismo".
     */
    fun haceCuanto(cuando: Long, ahora: Long): String {
        if (cuando <= 0L) return ""
        val min = ((ahora - cuando) / 60_000L).coerceAtLeast(0L)
        val horas = min / 60
        val dias = horas / 24
        return when {
            min < 1 -> "visto ahora mismo"
            min < 60 -> "visto hace $min min"
            horas < 24 -> "visto hace $horas h"
            dias < 2 -> "visto ayer"
            dias < 30 -> "visto hace $dias días"
            else -> "visto hace más de un mes"
        }
    }

    /** Enlace al hilo a partir de su número: es lo que se abre al tocar la fila. */
    fun url(tid: String): String = "https://forocoches.com/foro/showthread.php?t=$tid"
}
