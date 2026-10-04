package com.fcplus.forocoches

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class IgnoreListRepositoryTest {

    private lateinit var repo: IgnoreListRepository

    @Before
    fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        repo = IgnoreListRepository(ctx)
    }

    @Test
    fun `getIgnoredUsers devuelve lista vacía inicialmente`() {
        assertTrue(repo.getIgnoredUsers().isEmpty())
    }

    @Test
    fun `setIgnoredUsers persiste y getIgnoredUsers devuelve los mismos usuarios`() {
        val users = listOf("UsuarioUno", "UsuarioDos")
        repo.setIgnoredUsers(users)
        assertEquals(users.toSet(), repo.getIgnoredUsers().toSet())
    }

    @Test
    fun `getLastUpdated devuelve 0 si nunca se actualizó`() {
        assertEquals(0L, repo.getLastUpdated())
    }

    @Test
    fun `setIgnoredUsers actualiza lastUpdated`() {
        val before = System.currentTimeMillis()
        repo.setIgnoredUsers(listOf("user1"))
        val after = System.currentTimeMillis()
        assertTrue(repo.getLastUpdated() in before..after)
    }
}
