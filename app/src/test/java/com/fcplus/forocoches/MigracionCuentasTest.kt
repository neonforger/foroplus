package com.fcplus.forocoches

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Cuándo hay que mover los datos "sin dueño" a la cuenta activa.
 *
 * Esto le pasa a TODO el que actualice, no solo a quien use varias cuentas: si falla, el foro
 * entero le sale en negrita porque se han perdido las marcas de leído.
 */
class MigracionCuentasTest {

    @Test
    fun `con sesion y sin migrar todavia, se migra`() {
        assertTrue(MigracionCuentas.hayQueMigrar(yaHecha = false, uidActivo = "911128", hayCuentas = false))
    }

    @Test
    fun `si ya se hizo no se repite`() {
        assertFalse(MigracionCuentas.hayQueMigrar(yaHecha = true, uidActivo = "911128", hayCuentas = false))
    }

    /** Sin sesión no se sabe de quién son esos datos: se esperan a que entre. */
    @Test
    fun `sin sesion no se migra nada`() {
        assertFalse(MigracionCuentas.hayQueMigrar(yaHecha = false, uidActivo = "", hayCuentas = false))
    }

    /** Si ya hay cuentas guardadas, esta instalación ya vive en el mundo nuevo. */
    @Test
    fun `con cuentas ya guardadas no se migra`() {
        assertFalse(MigracionCuentas.hayQueMigrar(yaHecha = false, uidActivo = "911128", hayCuentas = true))
    }

    /**
     * La regla que protege a los 144: mientras el destino no esté confirmado, el origen se
     * queda. Aquí se comprueba la DECISIÓN; que el copiado no borre nada se verifica a mano.
     */
    @Test
    fun `tras migrar con exito ya no hay que volver a migrar`() {
        assertTrue(MigracionCuentas.hayQueMigrar(yaHecha = false, uidActivo = "911128", hayCuentas = false))
        // yaHecha pasa a true SOLO cuando el copiado terminó bien
        assertFalse(MigracionCuentas.hayQueMigrar(yaHecha = true, uidActivo = "911128", hayCuentas = false))
    }
}
