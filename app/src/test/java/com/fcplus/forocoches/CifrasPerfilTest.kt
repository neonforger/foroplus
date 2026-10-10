package com.fcplus.forocoches

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar

/** Los tres bloques del panel de tu cuenta y la línea "Miembro desde …" (fase 2). */
class CifrasPerfilTest {

    private val hoy = Calendar.getInstance().apply { clear(); set(2026, 7, 27) }.timeInMillis

    @Test
    fun `tres bloques con los mismos datos que la linea`() = assertEquals(
        listOf("400" to "mensajes", "70" to "hilos", "21,1" to "al día"),
        EstadisticasMiembro.cifras("400", "70", "08-ago-2026", hoy)
    )

    @Test
    fun `los miles se quedan como los escribe FC`() = assertEquals(
        "7.253" to "mensajes",
        EstadisticasMiembro.cifras("7.253", "38", "30-nov-2004", hoy).first()
    )

    @Test
    fun `sin fecha no hay al dia`() = assertEquals(
        listOf("400" to "mensajes", "70" to "hilos"),
        EstadisticasMiembro.cifras("400", "70", "", hoy)
    )

    @Test
    fun `sin datos ningun bloque`() =
        assertEquals(emptyList<Pair<String, String>>(), EstadisticasMiembro.cifras("", "", "", hoy))

    @Test
    fun `rango y fecha`() =
        assertEquals("Miembro desde 30-may-2026", EstadisticasMiembro.desde("Miembro", "30-may-2026"))

    @Test
    fun `solo fecha`() = assertEquals("Desde 30-may-2026", EstadisticasMiembro.desde("", "30-may-2026"))

    @Test
    fun `solo rango`() = assertEquals("Usuario", EstadisticasMiembro.desde("Usuario", "basura"))

    @Test
    fun `nada`() = assertEquals("", EstadisticasMiembro.desde(" ", ""))
}
