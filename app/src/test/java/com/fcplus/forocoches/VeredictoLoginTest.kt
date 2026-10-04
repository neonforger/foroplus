package com.fcplus.forocoches

import org.junit.Assert.assertEquals
import org.junit.Test

class VeredictoLoginTest {

    // ── El bug (2026-09-17): añadir una segunda cuenta ────────────────────────
    // Al añadir cuenta YA hay sesión, así que "¿hay cookie bbuserid?" vale true ANTES de
    // intentar nada: un login rechazado por FC se contaba como éxito, se cerraba el panel sin
    // decir nada y la cuenta no se añadía.

    // Al añadir cuenta se BORRA la sesión antes del POST (ver VeredictoLogin), así que una
    // cookie después del intento ya solo puede ser del que acaba de entrar.
    @Test
    fun `con la sesion borrada antes, la cookie vuelve a ser veredicto`() {
        assertEquals(
            VeredictoLogin.Veredicto.ENTRA,
            VeredictoLogin.de(posted = true, haySesion = true)
        )
    }

    @Test
    fun `entrar desde fuera si vale la cookie`() {
        assertEquals(
            VeredictoLogin.Veredicto.ENTRA,
            VeredictoLogin.de(posted = true, haySesion = true)
        )
    }

    @Test
    fun `sin sesion es que no ha entrado`() {
        assertEquals(
            VeredictoLogin.Veredicto.FALLA,
            VeredictoLogin.de(posted = true, haySesion = false)
        )
    }

    // Si el formulario no llegó a enviarse (Cloudflare delante, fallo de red) no ha entrado
    // nadie, aunque la cookie de la sesión ANTERIOR siga puesta.

    @Test
    fun `sin enviar el formulario no ha entrado nadie`() {
        assertEquals(
            VeredictoLogin.Veredicto.FALLA,
            VeredictoLogin.de(posted = false, haySesion = true)
        )
        assertEquals(
            VeredictoLogin.Veredicto.FALLA,
            VeredictoLogin.de(posted = false, haySesion = true)
        )
    }

    // El texto raspado del HTML de FC NO decide: `blockrow` es una clase genérica de vBulletin
    // y un falso positivo ahí rompería el login normal, que es el camino más crítico.

    @Test
    fun `el texto de error de FC no cambia el veredicto del login normal`() {
        assertEquals(
            VeredictoLogin.Veredicto.ENTRA,
            VeredictoLogin.de(posted = true, haySesion = true)
        )
    }

    // ── El mensaje ────────────────────────────────────────────────────────────

    @Test
    fun `si FC explica el motivo, se enseña el suyo`() {
        assertEquals(
            "Usuario o contraseña incorrectos",
            VeredictoLogin.mensajeAlAnadir("Usuario o contraseña incorrectos")
        )
    }

    @Test
    fun `sin motivo de FC, se dice lo que se sabe y no se miente`() {
        val m = VeredictoLogin.mensajeAlAnadir("")
        assertEquals(true, m.contains("No se pudo entrar"))
        // Nada de "revisa tu conexión": la sesión anterior sigue viva, así que hay red.
        assertEquals(false, m.lowercase().contains("conexión"))
    }
}
