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
data class Yo(val nombre: String, val diasCuenta: Int?, val puedeVotar: Boolean, val votosRestantes: Int, val propuestasRestantes: Int)
data class CancionPub(val id: String, val titulo: String, val artista: String?, val portada: String?, val youtube: String?, val spotify: String?)
data class Semana(
    val actualId: String, val cierra: Long, val actual: List<CancionPub>, val votadas: Set<String>,
    val anteriorId: String?, val cancionSemana: CancionPub?, val top: List<Pair<CancionPub, Int>>
)
data class Propuesta(val cancion: CancionPub, val yaEstaba: Boolean, val propuestasRestantes: Int)
data class Voto(val votada: Boolean, val votosRestantes: Int)
data class PlaylistResumen(val id: String, val nombre: String, val autor: String, val canciones: Int, val guardados: Int, val portada: String?)
data class PaginaPlaylists(val lista: List<PlaylistResumen>, val siguiente: String?)
data class PlaylistPub(val id: String, val nombre: String, val autor: String, val guardados: Int, val canciones: List<CancionPub>)
data class Publicada(val id: String, val canciones: Int, val fallidas: List<String>)

/**
 * Cliente del servidor de Música. Bloqueante: quien lo llame lo hace desde un hilo de fondo.
 *
 * - [token] se pide EN CADA llamada (no al construir): el token es de la cuenta de FC activa en
 *   ese momento (`{ tokens.de(uidActivo()) }`). Sin token no se manda `Authorization`.
 * - Nunca manda cookies de FC: ni aquí ni en [redReal].
 * - El servidor da 401 `token` / 403 `baneada` (no la vista pública) si el token que llega es
 *   malo; eso sale como [R.Error.tokenInservible] para que quien llama borre el token y, en
 *   `semana()`, reintente sin él.
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
        Yo(it.getString("nombre"), it.intONull("diasCuenta"), it.getBoolean("puedeVotar"), it.getInt("votosRestantes"), it.getInt("propuestasRestantes"))
    }

    // ---- canción de la semana ----

    /** Con token válido trae `votada` por canción; con token malo el servidor da 401/403 (ver la clase). */
    fun semana(): R<Semana> = llamar("GET", "/v1/semana") { j ->
        val act = j.getJSONObject("actual")
        val canciones = act.getJSONArray("canciones").objetos()
        val ant = j.optJSONObject("anterior")
        Semana(
            actualId = act.getString("id"),
            cierra = act.getLong("cierra"),
            actual = canciones.map { cancion(it) },
            votadas = canciones.filter { it.optBoolean("votada", false) }.map { it.getString("id") }.toSet(),
            anteriorId = ant?.getString("id"),
            cancionSemana = ant?.optJSONObject("cancionSemana")?.let { cancion(it) },
            top = ant?.optJSONArray("top")?.objetos()?.map { cancion(it) to it.optInt("votos", 0) } ?: emptyList()
        )
    }

    fun proponer(enlace: String): R<Propuesta> = llamar("POST", "/v1/semana/proponer", JSONObject().put("enlace", enlace)) {
        Propuesta(cancion(it.getJSONObject("cancion")), it.getBoolean("yaEstaba"), it.getInt("propuestasRestantes"))
    }

    /** [si] = votar (POST) o quitar el voto (DELETE, también con cuerpo). */
    fun votar(cancion: String, si: Boolean): R<Voto> =
        // Quitar el voto va con la canción en la URL y sin cuerpo (ver redConGuarda).
        (if (si) llamar("POST", "/v1/semana/voto", JSONObject().put("cancion", cancion)) { j -> Voto(j.getBoolean("votada"), j.getInt("votosRestantes")) }
        else llamar("DELETE", "/v1/semana/voto?cancion=" + enc(cancion)) { j -> Voto(j.getBoolean("votada"), j.getInt("votosRestantes")) })

    // ---- playlists públicas (listar y ver son públicas: no mandan token) ----

    fun playlists(orden: String, cursor: String?): R<PaginaPlaylists> {
        val q = "?orden=" + enc(orden) + (if (cursor != null) "&cursor=" + enc(cursor) else "")
        return llamar("GET", "/v1/playlists$q", conToken = false) { j ->
            PaginaPlaylists(
                j.getJSONArray("playlists").objetos().map {
                    PlaylistResumen(it.getString("id"), it.getString("nombre"), it.getString("autor"),
                        it.getInt("canciones"), it.getInt("guardados"), it.cadenaONull("portada"))
                },
                j.cadenaONull("siguiente")
            )
        }
    }

    fun playlist(id: String): R<PlaylistPub> = llamar("GET", "/v1/playlists/${enc(id)}", conToken = false) { j ->
        PlaylistPub(j.getString("id"), j.getString("nombre"), j.getString("autor"), j.getInt("guardados"),
            j.getJSONArray("canciones").objetos().map { cancion(it) })
    }

    fun publicar(nombre: String, enlaces: List<String>): R<Publicada> =
        llamar("POST", "/v1/playlists", JSONObject().put("nombre", nombre).put("enlaces", JSONArray(enlaces))) {
            Publicada(it.getString("id"), it.getInt("canciones"), it.cadenas("fallidas"))
        }

    fun anadirAPublicada(id: String, enlaces: List<String>): R<Publicada> =
        llamar("POST", "/v1/playlists/${enc(id)}/canciones", JSONObject().put("enlaces", JSONArray(enlaces))) {
            Publicada(id, it.getInt("canciones"), it.cadenas("fallidas"))
        }

    fun retirar(id: String): R<Unit> = llamar("DELETE", "/v1/playlists/${enc(id)}") { }

    /** Devuelve el nuevo recuento de guardados. */
    fun guardarPublicada(id: String): R<Int> = llamar("POST", "/v1/playlists/${enc(id)}/guardar") { it.getInt("guardados") }

    fun denunciar(tipo: String, objetivo: String, motivo: String): R<Unit> =
        llamar("POST", "/v1/denuncias", JSONObject().put("tipo", tipo).put("objetivo", objetivo).put("motivo", motivo)) { }

    fun borrarCuenta(): R<Unit> = llamar("DELETE", "/v1/cuenta") { }

    // ---- fontanería ----

    private fun <T> llamar(
        metodo: String, ruta: String, cuerpo: JSONObject? = null, conToken: Boolean = true,
        parsear: (JSONObject) -> T
    ): R<T> {
        val cab = linkedMapOf("Accept" to "application/json")
        if (cuerpo != null) cab["Content-Type"] = "application/json"
        if (conToken) token()?.takeIf { it.isNotBlank() }?.let { cab["Authorization"] = "Bearer $it" }
        val r = try {
            red(metodo, ServidorMusica.url(ruta), cuerpo?.toString(), cab)
        } catch (e: Exception) {
            return R.Error("red", 0, null)
        }
        val json = try { JSONObject(r.cuerpo) } catch (e: JSONException) { null }
        if (r.status == 202) return R.Error("pendiente", 202, json) // lo traduce comprobar()
        if (r.status !in 200..299) {
            return R.Error(json?.optString("error")?.takeIf { it.isNotEmpty() } ?: "http_${r.status}", r.status, json)
        }
        if (json == null) return R.Error("respuesta", r.status, null)
        return try { R.Ok(parsear(json)) } catch (e: JSONException) { R.Error("respuesta", r.status, json) }
    }

    private fun cancion(j: JSONObject) = CancionPub(
        j.getString("id"), j.getString("titulo"), j.cadenaONull("artista"), j.cadenaONull("portada"),
        j.cadenaONull("youtube"), j.cadenaONull("spotify")
    )

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    companion object {
        internal const val MAX_CUERPO = 2 * 1024 * 1024
        private const val TIMEOUT_MS = 10_000

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
                c.connectTimeout = TIMEOUT_MS
                c.readTimeout = TIMEOUT_MS
                c.instanceFollowRedirects = false
                c.useCaches = false
                for ((k, v) in cabeceras) if (!k.equals("Cookie", true)) c.setRequestProperty(k, v)
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
