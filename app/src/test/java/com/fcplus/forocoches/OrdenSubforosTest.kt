package com.fcplus.forocoches

import org.junit.Assert.assertEquals
import org.junit.Test

class OrdenSubforosTest {

    /** El índice REAL de FC, sondeado por CDP el 2026-08-28 (los seis primeros y el último). */
    private val comoLoDaFc = listOf(
        ForumTab(12, "Ayuda"),
        ForumTab(2, "General"),
        ForumTab(17, "Electrónica / Informática"),
        ForumTab(43, "Videojuegos"),
        ForumTab(23, "Empleo / Emprendimiento"),
        ForumTab(93, "Oposiciones"),
        ForumTab(8, "Pruebas")
    )

    @Test
    fun `General va el primero y Ayuda la ultima`() {
        val r = OrdenSubforos.porDefecto(comoLoDaFc)
        assertEquals("General", r.first().name)
        assertEquals("Ayuda", r.last().name)
    }

    @Test
    fun `el resto conserva el orden que le da FC`() {
        val r = OrdenSubforos.porDefecto(comoLoDaFc).map { it.name }
        assertEquals(
            listOf("General", "Electrónica / Informática", "Videojuegos",
                   "Empleo / Emprendimiento", "Oposiciones", "Pruebas", "Ayuda"),
            r
        )
    }

    @Test
    fun `no se pierde ni se duplica ningun subforo`() {
        // El invariante que de verdad importa: un subforo que se cayera al reordenar sería
        // un subforo al que NO se puede llegar desde la app.
        val r = OrdenSubforos.porDefecto(comoLoDaFc)
        assertEquals(comoLoDaFc.size, r.size)
        assertEquals(comoLoDaFc.map { it.fid }.toSet(), r.map { it.fid }.toSet())
    }

    @Test
    fun `si FC cambia los ids no se inventa nada, se deja su orden`() {
        // Movemos por fid y no por nombre a propósito (renombrar "Ayuda" no debe romperlo),
        // pero eso significa que si algún día cambian los ids esto se vuelve inofensivo:
        // el orden pasa a ser el del foro, que es de donde veníamos.
        val otros = listOf(ForumTab(101, "Uno"), ForumTab(102, "Dos"))
        assertEquals(otros, OrdenSubforos.porDefecto(otros))
    }

    @Test
    fun `con solo uno de los dos tambien vale`() {
        assertEquals(
            listOf("General", "Uno"),
            OrdenSubforos.porDefecto(listOf(ForumTab(101, "Uno"), ForumTab(2, "General"))).map { it.name }
        )
        assertEquals(
            listOf("Uno", "Ayuda"),
            OrdenSubforos.porDefecto(listOf(ForumTab(12, "Ayuda"), ForumTab(101, "Uno"))).map { it.name }
        )
    }

    @Test
    fun `una lista vacia no revienta`() {
        assertEquals(emptyList<ForumTab>(), OrdenSubforos.porDefecto(emptyList()))
    }

    @Test
    fun `aplicarlo dos veces da lo mismo`() {
        // Se guarda ya ordenado, así que se reordena sobre lo reordenado en cada repintado.
        val una = OrdenSubforos.porDefecto(comoLoDaFc)
        assertEquals(una, OrdenSubforos.porDefecto(una))
    }
}
