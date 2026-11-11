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
    val publicadaId: String? = null,
    /**
     * uid de FC de la cuenta que la publicó. La marca es de ESTE móvil pero la playlist pública es de una
     * cuenta: con varias cuentas, borrarla con otra daría 404 y se tomaría por "ya borrada". null = marca
     * anterior a este campo (dueña desconocida): se enseña a todas, pero solo se actúa si el servidor confirma.
     */
    val publicadaPor: String? = null
) {
    /** ¿Se enseña como publicada (y se puede borrar) estando activa [uid]? Las marcas sin dueña, sí. */
    fun deLaCuenta(uid: String) = publicadaId != null && (publicadaPor == null || publicadaPor == uid)

    /** Publicada, pero por otra cuenta de este móvil: con [uid] activa no se toca. */
    fun deOtraCuenta(uid: String) = publicadaId != null && publicadaPor != null && publicadaPor != uid
}

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

    /** "Sin canciones", "1 canción", "N canciones": un "0 canciones" se lee como un fallo. */
    fun cuantas(n: Int): String = when (n) {
        0 -> "Sin canciones"
        1 -> "1 canción"
        else -> "$n canciones"
    }

    /** Duración de una canción: "3:25", y con horas "1:02:03". */
    fun duracion(seg: Int): String {
        val s = seg.coerceAtLeast(0)
        val h = s / 3600
        val m = (s % 3600) / 60
        val r = s % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, r) else "%d:%02d".format(m, r)
    }

    /**
     * La línea de la cabecera: "12 canciones · 48 min". La duración total solo si TODAS la
     * tienen (spec): con una sin saber, el total sería mentira. Se redondea al minuto.
     */
    fun resumen(canciones: List<Cancion>): String {
        val n = cuantas(canciones.size)
        if (canciones.isEmpty() || canciones.any { it.duracionSeg == null }) return n
        val minutos = ((canciones.sumOf { it.duracionSeg ?: 0 } + 30) / 60).coerceAtLeast(1)
        val total = if (minutos >= 60) "${minutos / 60} h ${"%02d".format(minutos % 60)} min" else "$minutos min"
        return "$n · $total"
    }
}
