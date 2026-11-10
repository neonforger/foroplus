package com.fcplus.forocoches

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BarraTemporalTest {

    private val visibles = setOf(2, 17, 43)

    // ── Qué subforo escondido lleva pestaña temporal ──────────────────────────

    @Test
    fun `un subforo escondido abierto a proposito lleva pestana temporal`() {
        assertEquals(50, BarraTemporal.temporal("home", 50, setOf(50), visibles))
        assertEquals(50, BarraTemporal.temporal("top", 50, setOf(50), visibles))
    }

    @Test
    fun `si no se abrio a proposito no hay temporal y se salta como siempre`() {
        assertNull(BarraTemporal.temporal("home", 50, emptySet(), visibles))
    }

    @Test
    fun `si ya esta en la barra no hace falta temporal`() {
        assertNull(BarraTemporal.temporal("home", 17, setOf(17), visibles))
    }

    @Test
    fun `en una seccion no hay temporal`() {
        for (s in listOf("popurri", "mas18", "musica", "favs")) {
            assertNull(s, BarraTemporal.temporal(s, 50, setOf(50), visibles))
        }
    }

    @Test
    fun `el temporal es del subforo actual, no de otro recordado`() {
        assertNull(BarraTemporal.temporal("home", 2, setOf(50), visibles))
    }

    // ── Cuándo pintarPestanas salta al primero visible ────────────────────────

    @Test
    fun `salta si el subforo actual no se ve y no hay temporal`() {
        assertTrue(BarraTemporal.saltar("home", true, 50, visibles, hayTemporal = false))
    }

    @Test
    fun `no salta con temporal, en la barra, en otra fuente o sin visibles`() {
        assertFalse(BarraTemporal.saltar("home", true, 50, visibles, hayTemporal = true))
        assertFalse(BarraTemporal.saltar("home", true, 17, visibles, hayTemporal = false))
        assertFalse(BarraTemporal.saltar("top", true, 50, visibles, hayTemporal = false))
        assertFalse(BarraTemporal.saltar("home", false, 50, emptySet(), hayTemporal = false))
    }

    // ── Abrir un subforo por enlace (migas, "ir al subforo") ──────────────────

    @Test
    fun `un enlace a un subforo fuera de la barra lo recuerda aunque los nombres no hayan llegado`() {
        assertEquals(setOf(50), BarraTemporal.alAbrirPorEnlace(50, listOf(-3, 2, 17), emptySet()))
    }

    @Test
    fun `un enlace a un subforo de la barra no recuerda nada`() {
        assertEquals(emptySet<Int>(), BarraTemporal.alAbrirPorEnlace(17, listOf(-3, 2, 17), emptySet()))
    }

    @Test
    fun `lo ya recordado se conserva`() {
        assertEquals(setOf(9, 50), BarraTemporal.alAbrirPorEnlace(50, listOf(2), setOf(9)))
    }

    // ── Organizar ─────────────────────────────────────────────────────────────

    @Test
    fun `organizar olvida los subforos abiertos fuera de la barra`() {
        assertEquals(emptySet<Int>(), BarraTemporal.alOrganizar(setOf(50, 9)))
    }

    // ── Elegir en el selector ─────────────────────────────────────────────────

    private val tabs = listOf(-3, 2, 17)

    @Test
    fun `elegir el sitio actual no hace nada`() {
        assertEquals(BarraTemporal.Eleccion.Nada, BarraTemporal.elegir(2, 2, tabs, 2))
    }

    @Test
    fun `una pestana sin marcar se marca y se abre`() {
        assertEquals(BarraTemporal.Eleccion.EnBarra(marcar = true), BarraTemporal.elegir(17, 2, tabs, 2))
    }

    @Test
    fun `una pestana ya marcada pero de otra fuente solo se abre, sin volver a marcarla`() {
        // Marcada General pero se está en, p. ej., Suscripciones: ni select (dispararía
        // onTabReselected y cargaría dos veces) ni nada más que abrir.
        assertEquals(BarraTemporal.Eleccion.EnBarra(marcar = false), BarraTemporal.elegir(2, 50, tabs, 2))
    }

    @Test
    fun `un subforo que no esta en la barra es un escondido`() {
        assertEquals(BarraTemporal.Eleccion.Escondido, BarraTemporal.elegir(50, 2, tabs, 2))
    }

    // ── Qué ofrece el selector como "en tu barra" ─────────────────────────────

    @Test
    fun `la pestana temporal no cuenta como de la barra`() {
        val pestanas = listOf(Sitio(-3, "Música"), Sitio(2, "General"), Sitio(50, "Basket"))
        assertEquals(listOf(Sitio(-3, "Música"), Sitio(2, "General")), BarraTemporal.enBarra(pestanas, 50))
        assertEquals(pestanas, BarraTemporal.enBarra(pestanas, BarraTemporal.NINGUNO))
    }

    @Test
    fun `el Popurri, que vale menos uno, no se confunde con la ausencia de temporal`() {
        val pestanas = listOf(Sitio(-1, "Popurrí"), Sitio(2, "General"))
        assertEquals(pestanas, BarraTemporal.enBarra(pestanas, BarraTemporal.NINGUNO))
    }
}
