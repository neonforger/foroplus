package com.fcplus.forocoches

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
        pendientes.para("111")(a)
        pendientes.para("222")(b)
        assertEquals(a, pendientes.de("111"))
        assertEquals(b, pendientes.de("222"))
        pendientes.para("222")(null)
        assertNull(pendientes.de("222"))
        assertEquals(a, pendientes.de("111"))
    }

    @Test fun sinCuentaNoHayNada() {
        pendientes.guardar(Pendiente("", "x", 1L))
        assertNull(pendientes.de(""))
    }
}
