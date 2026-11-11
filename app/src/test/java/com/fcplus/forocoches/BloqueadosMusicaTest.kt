package com.fcplus.forocoches

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class BloqueadosMusicaTest {
    private lateinit var b: BloqueadosMusica

    @Before fun preparar() {
        val p = ApplicationProvider.getApplicationContext<Context>().getSharedPreferences("test_bloqueados", Context.MODE_PRIVATE)
        p.edit().clear().commit()
        b = BloqueadosMusica(p)
    }

    @Test fun `bloquear y consultar sin distinguir mayusculas`() {
        b.bloquear("Pepe")
        assertTrue(b.contiene("pepe"))
        assertFalse(b.contiene("Ana"))
    }

    @Test fun `no duplica ni guarda vacios`() {
        b.bloquear("Pepe"); b.bloquear("PEPE"); b.bloquear("  ")
        assertEquals(listOf("Pepe"), b.nombres())
    }

    @Test fun `desbloquear`() {
        b.bloquear("Pepe"); b.bloquear("Ana")
        b.desbloquear("pepe")
        assertEquals(listOf("Ana"), b.nombres())
    }

    @Test fun `tope de 200 olvida al mas antiguo`() {
        for (i in 1..205) b.bloquear("u$i")
        assertEquals(BloqueadosMusica.MAX, b.nombres().size)
        assertFalse(b.contiene("u1"))
        assertTrue(b.contiene("u205"))
    }

    @Test fun `un valor roto no rompe`() {
        val p = ApplicationProvider.getApplicationContext<Context>().getSharedPreferences("test_bloqueados", Context.MODE_PRIVATE)
        p.edit().putString("nombres", "{no es json").commit()
        assertEquals(emptyList<String>(), BloqueadosMusica(p).nombres())
    }
}
