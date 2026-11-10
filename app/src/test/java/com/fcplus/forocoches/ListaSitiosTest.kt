package com.fcplus.forocoches

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ListaSitiosTest {

    private val barra = listOf(
        Sitio(-3, "Música"), Sitio(2, "General"), Sitio(17, "Electrónica / Informática"),
    )
    private val conocidos = listOf(
        Sitio(2, "General"), Sitio(17, "Electrónica / Informática"),
        Sitio(43, "Videojuegos"), Sitio(12, "Ayuda"), Sitio(50, "Álbum de motos"),
    )

    @Test
    fun `normalizar quita tildes y mayusculas`() {
        assertEquals("electronica", ListaSitios.normalizar("ELECTRÓNICA"))
        assertEquals("pena", ListaSitios.normalizar("Peña"))
    }

    @Test
    fun `dos secciones, la barra en su orden y el resto alfabetico sin tildes`() {
        val s = ListaSitios.secciones(barra, conocidos, 2, "")
        assertEquals(listOf("EN TU BARRA", "TODOS LOS SUBFOROS"), s.map { it.titulo })
        assertEquals(listOf("Música", "General", "Electrónica / Informática"), s[0].filas.map { it.nombre })
        assertEquals(listOf("Álbum de motos", "Ayuda", "Videojuegos"), s[1].filas.map { it.nombre })
    }

    @Test
    fun `solo la fila del sitio actual lleva la marca`() {
        val s = ListaSitios.secciones(barra, conocidos, 2, "")
        assertEquals(listOf("General"), s.flatMap { it.filas }.filter { it.actual }.map { it.nombre })
    }

    @Test
    fun `una seccion puede ser la actual`() {
        val s = ListaSitios.secciones(barra, conocidos, -3, "")
        assertEquals(listOf("Música"), s.flatMap { it.filas }.filter { it.actual }.map { it.nombre })
    }

    @Test
    fun `un subforo escondido y actual sale en todos con la marca`() {
        val s = ListaSitios.secciones(barra, conocidos, 43, "")
        val todos = s.single { it.titulo == "TODOS LOS SUBFOROS" }
        assertTrue(todos.filas.single { it.clave == 43 }.actual)
    }

    @Test
    fun `sin subforos fuera de la barra la seccion de todos no sale`() {
        val todosEnBarra = conocidos + Sitio(-3, "Música")
        val s = ListaSitios.secciones(todosEnBarra, conocidos, 2, "")
        assertEquals(listOf("EN TU BARRA"), s.map { it.titulo })
    }

    @Test
    fun `el filtro ignora mayusculas y tildes`() {
        val s = ListaSitios.secciones(barra, conocidos, 2, "ELECTRONICA")
        assertEquals(listOf("EN TU BARRA"), s.map { it.titulo })
        assertEquals(listOf("Electrónica / Informática"), s[0].filas.map { it.nombre })
        val s2 = ListaSitios.secciones(barra, conocidos, 2, "album")
        assertEquals(listOf("Álbum de motos"), s2.single().filas.map { it.nombre })
    }

    @Test
    fun `una seccion sin coincidencias desaparece y sin ninguna no queda nada`() {
        assertTrue(ListaSitios.secciones(barra, conocidos, 2, "zzz").isEmpty())
    }

    @Test
    fun `los espacios alrededor de la busqueda no cuentan`() {
        val s = ListaSitios.secciones(barra, conocidos, 2, "  ayuda ")
        assertEquals(listOf("Ayuda"), s.single().filas.map { it.nombre })
    }
}
