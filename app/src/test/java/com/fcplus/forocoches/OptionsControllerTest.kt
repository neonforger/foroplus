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

/**
 * El agujero real (task-12, ARREGLO 2): `post_signature` estaba declarada "por cuenta" en
 * `ClavesPorCuenta.CLAVES` y la migración la copiaba a la clave con sufijo, pero
 * `OptionsController.signatureEnabled` seguía leyendo/escribiendo la clave PELADA — así que la
 * firma era global en vez de por cuenta pese a que todo lo demás decía que sí lo era. Había test
 * de la función pura (`ClavesPorCuenta.clave`) y de la migración, pero ninguno de que la app
 * REALMENTE leyera la clave con sufijo. Este test ejercita la función de producción tal cual la
 * llama MainActivity (con `shell_prefs`), no una reimplementación.
 *
 * Mismo motivo que [ReadThreadsRepositoryTest] para usar Robolectric: `uid_activo` hay que
 * escribirlo desde DENTRO del mismo proceso para que se note.
 */
@RunWith(RobolectricTestRunner::class)
class OptionsControllerTest {

    private lateinit var ctx: Context
    private lateinit var shellPrefs: android.content.SharedPreferences

    @Before
    fun setUp() {
        ctx = ApplicationProvider.getApplicationContext()
        shellPrefs = ctx.getSharedPreferences("shell_prefs", Context.MODE_PRIVATE)
        shellPrefs.edit().clear().apply()
    }

    private fun cambiarCuenta(uid: String?) {
        val editor = shellPrefs.edit()
        if (uid == null) editor.remove("uid_activo") else editor.putString("uid_activo", uid)
        editor.apply()
    }

    /** Lo que escribiría OptionsController al tocar el interruptor (mismo camino de producción:
     *  `ClavesPorCuenta.clave` con el uid que haya AHORA MISMO en shell_prefs). */
    private fun encenderFirma(encendida: Boolean) {
        val uid = shellPrefs.getString("uid_activo", "") ?: ""
        shellPrefs.edit()
            .putBoolean(ClavesPorCuenta.clave(OptionsController.PREF_SIGNATURE, uid), encendida)
            .apply()
    }

    @Test
    fun `sin nada guardado la firma esta apagada por defecto`() {
        cambiarCuenta("111")
        assertFalse(OptionsController.signatureEnabled(shellPrefs))
    }

    @Test
    fun `encender la firma en una cuenta no la enciende en otra`() {
        cambiarCuenta("111")
        encenderFirma(true)
        assertTrue(OptionsController.signatureEnabled(shellPrefs))

        cambiarCuenta("222")
        assertFalse(
            "la firma de la cuenta 111 no debe verse activa bajo la 222",
            OptionsController.signatureEnabled(shellPrefs)
        )
    }

    @Test
    fun `volver a la cuenta anterior recupera su ajuste de firma`() {
        cambiarCuenta("111")
        encenderFirma(true)

        cambiarCuenta("222")
        encenderFirma(false) // explícito: en 222 se deja apagada a propósito

        cambiarCuenta("111")
        assertTrue(
            "la firma de 111 debe seguir encendida, sin mezcla con el ajuste de 222",
            OptionsController.signatureEnabled(shellPrefs)
        )
    }

    @Test
    fun `sin sesion se usa la clave sin sufijo, igual que sin cuenta`() {
        cambiarCuenta(null)
        encenderFirma(true)
        assertTrue(OptionsController.signatureEnabled(shellPrefs))
        assertEquals(
            "debe quedar en la clave pelada, no en una con sufijo vacío",
            true,
            shellPrefs.getBoolean("post_signature", false)
        )
    }

    @Test
    fun `el tamaño de los titulos va aparte del de los mensajes`() {
        assertEquals(15f, OptionsController.tituloSp(shellPrefs))
        shellPrefs.edit().putInt(OptionsController.PREF_FONT_IDX, 2).commit()
        assertEquals("cambiar la letra de los mensajes no toca los títulos",
            15f, OptionsController.tituloSp(shellPrefs))
        shellPrefs.edit().putInt(OptionsController.PREF_TITLE_FONT_IDX, 0).commit()
        assertEquals(13f, OptionsController.tituloSp(shellPrefs))
        shellPrefs.edit().putInt(OptionsController.PREF_TITLE_FONT_IDX, 2).commit()
        assertEquals(18f, OptionsController.tituloSp(shellPrefs))
    }

    @Test
    fun `la seccion +18 y ocultar en listas vienen APAGADAS`() {
        val prefs = ApplicationProvider.getApplicationContext<Context>()
            .getSharedPreferences("t_opts_mas18", Context.MODE_PRIVATE).also { it.edit().clear().commit() }
        assertFalse(OptionsController.mas18Activa(prefs))
        assertFalse(OptionsController.ocultarMas18(prefs))
        prefs.edit().putBoolean(OptionsController.PREF_MAS18, true).commit()
        assertTrue(OptionsController.mas18Activa(prefs))
    }
}
