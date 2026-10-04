package com.fcplus.forocoches

import java.net.URI
import java.util.Locale

object TrustedOrigins {
    const val DEFAULT_URL = "https://forocoches.com/foro/"

    fun trustedUrlOrDefault(rawUrl: String?): String {
        val trimmed = rawUrl?.trim().orEmpty()
        return if (isTrustedForocochesUrl(trimmed)) trimmed else DEFAULT_URL
    }

    /** forocoches.com o cualquier subdominio (www, m, etc.), NO 'malforocoches.com'. */
    private fun isForocochesHost(host: String): Boolean =
        host == "forocoches.com" || host.endsWith(".forocoches.com")

    fun isTrustedForocochesUrl(rawUrl: String?): Boolean {
        val uri = parse(rawUrl) ?: return false
        val scheme = uri.scheme?.lowercase(Locale.US) ?: return false
        val host = uri.host?.lowercase(Locale.US) ?: return false
        // http o https: el logo/enlaces internos de FC pueden ir a m.forocoches.com o sin TLS;
        // esos deben quedarse dentro de la app, no abrirse en el navegador externo.
        return (scheme == "https" || scheme == "http") && isForocochesHost(host)
    }

    fun isHttpOrHttps(rawUrl: String?): Boolean {
        val scheme = parse(rawUrl)?.scheme?.lowercase(Locale.US) ?: return false
        return scheme == "http" || scheme == "https"
    }

    private fun parse(rawUrl: String?): URI? {
        val trimmed = rawUrl?.trim()
        if (trimmed.isNullOrEmpty()) return null
        return try {
            URI(trimmed)
        } catch (_: Exception) {
            null
        }
    }
}
