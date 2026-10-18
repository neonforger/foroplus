package com.fcplus.forocoches

import org.junit.Assert.assertEquals
import org.junit.Test

class TamanoTextoTest {

    @Test
    fun `sin nada guardado es el de siempre`() {
        assertEquals(15, TamanoTexto.leer(null, null))
    }

    @Test
    fun `quien tenia un boton conserva su tamaño`() {
        assertEquals(13, TamanoTexto.leer(null, 0))
        assertEquals(15, TamanoTexto.leer(null, 1))
        assertEquals(18, TamanoTexto.leer(null, 2))
    }

    @Test
    fun `un indice viejo fuera de rango se recorta como antes`() {
        assertEquals(13, TamanoTexto.leer(null, -4))
        assertEquals(18, TamanoTexto.leer(null, 9))
    }

    @Test
    fun `el sp guardado manda sobre el indice viejo`() {
        assertEquals(17, TamanoTexto.leer(17, 0))
    }

    @Test
    fun `el sp se queda dentro de la barra`() {
        assertEquals(TamanoTexto.MIN, TamanoTexto.leer(4, null))
        assertEquals(TamanoTexto.MAX, TamanoTexto.leer(40, null))
    }

    @Test
    fun `en el resumen se nombra sin numeros`() {
        assertEquals("pequeña", TamanoTexto.palabra(12))
        assertEquals("pequeña", TamanoTexto.palabra(14))
        assertEquals("normal", TamanoTexto.palabra(15))
        assertEquals("grande", TamanoTexto.palabra(16))
        assertEquals("grandes", TamanoTexto.palabra(22, plural = true))
        assertEquals("normales", TamanoTexto.palabra(15, plural = true))
        assertEquals("pequeños", TamanoTexto.palabra(13, plural = true))
    }

    @Test
    fun `la etiqueta de TalkBack dice Normal solo en el de siempre`() {
        assertEquals("Normal", TamanoTexto.etiqueta(15))
        assertEquals("16", TamanoTexto.etiqueta(16))
        assertEquals("12", TamanoTexto.etiqueta(12))
    }
}
