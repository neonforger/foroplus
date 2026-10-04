package com.fcplus.forocoches

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EnlacesAppTest {

    private fun ofrecer(
        soportado: Boolean = true,
        yaActivado: Boolean = false,
        sesiones: Int = EnlacesApp.SESIONES_MINIMAS,
        descartado: Boolean = false,
        hayAvisoRemoto: Boolean = false
    ) = EnlacesApp.debeOfrecer(soportado, yaActivado, sesiones, descartado, hayAvisoRemoto)

    @Test
    fun `el caso normal es Android 12 o mas, apagado y con la app ya conocida`() {
        assertTrue(ofrecer())
    }

    @Test
    fun `por debajo de Android 12 no se molesta a nadie`() {
        // Ahí el sistema ya ofrece la app en el diálogo de "abrir con": no hay problema.
        assertFalse(ofrecer(soportado = false))
    }

    @Test
    fun `a quien ya lo tiene activado no se le ofrece`() {
        assertFalse(ofrecer(yaActivado = true))
    }

    @Test
    fun `no en el primer arranque`() {
        assertFalse(ofrecer(sesiones = 1))
        assertFalse(ofrecer(sesiones = 2))
        assertTrue(ofrecer(sesiones = 3))
    }

    @Test
    fun `si lo cerro, no se insiste`() {
        assertFalse(ofrecer(descartado = true))
    }

    @Test
    fun `un aviso remoto tiene prioridad`() {
        // El canal de avisos es para emergencias; competir con él lo devalúa.
        assertFalse(ofrecer(hayAvisoRemoto = true))
    }

    @Test
    fun `muchas sesiones despues sigue ofreciendose si no lo ha cerrado`() {
        assertTrue(ofrecer(sesiones = 400))
    }
}
