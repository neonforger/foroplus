package com.fcplus.forocoches

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * La política de qué hacer con cada `styleid`. Se prueba aquí porque el efecto real es una
 * ESCRITURA en la cuenta del usuario: la regla que decide cuándo se dispara no puede estar
 * cubierta solo por "lo probé una vez en mi móvil".
 */
class ForumSkinTest {

    @Test
    fun `el juego moderno se deja en paz`() {
        // 8 = móvil, 9 = escritorio. Forzar 8 sobre alguien que está en 9 le cambiaría el
        // ajuste sin motivo: son los dos el markup que el motor sabe leer.
        assertEquals(ForumSkin.Decision.Ok, ForumSkin.decide(8, hasSession = true))
        assertEquals(ForumSkin.Decision.Ok, ForumSkin.decide(9, hasSession = true))
        assertEquals(ForumSkin.Decision.Ok, ForumSkin.decide(9, hasSession = false))
    }

    @Test
    fun `el juego antiguo con sesion se cambia`() {
        assertEquals(ForumSkin.Decision.Switch, ForumSkin.decide(5, hasSession = true))
        assertEquals(ForumSkin.Decision.Switch, ForumSkin.decide(7, hasSession = true))
    }

    @Test
    fun `el juego antiguo sin sesion no se puede cambiar`() {
        // Sin sesión no hay securitytoken válido: el POST no llegaría a ninguna parte.
        assertEquals(ForumSkin.Decision.NeedsSession, ForumSkin.decide(7, hasSession = false))
    }

    @Test
    fun `un styleid desconocido NO toca la cuenta`() {
        // Lo importante de este caso: si FC estrena un estilo 10, la app avisa pero NO
        // escribe a ciegas en la cuenta de nadie.
        assertEquals(ForumSkin.Decision.Unknown, ForumSkin.decide(10, hasSession = true))
        assertEquals(ForumSkin.Decision.Unknown, ForumSkin.decide(0, hasSession = true))
    }

    @Test
    fun `sin styleid legible tampoco se toca nada`() {
        assertEquals(ForumSkin.Decision.Unknown, ForumSkin.decide(null, hasSession = true))
    }

    @Test
    fun `el objetivo del cambio es el juego moderno`() {
        assertEquals(8, ForumSkin.TARGET)
        assertEquals(ForumSkin.Decision.Ok, ForumSkin.decide(ForumSkin.TARGET, hasSession = true))
    }

    @Test
    fun `solo hay aviso cuando la app no puede arreglarlo sola`() {
        assertNull(ForumSkin.warning(8, hasSession = true))
        assertNull(ForumSkin.warning(7, hasSession = true))   // se arregla solo, no se avisa
        assertNotNull(ForumSkin.warning(7, hasSession = false))
        assertNotNull(ForumSkin.warning(null, hasSession = true))
    }

    @Test
    fun `el aviso de estilo desconocido dice cual es`() {
        val w = ForumSkin.warning(10, hasSession = true)
        assertTrue(w!!.contains("10"))
    }
}
