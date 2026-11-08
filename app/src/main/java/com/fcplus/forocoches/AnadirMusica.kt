package com.fcplus.forocoches

/**
 * Lo que la hoja "Añadir" ha entendido de un texto pegado. [nombres] va por [RefCancion.clave] y
 * trae (canción, artista) solo de las líneas numeradas de una playlist compartida.
 */
data class PlanAnadir(
    val refs: List<RefCancion>,
    val cortos: List<String>,
    val nombre: String?,
    val nombres: Map<String, Pair<String?, String?>>
)

/** Las decisiones de la hoja "Añadir", puras: la hoja ([HojaAnadirMusica]) solo pinta y llama a la red. */
object AnadirMusica {

    fun plan(texto: String): PlanAnadir {
        val leida = PlaylistCompartida.leer(texto)
        val nombres = LinkedHashMap<String, Pair<String?, String?>>()
        for (l in leida.lineas) {
            if (l.cancion != null || l.artista != null) nombres[l.ref.clave] = l.cancion to l.artista
        }
        return PlanAnadir(
            leida.lineas.map { it.ref },
            EnlaceMusica.cortosSpotify(texto),
            leida.nombre?.let { nombreParaGuardar(it) },
            nombres
        )
    }

    /**
     * PlaylistCompartida no recorta el nombre del texto y [AlmacenMusica.importar] devuelve null
     * si pasa de [Musica.MAX_NOMBRE]: se recorta aquí. Null si queda vacío.
     */
    fun nombreParaGuardar(nombre: String): String? =
        nombre.trim().replace(Regex("""\s+"""), " ").take(Musica.MAX_NOMBRE).trim().ifEmpty { null }

    /**
     * La canción a guardar de una playlist pegada: lo que trae el texto manda sobre lo leído de la
     * red, y sin red sirve el texto solo. Si no hay título de ningún lado NO se descarta (un enlace
     * válido es una canción): entra con un título provisional y sin artista, ver [sinDatos].
     */
    fun combinar(ref: RefCancion, datos: Cancion?, cancionTexto: String?, artistaTexto: String?, ahora: Long): Cancion {
        val titulo = cancionTexto?.trim()?.ifEmpty { null } ?: datos?.titulo ?: tituloProvisional(ref)
        val artista = artistaTexto?.trim()?.ifEmpty { null } ?: datos?.artista
        return (datos ?: Cancion(ref, titulo, null, null, null, null, ahora)).copy(ref = ref, titulo = titulo, artista = artista)
    }

    /** True si [combinar] va a tener que inventar el título: ni la red ni el texto lo dieron. */
    fun sinDatos(datos: Cancion?, cancionTexto: String?, @Suppress("UNUSED_PARAMETER") artistaTexto: String?): Boolean =
        datos == null && cancionTexto.isNullOrBlank()

    fun tituloProvisional(ref: RefCancion): String = when (ref.fuente) {
        FuenteMusica.YOUTUBE -> "Vídeo de YouTube ${ref.id}"
        FuenteMusica.SPOTIFY -> "Canción de Spotify ${ref.id}"
    }

    /** Frase (con espacio delante) para añadir al mensaje final; vacía si todas traían datos. */
    fun avisoSinDatos(n: Int): String = when (n) {
        0 -> ""
        1 -> " 1 ha entrado sin título ni artista (no se pudo leer)."
        else -> " $n han entrado sin título ni artista (no se pudo leer)."
    }

    /** La canción tal como la dejó el usuario en los dos campos (un artista vacío es "sin artista"). */
    fun editar(ref: RefCancion, datos: Cancion?, titulo: String, artista: String, ahora: Long): Cancion? {
        val t = titulo.trim().ifEmpty { null } ?: return null
        val a = artista.trim().ifEmpty { null }
        return (datos ?: Cancion(ref, t, null, null, null, null, ahora)).copy(ref = ref, titulo = t, artista = a)
    }
}
