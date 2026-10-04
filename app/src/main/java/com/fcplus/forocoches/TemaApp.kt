package com.fcplus.forocoches

import android.content.SharedPreferences
import androidx.appcompat.app.AppCompatDelegate

/**
 * Modo claro / oscuro / según el sistema.
 *
 * La política vive aquí, en Kotlin puro y con tests, porque el valor que se guarda en las
 * preferencias tiene que sobrevivir a versiones futuras: si mañana se guardara un entero de
 * `AppCompatDelegate` y esas constantes cambiaran de valor, la gente se encontraría la app en
 * un modo que no eligió. Se guarda una palabra nuestra (`claro` / `oscuro` / `sistema`) y aquí
 * se traduce.
 *
 * Por defecto **sigue al sistema**: es lo que espera quien ya tiene el móvil configurado, y no
 * decide por nadie. El ajuste existe para quien quiere lo contrario que su móvil — leer el foro
 * en claro con el sistema en oscuro es una preferencia real, no un capricho.
 */
object TemaApp {

    const val PREF = "tema_app"

    const val SISTEMA = "sistema"
    const val CLARO = "claro"
    const val OSCURO = "oscuro"

    /** Las tres opciones, en el orden en el que se enseñan. */
    val OPCIONES = listOf(CLARO, OSCURO, SISTEMA)

    fun etiqueta(modo: String): String = when (modo) {
        CLARO -> "Claro"
        OSCURO -> "Oscuro"
        else -> "Sistema"
    }

    /** Lo guardado, o "sistema" si no hay nada o hay basura. */
    fun guardado(prefs: SharedPreferences): String =
        prefs.getString(PREF, SISTEMA).let { if (it in OPCIONES) it!! else SISTEMA }

    /** Constante de AppCompat para un modo. Lo desconocido cae en "sigue al sistema". */
    fun modoDelegate(modo: String): Int = when (modo) {
        CLARO -> AppCompatDelegate.MODE_NIGHT_NO
        OSCURO -> AppCompatDelegate.MODE_NIGHT_YES
        else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
    }

    /**
     * ¿La app se está pintando en oscuro AHORA MISMO?
     *
     * Se lee de la configuración y NO del ajuste guardado: con "sistema" el ajuste no dice
     * nada, lo decide el móvil. Recibe el `uiMode` para poder probarse sin Android delante.
     */
    fun esOscuro(uiMode: Int): Boolean =
        (uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
            android.content.res.Configuration.UI_MODE_NIGHT_YES

    /**
     * Aplica el tema guardado. Se llama en `Application.onCreate`, ANTES de que se infle
     * ninguna vista: hacerlo más tarde obliga a recrear la actividad y se ve el cambiazo.
     */
    fun aplicarGuardado(prefs: SharedPreferences) {
        AppCompatDelegate.setDefaultNightMode(modoDelegate(guardado(prefs)))
    }

    /** Guarda y aplica. Devuelve true si el modo ha cambiado de verdad. */
    fun elegir(prefs: SharedPreferences, modo: String): Boolean {
        val nuevo = if (modo in OPCIONES) modo else SISTEMA
        if (guardado(prefs) == nuevo) return false
        prefs.edit().putString(PREF, nuevo).apply()
        AppCompatDelegate.setDefaultNightMode(modoDelegate(nuevo))
        return true
    }
}
