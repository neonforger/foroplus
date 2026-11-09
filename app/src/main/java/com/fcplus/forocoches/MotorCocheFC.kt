package com.fcplus.forocoches

import android.os.Looper
import org.json.JSONException
import org.json.JSONObject
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * El [MotorCoche] de verdad: `fcLeerCoche`/`fcPonerCoche` de extractor.js, en el WebView motor.
 *
 * `evaluateJavascript` no espera promesas (gotcha): su callback salta al instante con null. Por
 * eso el JS contesta por el puente (`AndroidShell.onCoche` → [alLlegar]) y aquí se espera esa
 * respuesta con un cerrojo, como mucho [esperaMs]. Una petición cada vez; cada una lleva su número
 * y la respuesta de una que ya se dio por perdida se tira.
 *
 * @param evaluar lanza un trozo de JS en el motor (MainActivity: `webView.evaluateJavascript` en el
 *   hilo de la interfaz).
 */
class MotorCocheFC(
    private val evaluar: (String) -> Unit,
    private val esperaMs: Long = 15_000
) {
    private val numero = AtomicInteger()
    private val cerrojo = Any()
    @Volatile private var espera: Espera? = null

    private class Espera(val id: Int) {
        val listo = CountDownLatch(1)
        @Volatile var json: JSONObject? = null
    }

    /** Lo llama el puente, en su propio hilo: suelta a quien espera esa petición. */
    fun alLlegar(json: String) {
        val o = try { JSONObject(json) } catch (e: JSONException) { return }
        val e = espera ?: return
        if (o.optInt("peticion", -1) != e.id) return
        e.json = o
        e.listo.countDown()
    }

    /** El motor para la cuenta [uid]: si la sesión del WebView es de otra, ni lee ni escribe. */
    fun para(uid: String) = DeCuenta(uid)

    inner class DeCuenta(private val uid: String) : MotorCoche {
        /**
         * Por qué falló lo último: "login", "challenge", "cuenta", "fc" o "tiempo"; null si fue
         * bien. La hoja lo usa para decir algo más útil que "sin sesión".
         */
        @Volatile var ultimoError: String? = null
            private set

        override fun leer(): String? {
            val o = pedir { id -> "window.fcLeerCoche&&fcLeerCoche(${JSONObject.quote(uid)},$id)" }
            if (o == null || !o.optBoolean("ok")) {
                ultimoError = o?.optString("error")?.takeIf { it.isNotEmpty() } ?: "tiempo"
                return null
            }
            ultimoError = null
            return o.optString("valor")
        }

        override fun poner(valor: String): Boolean {
            val o = pedir { id ->
                "window.fcPonerCoche&&fcPonerCoche(${JSONObject.quote(valor)},${JSONObject.quote(uid)},$id)"
            }
            if (o == null || !o.optBoolean("ok")) {
                ultimoError = o?.optString("error")?.takeIf { it.isNotEmpty() } ?: "tiempo"
                return false
            }
            val puesto = VerificacionMusica.cochesIguales(o.optString("valor"), valor)
            ultimoError = if (puesto) null else "fc"
            if (!puesto) android.util.Log.w("Musica", "coche no puesto: post=${o.optInt("post")} ${o.optString("detalle")}")
            return puesto
        }
    }

    private fun pedir(js: (Int) -> String): JSONObject? {
        // Bloquea hasta 15 s y la respuesta llega por el puente: desde el hilo de la interfaz
        // congelaría la app (y evaluar() necesita ese hilo libre para lanzar el JS).
        check(Looper.myLooper() != Looper.getMainLooper()) { "MotorCocheFC: nunca desde el hilo de la interfaz" }
        synchronized(cerrojo) {
            val e = Espera(numero.incrementAndGet())
            espera = e
            try {
                evaluar(js(e.id))
                return if (e.listo.await(esperaMs, TimeUnit.MILLISECONDS)) e.json else null
            } finally {
                espera = null
            }
        }
    }

    companion object {
        /**
         * El hilo de todo lo que toca el coche: verificar y devolver el original al arrancar. Uno
         * solo y compartido: así una restauración nunca se cruza con una verificación en marcha
         * (pondría el original en mitad de la comprobación), sino que espera a que acabe y lee lo
         * pendiente entonces. Nunca se apaga: cerrar la hoja no puede cortar el `finally` que
         * devuelve el coche.
         */
        val hilo: ExecutorService = Executors.newSingleThreadExecutor()
    }
}
