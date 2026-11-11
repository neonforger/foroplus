package com.fcplus.forocoches

import android.content.Context
import android.view.View
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner

/**
 * El panel de Música es SOLO el feed (2026-11-11): al entrar se ve el feed y el botón de compartir,
 * sin playlists, sin "Lo tuyo" y sin "+"; y con el servidor apagado no hay nada que enseñar.
 */
@RunWith(RobolectricTestRunner::class)
class MusicaControllerTest {

    private fun actividad(): AppCompatActivity {
        val a = Robolectric.buildActivity(AppCompatActivity::class.java).get()
        a.setTheme(R.style.Theme_FCForocoches)
        a.setContentView(FrameLayout(a))
        return a
    }

    /** El panel como lo mete activity_main.xml: inflado dentro del FrameLayout de la lista. */
    private fun panel(a: AppCompatActivity): View {
        val contenedor = a.findViewById<FrameLayout>(android.R.id.content).getChildAt(0) as FrameLayout
        return a.layoutInflater.inflate(R.layout.panel_musica, contenedor, true)
            .findViewById(R.id.panel_musica)
    }

    private fun enganches(servidor: Boolean, uid: String = "123456", tokens: TokensMusica? = null): EnganchesMusica {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        return EnganchesMusica(
            servidorActivo = { servidor }, uidActivo = { uid }, cambiandoDeCuenta = { false },
            motorCoche = MotorCocheFC({ }),
            tokens = lazy { tokens ?: TokensMusica(ctx.getSharedPreferences("t_mc_tokens", Context.MODE_PRIVATE)) },
            pendientes = PendientesCoche(ctx.getSharedPreferences("t_mc_pend", Context.MODE_PRIVATE)), entrar = { }
        )
    }

    private val sinRed = ApiMusica({ null }, red = { _, _, _, _ -> throw java.io.IOException("sin red") })

    @Test
    fun `con el servidor encendido el panel es el feed con su boton de compartir`() {
        val a = actividad()
        val raiz = panel(a)
        val musica = MusicaController(a, raiz, enganches(true), { sinRed }) {}
        assertFalse(musica.visible)
        assertEquals(View.GONE, raiz.visibility)

        musica.mostrar()

        assertTrue(musica.visible)
        assertEquals(View.VISIBLE, raiz.visibility)
        assertEquals(View.VISIBLE, raiz.findViewById<View>(R.id.musica_feed_panel).visibility)
        assertEquals(View.VISIBLE, raiz.findViewById<View>(R.id.musica_compartir).visibility)
    }

    @Test
    fun `con el servidor apagado el feed no se enseña ni pide nada`() {
        val a = actividad()
        val raiz = panel(a)
        val musica = MusicaController(a, raiz, enganches(false), { sinRed }) {}

        musica.mostrar()

        assertEquals(View.GONE, raiz.findViewById<View>(R.id.musica_feed_panel).visibility)
        assertEquals(View.GONE, raiz.findViewById<View>(R.id.musica_compartir).visibility)
    }

    @Test
    fun `ocultar quita el panel y el feed`() {
        val a = actividad()
        val raiz = panel(a)
        val musica = MusicaController(a, raiz, enganches(true), { sinRed }) {}
        musica.mostrar()
        musica.ocultar()
        assertFalse(musica.visible)
        assertEquals(View.GONE, raiz.visibility)
        assertEquals(View.GONE, raiz.findViewById<View>(R.id.musica_feed_panel).visibility)
        assertEquals(View.GONE, raiz.findViewById<View>(R.id.musica_compartir).visibility)
    }

    @Test
    fun `sin reproductor sonando deja deslizar desde cualquier sitio`() {
        val a = actividad()
        val musica = MusicaController(a, panel(a), enganches(true), { sinRed }) {}
        musica.mostrar()
        assertTrue(musica.dejaDeslizarDesde(10, 10))
    }

    // ── "Borrar mi cuenta de Música" en Opciones: solo con token ─────────────

    private fun ctrlSincrono(a: AppCompatActivity, e: EnganchesMusica): MusicaController {
        val m = MusicaController(a, panel(a), e, { sinRed }) {}
        m.hiloToken = { it() }  // el token se mira en línea: sin esperas
        return m
    }

    private fun tieneCuenta(m: MusicaController): Boolean? {
        var r: Boolean? = null
        m.tieneCuenta { r = it }
        org.robolectric.shadows.ShadowLooper.idleMainLooper()
        return r
    }

    @Test
    fun `tieneCuenta es si solo con token de la cuenta activa`() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        ctx.getSharedPreferences("t_mc_tok2", Context.MODE_PRIVATE).edit().clear().commit()
        val tokens = TokensMusica(ctx.getSharedPreferences("t_mc_tok2", Context.MODE_PRIVATE))
        val a = actividad()
        assertEquals(false, tieneCuenta(ctrlSincrono(a, enganches(true, "123456", tokens))))
        tokens.guardar("123456", "tok")
        assertEquals(true, tieneCuenta(ctrlSincrono(a, enganches(true, "123456", tokens))))
        // El token es de OTRA cuenta: con esta activa, no.
        assertEquals(false, tieneCuenta(ctrlSincrono(a, enganches(true, "999999", tokens))))
        // Sin sesión (uid vacío): no.
        assertEquals(false, tieneCuenta(ctrlSincrono(a, enganches(true, "", tokens))))
    }

    @Test
    fun `tieneCuenta con el servidor apagado es no aunque haya token`() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        ctx.getSharedPreferences("t_mc_tok3", Context.MODE_PRIVATE).edit().clear().commit()
        val tokens = TokensMusica(ctx.getSharedPreferences("t_mc_tok3", Context.MODE_PRIVATE))
        tokens.guardar("123456", "tok")
        assertEquals(false, tieneCuenta(ctrlSincrono(actividad(), enganches(false, "123456", tokens))))
    }
}
