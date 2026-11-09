package com.fcplus.forocoches

import android.content.Context
import android.content.SharedPreferences

/**
 * El token de Música de CADA cuenta de FC (clave `musica_token_<uid>`). Es una credencial del
 * servidor de Música, no de FC, pero igualmente va cifrada: ver [cifradas].
 *
 * Recibe las preferencias en vez de abrirlas para que los tests usen unas normales. Quien lo
 * use debe pedir el token con el uid de la cuenta ACTIVA en el momento de la llamada (lambda
 * `{ tokens.de(uidActivo()) }`), no copiar el valor al construir el cliente.
 */
class TokensMusica(private val prefs: SharedPreferences) {
    // Sin uid no hay cuenta: ClavesPorCuenta devolvería la clave "global" y un token sin dueño
    // se le pegaría a quien entrara después. Sin uid no se lee, no se guarda y no se borra nada.
    fun de(uid: String): String? =
        if (uid.isEmpty()) null else prefs.getString(clave(uid), null)?.takeIf { it.isNotEmpty() }

    fun guardar(uid: String, token: String) {
        if (uid.isEmpty() || token.isEmpty()) return
        prefs.edit().putString(clave(uid), token).remove(claveBaneada(uid)).apply()
    }

    fun borrar(uid: String) {
        if (uid.isEmpty()) return
        prefs.edit().remove(clave(uid)).apply()
    }

    /**
     * La cuenta quedó baneada en Música (403 `baneada`): el token se borra, pero la portada tiene que
     * seguir diciéndolo en vez de ofrecer verificarse otra vez. Se olvida al guardar un token nuevo.
     */
    fun marcarBaneada(uid: String) {
        if (uid.isEmpty()) return
        prefs.edit().putBoolean(claveBaneada(uid), true).apply()
    }

    fun baneada(uid: String): Boolean = uid.isNotEmpty() && prefs.getBoolean(claveBaneada(uid), false)

    private fun claveBaneada(uid: String) = ClavesPorCuenta.clave("musica_baneada", uid)

    private fun clave(uid: String) = ClavesPorCuenta.clave("musica_token", uid)

    companion object {
        /**
         * Igual que el cofre de cookies de MainActivity: `security-crypto:1.0.0` no tiene
         * `MasterKey.Builder`, solo `MasterKeys.getOrCreate`. Si el keystore falla (pasa en
         * algunos móviles) se cae a preferencias normales antes que dejar Música rota.
         */
        fun cifradas(ctx: Context): SharedPreferences = try {
            val alias = androidx.security.crypto.MasterKeys.getOrCreate(
                androidx.security.crypto.MasterKeys.AES256_GCM_SPEC
            )
            androidx.security.crypto.EncryptedSharedPreferences.create(
                "fc_musica_tokens", alias, ctx,
                androidx.security.crypto.EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                androidx.security.crypto.EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Throwable) {
            android.util.Log.w("Musica", "sin cifrado disponible", e)
            ctx.getSharedPreferences("fc_musica_tokens", Context.MODE_PRIVATE)
        }
    }
}
