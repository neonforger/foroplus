package com.fcplus.forocoches

import com.fcplus.forocoches.EncuestaNueva.Companion.Validacion
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)   // org.json de verdad, no el stub de android.jar
class EncuestaNuevaTest {

    private fun v(p: String = "¿Sí o no?", o: List<String> = listOf("sí", "no"), dias: String = "") =
        EncuestaNueva.validar(p, o, multiple = false, publica = false, dias = dias)

    private fun error(r: Validacion) = (r as Validacion.Error).motivo

    @Test
    fun `una encuesta normal vale`() {
        val r = v() as Validacion.Ok
        assertEquals(listOf("sí", "no"), r.encuesta.opciones)
        assertEquals(0, r.encuesta.dias)
    }

    @Test
    fun `sin pregunta no`() {
        assertTrue(error(v(p = "  ")).contains("pregunta"))
    }

    @Test
    fun `las filas vacias no cuentan como opcion`() {
        assertEquals(listOf("a", "b"), (v(o = listOf(" a ", "", "  ", "b")) as Validacion.Ok).encuesta.opciones)
        assertTrue(error(v(o = listOf("a", "", " "))).contains("al menos 2"))
    }

    @Test
    fun `tope de opciones`() {
        assertTrue(v(o = (1..10).map { "o$it" }) is Validacion.Ok)
        assertTrue(error(v(o = (1..11).map { "o$it" })).contains("10"))
    }

    @Test
    fun `opciones repetidas no`() {
        assertTrue(error(v(o = listOf("Sí", "sí"))).contains("repetidas"))
    }

    @Test
    fun `dias vacio es para siempre y lo raro se rechaza`() {
        assertEquals(7, (v(dias = "7") as Validacion.Ok).encuesta.dias)
        assertTrue(v(dias = "-1") is Validacion.Error)
        assertTrue(v(dias = "abc") is Validacion.Error)
        assertTrue(v(dias = "9999") is Validacion.Error)
    }

    @Test
    fun `el json que recibe el motor`() {
        val e = EncuestaNueva("P", listOf("a", "b"), multiple = true, publica = false, dias = 3)
        val o = JSONObject(e.json())
        assertEquals("P", o.getString("pregunta"))
        assertEquals(2, o.getJSONArray("opciones").length())
        assertEquals(true, o.getBoolean("multiple"))
        assertEquals(3, o.getInt("dias"))
    }
}
