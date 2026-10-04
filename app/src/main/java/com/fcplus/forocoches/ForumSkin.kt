package com.fcplus.forocoches

/**
 * Diseño ("skin") con el que FC nos sirve el HTML.
 *
 * FC tiene dos juegos de plantillas y elige por un ajuste **de la CUENTA** (`styleid`), no por
 * la petición: sondeado el 2026-08-10, **ni `?styleid=N` en la URL ni las cookies de estilo
 * hacen nada** — la página vuelve igual. La única palanca es el POST que usa la propia FC en
 * su `setOldDesign()`: `profile.php?do=updatestyleid` con `newstyleset`.
 *
 * El motor solo sabe leer el juego MODERNO (los selectores de `extractor.js` son los de ese
 * markup: `[id^="postmenu_"]`, `div.postbit_wrapper`…). Con el antiguo, `fcLoadThread`
 * descarta todos los posts y el usuario ve el hilo vacío — que es justo lo que reportó un
 * tester. Por eso, al detectar el juego antiguo, la app lo cambia (decisión del dueño,
 * declarada en la ficha de Play).
 *
 * Aquí vive la POLÍTICA (qué hacer con cada styleid); `extractor.js` solo lee y ejecuta.
 */
object ForumSkin {

    /** Juego antiguo: 5 = escritorio, 7 = móvil. FC sirve uno u otro según el dispositivo. */
    private val OLD = setOf(5, 7)

    /** Juego moderno: 8 = móvil, 9 = escritorio. Es el que sabe parsear el motor. */
    private val MODERN = setOf(8, 9)

    /** Valor que se envía en `newstyleset` para pasar al juego moderno. */
    const val TARGET = 8

    sealed class Decision {
        /** Nos están sirviendo el markup que el motor sabe leer: no se toca nada. */
        object Ok : Decision()

        /** Juego antiguo con sesión: se cambia la cuenta al moderno. */
        object Switch : Decision()

        /** Juego antiguo pero sin sesión: no hay `securitytoken`, no se puede cambiar. */
        object NeedsSession : Decision()

        /**
         * No sabemos qué nos han servido (FC cambió el número, o no encontramos el
         * `styleid` en la página). NO se toca la cuenta a ciegas: se avisa y punto.
         */
        object Unknown : Decision()
    }

    fun decide(styleid: Int?, hasSession: Boolean): Decision = when {
        styleid == null -> Decision.Unknown
        styleid in MODERN -> Decision.Ok
        styleid !in OLD -> Decision.Unknown
        hasSession -> Decision.Switch
        else -> Decision.NeedsSession
    }

    /**
     * Explicación para el usuario cuando el contenido llega vacío. Es el CANARIO que no
     * existía (gotcha 17): sin esto, un cambio de HTML de FC se ve como un hilo en blanco y
     * nadie se entera hasta que un tester se queja.
     */
    fun warning(styleid: Int?, hasSession: Boolean): String? = when (decide(styleid, hasSession)) {
        Decision.Ok -> null
        Decision.Switch -> null
        Decision.NeedsSession ->
            "Tu cuenta del foro usa el diseño antiguo y ForoPlus necesita el moderno. " +
                "Inicia sesión para que la app pueda cambiarlo."
        Decision.Unknown ->
            "El foro está sirviendo un diseño que ForoPlus todavía no sabe leer" +
                (if (styleid != null) " (estilo $styleid)" else "") +
                ". Puede que ForoCoches haya cambiado su web."
    }
}
