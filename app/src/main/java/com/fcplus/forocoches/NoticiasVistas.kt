package com.fcplus.forocoches

/**
 * Qué citas y menciones se pintan como NUEVAS.
 *
 * ForoCoches **no lo dice**. Sondeado por CDP el 2026-09-11 sobre la página real
 * (`member.php?u=…&tab=quotes`): la celda del icono de estado viene **vacía**
 * (`<td class="result-bit-icon"></td>`, el mismo statusicon muerto del gotcha 16) y la fila no
 * trae ninguna clase que distinga leído de sin leer. Lo único que existe es el contador del
 * menú. Por eso la lista salía **entera en negrita** —de hecho ni había regla: el `textStyle`
 * estaba fijo en el layout— y no se distinguía lo nuevo (reportado por Green Floyd).
 *
 * Así que manda la memoria local, igual que en las negritas de los hilos y en [Insignias]: se
 * recuerda lo que ya miraste y solo se resalta lo que no estaba.
 */
object NoticiasVistas {

    /** Tope de ids recordados. La página de FC enseña unas pocas: con esto sobra de largo. */
    const val TOPE = 200

    /**
     * Identidad de una cita/mención: el **pid** de su enlace (`showthread.php?p=518956743#…`).
     * No vale la URL entera: FC le cuelga anclas y parámetros que cambian.
     */
    fun idDe(url: String): String = Regex("[?&]p=(\\d+)").find(url)?.groupValues?.get(1) ?: ""

    /**
     * ¿Se pinta como nueva?
     *
     * En la **primera visita** no se resalta nada aunque no haya memoria de ninguna: si no,
     * la primera vez que abres el panel te aparecerían en negrita las cincuenta de tu historial
     * como si acabaran de llegar.
     */
    fun esNueva(url: String, vistas: Set<String>, primeraVisita: Boolean): Boolean {
        if (primeraVisita) return false
        val id = idDe(url)
        return id.isNotEmpty() && id !in vistas
    }

    /**
     * Lo que se recuerda tras mirar el panel. Cuando se pasa del tope se tiran **las más
     * viejas**, que son las que ya no van a volver a aparecer en la página de FC.
     */
    /**
     * Se guarda como **una cadena ordenada**, igual que los avisos descartados, y no como un
     * `StringSet`: el `Set` de `SharedPreferences` **no conserva el orden**, y sin orden la
     * regla de "al pasar del tope se tiran las más viejas" no significa nada.
     */
    fun leer(guardado: String): Set<String> =
        LinkedHashSet(guardado.split(',').map { it.trim() }.filter { it.isNotEmpty() })

    fun guardar(vistas: Set<String>): String = vistas.joinToString(",")

    fun recordar(vistas: Set<String>, urls: List<String>, tope: Int = TOPE): Set<String> {
        val nuevo = LinkedHashSet(vistas)
        for (u in urls) {
            val id = idDe(u)
            if (id.isNotEmpty()) nuevo.add(id)
        }
        while (nuevo.size > tope) nuevo.remove(nuevo.first())
        return nuevo
    }

    /**
     * Da por leída UNA sola, la que acabas de tocar.
     *
     * Es lo que distingue este panel de una lista cualquiera: antes, abrir la pantalla marcaba
     * la tanda ENTERA (`recordar` con todas las urls), así que entrabas, veías tres resaltadas,
     * salías, volvías y estaban las tres en blanco sin haber abierto ninguna. Con el diseño
     * nuevo —donde lo no leído se ve de lejos— eso cantaba demasiado. Decisión del dueño el
     * 2026-09-21: **se marca al tocar**, y para vaciarlo de golpe está el botón de la cabecera,
     * que sigue llamando a [recordar].
     */
    fun marcarLeida(vistas: Set<String>, url: String, tope: Int = TOPE): Set<String> =
        recordar(vistas, listOf(url), tope)
}
