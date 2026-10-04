package com.fcplus.forocoches

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RestauracionTest {

    @Test
    fun `el mismo hilo con su ancla cargada se restaura sin pedir nada`() {
        assertFalse(
            Restauracion.hayQueRecargar(
                hiloEnPantalla = true, hayAncla = true, anclaCargada = true,
                paginaGuardada = 3, paginaCargada = 3
            )
        )
    }

    @Test
    fun `mismo hilo pero el ancla ya no esta cargada obliga a traer su pagina`() {
        // El fallo real: saltas a una cita de otra pagina y el atras no hacia nada.
        assertTrue(
            Restauracion.hayQueRecargar(
                hiloEnPantalla = true, hayAncla = true, anclaCargada = false,
                paginaGuardada = 3, paginaCargada = 7
            )
        )
    }

    @Test
    fun `otro hilo siempre se recarga`() {
        assertTrue(
            Restauracion.hayQueRecargar(
                hiloEnPantalla = false, hayAncla = true, anclaCargada = true,
                paginaGuardada = 1, paginaCargada = 1
            )
        )
        assertTrue(
            Restauracion.hayQueRecargar(
                hiloEnPantalla = false, hayAncla = false, anclaCargada = false,
                paginaGuardada = 1, paginaCargada = 1
            )
        )
    }

    @Test
    fun `una pagina sin ancla que no es la pintada se recarga`() {
        // Green Floyd (Telegram 2161) y Marquez (2167), medido el 2026-09-27 en t=10816085:
        // 1 -> 2 -> 3 sin hacer scroll en la 2. La entrada de la 2 se quedo sin ancla, y "sin
        // ancla vale cualquier sitio" dejaba la 3 pintada: el primer atras no hacia nada y el
        // segundo saltaba a la 1.
        assertTrue(
            Restauracion.hayQueRecargar(
                hiloEnPantalla = true, hayAncla = false, anclaCargada = false,
                paginaGuardada = 2, paginaCargada = 3
            )
        )
    }

    @Test
    fun `una pagina sin ancla que ya es la pintada no se recarga`() {
        assertFalse(
            Restauracion.hayQueRecargar(
                hiloEnPantalla = true, hayAncla = false, anclaCargada = false,
                paginaGuardada = 2, paginaCargada = 2
            )
        )
    }

    // ── Volver a una lista de resultados de busqueda ──────────────────────────
    // Reportado por Green Floyd (2026-09-13) y repetido por Rober (2026-09-14): al salir de
    // un resultado con el atras aparecia el subforo, no los resultados.

    @Test
    fun `si lo pintado ya son esos resultados no se repite la busqueda`() {
        assertFalse(
            Restauracion.hayQueRehacerBusqueda(
                queryGuardada = "merengue", sourceCargado = "search",
                queryCargada = "merengue", listaCargada = true
            )
        )
    }

    @Test
    fun `si lo pintado es otra lista se rehace la busqueda`() {
        assertTrue(
            Restauracion.hayQueRehacerBusqueda(
                queryGuardada = "merengue", sourceCargado = "home",
                queryCargada = "merengue", listaCargada = true
            )
        )
    }

    @Test
    fun `si lo pintado es otra busqueda se rehace`() {
        assertTrue(
            Restauracion.hayQueRehacerBusqueda(
                queryGuardada = "merengue", sourceCargado = "search",
                queryCargada = "nike", listaCargada = true
            )
        )
    }

    @Test
    fun `una busqueda a medio cargar se rehace`() {
        assertTrue(
            Restauracion.hayQueRehacerBusqueda(
                queryGuardada = "merengue", sourceCargado = "search",
                queryCargada = "merengue", listaCargada = false
            )
        )
    }

    /** Pila vieja o entrada por notificacion: no hay consulta que repetir. */
    @Test
    fun `sin consulta guardada no se rehace nada`() {
        assertFalse(
            Restauracion.hayQueRehacerBusqueda(
                queryGuardada = "", sourceCargado = "home",
                queryCargada = "", listaCargada = true
            )
        )
    }

    // ── Volver a la lista de un subforo, al TOP o al Popurri ──────────────────
    // javier hg (Telegram 2185, 2026-09-26): entrabas a un hilo desde los hilos del momento y
    // al volver aparecia el General. La pila guardaba "home" y el atras rehacia Inicio.

    @Test
    fun `volver al TOP que sigue pintado no recarga nada`() {
        assertFalse(
            Restauracion.hayQueRehacerLista(
                sourceGuardado = "top", fidGuardado = 2,
                sourceCargado = "top", fidCargado = 2, listaCargada = true
            )
        )
    }

    @Test
    fun `volver al TOP con otra lista pintada lo rehace`() {
        assertTrue(
            Restauracion.hayQueRehacerLista(
                sourceGuardado = "top", fidGuardado = 2,
                sourceCargado = "home", fidCargado = 2, listaCargada = true
            )
        )
    }

    @Test
    fun `volver a un subforo con otro subforo pintado lo rehace`() {
        assertTrue(
            Restauracion.hayQueRehacerLista(
                sourceGuardado = "home", fidGuardado = 2,
                sourceCargado = "home", fidCargado = 6, listaCargada = true
            )
        )
    }

    @Test
    fun `en el Popurri el subforo no cuenta`() {
        assertFalse(
            Restauracion.hayQueRehacerLista(
                sourceGuardado = "popurri", fidGuardado = 2,
                sourceCargado = "popurri", fidCargado = 6, listaCargada = true
            )
        )
    }

    @Test
    fun `una lista sin cargar siempre se rehace`() {
        assertTrue(
            Restauracion.hayQueRehacerLista(
                sourceGuardado = "home", fidGuardado = 2,
                sourceCargado = "home", fidCargado = 2, listaCargada = false
            )
        )
    }

    @Test
    fun `una entrada sin subforo vale para el que haya`() {
        assertFalse(
            Restauracion.hayQueRehacerLista(
                sourceGuardado = "home", fidGuardado = 0,
                sourceCargado = "home", fidCargado = 6, listaCargada = true
            )
        )
    }
}
