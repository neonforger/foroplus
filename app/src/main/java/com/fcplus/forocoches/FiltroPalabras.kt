package com.fcplus.forocoches

/**
 * Decide si el título de un hilo cae por el **filtro de palabras**.
 *
 * Nació el 2026-09-17 de un fallo que reportó Márquez: el hilo oficial de **Apple** no le salía
 * ni en el listado, ni en la búsqueda, ni en Suscripciones —aunque ForoCoches sí lo tenía, y el
 * propio motor lo veía al verificar la suscripción—, y él juraba no haber filtrado esa palabra.
 * Tenía razón: el filtro casaba por **subcadena** (`titulo.contains(palabra)`) y la lista de
 * fábrica empieza por **"PP"**, que vive dentro de "a**pp**le". Con la misma regla desaparecían
 * "app", "apps" y "WhatsApp" — en un foro donde media Electrónica habla justo de eso.
 *
 * Y desaparecían **en silencio**: el hilo seguía abriéndose por enlace, así que parecía un fallo
 * de parseo o de ForoCoches, no un filtro haciendo su trabajo demasiado bien.
 *
 * **La regla: la palabra tiene que ABRIR una palabra del título**, no aparecer en medio de otra.
 *
 * Se conserva a propósito la coincidencia por **prefijo** (que "eleccion" siga tapando
 * "elecciones") en vez de exigir la palabra entera: es lo que la gente espera al escribir una
 * palabra suelta, ya funcionaba así y quitarlo dejaría pasar los plurales de golpe a quien tenga
 * el filtro puesto desde hace meses. Lo único que se corta es la coincidencia **interior**, que
 * es la que escondía Apple.
 *
 * `isLetterOrDigit` de Kotlin es Unicode, así que los acentos cuentan como letra: sin eso,
 * "pol í tica" se partiría por la tilde y "tica" casaría como si empezase palabra.
 */
object FiltroPalabras {

    /** true si alguna de [palabras] abre una palabra de [titulo]. */
    fun hayQueOcultar(titulo: String, palabras: List<String>): Boolean {
        if (palabras.isEmpty()) return false
        val t = titulo.lowercase()
        return palabras.any { contiene(t, it) }
    }

    /** [titulo] ya en minúsculas; [palabra] tal cual se guardó. */
    private fun contiene(titulo: String, palabra: String): Boolean {
        val p = palabra.trim().lowercase()
        // Una palabra vacía casa con todo: escondería el foro entero.
        if (p.isEmpty()) return false
        var i = titulo.indexOf(p)
        while (i >= 0) {
            if (abrePalabra(titulo, i)) return true
            i = titulo.indexOf(p, i + 1)
        }
        return false
    }

    /** Abre palabra si está al principio del título o si lo anterior no es letra ni cifra. */
    private fun abrePalabra(titulo: String, i: Int): Boolean =
        i == 0 || !titulo[i - 1].isLetterOrDigit()
}
