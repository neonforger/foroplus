package com.fcplus.forocoches

import org.json.JSONObject

/** Lo que trajo una versión, contado para quien usa la app (no para quien la programa). */
data class VersionNotas(
    val codigo: Int,
    val nombre: String,
    /** `yyyy-MM-dd`, o "" si no se sabe. */
    val fecha: String,
    val notas: List<String>
)

/**
 * El historial de versiones de "Acerca de" (lo pidió Juan: ayuda a descubrir funciones que
 * pasaron desapercibidas).
 *
 * Vive DENTRO del APK (`assets/novedades.json`) y no en `fc_config.json` a propósito: el
 * historial es de lo que tienes instalado, así que tiene que ir con la versión, funcionar sin
 * red y no poder contar cosas de una versión que aún no tienes. El bloque `actualizacion` de
 * la config es otra cosa: habla de la versión SIGUIENTE.
 *
 * Las claves (`code`, `nombre`, `notas`) son las mismas que en `fc_config.json`, para que al
 * publicar se copie el bloque tal cual. `NovedadesTest` falla si la versión que se compila no
 * está en el fichero: es el recordatorio del ritual.
 */
object Novedades {

    fun leer(json: String): List<VersionNotas> {
        val arr = try {
            JSONObject(json).optJSONArray("versiones")
        } catch (_: Exception) {
            null
        } ?: return emptyList()
        val lista = ArrayList<VersionNotas>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val codigo = o.optInt("code", 0)
            if (codigo <= 0) continue
            val notas = ArrayList<String>()
            o.optJSONArray("notas")?.let { n ->
                for (j in 0 until n.length()) n.optString(j).trim().takeIf { it.isNotEmpty() }?.let(notas::add)
            }
            lista.add(VersionNotas(codigo, o.optString("nombre").trim(), o.optString("fecha").trim(), notas))
        }
        return lista.distinctBy { it.codigo }.sortedByDescending { it.codigo }
    }

    /**
     * Solo hasta la versión instalada. Con `0` (no se pudo leer) se enseña todo: es peor
     * esconder el historial entero que enseñar una versión de más.
     */
    fun hasta(versiones: List<VersionNotas>, codigoInstalado: Int): List<VersionNotas> =
        if (codigoInstalado <= 0) versiones else versiones.filter { it.codigo <= codigoInstalado }

    private val MESES = arrayOf("ene", "feb", "mar", "abr", "may", "jun",
                                "jul", "ago", "sep", "oct", "nov", "dic")

    /** `2026-09-24` → `24 sep 2026`. Lo que no entiende lo devuelve vacío. */
    fun fechaLegible(iso: String): String {
        val m = Regex("""^(\d{4})-(\d{2})-(\d{2})$""").find(iso.trim()) ?: return ""
        val (anio, mes, dia) = m.destructured
        val nombreMes = MESES.getOrNull(mes.toInt() - 1) ?: return ""
        return "${dia.toInt()} $nombreMes $anio"
    }

    fun cabecera(v: VersionNotas): String {
        val fecha = fechaLegible(v.fecha)
        return if (fecha.isEmpty()) v.nombre else "${v.nombre} · $fecha"
    }
}
