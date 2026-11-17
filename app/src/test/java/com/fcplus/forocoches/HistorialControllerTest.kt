package com.fcplus.forocoches

import android.content.Context
import android.content.SharedPreferences
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * El historial va POR CUENTA. El test que vale es el del puente (MEMORY, "declarar que un dato
 * va por cuenta no lo hace ir por cuenta"): escrito con la cuenta A, NO se lee con la B, y al
 * volver a A sigue ahí. El uid se cambia entre llamadas sin recrear nada, igual que en la app.
 */
@RunWith(RobolectricTestRunner::class)
class HistorialControllerTest {

    private lateinit var prefs: SharedPreferences
    private var uid = "111"
    private var reloj = 1_000_000L
    private lateinit var historial: HistorialController

    @Before
    fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        prefs = ctx.getSharedPreferences("shell_prefs", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        historial = HistorialController(AlmacenHistorial(prefs) { uid }) { reloj }
    }

    @Test
    fun `lo apuntado con una cuenta no se ve desde otra, y vuelve al volver`() {
        uid = "111"
        historial.apuntar("1", "Hilo de A", 2, "General", "pepe")
        uid = "222"
        assertTrue(historial.filas().isEmpty())
        historial.apuntar("9", "Hilo de B", 0, "", "")
        uid = "111"
        assertEquals(listOf("1"), historial.filas().map { it.tid })
        uid = "222"
        assertEquals(listOf("9"), historial.filas().map { it.tid })
    }

    @Test
    fun `se guarda con la clave por cuenta`() {
        uid = "111"
        historial.apuntar("1", "Hilo", 0, "", "")
        assertTrue(prefs.contains(ClavesPorCuenta.clave(AlmacenHistorial.CLAVE, "111")))
        assertTrue(!prefs.contains(AlmacenHistorial.CLAVE))
        assertTrue(AlmacenHistorial.CLAVE in ClavesPorCuenta.CLAVES)
    }

    @Test
    fun `borrar solo borra el de la cuenta activa`() {
        uid = "111"; historial.apuntar("1", "A", 0, "", "")
        uid = "222"; historial.apuntar("2", "B", 0, "", "")
        AlmacenHistorial(prefs) { uid }.borrar()
        assertTrue(historial.filas().isEmpty())
        uid = "111"
        assertEquals(listOf("1"), historial.filas().map { it.tid })
    }

    @Test
    fun `las filas llevan titulo, subforo, autor y visto hace X, sin respuestas ni ultimo mensaje`() {
        historial.apuntar("5", "Un hilo", 2, "General", "pepe")
        reloj += 5 * 60_000L
        val f = historial.filas().single()
        assertEquals("Un hilo", f.title)
        assertEquals(2, f.foroFid)
        assertEquals("pepe", f.author)
        assertEquals("visto hace 5 min", f.time)
        assertEquals("", f.replies)
        assertEquals("", f.lastPostUrl)
        assertEquals("https://forocoches.com/foro/showthread.php?t=5", f.url)
    }

    @Test
    fun `lo mas reciente arriba`() {
        historial.apuntar("1", "Uno", 0, "", "")
        reloj += 1000
        historial.apuntar("2", "Dos", 0, "", "")
        reloj += 1000
        historial.apuntar("1", "Uno", 0, "", "")
        assertEquals(listOf("1", "2"), historial.filas().map { it.tid })
    }
}
