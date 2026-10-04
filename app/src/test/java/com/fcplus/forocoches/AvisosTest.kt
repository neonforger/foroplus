package com.fcplus.forocoches

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AvisosTest {

    private fun cfg(json: String) = JSONObject(json)

    private val NORMAL = """
        {"aviso": {"id": "roto-agosto", "texto": "El foro ha cambiado y estamos arreglándolo."}}
    """.trimIndent()

    @Test
    fun `sin config no hay aviso`() {
        assertNull(Avisos.para(null, 31, emptySet()))
    }

    @Test
    fun `sin el bloque aviso no hay aviso`() {
        assertNull(Avisos.para(cfg("""{"version": 3}"""), 31, emptySet()))
    }

    @Test
    fun `un aviso normal se enseña`() {
        val a = Avisos.para(cfg(NORMAL), 31, emptySet())!!
        assertEquals("roto-agosto", a.id)
        assertEquals("El foro ha cambiado y estamos arreglándolo.", a.texto)
        assertEquals(false, a.fijo)
        assertEquals("", a.enlace)
    }

    @Test
    fun `sin id no se enseña nada`() {
        // Sin id no se puede recordar que lo cerraste: saldría en cada arranque para siempre.
        assertNull(Avisos.para(cfg("""{"aviso": {"texto": "hola"}}"""), 31, emptySet()))
    }

    @Test
    fun `sin texto no se enseña nada`() {
        assertNull(Avisos.para(cfg("""{"aviso": {"id": "x", "texto": "  "}}"""), 31, emptySet()))
    }

    @Test
    fun `un aviso ya descartado no vuelve`() {
        assertNull(Avisos.para(cfg(NORMAL), 31, setOf("roto-agosto")))
    }

    @Test
    fun `descartar uno no silencia a los demas`() {
        val a = Avisos.para(cfg(NORMAL), 31, setOf("otro-aviso", "uno-viejo"))
        assertEquals("roto-agosto", a?.id)
    }

    @Test
    fun `un aviso fijo se enseña aunque lo hayan cerrado`() {
        val j = """{"aviso": {"id": "caido", "texto": "Caído", "fijo": true}}"""
        val a = Avisos.para(cfg(j), 31, setOf("caido"))!!
        assertEquals(true, a.fijo)
    }

    @Test
    fun `hasta limita el aviso a las versiones afectadas`() {
        val j = """{"aviso": {"id": "v31", "texto": "Actualiza", "hasta": 31}}"""
        assertEquals("v31", Avisos.para(cfg(j), 30, emptySet())?.id)   // más vieja: le afecta
        assertEquals("v31", Avisos.para(cfg(j), 31, emptySet())?.id)   // justo esa: le afecta
        assertNull(Avisos.para(cfg(j), 32, emptySet()))                // ya actualizó: silencio
    }

    @Test
    fun `sin hasta el aviso vale para todas las versiones`() {
        assertEquals("roto-agosto", Avisos.para(cfg(NORMAL), 999, emptySet())?.id)
    }

    @Test
    fun `si no se sabe la version se enseña igual`() {
        // versionCode 0 = no se pudo leer del sistema. Lado seguro: mejor un aviso de más que
        // dejar a alguien con la app rota y sin ninguna explicación.
        val j = """{"aviso": {"id": "v31", "texto": "Actualiza", "hasta": 31}}"""
        assertEquals("v31", Avisos.para(cfg(j), 0, emptySet())?.id)
    }

    @Test
    fun `el enlace es opcional y se respeta`() {
        val j = """{"aviso": {"id": "tg", "texto": "Únete", "enlace": "https://t.me/foroplus"}}"""
        assertEquals("https://t.me/foroplus", Avisos.para(cfg(j), 31, emptySet())?.enlace)
    }

    @Test
    fun `los descartados se leen y se anaden sin duplicar`() {
        assertEquals(emptyList<String>(), Avisos.leerDescartados(""))
        assertEquals(listOf("a", "b"), Avisos.leerDescartados("a\nb"))
        assertEquals("a\nb", Avisos.conDescartado("a", "b"))
        // repetir uno lo mueve al final, no lo duplica
        assertEquals("b\na", Avisos.conDescartado("a\nb", "a"))
        assertEquals("a", Avisos.conDescartado("", "a"))
    }

    @Test
    fun `la lista de descartados no crece sin fin`() {
        var raw = ""
        for (i in 1..Avisos.MAX_RECORDADOS + 20) raw = Avisos.conDescartado(raw, "aviso$i")
        val l = Avisos.leerDescartados(raw)
        assertEquals(Avisos.MAX_RECORDADOS, l.size)
        assertEquals("aviso${Avisos.MAX_RECORDADOS + 20}", l.last())   // el más nuevo sobrevive
    }
}
