package com.fcplus.forocoches

import org.json.JSONObject

/** Qué hay que pedirle al foro con lo que se ha escrito en el buscador. */
sealed class PeticionBusqueda {
    /** Palabras, y si hay usuario, solo en lo suyo: `fcSearch` con `searchuser`. */
    data class Palabras(val palabras: String, val usuario: String, val porTitulos: Boolean) : PeticionBusqueda()

    /** Usuario sin palabras: todo lo suyo, que es justo lo que ya enseña su ficha. */
    data class TodoDe(val usuario: String, val porTitulos: Boolean) : PeticionBusqueda()

    /** No se puede buscar así; [motivo] es lo que se le dice a quien busca. */
    data class NoVale(val motivo: String) : PeticionBusqueda()
}

/**
 * Buscar por usuario desde el buscador, como el formulario de FC (`searchuser`).
 *
 * Medido por CDP el 2026-10-07: FC combina `query` con `searchuser` (+ `exactname=1`, que el
 * formulario no trae pero acepta) y filtra de verdad por las dos cosas; por títulos hay que
 * mandar además `starteronly=1` para que salgan los hilos que ABRIÓ. Lo de hablar con el foro
 * vive en `extractor.js` (`fcSearch`, `fcSugerirUsuarios`); aquí solo se decide.
 */
object BusquedaPorUsuario {

    /**
     * Mínimo de caracteres de la búsqueda. No es nuestro: FC rechaza menos de 3 ("Para
     * búsquedas de menos de 3 caracteres ... puedes usar el motor de búsqueda Google"), y su
     * autocompletado de nombres tampoco contesta antes de 3 (`min_chars=3`).
     */
    const val MINIMO = 3

    /** El nombre tal y como lo quiere FC: sin la `@` que la gente escribe por costumbre. */
    fun limpiarUsuario(texto: String): String = texto.trim().trimStart('@').trim()

    fun decidir(palabras: String, usuario: String, porTitulos: Boolean): PeticionBusqueda {
        val p = palabras.trim()
        val u = limpiarUsuario(usuario)
        if (p.isEmpty() && u.isEmpty()) return PeticionBusqueda.NoVale("Escribe qué buscar o de quién")
        if (p.isEmpty()) return PeticionBusqueda.TodoDe(u, porTitulos)
        if (p.length < MINIMO) return PeticionBusqueda.NoVale("Escribe al menos $MINIMO caracteres")
        return PeticionBusqueda.Palabras(p, u, porTitulos)
    }

    /** Cabecera de los resultados. Sin usuario, la de siempre. */
    fun cabecera(palabras: String, usuario: String, porMensajes: Boolean): String {
        val base = "\"$palabras\""
        val u = limpiarUsuario(usuario)
        return when {
            porMensajes && u.isEmpty() -> "$base · mensajes"
            porMensajes -> "$base · mensajes de @$u"
            u.isEmpty() -> base
            else -> "$base · hilos de @$u"
        }
    }

    /**
     * Qué búsqueda es, para saber al volver atrás si lo cargado sigue siendo ESA. Sin usuario es
     * la palabra a secas, como antes; con usuario, otra búsqueda distinta aunque la palabra sea
     * la misma.
     */
    fun clave(palabras: String, usuario: String): String {
        val u = limpiarUsuario(usuario)
        return if (u.isEmpty()) palabras else "$palabras\u0000@$u"
    }

    fun pedirSugerencias(texto: String): Boolean = limpiarUsuario(texto).length >= MINIMO

    /**
     * Los nombres que propone el foro para lo que hay escrito AHORA. Escribir rápido lanza varias
     * peticiones y pueden llegar desordenadas: una que no es de lo escrito devuelve null y se
     * tira. También null si el JSON no se entiende.
     */
    fun sugerencias(json: String, escritoAhora: String): List<String>? {
        return try {
            val o = JSONObject(json)
            if (o.optString("fragment") != limpiarUsuario(escritoAhora)) return null
            val arr = o.optJSONArray("nombres") ?: return emptyList()
            (0 until arr.length()).map { arr.optString(it).trim() }.filter { it.isNotEmpty() }.distinct()
        } catch (_: Exception) {
            null
        }
    }
}
