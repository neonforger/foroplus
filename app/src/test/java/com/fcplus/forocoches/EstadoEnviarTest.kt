package com.fcplus.forocoches

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * ¿Hay algo que enviar? Decide si el botón de enviar se pinta "listo" (rojo) o apagado (gris).
 * Con citas pendientes SÍ hay algo: la barra rápida las lleva al editor completo.
 */
class EstadoEnviarTest {

    @Test fun `vacio y sin citas no hay nada`() = assertFalse(EstadoEnviar.listo("", 0))

    @Test fun `solo espacios y saltos no cuentan`() = assertFalse(EstadoEnviar.listo("  \n \t ", 0))

    @Test fun `con texto si`() = assertTrue(EstadoEnviar.listo("hola", 0))

    @Test fun `con citas pendientes si aunque no haya texto`() = assertTrue(EstadoEnviar.listo("", 2))
}
