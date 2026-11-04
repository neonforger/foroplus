package com.fcplus.forocoches

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Un carrusel de Instagram se congelaba a medias si el dedo se desviaba un poco en vertical:
 * la lista del hilo se quedaba el gesto (medido el 2026-11-04). El slop típico es 24 px.
 */
class GestoEmbedTest {

    private val slop = 24

    @Test
    fun `de lado con algo de deriva vertical es del embed`() {
        assertTrue(GestoEmbed.esHorizontal(dx = 30f, dy = 6f, touchSlop = slop))
        assertTrue(GestoEmbed.esHorizontal(dx = 13f, dy = 0f, touchSlop = slop))
    }

    @Test
    fun `se decide antes de que la lista intercepte`() {
        // La lista se lo queda al pasar el slop entero en vertical: con medio slop de lado basta.
        assertTrue(GestoEmbed.esHorizontal(dx = slop / 2f + 1, dy = 2f, touchSlop = slop))
    }

    @Test
    fun `un temblor no cuenta`() {
        assertFalse(GestoEmbed.esHorizontal(dx = 5f, dy = 0f, touchSlop = slop))
    }

    @Test
    fun `un scroll vertical con algo de lado sigue siendo de la lista`() {
        assertFalse(GestoEmbed.esHorizontal(dx = 20f, dy = 30f, touchSlop = slop))
        assertFalse(GestoEmbed.esHorizontal(dx = 20f, dy = 12f, touchSlop = slop))
    }
}
