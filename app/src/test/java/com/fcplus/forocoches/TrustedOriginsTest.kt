package com.fcplus.forocoches

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TrustedOriginsTest {

    @Test
    fun `accepts trusted forocoches https origins`() {
        assertTrue(TrustedOrigins.isTrustedForocochesUrl("https://forocoches.com/foro/"))
        assertTrue(TrustedOrigins.isTrustedForocochesUrl("https://www.forocoches.com/foro/private.php"))
    }

    @Test
    fun `accepts http forocoches origins (enlaces internos sin TLS)`() {
        // Deliberado: enlaces internos de FC pueden venir sin TLS y deben quedarse en la app
        // (usesCleartextTraffic=false ya impide cargarlos de verdad por red).
        assertTrue(TrustedOrigins.isTrustedForocochesUrl("http://forocoches.com/foro/"))
    }

    @Test
    fun `rejects lookalike and non-forocoches origins`() {
        assertFalse(TrustedOrigins.isTrustedForocochesUrl("https://forocoches.com.evil.test/foro/"))
        assertFalse(TrustedOrigins.isTrustedForocochesUrl("https://evil.test/?next=forocoches.com"))
        assertFalse(TrustedOrigins.isTrustedForocochesUrl("ftp://forocoches.com/foro/"))
    }

    @Test
    fun `la cookie de FC solo viaja a FC y por https`() {
        assertTrue(TrustedOrigins.llevaCookieDeFc("https://forocoches.com/foro/attachment.php?id=1"))
        assertTrue(TrustedOrigins.llevaCookieDeFc("https://static.forocoches.com/img/a.png"))
        assertTrue(TrustedOrigins.llevaCookieDeFc("HTTPS://ForoCoches.com/foro/x.gif"))
        // Lo que dejaba pasar el contains("forocoches.com") (aviso de @ivhere, 2026-10-04):
        assertFalse(TrustedOrigins.llevaCookieDeFc("https://forocoches.com.evil.test/x.jpg"))
        assertFalse(TrustedOrigins.llevaCookieDeFc("https://evil.test/forocoches.com.jpg"))
        assertFalse(TrustedOrigins.llevaCookieDeFc("https://evil.test/x.jpg?forocoches.com"))
        assertFalse(TrustedOrigins.llevaCookieDeFc("https://malforocoches.com/x.jpg"))
        assertFalse(TrustedOrigins.llevaCookieDeFc("https://forocoches.com@evil.test/x.jpg"))
        assertFalse(TrustedOrigins.llevaCookieDeFc("https://evil.test\\@forocoches.com/x.jpg"))
        // Sin TLS, nunca: la cookie iría en claro.
        assertFalse(TrustedOrigins.llevaCookieDeFc("http://forocoches.com/foro/x.png"))
        assertFalse(TrustedOrigins.llevaCookieDeFc(""))
        assertFalse(TrustedOrigins.llevaCookieDeFc(null))
    }

    @Test
    fun `falls back to default url for untrusted input`() {
        assertEquals(
            TrustedOrigins.DEFAULT_URL,
            TrustedOrigins.trustedUrlOrDefault("https://evil.test/")
        )
    }
}
