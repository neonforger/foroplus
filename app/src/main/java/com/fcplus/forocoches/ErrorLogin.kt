package com.fcplus.forocoches

/**
 * Qué contarle al usuario cuando no consigue entrar, y si merece la pena verificar antes.
 *
 * ## El fallo que arregla
 *
 * El 2026-09-06, el día que entraron 60 personas de golpe, **cinco no pudieron iniciar sesión**:
 * todas con VPN o viviendo fuera de España. El mensaje que veían era *"ForoCoches está pidiendo
 * verificación. Inténtalo de nuevo en un momento"*… y **reintentar no podía funcionar jamás**,
 * porque su IP seguía siendo la misma y el desafío de Cloudflare seguía ahí.
 *
 * Se apañaron solos, y de paso descubrieron el remedio: poner la VPN en España, entrar, y a
 * partir de ahí funciona incluso sin VPN. O sea que lo que se atraganta es **el login**, no el
 * uso posterior.
 *
 * ## Por qué el login se quedaba sin salida y el listado no
 *
 * Ante un Cloudflare, el listado llama a `showWeb()` y el usuario resuelve el desafío; el login
 * solo pintaba un texto. Esa asimetría era todo el fallo. Ahora el login también ofrece resolver
 * el desafío (detrás de una capa opaca, como al reportar), y **este mensaje es el último
 * recurso**: lo que se dice cuando la verificación tampoco ha podido ser.
 */
object ErrorLogin {

    /**
     * ¿Hay que intentar la verificación interactiva?
     *
     * Solo con un Cloudflare y **solo una vez por intento de login**: sin esa segunda condición,
     * un desafío que no se deja resolver dejaría al usuario en un bucle de overlays.
     */
    fun debeVerificar(error: String, yaSeIntento: Boolean): Boolean =
        error == "cloudflare" && !yaSeIntento

    /**
     * El texto que ve el usuario.
     *
     * Para el caso de Cloudflare **se dice lo que de verdad funciona**, no "inténtalo más tarde":
     * ese consejo es falso y fue lo que tuvo a cinco personas dando vueltas una noche entera.
     */
    fun mensaje(error: String): String = when {
        error == "cloudflare" ->
            "ForoCoches no está dejando pasar tu conexión.\n\n" +
                "Suele ocurrir con VPN o desde fuera de España. Prueba a desactivar la VPN, " +
                "o a conectarla a un servidor de España, y vuelve a entrar."
        error.isNotEmpty() -> error
        else -> "Usuario o contraseña incorrectos"
    }
}
