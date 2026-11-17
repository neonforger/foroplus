package com.fcplus.forocoches

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IntroduccionTest {

    @Test
    fun `solo sale en una instalacion nueva y una vez`() {
        assertTrue(Introduccion.alArrancar(instalacionNueva = true, yaVista = false))
        assertFalse(Introduccion.alArrancar(instalacionNueva = true, yaVista = true))
        // Quien ya usaba la app y actualiza no la ve al arrancar (la tiene en Opciones).
        assertFalse(Introduccion.alArrancar(instalacionNueva = false, yaVista = false))
    }

    @Test
    fun `cinco paginas, la primera sin captura y el resto con la suya`() {
        assertEquals(5, Introduccion.PAGINAS.size)
        assertEquals(0, Introduccion.PAGINAS.first().imagen)
        assertTrue(Introduccion.PAGINAS.drop(1).all { it.imagen != 0 })
        assertTrue(Introduccion.PAGINAS.all { it.titulo.isNotBlank() && it.texto.isNotBlank() })
    }

    @Test
    fun `botones y saltar segun la pagina`() {
        assertEquals("Empezar", Introduccion.botonPrincipal(0))
        assertEquals("Siguiente", Introduccion.botonPrincipal(2))
        assertEquals("Entrar a ForoPlus", Introduccion.botonPrincipal(Introduccion.PAGINAS.lastIndex))
        assertTrue(Introduccion.haySaltar(0))
        assertFalse(Introduccion.haySaltar(Introduccion.PAGINAS.lastIndex))
    }

    @Test
    fun `el interruptor de Musica es el mismo ajuste que la fila de Subforos de arriba`() {
        assertTrue(Introduccion.musicaVisible(""))
        assertEquals("musica", Introduccion.ocultosConMusica("", visible = false))
        assertEquals("17,43,musica", Introduccion.ocultosConMusica("17,43", visible = false))
        assertEquals("17,43", Introduccion.ocultosConMusica("17,musica,43", visible = true))
        // Encenderla dos veces o apagarla dos veces no duplica ni rompe nada.
        assertEquals("17,musica", Introduccion.ocultosConMusica("17,musica", visible = false))
        assertFalse(Introduccion.musicaVisible("17,musica"))
    }

    @Test
    fun `el boton principal avanza y en la ultima termina`() {
        assertEquals(1, Introduccion.siguiente(0))
        assertNull(Introduccion.siguiente(Introduccion.PAGINAS.lastIndex))
    }
}
