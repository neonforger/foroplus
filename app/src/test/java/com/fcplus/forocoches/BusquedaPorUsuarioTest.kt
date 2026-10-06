package com.fcplus.forocoches

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class BusquedaPorUsuarioTest {

    // ── Qué se pide según lo que se haya escrito ───────────────────────────────

    @Test
    fun `solo palabras es la busqueda de siempre`() {
        assertEquals(
            PeticionBusqueda.Palabras("tesla", "", porTitulos = true),
            BusquedaPorUsuario.decidir("tesla", "", porTitulos = true)
        )
    }

    @Test
    fun `palabras y usuario buscan esas palabras solo en lo suyo`() {
        assertEquals(
            PeticionBusqueda.Palabras("foroplus", "neonforger", porTitulos = false),
            BusquedaPorUsuario.decidir("foroplus", "neonforger", porTitulos = false)
        )
    }

    @Test
    fun `solo usuario es todo lo suyo, como desde su ficha`() {
        assertEquals(
            PeticionBusqueda.TodoDe("neonforger", porTitulos = true),
            BusquedaPorUsuario.decidir("", "neonforger", porTitulos = true)
        )
    }

    @Test
    fun `sin palabras ni usuario no se busca nada`() {
        assertTrue(BusquedaPorUsuario.decidir("  ", " ", porTitulos = true) is PeticionBusqueda.NoVale)
    }

    @Test
    fun `menos de 3 caracteres no vale tampoco con usuario`() {
        // Lo dice el propio FC: "Para búsquedas de menos de 3 caracteres ... Google".
        val r = BusquedaPorUsuario.decidir("ab", "neonforger", porTitulos = true)
        assertTrue(r is PeticionBusqueda.NoVale)
    }

    @Test
    fun `la arroba y los espacios del usuario sobran`() {
        assertEquals("SVA INV", BusquedaPorUsuario.limpiarUsuario("  @SVA INV "))
        assertEquals(
            PeticionBusqueda.Palabras("tesla", "neonforger", porTitulos = true),
            BusquedaPorUsuario.decidir(" tesla ", "@neonforger", porTitulos = true)
        )
    }

    // ── Lo que dice la cabecera de los resultados ──────────────────────────────

    @Test
    fun `sin usuario la cabecera es la de siempre`() {
        assertEquals("\"tesla\"", BusquedaPorUsuario.cabecera("tesla", "", porMensajes = false))
        assertEquals("\"tesla\" · mensajes", BusquedaPorUsuario.cabecera("tesla", "", porMensajes = true))
    }

    @Test
    fun `con usuario la cabecera dice de quien`() {
        assertEquals("\"tesla\" · hilos de @neonforger", BusquedaPorUsuario.cabecera("tesla", "neonforger", false))
        assertEquals("\"tesla\" · mensajes de @neonforger", BusquedaPorUsuario.cabecera("tesla", "neonforger", true))
    }

    // ── Volver atrás a unos resultados ─────────────────────────────────────────

    @Test
    fun `la misma palabra con otro usuario es otra busqueda`() {
        // Si no, volver a "tesla de @x" enseñaría lo cargado de "tesla" a secas.
        assertNotEquals(BusquedaPorUsuario.clave("tesla", ""), BusquedaPorUsuario.clave("tesla", "neonforger"))
        assertEquals(BusquedaPorUsuario.clave("tesla", "neonforger"), BusquedaPorUsuario.clave("tesla", "@neonforger"))
    }

    @Test
    fun `sin usuario la clave es la palabra, como antes`() {
        assertEquals("tesla", BusquedaPorUsuario.clave("tesla", ""))
    }

    // ── Sugerencias de nombres ─────────────────────────────────────────────────

    @Test
    fun `se piden a partir de 3 letras, como en la web`() {
        assertFalse(BusquedaPorUsuario.pedirSugerencias("ki"))
        assertFalse(BusquedaPorUsuario.pedirSugerencias("@ki"))
        assertTrue(BusquedaPorUsuario.pedirSugerencias("kin"))
    }

    @Test
    fun `las sugerencias de lo que hay escrito se aceptan`() {
        val json = """{"fragment":"kini","nombres":["kini","kini 17","Kinigos"]}"""
        assertEquals(listOf("kini", "kini 17", "Kinigos"), BusquedaPorUsuario.sugerencias(json, "kini"))
    }

    @Test
    fun `las sugerencias de algo que ya no esta escrito se tiran`() {
        // Escribir rápido lanza varias peticiones: la que llega tarde no puede pisar a la buena.
        val json = """{"fragment":"kin","nombres":["kin","kini"]}"""
        assertNull(BusquedaPorUsuario.sugerencias(json, "kini"))
    }

    @Test
    fun `sugerencias rotas o vacias no rompen nada`() {
        assertEquals(emptyList<String>(), BusquedaPorUsuario.sugerencias("""{"fragment":"kini","nombres":[]}""", "kini"))
        assertNull(BusquedaPorUsuario.sugerencias("no es json", "kini"))
    }
}
