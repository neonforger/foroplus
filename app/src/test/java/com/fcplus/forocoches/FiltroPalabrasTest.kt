package com.fcplus.forocoches

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FiltroPalabrasTest {

    private val deFabrica = KeywordRepository.DEFAULT_KEYWORDS.toList()

    // ── El bug que lo destapó (Márquez, 2026-09-16) ───────────────────────────
    // "PP" viene de fábrica y "apple" lo contiene EN MEDIO, así que el hilo oficial
    // de Apple desaparecía del listado, de la búsqueda y de Suscripciones a la vez.

    @Test
    fun `el hilo de Apple no lo esconde el filtro de politica`() {
        assertFalse(
            FiltroPalabras.hayQueOcultar(
                "Apple iPhone - Hilo Oficial (vol. 230) - Burdeos es el color", deFabrica
            )
        )
    }

    @Test
    fun `app y WhatsApp tampoco los esconde PP`() {
        assertFalse(FiltroPalabras.hayQueOcultar("Mejor app para grabar rutas", deFabrica))
        assertFalse(FiltroPalabras.hayQueOcultar("WhatsApp se cae otra vez", deFabrica))
    }

    // ── Lo que el filtro SÍ tiene que seguir escondiendo ──────────────────────

    @Test
    fun `sigue escondiendo la palabra de verdad`() {
        assertTrue(FiltroPalabras.hayQueOcultar("El PP gana las elecciones", deFabrica))
        assertTrue(FiltroPalabras.hayQueOcultar("Rueda de prensa del Gobierno", deFabrica))
    }

    @Test
    fun `la puntuacion no protege a la palabra`() {
        assertTrue(FiltroPalabras.hayQueOcultar("¿Elecciones ya?", deFabrica))
        assertTrue(FiltroPalabras.hayQueOcultar("(PSOE) otra vez", deFabrica))
        assertTrue(FiltroPalabras.hayQueOcultar("Hablemos de política.", deFabrica))
    }

    @Test
    fun `casa aunque la palabra abra el titulo`() {
        assertTrue(FiltroPalabras.hayQueOcultar("VOX propone algo", deFabrica))
    }

    // ── La regla: casa al PRINCIPIO de palabra, no en medio ───────────────────
    // Se conserva el prefijo a propósito: quien escribe "eleccion" espera tapar
    // "elecciones", y eso ya funcionaba. Lo que se corta es la coincidencia
    // interior, que es la que escondía Apple.

    @Test
    fun `el prefijo sigue valiendo para plurales y derivados`() {
        assertTrue(FiltroPalabras.hayQueOcultar("Las eleccion es del domingo", listOf("eleccion")))
        assertTrue(FiltroPalabras.hayQueOcultar("Resultados de las elecciones", listOf("eleccion")))
    }

    @Test
    fun `no casa dentro de una palabra`() {
        assertFalse(FiltroPalabras.hayQueOcultar("Compro chapa y pintura", listOf("apa")))
        assertFalse(FiltroPalabras.hayQueOcultar("Suzuki Vitara", listOf("ara")))
    }

    @Test
    fun `los acentos son letras, no separadores`() {
        // Si "í" contara como separador, "polí|tica" partiría la palabra y "tica"
        // casaría como si empezara uno nueva.
        assertFalse(FiltroPalabras.hayQueOcultar("Hablemos de política", listOf("tica")))
        assertTrue(FiltroPalabras.hayQueOcultar("Hablemos de política", listOf("política")))
    }

    @Test
    fun `mayusculas y minusculas dan igual`() {
        assertTrue(FiltroPalabras.hayQueOcultar("el pp y el PSOE", listOf("PP")))
        assertTrue(FiltroPalabras.hayQueOcultar("EL PP MANDA", listOf("pp")))
    }

    // ── Bordes ────────────────────────────────────────────────────────────────

    @Test
    fun `sin palabras no se oculta nada`() {
        assertFalse(FiltroPalabras.hayQueOcultar("Lo que sea", emptyList()))
    }

    @Test
    fun `una palabra vacia o en blanco no oculta el foro entero`() {
        assertFalse(FiltroPalabras.hayQueOcultar("Lo que sea", listOf("", "   ")))
    }

    @Test
    fun `una palabra guardada con espacios de sobra sigue valiendo`() {
        assertTrue(FiltroPalabras.hayQueOcultar("Mitin del PSOE", listOf("  psoe  ")))
    }

    @Test
    fun `una palabra de varias palabras casa por su principio`() {
        assertTrue(FiltroPalabras.hayQueOcultar("Vamos al Congreso de los Diputados", listOf("congreso de los")))
        assertFalse(FiltroPalabras.hayQueOcultar("Vamos al Congreso", listOf("congreso de los")))
    }

    @Test
    fun `un numero pegado no abre palabra nueva`() {
        assertFalse(FiltroPalabras.hayQueOcultar("Multa de 300pp", listOf("pp")))
    }
}
