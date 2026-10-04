package com.fcplus.forocoches

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Qué hay que volver a pedir cuando el hilo abierto tiene que refrescarse.
 *
 * El bug: **al editar un mensaje la app te devolvía a la página 1** (Green Floyd, 2026-09-13).
 * No era un efecto colateral — `reloadCurrentThread()` pedía la página 1 a propósito, y su
 * propio comentario lo decía. Si editabas en la página 12 de un hilo, acababas arriba del todo
 * con doce páginas de scroll por delante y sin ver el cambio que acababas de hacer.
 */
class RecargaHiloTest {

    @Test
    fun `tras editar se vuelve a la pagina donde estabas, no a la 1`() {
        val r = RecargaHilo.tras(Motivo.EDITAR, paginaCargada = 12, pid = "519188212")
        assertEquals(12, r.pagina)
    }

    /** Y encima resaltado, para VER el cambio que acabas de hacer. */
    @Test
    fun `tras editar se salta al mensaje editado`() {
        val r = RecargaHilo.tras(Motivo.EDITAR, paginaCargada = 12, pid = "519188212")
        assertEquals("519188212", r.pidDestacado)
    }

    @Test
    fun `tras borrar se recarga la misma pagina`() {
        val r = RecargaHilo.tras(Motivo.BORRAR, paginaCargada = 7, pid = "519188212")
        assertEquals(7, r.pagina)
    }

    /** Un post borrado ya no está: saltar a él dejaría la lista quieta sin explicación. */
    @Test
    fun `tras borrar no se salta a ningun mensaje`() {
        val r = RecargaHilo.tras(Motivo.BORRAR, paginaCargada = 7, pid = "519188212")
        assertEquals("", r.pidDestacado)
    }

    /**
     * Cambiar ignorados o palabras filtradas también recargaba por la página 1. Mismo castigo
     * por una acción que ni siquiera va del hilo.
     */
    @Test
    fun `al cambiar los filtros se mantiene la pagina y no se resalta nada`() {
        val r = RecargaHilo.tras(Motivo.FILTROS, paginaCargada = 5, pid = "")
        assertEquals(5, r.pagina)
        assertEquals("", r.pidDestacado)
    }

    /** Sin pid no hay nada que resaltar, aunque el motivo sea editar. */
    @Test
    fun `editar sin pid conocido no resalta`() {
        assertEquals("", RecargaHilo.tras(Motivo.EDITAR, paginaCargada = 3, pid = "").pidDestacado)
    }

    /** Defensa: una página sin cargar todavía (0) no puede pedirse a FC. */
    @Test
    fun `una pagina invalida cae a la primera`() {
        assertEquals(1, RecargaHilo.tras(Motivo.EDITAR, paginaCargada = 0, pid = "x").pagina)
        assertEquals(1, RecargaHilo.tras(Motivo.BORRAR, paginaCargada = -3, pid = "").pagina)
    }
}
