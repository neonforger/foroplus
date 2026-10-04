package com.fcplus.forocoches

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DescargasTest {

    @Test
    fun `la carpeta es el tid y nada mas`() {
        assertEquals("10812099", Descargas.carpetaDe("10812099"))
        // Nada de rutas: un tid con basura no puede salirse de su carpeta.
        assertEquals("10812099", Descargas.carpetaDe("../10812099"))
        assertEquals("", Descargas.carpetaDe(""))
        assertEquals("", Descargas.carpetaDe("../../etc"))
    }

    @Test
    fun `un tid que no es numero no vale`() {
        assertTrue(Descargas.tidValido("10812099"))
        assertFalse(Descargas.tidValido(""))
        assertFalse(Descargas.tidValido("hola"))
    }

    // ── La guarda: sustituir solo pregunta cuando se pierde algo ─────────────

    @Test
    fun `no pregunta si la copia nueva trae lo mismo o mas`() {
        assertFalse(Descargas.hayQuePreguntar(210, 210))
        assertFalse(Descargas.hayQuePreguntar(210, 240))
    }

    @Test
    fun `pregunta si la copia nueva trae menos mensajes`() {
        assertTrue(Descargas.hayQuePreguntar(210, 193))
        assertTrue(Descargas.hayQuePreguntar(210, 0))
    }

    /** Primera descarga: no hay nada que perder, así que no se pregunta. */
    @Test
    fun `no pregunta si no habia copia`() {
        assertFalse(Descargas.hayQuePreguntar(0, 193))
    }

    // ── Tamaños ─────────────────────────────────────────────────────────────

    @Test
    fun `los tamanos medidos hoy salen como se esperan`() {
        assertEquals("182 KB", Descargas.tamanoLegible(186_368))   // el hilo de 7 páginas
        assertEquals("41 KB", Descargas.tamanoLegible(41_984))
        assertEquals("12 KB", Descargas.tamanoLegible(12_288))
    }

    @Test
    fun `de KB a MB y a GB`() {
        assertEquals("999 B", Descargas.tamanoLegible(999))
        assertEquals("1 KB", Descargas.tamanoLegible(1024))
        assertEquals("1,0 MB", Descargas.tamanoLegible(1024L * 1024))
        assertEquals("1,9 MB", Descargas.tamanoLegible((1.9 * 1024 * 1024).toLong()))
        assertEquals("18 MB", Descargas.tamanoLegible(18L * 1024 * 1024))
        assertEquals("2,0 GB", Descargas.tamanoLegible(2L * 1024 * 1024 * 1024))
    }

    @Test
    fun `un tamano negativo no revienta`() {
        assertEquals("0 KB", Descargas.tamanoLegible(-1))
    }

    // ── Textos y páginas ────────────────────────────────────────────────────

    @Test
    fun `el resumen va en singular cuando toca`() {
        assertEquals("7 páginas · 210 mensajes", Descargas.resumen(7, 210))
        assertEquals("1 página · 1 mensaje", Descargas.resumen(1, 1))
    }

    @Test
    fun `se piden todas las paginas, empezando por la primera`() {
        assertEquals(listOf(1, 2, 3, 4, 5, 6, 7), Descargas.paginasAPedir(7))
        assertEquals(listOf(1), Descargas.paginasAPedir(1))
    }

    /** FC no siempre sabe decir cuántas páginas hay: eso es UNA página, no ninguna. */
    @Test
    fun `sin numero de paginas se pide una`() {
        assertEquals(listOf(1), Descargas.paginasAPedir(0))
        assertEquals(listOf(1), Descargas.paginasAPedir(-3))
    }

    // ── Qué imágenes se bajan ───────────────────────────────────────────────

    /** El markup real medido hoy: una directa y una por el proxy de FC. */
    private val htmlReal = listOf(
        """<img class="imgpost" src="https://i.imgur.com/KU4ahS4.gif">""",
        """<img class="imgpost" src="https://images.forocoches.com?url=https%3A%2F%2Fst3.depositphotos.com%2Fx.jpg">""",
        """texto con <img src="https://forocoches.com/foro/images/smilies/roto2.gif"> un smiley"""
    )

    @Test
    fun `saca las imagenes de verdad y deja fuera los smilies`() {
        val urls = Descargas.urlsDeImagen(htmlReal)
        assertEquals(2, urls.size)
        assertTrue(urls[0].startsWith("https://i.imgur.com/"))
        assertTrue(urls[1].startsWith("https://images.forocoches.com"))
    }

    @Test
    fun `no repite la misma imagen`() {
        val repetida = listOf(
            """<img src="https://i.imgur.com/a.png">""",
            """<img src="https://i.imgur.com/a.png">"""
        )
        assertEquals(1, Descargas.urlsDeImagen(repetida).size)
    }

    @Test
    fun `ignora lo que no es http`() {
        val raros = listOf("""<img src="data:image/png;base64,AAAA">""", """<img src="/foro/x.png">""")
        assertEquals(0, Descargas.urlsDeImagen(raros).size)
    }

    @Test
    fun `el nombre del fichero es estable y sale del url`() {
        val a = Descargas.nombreDeImagen("https://i.imgur.com/a.png")
        assertEquals(a, Descargas.nombreDeImagen("https://i.imgur.com/a.png"))
        assertTrue(a.endsWith(".img"))
        assertTrue(a != Descargas.nombreDeImagen("https://i.imgur.com/b.png"))
    }
}
