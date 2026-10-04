package com.fcplus.forocoches

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CuandoPedirValoracionTest {

    @Test
    fun `no se pide a quien apenas la ha usado`() {
        assertFalse(CuandoPedirValoracion.toca(0, intentos = 0))
        assertFalse(CuandoPedirValoracion.toca(14, intentos = 0))
    }

    @Test
    fun `a los quince mensajes publicados, una vez`() {
        assertTrue(CuandoPedirValoracion.toca(15, intentos = 0))
        assertTrue(CuandoPedirValoracion.toca(300, intentos = 0))
    }

    @Test
    fun `una vez ense_ada no se vuelve a pedir jamas`() {
        // Google no garantiza que no se la ensene a quien ya valoro: se pide lo minimo.
        assertFalse(CuandoPedirValoracion.toca(15, intentos = 1))
        assertFalse(CuandoPedirValoracion.toca(5000, intentos = 1))
    }
}
