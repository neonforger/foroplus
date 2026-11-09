package com.fcplus.forocoches

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/**
 * `restaurarCoche` lee lo pendiente DENTRO del hilo del coche, no al encolar: si había una
 * verificación en marcha, cuando le toca ya lo ha devuelto ella y no hay nada que hacer.
 */
@RunWith(RobolectricTestRunner::class)
class EnganchesMusicaTest {
    private val original = "Seat Ibiza 1.9 TDI 2004"
    private val lanzados = mutableListOf<String>()
    private lateinit var pendientes: PendientesCoche
    private lateinit var enganches: EnganchesMusica

    @Before fun preparar() {
        val prefs = ApplicationProvider.getApplicationContext<Context>()
            .getSharedPreferences("test_enganches_coche", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        pendientes = PendientesCoche(prefs)
        lateinit var motor: MotorCocheFC
        // "FC" contesta que el perfil tiene el original (para poner y para leer).
        motor = MotorCocheFC({ js ->
            synchronized(lanzados) { lanzados += js }
            val id = Regex(",(\\d+)\\)$").find(js)!!.groupValues[1].toInt()
            thread { motor.alLlegar("""{"ok":true,"valor":"$original","peticion":$id}""") }
        }, esperaMs = 2_000)
        enganches = EnganchesMusica(
            servidorActivo = { true }, uidActivo = { "123456" }, cambiandoDeCuenta = { false },
            motorCoche = motor, tokens = lazy { error("no hace falta") }, pendientes = pendientes, entrar = { }
        )
    }

    /** Ocupa el hilo del coche hasta que se suelte el cerrojo (una verificación en marcha). */
    private fun ocupar(): CountDownLatch {
        val suelta = CountDownLatch(1)
        val empezado = CountDownLatch(1)
        MotorCocheFC.hilo.execute { empezado.countDown(); suelta.await(5, TimeUnit.SECONDS) }
        empezado.await(5, TimeUnit.SECONDS)
        return suelta
    }

    private fun esperarAlHilo() = MotorCocheFC.hilo.submit { }.get(10, TimeUnit.SECONDS)

    @Test fun loPendienteSeLeeAlTocarleNoAlEncolar() {
        pendientes.para("123456").guardar(Pendiente("123456", original, System.currentTimeMillis()))
        val suelta = ocupar()
        enganches.restaurarCoche("123456")
        // Mientras esperaba, la verificación en marcha devolvió el coche y borró lo guardado.
        pendientes.para("123456").borrar()
        suelta.countDown()
        esperarAlHilo()
        assertEquals(emptyList<String>(), synchronized(lanzados) { lanzados.toList() })
    }

    @Test fun conUnaYaEnColaNoSeEncolaOtra() {
        pendientes.para("123456").guardar(Pendiente("123456", original, System.currentTimeMillis()))
        val suelta = ocupar()
        repeat(5) { enganches.restaurarCoche("123456") }   // arrancar, onResume, onResume…
        suelta.countDown()
        esperarAlHilo()
        // Una sola restauración: poner + leer.
        assertEquals(2, synchronized(lanzados) { lanzados.size })
        assertNull(pendientes.de("123456"))
    }

    @Test fun siSigueGuardadoSeDevuelve() {
        pendientes.para("123456").guardar(Pendiente("123456", original, System.currentTimeMillis()))
        enganches.restaurarCoche("123456")
        esperarAlHilo()
        val js = synchronized(lanzados) { lanzados.toList() }
        assertTrue(js[0].startsWith("window.fcPonerCoche&&fcPonerCoche(\"$original\",\"123456\","))
        assertTrue(js[1].startsWith("window.fcLeerCoche&&fcLeerCoche(\"123456\","))
        assertNull(pendientes.de("123456"))
    }
}
