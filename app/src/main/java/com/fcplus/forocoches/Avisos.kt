package com.fcplus.forocoches

import org.json.JSONObject

/**
 * Un mensaje del autor de la app a quien la usa, servido desde `fc_config.json`.
 *
 * @param id identifica el aviso. Es lo que se recuerda al descartarlo, así que **cambiar el
 *   texto sin cambiar el id NO vuelve a enseñarlo**: para un aviso nuevo, id nuevo.
 * @param fijo no se puede descartar. Solo para "la app está rota y lo sabemos": un aviso que
 *   no se puede quitar es una piedra en el zapato, y se gasta rápido.
 * @param enlace opcional; con él, tocar el aviso abre esa dirección (el Telegram, un hilo…).
 */
data class Aviso(
    val id: String,
    val texto: String,
    val fijo: Boolean,
    val enlace: String
)

/**
 * Decide qué aviso enseñar. Es el ÚNICO canal para hablarle a los usuarios sin publicar una
 * versión en Play: cuando ForoCoches cambie su HTML y la app deje de leer nada (gotcha 17),
 * arreglarlo exige compilar, subir y esperar la revisión de Google — un día o dos —, y hasta
 * ahora la gente se quedaba mirando una app rota **sin ninguna explicación**. Con esto se les
 * puede decir que se sabe y que se está en ello, y eso se edita en un fichero, no en el APK.
 *
 * Lógica pura y sin Android a propósito: es la clase de cosa que hay que poder probar sin
 * enseñarle un aviso equivocado a nadie.
 */
object Avisos {

    /** Tope de ids recordados. Se descartan los más viejos: nadie vuelve a un aviso de 2027. */
    const val MAX_RECORDADOS = 50

    /**
     * @param versionCode la versión INSTALADA, para poder dirigir un aviso solo a quien le
     *   afecta (campo `hasta`). Sin esto, "actualiza que la 31 falla" se le enseñaría también
     *   a quien ya está en la 32, que es justo a quien no hay que molestar.
     * @param descartados ids que este usuario ya cerró.
     */
    fun para(config: JSONObject?, versionCode: Int, descartados: Collection<String>): Aviso? {
        val b = config?.optJSONObject("aviso") ?: return null
        val id = b.optString("id").trim()
        val texto = b.optString("texto").trim()
        // Sin id no se puede recordar que lo cerraste → saldría en cada arranque para siempre.
        // Sin texto no hay nada que contar. En ambos casos, mejor callarse.
        if (id.isEmpty() || texto.isEmpty()) return null

        val hasta = b.optInt("hasta", 0)
        if (hasta > 0 && versionCode > hasta) return null

        val fijo = b.optBoolean("fijo", false)
        if (!fijo && descartados.contains(id)) return null

        return Aviso(id = id, texto = texto, fijo = fijo, enlace = b.optString("enlace").trim())
    }

    /** Los ids descartados viven en una sola cadena, del más viejo al más nuevo. */
    fun leerDescartados(raw: String): List<String> =
        raw.split('\n').map { it.trim() }.filter { it.isNotEmpty() }

    /** Añade un id al final, sin duplicarlo, respetando el tope. */
    fun conDescartado(raw: String, id: String): String {
        val limpio = id.trim()
        if (limpio.isEmpty()) return raw
        val lista = leerDescartados(raw).filter { it != limpio } + limpio
        return lista.takeLast(MAX_RECORDADOS).joinToString("\n")
    }
}
