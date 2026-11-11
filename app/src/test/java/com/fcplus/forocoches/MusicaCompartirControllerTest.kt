package com.fcplus.forocoches

import android.app.Dialog
import android.content.Context
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.test.core.app.ApplicationProvider
import com.google.android.material.bottomsheet.BottomSheetDialog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadows.ShadowDialog

/** Cómo abre MusicaController la pantalla de compartir (A6): una sola, con token, y por dónde entra lo compartido. */
@RunWith(RobolectricTestRunner::class)
class MusicaCompartirControllerTest {
    @get:Rule val carpeta = TemporaryFolder()

    private lateinit var act: AppCompatActivity
    private lateinit var raiz: View
    private lateinit var tokens: TokensMusica
    private lateinit var enganches: EnganchesMusica
    private var servidor = true
    private val enCola = ArrayList<() -> Unit>()
    private val enlace = "https://open.spotify.com/track/4uLU6hMCjMI75M1A2tKUQC"

    @Before fun preparar() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        for (f in listOf("m_tokens", "m_pend")) ctx.getSharedPreferences(f, Context.MODE_PRIVATE).edit().clear().commit()
        tokens = TokensMusica(ctx.getSharedPreferences("m_tokens", Context.MODE_PRIVATE))
        enganches = EnganchesMusica(
            servidorActivo = { servidor }, uidActivo = { "123456" }, cambiandoDeCuenta = { false },
            motorCoche = MotorCocheFC({ }), tokens = lazy { tokens },
            pendientes = PendientesCoche(ctx.getSharedPreferences("m_pend", Context.MODE_PRIVATE)), entrar = { }
        )
        act = Robolectric.buildActivity(AppCompatActivity::class.java).get()
        act.setTheme(R.style.Theme_FCForocoches)
        act.setContentView(FrameLayout(act))
        val cont = act.findViewById<FrameLayout>(android.R.id.content).getChildAt(0) as FrameLayout
        raiz = act.layoutInflater.inflate(R.layout.panel_musica, cont, true).findViewById(R.id.panel_musica)
    }

    private fun controlador(): MusicaController {
        val m = MusicaController(
            act, raiz, AlmacenMusica(carpeta.root), enganches,
            { ApiMusica({ null }, red = { _, _, _, _ -> Respuesta(404, "{}") }) }
        ) {}
        m.hiloToken = { enCola.add(it) }  // mirar el token queda en cola: cada test decide cuándo corre
        return m
    }

    private fun correrCola() { val c = ArrayList(enCola); enCola.clear(); c.forEach { it() } }

    private fun mostrados(): List<Dialog> = ShadowDialog.getShownDialogs().filter { it.isShowing }
    private fun hojasCompartir() = mostrados().filter { it.findViewById<View>(R.id.compartir_buscar) != null }
    private fun hojasVerificar() = mostrados().filter { it is BottomSheetDialog && it.findViewById<View>(R.id.verificar_boton) != null }

    @Test fun `dos abrirCompartir seguidos abren una sola pantalla`() {
        tokens.guardar("123456", "tok")
        val m = controlador()
        m.abrirCompartir(null)
        m.abrirCompartir(null)   // antes de que conteste el hilo del token
        assertEquals("el segundo no encola nada", 1, enCola.size)
        correrCola()
        m.abrirCompartir(null)   // con la pantalla ya abierta
        assertTrue(enCola.isEmpty())
        correrCola()
        assertEquals(1, hojasCompartir().size)
    }

    @Test fun `sin token abre la verificacion y no la pantalla`() {
        val m = controlador()
        m.abrirCompartir(null)
        correrCola()
        assertEquals(1, hojasVerificar().size)
        assertTrue(hojasCompartir().isEmpty())
        assertNull(m.hojaCompartir)
    }

    @Test fun `compartido con feed y una cancion abre la pantalla con el enlace puesto`() {
        tokens.guardar("123456", "tok")
        val m = controlador()
        m.recibirCompartido("escucha esto $enlace")
        correrCola()
        val h = hojasCompartir().single()
        assertEquals(enlace, h.findViewById<EditText>(R.id.compartir_buscar).text.toString())
    }

    @Test fun `compartido con el servidor apagado sigue por la hoja de anadir`() {
        servidor = false
        val m = controlador()
        m.recibirCompartido("escucha esto $enlace")
        correrCola()
        assertTrue(hojasCompartir().isEmpty())
        assertTrue(enCola.isEmpty())
        assertEquals(1, mostrados().filterIsInstance<BottomSheetDialog>().size)
    }
}
