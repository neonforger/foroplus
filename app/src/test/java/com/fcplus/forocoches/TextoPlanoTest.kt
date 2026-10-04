package com.fcplus.forocoches

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class TextoPlanoTest {

    /** Lo que Android deja donde había una foto. */
    private val hueco = "\uFFFC"

    @Test
    fun `una imagen citada no deja la cajita OBJ`() {
        // El caso real del 2026-08-28: "Estan naranajas" seguido de una foto.
        val r = TextoPlano.sinHuecosDeImagen("Estan naranajas $hueco")
        assertFalse(r.contains(hueco))
        assertEquals("Estan naranajas ", r)
    }

    @Test
    fun `al quitarla no queda un agujero de dos espacios`() {
        assertEquals("mira esto", TextoPlano.sinHuecosDeImagen("mira $hueco esto"))
    }

    @Test
    fun `varias fotos seguidas`() {
        assertEquals("", TextoPlano.sinHuecosDeImagen("$hueco$hueco$hueco"))
    }

    @Test
    fun `un texto sin fotos no se toca`() {
        val t = "Joder eso es por los doritos."
        assertEquals(t, TextoPlano.sinHuecosDeImagen(t))
    }

    @Test
    fun `los saltos de linea se respetan porque los arregla quien llama`() {
        assertEquals("uno\n\n\ndos", TextoPlano.sinHuecosDeImagen("uno$hueco\n\n\ndos"))
    }

    @Test
    fun `un espacio simple sobrevive`() {
        assertEquals("a b", TextoPlano.sinHuecosDeImagen("a b"))
    }
}

/** Lo que se anadio al poder seleccionar y copiar texto de un mensaje (2026-09-11). */
class TextoPlanoComposerTest {

    @Test
    fun `quita la cajita que viene pegada de otro mensaje`() {
        assertEquals("mira esto: ", TextoPlano.sinCajitas("mira esto: \uFFFC"))
    }

    @Test
    fun `NO le toca los espacios a lo que ha escrito la persona`() {
        // sinHuecosDeImagen si los colapsa; aqui seria meterse donde no nos llaman.
        assertEquals("hola  mundo", TextoPlano.sinCajitas("hola  mundo"))
    }

    @Test
    fun `un texto limpio pasa igual`() {
        assertEquals("sin nada raro", TextoPlano.sinCajitas("sin nada raro"))
    }
}
