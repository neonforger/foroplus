package com.fcplus.forocoches

import org.junit.Assert.assertEquals
import org.junit.Test

class ReplyResultTest {

    @Test
    fun `saca el pid del ancla post`() {
        assertEquals(
            "123456789",
            replyPidFromUrl("https://forocoches.com/foro/showthread.php?p=123456789#post123456789")
        )
    }

    @Test
    fun `el ancla post gana aunque el parametro p diga otro pid`() {
        assertEquals(
            "555",
            replyPidFromUrl("https://forocoches.com/foro/showthread.php?p=999#post555")
        )
    }

    @Test
    fun `saca el pid del parametro p`() {
        assertEquals(
            "987654",
            replyPidFromUrl("https://forocoches.com/foro/showthread.php?p=987654")
        )
    }

    @Test
    fun `el parametro p vale aunque haya otros parametros delante`() {
        assertEquals(
            "555",
            replyPidFromUrl("https://forocoches.com/foro/showthread.php?t=10&p=555&page=3")
        )
    }

    @Test
    fun `una url de hilo sin pid devuelve vacio`() {
        assertEquals("", replyPidFromUrl("https://forocoches.com/foro/showthread.php?t=10708037"))
    }

    @Test
    fun `no confunde el page con el pid`() {
        assertEquals("", replyPidFromUrl("https://forocoches.com/foro/showthread.php?t=10&page=7"))
    }

    @Test
    fun `url vacia devuelve vacio`() {
        assertEquals("", replyPidFromUrl(""))
    }

    // ── paginaDeMiMensaje ────────────────────────────────────────────────────

    @Test
    fun `tu mensaje abrio pagina nueva - se pide la nueva ultima`() {
        // Juan: era el primero de la 7 y la app te dejaba en el último de la 6.
        assertEquals(7, paginaDeMiMensaje(estaTuMensaje = false, pagina = 6, paginasTotales = 7))
    }

    @Test
    fun `tu mensaje esta en la pagina que llego - no se pide nada`() {
        assertEquals(null, paginaDeMiMensaje(estaTuMensaje = true, pagina = 7, paginasTotales = 7))
    }

    @Test
    fun `no esta pero no hay mas paginas - no se reintenta en bucle`() {
        assertEquals(null, paginaDeMiMensaje(estaTuMensaje = false, pagina = 7, paginasTotales = 7))
    }
}
