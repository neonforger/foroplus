package com.fcplus.forocoches

/**
 * Cuándo ofrecerle al usuario que los enlaces de ForoCoches se abran en la app.
 *
 * ## Por qué hace falta ofrecerlo siquiera
 *
 * La app declara los enlaces de hilo de `forocoches.com`, pero **Android nunca los va a
 * verificar**: los App Links exigen un `assetlinks.json` servido desde ese dominio, y el dominio
 * no es nuestro. En Android 12+ eso significa que el sistema NO abre la app sola hasta que el
 * usuario lo autoriza a mano, en un ajuste que no encuentra nadie por su cuenta.
 *
 * ## Las cinco reglas, y por qué cada una
 *
 * 1. **Solo en Android 12+.** Por debajo el sistema ya ofrece la app en el diálogo de "abrir
 *    con" sin pedir permiso: avisar ahí sería molestar por un problema que esa gente no tiene.
 * 2. **Solo si está apagado**, y se pregunta al SISTEMA, no a una preferencia nuestra. El
 *    usuario puede activarlo o desactivarlo desde Ajustes sin pasar por aquí; fiarse de lo que
 *    creíamos recordar acabaría enseñando el aviso a quien ya lo tiene puesto.
 * 3. **No en el primer arranque.** Pedir un permiso raro antes de que entienda qué es la app se
 *    lee como una app pidiendo cosas, y gasta el único momento de buena voluntad que hay.
 * 4. **Si lo cerró, no se insiste.** Queda la fila de Opciones para quien se arrepienta.
 * 5. **Un aviso remoto manda sobre esto.** El canal de avisos es para emergencias (que FC
 *    cambie su HTML y la app deje de leer nada); si compite con esto el día que haga falta de
 *    verdad, el canal pierde su razón de ser.
 */
object EnlacesApp {

    /** A partir de la tercera sesión: ya sabe qué es la app y aún se acuerda de instalarla. */
    const val SESIONES_MINIMAS = 3

    fun debeOfrecer(
        soportado: Boolean,
        yaActivado: Boolean,
        sesiones: Int,
        descartado: Boolean,
        hayAvisoRemoto: Boolean
    ): Boolean =
        soportado && !yaActivado && !descartado && !hayAvisoRemoto && sesiones >= SESIONES_MINIMAS
}
