package com.fcplus.forocoches

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class LupaActividadTest {

    // ── Dónde sale la lupa ─────────────────────────────────────────────────────

    @Test
    fun `sin sesion no hay lupa porque la busqueda de FC la exige`() {
        assertFalse(LupaActividad.disponible(logueado = false, modo = "posts", enHilo = ""))
    }

    @Test
    fun `sale en las tres listas de la actividad de un usuario`() {
        for (modo in listOf("posts", "started", "threads")) {
            assertTrue(modo, LupaActividad.disponible(true, modo, ""))
        }
    }

    @Test
    fun `no sale en sus mensajes acotados a un hilo`() {
        assertFalse(LupaActividad.disponible(true, "posts", enHilo = "8076555"))
    }

    @Test
    fun `no sale en un modo desconocido`() {
        assertFalse(LupaActividad.disponible(true, "favs", ""))
    }

    // ── Qué se pide ─────────────────────────────────────────────────────────────

    @Test
    fun `en sus mensajes busca por mensajes`() {
        assertEquals(
            LupaActividad.Peticion.Buscar("tesla", "pepe", porTitulos = false, participa = false),
            LupaActividad.decidir("  tesla ", "pepe", "posts")
        )
    }

    @Test
    fun `en hilos suyos busca por titulos los que abrio`() {
        assertEquals(
            LupaActividad.Peticion.Buscar("tesla", "pepe", porTitulos = true, participa = false),
            LupaActividad.decidir("tesla", "pepe", "started")
        )
    }

    @Test
    fun `en participa busca por titulos donde escribio`() {
        assertEquals(
            LupaActividad.Peticion.Buscar("tesla", "pepe", porTitulos = true, participa = true),
            LupaActividad.decidir("tesla", "pepe", "threads")
        )
    }

    @Test
    fun `el usuario va sin la arroba`() {
        val p = LupaActividad.decidir("tesla", "@pepe", "posts") as LupaActividad.Peticion.Buscar
        assertEquals("pepe", p.usuario)
    }

    @Test
    fun `vacio no busca`() {
        assertTrue(LupaActividad.decidir("   ", "pepe", "posts") is LupaActividad.Peticion.NoVale)
    }

    @Test
    fun `menos de 3 caracteres no busca porque FC no lo admite`() {
        val p = LupaActividad.decidir("ab", "pepe", "posts")
        assertEquals(LupaActividad.Peticion.NoVale("Escribe al menos 3 caracteres"), p)
    }

    @Test
    fun `sin usuario o con modo raro no busca`() {
        assertTrue(LupaActividad.decidir("tesla", "", "posts") is LupaActividad.Peticion.NoVale)
        assertTrue(LupaActividad.decidir("tesla", "pepe", "favs") is LupaActividad.Peticion.NoVale)
    }

    // ── Lo que se le dice a quien busca ────────────────────────────────────────

    @Test
    fun `la pista dice donde se busca`() {
        assertEquals("Buscar en sus mensajes", LupaActividad.pista("posts"))
        assertEquals("Buscar en los títulos de sus hilos", LupaActividad.pista("started"))
        assertEquals("Buscar en los títulos donde participa", LupaActividad.pista("threads"))
    }

    @Test
    fun `vacio por mensajes dice que no se encontro y los limites del buscador`() {
        val t = LupaActividad.vacio("tesla", "pepe", porTitulos = false)
        assertTrue(t, t.startsWith("Ningún mensaje de @pepe contiene «tesla»"))
        assertTrue(t, t.contains("menos de 3 letras"))
        assertTrue(t, t.contains("ñ o tilde"))
        // Por mensajes no se habla de hilos que no tiene indexados.
        assertFalse(t, t.contains("todos los hilos"))
    }

    @Test
    fun `vacio por titulos de un usuario avisa de que FC no tiene todos sus hilos`() {
        val t = LupaActividad.vacio("tesla", "@pepe", porTitulos = true)
        assertTrue(t, t.startsWith("Ningún hilo de @pepe tiene «tesla» en el título"))
        assertTrue(t, t.contains("no tiene todos los hilos de cada usuario"))
    }

    @Test
    fun `vacio sin usuario es la busqueda de todo el foro`() {
        val t = LupaActividad.vacio("tesla", "", porTitulos = true)
        assertTrue(t, t.startsWith("Ningún hilo tiene «tesla» en el título"))
        assertFalse(t, t.contains("@"))
        assertFalse(t, t.contains("todos los hilos"))
    }

    @Test
    fun `cabecera de participa`() {
        assertEquals("\"tesla\" · donde participa @pepe", LupaActividad.cabeceraParticipa("tesla", "@pepe"))
    }

    @Test
    fun `participa es otra busqueda aunque coincidan palabra y usuario`() {
        assertNotEquals(LupaActividad.clave("tesla", "pepe", true), LupaActividad.clave("tesla", "pepe", false))
        // Sin participa, la clave de siempre (no cambia lo que ya funcionaba).
        assertEquals(BusquedaPorUsuario.clave("tesla", "pepe"), LupaActividad.clave("tesla", "pepe", false))
    }

    // ── Volver a la lista entera ───────────────────────────────────────────────

    private fun guardada(filas: List<String> = listOf("a", "b")) =
        ActividadGuardada("pepe", "posts", "", filas, 3, "https://x/search.php?searchid=1", false, 5, -20)

    @Test
    fun `se recupera la de la misma pantalla`() {
        val g = guardada()
        assertSame(g, ActividadGuardada.tomar(g, "pepe", "posts", ""))
    }

    @Test
    fun `no se recupera la de otro usuario, otra lista u otro hilo`() {
        val g = guardada()
        assertNull(ActividadGuardada.tomar(g, "juan", "posts", ""))
        assertNull(ActividadGuardada.tomar(g, "pepe", "started", ""))
        assertNull(ActividadGuardada.tomar(g, "pepe", "posts", "123"))
    }

    @Test
    fun `una guardada vacia no sirve y se pide otra vez`() {
        assertNull(ActividadGuardada.tomar(guardada(emptyList()), "pepe", "posts", ""))
        assertNull(ActividadGuardada.tomar<String>(null, "pepe", "posts", ""))
    }

    // ── Fin de una lista de búsqueda (gotcha 37) ────────────────────────────────

    @Test
    fun `una pagina siguiente sin hilos es el final`() {
        assertTrue(FinDeLista.agotadaTrasError("empty", pagina = 4, hayFilas = true))
    }

    @Test
    fun `un error de red no acaba la lista, se reintenta al deslizar`() {
        assertFalse(FinDeLista.agotadaTrasError("TypeError: Failed to fetch", 4, true))
    }

    @Test
    fun `la primera pagina vacia no es agotar, es que no hay resultados`() {
        assertFalse(FinDeLista.agotadaTrasError("empty", pagina = 1, hayFilas = false))
        assertFalse(FinDeLista.agotadaTrasError("empty", pagina = 1, hayFilas = true))
    }
}
