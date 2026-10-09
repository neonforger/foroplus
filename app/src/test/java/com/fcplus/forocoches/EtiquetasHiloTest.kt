package com.fcplus.forocoches

import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EtiquetasHiloTest {

    private fun casos(): JSONArray {
        val txt = javaClass.classLoader!!.getResource("contrato/etiquetas-casos.json").readText()
        return JSONArray(txt)
    }

    @Test
    fun `todos los casos compartidos con el lector`() {
        val arr = casos()
        for (i in 0 until arr.length()) {
            val c = arr.getJSONObject(i)
            val esperadas = (0 until c.getJSONArray("etiquetas").length())
                .map { c.getJSONArray("etiquetas").getString(it) }.toSet()
            assertEquals("título: ${c.getString("titulo")}", esperadas, EtiquetasHilo.de(c.getString("titulo")))
        }
    }

    @Test
    fun `entra en la seccion con cualquiera de las cinco, no con peña sola`() {
        assertTrue(EtiquetasHilo.entraEnMas18("algo +hd"))
        assertTrue(EtiquetasHilo.entraEnMas18("algo +prv"))
        assertFalse(EtiquetasHilo.entraEnMas18("La peña del barrio"))
        assertFalse(EtiquetasHilo.entraEnMas18("sin nada"))
    }

    @Test
    fun `ocultar en listas normales solo mira +18 y +16`() {
        assertTrue(EtiquetasHilo.esMas18o16("x +18"))
        assertTrue(EtiquetasHilo.esMas18o16("x (+16)"))
        assertFalse(EtiquetasHilo.esMas18o16("x +14"))
        assertFalse(EtiquetasHilo.esMas18o16("x +hd"))
    }
}
