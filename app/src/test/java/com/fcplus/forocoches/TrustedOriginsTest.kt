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
    fun `falls back to default url for untrusted input`() {
        assertEquals(
            TrustedOrigins.DEFAULT_URL,
            TrustedOrigins.trustedUrlOrDefault("https://evil.test/")
        )
    }
}
