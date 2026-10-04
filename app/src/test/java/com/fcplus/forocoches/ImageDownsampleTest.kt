package com.fcplus.forocoches

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Cálculo de inSampleSize para no decodificar fotos a resolución completa (revientan la caché /
 * la memoria). Cubre el bug: foto de 4000x1848 no se renderizaba por superar la LruCache de 24 MB.
 */
class ImageDownsampleTest {

    @Test fun `imagen pequena no se reduce`() {
        assertEquals(1, sampleSizeFor(1500, 1000, 2048))
    }

    @Test fun `justo en el limite no se reduce`() {
        assertEquals(1, sampleSizeFor(2048, 2048, 2048))
    }

    @Test fun `foto del bug 4000x1848 se reduce a la mitad`() {
        assertEquals(2, sampleSizeFor(4000, 1848, 2048))
    }

    @Test fun `un pixel por encima ya reduce`() {
        assertEquals(2, sampleSizeFor(2049, 100, 2048))
    }

    @Test fun `foto enorme 8000x6000 se reduce a un cuarto`() {
        assertEquals(4, sampleSizeFor(8000, 6000, 2048))
    }

    @Test fun `dimensiones invalidas devuelven 1`() {
        assertEquals(1, sampleSizeFor(0, 0, 2048))
    }
}
