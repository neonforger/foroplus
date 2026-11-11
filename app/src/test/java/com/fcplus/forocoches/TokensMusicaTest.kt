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
class TokensMusicaTest {
    private lateinit var tokens: TokensMusica

    @Before fun preparar() {
        val prefs = ApplicationProvider.getApplicationContext<Context>()
            .getSharedPreferences("test_tokens_musica", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        tokens = TokensMusica(prefs)
    }

    @Test fun guardaLeeYBorraPorCuenta() {
        assertNull(tokens.de("111"))
        tokens.guardar("111", "tokA")
        tokens.guardar("222", "tokB")
        assertEquals("tokA", tokens.de("111"))
        assertEquals("tokB", tokens.de("222"))
        tokens.borrar("111")
        assertNull(tokens.de("111"))
        assertEquals("tokB", tokens.de("222"))
    }

    @Test fun usaLaClavePorCuenta() {
        val prefs = ApplicationProvider.getApplicationContext<Context>()
            .getSharedPreferences("test_tokens_musica", Context.MODE_PRIVATE)
        tokens.guardar("111", "tokA")
        assertEquals("tokA", prefs.getString(ClavesPorCuenta.clave("musica_token", "111"), null))
    }

    @Test fun sinUidNoSeGuardaNiSeLeeNada() {
        tokens.guardar("", "huerfano")
        assertNull(tokens.de(""))
        assertNull(tokens.de("111"))
    }

    /**
     * El puente (gotcha "declarar que un dato va por cuenta no lo hace ir por cuenta"): el
     * ApiMusica se construye UNA vez y la cuenta activa cambia después; el token que sale es el
     * de la cuenta de cada momento.
     */
    @Test fun tokenDeLaCuentaActiva() {
        tokens.guardar("111", "token-de-111")
        tokens.guardar("222", "token-de-222")
        var uidActivo = "111"
        val cabeceras = mutableListOf<Map<String, String>>()
        val api = ApiMusica(
            token = { tokens.de(uidActivo) },
            red = { _, _, _, h -> cabeceras.add(h); Respuesta(200, """{"nombre":"YoMismo","diasCuenta":162,"puedePublicar":true,"publicacionesRestantes":6}""") }
        )
        api.yo()
        uidActivo = "222"
        api.yo()
        assertEquals("Bearer token-de-111", cabeceras[0]["Authorization"])
        assertEquals("Bearer token-de-222", cabeceras[1]["Authorization"])
        uidActivo = "333" // sin token: no se manda ninguno
        api.yo()
        assertNull(cabeceras[2]["Authorization"])
    }

    @Test fun baneadaSeRecuerdaPorCuentaYSeOlvidaAlVerificarseDeNuevo() {
        assertEquals(false, tokens.baneada("111"))
        tokens.marcarBaneada("111")
        assertEquals(true, tokens.baneada("111"))
        assertEquals(false, tokens.baneada("222"))
        tokens.borrar("111")
        assertEquals(true, tokens.baneada("111"))
        tokens.guardar("111", "nuevo")
        assertEquals(false, tokens.baneada("111"))
        // Sin cuenta no se marca nada.
        tokens.marcarBaneada("")
        assertEquals(false, tokens.baneada(""))
    }

    @Test fun trasErrorTiraElTokenMuertoYDeUnaBaneadaSoloRecuerdaElBaneo() {
        tokens.guardar("111", "t"); tokens.guardar("222", "t"); tokens.guardar("333", "t")
        tokens.trasError("111", ApiMusica.R.Error("baneada", 403, null))
        assertEquals("t", tokens.de("111")); assertEquals(true, tokens.baneada("111"))  // baneada: el token se queda para poder borrar la cuenta
        tokens.trasError("222", ApiMusica.R.Error("token", 401, null))
        assertNull(tokens.de("222")); assertEquals(false, tokens.baneada("222"))
        tokens.trasError("333", ApiMusica.R.Error("red", 0, null))
        assertEquals("t", tokens.de("333"))
    }
}
