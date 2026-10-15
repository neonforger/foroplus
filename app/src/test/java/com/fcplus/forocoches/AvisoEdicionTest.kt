package com.fcplus.forocoches

import org.junit.Assert.assertEquals
import org.junit.Test

class AvisoEdicionTest {

    @Test
    fun `sin fecha de edicion no hay aviso`() {
        assertEquals("", AvisoEdicion.texto("", "", "Fulano"))
        assertEquals("", AvisoEdicion.texto("Fulano", "  ", "Fulano"))
    }

    @Test
    fun `si lo edita el propio autor no se repite su nombre`() {
        assertEquals("Editado · Ayer 22:51", AvisoEdicion.texto("Benhur", "Ayer 22:51", "Benhur"))
        // FC no siempre respeta mayúsculas igual en los dos sitios.
        assertEquals("Editado · Hoy 07:33", AvisoEdicion.texto("benhur", "Hoy 07:33", "Benhur"))
    }

    @Test
    fun `si lo edita otro se dice quien`() {
        assertEquals(
            "Editado por UnModerador · 14-nov-2025 00:03",
            AvisoEdicion.texto("UnModerador", "14-nov-2025 00:03", "Benhur")
        )
    }

    @Test
    fun `sin quien (solo el pie de FC) sale sin nombre`() {
        assertEquals("Editado · Ayer 22:51", AvisoEdicion.texto("", "Ayer 22:51", "Benhur"))
    }
}
