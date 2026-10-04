package com.fcplus.forocoches

import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Robolectric porque los spans son de Android. */
@RunWith(RobolectricTestRunner::class)
class LineaAutorAvisoTest {

    private val rojo = 0xFFC8102E.toInt()

    @Test
    fun `solo el nombre va en rojo`() {
        val t = lineaAutorAviso("pepito", "te citó", rojo) as Spanned
        assertEquals("@pepito te citó", t.toString())
        val spans = t.getSpans(0, t.length, ForegroundColorSpan::class.java)
        assertEquals(1, spans.size)
        assertEquals(rojo, spans[0].foregroundColor)
        assertEquals(0, t.getSpanStart(spans[0]))
        assertEquals("@pepito".length, t.getSpanEnd(spans[0]))
    }

    @Test
    fun `el nombre va sin negrita`() {
        val t = lineaAutorAviso("pepito", "te mencionó", rojo) as Spanned
        assertTrue(t.getSpans(0, t.length, StyleSpan::class.java).isEmpty())
    }

    @Test
    fun `sin autor queda el verbo solo y sin color`() {
        val t = lineaAutorAviso("", "te citó", rojo)
        assertEquals("te citó", t.toString())
        assertTrue(t !is Spanned || t.getSpans(0, t.length, Any::class.java).isEmpty())
    }
}
