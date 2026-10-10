package com.fcplus.forocoches

import org.json.JSONArray
import org.json.JSONObject

/** Una cuenta de ForoCoches guardada en la app. El [uid] es su identidad. */
data class Cuenta(val uid: String, val nombre: String, val avatar: String)

/**
 * Las cuentas guardadas y las reglas de la lista.
 *
 * Se identifica por **uid numérico** y no por nombre: el uid no cambia nunca y vale tal cual
 * para nombres de fichero. Sale de `loggedinuser` del quick-reply o de `usercp.php` (medido el
 * 2026-09-16; el gotcha 1 —FC enmascara los uid a `u=0`— aplica a los perfiles de OTROS, no al
 * propio).
 *
 * Puro a propósito: aquí no se tocan preferencias ni cookies, solo la lista.
 */
object Cuentas {

    /** Tope decidido por el dueño. Suficiente para cualquiera y deja la hoja legible. */
    const val TOPE = 5

    fun leer(json: String): List<Cuenta> {
        if (json.isBlank()) return emptyList()
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                val uid = o.optString("uid").trim()
                if (uid.isEmpty()) null
                else Cuenta(uid, o.optString("nombre"), o.optString("avatar"))
            }
        } catch (_: Exception) {
            // Un JSON roto no puede dejar la app sin cuentas Y sin arrancar: lista vacía y la
            // migración la volverá a crear con la sesión que haya.
            emptyList()
        }
    }

    fun guardar(cuentas: List<Cuenta>): String {
        val arr = JSONArray()
        for (c in cuentas) {
            arr.put(JSONObject().put("uid", c.uid).put("nombre", c.nombre).put("avatar", c.avatar))
        }
        return arr.toString()
    }

    /**
     * Añade [nueva], o **actualiza en su sitio** si ese uid ya estaba (volver a entrar refresca
     * el nombre y el avatar sin duplicar ni reordenar). Actualizar se permite aunque esté lleno.
     */
    fun anadir(actuales: List<Cuenta>, nueva: Cuenta): List<Cuenta> {
        if (nueva.uid.isEmpty()) return actuales
        val i = actuales.indexOfFirst { it.uid == nueva.uid }
        if (i >= 0) return actuales.toMutableList().also { it[i] = nueva }
        if (!hayHueco(actuales)) return actuales
        return actuales + nueva
    }

    fun quitar(actuales: List<Cuenta>, uid: String): List<Cuenta> =
        actuales.filterNot { it.uid == uid }

    fun hayHueco(actuales: List<Cuenta>): Boolean = actuales.size < TOPE

    /** Uid al que pasar tras quitar [uidQuitado], o "" si no queda ninguna (→ cerrar sesión). */
    fun siguienteTras(actuales: List<Cuenta>, uidQuitado: String): String =
        quitar(actuales, uidQuitado).firstOrNull()?.uid ?: ""

    /**
     * Qué cuenta enseñar en la cabecera del composer, o `null` si no hay nada fiable que decir.
     *
     * La cabecera es la respuesta a "con qué cuenta voy a publicar esto", que es lo que pidió
     * un tester tras fotografiar el panel sin ninguna señal. Por eso el criterio es **callar
     * antes que mentir**: decir la cuenta equivocada encima de una caja de texto es peor que no
     * decir nada, porque invita a confiar.
     *
     * Devuelve `null` cuando:
     * - [cambiando] — hay un cambio de cuenta en curso y FC todavía no ha confirmado quién
     *   eres (mismo criterio que la barra de respuesta rápida);
     * - [uidActivo] está vacío (sin sesión);
     * - ese uid no está en la lista, o está pero **sin nombre**: "como @" no informa de nada.
     *
     * Ojo con la última: se enseña **aunque solo haya UNA cuenta guardada**, al revés que el
     * avatar de la barra rápida. Ahí se esconde porque ocupa sitio dentro del hilo; aquí no
     * cuesta alto, y el día que se te caduque la sesión y entres con otra cuenta, el aviso ya
     * está puesto.
     */
    fun paraComposer(actuales: List<Cuenta>, uidActivo: String, cambiando: Boolean): Cuenta? {
        if (cambiando || uidActivo.isEmpty()) return null
        return actuales.firstOrNull { it.uid == uidActivo }?.takeIf { it.nombre.isNotBlank() }
    }

    /** Lo que se lee bajo el título del composer. */
    fun etiquetaComposer(cuenta: Cuenta): String = "como @${cuenta.nombre}"

    /**
     * La línea entera bajo el título del composer: con qué cuenta y en qué hilo ("como @x · en
     * Mi hilo"). Lo que falte no se dice; el hilo, sin el "- Página N" de vBulletin.
     */
    fun subtituloComposer(etiquetaCuenta: String?, tituloHilo: String): String {
        val hilo = CompartirFC.tituloLimpio(tituloHilo)
        return listOfNotNull(
            etiquetaCuenta?.takeIf { it.isNotBlank() },
            hilo.takeIf { it.isNotBlank() }?.let { "en $it" }
        ).joinToString(" · ")
    }
}
