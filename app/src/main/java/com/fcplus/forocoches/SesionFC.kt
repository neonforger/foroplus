package com.fcplus.forocoches

import android.webkit.CookieManager

/**
 * La sesión de ForoCoches, que son **cuatro cookies** y se pueden guardar y reponer.
 *
 * Medido en dispositivo el 2026-09-16 con sesión viva: borrando todas las cookies del dominio y
 * reponiendo solo estas cuatro, FC vuelve a reconocer al usuario (`loggedinuser` = el mismo uid).
 * Ese experimento es el cimiento del cambio de cuenta.
 *
 * **Son HttpOnly**: las ve `CookieManager` desde Kotlin, **no** `document.cookie` desde JS. Una
 * sonda por JS dirá siempre que no hay sesión.
 *
 * **Al verificar una sesión, cuidado con Varnish** (gotcha 10): tras borrar las cookies FC sigue
 * sirviendo "estás logueado" durante ~1 minuto, y ni `?_=Date.now()` ni `cache:'no-store'` lo
 * evitan. Una comprobación con pocos segundos de margen dice lo CONTRARIO de la verdad.
 */
object SesionFC {

    const val URL = "https://forocoches.com"

    /** Las cuatro medidas. Las otras seis del dominio no hacen falta para la sesión. */
    val DE_SESION = listOf("bbuserid", "bbpassword", "bbsessionhash", "bbimloggedin")

    /** Parte la cabecera `n=v; n2=v2` en pares. Puro para poder probarlo sin Android. */
    fun deCabecera(cabecera: String): Map<String, String> =
        cabecera.split(";").mapNotNull { trozo ->
            val i = trozo.indexOf('=')
            if (i <= 0) return@mapNotNull null
            // substringAfter el PRIMER '=' y no split('='): los valores pueden llevar '='.
            trozo.substring(0, i).trim() to trozo.substring(i + 1).trim()
        }.toMap()

    /** Formato exacto con el que se restauró la sesión en la medición. */
    fun aSetCookie(nombre: String, valor: String): String =
        "$nombre=$valor; Domain=forocoches.com; Path=/; Secure; HttpOnly"

    /** Las cookies de sesión que hay ahora mismo, o un mapa vacío si no hay ninguna. */
    fun leer(cm: CookieManager): Map<String, String> =
        deCabecera(cm.getCookie(URL) ?: "").filterKeys { it in DE_SESION }

    fun poner(cm: CookieManager, cookies: Map<String, String>) {
        for ((n, v) in cookies) cm.setCookie(URL, aSetCookie(n, v))
        cm.flush()
    }

    /**
     * Borra TODAS las cookies y avisa cuando de verdad ha terminado (la API es asíncrona).
     *
     * **Hay que llamarla desde un hilo con `Looper`** (en la app, el de interfaz):
     * `removeAllCookies` lanza `IllegalStateException` si no lo hay. Lo destapó el test
     * instrumentado, que corre en el hilo de instrumentación y no lo tiene; en la app nunca
     * falla porque el cierre de sesión siempre se dispara desde el hilo de interfaz.
     */
    fun borrar(cm: CookieManager, alAcabar: () -> Unit) {
        cm.removeAllCookies {
            cm.flush()
            alAcabar()
        }
    }
}
