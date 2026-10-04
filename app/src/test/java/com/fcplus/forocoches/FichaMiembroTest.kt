package com.fcplus.forocoches

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FichaMiembroTest {

    @Test
    fun `cada icono de FC se traduce a su etiqueta`() {
        // Iconos reales del bloque "Sobre mí", sondeados el 2026-09-27 en member.php?u=911128.
        val campos = FichaMiembro.campos(
            listOf(
                "coches" to "polo 600",
                "ubicacion-icon" to "Madrid",
                "intereses-icon" to "Motos",
                "work" to "Fontanero"
            )
        )
        assertEquals(
            listOf(
                FichaMiembro.Campo("Coche", "polo 600"),
                FichaMiembro.Campo("Ubicación", "Madrid"),
                FichaMiembro.Campo("Intereses", "Motos"),
                FichaMiembro.Campo("Ocupación", "Fontanero")
            ),
            campos
        )
    }

    @Test
    fun `en tu propia ficha FC pinta N-A en los vacios y eso no se ensena`() {
        val campos = FichaMiembro.campos(
            listOf("coches" to "polo 600", "ubicacion-icon" to "N/A", "work" to " n/a ")
        )
        assertEquals(listOf(FichaMiembro.Campo("Coche", "polo 600")), campos)
    }

    @Test
    fun `un campo vacio o un icono desconocido no dejan hueco`() {
        val campos = FichaMiembro.campos(
            listOf("coches" to "   ", "icono-nuevo-de-fc" to "algo", "work" to "Taxista")
        )
        assertEquals(listOf(FichaMiembro.Campo("Ocupación", "Taxista")), campos)
    }

    @Test
    fun `el orden es siempre el mismo aunque FC los cambie de sitio`() {
        val campos = FichaMiembro.campos(listOf("work" to "Taxista", "coches" to "Ibiza"))
        assertEquals(listOf("Coche", "Ocupación"), campos.map { it.etiqueta })
    }

    @Test
    fun `los espacios de mas se recogen`() {
        val campos = FichaMiembro.campos(listOf("coches" to "  seat \n  león  "))
        assertEquals("seat león", campos.single().valor)
    }

    @Test
    fun `el aviso de firma sin configurar no es una firma`() {
        // Lo que pinta FC en TU ficha cuando no tienes firma (sondeado el 2026-09-27).
        assertFalse(FichaMiembro.hayFirma("Aquí se verá tu firma, una vez la configures."))
        assertFalse(FichaMiembro.hayFirma("   "))
        assertTrue(
            FichaMiembro.hayFirma(
                "A veces, las cosas más difíciles en la vida son las que más valen la pena."
            )
        )
    }
}
