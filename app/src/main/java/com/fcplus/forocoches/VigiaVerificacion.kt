package com.fcplus.forocoches

/**
 * Lo que decide el sondeo de una verificación de Cloudflare hecha en un WebView TAPADO
 * (reportar, gotcha 12; entrar, `abrirVerificacionLogin`): cuándo preguntar a la página, si la
 * tapa nativa se puede quitar y cuándo rendirse.
 *
 * **Por qué existe (2026-11-13).** El sondeo preguntaba con `evaluateJavascript` y programaba la
 * SIGUIENTE pregunta dentro del callback. Una pregunta lanzada justo cuando el challenge da paso
 * a la página real va al documento que se está yendo y **su callback no vuelve nunca**: la
 * cadena se paraba con la tapa quitada (la última respuesta era "challenge"), el formulario de
 * FC se quedaba a la vista sin enviarse y el plazo de 90 s, que vivía en el mismo callback,
 * tampoco saltaba. Lo vio Juan dos veces (reporte rellenado a mano en la web de FC y perdido) y
 * se reprodujo en el Samsung sin las cookies de consentimiento: tras `carga … report.php` no
 * volvió ni un callback, y por CDP la página contestaba al momento con el formulario cargado.
 *
 * Reglas: se pregunta por reloj, no por respuesta; una pregunta sin contestar en
 * [perdidaMs] se da por perdida y su respuesta tardía no cuenta; y la tapa solo se quita con una
 * respuesta RECIENTE (≤ [frescuraMs]) que diga "challenge y sin formulario". Si no se sabe qué
 * hay en pantalla, tapa: la regla de oro se cumple por defecto, no por suerte.
 */
class VigiaVerificacion(
    private val inicio: Long,
    private val plazoMs: Long = 90_000L,
    private val perdidaMs: Long = 1_000L,
    private val frescuraMs: Long = 1_000L
) {
    private var ultimaPregunta = 0
    private var pendiente: Int? = null
    private var pendienteDesde = 0L
    private var respuestaEn: Long? = null
    private var cf = false

    /** Preguntas dadas por perdidas (su callback no volvió a tiempo). Para la traza. */
    var perdidas = 0
        private set

    /** La última respuesta válida vio el formulario real. */
    var formulario = false
        private set

    /**
     * Número de la pregunta que hay que lanzar ahora, o null si la anterior aún puede contestar.
     */
    fun siguientePregunta(ahora: Long): Int? {
        if (pendiente != null) {
            if (ahora - pendienteDesde < perdidaMs) return null
            perdidas++
        }
        ultimaPregunta++
        pendiente = ultimaPregunta
        pendienteDesde = ahora
        return ultimaPregunta
    }

    /** Apunta la respuesta; false si es de una pregunta ya dada por perdida (no cuenta). */
    fun respuesta(pregunta: Int, ahora: Long, cf: Boolean, form: Boolean): Boolean {
        if (pregunta != pendiente) return false
        pendiente = null
        respuestaEn = ahora
        this.cf = cf
        formulario = form
        return true
    }

    /** La tapa solo se quita con el challenge a la vista, sin formulario y visto hace nada. */
    fun tapaPuesta(ahora: Long): Boolean {
        val vista = respuestaEn ?: return true
        val reciente = ahora - vista <= frescuraMs
        return !(reciente && cf && !formulario)
    }

    fun agotado(ahora: Long): Boolean = ahora - inicio > plazoMs
}
