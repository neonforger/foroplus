package com.fcplus.forocoches

import org.junit.Assert.assertEquals
import org.junit.Test

class CitasPendientesTest {

    @Test
    fun `citar y arrepentirse no deja la cita pegada`() {
        // El fallo reportado: citas A, das atrás, citas B y respondes a los dos.
        val prov = CitasPendientes.alCitar(emptySet(), emptySet(), "A")
        assertEquals(emptySet<String>(), CitasPendientes.alCerrarSinEnviar(setOf("A"), prov))
    }

    @Test
    fun `las del mas sobreviven, que para eso se guardan`() {
        // "＋" no pasa por alCitar: nunca es provisional.
        assertEquals(setOf("A"), CitasPendientes.alCerrarSinEnviar(setOf("A"), emptySet()))
    }

    @Test
    fun `citar algo que YA habias marcado con el mas no lo descarta`() {
        // Lo añadiste tú a propósito antes; abrir y cerrar el composer no borra esa decisión.
        val prov = CitasPendientes.alCitar(setOf("A"), emptySet(), "A")
        assertEquals(emptySet<String>(), prov)
        assertEquals(setOf("A"), CitasPendientes.alCerrarSinEnviar(setOf("A"), prov))
    }

    @Test
    fun `mezclando las dos, lo del mas se queda y lo de citar se va`() {
        var prov = emptySet<String>()
        prov = CitasPendientes.alCitar(setOf("A"), prov, "B")   // A venía del ＋
        assertEquals(setOf("A"), CitasPendientes.alCerrarSinEnviar(setOf("A", "B"), prov))
    }

    @Test
    fun `sin nada citado no revienta`() {
        assertEquals(emptySet<String>(), CitasPendientes.alCerrarSinEnviar(emptySet(), setOf("A")))
    }
}
