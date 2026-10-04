package com.fcplus.forocoches

import org.junit.Assert.assertEquals
import org.junit.Test

class SmileysEnTextoTest {

    private val codigos = listOf(":roto2:", ":qmeparto:", ":meparto:", ":)")

    private fun cods(t: String) = SmileysEnTexto.buscar(t, codigos).map { it.codigo }

    @Test
    fun `un codigo cerrado se encuentra con sus posiciones`() {
        assertEquals(listOf(SmileysEnTexto.Tramo(5, 12, ":roto2:")), SmileysEnTexto.buscar("hola :roto2: xd", codigos))
    }

    @Test
    fun `a medio escribir no es nada`() {
        // Se convierte cuando lo ACABAS de escribir, no antes.
        assertEquals(emptyList<String>(), cods("hola :roto2"))
        assertEquals(emptyList<String>(), cods("hola :rot"))
    }

    @Test
    fun `varios seguidos y pegados`() {
        assertEquals(listOf(":roto2:", ":roto2:", ":meparto:"), cods(":roto2::roto2:x:meparto:"))
    }

    @Test
    fun `gana el mas largo y no se solapan`() {
        // ":qmeparto:" contiene "meparto:" pero es otro smiley.
        assertEquals(listOf(":qmeparto:"), cods("jaja :qmeparto:"))
    }

    @Test
    fun `codigos que no son de dos puntos tambien valen`() {
        assertEquals(listOf(":)"), cods("vale :)"))
    }

    @Test
    fun `lo que no esta en la lista de FC no se toca`() {
        assertEquals(emptyList<String>(), cods("hora 10:30: listo :inventado:"))
    }

    @Test
    fun `sin lista o sin texto no hay nada`() {
        assertEquals(emptyList<String>(), SmileysEnTexto.buscar(":roto2:", emptyList()))
        assertEquals(emptyList<String>(), SmileysEnTexto.buscar("", codigos))
    }
}
