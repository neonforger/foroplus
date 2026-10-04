package com.fcplus.forocoches

import org.junit.Assert.assertEquals
import org.junit.Test

class PlegableTest {

    @Test
    fun `sin nada que enseñar no se ofrece abrir nada`() {
        // Un botón que abre una lista vacía no es un atajo, es un obstáculo. Misma regla que
        // la pestaña del Popurrí, que no sale si no has elegido subforos.
        assertEquals("", Plegable.rotulo(0, "ignorado", "ignorados", abierto = false))
        assertEquals("", Plegable.rotulo(0, "ignorado", "ignorados", abierto = true))
    }

    @Test
    fun `uno solo va en singular`() {
        assertEquals("Ver 1 ignorado  ▾", Plegable.rotulo(1, "ignorado", "ignorados", false))
    }

    @Test
    fun `varios van en plural, con el numero por delante`() {
        assertEquals("Ver 37 ignorados  ▾", Plegable.rotulo(37, "ignorado", "ignorados", false))
    }

    @Test
    fun `abierto cambia el verbo y la flecha, y nada mas`() {
        assertEquals("Ocultar 37 ignorados  ▴", Plegable.rotulo(37, "ignorado", "ignorados", true))
        assertEquals("Ocultar 1 ignorado  ▴", Plegable.rotulo(1, "ignorado", "ignorados", true))
    }

    @Test
    fun `sirve para cualquier lista, no solo para los ignorados`() {
        // Sin artículo a propósito: "los 37 ignorados" pero "las 4 palabras", y meter el
        // género como parámetro para tres sitios no compensa. Así se lee igual de bien.
        assertEquals("Ver 4 palabras  ▾", Plegable.rotulo(4, "palabra", "palabras", false))
        assertEquals("Ver 1 palabra  ▾", Plegable.rotulo(1, "palabra", "palabras", false))
        assertEquals("Ver 31 subforos  ▾", Plegable.rotulo(31, "subforo", "subforos", false))
    }

    @Test
    fun `un numero imposible no revienta ni miente`() {
        assertEquals("", Plegable.rotulo(-1, "ignorado", "ignorados", false))
    }
}
