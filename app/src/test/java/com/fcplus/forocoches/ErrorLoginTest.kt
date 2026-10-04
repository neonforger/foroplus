package com.fcplus.forocoches

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ErrorLoginTest {

    @Test
    fun `con Cloudflare se intenta verificar`() {
        assertTrue(ErrorLogin.debeVerificar("cloudflare", yaSeIntento = false))
    }

    @Test
    fun `pero solo UNA vez, para no dejar al usuario en un bucle`() {
        assertFalse(ErrorLogin.debeVerificar("cloudflare", yaSeIntento = true))
    }

    @Test
    fun `una contraseña mal no abre ninguna verificación`() {
        assertFalse(ErrorLogin.debeVerificar("", yaSeIntento = false))
        assertFalse(ErrorLogin.debeVerificar("Usuario o contraseña incorrectos", false))
    }

    @Test
    fun `el mensaje de Cloudflare dice lo que SÍ funciona`() {
        // El texto viejo era "inténtalo de nuevo en un momento", y reintentar con la misma IP
        // no puede funcionar nunca. Cinco personas dieron vueltas una noche por esa frase.
        val m = ErrorLogin.mensaje("cloudflare")
        assertTrue(m, m.contains("VPN"))
        assertTrue(m, m.contains("España"))
        assertFalse(m, m.contains("más tarde"))
        assertFalse(m, m.contains("en un momento"))
    }

    @Test
    fun `el error que manda FC se enseña tal cual`() {
        assertEquals("Debes esperar 30 segundos", ErrorLogin.mensaje("Debes esperar 30 segundos"))
    }

    @Test
    fun `sin error concreto, lo más probable es la contraseña`() {
        assertEquals("Usuario o contraseña incorrectos", ErrorLogin.mensaje(""))
    }
}
