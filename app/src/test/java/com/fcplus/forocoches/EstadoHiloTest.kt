package com.fcplus.forocoches

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * El estado de vBulletin que FC deja en un COMENTARIO antes del icono de cada fila
 * (`<!-- _dot_hot_new-->`). Sondeado el 2026-09-27: `_new`, `_hot` y `_hot_new` en 420 filas de
 * cinco subforos, y `_dot` al escribir en un hilo de Pruebas. `_lock` no se ha visto nunca.
 */
class EstadoHiloTest {

    @Test
    fun `el comentario vacio es un hilo sin nada`() {
        assertEquals(EstadoHilo(), EstadoHilo.de(""))
    }

    @Test
    fun `has participado y esta caliente`() {
        val e = EstadoHilo.de("_dot_hot")
        assertTrue(e.participado)
        assertFalse(e.cerrado)
    }

    @Test
    fun `cerrado aunque venga mezclado con todo lo demas`() {
        // Orden de vBulletin: dot, hot, lock, new.
        val e = EstadoHilo.de("_dot_hot_lock_new")
        assertTrue(e.participado)
        assertTrue(e.cerrado)
    }

    @Test
    fun `new y hot solos no marcan nada de lo que pinta la fila`() {
        // La novedad la decide isThreadUnread (FC + memoria local), no este comentario: si no,
        // el sobre y la negrita podrían contradecirse.
        assertEquals(EstadoHilo(), EstadoHilo.de("_hot_new"))
    }

    @Test
    fun `se tolera basura alrededor`() {
        assertTrue(EstadoHilo.de("  _dot ").participado)
        assertFalse(EstadoHilo.de("_dotty").participado)
    }
}
