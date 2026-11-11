package com.fcplus.forocoches

/**
 * Decisiones (puras) de la pestaña TEMPORAL de la barra de subforos: un subforo escondido de la
 * barra que el usuario abre a propósito (selector, enlace, miga) se queda con una pestaña al
 * final, marcada, en vez de provocar el salto al primero visible de `pintarPestanas`.
 *
 * `fueraDeBarra` es el conjunto de fids abiertos así. Se decide por el fid, NUNCA por si los
 * nombres ya han llegado: con el arranque en frío la pestaña se construye cuando llegan.
 */
object BarraTemporal {
    /** "No hay pestaña temporal". No vale -1: es la clave de la pestaña del Popurrí. */
    const val NINGUNO = Int.MIN_VALUE

    /** El fid que lleva pestaña temporal ahora mismo, o null. */
    fun temporal(listSource: String, fid: Int, fueraDeBarra: Set<Int>, visibles: Set<Int>): Int? =
        if ((listSource == "home" || listSource == "top") && fid in fueraDeBarra && fid !in visibles) fid else null

    /** ¿Salta `pintarPestanas` al primer subforo visible? [hayTemporal]: se le va a construir una. */
    fun saltar(listSource: String, hayVisibles: Boolean, fid: Int, visibles: Set<Int>, hayTemporal: Boolean): Boolean =
        listSource == "home" && hayVisibles && !hayTemporal && fid !in visibles

    /** El conjunto tras abrir [fid] por un enlace: si no es una pestaña de la barra, se recuerda. */
    fun alAbrirPorEnlace(fid: Int, clavesDePestanas: List<Int>, fueraDeBarra: Set<Int>): Set<Int> =
        if (fid in clavesDePestanas) fueraDeBarra else fueraDeBarra + fid

    /** Organizar cambia qué se ve: lo que se abrió fuera de la barra se olvida (si escondes lo que lees, se salta). */
    @Suppress("UNUSED_PARAMETER")
    fun alOrganizar(fueraDeBarra: Set<Int>): Set<Int> = emptySet()

    sealed class Eleccion {
        object Nada : Eleccion()
        /** Pestaña de la barra. [marcar]: hay que seleccionarla (sin disparar el listener). */
        data class EnBarra(val marcar: Boolean) : Eleccion()
        /** Subforo fuera de la barra: pestaña temporal. */
        object Escondido : Eleccion()
    }

    fun elegir(clave: Int, actual: Int, clavesDePestanas: List<Int>, seleccionada: Int?): Eleccion = when {
        clave == actual -> Eleccion.Nada
        clave in clavesDePestanas -> Eleccion.EnBarra(marcar = clave != seleccionada)
        else -> Eleccion.Escondido
    }

    /** Las pestañas que el selector llama "en tu barra": todas menos la temporal. */
    fun enBarra(pestanas: List<Sitio>, temporalFid: Int): List<Sitio> = pestanas.filter { it.clave != temporalFid }
}
