package com.fcplus.forocoches

import org.junit.Assert.assertEquals
import org.junit.Test

class PageBarTest {

    @Test
    fun `una sola pagina no pinta ventana`() {
        assertEquals(listOf(1), pageWindow(current = 1, total = 1))
    }

    @Test
    fun `menos paginas que el hueco las pinta todas`() {
        assertEquals(listOf(1, 2, 3), pageWindow(current = 2, total = 3))
    }

    @Test
    fun `en medio centra la ventana en la pagina actual`() {
        assertEquals(listOf(5, 6, 7, 8, 9), pageWindow(current = 7, total = 20))
    }

    @Test
    fun `al principio no se sale por la izquierda`() {
        assertEquals(listOf(1, 2, 3, 4, 5), pageWindow(current = 1, total = 20))
        assertEquals(listOf(1, 2, 3, 4, 5), pageWindow(current = 2, total = 20))
    }

    @Test
    fun `al final no se sale por la derecha`() {
        assertEquals(listOf(16, 17, 18, 19, 20), pageWindow(current = 20, total = 20))
        assertEquals(listOf(16, 17, 18, 19, 20), pageWindow(current = 19, total = 20))
    }

    @Test
    fun `la pagina actual fuera de rango se recorta`() {
        assertEquals(listOf(1, 2, 3, 4, 5), pageWindow(current = 0, total = 20))
        assertEquals(listOf(16, 17, 18, 19, 20), pageWindow(current = 99, total = 20))
    }

    @Test
    fun `total invalido devuelve la pagina uno`() {
        assertEquals(listOf(1), pageWindow(current = 3, total = 0))
    }

    @Test
    fun `con numeros de tres cifras se ensenan menos para que quepa la barra`() {
        // Medido en el dispositivo: con 5 números de 3 cifras, `»` se sale de los 1080px y
        // la última página deja de ser alcanzable de un toque.
        assertEquals(5, pageSpan(24))
        assertEquals(5, pageSpan(99))
        assertEquals(3, pageSpan(100))
        assertEquals(3, pageSpan(1200))
    }

    @Test
    fun `la ventana estrecha sigue centrada y pegada a los extremos`() {
        assertEquals(listOf(149, 150, 151), pageWindow(150, 800, pageSpan(800)))
        assertEquals(listOf(1, 2, 3), pageWindow(1, 800, pageSpan(800)))
        assertEquals(listOf(798, 799, 800), pageWindow(800, 800, pageSpan(800)))
    }
}
