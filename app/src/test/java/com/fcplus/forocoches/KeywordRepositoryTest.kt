package com.fcplus.forocoches

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class KeywordRepositoryTest {

    private lateinit var repo: KeywordRepository

    @Before
    fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        repo = KeywordRepository(ctx)
    }

    @Test
    fun `isEnabled devuelve true por defecto`() {
        assertTrue(repo.isEnabled())
    }

    @Test
    fun `setEnabled persiste el valor`() {
        repo.setEnabled(false)
        assertFalse(repo.isEnabled())
        repo.setEnabled(true)
        assertTrue(repo.isEnabled())
    }

    @Test
    fun `getKeywords devuelve las palabras por defecto inicialmente`() {
        val keywords = repo.getKeywords()
        assertTrue(keywords.isNotEmpty())
        assertTrue(keywords.any { it.equals("PSOE", ignoreCase = true) })
    }

    @Test
    fun `addKeyword añade una nueva palabra`() {
        repo.addKeyword("testword")
        assertTrue(repo.getKeywords().contains("testword"))
    }

    @Test
    fun `removeKeyword elimina la palabra`() {
        repo.addKeyword("testword")
        repo.removeKeyword("testword")
        assertFalse(repo.getKeywords().contains("testword"))
    }

    @Test
    fun `resetToDefaults restaura las palabras por defecto`() {
        repo.addKeyword("testword")
        repo.removeKeyword("PSOE")
        repo.resetToDefaults()
        val keywords = repo.getKeywords()
        assertFalse(keywords.contains("testword"))
        assertTrue(keywords.any { it.equals("PSOE", ignoreCase = true) })
    }
}
