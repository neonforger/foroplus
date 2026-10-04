package com.fcplus.forocoches

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

class IsThreadUnreadTest {

    @Test
    fun `lo que no has abierto AQUI sale en negrita, diga FC lo que diga`() {
        // El cambio del 2026-09-11: el estado de leído de FC es de la CUENTA, no del móvil.
        // Quien lee el foro también en el PC llegaba y lo veía todo gris.
        assertTrue(isThreadUnread(readReplies = null, replies = "42"))
        assertTrue(isThreadUnread(readReplies = null, replies = ""))
    }

    @Test
    fun `abierto y sin respuestas nuevas queda leido`() {
        assertFalse(isThreadUnread(readReplies = 42, replies = "42"))
    }

    @Test
    fun `abierto y con respuestas nuevas vuelve a no leido`() {
        assertTrue(isThreadUnread(readReplies = 42, replies = "45"))
    }

    @Test
    fun `abierto y con menos respuestas que antes queda leido`() {
        // Un post borrado baja el contador: no es contenido nuevo.
        assertFalse(isThreadUnread(readReplies = 42, replies = "40"))
    }

    @Test
    fun `sin contador de respuestas un hilo ya abierto queda leido`() {
        assertFalse(isThreadUnread(readReplies = 42, replies = ""))
    }
}
