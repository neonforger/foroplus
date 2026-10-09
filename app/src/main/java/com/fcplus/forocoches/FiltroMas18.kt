package com.fcplus.forocoches

/**
 * Los chips de la sección +18. Cada usuario ve lo que quiera (dueño, 2026-10-09): la primera vez,
 * todo encendido. Se guardan las APAGADAS, no las encendidas, para que una etiqueta nueva salga
 * encendida sin migrar nada. El chip "Peñas" es un veto (apagado esconde los hilos de peña aunque
 * lleven +18) y nunca hace visible un hilo por sí solo; un hilo que solo sea de peña no entra en
 * la sección.
 */
object FiltroMas18 {
    const val PREF = "mas18_apagadas"

    fun apagadas(guardado: String): Set<String> =
        guardado.split(',').map { it.trim() }.filter { it in EtiquetasHilo.TODAS }.toSet()

    fun guardar(apagadas: Set<String>): String = apagadas.filter { it in EtiquetasHilo.TODAS }.joinToString(",")

    fun visible(h: HiloMas18, apagadas: Set<String>): Boolean {
        if ("peña" in h.etiquetas && "peña" in apagadas) return false
        return h.etiquetas.any { it != "peña" && it !in apagadas }
    }

    /** Añade una página a lo ya pintado sin repetir hilos (el archivo puede reordenarse entre páginas). */
    fun unir(antes: List<HiloMas18>, nuevos: List<HiloMas18>): List<HiloMas18> {
        val vistos = antes.mapTo(HashSet()) { it.tid }
        return antes + nuevos.filter { vistos.add(it.tid) }
    }

    fun textoVacio(apagadas: Set<String>): String =
        if (EtiquetasHilo.TODAS.all { it in apagadas }) "Enciende algún filtro para ver hilos"
        else "No hay hilos que mostrar"
}
