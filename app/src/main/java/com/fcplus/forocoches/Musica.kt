package com.fcplus.forocoches

/** Una canción guardada. La identidad es [ref] (su `clave`); lo demás es solo para pintarla. */
data class Cancion(
    val ref: RefCancion,
    val titulo: String,
    val artista: String?,
    val album: String?,
    val duracionSeg: Int?,
    val portada: String?,
    val anadida: Long
)

/** [claves] son `RefCancion.clave`, en orden de reproducción; los datos viven en [EstadoMusica.canciones]. */
data class Playlist(
    val id: String,
    val nombre: String,
    val claves: List<String>,
    val creada: Long,
    val modificada: Long,
    val publicadaId: String? = null
)

data class EstadoMusica(val canciones: Map<String, Cancion>, val playlists: List<Playlist>)

/** Reglas puras de la música: límites del spec y normalización de nombres. */
object Musica {
    const val MAX_NOMBRE = 60
    const val MAX_PLAYLISTS = 50
    const val MAX_CANCIONES = 200
    const val FAVORITAS = "Mis favoritas"

    private val ESPACIOS = Regex("\\s+")

    /** Recorta y colapsa espacios; null si queda vacío o pasa de [MAX_NOMBRE]. */
    fun nombreLimpio(nombre: String): String? {
        val n = nombre.trim().replace(ESPACIOS, " ")
        return if (n.isEmpty() || n.length > MAX_NOMBRE) null else n
    }

    /** "X", y si está ocupado "X (2)", "X (3)"…; el sufijo nunca saca el nombre del tope. */
    fun nombreLibre(deseado: String, existentes: List<String>): String {
        if (deseado !in existentes) return deseado
        var n = 2
        while (true) {
            val sufijo = " ($n)"
            val candidato = deseado.take(MAX_NOMBRE - sufijo.length) + sufijo
            if (candidato !in existentes) return candidato
            n++
        }
    }
}
