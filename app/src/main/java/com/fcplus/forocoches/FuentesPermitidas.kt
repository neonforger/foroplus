package com.fcplus.forocoches

import java.net.URI

/**
 * De dónde puede descargar la app lo que anuncia la config remota: la lista de la sección +18 y
 * los emoticonos de ForoPlus ([EmoticonosRemotos]). Es el requisito de transparencia: la app NO se
 * conecta a ningún servidor nuestro, ni aunque se cambie la config remota. Si un día algo se muda
 * (un CDN), su prefijo se añade AQUÍ, en el código, y se ve en el diff público.
 */
object FuentesPermitidas {
    private val PREFIJOS = listOf(
        "https://raw.githubusercontent.com/neonforger/foroplus-mas18/",
        EmoticonosRemotos.ORIGEN,
    )

    fun aceptada(url: String): Boolean {
        // Rechazo de escape del repo con caracteres codificados (..), hex (%), barras invertidas,
        // parámetros (?#) o espacios/control que permitan ataques de inyección.
        if (url.contains("..") || url.contains("%") || url.contains("\\") ||
            url.contains("?") || url.contains("#") ||
            url.any { it.isWhitespace() || it.isISOControl() }) {
            return false
        }

        // Validación estricta de URI: solo HTTPS, raw.githubusercontent.com, sin autenticación.
        return try {
            val uri = URI(url)
            uri.scheme == "https" &&
            uri.host == "raw.githubusercontent.com" &&
            uri.userInfo == null &&
            PREFIJOS.any { url.startsWith(it) }
        } catch (e: Exception) {
            false
        }
    }
}
