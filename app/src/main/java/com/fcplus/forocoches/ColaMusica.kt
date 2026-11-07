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
}
