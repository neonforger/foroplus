package com.fcplus.forocoches

/**
 * Por qué una lista de hilos ha llegado con hilos y se ha quedado vacía.
 *
 * Lo reportó Juan el 2026-10-23: al buscar los hilos de alguien que tiene en ignorados, la app
 * decía "No hay hilos que mostrar", como si no tuviera ninguno. Los tenía: el filtro de
 * ignorados se los llevaba todos. Ahora se dice.
 */
object AvisoListaVacia {

    /**
     * El aviso, o null si no es el caso (la lista no estaba vacía en origen, o no todos los
     * hilos eran de gente ignorada: entonces los ha tapado otro filtro y se deja el genérico).
     *
     * @param autores el autor de cada hilo que trajo FC, ANTES de filtrar.
     * @param ignorados los usuarios ignorados, como los guarda la app.
     */
    fun porIgnorados(autores: List<String>, ignorados: Collection<String>): String? {
        if (autores.isEmpty()) return null
        val ign = ignorados.map { it.lowercase() }.toHashSet()
        if (!autores.all { it.isNotEmpty() && it.lowercase() in ign }) return null
        val distintos = autores.distinctBy { it.lowercase() }
        return if (distintos.size == 1)
            "No hay hilos que mostrar: son de @${distintos[0]}, que tienes en ignorados"
        else "No hay hilos que mostrar: son de usuarios que tienes en ignorados"
    }
}
