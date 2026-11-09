package com.fcplus.forocoches

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PendientesCocheTest {
    private lateinit var pendientes: PendientesCoche

    @Before fun preparar() {
        val prefs = ApplicationProvider.getApplicationContext<Context>()
            .getSharedPreferences("test_musica_coche", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        pendientes = PendientesCoche(prefs)
    }

    @Test fun loDeUnaCuentaNoLoVeOtraNiLoPisa() {
        val a = Pendiente("111", "Seat Ibiza 1.9 TDI 2004", 1L)
        val b = Pendiente("222", "", 2L)
        assertTrue(pendientes.para("111").guardar(a))
        assertTrue(pendientes.para("222").guardar(b))
        assertEquals(a, pendientes.de("111"))
        assertEquals(b, pendientes.para("222").leer())
        pendientes.para("222").borrar()
        assertNull(pendientes.de("222"))
        assertEquals(a, pendientes.de("111"))
    }

    @Test fun nuncaPisaUnOriginalGuardado() {
        // El original guardado es lo único que queda del coche de verdad mientras el de prueba
        // siga puesto: un segundo guardar (otra verificación) no puede sustituirlo.
        val bueno = Pendiente("111", "Seat Ibiza 1.9 TDI 2004", 1L)
        val dePrueba = Pendiente("111", "Audi A3 1.6 TDI Sport 2015", 2L)
        assertTrue(pendientes.para("111").guardar(bueno))
        assertFalse(pendientes.para("111").guardar(dePrueba))
        assertEquals(bueno, pendientes.de("111"))
    }

    @Test fun conOtraCuentaEnElPendienteNoSeGuarda() {
        assertFalse(pendientes.para("111").guardar(Pendiente("222", "x", 1L)))
        assertNull(pendientes.de("111"))
        assertNull(pendientes.de("222"))
    }

    @Test fun sinCuentaNoHayNada() {
        assertFalse(pendientes.para("").guardar(Pendiente("", "x", 1L)))
        assertNull(pendientes.de(""))
    }
}
