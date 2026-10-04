package com.fcplus.forocoches

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdBlockerTest {

    @Test
    fun `bloquea dominios de redes de publicidad`() {
        assertTrue(AdBlocker.shouldBlock("https://googlesyndication.com/pagead/js/adsbygoogle.js"))
        assertTrue(AdBlocker.shouldBlock("https://doubleclick.net/something"))
        assertTrue(AdBlocker.shouldBlock("https://static.criteo.net/js/ld/publishertag.js"))
        assertTrue(AdBlocker.shouldBlock("https://taboola.com/widgets"))
    }

    @Test
    fun `no bloquea URLs de Forocoches`() {
        assertFalse(AdBlocker.shouldBlock("https://forocoches.com/foro/"))
        assertFalse(AdBlocker.shouldBlock("https://forocoches.com/foro/showthread.php?t=12345"))
        assertFalse(AdBlocker.shouldBlock("https://static.forocoches.com/images/logo.png"))
    }

    @Test
    fun `no bloquea recursos genericos de CDN`() {
        assertFalse(AdBlocker.shouldBlock("https://fonts.googleapis.com/css2?family=Roboto"))
        assertFalse(AdBlocker.shouldBlock("https://ajax.googleapis.com/ajax/libs/jquery/3.7.0/jquery.min.js"))
    }
}
