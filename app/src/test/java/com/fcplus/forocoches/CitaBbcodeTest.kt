package com.fcplus.forocoches

import org.junit.Assert.assertEquals
import org.junit.Test

class CitaBbcodeTest {

    /** La cita REAL que devolvió FC el 2026-08-28 para el mensaje 518303666. */
    private val deFc =
        "[QUOTE=V. Jones;518303666]Estan naranajas :roto2:\n\n" +
            "[IMG]https://i.imgur.com/7kaUa1L.png[/IMG]\n\n" +
            "[IMG]https://i.imgur.com/oIkmSUA.png[/IMG][/QUOTE]"

    @Test
    fun `el envoltorio se quita y lo de dentro no se toca`() {
        val c = CitaBbcode.sinEnvoltura(deFc)
        assertEquals(
            "Estan naranajas :roto2:\n\n" +
                "[IMG]https://i.imgur.com/7kaUa1L.png[/IMG]\n\n" +
                "[IMG]https://i.imgur.com/oIkmSUA.png[/IMG]",
            c
        )
    }

    @Test
    fun `sin envoltorio se devuelve tal cual`() {
        assertEquals("hola", CitaBbcode.sinEnvoltura("hola"))
        assertEquals("", CitaBbcode.sinEnvoltura(""))
    }

    @Test
    fun `la tarjeta dice que hay fotos, no escupe la URL`() {
        // Lo que el usuario quiere saber es QUÉ cita, y una tarjeta llena de
        // https://i.imgur.com/... no se lo dice.
        assertEquals("Estan naranajas :roto2: 🖼 imagen 🖼 imagen", CitaBbcode.previsualizacion(deFc))
    }

    @Test
    fun `las demas etiquetas no se enseñan pero su texto si`() {
        assertEquals("mira esto", CitaBbcode.previsualizacion("[QUOTE=A;1]mira [B]esto[/B][/QUOTE]"))
    }

    @Test
    fun `si el usuario recorta, no se publica el cartel de la foto`() {
        // El diálogo de recortar se rellena con la vista previa. Lo que salga de ahí se
        // publica tal cual, y "🖼 imagen" dentro de un mensaje del foro es basura.
        assertEquals("Estan naranajas :roto2:",
            CitaBbcode.sinMarcas("Estan naranajas :roto2: 🖼 imagen 🖼 imagen"))
        assertEquals("", CitaBbcode.sinMarcas("🖼 imagen"))
    }

    @Test
    fun `un mensaje que era SOLO fotos no queda vacio`() {
        // El caso del tester: dos fotos y nada de texto. Sin esto la tarjeta salía en blanco.
        assertEquals("🖼 imagen 🖼 imagen",
            CitaBbcode.previsualizacion("[QUOTE=A;1][IMG]a.png[/IMG]\n[IMG]b.png[/IMG][/QUOTE]"))
    }

    // ── aTexto: las citas pasan a la caja para editarlas ─────────────────────

    @Test
    fun `sin texto las citas quedan arriba y el cursor en la linea de debajo`() {
        val (t, cursor) = CitaBbcode.aTexto(listOf("[QUOTE=ana;1]hola[/QUOTE]\n"), "")
        assertEquals("[QUOTE=ana;1]hola[/QUOTE]\n\n", t)
        assertEquals("[QUOTE=ana;1]hola[/QUOTE]\n".length, cursor)
    }

    @Test
    fun `varias citas conservan su orden y lo escrito va detras`() {
        val (t, _) = CitaBbcode.aTexto(listOf("[QUOTE=a;1]x[/QUOTE]", "[QUOTE=b;2]y[/QUOTE]"), "mi respuesta")
        assertEquals("[QUOTE=a;1]x[/QUOTE]\n[QUOTE=b;2]y[/QUOTE]\n\nmi respuesta", t)
    }

    @Test
    fun `el cursor cae en la linea en blanco entre citas y respuesta`() {
        val (t, cursor) = CitaBbcode.aTexto(listOf("[QUOTE=a;1]x[/QUOTE]"), "resp")
        assertEquals('\n', t[cursor - 1])
        assertEquals("\nresp", t.substring(cursor))
    }

    @Test
    fun `sin citas no cambia nada`() {
        assertEquals("hola" to 4, CitaBbcode.aTexto(listOf("", " "), "hola"))
    }
}
