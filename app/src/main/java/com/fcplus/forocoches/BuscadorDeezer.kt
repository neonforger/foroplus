package com.fcplus.forocoches

import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.net.URLEncoder

/** Un resultado del buscador de canciones de Deezer. `portada` ya viene filtrada por host. */
data class ResultadoDeezer(
    val id: Long,
    val titulo: String,
    val artista: String,
    val portada: String?,
    val duracionSeg: Int?
)

/** Quien hace la petición: URL -> cuerpo de un 200, o null. Se inyecta para probar sin red. */
typealias RedDeezer = (url: String) -> String?

/**
 * Busca canciones en la API pública de Deezer (sin clave) y pide su fragmento de 30 s, desde el móvil.
 *
 * Mismo criterio que [BuscadorMusica]: solo https, host EXACTO de una lista cerrada, **sin cookies**
 * (gotcha 55) y **sin seguir redirecciones**. Los hosts salen de respuestas reales (fixtures en
 * `src/test/resources/deezer/`, medido 2026-11-11): la API en `api.deezer.com`, los fragmentos en
 * `cdnt-preview.dzcdn.net` y las portadas en `cdn-images.dzcdn.net` (`e-cdns-images.dzcdn.net` es el
 * host histórico de portadas: no salió en las respuestas de hoy, se deja por si vuelve).
 *
 * La URL `preview` va firmada (`hdnea=exp=…~hmac=…`) y **caduca**: la de la búsqueda no se guarda ni
 * se reutiliza; [fragmento] pide `track/<id>` en el momento de dar al play.
 * La lista usa `cover_medium` (250 px): es pequeña y basta para una fila.
 * Las funciones de red bloquean: llamarlas fuera del hilo principal.
 */
object BuscadorDeezer {

    val HOSTS: Set<String> = setOf("api.deezer.com")
    val HOSTS_PORTADA: Set<String> = setOf("cdn-images.dzcdn.net", "e-cdns-images.dzcdn.net")
    val HOSTS_FRAGMENTO: Set<String> = setOf("cdnt-preview.dzcdn.net")

    internal const val TIMEOUT_MS = 8_000
    internal const val MAX_CUERPO = 512 * 1024
    private const val MIN_CONSULTA = 2

    // Mismo valor que BuscadorMusica.UA.
    internal const val UA =
        "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"

    private fun hostEn(url: String, permitidos: Set<String>): Boolean = try {
        val u = URI(url)
        u.scheme == "https" && u.rawUserInfo == null && u.port == -1 && u.host in permitidos
    } catch (e: Exception) {
        false
    }

    /** https y host EXACTO de [HOSTS] (la red real lo exige antes de abrir nada). */
    fun aceptada(url: String): Boolean = hostEn(url, HOSTS)

    /** Parsea `search/track`. Descarta lo que no tenga id o título; la portada de host raro queda a null. */
    fun parsearBusqueda(json: String): List<ResultadoDeezer> = try {
        val data = JSONObject(json).optJSONArray("data")
        val salida = ArrayList<ResultadoDeezer>()
        for (i in 0 until (data?.length() ?: 0)) {
            val o = data!!.optJSONObject(i) ?: continue
            val id = o.optLong("id", 0L)
            val titulo = o.optString("title", "").trim()
            if (id <= 0L || titulo.isEmpty()) continue
            val portada = o.optJSONObject("album")?.optString("cover_medium", "")
                ?.takeIf { it.isNotEmpty() && hostEn(it, HOSTS_PORTADA) }
            val dur = o.optInt("duration", 0).takeIf { it > 0 }
            salida += ResultadoDeezer(id, titulo, o.optJSONObject("artist")?.optString("name", "")?.trim().orEmpty(), portada, dur)
        }
        salida
    } catch (e: Exception) {
        emptyList()
    }

    /** La URL del fragmento de `track/<id>`, solo si es https y de [HOSTS_FRAGMENTO]; si no, null. */
    fun parsearFragmento(json: String): String? = try {
        JSONObject(json).optString("preview", "").takeIf { it.isNotEmpty() && hostEn(it, HOSTS_FRAGMENTO) }
    } catch (e: Exception) {
        null
    }

    /** Hasta 10 canciones. Con la consulta recortada de menos de 2 letras no se toca la red. */
    fun buscar(q: String, red: RedDeezer = ::redDeezerReal): List<ResultadoDeezer> {
        val consulta = q.trim()
        if (consulta.length < MIN_CONSULTA) return emptyList()
        val json = red("https://api.deezer.com/search/track?q=${URLEncoder.encode(consulta, "UTF-8")}&limit=10")
            ?: return emptyList()
        return parsearBusqueda(json)
    }

    /** Un fragmento FRESCO: siempre pide `track/<id>` (la URL de la búsqueda puede haber caducado). */
    fun fragmento(id: Long, red: RedDeezer = ::redDeezerReal): String? =
        red("https://api.deezer.com/track/$id")?.let { parsearFragmento(it) }
}

/** https, host de [BuscadorDeezer.HOSTS], sin cookies ni redirecciones, máx. 512 KB, 8 s. Null si falla. */
fun redDeezerReal(url: String): String? {
    if (!BuscadorDeezer.aceptada(url)) return null
    val conn = try {
        (URL(url).openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = false
            requestMethod = "GET"
            connectTimeout = BuscadorDeezer.TIMEOUT_MS
            readTimeout = BuscadorDeezer.TIMEOUT_MS
            setRequestProperty("User-Agent", BuscadorDeezer.UA)
        }
    } catch (e: Exception) {
        return null
    }
    return try {
        if (conn.responseCode != 200) null
        else conn.inputStream.use { entrada ->
            val salida = ByteArrayOutputStream()
            val buf = ByteArray(8 * 1024)
            while (salida.size() < BuscadorDeezer.MAX_CUERPO) {
                val n = entrada.read(buf, 0, minOf(buf.size, BuscadorDeezer.MAX_CUERPO - salida.size()))
                if (n < 0) break
                salida.write(buf, 0, n)
            }
            salida.toString("UTF-8")
        }
    } catch (e: Exception) {
        null
    } finally {
        conn.disconnect()
    }
}
