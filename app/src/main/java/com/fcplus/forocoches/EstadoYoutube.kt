package com.fcplus.forocoches

/**
 * Lo que el reproductor de YouTube nos cuenta por postMessage, y las comprobaciones previas a
 * meter un id en el HTML/JS del [EmbedView]. Puro para poder probarlo sin WebView.
 */
object EstadoYoutube {
    /** `playerState` 0 = el vídeo ha llegado al final (medido en EmbedView, 2026-11-07). */
    const val TERMINADO = 0

    /** Códigos de error documentados de la API IFrame de YouTube. */
    private val ERRORES = setOf(2, 5, 100, 101, 150)

    fun esError(codigo: Int): Boolean = codigo in ERRORES

    /** 101 y 150: el dueño del vídeo no deja incrustarlo; hay que saltar al siguiente. */
    fun noIncrustable(codigo: Int): Boolean = codigo == 101 || codigo == 150

    /**
     * Un vídeo que no se deja ver aquí, cargado con `loadVideoById` en el reproductor de Música,
     * NO dispara `onError`: YouTube manda un `infoDelivery` con `playerState` -1 y
     * `videoData.errorCode` (medido el 2026-11-08 con uno de incrustar prohibido: `"auth"`, y la
     * cola se quedaba parada en él). Se traduce a un código de la API IFrame para tratarlo igual:
     * `"auth"` es el caso de 101/150 (no deja incrustarlo, o pide sesión o edad); cualquier otro,
     * un fallo del reproductor (5). Los dos son errores de verdad: se salta la canción.
     */
    fun codigoDeError(errorCode: String): Int = if (errorCode == "auth") 150 else 5

    private val ID_YOUTUBE = Regex("^[A-Za-z0-9_-]{11}$")
    private val ID_SPOTIFY = Regex("^[A-Za-z0-9]{22}$")

    /** Se valida antes de inyectar el id en JS o en una URL: nada de cadenas libres. */
    fun idValido(id: String): Boolean = ID_YOUTUBE.matches(id)

    fun idSpotifyValido(id: String): Boolean = ID_SPOTIFY.matches(id)
}
