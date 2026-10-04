package com.fcplus.forocoches

import android.webkit.CookieManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Que guardar y reponer cookies funcione con el `CookieManager` DE VERDAD.
 *
 * No hace falta una sesión real de FC: con cookies de mentira se prueba el mecanismo, que es lo
 * que puede romperse. La sesión real ya se verificó a mano el 2026-09-16.
 *
 * OJO: `connectedDebugAndroidTest` **desinstala la app debug al terminar** y se lleva la sesión
 * de FC del `.v2`. Si hacía falta esa sesión para otra prueba, hacerla ANTES.
 */
@RunWith(AndroidJUnit4::class)
class SesionFCInstrumentadoTest {

    /**
     * `removeAllCookies` exige un hilo con `Looper` (ver KDoc de [SesionFC.borrar]). El hilo de
     * instrumentación no lo tiene, así que hay que lanzar el borrado en el hilo principal — pero
     * ESPERAR el latch aquí, en el hilo del test: si se espera dentro de `runOnMainSync`, el
     * propio hilo principal bloqueado nunca podría entregar el callback y sería interbloqueo.
     */
    private fun borrarYEsperar(cm: CookieManager) {
        val latch = CountDownLatch(1)
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            SesionFC.borrar(cm) { latch.countDown() }
        }
        assertTrue("el borrado no terminó", latch.await(10, TimeUnit.SECONDS))
    }

    @Test
    fun las_cookies_de_sesion_se_guardan_y_se_reponen() {
        val cm = CookieManager.getInstance()
        cm.setAcceptCookie(true)
        borrarYEsperar(cm)

        val falsas = mapOf(
            "bbuserid" to "123456",
            "bbpassword" to "abcdefabcdefabcdefabcdefabcdef12",
            "bbsessionhash" to "0123456789abcdef0123456789abcdef",
            "bbimloggedin" to "yes"
        )
        SesionFC.poner(cm, falsas)

        val leidas = SesionFC.leer(cm)
        assertEquals("deben volver las cuatro", falsas, leidas)

        borrarYEsperar(cm)
        assertEquals("tras borrar no debe quedar ninguna", emptyMap<String, String>(), SesionFC.leer(cm))

        SesionFC.poner(cm, leidas)
        assertEquals("y al reponerlas deben volver iguales", falsas, SesionFC.leer(cm))

        borrarYEsperar(cm)
    }

    /** Las que no son de sesión no se llevan por delante al leer. */
    @Test
    fun leer_solo_devuelve_las_de_sesion() {
        val cm = CookieManager.getInstance()
        cm.setAcceptCookie(true)
        borrarYEsperar(cm)
        cm.setCookie(SesionFC.URL, SesionFC.aSetCookie("bbuserid", "123456"))
        cm.setCookie(SesionFC.URL, SesionFC.aSetCookie("bblastvisit", "999"))
        cm.flush()

        assertEquals(setOf("bbuserid"), SesionFC.leer(cm).keys)

        borrarYEsperar(cm)
    }
}
