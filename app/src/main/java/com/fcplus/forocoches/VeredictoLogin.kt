package com.fcplus.forocoches

/**
 * Si un login ha entrado o no — **y en particular al AÑADIR una segunda cuenta**.
 *
 * El 2026-09-17 el dueño reportó que la app no le dejaba añadir una cuenta nueva: metía usuario
 * y contraseña, el panel se cerraba **sin decir nada**, salía "Sesión iniciada"… y seguía con la
 * cuenta de antes. Reproducido en el Samsung y medido por CDP (el motor seguía logueado como la
 * cuenta vieja), la cadena era ésta:
 *
 *  1. `onLoginResult` daba el veredicto con `isLoggedIn()`, o sea **"¿hay cookie `bbuserid`?"**.
 *     Eso vale cuando entras desde fuera, pero al añadir cuenta **la cookie ya está puesta antes
 *     de empezar**: el test da que sí pase lo que pase, incluso si FC ha rechazado el login.
 *  2. Como se daba por bueno, se pedía `fcQuienSoy`… que contestaba **la cuenta vieja**, porque
 *     la sesión nunca había cambiado. `Cuentas.anadir` no añadía nada (ya estaba) y nadie se
 *     enteraba de nada.
 *  3. Y el motivo del rechazo **sí venía**: `fcLogin` extrae el error del HTML de FC, pero
 *     Kotlin lo tiraba en cuanto la cookie decía que sí.
 *
 * Y medido hasta el final (CDP, 2026-09-17), el fallo de fondo era **otro**, y es el que de
 * verdad impedía añadir la cuenta: FC contestaba *"Bienvenido de nuevo Duminuvi. Gracias por
 * iniciar sesión"* y **la sesión seguía siendo la de antes**. La sesión de FC son CUATRO cookies
 * (gotcha 6) y el `bbsessionhash` viejo manda sobre las credenciales nuevas, así que hay que
 * **borrar la sesión ANTES** de entrar — que es justo lo que `cambiarACuenta` ya hacía con
 * `SesionFC.borrar` y el login al añadir cuenta no.
 *
 * Con la sesión borrada antes del POST, la cookie **vuelve a ser un veredicto honesto**: si
 * después hay `bbuserid`, es del que acaba de entrar; si no hay, no ha entrado nadie y se
 * reponen las cookies de la cuenta anterior.
 *
 * De ahí las reglas de aquí:
 * - **Si el motor no llegó a enviar el formulario (`posted=false`), no ha entrado nadie.** Ese
 *   dato lo manda `fcLogin` desde el primer día (Cloudflare delante, fallo de red) y Kotlin no
 *   lo leía.
 * - **Si hay sesión después, ha entrado** — y solo porque antes nos hemos asegurado de que no
 *   la hubiera.
 *
 * **Lo que a propósito NO decide: el texto de error raspado del HTML de FC.** Sirve para
 * explicárselo al usuario, pero no para el veredicto: el regex de `fcLogin` busca clases
 * `error|standard_error|blockrow`, y `blockrow` es una clase genérica de vBulletin que puede
 * aparecer en una página perfectamente buena. Dejar que ese texto mande sobre la cookie
 * arriesga romper el login **normal** —el camino más crítico de la app— por un falso positivo,
 * a cambio de nada: el veredicto honesto sale de borrar la sesión antes de entrar.
 */
object VeredictoLogin {

    enum class Veredicto {
        /** Dentro: se puede cerrar el panel y refrescar con la sesión nueva. */
        ENTRA,
        /** No ha entrado: el panel se queda y se dice por qué. */
        FALLA
    }

    /**
     * [posted] es el `posted` de `fcLogin`: false = el formulario **no llegó a enviarse**
     * (Cloudflare por delante, fallo de red). [haySesion] es la cookie `bbuserid`.
     */
    fun de(posted: Boolean, haySesion: Boolean): Veredicto =
        if (posted && haySesion) Veredicto.ENTRA else Veredicto.FALLA

    /**
     * Qué se le dice al usuario cuando añadir cuenta no sale.
     *
     * Si FC da el motivo, se enseña **el suyo**: explica sus errores mejor que nosotros (misma
     * decisión que en [ErrorPublicar]). Y cuando no lo da, no se inventa una causa — en
     * particular no se culpa a la conexión: el POST ha ido y ha vuelto, así que hay red.
     */
    fun mensajeAlAnadir(err: String): String =
        if (err.isNotBlank()) err.trim()
        else "No se pudo entrar con esa cuenta. Comprueba el usuario y la contraseña."
}
