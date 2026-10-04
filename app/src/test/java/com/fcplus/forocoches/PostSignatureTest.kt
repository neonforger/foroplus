package com.fcplus.forocoches

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * La firma es opcional (Opciones → "Firma de ForoPlus"). Lo que se prueba aquí es el montaje
 * del mensaje, que es justo lo que NO se puede verificar en el dispositivo sin publicar un
 * post real en la cuenta del dueño.
 */
class PostSignatureTest {

    @Test
    fun `activada, la firma va al final con aire por encima`() {
        val out = PostSignature.append("hola", enabled = true)
        assertEquals("hola\n\n\n[SIZE=1]Enviado desde ForoPlus[/SIZE]", out)
    }

    @Test
    fun `desactivada, el cuerpo sale intacto`() {
        assertEquals("hola", PostSignature.append("hola", enabled = false))
    }

    @Test
    fun `desactivada, no deja ni saltos de linea sueltos al final`() {
        // El fallo clásico sería añadir el hueco siempre y solo condicionar el texto:
        // el post acabaría con tres líneas en blanco invisibles.
        val out = PostSignature.append("hola", enabled = false)
        assertFalse(out.endsWith("\n"))
        assertEquals("hola".length, out.length)
    }

    @Test
    fun `desactivada, no queda ni rastro del texto de la firma`() {
        val out = PostSignature.append("un mensaje cualquiera", enabled = false)
        assertFalse(out.contains("ForoPlus"))
        assertFalse(out.contains("SIZE"))
    }

    @Test
    fun `respeta el cuerpo tal cual, tambien con BBCode y saltos dentro`() {
        val body = "[QUOTE=pepe;1]hola[/QUOTE]\nmi respuesta"
        assertTrue(PostSignature.append(body, enabled = true).startsWith(body))
        assertEquals(body, PostSignature.append(body, enabled = false))
    }

    @Test
    fun `cuerpo vacio con la firma activada no revienta`() {
        assertEquals("\n\n\n[SIZE=1]Enviado desde ForoPlus[/SIZE]", PostSignature.append("", true))
    }
}
