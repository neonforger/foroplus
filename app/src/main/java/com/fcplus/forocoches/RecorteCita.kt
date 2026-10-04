package com.fcplus.forocoches

/**
 * Dónde cortar una cita demasiado larga.
 *
 * En FC se cita mucho y a veces se cita un mensaje enorme, y entonces la respuesta de una línea
 * queda enterrada bajo media pantalla de texto ajeno. Medido sobre 66 citas reales de 12 hilos
 * del General: la mediana son **102 caracteres**, el percentil 90 son 645 y la más larga 1.451.
 * O sea, el 85 % de las citas no molesta a nadie: sobra con plegar la cola larga.
 *
 * Se pliega, **no se hace scroll**: la cita no es una vista propia (el mensaje entero —cita,
 * texto, fotos y enlaces— se pinta como un solo bloque de texto), así que una caja con scroll
 * obligaría a partir cada mensaje en varias vistas. Y una zona con scroll dentro de una lista
 * que también scrollea es una trampa: si fallas el dedo, se te mueve el hilo entero.
 */
object RecorteCita {

    /**
     * Qué tramos del mensaje son citas, a partir de los rangos de sus [CitaSpan].
     *
     * **Cada cita es su propio tramo.** Parece obvio y no lo era: hasta el 2026-09-16 esto
     * fusionaba los rangos que empezaran "a un carácter" del final del anterior, creyendo que
     * una cita de varios párrafos podía traer un span por párrafo. Medido sobre el HTML real:
     * `Html.fromHtml` da **un QuoteSpan por `<blockquote>`**, párrafos incluidos — la cita de
     * 707 caracteres y varios párrafos del hilo 10806006 sale como un único span. Lo que sí
     * hace es dejar **exactamente un salto de línea entre dos citas seguidas**, así que la
     * regla fusionaba CITAS DISTINTAS.
     *
     * El daño no era cosmético. Con cinco citas fusionadas en un tramo, el plegado cortaba
     * por el medio y borraba cuatro citas enteras de una sentada; sus spans quedaban con
     * longitud CERO, y **un span vacío sigue siendo un `LeadingMarginSpan`: sigue reservando
     * su margen**. En el Samsung eran 5 × 39 px = 195 px empujando el texto a la derecha y 83
     * px saliéndose de la pantalla. Es el "algunos mensajes se cortan por el lado derecho".
     *
     * Los rangos vacíos se descartan: una cita sin texto no se pliega ni se adorna.
     */
    fun tramos(marcas: List<Pair<Int, Int>>): List<Pair<Int, Int>> =
        marcas.filter { it.first < it.second }.sortedBy { it.first }

    /**
     * Tope de caracteres antes de plegar. Con el ancho del mensaje (~44 caracteres por línea a
     * 15sp) son unas 14 líneas: por debajo de la media pantalla que se puso como techo, y lo
     * bastante bajo para que las citas del percentil 90 en adelante dejen de estorbar.
     */
    const val LIMITE = 600

    /** Texto que sustituye a lo recortado. El "…" avisa de que falta algo aunque no se toque. */
    const val MAS = "…  Ver cita completa"

    /** Dónde empieza, dentro de [MAS], la parte que se puede tocar. */
    val INICIO_ENLACE = MAS.indexOf("Ver")

    /** Caracteres que caben en una línea del mensaje (15sp en el ancho útil). De aquí sale
     *  también el LIMITE: 600 / 44 ≈ 14 líneas. */
    const val CHARS_POR_LINEA = 44

    /**
     * Lo que "ocupa" una imagen, medido en caracteres de texto.
     *
     * Hace falta porque en el texto **una foto es UN solo carácter** (el U+FFFC que deja
     * `Html.fromHtml`, ver gotcha 21): contada así, una cita con diez fotos sumaba diez y no se
     * plegaba nunca — justo el "una cita con fotos repite el mensaje entero" que reportaron.
     * Se mide por la ALTURA con la que se va a dibujar, no por un coste fijo, porque por el
     * mismo sitio pasan los smileys: con un coste plano, cuatro caritas plegaban la cita.
     */
    fun costeDeImagen(alturaPx: Int, alturaLineaPx: Int): Int {
        if (alturaPx <= 0 || alturaLineaPx <= 0) return 1
        val lineas = alturaPx.toFloat() / alturaLineaPx
        return maxOf(1, (lineas * CHARS_POR_LINEA).toInt())
    }

    /**
     * Índice donde cortar el tramo `[ini, fin)` de [texto] para que no pase de [limite].
     * Devuelve `fin` si no hace falta cortar.
     *
     * [costeDe] dice lo que ocupa el carácter de cada índice; por defecto uno, que es lo que
     * vale para el texto. Las imágenes pesan lo suyo (ver [costeDeImagen]).
     *
     * Se retrocede hasta el último espacio para no partir una palabra por la mitad, pero solo
     * un poco: en un texto sin espacios (un enlace larguísimo, japonés…) retroceder hasta el
     * principio dejaría la cita en nada, así que ahí manda el corte seco.
     */
    fun corte(
        texto: CharSequence,
        ini: Int,
        fin: Int,
        limite: Int = LIMITE,
        costeDe: (Int) -> Int = { 1 }
    ): Int {
        if (ini >= fin) return fin
        var acumulado = 0
        var sobra = -1
        for (i in ini until fin) {
            acumulado += costeDe(i)
            if (acumulado > limite) { sobra = i; break }
        }
        if (sobra < 0) return fin
        // Nunca se corta en el propio arranque: una cita que se queda en nada y un "Ver cita
        // completa" es peor que enseñar algo.
        val seco = maxOf(ini + 1, sobra)
        val minimo = ini + (seco - ini) / 2
        var i = seco
        while (i > minimo && !texto[i - 1].isWhitespace()) i--
        return if (i > minimo) i else seco
    }

    /**
     * Retrocede sobre los saltos de línea finales del tramo. El fondo y la raya de la cita son
     * un estilo de PÁRRAFO: si el recorte se come el salto que lo cierra, el adorno se derrama
     * sobre el texto de quien responde.
     */
    fun sinSaltosFinales(texto: CharSequence, ini: Int, fin: Int): Int {
        var f = fin
        while (f > ini && texto[f - 1] == '\n') f--
        return f
    }
}
