package com.fcplus.forocoches

import android.content.SharedPreferences

/**
 * Estilo de la app: "Compacta" (la de siempre) o "Tarjetas" (fondo gris y filas/mensajes como
 * tarjetas redondeadas). Gemelo de [TemaApp]: se guarda una palabra NUESTRA, no un id de
 * recurso, porque los `R.style` cambian de número entre compilaciones.
 *
 * Cómo se aplica: todo lo que cambia entre estilos es un atributo de tema `fc*`
 * (res/values/attrs.xml). El tema base les da los valores de la Compacta y el overlay de
 * Tarjetas los redefine; MainActivity aplica [overlay] antes de `setContentView`.
 */
object EstiloApp {

    const val PREF = "estilo_app"

    const val COMPACTA = "compacta"
    const val TARJETAS = "tarjetas"

    /** En el orden en el que se enseñan. */
    val OPCIONES = listOf(COMPACTA, TARJETAS)

    fun etiqueta(estilo: String): String = if (estilo == TARJETAS) "Tarjetas" else "Compacta"

    /** Lo guardado, o la compacta si no hay nada o hay basura. */
    fun guardado(prefs: SharedPreferences): String =
        prefs.getString(PREF, COMPACTA).let { if (it in OPCIONES) it!! else COMPACTA }

    /** Guarda el estilo. Devuelve true si ha cambiado de verdad (y entonces hay que recrear). */
    fun elegir(prefs: SharedPreferences, estilo: String): Boolean {
        val nuevo = if (estilo in OPCIONES) estilo else COMPACTA
        if (guardado(prefs) == nuevo) return false
        prefs.edit().putString(PREF, nuevo).apply()
        return true
    }

    /** El overlay de tema del estilo, o null si es la compacta (el tema base ya lo es). */
    fun overlay(estilo: String): Int? =
        if (estilo == TARJETAS) R.style.ThemeOverlay_FC_Tarjetas else null
}
