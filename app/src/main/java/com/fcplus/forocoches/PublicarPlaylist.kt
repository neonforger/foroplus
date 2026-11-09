package com.fcplus.forocoches

/**
 * Publicar una playlist tuya en "Playlists de los shurs", en tandas, sin Android.
 *
 * Por qué en tandas: el plan gratuito de Workers corta una petición a los 10 ms de CPU (error
 * 1102) y cada canción NUEVA para el servidor cuesta una tarjeta leída fuera. Una playlist de
 * 200 canciones no cabe en una petición. [TANDA] es el tamaño de cada una; el servidor la acepta
 * hasta su `MAX_ENLACES` (10).
 *
 * Reintentos: la petición que CREA no se repite nunca por su cuenta (el servidor no impide dos
 * playlists con el mismo nombre, y un tiempo agotado puede haberla creado ya). Las que AMPLÍAN sí:
 * añadir lo que ya está es no hacer nada.
 */
object PublicarPlaylist {
    /**
     * Enlaces por petición. Medido en el servidor real (`wrangler tail`, 2026-11-09): cada
     * canción NUEVA cuesta unos 6-7 ms de CPU (tandas de 3 canciones nuevas: 19-26 ms; una GET sin
     * tarjetas, 1-4 ms), y el plan gratuito da 10 ms por petición. Con 1 sale a unos 8-9 ms, que es
     * lo único que cabe; las conocidas casi no cuestan, pero la app no sabe cuáles son. Con más de
     * una canción nueva por petición se pasa del límite (aquí no llegó a salir 1102: las de 19-26 ms
     * contestaron `ok`, pero no hay que fiarse de eso). Subirla exige servidor de pago o más ligero.
     */
    const val TANDA = 1

    /** Veces que se intenta una tanda de ampliar antes de darla por perdida. */
    const val INTENTOS = 3

    private const val PAUSA_MS = 1_500L

    sealed class Resultado {
        /** Todo publicado. [fallidas] son los enlaces que el servidor no pudo montar. */
        data class Hecho(val id: String, val canciones: Int, val fallidas: List<String>) : Resultado()

        /** Creada pero incompleta: [canciones] ya están publicadas y [error] es lo que cortó el resto. */
        data class Parcial(val id: String, val canciones: Int, val fallidas: List<String>, val error: ApiMusica.R.Error) : Resultado()

        /** No se creó nada, salvo si [dudosa]: sin respuesta (tiempo agotado), puede que SÍ se creara. */
        data class Fallo(val error: ApiMusica.R.Error, val dudosa: Boolean) : Resultado()
    }

    fun tandas(enlaces: List<String>, tam: Int = TANDA): List<List<String>> = enlaces.chunked(tam)

    /**
     * Bloqueante (hilo de fondo). [alCrear] recibe el id en cuanto el servidor crea la playlist,
     * ANTES de las demás tandas: si el móvil se cae a medias, la marca local ya existe y se puede
     * retirar. [alTanda] recibe (hechas, total) antes de cada petición y al acabar.
     */
    fun publicar(
        api: ApiMusica, nombre: String, enlaces: List<String>, tam: Int = TANDA,
        alCrear: (String) -> Unit, pausa: (Long) -> Unit = { Thread.sleep(it) },
        alTanda: (hechas: Int, total: Int) -> Unit = { _, _ -> }
    ): Resultado {
        val tandas = tandas(enlaces, tam)
        require(tandas.isNotEmpty()) { "sin enlaces" }
        alTanda(0, tandas.size)
        val primera = when (val r = api.publicar(nombre, tandas[0])) {
            is ApiMusica.R.Ok -> r.v
            is ApiMusica.R.Error -> return Resultado.Fallo(r, dudosa = r.codigo == "red")
        }
        alCrear(primera.id)
        var canciones = primera.canciones
        val fallidas = primera.fallidas.toMutableList()
        for ((i, t) in tandas.drop(1).withIndex()) {
            alTanda(i + 1, tandas.size)
            when (val r = ampliar(api, primera.id, t, pausa)) {
                is ApiMusica.R.Ok -> { canciones = r.v.canciones; fallidas += r.v.fallidas }
                is ApiMusica.R.Error -> return Resultado.Parcial(primera.id, canciones, fallidas, r)
            }
        }
        alTanda(tandas.size, tandas.size)
        return Resultado.Hecho(primera.id, canciones, fallidas)
    }

    /** Sin red y 409 `ocupada` (otro escrito a la vez) se repiten; lo demás es definitivo. */
    private fun ampliar(api: ApiMusica, id: String, enlaces: List<String>, pausa: (Long) -> Unit): ApiMusica.R<Publicada> {
        var r = api.anadirAPublicada(id, enlaces)
        var n = 1
        while (r is ApiMusica.R.Error && (r.codigo == "red" || r.codigo == "ocupada") && n < INTENTOS) {
            pausa(PAUSA_MS * n)
            r = api.anadirAPublicada(id, enlaces)
            n++
        }
        return r
    }
}

/** Borrar la cuenta de Música: el servidor, el token de este móvil y las marcas de "publicada". */
object BorrarCuentaMusica {
    /**
     * Bloqueante. `true` = la cuenta ya no existe (borrada ahora, o el token ya no valía: el
     * servidor no la conoce) y el token de [uid] se ha tirado. Sin respuesta, no se toca nada
     * (se puede repetir).
     */
    fun borrar(api: ApiMusica, uid: String, borrarToken: (String) -> Unit): Boolean {
        val r = api.borrarCuenta()
        val hecho = r is ApiMusica.R.Ok || (r is ApiMusica.R.Error && r.status == 401 && r.codigo == "token")
        if (hecho) borrarToken(uid)
        return hecho
    }

    /** Quita la marca de "publicada" de todas las playlists de este móvil (ya no existen en el servidor). */
    fun quitarMarcas(almacen: AlmacenMusica) {
        for (p in almacen.estado().playlists) if (p.publicadaId != null) almacen.marcarPublicada(p.id, null)
    }
}

/** Los textos de lo que sale mal al publicar, guardar o denunciar (códigos de error del servidor, ver B7). */
object TextosShurs {
    fun error(e: ApiMusica.R.Error): String = when {
        e.codigo == "baneada" -> "Esta cuenta de ForoCoches está bloqueada en Música."
        e.tokenInservible -> "Tu verificación ya no vale. Vuelve a verificarte para hacerlo"
        else -> when (e.codigo) {
            "red" -> "No se ha podido conectar con Música. Prueba en un rato"
            "nombre" -> "Ese nombre no vale para una playlist pública. Cámbialo e inténtalo otra vez"
            "tope" -> "Ya tienes ${Musica.MAX_PLAYLISTS} playlists publicadas. Retira alguna"
            "limite" -> "Has hecho muchas cosas seguidas. Prueba dentro de un rato"
            "llena" -> "La playlist pública ya tiene el máximo de ${Musica.MAX_CANCIONES} canciones"
            "no_existe", "http_404" -> "Esa playlist ya no existe"
            "motivo" -> "Cuéntanos el motivo (hasta 300 caracteres)"
            else -> "No se ha podido hacer ahora mismo. Prueba en un rato"
        }
    }

    /** Denunciar tiene su propio "límite": 5 al día. */
    fun errorDenuncia(e: ApiMusica.R.Error): String =
        if (e.codigo == "limite") "Has llegado al máximo de 5 denuncias de hoy. Prueba mañana" else error(e)
}
