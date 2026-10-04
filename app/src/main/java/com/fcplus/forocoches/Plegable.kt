package com.fcplus.forocoches

/**
 * El rótulo de una lista que se pliega en Opciones.
 *
 * Lo pidió el dueño el 2026-08-28: las listas de Opciones (ignorados, palabras filtradas y
 * los subforos del Popurrí) se pintan enteras, una fila por elemento, dentro del ScrollView
 * del panel. Con una lista de verdad —la del Popurrí son TODOS los subforos del foro, unos
 * treinta— eso es una pared que hay que recorrer para llegar a lo de abajo.
 *
 * La cura no es un scroll dentro del scroll: eso es la trampa de gestos que ya se descartó
 * con las citas largas ("una caja con scroll dentro de una lista que scrollea"). Se pliega,
 * igual que las citas, y la lista se enseña entera cuando se pide.
 *
 * Aquí solo vive el texto del botón, que es la única parte con reglas: el singular, y que
 * **con la lista vacía no hay botón** — un botón que abre un hueco no es un atajo.
 */
object Plegable {

    /**
     * Texto del botón que abre o cierra la lista, o **cadena vacía** si no hay nada que
     * enseñar (y entonces la fila no se pinta).
     *
     * Sin artículo a propósito: sería "los 37 ignorados" pero "las 4 palabras", y arrastrar
     * el género hasta aquí por tres sitios de llamada no compensa.
     */
    fun rotulo(cuantos: Int, singular: String, plural: String, abierto: Boolean): String {
        if (cuantos <= 0) return ""
        val cosa = if (cuantos == 1) singular else plural
        return if (abierto) "Ocultar $cuantos $cosa  ▴" else "Ver $cuantos $cosa  ▾"
    }
}
