package com.fcplus.forocoches

import kotlin.math.exp

/**
 * "Tirar para actualizar" desde abajo, en la última página de un hilo: lo que hace quien sigue un
 * hilo en directo para ver si han escrito más (lo pidieron en el grupo; dueño, 2026-11-17). Es el
 * gesto de arriba de cualquier app (Instagram, el SwipeRefreshLayout del propio hilo), pero al
 * final: la lista sube con resistencia, la rueda nace en el hueco y se va dibujando con el dedo, y
 * soltando pasado el punto se recarga.
 *
 * Aquí solo los números y las decisiones; el gesto y la rueda viven en [RefrescoDesdeAbajo].
 * Las distancias van en dp: quien llama las pasa a píxeles.
 */
object TirarDesdeAbajo {

    /** Lo más que sube la lista, por mucho que tire el dedo. */
    const val MAX_DP = 110f

    /** Hueco (= lo que sube la lista) a partir del cual soltar recarga. Es también donde se queda
     *  la rueda girando mientras carga. */
    const val UMBRAL_DP = 56f

    /** La lista tiene que llevar este rato quieta en el fondo antes de apoyar el dedo. */
    const val QUIETA_MS = 500L

    /**
     * Un gesto más corto que esto (de apoyar a soltar) es un golpe de dedo para seguir bajando,
     * no un tirón: rebota, pero no recarga. Medido con adb en el Samsung: un golpe dura ~80 ms.
     */
    const val GOLPE_MAX_MS = 180L

    /**
     * Cuánto sube la lista para un recorrido del dedo: a la mitad al principio (como el de
     * arriba) y cada vez más duro, sin pasar nunca de [max]. Una curva y no un tope seco: el
     * tope seco se nota como un golpe contra una pared.
     */
    fun desplazamiento(tiron: Float, max: Float): Float {
        if (tiron <= 0f || max <= 0f) return 0f
        return max * (1f - exp(-(tiron * 0.5f) / max))
    }

    /** Lo dibujado de la rueda: 0 sin tirar, 1 justo en el umbral (y más allá, 1). */
    fun progreso(desplazado: Float, umbral: Float): Float =
        if (umbral <= 0f) 0f else (desplazado / umbral).coerceIn(0f, 1f)

    /** Al soltar: ¿se recarga? Pasado el umbral y sin ser un golpe. */
    fun recarga(desplazado: Float, umbral: Float, duracionMs: Long): Boolean =
        umbral > 0f && desplazado >= umbral && duracionMs >= GOLPE_MAX_MS

    /**
     * ¿Vale empezar? Con la lista quieta en el fondo un momento: quien baja a golpes de dedo
     * llega al fondo con la lista en marcha. Entre una recarga y otra NO hay espera: hubo un
     * respiro de 3 s y el dueño lo quitó (2026-11-17: "parece un bug"; en la web se recarga
     * todo lo que se quiera).
     */
    fun puedeArmar(ahora: Long, ultimoMovimiento: Long): Boolean =
        ahora - ultimoMovimiento >= QUIETA_MS

    /** Solo en la última página: en las demás lo que hay debajo es la página siguiente. */
    fun disponible(pagina: Int, paginas: Int, hayMensajes: Boolean): Boolean =
        hayMensajes && pagina >= paginas

    /**
     * Lo que se dice al acabar de recargar, si hace falta. Los mensajes nuevos de esta página se
     * ven llegar donde estaba la rueda, y "no hay nada nuevo" se entiende solo (dueño,
     * 2026-11-17); lo que NO se ve es que el hilo haya pasado a otra página.
     */
    fun aviso(paginasAntes: Int, paginasDespues: Int): String? = when {
        paginasDespues - paginasAntes == 1 -> "Hay una página nueva: la $paginasDespues"
        paginasDespues > paginasAntes -> "Hay ${paginasDespues - paginasAntes} páginas nuevas"
        else -> null
    }
}
