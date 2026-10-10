package com.fcplus.forocoches

import java.util.Calendar
import java.util.Locale

/**
 * La línea de antigüedad y actividad que se pinta bajo el nombre en el perfil de otro usuario.
 *
 * La pidió un tester el 2026-08-22 con un caso de uso muy concreto: *"si veo que un usuario se
 * ha registrado en agosto de 2026 y ya lleva 70 hilos y 400 mensajes, automáticamente le pongo
 * en ignorados por bot o cagahilos. Ahora mismo tengo que irme al foro a mirarle."*
 *
 * Por eso, además de los tres datos que da FC, se calcula **cuántos mensajes al día** escribe:
 * es el número que de verdad responde a su pregunta. 400 mensajes no dicen nada; 400 mensajes
 * en tres semanas, sí.
 */
object EstadisticasMiembro {

    /** Meses tal y como los abrevia FC: `30-nov-2004`. */
    private val MESES = listOf(
        "ene", "feb", "mar", "abr", "may", "jun",
        "jul", "ago", "sep", "oct", "nov", "dic"
    )

    /**
     * Número de FC ("7.253", "1.234,5") a entero.
     *
     * El punto es separador de MILES, no decimal (gotcha 11): "1.680" son mil seiscientos
     * ochenta, no uno coma seis. Lo que venga detrás de una coma se descarta.
     */
    fun numero(texto: String): Int? {
        val limpio = texto.trim().substringBefore(',').replace(".", "")
        if (limpio.isEmpty() || !limpio.all { it.isDigit() }) return null
        return limpio.toIntOrNull()
    }

    /**
     * Días transcurridos desde una fecha de registro con el formato de FC (`30-nov-2004`).
     * Devuelve null si no se entiende, y nunca menos de 1 (para no dividir por cero con
     * alguien que se registró hoy).
     */
    fun diasDesde(registro: String, hoyMillis: Long): Int? {
        val partes = registro.trim().lowercase(Locale("es")).split("-")
        if (partes.size != 3) return null
        val dia = partes[0].toIntOrNull() ?: return null
        val mes = MESES.indexOfFirst { partes[1].startsWith(it) }
        if (mes < 0) return null
        val anio = partes[2].toIntOrNull() ?: return null
        if (anio < 1990 || anio > 2999) return null
        val cal = Calendar.getInstance().apply {
            clear()
            set(anio, mes, dia)
        }
        val dias = ((hoyMillis - cal.timeInMillis) / 86_400_000L).toInt()
        return maxOf(dias, 1)
    }

    /**
     * La línea completa, ya lista para pintar. Cadena vacía si no hay ningún dato — mejor no
     * enseñar nada que enseñar un hueco con guiones.
     *
     * Ejemplos:
     * - `7.253 mensajes · 38 hilos · desde 30-nov-2004 · 0,9 al día`
     * - `400 mensajes · 70 hilos · desde 08-ago-2026 · 19,0 al día`
     */
    fun linea(mensajes: String, hilos: String, registro: String, hoyMillis: Long): String {
        val trozos = ArrayList<String>(4)
        if (mensajes.isNotBlank()) trozos.add("$mensajes mensajes")
        if (hilos.isNotBlank()) trozos.add("$hilos hilos")

        // La fecha entera, tal cual la da FC (pedido en Telegram 2211: "día, mes y año").
        if (registro.trim().split("-").size == 3) trozos.add("desde ${registro.trim()}")

        val n = numero(mensajes)
        val dias = diasDesde(registro, hoyMillis)
        if (n != null && dias != null) {
            val porDia = n.toDouble() / dias
            trozos.add(String.format(Locale("es"), "%.1f al día", porDia))
        }
        return trozos.joinToString(" · ")
    }

    /**
     * Las mismas cifras que [linea], por separado: los bloques del panel de tu cuenta (fase 2
     * del rediseño). `("7.253", "mensajes")`, `("38", "hilos")`, `("0,9", "al día")`; lo que
     * falte no sale.
     */
    fun cifras(mensajes: String, hilos: String, registro: String, hoyMillis: Long): List<Pair<String, String>> {
        val bloques = ArrayList<Pair<String, String>>(3)
        if (mensajes.isNotBlank()) bloques.add(mensajes.trim() to "mensajes")
        if (hilos.isNotBlank()) bloques.add(hilos.trim() to "hilos")
        val n = numero(mensajes)
        val dias = diasDesde(registro, hoyMillis)
        if (n != null && dias != null) {
            bloques.add(String.format(Locale("es"), "%.1f", n.toDouble() / dias) to "al día")
        }
        return bloques
    }

    /**
     * "Miembro · desde 30-may-2026", o lo que haya de eso (bajo tu nombre en el panel). Rango y
     * fecha SEPARADOS: la fecha es la de alta, no la del rango, y "Moderador desde…" mentiría.
     */
    fun desde(rango: String, registro: String): String {
        val r = rango.trim()
        val fecha = registro.trim().takeIf { it.split("-").size == 3 }
        return when {
            r.isNotEmpty() && fecha != null -> "$r · desde $fecha"
            fecha != null -> "Desde $fecha"
            else -> r
        }
    }

    /**
     * El rango de FC delante de la línea: `Miembro · 7.253 mensajes · …`.
     *
     * Es lo único de la ficha de FC que la app no enseñaba (sondeado el 2026-09-24: "Miembro"
     * en cuentas veteranas, "Usuario" en una reciente). Va primero porque es lo que antes dice
     * con quién hablas.
     */
    fun conRango(rango: String, linea: String): String {
        val r = rango.trim()
        return when {
            r.isEmpty() -> linea
            linea.isBlank() -> r
            else -> "$r · $linea"
        }
    }
}
