package com.fcplus.forocoches

/** Una canción de la semana en curso tal como se pinta: su corazón, y si ahora mismo se puede tocar. */
data class FilaSemana(val cancion: CancionPub, val votada: Boolean, val corazonActivo: Boolean)

/** Lo que trae una carga de la portada de Música (C3). [yo] es null sin token válido o si `/v1/yo` falló. */
data class DatosSemana(val semana: Semana, val yo: Yo?, val verificada: Boolean, val baneada: Boolean)

/**
 * Las decisiones de la canción de la semana en la portada, sin Android: qué corazones se pueden
 * tocar, cómo pasa una canción del servidor al reproductor y qué texto sale con cada error.
 * Lo que pinta y habla con la red es [PanelSemana].
 */
object VistaSemana {
    enum class Accion { VOTAR, PROPONER }

    /**
     * Una fila por canción propuesta, en el orden del servidor (votos, y a igualdad la que llegó
     * antes: el recuento no llega a la app). El corazón se puede tocar con permiso de voto y votos
     * por gastar; sin votos solo en las YA votadas, para poder quitar el voto. Sin [yo] (sin
     * token, o `/v1/yo` sin contestar) ninguno: no se sabe cuántos votos quedan.
     */
    fun filas(semana: Semana, yo: Yo?): List<FilaSemana> = semana.actual.map { c ->
        val votada = c.id in semana.votadas
        FilaSemana(c, votada, yo != null && yo.puedeVotar && (yo.votosRestantes > 0 || votada))
    }

    /** `youtube` primero, si no `spotify` (como en las playlists propias); null si ninguno vale. */
    fun cancionDe(c: CancionPub): Cancion? {
        val ref = listOfNotNull(c.youtube, c.spotify).firstNotNullOfOrNull { EnlaceMusica.de(it) } ?: return null
        return Cancion(ref, c.titulo, c.artista, null, null, c.portada, 0L)
    }

    /** Lo que se dice al tocar un corazón apagado. */
    fun motivoSinCorazon(yo: Yo?): String = when {
        yo == null -> "No se ha podido comprobar tu cuenta. Prueba en un rato"
        !yo.puedeVotar -> TEXTO_ANTIGUEDAD
        else -> "Ya has usado tus $VOTOS votos de esta semana. Quita uno para votar otra canción"
    }

    fun textoError(e: ApiMusica.R.Error, accion: Accion): String = when (e.codigo) {
        "red" -> "No se ha podido conectar con Música. Prueba en un rato"
        "antiguedad" -> TEXTO_ANTIGUEDAD
        "tope" -> if (accion == Accion.VOTAR) "Ya has usado tus $VOTOS votos de esta semana"
        else "Ya has propuesto tus $PROPUESTAS propuestas de esta semana"
        "enlace", "no_es_cancion" -> "Eso no parece una canción de YouTube o de Spotify"
        "no_encontrada" -> "No se ha podido encontrar esa canción. Prueba con otro enlace"
        "limite" -> "Has propuesto muchas canciones nuevas en poco tiempo. Prueba dentro de un rato"
        "no_esta" -> "Esa canción ya no está en la votación de esta semana"
        "oculta" -> "Esa canción la ha ocultado la moderación y no se puede proponer"
        else -> "No se ha podido hacer ahora mismo. Prueba en un rato"
    }

    /** "Te quedan 7 votos y 2 propuestas esta semana". */
    fun restantes(yo: Yo): String {
        if (yo.votosRestantes <= 0 && yo.propuestasRestantes <= 0) return "Has gastado tus votos y tus propuestas de esta semana"
        val verbo = if (yo.votosRestantes <= 1 && yo.propuestasRestantes <= 1) "Te queda" else "Te quedan"
        return "$verbo ${plural(yo.votosRestantes, "voto", "votos")} y " +
            "${plural(yo.propuestasRestantes, "propuesta", "propuestas")} esta semana"
    }

    /** Qué hacer tras un error de votar o proponer: el texto, y si hay que tirar el token, recordar el baneo y volver a cargar. */
    data class TrasFallo(val texto: String, val borrarToken: Boolean, val baneada: Boolean, val recargar: Boolean)

    fun trasFallo(e: ApiMusica.R.Error, accion: Accion): TrasFallo {
        val baneada = e.codigo == "baneada"
        val texto = when {
            baneada -> "Esta cuenta de ForoCoches está bloqueada en Música."
            e.tokenInservible -> "Tu verificación ya no vale. Vuelve a verificarte para votar y proponer"
            else -> textoError(e, accion)
        }
        return TrasFallo(texto, e.tokenInservible, baneada, e.tokenInservible || e.codigo == "no_esta")
    }

    private fun plural(n: Int, uno: String, varios: String) = if (n == 1) "1 $uno" else "$n $varios"

    const val VOTOS = 10
    const val PROPUESTAS = 3
    const val TEXTO_ANTIGUEDAD = "Para votar y proponer, tu cuenta de ForoCoches necesita al menos 90 días"
}

/**
 * Carga lo que pinta la portada: la semana y, con token, `/v1/yo`. Bloqueante (hilo de fondo).
 *
 * Un token malo no rompe la portada: `GET /v1/semana` con un token muerto da 401/403 en vez de
 * caer a la vista pública (para que la app se entere), así que aquí se borra el token y se repite
 * SIN él. Una cuenta baneada (403 `baneada`) se anota antes, para decirlo en vez de ofrecer
 * verificarse (misma regla que la hoja de C2).
 */
object CargaSemana {
    fun cargar(
        api: ApiMusica, tieneToken: () -> Boolean, borrarToken: () -> Unit,
        baneadaGuardada: () -> Boolean = { false }, guardarBaneada: () -> Unit = { }
    ): ApiMusica.R<DatosSemana> {
        var conToken = tieneToken()
        var baneada = !conToken && baneadaGuardada()
        var r = api.semana()
        if (r is ApiMusica.R.Error && conToken && r.tokenInservible) {
            baneada = r.codigo == "baneada"
            if (baneada) guardarBaneada()
            borrarToken()
            conToken = false
            r = api.semana()
        }
        val semana = when (r) {
            is ApiMusica.R.Ok -> r.v
            is ApiMusica.R.Error -> return r
        }
        var yo: Yo? = null
        if (conToken) {
            when (val y = api.yo()) {
                is ApiMusica.R.Ok -> yo = y.v
                is ApiMusica.R.Error -> if (y.tokenInservible) {
                    baneada = y.codigo == "baneada"
                    if (baneada) guardarBaneada()
                    borrarToken()
                    conToken = false
                }
            }
        }
        return ApiMusica.R.Ok(DatosSemana(semana, yo, conToken, baneada))
    }
}
