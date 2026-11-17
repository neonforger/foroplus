package com.fcplus.forocoches

import com.fcplus.forocoches.HistorialHilos.Visita
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HistorialHilosTest {

    private fun v(tid: String, titulo: String = "Hilo $tid", cuando: Long = 1L, fid: Int = 0,
                  foro: String = "", autor: String = "") = Visita(tid, titulo, fid, foro, autor, cuando)

    @Test
    fun `lo ultimo abierto va arriba`() {
        val l = HistorialHilos.apuntar(HistorialHilos.apuntar(emptyList(), v("1")), v("2"))
        assertEquals(listOf("2", "1"), l.map { it.tid })
    }

    @Test
    fun `volver a un hilo lo sube sin duplicarlo`() {
        var l = emptyList<Visita>()
        for (t in listOf("1", "2", "3")) l = HistorialHilos.apuntar(l, v(t))
        l = HistorialHilos.apuntar(l, v("1", "Título nuevo", cuando = 9L))
        assertEquals(listOf("1", "3", "2"), l.map { it.tid })
        assertEquals("Título nuevo", l.first().titulo)
        assertEquals(9L, l.first().cuando)
    }

    @Test
    fun `se recorta a los 30 ultimos`() {
        var l = emptyList<Visita>()
        for (i in 1..45) l = HistorialHilos.apuntar(l, v(i.toString()))
        assertEquals(HistorialHilos.TOPE, l.size)
        assertEquals("45", l.first().tid)
        assertEquals("16", l.last().tid)
    }

    @Test
    fun `sin tid o sin titulo no se apunta`() {
        val l = listOf(v("1"))
        assertEquals(l, HistorialHilos.apuntar(l, v("", "algo")))
        assertEquals(l, HistorialHilos.apuntar(l, v("2", "   ")))
    }

    @Test
    fun `una visita sin autor ni subforo hereda los de antes`() {
        val l = HistorialHilos.apuntar(emptyList(), v("1", fid = 2, foro = "General", autor = "pepe"))
        val l2 = HistorialHilos.apuntar(l, v("1"))
        assertEquals(2, l2.first().fid)
        assertEquals("General", l2.first().foro)
        assertEquals("pepe", l2.first().autor)
    }

    @Test
    fun `quitar uno`() {
        val l = listOf(v("1"), v("2"))
        assertEquals(listOf("2"), HistorialHilos.quitar(l, "1").map { it.tid })
    }

    @Test
    fun `guardar y leer da lo mismo, con comillas y comas en el titulo`() {
        val l = listOf(
            v("10", "Hilo \"oficial\", con comas: y ñ", 123L, 2, "General", "ana"),
            v("11", "Otro", 99L)
        )
        assertEquals(l, HistorialHilos.leer(HistorialHilos.guardar(l)))
    }

    @Test
    fun `leer es tolerante a basura`() {
        assertTrue(HistorialHilos.leer(null).isEmpty())
        assertTrue(HistorialHilos.leer("").isEmpty())
        assertTrue(HistorialHilos.leer("no es json").isEmpty())
        assertTrue(HistorialHilos.leer("{\"t\":\"1\"}").isEmpty())
        val mezcla = """[{"t":"1","n":"Bueno","c":5}, 7, {"t":"","n":"sin tid"}, {"t":"abc","n":"tid raro"},
            {"t":"2"}, {"t":"1","n":"Duplicado"}, {"t":"3","n":"Fecha rota","c":"ayer","f":-4}]"""
        val l = HistorialHilos.leer(mezcla)
        assertEquals(listOf("1", "3"), l.map { it.tid })
        assertEquals("Bueno", l[0].titulo)
        assertEquals(0L, l[1].cuando)
        assertEquals(0, l[1].fid)
    }

    @Test
    fun `leer no devuelve mas del tope aunque lo guardado sea mas largo`() {
        val largo = (1..50).map { v(it.toString()) }
        assertEquals(HistorialHilos.TOPE, HistorialHilos.leer(HistorialHilos.guardar(largo)).size)
    }

    @Test
    fun `visto hace X`() {
        val ahora = 10_000_000_000L
        val min = 60_000L
        assertEquals("visto ahora mismo", HistorialHilos.haceCuanto(ahora - 20_000L, ahora))
        assertEquals("visto hace 5 min", HistorialHilos.haceCuanto(ahora - 5 * min, ahora))
        assertEquals("visto hace 3 h", HistorialHilos.haceCuanto(ahora - 3 * 60 * min, ahora))
        assertEquals("visto ayer", HistorialHilos.haceCuanto(ahora - 30 * 60 * min, ahora))
        assertEquals("visto hace 4 días", HistorialHilos.haceCuanto(ahora - 4 * 24 * 60 * min, ahora))
        assertEquals("visto hace más de un mes", HistorialHilos.haceCuanto(ahora - 40L * 24 * 60 * min, ahora))
        // Reloj del móvil hacia atrás: nada de "hace -3 min".
        assertEquals("visto ahora mismo", HistorialHilos.haceCuanto(ahora + 3 * min, ahora))
        assertEquals("", HistorialHilos.haceCuanto(0L, ahora))
    }

    @Test
    fun `la url del hilo sale del tid`() {
        assertEquals("https://forocoches.com/foro/showthread.php?t=123", HistorialHilos.url("123"))
    }
}
