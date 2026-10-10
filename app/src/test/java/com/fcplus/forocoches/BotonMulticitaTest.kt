package com.fcplus.forocoches

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * El botón de multicita dice su estado con palabras, no solo con un símbolo: antes era un "＋"
 * que pasaba a "✓" en gris/rojo, y quien no sabía qué era no lo descubría.
 */
class BotonMulticitaTest {

    @Test
    fun `sin marcar invita a añadir`() {
        val a = BotonMulticita.aspecto(marcado = false)
        assertEquals("Multicita", a.texto)
        assertEquals("Añadir a la multicita", a.descripcion)
        assertEquals(R.color.fc_texto_2, a.color)
        assertNull(a.fondo)
    }

    @Test
    fun `marcado lo dice y se ve como pastilla`() {
        val a = BotonMulticita.aspecto(marcado = true)
        assertEquals("En multicita", a.texto)
        assertEquals("Quitar de la multicita", a.descripcion)
        assertEquals(R.color.fc_rojo, a.color)
        assertEquals(R.drawable.bg_pastilla_suave, a.fondo)
        assertNotEquals(BotonMulticita.aspecto(false).icono, a.icono)
    }
}
