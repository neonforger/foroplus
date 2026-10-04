package com.fcplus.forocoches

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CitaPrivadaTest {

    /** La forma real con la que FC rellena la respuesta a un MP. */
    private val citaDeFc =
        "[QUOTE=Fulanito;518167826]Hola, te escribo por lo del coche.\n" +
            "¿Sigue disponible?[/QUOTE]"

    @Test
    fun `se saca quien escribio lo citado`() {
        assertEquals("Fulanito", CitaPrivada.autor(citaDeFc))
    }

    @Test
    fun `un quote sin id tambien da el nombre`() {
        assertEquals("Fulanito", CitaPrivada.autor("[QUOTE=Fulanito]hola[/QUOTE]"))
    }

    @Test
    fun `sin quote no se inventa un autor`() {
        assertEquals("", CitaPrivada.autor("texto suelto"))
        assertEquals("", CitaPrivada.autor(""))
    }

    @Test
    fun `la tarjeta enseña el texto, no las etiquetas`() {
        val vista = CitaPrivada.previsualizacion(citaDeFc)
        assertEquals("Hola, te escribo por lo del coche. ¿Sigue disponible?", vista)
        assertFalse(vista.contains("["))
    }

    @Test
    fun `otras etiquetas de BBCode tampoco se enseñan`() {
        val vista = CitaPrivada.previsualizacion("[QUOTE=A;1]mira esto [B]en negrita[/B][/QUOTE]")
        assertEquals("mira esto en negrita", vista)
    }

    @Test
    fun `la cita anidada NO se enseña, solo lo que escribió quien te responde`() {
        // La forma REAL con la que FC rellena una respuesta en una conversación de ida y
        // vuelta (sondeada el 2026-08-27): tu mensaje viejo va anidado y PEGADO al principio.
        // Enseñarlo hacía creer que estabas respondiendo al primer mensaje.
        val vista = CitaPrivada.previsualizacion(
            "[QUOTE=Iznogud;1][QUOTE=neonforger;2]lo que dije yo hace tres días[/QUOTE]" +
                "y esto es lo que me contesta él ahora[/QUOTE]"
        )
        assertEquals("y esto es lo que me contesta él ahora", vista)
        assertFalse(vista, vista.contains("lo que dije yo"))
    }

    @Test
    fun `con varias vueltas anidadas tampoco se cuela ninguna`() {
        val vista = CitaPrivada.previsualizacion(
            "[QUOTE=A;1][QUOTE=B;2][QUOTE=A;3]la primera[/QUOTE]la segunda[/QUOTE]la última[/QUOTE]"
        )
        assertEquals("la última", vista)
    }

    @Test
    fun `el autor sigue siendo el de la cita de FUERA, que es a quien respondes`() {
        assertEquals("Iznogud", CitaPrivada.autor(
            "[QUOTE=Iznogud;1][QUOTE=neonforger;2]viejo[/QUOTE]nuevo[/QUOTE]"
        ))
    }

    @Test
    fun `al enviar va la cita delante y la respuesta detras`() {
        assertEquals("$citaDeFc\n\nSí, sigue.", CitaPrivada.montar(citaDeFc, "Sí, sigue."))
    }

    @Test
    fun `quitando la cita va solo la respuesta, sin saltos sueltos`() {
        // El fallo clásico: condicionar el texto y no el pegamento, y mandar "\n\nSí, sigue."
        assertEquals("Sí, sigue.", CitaPrivada.montar("", "Sí, sigue."))
        assertEquals("Sí, sigue.", CitaPrivada.montar("   ", "  Sí, sigue.  "))
    }

    @Test
    fun `sin respuesta no se manda la cita con cola`() {
        assertEquals(citaDeFc, CitaPrivada.montar(citaDeFc, ""))
    }

    @Test
    fun `sin nada no se manda nada`() {
        assertEquals("", CitaPrivada.montar("", ""))
    }
}
