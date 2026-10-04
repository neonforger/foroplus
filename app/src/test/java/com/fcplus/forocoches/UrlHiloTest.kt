package com.fcplus.forocoches

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * El fallo que arreglan estos tests: "las páginas se quedan congeladas".
 *
 * Reportado por Green Floyd el 2026-09-13 con la repro exacta ("ve al último tema CERRADO
 * haciendo clic en el último mensaje, y luego a páginas anteriores"), y confirmado por el
 * dueño. Medido por CDP contra FC el 2026-09-15 sobre el hilo 10804016:
 *
 *   ?p=519079808          → posts 519079675 … 519079808
 *   ?p=519079808&page=2   → posts 519079675 … 519079808   (IDÉNTICO)
 *   ?p=519079808&page=3   → posts 519079675 … 519079808   (IDÉNTICO)
 *   ?t=10804016&page=2    → posts 519079810 … 519079981   (distinto, correcto)
 *
 * O sea: **FC ignora `page=` cuando la URL lleva `p=`**. Y al entrar por el último mensaje
 * del listado la URL es `?p=` (gotcha 14), así que si nadie la canonicaliza a `?t=` toda la
 * paginación pide siempre la misma página.
 */
class UrlHiloTest {

    @Test
    fun `una url por tid se reconoce`() {
        assertTrue(UrlHilo.tieneTid("https://forocoches.com/foro/showthread.php?t=10804016"))
        assertTrue(UrlHilo.tieneTid("https://forocoches.com/foro/showthread.php?t=10804016&page=7"))
    }

    @Test
    fun `una url por post NO tiene tid`() {
        assertFalse(UrlHilo.tieneTid("https://forocoches.com/foro/showthread.php?p=519079808"))
    }

    /** "showthread" lleva una `t` pegada a otras letras: no puede colar como `t=`. */
    @Test
    fun `el nombre del script no se confunde con el parametro`() {
        assertFalse(UrlHilo.tieneTid("https://forocoches.com/foro/showthread.php"))
        assertFalse(UrlHilo.tieneTid("https://forocoches.com/foro/printthread.php?p=1"))
    }

    /** Un `t=` de otro parámetro (`highlight=`, `goto=`) tampoco es el del hilo. */
    @Test
    fun `solo cuenta el parametro t entero`() {
        assertFalse(UrlHilo.tieneTid("https://forocoches.com/foro/showthread.php?p=1&highlight=t=2"))
    }

    @Test
    fun `la pagina 1 no lleva sufijo`() {
        val u = "https://forocoches.com/foro/showthread.php?t=10804016"
        assertEquals(u, UrlHilo.pagina(u, 1, ""))
    }

    @Test
    fun `sobre una url por tid se anade page`() {
        assertEquals(
            "https://forocoches.com/foro/showthread.php?t=10804016&page=64",
            UrlHilo.pagina("https://forocoches.com/foro/showthread.php?t=10804016", 64, "10804016")
        )
    }

    /**
     * EL BUG. Sobre una URL por post, añadir `page=` no sirve de nada: hay que reconstruir
     * desde el tid. Sin esto, la 64, la 63 y la 62 devolvían las tres la misma página.
     */
    @Test
    fun `sobre una url por post se reconstruye desde el tid`() {
        assertEquals(
            "https://forocoches.com/foro/showthread.php?t=10804016&page=64",
            UrlHilo.pagina("https://forocoches.com/foro/showthread.php?p=519079808", 64, "10804016")
        )
    }

    /**
     * Sin tid no hay nada que reconstruir. Se devuelve la URL por post TAL CUAL y sin
     * `page=`: mentirle a FC con un parámetro que ignora es lo que hacía creer que el salto
     * había funcionado. Así al menos la página que llega es la que se pidió.
     */
    @Test
    fun `sin tid no se inventa un page que FC va a ignorar`() {
        val u = "https://forocoches.com/foro/showthread.php?p=519079808"
        assertEquals(u, UrlHilo.pagina(u, 64, ""))
    }

    @Test
    fun `la canonica se construye con el tid`() {
        assertEquals(
            "https://forocoches.com/foro/showthread.php?t=10804016",
            UrlHilo.canonica("10804016")
        )
    }

    /** Ya canónica: no se toca (evita canonicalizar en bucle en cada página que llega). */
    @Test
    fun `una url que ya es canonica no cambia`() {
        val u = "https://forocoches.com/foro/showthread.php?t=10804016"
        assertEquals(u, UrlHilo.canonicalizar(u, "10804016"))
    }

    @Test
    fun `una url por post se canonicaliza en cuanto se sabe el tid`() {
        assertEquals(
            "https://forocoches.com/foro/showthread.php?t=10804016",
            UrlHilo.canonicalizar("https://forocoches.com/foro/showthread.php?p=519079808", "10804016")
        )
    }

    /** Si el tid no llegó (hilo cerrado con el extractor viejo), no se rompe nada. */
    @Test
    fun `sin tid la url se queda como estaba`() {
        val u = "https://forocoches.com/foro/showthread.php?p=519079808"
        assertEquals(u, UrlHilo.canonicalizar(u, ""))
    }
}
