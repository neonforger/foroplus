package com.fcplus.forocoches

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

/**
 * La fecha de una cita/mención.
 *
 * La lista de FC sella cada fila SOLO con la hora ("21:38"), sea de hoy o de hace tres días.
 * El 2026-09-24 un tester vio "hace 1 h" en citas de AYER a las 9:30: la app daba por hecho que
 * toda hora era de hoy. Ahora la fecha buena sale del propio mensaje, y mientras no se sabe se
 * enseña la hora tal cual.
 */
class SelloNoticiaTest {

    /** 2026-09-24 a la hora dada, en la zona del móvil. */
    private fun el24(h: Int, m: Int, dia: Int = 24, mes: Int = Calendar.SEPTEMBER, anio: Int = 2026): Long =
        Calendar.getInstance().apply {
            clear(); set(anio, mes, dia, h, m)
        }.timeInMillis

    private val ahora = el24(10, 23)   // la hora de la captura del tester

    // ── texto(): lo que se pinta en la fila ─────────────────────────────────

    @Test
    fun `sin fecha real se ensena la hora de FC tal cual, nunca un hace inventado`() {
        // El fallo: "9:30" a las 10:23 salía "hace 1 h" y era de AYER.
        assertEquals("9:30", SelloNoticia.texto("9:30", null, ahora))
        assertEquals("23:04", SelloNoticia.texto("23:04", null, ahora))
    }

    @Test
    fun `con la fecha real se usa la fecha real`() {
        assertEquals("ayer 9:30", SelloNoticia.texto("9:30", el24(9, 30, dia = 23), ahora))
    }

    // ── relativo(): los casos medidos el 2026-09-24 ─────────────────────────

    @Test
    fun `las tres citas reales del sondeo`() {
        // Sellos de la lista: 10:58, 21:50, 21:38. Fechas reales: Hoy 10:58, Ayer 21:50,
        // 22-sep-2026 21:38. Miradas a las 11:30.
        val a = el24(11, 30)
        assertEquals("hace 32 min", SelloNoticia.relativo(el24(10, 58), a))
        assertEquals("ayer 21:50", SelloNoticia.relativo(el24(21, 50, dia = 23), a))
        assertEquals("22 sep", SelloNoticia.relativo(el24(21, 38, dia = 22), a))
    }

    @Test
    fun `el mismo minuto es ahora`() {
        assertEquals("ahora", SelloNoticia.relativo(el24(10, 23), ahora))
    }

    @Test
    fun `de hoy en horas`() {
        assertEquals("hace 1 h", SelloNoticia.relativo(el24(9, 0), ahora))
        assertEquals("hace 10 h", SelloNoticia.relativo(el24(0, 5), ahora))
    }

    @Test
    fun `de ayer pero hace poco sigue en horas`() {
        // 23:50 de ayer mirado a las 01:20: "hace 1 h" es más claro que "ayer 23:50".
        assertEquals("hace 1 h", SelloNoticia.relativo(el24(23, 50, dia = 23), el24(1, 20)))
        assertEquals("hace 30 min", SelloNoticia.relativo(el24(23, 50, dia = 23), el24(0, 20)))
    }

    @Test
    fun `de otro ano lleva el ano`() {
        assertEquals("3 dic 2025", SelloNoticia.relativo(el24(12, 0, dia = 3, mes = Calendar.DECEMBER, anio = 2025), ahora))
    }

    @Test
    fun `un reloj un poco adelantado no dice hace menos algo`() {
        assertEquals("ahora", SelloNoticia.relativo(el24(10, 25), ahora))
    }

    // ── caché pid → fecha ───────────────────────────────────────────────────

    @Test
    fun `la cache va y vuelve`() {
        val m = mapOf("519661242" to 1L, "519641613" to 2L)
        assertEquals(m, SelloNoticia.leerCache(SelloNoticia.guardarCache(m)))
    }

    @Test
    fun `cache vacia o rota no revienta`() {
        assertEquals(emptyMap<String, Long>(), SelloNoticia.leerCache(""))
        assertEquals(mapOf("5" to 7L), SelloNoticia.leerCache("basura,5:7,x:y,:3"))
    }

    @Test
    fun `la cache tiene tope y se queda con los mensajes mas nuevos`() {
        // Los pid de FC crecen con el tiempo: los más altos son los que siguen en la lista.
        val m = (1..10).associate { it.toString() to it.toLong() }
        val g = SelloNoticia.leerCache(SelloNoticia.guardarCache(m, tope = 3))
        assertEquals(setOf("8", "9", "10"), g.keys)
        assertTrue(g.values.all { it >= 8 })
    }
}
