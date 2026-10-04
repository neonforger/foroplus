package com.fcplus.forocoches

/**
 * Qué se enseña de la firma y del bloque "Sobre mí" de una ficha de ForoCoches.
 *
 * FC no pone etiqueta a los campos de "Sobre mí": cada fila es un ICONO (`var(--coches)`,
 * `var(--ubicacion-icon)`…) y el valor al lado. `extractor.js` entrega la pareja
 * (nombre del icono, valor) tal cual y aquí se decide qué significa y qué se pinta.
 *
 * Sondeado el 2026-09-27 en `member.php`: en una ficha AJENA solo salen los campos rellenos;
 * en la PROPIA salen los cuatro y los vacíos dicen `N/A`, y la firma vacía dice "Aquí se verá
 * tu firma, una vez la configures." Nada de eso es un dato del usuario.
 */
object FichaMiembro {

    data class Campo(val etiqueta: String, val valor: String)

    /** Icono de FC → etiqueta, en el orden en que se pintan. */
    private val ETIQUETAS = linkedMapOf(
        "coches" to "Coche",
        "ubicacion-icon" to "Ubicación",
        "intereses-icon" to "Intereses",
        "work" to "Ocupación"
    )

    /**
     * Los campos que merece la pena pintar, en orden fijo. Un icono que no conocemos se
     * descarta: sin saber qué es no hay etiqueta honesta que ponerle.
     */
    fun campos(crudos: List<Pair<String, String>>): List<Campo> {
        val porIcono = HashMap<String, String>()
        for ((icono, valor) in crudos) {
            val limpio = valor.replace(Regex("\\s+"), " ").trim()
            if (limpio.isEmpty() || limpio.equals("N/A", ignoreCase = true)) continue
            porIcono.putIfAbsent(icono.trim(), limpio)
        }
        return ETIQUETAS.mapNotNull { (icono, etiqueta) ->
            porIcono[icono]?.let { Campo(etiqueta, it) }
        }
    }

    /** Si el texto de la firma es una firma de verdad y no el aviso de "sin configurar". */
    fun hayFirma(texto: String): Boolean {
        val t = texto.trim()
        return t.isNotEmpty() && !t.startsWith("Aquí se verá tu firma", ignoreCase = true)
    }
}
