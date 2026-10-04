package com.fcplus.forocoches

import android.content.Context
import android.content.SharedPreferences

/**
 * El caché de ignorados es de la cuenta (en FC los ignorados ya van por cuenta): sin separarlo
 * se filtraría con la lista de otra persona. El fichero se resuelve por uid activo en cada
 * acceso (ver [prefs] más abajo).
 */
class IgnoreListRepository(context: Context) {

    private val ctx = context.applicationContext

    /**
     * El fichero se resuelve EN CADA ACCESO, no al construir: el uid de la cuenta activa no se
     * conoce hasta que el usuario entra, y cambia al cambiar de cuenta. Resolverlo aquí evita
     * tener que recrear este repositorio (y volver a cablear a quien lo usa) en cada cambio, y
     * con ello la clase de bug "el repositorio se quedó apuntando a la cuenta anterior".
     * `getSharedPreferences` está cacheado por nombre en Android, así que esto es barato.
     */
    private val prefs: SharedPreferences
        get() = ctx.getSharedPreferences(
            ClavesPorCuenta.fichero("fc_filtro", uidActivo()), Context.MODE_PRIVATE
        )

    /** Uid de la cuenta activa, leído de disco: lo escribe MainActivity y lo leen también los
     *  procesos en segundo plano, que no ven la memoria de la app. */
    private fun uidActivo(): String =
        ctx.getSharedPreferences("shell_prefs", Context.MODE_PRIVATE)
            .getString("uid_activo", "") ?: ""

    fun getIgnoredUsers(): List<String> =
        prefs.getStringSet("ignored_users", emptySet())?.toList() ?: emptyList()

    fun setIgnoredUsers(users: List<String>) {
        prefs.edit()
            .putStringSet("ignored_users", users.toSet())
            .putLong("last_updated", System.currentTimeMillis())
            .apply()
    }

    fun getLastUpdated(): Long = prefs.getLong("last_updated", 0L)
}
