package com.fcplus.forocoches

enum class FuenteMusica { YOUTUBE, SPOTIFY }

/** Una canción identificada por su fuente y su id; [clave] es lo que se guarda y se manda al servidor. */
data class RefCancion(val fuente: FuenteMusica, val id: String) {
    val clave: String get() = prefijo(fuente) + ":" + id

    fun url(): String = when (fuente) {
        FuenteMusica.YOUTUBE -> "https://www.youtube.com/watch?v=$id"
        FuenteMusica.SPOTIFY -> "https://open.spotify.com/track/$id"
    }

    companion object {
        private fun prefijo(f: FuenteMusica) = f.name.lowercase()

        fun deClave(clave: String): RefCancion? {
            val i = clave.indexOf(':')
            if (i <= 0) return null
            val fuente = FuenteMusica.values().firstOrNull { prefijo(it) == clave.substring(0, i) } ?: return null
            val id = clave.substring(i + 1)
            // Misma validación que al reconocer un enlace: una clave no cuela ids que un enlace no daría.
            return EnlaceMusica.de(RefCancion(fuente, id).url())?.takeIf { it.fuente == fuente && it.id == id }
        }
    }
}

/**
 * Reconoce canciones de YouTube y Spotify en enlaces y textos. Los casos están compartidos con el
 * servidor en `contrato/musica-enlaces.json`: lo que cambie aquí se cambia allí.
 */
object EnlaceMusica {
    private val URL_O_URI = Regex("""https?://[^\s\[\]<>"]+|spotify:track:[A-Za-z0-9]+""", RegexOption.IGNORE_CASE)

    // Un id de pista de Spotify son exactamente 22 caracteres base62; el (?![A-Za-z0-9]) evita
    // aceptar el prefijo de uno más largo. Se admite el segmento de idioma (/intl-es/).
    private val SPOTIFY = Regex(
        """(?:open[.]spotify[.]com/(?:intl-[A-Za-z-]+/)?track/|^spotify:track:)([A-Za-z0-9]{22})(?![A-Za-z0-9])""",
        RegexOption.IGNORE_CASE
    )
    private val CORTO = Regex("""https?://spotify[.]link/[A-Za-z0-9]+""", RegexOption.IGNORE_CASE)

    fun de(url: String): RefCancion? {
        val u = url.trim()
        YoutubeId.de(u)?.let { return RefCancion(FuenteMusica.YOUTUBE, it) }
        SPOTIFY.find(u)?.let { return RefCancion(FuenteMusica.SPOTIFY, it.groupValues[1]) }
        return null
    }

    /** Las canciones del texto en orden de aparición, sin repetir. */
    fun todas(texto: String): List<RefCancion> =
        URL_O_URI.findAll(texto).mapNotNull { de(it.value) }.distinct().toList()

    /** Enlaces `spotify.link/…` tal cual: resolverlos exige red y no es cosa de esta clase. */
    fun cortosSpotify(texto: String): List<String> =
        CORTO.findAll(texto).map { it.value }.distinct().toList()
}
