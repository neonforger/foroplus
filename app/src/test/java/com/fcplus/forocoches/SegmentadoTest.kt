package com.fcplus.forocoches

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Control segmentado de Opciones → Apariencia (tema, tamaños): la opción elegida va en una
 * pastilla roja con la tinta de "sobre rojo" (contraste AA en claro y oscuro, ContrasteTest);
 * las demás, sin fondo, en texto secundario.
 */
class SegmentadoTest {

    @Test
    fun `la elegida es pastilla roja`() {
        val a = Segmentado.aspecto(elegido = true)
        assertEquals(R.drawable.bg_segmento_elegido, a.fondo)
        assertEquals(R.color.fc_sobre_rojo, a.color)
        assertTrue(a.negrita)
    }

    @Test
    fun `las demas sin fondo`() {
        val a = Segmentado.aspecto(elegido = false)
        assertNull(a.fondo)
        assertEquals(R.color.fc_texto_2, a.color)
        assertFalse(a.negrita)
    }
}
