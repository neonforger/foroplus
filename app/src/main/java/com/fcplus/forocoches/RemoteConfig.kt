package com.fcplus.forocoches

import android.content.Context
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Configuración de selectores "remota": permite arreglar roturas cuando FC cambia su HTML
 * **sin publicar una versión nueva** en Play Store. Es DATOS (selectores), no código
 * ejecutable, así que cumple las políticas de Play Store.
 *
 * Estrategia: usa la copia cacheada (o el JSON bundleado en assets como fallback) al
 * instante; refresca en 2º plano para la próxima carga. Si el remoto está caído o es
 * inválido, sigue funcionando con lo que tenga.
 */
object RemoteConfig {

    // Repo PÚBLICO y propio, creado para esto. Editar ese fichero y hacer push cambia la
    // config y el aviso de versión de todos los usuarios sin publicar nada en Play.
    // Vive en la cuenta del proyecto desde la 49 (2026-10-06); las versiones hasta la 48
    // leen la copia de `albertdom/foroplus-config`, que hay que mantener IGUAL mientras
    // quede gente en ellas.
    private const val REMOTE_URL =
        "https://raw.githubusercontent.com/neonforger/foroplus-config/main/fc_config.json"
    private const val PREFS = "fc_remote_config"
    private const val KEY_JSON = "json"

    /** Config ya parseada, o null si no hay nada usable. */
    fun cached(context: Context): JSONObject? = try {
        JSONObject(cachedJson(context))
    } catch (e: Exception) {
        null
    }

    /** JSON a inyectar en el WebView: cacheado si existe y es válido, si no el bundleado. */
    fun cachedJson(context: Context): String {
        val cached = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_JSON, null)
        if (cached != null && isValid(cached)) return cached
        return bundledJson(context)
    }

    /**
     * ¿Ha terminado ya el refresco de este arranque? Importa porque el aviso de versión usa
     * las notas del fichero: si se leyera la copia de la vez ANTERIOR, el primer arranque tras
     * publicar enseñaría el aviso sin novedades y el bueno solo a la segunda — cuando media
     * gente ya habría actualizado.
     */
    @Volatile
    private var refrescoHecho = false
    private val alTerminar = java.util.Collections.synchronizedList(ArrayList<() -> Unit>())

    /**
     * Ejecuta [cb] en cuanto la config recién bajada esté guardada, o al agotarse [timeoutMs]
     * si no hay red. El tope es imprescindible: sin él, un remoto caído retrasaría el arranque.
     */
    fun cuandoEsteFresca(timeoutMs: Long, handler: android.os.Handler, cb: () -> Unit) {
        if (refrescoHecho) { cb(); return }
        val yaDisparado = java.util.concurrent.atomic.AtomicBoolean(false)
        val disparar = { if (yaDisparado.compareAndSet(false, true)) handler.post(cb) }
        alTerminar.add(disparar)
        handler.postDelayed({ disparar() }, timeoutMs)
    }

    private fun avisarTerminado() {
        refrescoHecho = true
        val copia = synchronized(alTerminar) { val c = alTerminar.toList(); alTerminar.clear(); c }
        copia.forEach { runCatching { it() } }
    }

    /** Descarga el config remoto en 2º plano y lo cachea para la próxima carga. */
    fun refresh(context: Context) {
        Thread {
            try {
                val conn = URL(REMOTE_URL).openConnection() as HttpURLConnection
                conn.connectTimeout = 8_000
                conn.readTimeout = 8_000
                val codigo = conn.responseCode
                var guardado = false
                if (codigo in 200..299) {
                    val body = conn.inputStream.bufferedReader(Charsets.UTF_8).readText()
                    if (isValid(body)) {
                        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                            .putString(KEY_JSON, body).apply()
                        guardado = true
                    }
                }
                // Sin esta traza, un fallo aquí es invisible: la app sigue con la copia vieja
                // y nadie se entera de que la config remota lleva semanas sin llegar.
                android.util.Log.d("FC_SHELL", "config remota: http=$codigo guardada=$guardado")
                conn.disconnect()
            } catch (e: Exception) {
                // sin red / remoto caído -> seguimos con lo cacheado/bundleado
                android.util.Log.d("FC_SHELL", "config remota falló: $e")
            } finally {
                // Pase lo que pase: quien espere la config no puede quedarse colgado.
                avisarTerminado()
            }
        }.start()
    }

    private fun bundledJson(context: Context): String = try {
        context.assets.open("fc_config.json").bufferedReader(Charsets.UTF_8).readText()
    } catch (e: Exception) {
        "{}"
    }

    /** Validación mínima: JSON objeto con "version". Evita cachear basura/HTML de error. */
    private fun isValid(s: String): Boolean = try {
        JSONObject(s).has("version")
    } catch (e: Exception) {
        false
    }
}
