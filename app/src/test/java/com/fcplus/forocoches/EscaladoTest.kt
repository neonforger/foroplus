package com.fcplus.forocoches

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EscaladoTest {

    @Test
    fun `lo que ya cabe no se toca`() {
        // Un smiley de FC. Encogerlo lo devolveria a "se ven diminutos".
        val e = escaladoPara(25, 18, 842)
        assertEquals(1, e.muestreo)
        assertFalse(e.ajusteFino)
    }

    @Test
    fun `la foto tipica del foro se clava al ancho de dibujo`() {
        // El caso medido en t=10736632: 1263x627 dibujada a 842.
        val e = escaladoPara(1263, 627, 842)
        assertEquals(1, e.muestreo)          // 1263/2 = 631, se pasaria por debajo
        assertTrue(e.ajusteFino)
        assertEquals(1263, e.desde)
        assertEquals(842, e.hasta)
    }

    @Test
    fun `una foto enorme desbasta por potencias de dos sin bajar del objetivo`() {
        val e = escaladoPara(4000, 3000, 842)
        assertEquals(4, e.muestreo)          // 4000/4 = 1000 >= 842; /8 = 500 se pasaria
        assertEquals(1000, e.desde)
        assertEquals(842, e.hasta)
    }

    @Test
    fun `manda el lado mayor, sea el alto`() {
        val e = escaladoPara(600, 2400, 842)
        assertEquals(2, e.muestreo)          // 2400/2 = 1200 >= 842
        assertEquals(1200, e.desde)
        assertEquals(842, e.hasta)
    }

    @Test
    fun `datos absurdos no escalan nada`() {
        assertEquals(1, escaladoPara(0, 100, 842).muestreo)
        assertEquals(1, escaladoPara(100, 100, 0).muestreo)
    }
}
