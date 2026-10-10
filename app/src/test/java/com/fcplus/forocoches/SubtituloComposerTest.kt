package com.fcplus.forocoches

import org.junit.Assert.assertEquals
import org.junit.Test

/** La línea bajo "Responder": con qué cuenta y en qué hilo (fase 2 del rediseño). */
class SubtituloComposerTest {

    @Test fun `cuenta y hilo`() =
        assertEquals("como @neonforger · en Mi hilo", Cuentas.subtituloComposer("como @neonforger", "Mi hilo"))

    @Test fun `el hilo sale limpio, sin la pagina`() =
        assertEquals("como @neonforger · en Mi hilo", Cuentas.subtituloComposer("como @neonforger", "Mi hilo - Página 3"))

    @Test fun `sin cuenta que enseñar, solo el hilo`() =
        assertEquals("en Mi hilo", Cuentas.subtituloComposer(null, "Mi hilo"))

    @Test fun `sin hilo, solo la cuenta`() =
        assertEquals("como @neonforger", Cuentas.subtituloComposer("como @neonforger", "  "))

    @Test fun `sin nada, vacio`() = assertEquals("", Cuentas.subtituloComposer(null, ""))
}
