package com.fcplus.forocoches

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

class TituloCancionTest {

    // Se lee por clave y se ignoran las que no conoce: el servidor añade "claves" a este fichero.
    private val contrato: JSONObject by lazy {
        JSONObject(javaClass.classLoader!!.getResource("contrato/musica-titulos.json").readText())
    }

    private fun lista(a: JSONArray) = (0 until a.length()).map { a.getString(it) }

    @Test
    fun contratoDeTitulos() {
        val arr = contrato.getJSONArray("titulos")
        for (i in 0 until arr.length()) {
            val c = arr.getJSONObject(i)
            val r = TituloCancion.separar(c.getString("titulo"), c.optString("canal").ifEmpty { null })
            assertEquals("cancion de: ${c.getString("titulo")}", c.getString("cancion"), r.cancion)
            assertEquals("artista de: ${c.getString("titulo")}", c.getString("artista"), r.artista)
        }
    }

    @Test
    fun etiquetasIgualesAlContrato() {
        assertEquals(lista(contrato.getJSONArray("etiquetasVideo")), TituloCancion.ETIQUETAS_VIDEO)
        assertEquals(lista(contrato.getJSONArray("etiquetasVersion")), TituloCancion.ETIQUETAS_VERSION)
    }

    @Test
    fun limpiarNoTocaParentesisNormales() {
        assertEquals("Hoy (no) es lunes", TituloCancion.limpiar("Hoy (no) es lunes"))
        assertEquals("Soldadito (Directo)", TituloCancion.limpiar("Soldadito (Directo)"))
    }

    @Test
    fun limpiarSoloQuitaEtiquetasDeVideoYConservaLasDeVersion() {
        assertEquals("Tema (Remastered 2011)", TituloCancion.limpiar("Tema [VIDEO OFICIAL]  (Remastered 2011)"))
        assertEquals("Tema (2009 Remaster)", TituloCancion.limpiar("Tema (2009 Remaster)"))
        assertEquals("Tema", TituloCancion.limpiar("Tema (Vídeo Oficial)"))
    }
}
