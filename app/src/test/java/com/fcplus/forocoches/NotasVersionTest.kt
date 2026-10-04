package com.fcplus.forocoches

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** org.json es de Android: hace falta Robolectric para ejercitarlo en la JVM. */
@RunWith(RobolectricTestRunner::class)
class NotasVersionTest {

    private fun config(code: Int, bloqueante: Boolean = false, notas: String = """["Una cosa","Otra"]""") =
        JSONObject("""{"version":2,"actualizacion":{"code":$code,"nombre":"1.5.0","bloqueante":$bloqueante,"notas":$notas}}""")

    @Test
    fun `si el fichero habla de la misma version, se usan sus notas`() {
        val a = NotasVersion.para(29, config(29))
        assertEquals(listOf("Una cosa", "Otra"), a.notas)
        assertEquals("1.5.0", a.nombre)
    }

    @Test
    fun `si el fichero habla de OTRA version, se avisa sin detalle`() {
        // Caso real esperable: se publica la versión y se olvida actualizar el JSON. Enseñar
        // las notas de la anterior sería describir cambios que no se van a instalar.
        val a = NotasVersion.para(29, config(28))
        assertTrue(a.notas.isEmpty())
        assertEquals("", a.nombre)
    }

    @Test
    fun `una version desincronizada tampoco puede bloquear`() {
        // Bloquear a ciegas encerraría al usuario por una decisión tomada para otra versión.
        assertFalse(NotasVersion.para(29, config(28, bloqueante = true)).bloqueante)
        assertTrue(NotasVersion.para(29, config(29, bloqueante = true)).bloqueante)
    }

    @Test
    fun `sin config o sin bloque de actualizacion no revienta`() {
        assertTrue(NotasVersion.para(29, null).notas.isEmpty())
        assertFalse(NotasVersion.para(29, JSONObject("""{"version":2}""")).bloqueante)
    }

    @Test
    fun `las notas vacias o en blanco se descartan`() {
        val a = NotasVersion.para(29, config(29, notas = """["Buena","","   ","Otra"]"""))
        assertEquals(listOf("Buena", "Otra"), a.notas)
    }

    @Test
    fun `sin notas el mensaje es honesto, no inventado`() {
        val m = NotasVersion.mensaje(NotasVersion.para(29, config(28)))
        assertTrue(m.contains("versión nueva"))
        assertFalse(m.contains("•"))
    }

    @Test
    fun `con notas se listan una por linea`() {
        val m = NotasVersion.mensaje(NotasVersion.para(29, config(29)))
        assertEquals("•  Una cosa\n•  Otra", m)
    }

    @Test
    fun `el titulo lleva el numero de version solo si se conoce`() {
        assertEquals("Versión 1.5.0 disponible", NotasVersion.titulo(NotasVersion.para(29, config(29))))
        assertEquals("Actualización disponible", NotasVersion.titulo(NotasVersion.para(29, config(28))))
    }
}
