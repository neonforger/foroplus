package com.fcplus.forocoches

import android.content.SharedPreferences

/**
 * Tono del modo oscuro: "gris oscuro" (el de siempre, fondo #121214), "oscuro" (un paso hacia el
 * negro) o "negro" (negro puro, para pantallas AMOLED). Lo pidió un usuario; se decidió con el
 * dueño un selector de tres y no una barra: tres tonos pensados uno a uno se distinguen y se
 * leen, un deslizador da cien grises a medias.
 *
 * Gemelo de [EstiloApp]: se guarda una palabra NUESTRA y se aplica como overlay de tema que
 * redefine la familia de superficies (`fcFondo`, `fcTarjeta`… en res/values/attrs.xml).
 * MainActivity lo aplica antes de `setContentView`, DESPUÉS del de estilo; los dos tocan
 * atributos distintos, así que se combinan. Solo cambia algo de noche: de día los colores de
 * los overlays son alias de los claros de siempre.
 *
 * Por defecto, el gris: a quien no toque nada no le cambia nada.
 */
object TonoOscuro {

    const val PREF = "tono_oscuro"

    const val GRIS = "gris"
    const val OSCURO = "oscuro"
    const val NEGRO = "negro"

    /** En el orden en el que se enseñan (del más claro al más negro). */
    val OPCIONES = listOf(GRIS, OSCURO, NEGRO)

    fun etiqueta(tono: String): String = when (tono) {
        OSCURO -> "Oscuro"
        NEGRO -> "Negro puro"
        else -> "Gris oscuro"
    }

    /** Lo guardado, o el gris si no hay nada o hay basura. */
    fun guardado(prefs: SharedPreferences): String =
        prefs.getString(PREF, GRIS).let { if (it in OPCIONES) it!! else GRIS }

    /** Guarda el tono. Devuelve true si ha cambiado de verdad (y entonces hay que recrear). */
    fun elegir(prefs: SharedPreferences, tono: String): Boolean {
        val nuevo = if (tono in OPCIONES) tono else GRIS
        if (guardado(prefs) == nuevo) return false
        prefs.edit().putString(PREF, nuevo).apply()
        return true
    }

    /** El overlay de tema del tono, o null si es el gris (el tema base ya lo es). */
    fun overlay(tono: String): Int? = when (tono) {
        OSCURO -> R.style.ThemeOverlay_FC_Tono_Oscuro
        NEGRO -> R.style.ThemeOverlay_FC_Tono_Negro
        else -> null
    }

    /**
     * Lo que dice el resumen de Apariencia, o null si no hay que decir nada: el gris es el de
     * fábrica, y con el tema claro el tono no se ve, así que nombrarlo sería ruido.
     */
    fun resumen(tono: String, tema: String): String? = when {
        tema == TemaApp.CLARO -> null
        tono == OSCURO -> "tono oscuro"
        tono == NEGRO -> "negro puro"
        else -> null
    }
}
