package com.fcplus.forocoches

import android.content.SharedPreferences
import java.io.File

/**
 * Las playlists locales de Música se quitaron (2026-11-11: Música es solo el feed). Solo las
 * hubo en las compilaciones de pruebas del dueño, nunca en Play, pero su carpeta `filesDir/musica`
 * (el JSON de `AlmacenMusica`) se quedaría ahí para siempre: ocupando sitio y con las canciones
 * que guardó. Se borra UNA vez; la marca en prefs evita volver a mirar el disco en cada arranque.
 */
object LimpiezaMusica {
    const val PREFS = "musica_limpieza"
    const val CLAVE = "carpeta_playlists_borrada"

    /** Bloqueante (disco). `true` si había algo que borrar y se borró; la marca se pone siempre que no falle. */
    fun carpetaDePlaylists(filesDir: File, prefs: SharedPreferences): Boolean {
        if (prefs.getBoolean(CLAVE, false)) return false
        val carpeta = File(filesDir, "musica")
        val habia = carpeta.exists()
        // Si no se pudo borrar (disco raro) no se marca: se reintenta en el siguiente arranque.
        if (habia && !carpeta.deleteRecursively()) return false
        prefs.edit().putBoolean(CLAVE, true).apply()
        return habia
    }
}
