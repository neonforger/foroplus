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
class DescargaMas18Test {

    private val cfg = ConfigMas18("https://raw.githubusercontent.com/neonforger/foroplus-mas18/datos/paginas/")
    private lateinit var prefs: android.content.SharedPreferences
    private val ejemplo = javaClass.classLoader!!.getResource("contrato/mas18-ejemplo.json").readText()

    @Before fun limpiar() {
        prefs = ApplicationProvider.getApplicationContext<Context>().getSharedPreferences("t_mas18", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
    }

    @Test fun `pide la url de la pagina y la lee`() {
        var pedida = ""
        val r = DescargaMas18(prefs) { pedida = it; ejemplo }.pagina(cfg, 1)
        assertEquals(cfg.urlPagina(1), pedida)
        assertTrue(r.lectura is LecturaMas18.Bien)
        assertFalse(r.deCache)
    }

    @Test fun `sin red usa la ultima buena de esa pagina`() {
        DescargaMas18(prefs) { ejemplo }.pagina(cfg, 1)
        val r = DescargaMas18(prefs) { null }.pagina(cfg, 1)
        assertTrue(r.lectura is LecturaMas18.Bien)
        assertTrue(r.deCache)
    }

    @Test fun `un 404 en html no pisa la cache buena`() {
        DescargaMas18(prefs) { ejemplo }.pagina(cfg, 1)
        val r = DescargaMas18(prefs) { "<html>404</html>" }.pagina(cfg, 1)
        assertTrue(r.lectura is LecturaMas18.Bien)
        assertTrue(r.deCache)
    }

    @Test fun `sin red y sin cache dice el motivo`() {
        val r = DescargaMas18(prefs) { null }.pagina(cfg, 2)
        assertTrue(r.lectura is LecturaMas18.Mal)
        assertEquals("Sin conexión: no se ha podido bajar la lista", (r.lectura as LecturaMas18.Mal).motivo)
    }

    @Test fun `una url no permitida ni se pide`() {
        var pedidas = 0
        val r = DescargaMas18(prefs) { pedidas++; ejemplo }.pagina(ConfigMas18("https://otro.com/p/"), 1)
        assertEquals(0, pedidas)
        assertTrue(r.lectura is LecturaMas18.Mal)
    }
}
