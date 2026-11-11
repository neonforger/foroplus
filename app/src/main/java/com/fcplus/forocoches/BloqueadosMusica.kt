package com.fcplus.forocoches

import android.content.SharedPreferences
import org.json.JSONArray

/**
 * Los nombres que ESTE usuario ha decidido no ver en el feed de Música ("No ver a X"). Solo
 * esconde filas en su móvil: no avisa a nadie ni toca el servidor.
 *
 * Es del móvil y no de la cuenta de FC: quien se quita de en medio a alguien no quiere volver a
 * verlo porque cambie de cuenta. Tope [MAX]: al pasarse se olvida el más antiguo (una lista que
 * crece sin límite acabaría pesando en cada pintado del feed). Los nombres de FC no distinguen
 * mayúsculas, y aquí tampoco.
 */
class BloqueadosMusica(private val prefs: SharedPreferences) {

    fun nombres(): List<String> = try {
        val j = JSONArray(prefs.getString(CLAVE, null) ?: "[]")
        (0 until j.length()).map { j.getString(it) }
    } catch (_: Exception) {
        emptyList()  // un valor roto no puede dejar el feed sin pintar
    }

    fun contiene(nombre: String): Boolean = nombres().any { it.equals(nombre, ignoreCase = true) }

    fun bloquear(nombre: String) {
        val n = nombre.trim()
        if (n.isEmpty() || contiene(n)) return
        val lista = (nombres() + n).takeLast(MAX)
        prefs.edit().putString(CLAVE, JSONArray(lista).toString()).apply()
    }

    fun desbloquear(nombre: String) {
        val lista = nombres().filterNot { it.equals(nombre, ignoreCase = true) }
        prefs.edit().putString(CLAVE, JSONArray(lista).toString()).apply()
    }

    companion object {
        const val MAX = 200
        const val FICHERO = "musica_bloqueados"
        private const val CLAVE = "nombres"
    }
}
