package com.fcplus.forocoches

enum class Target { HOME, PM_INBOX, THREAD }

data class Route(val target: Target, val url: String = "", val deferred: Boolean = false)

/**
 * Decisión pura de "¿a qué destino NATIVO voy al abrir la app por notificación/launcher?".
 * Sin dependencias de Android ni estado; por diseño no existe un destino web.
 */
object IntentRouter {
    // Había un destino más, NOTICES, al que se llegaba tocando la notificación de
    // citas/menciones. Se fue con las notificaciones push el 2026-09-21: sin nadie que mande
    // ese extra, era una ruta inalcanzable (y un enum al que nadie podía llegar).
    fun route(rawUrl: String?, engineReady: Boolean): Route {
        val url = TrustedOrigins.trustedUrlOrDefault(rawUrl)
        return when {
            url.contains("private.php") -> Route(Target.PM_INBOX, url = url, deferred = !engineReady)
            url.contains("showthread.php") -> Route(Target.THREAD, url = url, deferred = !engineReady)
            // Cualquier otra cosa (url nula/basura/home): lista nativa. Nunca la capa web.
            else -> Route(Target.HOME)
        }
    }
}
