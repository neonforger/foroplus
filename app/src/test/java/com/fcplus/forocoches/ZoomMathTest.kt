package com.fcplus.forocoches

import org.junit.Assert.assertEquals
import org.junit.Test

class ZoomMathTest {

    @Test
    fun `la escala de ajuste deja la imagen entera dentro`() {
        // foto apaisada en pantalla vertical: manda el ancho
        assertEquals(0.5f, ZoomMath.escalaDeAjuste(2000, 1000, 1000, 2000), 0.001f)
        // foto vertical en pantalla vertical: manda el alto
        assertEquals(0.5f, ZoomMath.escalaDeAjuste(1000, 4000, 1000, 2000), 0.001f)
    }

    @Test
    fun `medidas imposibles no revientan`() {
        assertEquals(1f, ZoomMath.escalaDeAjuste(0, 100, 100, 100), 0.001f)
        assertEquals(1f, ZoomMath.escalaDeAjuste(100, 100, 0, 100), 0.001f)
    }

    @Test
    fun `no se puede reducir por debajo del ajuste`() {
        assertEquals(2f, ZoomMath.limitarEscala(0.1f, ajuste = 2f), 0.001f)
    }

    @Test
    fun `no se puede ampliar mas alla del tope`() {
        assertEquals(8f, ZoomMath.limitarEscala(99f, ajuste = 2f, maxFactor = 4f), 0.001f)
    }

    @Test
    fun `si el contenido cabe se centra y no se deja arrastrar`() {
        // contenido 400 en vista 1000 -> siempre centrado en 300, se pida lo que se pida
        assertEquals(300f, ZoomMath.limitarDesplazamiento(-999f, 400f, 1000f), 0.001f)
        assertEquals(300f, ZoomMath.limitarDesplazamiento(999f, 400f, 1000f), 0.001f)
    }

    @Test
    fun `si el contenido no cabe no se dejan huecos en los bordes`() {
        // contenido 2000 en vista 1000: el margen util va de -1000 a 0
        assertEquals(0f, ZoomMath.limitarDesplazamiento(50f, 2000f, 1000f), 0.001f)      // borde izq
        assertEquals(-1000f, ZoomMath.limitarDesplazamiento(-5000f, 2000f, 1000f), 0.001f) // borde der
        assertEquals(-400f, ZoomMath.limitarDesplazamiento(-400f, 2000f, 1000f), 0.001f)   // en medio
    }

    @Test
    fun `el doble toque amplia y el siguiente vuelve al ajuste`() {
        val ajuste = 2f
        val ampliada = ZoomMath.escalaTrasDobleToque(ajuste, ajuste)
        assertEquals(5f, ampliada, 0.001f)                                  // 2 * 2.5
        assertEquals(ajuste, ZoomMath.escalaTrasDobleToque(ampliada, ajuste), 0.001f)
    }

    @Test
    fun `tras una pinza minima el doble toque sigue volviendo al ajuste`() {
        // una pinza deja escalas como 2.005: eso NO cuenta como "ampliado"
        assertEquals(5f, ZoomMath.escalaTrasDobleToque(2.005f, 2f), 0.001f)
    }

    @Test
    fun `el doble toque respeta el tope de ampliacion`() {
        // con maxFactor 4 y factor de doble toque 2.5 nunca se pasa
        val r = ZoomMath.escalaTrasDobleToque(1f, 1f)
        assertEquals(true, r <= ZoomMath.MAX_FACTOR)
    }

    @Test
    fun `el visor decodifica mas grande que la lista pero con freno`() {
        assertEquals(1600, ZoomMath.ladoMaximoVisor(400))    // pantalla pequeña: el suelo
        assertEquals(2160, ZoomMath.ladoMaximoVisor(720))
        assertEquals(2560, ZoomMath.ladoMaximoVisor(1440))   // pantalla grande: el techo
    }
}
