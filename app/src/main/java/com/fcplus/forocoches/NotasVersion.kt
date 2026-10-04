package com.fcplus.forocoches

import org.json.JSONObject

/**
 * Qué contar de la versión nueva, y si se puede posponer.
 *
 * La API de Play **no da las notas de la versión**: `AppUpdateInfo` solo expone disponibilidad,
 * `versionCode`, antigüedad, prioridad y bytes. El texto de "Novedades" que se escribe al subir
 * el AAB se queda en la ficha de la tienda. Por eso las notas salen de nuestro `fc_config.json`
 * (repo público propio), y esta clase decide qué enseñar.
 */
data class AvisoVersion(
    val codigo: Int,
    val nombre: String,
    val notas: List<String>,
    val bloqueante: Boolean
)

object NotasVersion {

    /**
     * Cruza lo que dice Play (solo el `versionCode` disponible) con lo que dice nuestra config.
     *
     * Regla clave: las notas **solo** se usan si el `code` del fichero coincide con la versión
     * que Play ofrece. Si no coincide —típico: se publicó la versión y se olvidó actualizar el
     * JSON— se avisa SIN detalle, en vez de describir cambios que no son los que se van a
     * instalar. Y por lo mismo, tampoco se bloquea: bloquear a ciegas dejaría a la gente
     * encerrada por una decisión tomada para otra versión.
     */
    fun para(codigoDisponible: Int, config: JSONObject?): AvisoVersion {
        val bloque = config?.optJSONObject("actualizacion")
            ?: return AvisoVersion(codigoDisponible, "", emptyList(), false)
        if (bloque.optInt("code", 0) != codigoDisponible) {
            return AvisoVersion(codigoDisponible, "", emptyList(), false)
        }
        val arr = bloque.optJSONArray("notas")
        val notas = ArrayList<String>()
        if (arr != null) {
            for (i in 0 until arr.length()) {
                arr.optString(i).trim().takeIf { it.isNotEmpty() }?.let { notas.add(it) }
            }
        }
        return AvisoVersion(
            codigo = codigoDisponible,
            nombre = bloque.optString("nombre").trim(),
            notas = notas,
            bloqueante = bloque.optBoolean("bloqueante", false)
        )
    }

    /** Texto del diálogo. Sin notas se queda en algo honesto en vez de inventar nada. */
    fun mensaje(aviso: AvisoVersion): String {
        if (aviso.notas.isEmpty()) {
            return "Hay una versión nueva de ForoPlus disponible en Google Play."
        }
        return aviso.notas.joinToString("\n") { "•  $it" }
    }

    /** Título del diálogo, con el número de versión si lo sabemos. */
    fun titulo(aviso: AvisoVersion): String =
        if (aviso.nombre.isNotEmpty()) "Versión ${aviso.nombre} disponible" else "Actualización disponible"
}
