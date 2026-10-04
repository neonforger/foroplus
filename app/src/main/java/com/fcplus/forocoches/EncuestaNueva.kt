package com.fcplus.forocoches

import org.json.JSONArray
import org.json.JSONObject

/**
 * Una encuesta para un hilo nuevo, tal y como la va a pedir FC.
 *
 * En FC son DOS envíos seguidos (medido por CDP el 2026-09-24 en Pruebas): el hilo con
 * `postpoll=yes` y `polloptions=N`, que redirige a `poll.php?t=T&polloptions=N`, y ahí el
 * formulario de la encuesta (`question`, `options[1..N]`, `timeout` en días, `multiple`,
 * `public`). Lo hace el motor (`fcCreateThread`); aquí solo se decide si lo escrito vale,
 * ANTES de publicar nada: un hilo creado con una encuesta que luego FC rechaza se queda sin
 * encuesta y ya no hay vuelta atrás.
 */
data class EncuestaNueva(
    val pregunta: String,
    val opciones: List<String>,
    val multiple: Boolean,
    val publica: Boolean,
    /** Días que dura; 0 = no se cierra nunca (lo que FC entiende por timeout vacío). */
    val dias: Int
) {
    fun json(): String = JSONObject()
        .put("pregunta", pregunta)
        .put("opciones", JSONArray(opciones))
        .put("multiple", multiple)
        .put("publica", publica)
        .put("dias", dias)
        .toString()

    companion object {
        const val MIN_OPCIONES = 2
        /** El máximo de fábrica de vBulletin (`maxpolloptions`). */
        const val MAX_OPCIONES = 10
        const val MAX_DIAS = 365

        /** Resultado de validar: la encuesta lista, o el motivo en lenguaje de usuario. */
        sealed class Validacion {
            data class Ok(val encuesta: EncuestaNueva) : Validacion()
            data class Error(val motivo: String) : Validacion()
        }

        /**
         * @param opciones las cajas tal cual; las vacías se ignoran (dejar una fila en blanco
         *   no es un error, es no haberla usado).
         * @param dias el texto de la caja de días; vacío = no caduca.
         */
        fun validar(
            pregunta: String,
            opciones: List<String>,
            multiple: Boolean,
            publica: Boolean,
            dias: String
        ): Validacion {
            val p = pregunta.trim()
            if (p.isEmpty()) return Validacion.Error("Escribe la pregunta de la encuesta")
            val ops = opciones.map { it.trim() }.filter { it.isNotEmpty() }
            if (ops.size < MIN_OPCIONES) return Validacion.Error("La encuesta necesita al menos $MIN_OPCIONES opciones")
            if (ops.size > MAX_OPCIONES) return Validacion.Error("Como mucho $MAX_OPCIONES opciones")
            if (ops.map { it.lowercase() }.toSet().size != ops.size) {
                return Validacion.Error("Hay opciones repetidas")
            }
            val d = dias.trim()
            val n = if (d.isEmpty()) 0 else d.toIntOrNull() ?: -1
            if (n < 0 || n > MAX_DIAS) return Validacion.Error("Los días tienen que ser un número entre 0 y $MAX_DIAS")
            return Validacion.Ok(EncuestaNueva(p, ops, multiple, publica, n))
        }
    }
}
