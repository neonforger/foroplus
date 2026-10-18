package com.fcplus.forocoches

/**
 * El tamaño de la letra de los mensajes y de los títulos del listado, en sp.
 *
 * Hasta el 2026-10-18 eran tres botones (Pequeña / Normal / Grande = 13 / 15 / 18) y se guardaba
 * el ÍNDICE. Ahora son dos barras de [MIN] a [MAX] y se guarda el sp; quien tenía un índice
 * guardado conserva su tamaño ([leer] lo traduce), así que nadie ve cambiar su letra al actualizar.
 *
 * Puro para poder probarlo; lo usan [OptionsController] y [ResumenOpciones].
 */
object TamanoTexto {

    const val MIN = 12
    const val MAX = 22

    /** El de siempre: el 15sp que tenían los mensajes y `item_thread` desde el principio. */
    const val NORMAL = 15

    /** Lo que valía cada botón de antes. */
    private val DE_INDICE = intArrayOf(13, NORMAL, 18)

    /**
     * El tamaño a aplicar: el guardado en sp si lo hay; si no, el del índice viejo; si tampoco,
     * [NORMAL]. Siempre dentro de la barra, para que lo que se aplica y lo que marca coincidan.
     */
    fun leer(sp: Int?, indiceViejo: Int?): Int = when {
        sp != null -> sp.coerceIn(MIN, MAX)
        indiceViejo != null -> DE_INDICE[indiceViejo.coerceIn(0, DE_INDICE.size - 1)]
        else -> NORMAL
    }

    /** Lo que dice TalkBack de la barra: "Normal" en el de siempre, el número en el resto. */
    fun etiqueta(sp: Int): String = if (sp == NORMAL) "Normal" else "$sp"

    /**
     * Cómo se nombra en el resumen de Opciones, sin números: por debajo, en o por encima del de
     * siempre. [plural] para los títulos ("títulos pequeños").
     */
    fun palabra(sp: Int, plural: Boolean = false): String {
        val base = when {
            sp < NORMAL -> "pequeña"
            sp == NORMAL -> "normal"
            else -> "grande"
        }
        return if (!plural) base else when (base) {
            "pequeña" -> "pequeños"
            "normal" -> "normales"
            else -> "grandes"
        }
    }
}
