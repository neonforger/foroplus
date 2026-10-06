package com.fcplus.forocoches

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class PopurriTest {

    /** Jueves 27 de agosto de 2026, 15:00. */
    private val ahora = Calendar.getInstance()
        .apply { clear(); set(2026, 7, 27, 15, 0) }.timeInMillis

    private fun hilo(tid: String, hora: String) =
        ThreadItem(tid = tid, title = "t$tid", author = "a", replies = "1", time = hora, url = "u")

    // ── Las tres formas en que FC escribe la hora (gotcha 14) ──────────────────

    @Test
    fun `Hoy es hoy a esa hora`() {
        val m = Popurri.momento("Hoy 10:57", ahora)!!
        val c = Calendar.getInstance().apply { timeInMillis = m }
        assertEquals(2026, c.get(Calendar.YEAR))
        assertEquals(7, c.get(Calendar.MONTH))
        assertEquals(27, c.get(Calendar.DAY_OF_MONTH))
        assertEquals(10, c.get(Calendar.HOUR_OF_DAY))
        assertEquals(57, c.get(Calendar.MINUTE))
    }

    @Test
    fun `Ayer resta un dia`() {
        val c = Calendar.getInstance().apply { timeInMillis = Popurri.momento("Ayer 13:32", ahora)!! }
        assertEquals(26, c.get(Calendar.DAY_OF_MONTH))
        assertEquals(13, c.get(Calendar.HOUR_OF_DAY))
    }

    @Test
    fun `la fecha larga lleva el mes ABREVIADO y con guiones, no dd-mm-aa`() {
        val c = Calendar.getInstance().apply {
            timeInMillis = Popurri.momento("01-jul-2026 14:24", ahora)!!
        }
        assertEquals(2026, c.get(Calendar.YEAR))
        assertEquals(6, c.get(Calendar.MONTH))   // julio
        assertEquals(1, c.get(Calendar.DAY_OF_MONTH))
        assertEquals(24, c.get(Calendar.MINUTE))
    }

    @Test
    fun `lo que no se entiende no se inventa`() {
        assertNull(Popurri.momento("", ahora))
        assertNull(Popurri.momento("hace un rato", ahora))
        assertNull(Popurri.momento("Hoy", ahora))          // sin hora no hay nada que ordenar
        assertNull(Popurri.momento("01-xxx-2026 14:24", ahora))
        assertNull(Popurri.momento("Hoy 99:99", ahora))
    }

    // ── La mezcla ─────────────────────────────────────────────────────────────

    @Test
    fun `lo mas reciente va primero, vengan de donde vengan`() {
        val videojuegos = listOf(hilo("1", "Ayer 22:00"), hilo("2", "Hoy 09:00"))
        val electronica = listOf(hilo("3", "Hoy 14:30"), hilo("4", "01-jul-2026 10:00"))

        val mezcla = Popurri.mezclar(listOf(videojuegos, electronica), ahora)

        assertEquals(listOf("3", "2", "1", "4"), mezcla.map { it.tid })
    }

    @Test
    fun `un hilo que sale en dos subforos aparece una sola vez`() {
        val a = listOf(hilo("7", "Hoy 10:00"))
        val b = listOf(hilo("7", "Hoy 10:00"), hilo("8", "Hoy 11:00"))

        val mezcla = Popurri.mezclar(listOf(a, b), ahora)

        assertEquals(listOf("8", "7"), mezcla.map { it.tid })
    }

    @Test
    fun `las filas sin hora van al final y no se barajan entre refrescos`() {
        val lista = listOf(hilo("1", ""), hilo("2", "Hoy 10:00"), hilo("3", "chorrada"))

        val mezcla = Popurri.mezclar(listOf(lista), ahora)

        assertEquals("2", mezcla.first().tid)
        // El orden entre las dos ilegibles es el de llegada, no uno cualquiera.
        assertEquals(listOf("1", "3"), mezcla.drop(1).map { it.tid })
    }

    @Test
    fun `mezclar nada no revienta`() {
        assertTrue(Popurri.mezclar(emptyList(), ahora).isEmpty())
        assertTrue(Popurri.mezclar(listOf(emptyList()), ahora).isEmpty())
    }

    // ── La elección de subforos ───────────────────────────────────────────────

    @Test
    fun `la seleccion va y viene de las preferencias`() {
        assertEquals(listOf(2, 45, 109), Popurri.leer("2,45,109"))
        assertEquals("2,45,109", Popurri.guardar(listOf(2, 45, 109)))
        assertEquals(emptyList<Int>(), Popurri.leer(""))
        assertEquals(emptyList<Int>(), Popurri.leer("nada,de,esto"))
    }

    @Test
    fun `no se guardan repetidos ni mas de los que caben`() {
        assertEquals(listOf(2, 45), Popurri.leer("2,45,2,45"))
        assertEquals(Popurri.TOPE, Popurri.leer("1,2,3,4,5,6,7,8").size)
    }

    @Test
    fun `marcar y desmarcar`() {
        assertEquals(listOf(2, 45), Popurri.alternar(listOf(2), 45))
        assertEquals(listOf(2), Popurri.alternar(listOf(2, 45), 45))
    }

    @Test
    fun `pasarse del tope saca el mas antiguo en vez de ignorar el toque`() {
        // Un botón que no hace nada y no dice por qué es peor que un cambio que se ve.
        val lleno = listOf(1, 2, 3, 4, 5)
        val despues = Popurri.alternar(lleno, 6)
        assertEquals(Popurri.TOPE, despues.size)
        assertEquals(listOf(2, 3, 4, 5, 6), despues)
    }

    // ── De qué subforo es cada hilo (la miga encima del título) ────────────────

    @Test
    fun `cada hilo se lleva el subforo de su lista`() {
        val etiquetados = Popurri.conForo(17, listOf(hilo("1", "Hoy 10:00"), hilo("2", "Hoy 09:00")))
        assertEquals(listOf(17, 17), etiquetados.map { it.foroFid })
    }

    @Test
    fun `sin subforo valido los hilos se quedan como venian`() {
        val hilos = listOf(hilo("1", "Hoy 10:00"))
        assertEquals(hilos, Popurri.conForo(0, hilos))
    }

    @Test
    fun `un hilo repetido en dos subforos se queda con el de su primera aparicion`() {
        // mezclar se queda con la primera aparición: el subforo tiene que viajar con ella.
        val general = Popurri.conForo(2, listOf(hilo("1", "Hoy 10:00")))
        val electronica = Popurri.conForo(17, listOf(hilo("1", "Hoy 10:00"), hilo("2", "Hoy 11:00")))
        val mezcla = Popurri.mezclar(listOf(general, electronica), ahora)
        assertEquals(mapOf("2" to 17, "1" to 2), mezcla.associate { it.tid to it.foroFid })
    }

    @Test
    fun `la etiqueta es el nombre del subforo`() {
        val nombres = mapOf(2 to "General", 17 to "Electrónica / Informática")
        assertEquals("Electrónica / Informática", Popurri.etiqueta(17, nombres))
    }

    @Test
    fun `sin subforo o con uno desconocido no hay etiqueta`() {
        // Fuera del Popurrí el hilo no trae subforo (0), y la lista de nombres puede no haber
        // llegado todavía: en los dos casos la fila se pinta sin miga, no con un número.
        val nombres = mapOf(2 to "General")
        assertEquals("", Popurri.etiqueta(0, nombres))
        assertEquals("", Popurri.etiqueta(17, nombres))
        assertEquals("", Popurri.etiqueta(2, emptyMap()))
    }
}
