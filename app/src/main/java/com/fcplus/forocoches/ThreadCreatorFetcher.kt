package com.fcplus.forocoches

import org.jsoup.Jsoup
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * Obtiene el CREADOR de un hilo (autor del primer post) descargando solo lo justo:
 * lee el stream por trozos y **corta la conexión** en cuanto encuentra el primer post,
 * sin bajarse el hilo entero. Pensado para el skin viejo móvil, cuyo listado no muestra
 * el creador (solo el último que postea).
 */
class ThreadCreatorFetcher {

    companion object {
        private const val UA =
            "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"
        private const val MAX_BYTES = 200_000      // tope de seguridad
        private const val MIN_PARSE = 6_000        // no parsear hasta tener algo de cuerpo
    }

    /**
     * Autor del PRIMER post (= creador). Robusto a skins:
     *  - viejo: `li.postbit span.xsaid a[href*=member.php]`
     *  - nuevo: `#postmenu_ b a[href*=member.php]`
     * Funciona también con HTML parcial (jsoup tolera el cierre incompleto).
     */
    fun firstPostAuthor(html: String): String? {
        val doc = Jsoup.parse(html)
        val a = doc.selectFirst("li.postbit span.xsaid a[href*=member.php]")
            ?: doc.selectFirst("[id^=postmenu_] b a[href*=member.php]")
            ?: doc.selectFirst("[id^=postmenu_] a[href*=member.php]")
        return a?.text()?.trim()?.ifBlank { null }
    }

    /** Descarga showthread con corte temprano y devuelve el creador, o null si falla. */
    fun fetchCreator(threadId: Long, cookie: String): String? {
        val url = "https://forocoches.com/foro/showthread.php?t=$threadId"
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.setRequestProperty("Cookie", cookie)
        conn.setRequestProperty("User-Agent", UA)
        conn.connectTimeout = 10_000
        conn.readTimeout = 15_000
        try {
            if (conn.responseCode !in 200..299) return null
            val buf = ByteArrayOutputStream()
            val chunk = ByteArray(8192)
            conn.inputStream.use { input ->
                while (buf.size() < MAX_BYTES) {
                    val n = input.read(chunk)
                    if (n < 0) break
                    buf.write(chunk, 0, n)
                    if (buf.size() >= MIN_PARSE) {
                        firstPostAuthor(buf.toString("UTF-8"))?.let { return it } // cierra y corta
                    }
                }
            }
            return firstPostAuthor(buf.toString("UTF-8"))
        } catch (e: Exception) {
            return null
        } finally {
            conn.disconnect()
        }
    }
}
