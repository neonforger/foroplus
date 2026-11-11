package com.fcplus.forocoches

/** Borrar la cuenta de Música: el servidor y el token de este móvil. */
object BorrarCuentaMusica {
    enum class Resultado {
        /** La cuenta ya no existe en el servidor y el token de este móvil se ha tirado. */
        HECHO,

        /**
         * 401 `token`: el token no vale (caducó al año, o lo purgó el servidor), pero la cuenta y sus publicaciones
         * PUEDEN seguir ahí: no se da por borrada. Se tira el token muerto y hay que verificarse otra vez.
         */
        VERIFICAR,

        /** Sin respuesta que valga: no se toca nada (se puede repetir). */
        FALLO
    }

    /** Bloqueante. El token de [uid] se tira si el servidor borró la cuenta o si ya no valía. */
    fun borrar(api: ApiMusica, uid: String, borrarToken: (String) -> Unit): Resultado {
        val r = api.borrarCuenta()
        val res = when {
            r is ApiMusica.R.Ok -> Resultado.HECHO
            r is ApiMusica.R.Error && r.status == 401 && r.codigo == "token" -> Resultado.VERIFICAR
            else -> Resultado.FALLO
        }
        if (res != Resultado.FALLO) borrarToken(uid)
        return res
    }
}
