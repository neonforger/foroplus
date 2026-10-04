package com.fcplus.forocoches

/**
 * El orden **por defecto** de las pestañas de subforo.
 *
 * Las pestañas se pintan con el orden literal del índice de ForoCoches, y ese orden no es el
 * que le sirve a alguien que entra a leer. Sondeado por CDP el 2026-08-28, el índice real
 * empieza así:
 *
 * ```
 * 12  Ayuda
 *  2  General
 * 17  Electrónica / Informática
 * ...
 * ```
 *
 * O sea que la primera pestaña, la que se lleva el sitio bueno, es la de **dudas sobre el
 * propio foro**, y la puerta de entrada de verdad queda la segunda. Lo reportó el dueño el
 * 2026-08-28 ("no aporta nada ahí en primera línea").
 *
 * Aquí solo se mueven esas dos: **General al principio y Ayuda al final**, y todo lo demás se
 * queda en el orden que le da FC. Es un DEFECTO, no una imposición — cuando exista la pantalla
 * de organizar barras, la elección del usuario manda sobre esto. Y que sea un buen defecto
 * importa: la mayoría no abre Opciones nunca.
 *
 * **Se mueve por fid, no por nombre.** Un `if (nombre == "Ayuda")` deja de funcionar en
 * silencio el día que FC lo renombre; los ids de vBulletin no cambian. El precio es que si
 * algún día cambiaran, esto se vuelve inofensivo y el orden pasa a ser el del foro, que es de
 * donde veníamos.
 */
object OrdenSubforos {

    /** General: la puerta de entrada del foro. */
    const val GENERAL = 2

    /** Ayuda: dudas sobre el foro. Útil, pero no lo primero que se abre cada día. */
    const val AYUDA = 12

    /**
     * Reordena la lista del índice dejando [GENERAL] delante y [AYUDA] detrás.
     *
     * No quita ni duplica nada: un subforo que se cayera aquí sería un subforo al que ya no se
     * puede llegar desde la app. Es idempotente, porque la lista se guarda ya ordenada y se
     * vuelve a pasar por aquí en cada repintado.
     */
    fun porDefecto(lista: List<ForumTab>): List<ForumTab> {
        val general = lista.filter { it.fid == GENERAL }
        val ayuda = lista.filter { it.fid == AYUDA }
        val resto = lista.filter { it.fid != GENERAL && it.fid != AYUDA }
        return general + resto + ayuda
    }
}
