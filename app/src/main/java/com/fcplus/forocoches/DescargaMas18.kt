package com.fcplus.forocoches

import android.content.SharedPreferences
import java.net.HttpURLConnection
import java.net.URL

data class ResultadoMas18(val lectura: LecturaMas18, val deCache: Boolean)

/**
 * Baja una página de la sección +18 de GitHub. HTTP nativo: no es HTML de FC, es un JSON público
 * (como fc_config.json). Guarda la última página BUENA de cada número: sin red, o si GitHub
 * devuelve un error en HTML, se enseña esa en vez de una lista vacía.
 *
 * Bloquea: llamarla desde un hilo de fondo.
 */
class DescargaMas18(
    private val cache: SharedPreferences,
    private val bajar: (String) -> String? = ::bajarHttp,
) {
    fun pagina(cfg: ConfigMas18, n: Int): ResultadoMas18 {
        val url = cfg.urlPagina(n)
        if (!FuentesPermitidas.aceptada(url)) {
            return ResultadoMas18(LecturaMas18.Mal("Fuente de la lista no permitida"), false)
        }
        val cuerpo = try { bajar(url) } catch (_: Exception) { null }
        if (cuerpo != null) {
            val l = PaginaMas18Parser.leer(cuerpo)
            if (l is LecturaMas18.Bien) {
                cache.edit().putString(clave(n), cuerpo).apply()
                return ResultadoMas18(l, false)
            }
            guardada(n)?.let { return it }
            return ResultadoMas18(l, false)
        }
        return guardada(n) ?: ResultadoMas18(LecturaMas18.Mal("Sin conexión: no se ha podido bajar la lista"), false)
    }

    private fun guardada(n: Int): ResultadoMas18? {
        val s = cache.getString(clave(n), null) ?: return null
        val l = PaginaMas18Parser.leer(s)
        return if (l is LecturaMas18.Bien) ResultadoMas18(l, true) else null
    }

    private fun clave(n: Int) = "pagina_$n"

    companion object {
        const val PREFS = "mas18_cache"

        fun bajarHttp(url: String): String? {
            val c = URL(url).openConnection() as HttpURLConnection
            return try {
                c.connectTimeout = 8_000
                c.readTimeout = 8_000
                // No permitir redirecciones: un redirect podría sacarnos del repo permitido.
                c.instanceFollowRedirects = false
                if (c.responseCode !in 200..299) null
                else c.inputStream.bufferedReader(Charsets.UTF_8).readText()
            } finally {
                c.disconnect()
            }
        }
    }
}
