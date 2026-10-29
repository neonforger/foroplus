package com.fcplus.forocoches

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BuscarEnPaginaTest {

    private fun buscar(texto: String, consulta: String, fuera: List<IntRange> = emptyList()) =
        BuscarEnPagina.coincidencias(texto, BuscarEnPagina.preparar(consulta), fuera)

    @Test
    fun `sin tildes ni mayusculas`() {
        assertEquals(listOf(0 until 4, 10 until 14), buscar("Peña y la PENA", "pena"))
        assertEquals(listOf(4 until 10), buscar("Del camión", "CAMION"))
        assertEquals(listOf(4 until 10), buscar("Del camion", "camión"))
    }

    @Test
    fun `cada posicion plegada es la misma del original`() {
        // Si plegar cambiara la longitud (la NFD de la cadena entera lo hace), el resaltado
        // caería desplazado en cuanto hubiera una tilde antes de lo encontrado.
        val texto = "Ñandú, pingüino, ÀÉÎÕÜ, ﬁn"
        assertEquals(texto.length, BuscarEnPagina.plegar(texto).length)
        assertEquals(listOf(7 until 15), buscar(texto, "pinguino"))
    }

    @Test
    fun `un salto de linea o un espacio duro valen como espacio`() {
        assertEquals(listOf(0 until 8), buscar("hola\nque", "hola que"))
        assertEquals(listOf(0 until 8), buscar("hola que", "hola que"))
    }

    @Test
    fun `no se solapan`() {
        assertEquals(listOf(0 until 2, 2 until 4), buscar("aaaa", "aa"))
    }

    @Test
    fun `una consulta vacia o de espacios no encuentra nada`() {
        assertTrue(buscar("hola", "").isEmpty())
        assertTrue(buscar("hola  que", "   ").isEmpty())
    }

    @Test
    fun `la consulta se recorta pero no por dentro`() {
        assertEquals(listOf(5 until 11), buscar("dijo hola que", "  hola q "))
    }

    @Test
    fun `lo que pisa una zona tapada no cuenta`() {
        // El contenido de un spoiler no se encuentra: no se puede resaltar sin destaparlo.
        val texto = "foto aqui y foto alli"
        assertEquals(listOf(12 until 16), buscar(texto, "foto", fuera = listOf(0..3)))
        assertEquals(listOf(0 until 4), buscar(texto, "foto", fuera = listOf(14..20)))
    }

    @Test
    fun `los sitios van en orden de lectura`() {
        assertEquals(
            listOf(
                BuscarEnPagina.Sitio(0, 0), BuscarEnPagina.Sitio(0, 1), BuscarEnPagina.Sitio(2, 0)
            ),
            BuscarEnPagina.sitios(listOf(2, 0, 1))
        )
    }

    @Test
    fun `se empieza por lo que estas leyendo`() {
        val sitios = BuscarEnPagina.sitios(listOf(1, 0, 2, 0, 1))
        // Leyendo el mensaje 1 (sin coincidencias): la primera es la del 2, no la del 0.
        assertEquals(1, BuscarEnPagina.primeraDesde(sitios, 1))
        assertEquals(0, BuscarEnPagina.primeraDesde(sitios, 0))
        // Por debajo no hay ninguna: se vuelve a la primera, como el navegador.
        assertEquals(0, BuscarEnPagina.primeraDesde(BuscarEnPagina.sitios(listOf(1, 0, 0)), 2))
        assertEquals(-1, BuscarEnPagina.primeraDesde(emptyList(), 0))
    }

    @Test
    fun `siguiente y anterior dan la vuelta`() {
        assertEquals(1, BuscarEnPagina.siguiente(0, 3))
        assertEquals(0, BuscarEnPagina.siguiente(2, 3))
        assertEquals(0, BuscarEnPagina.siguiente(-1, 3))
        assertEquals(2, BuscarEnPagina.anterior(0, 3))
        assertEquals(2, BuscarEnPagina.anterior(-1, 3))
        assertEquals(-1, BuscarEnPagina.siguiente(0, 0))
        assertEquals(-1, BuscarEnPagina.anterior(0, 0))
    }

    @Test
    fun `el contador`() {
        assertEquals("", BuscarEnPagina.contador(-1, 0, consultaVacia = true))
        assertEquals("Sin resultados", BuscarEnPagina.contador(-1, 0, consultaVacia = false))
        assertEquals("3 de 7", BuscarEnPagina.contador(2, 7, consultaVacia = false))
        assertEquals("7 resultados", BuscarEnPagina.contador(-1, 7, consultaVacia = false))
        assertEquals("1 resultado", BuscarEnPagina.contador(-1, 1, consultaVacia = false))
    }
}
