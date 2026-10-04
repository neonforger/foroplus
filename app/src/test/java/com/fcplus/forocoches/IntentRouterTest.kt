package com.fcplus.forocoches

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Enrutado de los intents con los que se abre la app (enlaces de FC, launcher).
 * REGLA DE ORO: ningún destino es la capa web — de ahí el caso de la url nula.
 *
 * Antes había un destino más, NOTICES, al que llegaba la notificación de citas/menciones; se
 * fue con las push el 2026-09-21. El caso de la url nula viene de aquel bug (2026-07-26: una
 * notificación de cita sin url acababa enseñando el foro web) y se queda: sigue siendo la
 * guarda que impide que una url que no entendemos termine en la capa web.
 */
class IntentRouterTest {

    @Test
    fun `url de hilo con motor listo abre THREAD con esa url`() {
        val url = "https://forocoches.com/foro/showthread.php?p=123"
        val r = IntentRouter.route(rawUrl = url, engineReady = true)
        assertEquals(Target.THREAD, r.target)
        assertEquals(url, r.url)
        assertFalse(r.deferred)
    }

    @Test
    fun `url de hilo con motor no listo se difiere`() {
        val url = "https://forocoches.com/foro/showthread.php?t=999"
        val r = IntentRouter.route(rawUrl = url, engineReady = false)
        assertEquals(Target.THREAD, r.target)
        assertTrue(r.deferred)
    }

    @Test
    fun `url de MP abre la bandeja`() {
        val url = "https://forocoches.com/foro/private.php"
        val r = IntentRouter.route(rawUrl = url, engineReady = true)
        assertEquals(Target.PM_INBOX, r.target)
    }

    @Test
    fun `url nula (bug de la cita) va a HOME, jamas web`() {
        val r = IntentRouter.route(rawUrl = null, engineReady = true)
        assertEquals(Target.HOME, r.target)
    }

    @Test
    fun `url no-FC va a HOME`() {
        val r = IntentRouter.route(rawUrl = "https://malforocoches.com/foro/showthread.php?t=1", engineReady = true)
        assertEquals(Target.HOME, r.target)
    }

    @Test
    fun `url home de FC va a HOME`() {
        val r = IntentRouter.route(rawUrl = "https://forocoches.com/foro/", engineReady = true)
        assertEquals(Target.HOME, r.target)
    }
}
