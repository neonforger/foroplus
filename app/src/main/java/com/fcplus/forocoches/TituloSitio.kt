package com.fcplus.forocoches

/** Lo que la cabecera de la lista nativa dice y si se puede tocar para cambiar de sitio. */
data class TituloLista(val texto: String, val selector: Boolean)

/**
 * Qué título lleva la cabecera según de dónde sea la lista (puro, sin Android).
 *
 * En un subforo, en el Popurrí, en +18 y en Música la cabecera es un SELECTOR de sitio: la marca
 * en rojo y, detrás, el sitio con un ▾. En el resto (Suscripciones, Mis hilos, búsquedas, hilos
 * del momento…) es texto plano, como siempre.
 */
object TituloSitio {
    const val MARCA = "ForoPlus"
    const val SEPARADOR = "  "
    const val CHEVRON = " ▾"
    private const val PUNTOS = "…"

    /**
     * El título de las listas que son "un sitio", o null si la fuente no lo decide aquí (el que
     * llama pone su propio texto plano: Suscripciones, Mis hilos, búsquedas…).
     */
    fun deLista(listSource: String, forumId: Int, nombres: Map<Int, String>): TituloLista? =
        when (listSource) {
            "musica" -> TituloLista("Música", true)
            "popurri" -> TituloLista("Popurrí", true)
            "mas18" -> TituloLista("+18", true)
            "top" -> TituloLista("Hilos del momento", false)
            "home" -> nombres[forumId]?.let { TituloLista(it, true) } ?: TituloLista(MARCA, false)
            else -> null
        }

    fun descripcion(nombre: String) = "Estás en $nombre. Toca para cambiar de subforo"

    /**
     * Acorta SOLO el nombre del sitio para que quepa en [disponible] (en las unidades de
     * [medir]): el mayor prefijo que, con "…", cabe. Nunca deja un espacio antes de los puntos y
     * como mínimo devuelve "…".
     */
    fun recortar(nombre: String, disponible: Float, medir: (String) -> Float): String {
        if (medir(nombre) <= disponible) return nombre
        var lo = 0
        var hi = nombre.length - 1   // el nombre entero ya no cabe
        while (lo < hi) {
            val mid = (lo + hi + 1) / 2
            if (medir(nombre.substring(0, mid).trimEnd() + PUNTOS) <= disponible) lo = mid else hi = mid - 1
        }
        return nombre.substring(0, lo).trimEnd() + PUNTOS
    }
}
