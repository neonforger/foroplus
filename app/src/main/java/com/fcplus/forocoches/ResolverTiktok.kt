package com.fcplus.forocoches

import java.net.HttpURLConnection
import java.net.URL

/**
 * Resuelve un enlace corto de TikTok a la forma que acepta FC (ver [TiktokCorto]).
 *
 * Solo pregunta adónde redirige: lee la cabecera `Location` sin seguirla ni bajar la página, y
 * no manda cookies ni nada del usuario. Va a TikTok, no al foro, así que no choca con "cero HTTP
 * nativo para el HTML de FC". Medido el 2026-10-16: contesta 302 con cualquier User-Agent.
 * Bloquea: llamarlo fuera del hilo principal.
 */
object ResolverTiktok {

    private const val SALTOS = 3

    /** La forma larga, o null si no lleva a un vídeo o no hay red. */
    fun resolver(corto: String): String? {
        var url = corto.replaceFirst(Regex("^http://", RegexOption.IGNORE_CASE), "https://")
        repeat(SALTOS) {
            val destino = adonde(url) ?: return null
            TiktokCorto.completa(destino)?.let { return it }
            url = destino
        }
        return null
    }

    private fun adonde(url: String): String? {
        val conn = try {
            val u = URL(url)
            // Solo se pregunta a TikTok: una redirección a otro sitio no se sigue.
            if (u.protocol != "https" || !(u.host == "tiktok.com" || u.host.endsWith(".tiktok.com"))) return null
            u.openConnection() as HttpURLConnection
        } catch (e: Exception) {
            return null
        }
        conn.instanceFollowRedirects = false
        conn.requestMethod = "HEAD"
        conn.connectTimeout = 5_000
        conn.readTimeout = 5_000
        return try {
            if (conn.responseCode !in 300..399) null
            else conn.getHeaderField("Location")?.let { URL(URL(url), it).toString() }
        } catch (e: Exception) {
            null
        } finally {
            conn.disconnect()
        }
    }
}
