package com.fcplus.forocoches

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner

/**
 * La hoja de compartir una canción (A6), con la red, el hilo de fondo y la espera de 300 ms
 * sustituidos: lo que se pone en cola lo ejecuta cada test cuando quiere (así se prueba qué pasa
 * si una respuesta llega tarde).
 */
@RunWith(RobolectricTestRunner::class)
class CompartirCancionTest {
    private lateinit var act: AppCompatActivity
    private lateinit var enganches: EnganchesMusica
    private lateinit var tokens: TokensMusica

    private val esperas = ArrayList<Pair<Long, () -> Unit>>()
    private val fondo = ArrayList<() -> Unit>()
    private val busquedas = ArrayList<String>()
    private val peticiones = ArrayList<Triple<String, String, String?>>()
    private val publicadas = ArrayList<ItemFeed>()
    private var responder: (String, String) -> Respuesta = { _, _ -> Respuesta(404, "{}") }
    private var resultadosDe: (String) -> List<ResultadoDeezer> = { emptyList() }

    private val enlaceSpotify = "https://open.spotify.com/track/4uLU6hMCjMI75M1A2tKUQC"

    private fun res(id: Long, titulo: String) = ResultadoDeezer(id, titulo, "Alguien", null, 200)

    private fun publicacion() =
        """{"publicacion":{"id":"P1","autor":"YoMismo","texto":"frase","creada":1000,"caduca":90000000,"corazones":0,"corazon":false,"mia":true,""" +
            """"cancion":{"id":"c1","titulo":"Tema","artista":"Alguien","portada":null,"youtube":null,"spotify":null,"deezer":7}}}"""

    @Before fun preparar() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        for (f in listOf("c_tokens", "c_pend")) ctx.getSharedPreferences(f, Context.MODE_PRIVATE).edit().clear().commit()
        tokens = TokensMusica(ctx.getSharedPreferences("c_tokens", Context.MODE_PRIVATE))
        tokens.guardar("123456", "tok")
        enganches = EnganchesMusica(
            servidorActivo = { true }, uidActivo = { "123456" }, cambiandoDeCuenta = { false },
            motorCoche = MotorCocheFC({ }), tokens = lazy { tokens },
            pendientes = PendientesCoche(ctx.getSharedPreferences("c_pend", Context.MODE_PRIVATE)), entrar = { }
        )
        act = Robolectric.buildActivity(AppCompatActivity::class.java).get()
        act.setTheme(R.style.Theme_FCForocoches)
        act.setContentView(FrameLayout(act))
        responder = { m, _ -> if (m == "POST") Respuesta(200, publicacion()) else Respuesta(404, "{}") }
    }

    private fun hoja(): HojaCompartirCancion {
        val api = ApiMusica({ "tok" }, red = { m, u, c, _ -> peticiones.add(Triple(m, u, c)); responder(m, u) })
        return HojaCompartirCancion(
            act, enganches, { publicadas.add(it) },
            api = { api },
            buscar = { busquedas.add(it); resultadosDe(it) },
            segundoPlano = { fondo.add(it) },
            esperar = { ms, r -> esperas.add(ms to r) }
        )
    }

    private fun HojaCompartirCancion.escribir(texto: String) {
        vista!!.findViewById<EditText>(R.id.compartir_buscar).setText(texto)
    }

    /** Pasa los 300 ms de la última tecla (las esperas viejas también saltan: no deben hacer nada). */
    private fun pasarEspera() { val e = ArrayList(esperas); esperas.clear(); e.forEach { it.second() } }

    private fun correrFondo() { val f = ArrayList(fondo); fondo.clear(); f.forEach { it() } }

    private fun HojaCompartirCancion.boton() = vista!!.findViewById<TextView>(R.id.compartir_boton)
    private fun HojaCompartirCancion.motivo() = vista!!.findViewById<EditText>(R.id.compartir_motivo)
    private fun HojaCompartirCancion.filas() = vista!!.findViewById<LinearLayout>(R.id.compartir_lista)

    private fun textos(v: View): String {
        val salida = ArrayList<String>()
        fun rec(x: View) {
            if (x is TextView) salida.add(x.text.toString())
            if (x is ViewGroup) for (i in 0 until x.childCount) rec(x.getChildAt(i))
        }
        rec(v)
        return salida.joinToString(" | ")
    }

    @Test fun `escribir un enlace de spotify no busca y usa Enlace`() {
        val h = hoja(); h.mostrar()
        h.escribir(enlaceSpotify)
        pasarEspera(); correrFondo()
        assertTrue("un enlace no se busca", busquedas.isEmpty())
        assertEquals(View.VISIBLE, h.vista!!.findViewById<View>(R.id.compartir_panel).visibility)
        h.motivo().setText("Me encanta")
        assertTrue(h.boton().isEnabled)
        h.boton().performClick(); correrFondo()
        val cuerpo = peticiones.single { it.first == "POST" }.third!!
        assertTrue(cuerpo, cuerpo.contains("\"enlace\"") && cuerpo.contains("4uLU6hMCjMI75M1A2tKUQC"))
        assertFalse(cuerpo.contains("deezer"))
        assertEquals(1, publicadas.size)
    }

    @Test fun `una busqueda espera 300 ms y se recorta a 100 caracteres`() {
        val h = hoja(); h.mostrar()
        resultadosDe = { listOf(res(7, "Tema")) }
        h.escribir("x".repeat(150))
        assertTrue("antes de la espera no se busca", busquedas.isEmpty() && fondo.isEmpty())
        assertEquals(HojaCompartirCancion.ESPERA_MS, esperas.last().first)
        pasarEspera(); correrFondo()
        assertEquals(100, busquedas.single().length)
        assertEquals(1, h.filas().childCount)
    }

    @Test fun `respuesta vieja de busqueda se descarta`() {
        val h = hoja(); h.mostrar()
        resultadosDe = { q -> listOf(res(if (q == "abc") 1 else 2, "Para $q")) }
        h.escribir("abc"); pasarEspera()
        val primera = ArrayList(fondo); fondo.clear()
        h.escribir("abcd"); pasarEspera()
        val segunda = ArrayList(fondo); fondo.clear()
        segunda.forEach { it() }      // la nueva llega primero...
        primera.forEach { it() }      // ...y la vieja, tarde: no puede pisarla
        assertEquals(1, h.filas().childCount)
        val texto = textos(h.filas())
        assertTrue(texto, texto.contains("Para abcd"))
        assertFalse(texto, texto.contains("Para abc "))
    }

    @Test fun `texto de 101 deshabilita compartir`() {
        val h = hoja(); h.mostrar()
        resultadosDe = { listOf(res(7, "Tema")) }
        h.escribir("tema"); pasarEspera(); correrFondo()
        h.filas().getChildAt(0).performClick()
        h.motivo().setText("a".repeat(100))
        assertTrue(h.boton().isEnabled)
        h.motivo().setText("a".repeat(101))
        assertFalse(h.boton().isEnabled)
        assertEquals("101/100", h.vista!!.findViewById<TextView>(R.id.compartir_contador).text.toString())
        h.motivo().setText("")
        assertFalse("sin frase tampoco", h.boton().isEnabled)
        h.motivo().setText("mira http://x.com")
        assertFalse("con enlace tampoco", h.boton().isEnabled)
    }

    @Test fun `error 429 limite muestra el aviso y reactiva el boton`() {
        responder = { _, _ -> Respuesta(429, """{"error":"limite"}""") }
        val h = hoja(); h.mostrar()
        resultadosDe = { listOf(res(7, "Tema")) }
        h.escribir("tema"); pasarEspera(); correrFondo()
        h.filas().getChildAt(0).performClick()
        h.motivo().setText("buena")
        h.boton().performClick()
        assertEquals("Publicando…", h.boton().text.toString())
        assertFalse("mientras publica no se puede volver a pulsar", h.boton().isEnabled)
        correrFondo()
        val error = h.vista!!.findViewById<TextView>(R.id.compartir_error)
        assertEquals(View.VISIBLE, error.visibility)
        assertEquals("Por hoy ya has compartido muchas canciones; mañana puedes seguir.", error.text.toString())
        assertEquals("Compartir", h.boton().text.toString())
        assertTrue(h.boton().isEnabled)
        assertTrue(publicadas.isEmpty())
        assertFalse("el aviso no lleva cifras", error.text.contains(Regex("[0-9]")))
    }

    @Test fun `publicar bien avisa y cierra la hoja`() {
        val h = hoja(); h.mostrar()
        resultadosDe = { listOf(res(7, "Tema")) }
        h.escribir("tema"); pasarEspera(); correrFondo()
        h.filas().getChildAt(0).performClick()
        h.motivo().setText("  buena  ")
        h.boton().performClick(); correrFondo()
        val p = peticiones.single { it.first == "POST" }
        assertTrue(p.third!!, p.third!!.contains("\"deezer\":7") && p.third!!.contains("\"texto\":\"buena\""))
        assertEquals(listOf("P1"), publicadas.map { it.id })
        assertFalse(h.abierta)
    }

    @Test fun `un enlace inicial llega puesto y no se busca`() {
        val h = hoja(); h.mostrar(enlaceSpotify)
        assertEquals(enlaceSpotify, h.vista!!.findViewById<EditText>(R.id.compartir_buscar).text.toString())
        assertEquals(View.VISIBLE, h.vista!!.findViewById<View>(R.id.compartir_panel).visibility)
        pasarEspera(); correrFondo()
        assertTrue(busquedas.isEmpty())
    }

    @Test fun `enlaceDe saca el enlace de una frase y no confunde una busqueda`() {
        assertEquals(enlaceSpotify, HojaCompartirCancion.enlaceDe("mira esto $enlaceSpotify que mola"))
        assertEquals(null, HojaCompartirCancion.enlaceDe("despacito luis fonsi"))
    }

    @Test fun `si la publicacion falla por red el boton vuelve con el texto de red`() {
        responder = { _, _ -> throw java.io.IOException("sin red") }
        val h = hoja(); h.mostrar(enlaceSpotify)
        h.motivo().setText("buena")
        h.boton().performClick()
        assertEquals("Publicando…", h.boton().text.toString())
        correrFondo()
        val error = h.vista!!.findViewById<TextView>(R.id.compartir_error)
        assertEquals("Sin conexión. Comprueba tu red e inténtalo de nuevo.", error.text.toString())
        assertEquals("Compartir", h.boton().text.toString())
        assertTrue(h.boton().isEnabled)
        assertTrue(h.abierta)
    }

    @Test fun `un fallo inesperado al publicar tampoco deja el boton en Publicando`() {
        val h = HojaCompartirCancion(
            act, enganches, { publicadas.add(it) },
            api = { throw IllegalStateException("roto") },
            segundoPlano = { fondo.add(it) }, esperar = { ms, r -> esperas.add(ms to r) }
        )
        h.mostrar(enlaceSpotify)
        h.motivo().setText("buena")
        h.boton().performClick(); correrFondo()
        assertEquals("Compartir", h.boton().text.toString())
        assertTrue(h.boton().isEnabled)
    }

    @Test fun `el servidor devuelve la publicacion aun sin convertir y la hoja cierra`() {
        // La conversión la espera el servidor (8 s) y contesta igualmente: la app solo cierra y avisa al feed.
        val h = hoja(); h.mostrar(enlaceSpotify)
        h.motivo().setText("buena")
        h.boton().performClick(); correrFondo()
        assertEquals(1, publicadas.size)
        assertFalse(h.abierta)
    }

    @Test fun `elegir otra cancion con un enlace en la frase sigue explicando por que no se puede`() {
        val h = hoja(); h.mostrar()
        resultadosDe = { listOf(res(7, "Tema")) }
        h.escribir("tema"); pasarEspera(); correrFondo()
        h.motivo().setText("mira http://x.com")
        h.filas().getChildAt(0).performClick()
        val error = h.vista!!.findViewById<TextView>(R.id.compartir_error)
        assertEquals(View.VISIBLE, error.visibility)
        assertFalse(h.boton().isEnabled)
    }

    @Test fun `sin resultados sale el texto de que no hay nada`() {
        val h = hoja(); h.mostrar()
        h.escribir("nadaquebuscar"); pasarEspera(); correrFondo()
        val vacio = h.vista!!.findViewById<TextView>(R.id.compartir_vacio)
        assertEquals(View.VISIBLE, vacio.visibility)
        assertTrue(vacio.text.isNotEmpty())
    }
}
