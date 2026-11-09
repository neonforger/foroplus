package com.fcplus.forocoches

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CochePendienteTest {
    private val dia = 24L * 60 * 60 * 1000
    private val ahora = 1_800_000_000_000L
    private val p = Pendiente("123456", "Seat Ibiza 1.9 TDI 2004", ahora)

    @Test fun idaYVueltaDelJson() {
        assertEquals(p, CochePendiente.deJson(CochePendiente.aJson(p)))
    }

    @Test fun unCocheVacioTambienSeGuarda() {
        // Quien no tenía coche en el perfil también hay que dejarlo como estaba: sin coche.
        val vacio = Pendiente("123456", "", ahora)
        assertEquals(vacio, CochePendiente.deJson(CochePendiente.aJson(vacio)))
    }

    @Test fun jsonRotoOSinUidEsNull() {
        assertNull(CochePendiente.deJson(null))
        assertNull(CochePendiente.deJson(""))
        assertNull(CochePendiente.deJson("no es json"))
        assertNull(CochePendiente.deJson("""{"original":"x","desde":1}"""))
        assertNull(CochePendiente.deJson("""{"uid":"","original":"x","desde":1}"""))
        assertNull(CochePendiente.deJson("""{"uid":"123456","desde":1}"""))
    }

    @Test fun mismaCuentaYRecienteHayQueRestaurar() {
        assertTrue(CochePendiente.hayQueRestaurar(p, "123456", ahora + 5_000))
        assertTrue(CochePendiente.hayQueRestaurar(p, "123456", ahora + 29 * dia))
    }

    @Test fun otraCuentaNo() {
        assertFalse(CochePendiente.hayQueRestaurar(p, "654321", ahora))
        assertFalse(CochePendiente.hayQueRestaurar(p, "", ahora))
        assertFalse(CochePendiente.hayQueRestaurar(p, null, ahora))
    }

    @Test fun treintaYUnDiasNo() {
        assertFalse(CochePendiente.hayQueRestaurar(p, "123456", ahora + 31 * dia))
    }

    @Test fun nullNo() {
        assertFalse(CochePendiente.hayQueRestaurar(null, "123456", ahora))
    }
}
