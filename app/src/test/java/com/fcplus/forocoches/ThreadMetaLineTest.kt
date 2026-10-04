package com.fcplus.forocoches

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * La hora dejó de ir dentro de la línea de meta: ahora es un elemento aparte porque **es un
 * enlace al último mensaje del hilo** y necesita su propia zona de toque.
 */
class ThreadMetaLineTest {

    @Test
    fun `la meta es solo el autor`() {
        assertEquals("@Almogávar01", threadMetaLine("Almogávar01"))
    }

    @Test
    fun `sin autor la meta queda vacia`() {
        assertEquals("", threadMetaLine(""))
    }

    @Test
    fun `la hora lleva chevron cuando se puede tocar`() {
        // Es la única pista de que lleva a algún sitio: en la web de FC es texto gris idéntico
        // al resto y nadie descubre que es un enlace.
        assertEquals("Hoy a las 16:12  ›", threadTimeLabel("Hoy a las 16:12", true))
    }

    @Test
    fun `sin enlace la hora se pinta tal cual`() {
        assertEquals("Ayer a las 09:12", threadTimeLabel("Ayer a las 09:12", false))
    }

    @Test
    fun `sin hora no se pinta nada, ni el chevron`() {
        assertEquals("", threadTimeLabel("", true))
        assertEquals("", threadTimeLabel("", false))
    }

    @Test
    fun `la fecha absoluta se respeta`() {
        assertEquals("31/07/25 10:46  ›", threadTimeLabel("31/07/25 10:46", true))
    }
}
