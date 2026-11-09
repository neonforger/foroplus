package com.fcplus.forocoches

/**
 * Lo que la hoja "Añadir" ha entendido de un texto pegado. [nombres] va por [RefCancion.clave] y
 * trae (canción, artista) solo de las líneas numeradas de una playlist compartida. [descartadas]
 * son las que no se miran por pasar de [Musica.MAX_CANCIONES] (ver [AnadirMusica.plan]).
 */
data class PlanAnadir(
    val refs: List<RefCancion>,
    val cortos: List<String>,
    val nombre: String?,
    val nombres: Map<String, Pair<String?, String?>>,
    val descartadas: Int = 0
)

/** Las decisiones de la hoja "Añadir", puras: la hoja ([HojaAnadirMusica]) solo pinta y llama a la red. */
object AnadirMusica {

    /**
     * Como mucho [Musica.MAX_CANCIONES] entre enlaces largos y cortos, en ese orden: lo que llega
     * por "Guardar en Música" puede traer 60.000 caracteres (~2.000 enlaces) de cualquier app, y
     * la hoja pide a la red los datos de cada canción y resuelve cada `spotify.link`. Más de las
     * que caben en una playlist no se guardarían igualmente ([AlmacenMusica.importar] se queda con
     * las primeras), así que ni se piden.
     */
    fun plan(texto: String): PlanAnadir {
        val leida = PlaylistCompartida.leer(texto)
        val todas = leida.lineas.map { it.ref }
        val todosCortos = EnlaceMusica.cortosSpotify(texto)
        val refs = todas.take(Musica.MAX_CANCIONES)
        val cortos = todosCortos.take(Musica.MAX_CANCIONES - refs.size)
        val elegidas = refs.map { it.clave }.toHashSet()
        val nombres = LinkedHashMap<String, Pair<String?, String?>>()
        for (l in leida.lineas) {
            if (l.ref.clave !in elegidas) continue
            if (l.cancion != null || l.artista != null) nombres[l.ref.clave] = l.cancion to l.artista
        }
        return PlanAnadir(
            refs,
            cortos,
            leida.nombre?.let { nombreParaGuardar(it) },
            nombres,
            descartadas = (todas.size - refs.size) + (todosCortos.size - cortos.size)
        )
    }

    /** La línea de la hoja cuando el texto traía más de las que caben; null si no se dejó nada. */
    fun avisoDescartadas(n: Int): String? = when {
        n <= 0 -> null
        n == 1 -> "Una playlist admite hasta ${Musica.MAX_CANCIONES} canciones: se ha dejado fuera la última."
        else -> "Una playlist admite hasta ${Musica.MAX_CANCIONES} canciones: se han dejado fuera las $n últimas."
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
