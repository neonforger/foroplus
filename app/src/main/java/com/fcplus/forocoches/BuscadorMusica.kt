package com.fcplus.forocoches

import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.net.URLEncoder

/**
 * Pide a YouTube y Spotify el título, el artista y la portada de una canción, desde el móvil.
 *
 * Solo habla con los hosts de [HOSTS] por https, **sin cookies ni nada del usuario** (no se toca
 * `CookieHandler` y no se pone cabecera `Cookie`): lección del gotcha 55, la cookie de FC viajaba a
 * cualquier URL que contuviera el dominio. Aquí el host se compara EXACTO, sin sufijos ni
 * userinfo ni puerto raro, y las redirecciones no se siguen salvo en [resolverCorto] y al leer la página ([descargar], hasta 3), que las siguen
 * a mano comprobando cada salto. Las funciones de red bloquean: llamarlas fuera del hilo principal.
 * Lo que decide (hosts, URLs) va puro y con tests; la red no se prueba por diseño.
 */
object BuscadorMusica {

    val HOSTS: Set<String> = setOf("www.youtube.com", "open.spotify.com", "spotify.link")
    val HOSTS_PORTADA: Set<String> = setOf("i.ytimg.com", "i.scdn.co", "image-cdn-fa.spotifycdn.com")

    private const val SALTOS = 3
    private const val TIMEOUT_MS = 5_000
    private const val MAX_CUERPO = 1_000_000

    // Mismo valor que PostImages.UA (privado en PostAdapter.kt): si cambia allí, cambiarlo aquí.
    private const val UA =
        "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"

    private fun hostEn(url: String, permitidos: Set<String>): Boolean = try {
        val u = URI(url)
        u.scheme == "https" && u.rawUserInfo == null && u.port == -1 && u.host in permitidos
    } catch (e: Exception) {
        false
    }

    /** https y host EXACTAMENTE en [HOSTS] (sin sufijos, sin userinfo, sin puerto distinto del 443). */
    fun aceptada(url: String): Boolean = hostEn(url, HOSTS)

    /** La URL de portada si viene de un host de imágenes conocido; si no, null. */
    fun portadaAceptada(url: String?): String? = url?.takeIf { hostEn(it, HOSTS_PORTADA) }

    /** La portada de `og:image` de la página de Spotify, ya filtrada por host. */
    fun portadaDePagina(html: String): String? {
        val etiqueta = Regex("""<meta\b[^>]*>""", RegexOption.IGNORE_CASE).findAll(html)
            .map { it.value }
            .firstOrNull { Regex("""(?:property|name)\s*=\s*["']og:image["']""", RegexOption.IGNORE_CASE).containsMatchIn(it) }
            ?: return null
        val contenido = Regex("""content\s*=\s*(?:"([^"]*)"|'([^']*)')""", RegexOption.IGNORE_CASE).find(etiqueta) ?: return null
        return portadaAceptada(contenido.groupValues[1].ifEmpty { contenido.groupValues[2] }.trim())
    }

    private fun codificar(s: String) = URLEncoder.encode(s, "UTF-8")

    fun urlOembed(ref: RefCancion): String = when (ref.fuente) {
        FuenteMusica.YOUTUBE -> "https://www.youtube.com/oembed?url=${codificar(ref.url())}&format=json"
        FuenteMusica.SPOTIFY -> "https://open.spotify.com/oembed?url=${codificar(ref.url())}"
    }

    /** La página pública de la pista (de ahí salen artista, duración y álbum); solo Spotify. */
    fun urlPagina(ref: RefCancion): String? =
        if (ref.fuente == FuenteMusica.SPOTIFY) ref.url() else null

    /** Resuelve `spotify.link/…` siguiendo hasta 3 redirecciones, cada una https y en [HOSTS]. */
    fun resolverCorto(url: String): RefCancion? {
        var actual = url.trim()
        if (!hostEn(actual, setOf("spotify.link"))) return null
        repeat(SALTOS) {
            val destino = destinoDe(actual) ?: return null
            if (!aceptada(destino)) return null
            EnlaceMusica.de(destino)?.let { return it }
            actual = destino
        }
        return null
    }

    /** Lee la cabecera `Location` de [url] sin seguirla; null si no redirige o falla. */
    private fun destinoDe(url: String): String? {
        val conn = abrir(url, "HEAD") ?: return null
        return try {
            if (conn.responseCode !in 300..399) null
            else conn.getHeaderField("Location")?.let { URL(URL(url), it).toString() }
        } catch (e: Exception) {
            null
        } finally {
            conn.disconnect()
        }
    }

    private fun abrir(url: String, metodo: String): HttpURLConnection? = try {
        if (!aceptada(url)) null
        else (URL(url).openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = false
            requestMethod = metodo
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            setRequestProperty("User-Agent", UA)
            setRequestProperty("Accept-Language", "es-ES,es;q=0.9")
        }
    } catch (e: Exception) {
        null
    }

    /**
     * Dónde lleva un `Location` desde [desde]: la URL absoluta si sigue siendo un host aceptado
     * (https, exacto), null si no. Spotify manda a `open.spotify.com/intl-es/…` cuando la petición
     * lleva `Accept-Language: es` (medido 2026-11-08): sin seguir ese salto no había artista.
     */
    fun redireccion(desde: String, location: String?): String? = try {
        location?.let { URL(URL(desde), it).toString() }?.takeIf { aceptada(it) }
    } catch (e: Exception) {
        null
    }

    /** El cuerpo (máx. 1 MB) de un 200; null en cualquier otro caso. Sigue hasta [SALTOS] redirecciones a hosts aceptados, sin cookies. */
    private fun descargar(url: String, saltos: Int = SALTOS): String? {
        val conn = abrir(url, "GET") ?: return null
        return try {
            val codigo = conn.responseCode
            if (codigo in 301..303 || codigo == 307 || codigo == 308) {
                val destino = if (saltos > 0) redireccion(url, conn.getHeaderField("Location")) else null
                conn.disconnect()
                return destino?.let { descargar(it, saltos - 1) }
            }
            if (codigo != 200) null
            else conn.inputStream.use { entrada ->
                val salida = ByteArrayOutputStream()
                val buf = ByteArray(8 * 1024)
                while (salida.size() < MAX_CUERPO) {
                    val n = entrada.read(buf, 0, minOf(buf.size, MAX_CUERPO - salida.size()))
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

    /**
     * Los datos de la canción. Si falla el oEmbed devuelve null (decide quien llama); si solo falla
     * la página de Spotify, devuelve la canción sin artista, duración ni álbum.
     */
    fun datos(ref: RefCancion, reloj: () -> Long = System::currentTimeMillis): Cancion? {
        val oembed = descargar(urlOembed(ref))?.let { MetadatosMusica.oembed(it) } ?: return null
        return when (ref.fuente) {
            FuenteMusica.YOUTUBE -> {
                val sep = TituloCancion.separar(oembed.titulo, oembed.autor)
                Cancion(ref, sep.cancion, sep.artista, null, null, portadaAceptada(oembed.portada), reloj())
            }
            FuenteMusica.SPOTIFY -> {
                val html = urlPagina(ref)?.let { descargar(it) }
                val pagina = html?.let { MetadatosMusica.paginaSpotify(it) }
                val portada = portadaAceptada(oembed.portada) ?: html?.let { portadaDePagina(it) }
                Cancion(ref, oembed.titulo, pagina?.artista, pagina?.album, pagina?.duracionSeg, portada, reloj())
            }
        }
    }
}
