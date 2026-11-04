package com.fcplus.forocoches

/**
 * Lo que decide un reporte sin tocar Android: si el diálogo se puede enviar y qué contestó FC.
 *
 * Por qué existe (2026-11-04, lo destapó Juan): el formulario real de `report.php` no valida
 * nada en el navegador (ni `required` ni `onsubmit`, medido por CDP), pero el SERVIDOR rechaza
 * un reporte sin comentario. La app solo pedía comentario con "Otros", así que los reportes por
 * +18, Spam, Troll, Flood o Contenido salían vacíos, FC los tiraba y la app decía "Reporte
 * enviado ✓" a ciegas, a los 2,5 s, sin mirar la respuesta.
 */
object ResultadoReporte {

    sealed class Resultado {
        /** FC lo aceptó: tras enviar redirige (HTTP 3xx del servidor) al mensaje reportado. */
        data object Enviado : Resultado()
        /** FC contestó, pero no con la redirección: el reporte no ha entrado. */
        data class Rechazado(val mensaje: String) : Resultado()
        /** No se pudo saber (sin respuesta a tiempo): no se puede decir que ha llegado. */
        data object Desconocido : Resultado()
    }

    const val SIN_MOTIVO = "Elige un motivo"
    const val SIN_COMENTARIO = "Escribe por qué lo reportas: ForoCoches no acepta reportes sin explicación"
    const val RECHAZADO_GENERICO = "ForoCoches no ha aceptado el reporte"

    /** `null` si se puede enviar; si no, lo que hay que decirle a quien reporta. */
    fun validar(motivo: String?, comentario: String): String? = when {
        motivo == null -> SIN_MOTIVO
        comentario.isBlank() -> SIN_COMENTARIO
        else -> null
    }

    /**
     * Después de enviar, FC manda al mensaje reportado (`url` oculto del form:
     * `showthread.php?p=N#postN`). Ir allí es la señal de que lo ha aceptado. Esa página NO se
     * carga: bajo la capa relanzaba un Cloudflare que colgaba el flujo (gotcha 12).
     */
    fun esRedireccionDeExito(url: String?): Boolean = url?.contains("showthread.php") == true

    /**
     * La página en la que se quedó el envío, si no redirigió al hilo.
     *
     * Medido el 2026-11-04 (POST con el comentario vacío): FC contesta **200 en
     * `report.php?do=sendemail`, sin redirección**, con el aviso en
     * `<div class="logout_message" id="st-error-main-error-message">`: *"Por favor proporciona
     * una razón para reportar este mensaje."* Un reporte aceptado, en cambio, redirige por HTTP.
     *
     * @param texto el `innerText` de la página.
     * @param quedaFormulario si la página vuelve a traer el formulario de reporte.
     * @param avisoFC el texto de `#st-error-main-error-message`, si la página lo trae.
     */
    fun clasificar(url: String?, texto: String, quedaFormulario: Boolean, avisoFC: String? = null): Resultado = when {
        esRedireccionDeExito(url) -> Resultado.Enviado
        !avisoFC.isNullOrBlank() -> Resultado.Rechazado(avisoFC.trim().replace(Regex("\\s+"), " "))
        Regex("gracias", RegexOption.IGNORE_CASE).containsMatchIn(texto) && !quedaFormulario ->
            Resultado.Enviado
        texto.isBlank() -> Resultado.Desconocido
        else -> Resultado.Rechazado(mensajeDe(texto))
    }

    // Lo que suele decir un error de vBulletin. El texto de la página trae además el menú, los
    // anuncios y el aviso de cookies: se busca la línea del error, no se enseña la página entera.
    private val lineaDeError = Regex(
        "motivo|raz[oó]n|debes|tienes que|no puedes|no se puede|no tienes permiso|esperar|segundos|inv[aá]lid|error",
        RegexOption.IGNORE_CASE
    )
    private val ruido = Regex("cookie|socios|publicidad|precios|black friday", RegexOption.IGNORE_CASE)

    /** La línea del error de FC, o un genérico si no se encuentra ninguna reconocible. */
    fun mensajeDe(texto: String): String =
        texto.lines().map { it.trim() }
            .firstOrNull { it.length in 8..300 && lineaDeError.containsMatchIn(it) && !ruido.containsMatchIn(it) }
            ?: RECHAZADO_GENERICO
}
