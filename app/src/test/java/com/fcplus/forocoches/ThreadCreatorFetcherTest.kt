package com.fcplus.forocoches

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ThreadCreatorFetcherTest {

    private val fetcher = ThreadCreatorFetcher()

    private fun fixture(name: String): String =
        javaClass.classLoader!!.getResourceAsStream(name)!!
            .bufferedReader(Charsets.UTF_8).readText()

    @Test
    fun `creador en skin viejo (primer post, span xsaid)`() {
        assertEquals("Antonio_JTG", fetcher.firstPostAuthor(fixture("showthread_old.html")))
    }

    @Test
    fun `creador en skin nuevo (primer post, postmenu)`() {
        assertEquals("Benhur", fetcher.firstPostAuthor(fixture("showthread_mobile.html")))
    }

    @Test
    fun `funciona con HTML parcial (corte temprano)`() {
        // Simula el buffer parcial: corta poco después del primer post (incluye su ancla
        // de autor completa). Debe extraer el creador sin el resto del hilo.
        val full = fixture("showthread_old.html")
        val cut = full.substring(0, minOf(full.length, full.indexOf("class=\"xsaid\"") + 2000))
        assertEquals("Antonio_JTG", fetcher.firstPostAuthor(cut))
    }

    @Test
    fun `sin posts devuelve null`() {
        assertNull(fetcher.firstPostAuthor("<html><body><p>nada</p></body></html>"))
    }
}
