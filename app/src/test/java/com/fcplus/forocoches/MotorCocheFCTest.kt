package com.fcplus.forocoches

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.concurrent.thread

/** El cerrojo y el número de petición; el JS de verdad se prueba en el móvil. */
@RunWith(RobolectricTestRunner::class)
class MotorCocheFCTest {
    private val lanzados = mutableListOf<String>()
    /** Lo que contesta "el JS" a cada petición, desde otro hilo como el puente. */
    private var contesta: (id: Int) -> String? = { null }
    private lateinit var motor: MotorCocheFC

    private fun nuevo() {
        motor = MotorCocheFC({ js ->
            synchronized(lanzados) { lanzados += js }
            val id = Regex(",(\\d+)\\)$").find(js)!!.groupValues[1].toInt()
            thread { contesta(id)?.let { motor.alLlegar(it) } }
        }, esperaMs = 500)
    }

    /** En Robolectric el hilo del test ES el de la interfaz, y el motor no deja bloquearlo. */
    private fun <T> fuera(bloque: () -> T): T {
        var r: Result<T>? = null
        thread { r = runCatching(bloque) }.join()
        return r!!.getOrThrow()
    }

    @Test fun leeElCocheDeLaCuenta() {
        nuevo()
        contesta = { id -> """{"ok":true,"valor":"Seat Ibiza 1.9 TDI 2004","peticion":$id}""" }
        val m = motor.para("123456")
        assertEquals("Seat Ibiza 1.9 TDI 2004", fuera { m.leer() })
        assertNull(m.ultimoError)
        assertTrue(lanzados.single().startsWith("window.fcLeerCoche&&fcLeerCoche(\"123456\","))
    }

    @Test fun ponerSoloValeSiFCTieneLoPedido() {
        nuevo()
        contesta = { id -> """{"ok":true,"valor":"seat ibiza 1.9 tdi 2004","pedido":"x","peticion":$id}""" }
        val m = motor.para("123456")
        assertTrue(fuera { m.poner("Seat Ibiza 1.9 TDI 2004") })
        assertFalse(fuera { m.poner("Audi A3 1.6 TDI Sport 2015") })
        assertEquals("fc", m.ultimoError)
    }

    @Test fun lasComillasNoRompenElJs() {
        nuevo()
        contesta = { id -> """{"ok":true,"valor":"","peticion":$id}""" }
        fuera { motor.para("123456").poner("O'Neil \"GT\"\n") }
        assertTrue(lanzados.single().startsWith("window.fcPonerCoche&&fcPonerCoche(\"O'Neil \\\"GT\\\"\\n\",\"123456\","))
    }

    @Test fun laRespuestaDeOtraPeticionSeTira() {
        nuevo()
        contesta = { id -> """{"ok":true,"valor":"Seat Ibiza 1.9 TDI 2004","peticion":${id + 1}}""" }
        val m = motor.para("123456")
        assertNull(fuera { m.leer() })
        assertEquals("tiempo", m.ultimoError)
    }

    @Test fun ponerSinRespuestaQuedaDudosa() {
        // Sin respuesta en el plazo, el POST pudo llegar a FC más tarde: quien devuelve el
        // original tiene que saberlo para esperar y volver a mirar.
        nuevo()
        contesta = { null }
        val m = motor.para("123456")
        assertFalse(fuera { m.poner("Audi A3 1.6 TDI Sport 2015") })
        assertTrue(m.escrituraDudosa)
        assertEquals("tiempo", m.ultimoError)
        contesta = { id -> """{"ok":true,"valor":"Seat Ibiza 1.9 TDI 2004","peticion":$id}""" }
        assertTrue(fuera { m.poner("Seat Ibiza 1.9 TDI 2004") })
        assertFalse(m.escrituraDudosa)
    }

    @Test fun laCadenaCortadaPorPlazoTambienEsDudosa() {
        // El JS corta su cadena a los 10 s: si el POST ya había salido, pudo llegar igual.
        nuevo()
        contesta = { id -> """{"ok":false,"error":"plazo","peticion":$id}""" }
        val m = motor.para("123456")
        assertFalse(fuera { m.poner("Audi A3 1.6 TDI Sport 2015") })
        assertTrue(m.escrituraDudosa)
    }

    @Test fun unaRespuestaDeFCNoEsDudosa() {
        nuevo()
        contesta = { id -> """{"ok":false,"error":"challenge","peticion":$id}""" }
        val m = motor.para("123456")
        assertFalse(fuera { m.poner("Audi A3 1.6 TDI Sport 2015") })
        assertFalse(m.escrituraDudosa)
    }

    @Test fun elErrorDelMotorSeGuarda() {
        nuevo()
        contesta = { id -> """{"ok":false,"error":"challenge","peticion":$id}""" }
        val m = motor.para("123456")
        assertNull(fuera { m.leer() })
        assertEquals("challenge", m.ultimoError)
    }

    @Test(expected = IllegalStateException::class)
    fun desdeLaInterfazNo() {
        nuevo()
        motor.para("123456").leer()
    }
}
