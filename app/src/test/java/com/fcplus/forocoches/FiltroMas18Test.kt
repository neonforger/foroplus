package com.fcplus.forocoches

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FiltroMas18Test {

    private fun h(tid: Long, vararg et: String) = HiloMas18(tid, "t$tid", et.toSet(), "a", 0, null, "", 0, "")

    @Test fun `la primera vez esta todo encendido`() = assertTrue(FiltroMas18.apagadas("").isEmpty())

    @Test fun `guardar y leer las apagadas`() {
        val s = FiltroMas18.guardar(setOf("+hd", "+14"))
        assertEquals(setOf("+hd", "+14"), FiltroMas18.apagadas(s))
    }

    @Test fun `lo guardado que ya no existe se ignora`() =
        assertEquals(setOf("+hd"), FiltroMas18.apagadas("+hd,+99,basura"))

    @Test fun `se ve si ALGUNA de sus etiquetas esta encendida`() {
        assertTrue(FiltroMas18.visible(h(1, "+16", "+hd"), setOf("+hd")))
        assertFalse(FiltroMas18.visible(h(2, "+hd"), setOf("+hd")))
    }

    @Test fun `la peña sola no basta para verse si su otra etiqueta esta apagada`() {
        assertFalse(FiltroMas18.visible(h(3, "+prv", "peña"), setOf("+prv")))
    }

    @Test fun `un hilo repetido entre dos paginas sale una vez`() {
        val r = FiltroMas18.unir(listOf(h(1, "+18"), h(2, "+18")), listOf(h(2, "+18"), h(3, "+18")))
        assertEquals(listOf(1L, 2L, 3L), r.map { it.tid })
    }

    @Test fun `con todo apagado el vacio lo dice`() {
        assertEquals("Enciende algún filtro para ver hilos", FiltroMas18.textoVacio(EtiquetasHilo.TODAS.toSet()))
        assertEquals("No hay hilos que mostrar", FiltroMas18.textoVacio(emptySet()))
    }
}
