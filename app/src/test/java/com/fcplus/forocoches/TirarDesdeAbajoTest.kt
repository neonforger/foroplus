package com.fcplus.forocoches

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TirarDesdeAbajoTest {

    private val max = TirarDesdeAbajo.MAX_DP
    private val umbral = TirarDesdeAbajo.UMBRAL_DP

    @Test fun `sin tirar no se mueve`() {
        assertEquals(0f, TirarDesdeAbajo.desplazamiento(0f, max))
        assertEquals(0f, TirarDesdeAbajo.desplazamiento(-40f, max))
    }

    @Test fun `al principio sube mas o menos la mitad de lo que tira el dedo`() {
        val d = TirarDesdeAbajo.desplazamiento(20f, max)
        assertTrue("$d", d in 9f..10f)
    }

    @Test fun `cuanto mas tiras mas cuesta y nunca pasa del maximo`() {
        val a = TirarDesdeAbajo.desplazamiento(100f, max)
        val b = TirarDesdeAbajo.desplazamiento(200f, max)
        val c = TirarDesdeAbajo.desplazamiento(5000f, max)
        assertTrue(b > a)
        assertTrue("cada tramo sube menos", (b - a) < a)
        assertTrue(c <= max)
    }

    @Test fun `el umbral se alcanza con un recorrido razonable`() {
        // Con ~160 dp de dedo ya se pasa: ni un roce lo dispara ni hace falta media pantalla.
        assertTrue(TirarDesdeAbajo.desplazamiento(160f, max) >= umbral)
        assertTrue(TirarDesdeAbajo.desplazamiento(100f, max) < umbral)
    }

    @Test fun `la rueda se dibuja hasta el umbral`() {
        assertEquals(0f, TirarDesdeAbajo.progreso(0f, umbral))
        assertEquals(0.5f, TirarDesdeAbajo.progreso(umbral / 2, umbral))
        assertEquals(1f, TirarDesdeAbajo.progreso(umbral * 2, umbral))
    }

    @Test fun `al soltar recarga solo pasado el umbral y sin ser un golpe`() {
        assertTrue(TirarDesdeAbajo.recarga(umbral, umbral, duracionMs = 400))
        assertFalse(TirarDesdeAbajo.recarga(umbral - 1, umbral, duracionMs = 400))
        assertFalse("un golpe de dedo rebota pero no recarga",
            TirarDesdeAbajo.recarga(max, umbral, duracionMs = 80))
    }

    @Test fun `no arma recien llegado al fondo`() {
        assertFalse(TirarDesdeAbajo.puedeArmar(ahora = 10_000, ultimoMovimiento = 9_800))
        assertTrue(TirarDesdeAbajo.puedeArmar(ahora = 10_000, ultimoMovimiento = 9_000))
    }

    @Test fun `solo en la ultima pagina y con mensajes`() {
        assertTrue(TirarDesdeAbajo.disponible(5, 5, true))
        assertFalse(TirarDesdeAbajo.disponible(4, 5, true))
        assertFalse(TirarDesdeAbajo.disponible(5, 5, false))
    }

    @Test fun `solo se avisa de una pagina nueva`() {
        assertNull(TirarDesdeAbajo.aviso(3, 3))
        assertEquals("Hay una página nueva: la 4", TirarDesdeAbajo.aviso(3, 4))
        assertEquals("Hay 2 páginas nuevas", TirarDesdeAbajo.aviso(3, 5))
    }
}
