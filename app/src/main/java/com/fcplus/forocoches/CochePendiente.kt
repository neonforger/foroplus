package com.fcplus.forocoches

import android.content.SharedPreferences
import org.json.JSONException
import org.json.JSONObject

/**
 * El coche que tenía el perfil de FC de [uid] antes de poner el de prueba de la verificación de
 * Música ([VerificacionMusica]). Se guarda en el móvil ANTES de tocar el perfil y se borra cuando
 * FC confirma que vuelve a tenerlo; si la app se cierra a medias, al arrancar con esa cuenta se
 * devuelve. [original] puede ser "": quien no tenía coche se queda sin coche.
 */
data class Pendiente(val uid: String, val original: String, val desde: Long)

object CochePendiente {
    /**
     * Pasado un mes ya no se devuelve: el usuario habrá visto el coche raro y habrá puesto el que
     * quiera, y devolver el de entonces lo pisaría.
     */
    const val CADUCA_MS = 30L * 24 * 60 * 60 * 1000

    fun aJson(p: Pendiente): String =
        JSONObject().put("uid", p.uid).put("original", p.original).put("desde", p.desde).toString()

    /** Null si no hay nada, no se lee o le falta la cuenta o el original. */
    fun deJson(s: String?): Pendiente? {
        if (s.isNullOrBlank()) return null
        return try {
            val o = JSONObject(s)
            val uid = o.optString("uid")
            if (uid.isEmpty() || !o.has("original") || !o.has("desde")) null
            else Pendiente(uid, o.getString("original"), o.getLong("desde"))
        } catch (e: JSONException) {
            null
        }
    }

    /**
     * Solo con la MISMA cuenta activa: con otra, el motor escribiría el coche de una cuenta en el
     * perfil de otra. Un reloj que vuelve atrás (desde en el futuro) cuenta como reciente: ante la
     * duda, se devuelve.
     */
    fun hayQueRestaurar(p: Pendiente?, uidActivo: String?, ahora: Long): Boolean =
        p != null && !uidActivo.isNullOrEmpty() && p.uid == uidActivo && ahora - p.desde < CADUCA_MS
}

/**
 * Dónde se guardan los [Pendiente]: un fichero de preferencias propio (`musica_coche`), una clave
 * por cuenta. Por cuenta y no una sola: si una verificación de la cuenta A se queda a medias y
 * luego se verifica la B, la de B no puede pisar el original de A.
 */
class PendientesCoche(private val prefs: SharedPreferences) {
    fun de(uid: String): Pendiente? =
        if (uid.isEmpty()) null else CochePendiente.deJson(prefs.getString(clave(uid), null))

    /** Con commit() y no apply(): tiene que estar en disco ANTES de que el motor toque el perfil. */
    fun guardar(p: Pendiente) {
        if (p.uid.isEmpty()) return
        prefs.edit().putString(clave(p.uid), CochePendiente.aJson(p)).commit()
    }

    fun borrar(uid: String) {
        if (uid.isEmpty()) return
        prefs.edit().remove(clave(uid)).commit()
    }

    /** El callback que piden [VerificacionMusica] y [VerificacionMusica.restaurar] para [uid]. */
    fun para(uid: String): (Pendiente?) -> Unit = { p -> if (p == null) borrar(uid) else guardar(p) }

    private fun clave(uid: String) = "pendiente_$uid"

    companion object {
        const val FICHERO = "musica_coche"
    }
}
