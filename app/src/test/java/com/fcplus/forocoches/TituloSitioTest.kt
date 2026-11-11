package com.fcplus.forocoches

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TituloSitioTest {

    private val nombres = mapOf(2 to "General", 17 to "Electrónica / Informática")

    @Test
    fun `un subforo conocido se titula con su nombre y es selector`() {
        assertEquals(TituloLista("General", true), TituloSitio.deLista("home", 2, nombres))
    }

    @Test
    fun `las secciones se titulan con su nombre y son selector`() {
        assertEquals(TituloLista("Música", true), TituloSitio.deLista("musica", 2, nombres))
        assertEquals(TituloLista("Popurrí", true), TituloSitio.deLista("popurri", 2, nombres))
        assertEquals(TituloLista("+18", true), TituloSitio.deLista("mas18", 2, nombres))
    }

    @Test
    fun `sin el nombre del subforo todavia el titulo es la marca y no se toca`() {
        assertEquals(TituloLista("ForoPlus", false), TituloSitio.deLista("home", 99, nombres))
    }

    @Test
    fun `los hilos del momento no son un sitio al que ir`() {
        assertEquals(TituloLista("Hilos del momento", false), TituloSitio.deLista("top", 2, nombres))
    }

    @Test
    fun `suscripciones, mis hilos y busquedas no dicen nada aqui`() {
        for (s in listOf("favs", "mine", "search", "participated", "usuario")) {
            assertNull(s, TituloSitio.deLista(s, 2, nombres))
        }
    }

    @Test
    fun `la descripcion de accesibilidad dice donde estas y que se puede cambiar`() {
        assertEquals("Estás en General. Toca para cambiar de subforo", TituloSitio.descripcion("General"))
    }

    // Medidor de juguete: cada carácter mide 10.
    private val medir = { s: String -> s.length * 10f }

    @Test
    fun `si cabe el nombre no se toca`() {
        assertEquals("General", TituloSitio.recortar("General", 70f, medir))
    }

    @Test
    fun `si no cabe se acorta con puntos suspensivos y lo que se queda cabe`() {
        val r = TituloSitio.recortar("Electrónica / Informática", 100f, medir)
        assertEquals("Electróni…", r)
        assertTrue(medir(r) <= 100f)
    }

    @Test
    fun `se queda con el mayor prefijo posible, sin dejar un espacio antes de los puntos`() {
        assertEquals("Electrónica…", TituloSitio.recortar("Electrónica / Informática", 125f, medir))
    }

    @Test
    fun `sin sitio queda solo el punto suspensivo`() {
        assertEquals("…", TituloSitio.recortar("General", 0f, medir))
        assertEquals("…", TituloSitio.recortar("General", -5f, medir))
    }
}
