package com.fcplus.forocoches

/**
 * Medidas de las imágenes que van DENTRO del texto de un mensaje (smilies, iconos y fotos).
 *
 * Existe porque la app las pintaba a **píxel crudo**: un smiley de FC mide 20x15 px reales
 * (medidos por CDP sobre el foro: de 15x15 a 75x57 en los 347 que tiene), así que en una
 * pantalla de densidad 3 salía a 20 px físicos = 6,7dp de ancho al lado de un texto de 15sp.
 * De ahí el "se ven muy pequeños" que reportaron los testers.
 *
 * La regla correcta es la del navegador: **un píxel declarado en HTML es un CSS px, y un CSS px
 * es un dp**. Por eso se escala por la densidad de la pantalla y no por un "si es pequeño,
 * agrándalo": un salto por tamaño dejaría una imagen de 79 px enorme y la de 81 px diminuta.
 *
 * El tope de ancho se mantiene después de escalar, que es lo que hace también el foro (su CSS
 * limita las fotos al 100 % del contenedor).
 */
object ImagenEnTexto {

    /**
     * Por debajo de esto es un icono/smiley, no una foto: se centra en la línea de texto en vez
     * de colgar de la base. El mayor smiley de FC mide 75x57 px, así que 80 los cubre todos.
     */
    const val ICONO_MAX_PX = 80

    /**
     * Ancho máximo al que se dibuja una foto dentro de un mensaje (el resto es margen).
     *
     * Vive aquí porque lo necesitan DOS sitios y tenían que coincidir: el que pinta y el que
     * **decodifica** (`PostImages`). Cuando no coincidían, se guardaban en memoria bitmaps de
     * 1263 px para dibujarlos a 842 y la caché no daba abasto.
     */
    fun anchoMaximo(anchoPantallaPx: Int): Int = (anchoPantallaPx * 0.78f).toInt()

    fun esIcono(anchoPx: Int, altoPx: Int): Boolean =
        anchoPx in 1..ICONO_MAX_PX && altoPx in 1..ICONO_MAX_PX

    /**
     * Tamaño en píxeles con el que dibujar un bitmap de [anchoPx] x [altoPx].
     * Escala por [densidad] y, si se pasa de [anchoMaxPx], encoge manteniendo la proporción.
     */
    fun medida(anchoPx: Int, altoPx: Int, densidad: Float, anchoMaxPx: Int): Pair<Int, Int> {
        if (anchoPx <= 0 || altoPx <= 0) return 0 to 0
        var w = anchoPx * densidad
        var h = altoPx * densidad
        if (anchoMaxPx > 0 && w > anchoMaxPx) {
            h *= anchoMaxPx / w
            w = anchoMaxPx.toFloat()
        }
        // Nunca 0: un redondeo a cero haría desaparecer la imagen sin dejar rastro.
        return maxOf(1, w.toInt()) to maxOf(1, h.toInt())
    }

    /**
     * Centro vertical de la línea de texto respecto a la base (negativo: va por encima).
     * `ascent` viene negativo de las métricas de la fuente y `descent` positivo.
     */
    fun centroDeLinea(ascent: Int, descent: Int): Int = (ascent + descent) / 2

    /**
     * Borde superior de un icono de [alto] para que quede centrado en la línea, respecto a la
     * base del texto. Es lo que arregla el "descuadrado": `Html.fromHtml` alinea las imágenes
     * con la BASE, así que un smiley cuelga por debajo del renglón.
     */
    fun topDelIcono(ascent: Int, descent: Int, alto: Int): Int =
        centroDeLinea(ascent, descent) - alto / 2

    /** Borde inferior del mismo icono, para saber cuánto tiene que crecer la línea. */
    fun baseDelIcono(ascent: Int, descent: Int, alto: Int): Int =
        topDelIcono(ascent, descent, alto) + alto
}
