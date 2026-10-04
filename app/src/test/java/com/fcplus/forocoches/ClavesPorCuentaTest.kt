package com.fcplus.forocoches

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ClavesPorCuentaTest {

    @Test
    fun `un fichero lleva el uid detras`() {
        assertEquals("fc_leidos_911128", ClavesPorCuenta.fichero("fc_leidos", "911128"))
    }

    @Test
    fun `una clave lleva el uid detras`() {
        assertEquals("citas_vistas_911128", ClavesPorCuenta.clave("citas_vistas", "911128"))
    }

    /**
     * Sin uid se usa el nombre de siempre. Es lo que hace que la app siga funcionando mientras
     * no hay sesión, y lo que lee la migración cuando busca los datos antiguos.
     */
    @Test
    fun `sin uid se queda el nombre original`() {
        assertEquals("fc_leidos", ClavesPorCuenta.fichero("fc_leidos", ""))
        assertEquals("citas_vistas", ClavesPorCuenta.clave("citas_vistas", ""))
    }

    /** El caché de ignorados de FC es por cuenta: en FC ya lo es. */
    @Test
    fun `el fichero del filtro de ignorados va por cuenta`() {
        assertTrue("fc_filtro" in ClavesPorCuenta.FICHEROS)
    }

    /** La firma es por cuenta (decisión del dueño). */
    @Test
    fun `la firma va por cuenta`() {
        assertTrue("post_signature" in ClavesPorCuenta.CLAVES)
    }

    /** Estas son de la persona, no de la cuenta: no deben separarse. */
    @Test
    fun `el filtro de palabras y los hilos ignorados NO van por cuenta`() {
        assertFalse("fc_keywords" in ClavesPorCuenta.FICHEROS)
        assertFalse("hilos_ignorados" in ClavesPorCuenta.CLAVES)
    }

    @Test
    fun `el tema y las barras NO van por cuenta`() {
        assertFalse("post_font_idx" in ClavesPorCuenta.CLAVES)
        assertFalse("nav_orden" in ClavesPorCuenta.CLAVES)
        assertFalse("tabs_orden" in ClavesPorCuenta.CLAVES)
    }

    @Test
    fun `las cuatro marcas de citas y menciones van por cuenta`() {
        for (k in listOf("citas_vistas", "menciones_vistas", "vistas_quotes", "vistas_mentions")) {
            assertTrue(k, k in ClavesPorCuenta.CLAVES)
        }
    }

    /**
     * `mensajes_publicados` es el contador de la tarjeta de valoración de Play (de la APP),
     * no "mensajes tuyos" (de la CUENTA) pese al nombre: compartirlo entre cuentas es lo
     * correcto, así que NO debe separarse (ver el comentario en ClavesPorCuenta.CLAVES).
     */
    @Test
    fun `el contador de valoracion de Play NO va por cuenta`() {
        assertFalse("mensajes_publicados" in ClavesPorCuenta.CLAVES)
    }
}
