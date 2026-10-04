package com.fcplus.forocoches

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Las cookies que forman la sesión de FC.
 *
 * Medido en dispositivo el 2026-09-16: son CUATRO (`bbuserid`, `bbpassword`, `bbsessionhash`,
 * `bbimloggedin`), las cuatro HttpOnly. Corrige el gotcha 6, que decía "solo bbuserid" —
 * `bbuserid` es la SEÑAL que usa isLoggedIn(), no la sesión entera.
 */
class SesionFCTest {

    @Test
    fun `son las cuatro cookies medidas`() {
        assertEquals(
            listOf("bbuserid", "bbpassword", "bbsessionhash", "bbimloggedin").sorted(),
            SesionFC.DE_SESION.sorted()
        )
    }

    @Test
    fun `una cabecera de cookies se parte en pares`() {
        val m = SesionFC.deCabecera("bbuserid=911128; bbpassword=abc123; otra=x")
        assertEquals("911128", m["bbuserid"])
        assertEquals("abc123", m["bbpassword"])
        assertEquals("x", m["otra"])
    }

    @Test
    fun `una cabecera vacia da un mapa vacio`() {
        assertEquals(emptyMap<String, String>(), SesionFC.deCabecera(""))
    }

    /** Un valor con '=' dentro (base64) no se puede partir por el primer igual y ya. */
    @Test
    fun `un valor con igual dentro se conserva entero`() {
        assertEquals("a=b=c", SesionFC.deCabecera("k=a=b=c")["k"])
    }

    @Test
    fun `los trozos sin igual se ignoran`() {
        assertEquals(emptyMap<String, String>(), SesionFC.deCabecera("basura; masbasura"))
    }

    /**
     * El formato tiene que llevar Domain, Path, Secure y HttpOnly: así es como se restauró la
     * sesión de verdad en la medición del 2026-09-16.
     */
    @Test
    fun `el set-cookie lleva los atributos que FC necesita`() {
        val s = SesionFC.aSetCookie("bbuserid", "911128")
        assertTrue(s.startsWith("bbuserid=911128;"))
        assertTrue(s.contains("Domain=forocoches.com"))
        assertTrue(s.contains("Path=/"))
        assertTrue(s.contains("Secure"))
        assertTrue(s.contains("HttpOnly"))
    }
}
