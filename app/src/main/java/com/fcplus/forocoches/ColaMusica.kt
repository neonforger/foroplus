package com.fcplus.forocoches

/**
 * Qué toca después en una lista de canciones. Una canción de Spotify solo suena 30 s dentro de la
 * app, así que la cola se para en ella ([ParadaSpotify]) en vez de saltarla o de seguir de largo.
 */
sealed class Paso {
    /** Hay que reproducir la canción [indice] (YouTube, sin parar). */
    data class Reproducir(val indice: Int) : Paso()

    /** La canción [indice] es de Spotify: la cola se detiene ahí y se ofrece el enlace. */
    data class ParadaSpotify(val indice: Int) : Paso()

    /** No queda nada en esa dirección. */
    object Fin : Paso()
}

/**
 * Decide la siguiente y la anterior canción. Las posiciones en [saltar] (las que no se dejan
 * incrustar) se pasan por alto. La lógica va pura aquí para poder probarla sin el reproductor.
 */
object ColaMusica {

    /** Primera posición después de [actual] que no está en [saltar]; desde -1 empieza en 0. */
    fun siguiente(fuentes: List<FuenteMusica>, actual: Int, saltar: Set<Int>): Paso {
        val desde = maxOf(actual + 1, 0)
        for (i in desde until fuentes.size) {
            if (i in saltar) continue
            return paso(fuentes, i)
        }
        return Paso.Fin
    }

    /** Primera posición antes de [actual] que no está en [saltar]; si [actual] se sale por arriba, cuenta desde el final. */
    fun anterior(fuentes: List<FuenteMusica>, actual: Int, saltar: Set<Int>): Paso {
        val desde = minOf(actual - 1, fuentes.size - 1)
        for (i in desde downTo 0) {
            if (i in saltar) continue
            return paso(fuentes, i)
        }
        return Paso.Fin
    }

    private fun paso(fuentes: List<FuenteMusica>, i: Int): Paso =
        if (fuentes[i] == FuenteMusica.SPOTIFY) Paso.ParadaSpotify(i) else Paso.Reproducir(i)

    /**
     * La lista ha cambiado debajo del reproductor (reordenar, quitar). [claveActual] es la
     * canción cargada (o la del "Seguir con…" si está parado) y [indiceAntes] su posición en la
     * lista vieja; [claves], [fuentes] y [saltar] son ya de la lista nueva.
     *
     * - Si sigue en la lista, se sigue con ella donde haya quedado.
     * - Si la que se ha quitado es la que SUENA, entra la que ocupa su sitio (lo que se ha
     *   quitado no tiene por qué seguir oyéndose).
     * - Parado, el "Seguir con…" apunta a la que ocupe su sitio, sin salirse de la lista.
     */
    fun reubicar(
        claveActual: String?,
        indiceAntes: Int,
        claves: List<String>,
        fuentes: List<FuenteMusica>,
        saltar: Set<Int>,
        sonando: Boolean
    ): Reubicar {
        val ahora = if (claveActual == null) -1 else claves.indexOf(claveActual)
        return when {
            ahora >= 0 -> Reubicar.Sigue(ahora)
            sonando -> Reubicar.Sustituir(siguiente(fuentes, indiceAntes - 1, saltar))
            else -> Reubicar.Parado(minOf(indiceAntes, claves.size - 1))
        }
    }

    /**
     * Desde dónde sigue el reproductor en reposo: donde iba, o desde el principio si no iba por
     * ninguna o ya se acabó la playlist. Null si no hay nada que escuchar.
     */
    fun seguirDesde(total: Int, actual: Int, acabada: Boolean): Int? = when {
        total <= 0 -> null
        acabada || actual < 0 -> 0
        else -> minOf(actual, total - 1)
    }
}

/** Qué hace el reproductor cuando la lista cambia debajo de él (ver [ColaMusica.reubicar]). */
sealed class Reubicar {
    /** La canción cargada sigue en la lista, ahora en [indice]. */
    data class Sigue(val indice: Int) : Reubicar()

    /** Se ha quitado la que sonaba: toca [paso] (la que ocupa su sitio, o el final). */
    data class Sustituir(val paso: Paso) : Reubicar()

    /** Parado: el "Seguir con…" apunta a [indice] (-1 si no queda ninguna). */
    data class Parado(val indice: Int) : Reubicar()
}
