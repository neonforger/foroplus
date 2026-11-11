package com.fcplus.forocoches

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** Lo que devuelve la red: el código HTTP y el cuerpo como texto. */
data class Respuesta(val status: Int, val cuerpo: String)

data class Reto(val id: String, val coche: String, val caduca: Long)
/** [pendiente] = el 202 (el servidor aún no ha leído la ficha): preguntar otra vez en unos segundos. */
data class Comprobacion(
    val pendiente: Boolean, val token: String?, val nombre: String?, val puedeVotar: Boolean,
    val diasCuenta: Int? = null
)
data class Yo(val nombre: String, val diasCuenta: Int?, val puedePublicar: Boolean, val publicacionesRestantes: Int)
data class CancionFeed(
    val id: String, val titulo: String, val artista: String?, val portada: String?,
    val spotify: String?, val youtube: String?, val deezer: Long?
)
data class ItemFeed(
    val id: String, val autor: String, val texto: String, val creada: Long, val caduca: Long,
    val corazones: Int, val corazon: Boolean, val mia: Boolean, val cancion: CancionFeed
)
data class MasQuerida(val semana: String, val autor: String, val texto: String, val corazones: Int, val cancion: CancionFeed)
data class PaginaFeed(val items: List<ItemFeed>, val cursor: String?, val masQuerida: MasQuerida?)
data class EstadoCorazon(val corazones: Int, val corazon: Boolean)

enum class OrdenFeed(val param: String) { RECIENTES("recientes"), QUERIDAS("queridas") }

/** De dónde sale la canción al publicar: un id de Deezer (la búsqueda) o un enlace pegado. Nunca los dos. */
sealed class OrigenCancion {
    data class Deezer(val id: Long) : OrigenCancion()
    data class Enlace(val url: String) : OrigenCancion()
}

/**
 * Cliente del servidor de Música. Bloqueante: quien lo llame lo hace desde un hilo de fondo.
 *
 * - [token] se pide EN CADA llamada (no al construir): el token es de la cuenta de FC activa en
 *   ese momento (`{ tokens.de(uidActivo()) }`). Sin token no se manda `Authorization`.
 * - Nunca manda cookies de FC: ni aquí ni en [redReal].
 * - El servidor da 401 `token` / 403 `baneada` (no la vista pública) si el token que llega es
 *   malo; eso sale como [R.Error.tokenInservible] para que quien llama borre el token y, en
 *   `feed()`, se repite sin él.
 */
class ApiMusica(
    private val token: () -> String?,
    private val red: (metodo: String, url: String, cuerpo: String?, cabeceras: Map<String, String>) -> Respuesta = ::redReal
) {
    sealed class R<out T> {
        data class Ok<T>(val v: T) : R<T>()
        data class Error(val codigo: String, val status: Int, val datos: JSONObject?) : R<Nothing>() {
            /** 404 `reto` y 410 `caducado` son lo mismo para la app: pedir otro reto. */
            val retoCaducado get() = codigo == "caducado" || (codigo == "reto" && status == 404)
            /** 503 `challenge`: "ForoCoches no deja comprobarlo ahora, prueba en un rato". */
            val esChallenge get() = codigo == "challenge"
            /** El token ya no vale (caducado, borrado o cuenta baneada): hay que tirarlo. */
            val tokenInservible get() = (status == 401 && codigo == "token") || (status == 403 && codigo == "baneada")
        }
    }

    // ---- verificación (rutas públicas: no mandan token) ----

    fun reto(uid: String): R<Reto> = llamar("POST", "/v1/verificacion/reto", JSONObject().put("uid", uid), conToken = false) {
        Reto(it.getString("reto"), it.getString("coche"), it.getLong("caduca"))
    }

    fun comprobar(reto: String): R<Comprobacion> = llamar("POST", "/v1/verificacion/comprobar", JSONObject().put("reto", reto), conToken = false) { j ->
        Comprobacion(false, j.getString("token"), j.optString("nombre").takeIf { it.isNotEmpty() }, j.getBoolean("puedeVotar"), j.intONull("diasCuenta"))
    }.let { r ->
        // El 202 no lleva token: es "pendiente", no un error ni una cuenta verificada.
        if (r is R.Error && r.status == 202) R.Ok(Comprobacion(true, null, null, false)) else r
    }

    fun yo(): R<Yo> = llamar("GET", "/v1/yo") {
        Yo(it.getString("nombre"), it.intONull("diasCuenta"), it.getBoolean("puedePublicar"), it.getInt("publicacionesRestantes"))
    }

    // ---- feed de canciones de 24 h ----

    /**
     * Con token válido trae `corazon`/`mia` por publicación. El servidor trata un token malo en esta
     * ruta como invitado (200), pero por si un día da 401/403 se repite SIN token: leer es público.
     */
    fun feed(orden: OrdenFeed, cursor: String?): R<PaginaFeed> {
        val q = "?orden=" + orden.param + (if (cursor != null) "&cursor=" + enc(cursor) else "")
        val r = llamar("GET", "/v1/feed$q", parsear = ::paginaFeed)
        return if (r is R.Error && r.tokenInservible) llamar("GET", "/v1/feed$q", conToken = false, parsear = ::paginaFeed) else r
    }

    /** Exactamente uno de `deezer` / `enlace` en el cuerpo. Puede tardar: el servidor convierte la canción fuera. */
    fun publicar(texto: String, origen: OrigenCancion): R<ItemFeed> {
        val cuerpo = JSONObject().put("texto", texto)
        when (origen) {
            is OrigenCancion.Deezer -> cuerpo.put("deezer", origen.id)
            is OrigenCancion.Enlace -> cuerpo.put("enlace", origen.url)
        }
        return llamar("POST", "/v1/publicaciones", cuerpo, tiempoMs = TIMEOUT_LARGO_MS) { itemFeed(it.getJSONObject("publicacion")) }
    }

    fun borrarPublicacion(id: String): R<Unit> = llamar("DELETE", "/v1/publicaciones/${enc(id)}") { }

    /** [si] = dar el corazón (POST) o quitarlo (DELETE, sin cuerpo). */
    fun corazon(id: String, si: Boolean): R<EstadoCorazon> =
        llamar(if (si) "POST" else "DELETE", "/v1/publicaciones/${enc(id)}/corazon") { EstadoCorazon(it.getInt("corazones"), it.getBoolean("corazon")) }

    /** Quién dio corazón: solo lo contesta el servidor al autor. */
    fun quienes(id: String): R<List<String>> = llamar("GET", "/v1/publicaciones/${enc(id)}/corazones") { it.cadenas("nombres") }

    fun mias(): R<List<ItemFeed>> = llamar("GET", "/v1/publicaciones/mias") { j -> j.getJSONArray("items").objetos().map { itemFeed(it) } }

    fun denunciar(tipo: String, objetivo: String, motivo: String): R<Unit> =
        llamar("POST", "/v1/denuncias", JSONObject().put("tipo", tipo).put("objetivo", objetivo).put("motivo", motivo)) { }

    fun borrarCuenta(): R<Unit> = llamar("DELETE", "/v1/cuenta") { }

    // ---- fontanería ----

    private fun <T> llamar(
        metodo: String, ruta: String, cuerpo: JSONObject? = null, conToken: Boolean = true, tiempoMs: Int? = null,
        parsear: (JSONObject) -> T
    ): R<T> {
        val cab = linkedMapOf("Accept" to "application/json")
        if (cuerpo != null) cab["Content-Type"] = "application/json"
        if (tiempoMs != null) cab[CABECERA_TIMEOUT] = tiempoMs.toString()
        if (conToken) token()?.takeIf { it.isNotBlank() }?.let { cab["Authorization"] = "Bearer $it" }
        val r = try {
            red(metodo, ServidorMusica.url(ruta), cuerpo?.toString(), cab)
        } catch (e: Exception) {
            return R.Error("red", 0, null)
        }
        // Un 204 (borrar una publicación) no trae cuerpo: es un éxito, no una respuesta rota.
        val json = if (r.status == 204) JSONObject() else try { JSONObject(r.cuerpo) } catch (e: JSONException) { null }
        if (r.status == 202) return R.Error("pendiente", 202, json) // lo traduce comprobar()
        if (r.status !in 200..299) {
            return R.Error(json?.optString("error")?.takeIf { it.isNotEmpty() } ?: "http_${r.status}", r.status, json)
        }
        if (json == null) return R.Error("respuesta", r.status, null)
        return try { R.Ok(parsear(json)) } catch (e: JSONException) { R.Error("respuesta", r.status, json) }
    }

    private fun cancionFeed(j: JSONObject) = CancionFeed(
        j.getString("id"), j.getString("titulo"), j.cadenaONull("artista"), j.cadenaONull("portada"),
        j.cadenaONull("spotify"), j.cadenaONull("youtube"), if (j.isNull("deezer")) null else j.getLong("deezer")
    )

    // `corazon` y `mia` solo vienen con token: sin él, falso.
    private fun itemFeed(j: JSONObject) = ItemFeed(
        j.getString("id"), j.getString("autor"), j.getString("texto"), j.getLong("creada"), j.getLong("caduca"),
        j.getInt("corazones"), j.optBoolean("corazon", false), j.optBoolean("mia", false), cancionFeed(j.getJSONObject("cancion"))
    )

    private fun paginaFeed(j: JSONObject) = PaginaFeed(
        j.getJSONArray("items").objetos().map { itemFeed(it) },
        j.cadenaONull("cursor"),
        j.optJSONObject("masQuerida")?.let { m ->
            MasQuerida(m.getString("semana"), m.getString("autor"), m.getString("texto"), m.getInt("corazones"), cancionFeed(m.getJSONObject("cancion")))
        }
    )

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    companion object {
        internal const val MAX_CUERPO = 2 * 1024 * 1024
        private const val TIMEOUT_MS = 10_000

        /**
         * Publicar una canción la convierte fuera en el servidor: pueden tardar
         * más que el resto. Un tiempo agotado antes de que acabe no dice si se creó.
         */
        internal const val TIMEOUT_LARGO_MS = 30_000

        /** Cabecera interna (no sale a la red): el tiempo de esta llamada, para [redReal]. */
        const val CABECERA_TIMEOUT = "X-Foroplus-Timeout-Ms"

        /**
         * La red de verdad. Sin redirecciones (el servidor no redirige; seguir una llevaría el
         * `Authorization` a otro sitio), sin tocar `CookieHandler` y sin cabecera `Cookie`, y con
         * el cuerpo cortado a 2 MB. Solo habla con [ServidorMusica.aceptada].
         */
        fun redReal(metodo: String, url: String, cuerpo: String?, cabeceras: Map<String, String>): Respuesta =
            redConGuarda(metodo, url, cuerpo, cabeceras, ServidorMusica::aceptada)

        /** [aceptada] es inyectable solo para probar la red contra un servidor local. */
        internal fun redConGuarda(
            metodo: String, url: String, cuerpo: String?, cabeceras: Map<String, String>,
            aceptada: (String) -> Boolean
        ): Respuesta {
            require(aceptada(url)) { "dirección no permitida" }
            val c = URL(url).openConnection() as HttpURLConnection
            try {
                c.requestMethod = metodo
                val tiempo = cabeceras.entries.firstOrNull { it.key == CABECERA_TIMEOUT }?.value?.toIntOrNull() ?: TIMEOUT_MS
                c.connectTimeout = minOf(tiempo, TIMEOUT_MS)
                c.readTimeout = tiempo
                c.instanceFollowRedirects = false
                c.useCaches = false
                for ((k, v) in cabeceras) if (!k.equals("Cookie", true) && k != CABECERA_TIMEOUT) c.setRequestProperty(k, v)
                // Nunca cuerpo en un DELETE: algunas versiones de Android lanzan "DELETE does not
                // support writing". Quien necesite mandar algo con DELETE lo manda en la URL.
                if (cuerpo != null && !metodo.equals("DELETE", true)) {
                    c.doOutput = true
                    c.outputStream.use { it.write(cuerpo.toByteArray(Charsets.UTF_8)) }
                }
                val hayAuth = cabeceras.keys.any { it.equals("Authorization", true) }
                val status = try {
                    c.responseCode
                } catch (e: java.io.IOException) {
                    return respuestaDeFallo(e, hayAuth) ?: throw e
                }
                val flujo = if (status >= 400) c.errorStream else c.inputStream
                return Respuesta(status, flujo?.use { leerTope(it) } ?: "")
            } finally {
                c.disconnect()
            }
        }

        /**
         * Android lanza "No authentication challenges found" al leer el código de un 401 sin
         * `WWW-Authenticate`. El servidor ya lo manda, pero por si acaso (proxies, versiones) eso
         * es, para la app, un 401 `token`: el token que mandamos no vale. Null = otro fallo, que siga.
         */
        internal fun respuestaDeFallo(e: java.io.IOException, hayAuth: Boolean): Respuesta? =
            if (hayAuth && e.message?.contains("authentication challenge", ignoreCase = true) == true)
                Respuesta(401, """{"error":"token"}""") else null

        private fun leerTope(f: java.io.InputStream): String {
            val sal = java.io.ByteArrayOutputStream()
            val buf = ByteArray(8192)
            while (sal.size() < MAX_CUERPO) {
                val n = f.read(buf, 0, minOf(buf.size, MAX_CUERPO - sal.size()))
                if (n < 0) break
                sal.write(buf, 0, n)
            }
            return sal.toString("UTF-8")
        }
    }
}

private fun JSONObject.cadenaONull(k: String): String? = if (isNull(k)) null else getString(k)
private fun JSONObject.intONull(k: String): Int? = if (isNull(k)) null else getInt(k)
private fun JSONObject.cadenas(k: String): List<String> =
    optJSONArray(k)?.let { a -> (0 until a.length()).map { a.getString(it) } } ?: emptyList()
private fun JSONArray.objetos(): List<JSONObject> = (0 until length()).map { getJSONObject(it) }
