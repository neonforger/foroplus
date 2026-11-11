package com.fcplus.forocoches

import android.content.Context
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner

/** El feed de la portada de Música, con una red falsa y los hilos en línea (sin esperas). */
@RunWith(RobolectricTestRunner::class)
class FeedControllerTest {
    @get:Rule val carpeta = TemporaryFolder()

    private val llamadas = ArrayList<String>()
    private var responder: (String, String) -> Respuesta = { _, _ -> Respuesta(404, "{}") }
    private var ahora = 1_000_000L
    private var verificaciones = 0
    private var antiguedad = false
    private lateinit var act: AppCompatActivity
    private lateinit var raiz: View
    private lateinit var tokens: TokensMusica
    private lateinit var bloqueados: BloqueadosMusica
    private lateinit var almacen: AlmacenMusica
    private lateinit var enganches: EnganchesMusica
    private var servidor = true

    private fun cancion(id: String) =
        """{"id":"c$id","titulo":"Tema $id","artista":"Alguien","portada":null,"youtube":"https://www.youtube.com/watch?v=dQw4w9WgXc$id","spotify":null,"deezer":null}"""

    private fun item(id: String, autor: String, corazones: Int = 3, corazon: Boolean = false, mia: Boolean = false) =
        """{"id":"$id","autor":"$autor","texto":"frase","creada":${ahora - 60_000},"caduca":${ahora + 3_600_000 * 5},"corazones":$corazones,"corazon":$corazon,"mia":$mia,"cancion":${cancion(id.takeLast(1))}}"""

    private fun feed(vararg items: String) = Respuesta(200, """{"items":[${items.joinToString(",")}],"cursor":null}""")

    @Before fun preparar() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        for (f in listOf("t_tokens", "t_pend", "t_bloq")) ctx.getSharedPreferences(f, Context.MODE_PRIVATE).edit().clear().commit()
        tokens = TokensMusica(ctx.getSharedPreferences("t_tokens", Context.MODE_PRIVATE))
        bloqueados = BloqueadosMusica(ctx.getSharedPreferences("t_bloq", Context.MODE_PRIVATE))
        almacen = AlmacenMusica(carpeta.root)
        enganches = EnganchesMusica(
            servidorActivo = { servidor }, uidActivo = { "123456" }, cambiandoDeCuenta = { false },
            motorCoche = MotorCocheFC({ }), tokens = lazy { tokens },
            pendientes = PendientesCoche(ctx.getSharedPreferences("t_pend", Context.MODE_PRIVATE)), entrar = { }
        )
        act = Robolectric.buildActivity(AppCompatActivity::class.java).get()
        act.setTheme(R.style.Theme_FCForocoches)
        act.setContentView(FrameLayout(act))
        val cont = act.findViewById<FrameLayout>(android.R.id.content).getChildAt(0) as FrameLayout
        raiz = act.layoutInflater.inflate(R.layout.panel_musica, cont, true).findViewById(R.id.panel_musica)
        responder = { _, u ->
            when {
                u.contains("/v1/feed") -> feed(item("P1", "Pepe"), item("P2", "Ana"))
                u.endsWith("/v1/yo") -> Respuesta(200, """{"nombre":"YoMismo","diasCuenta":${if (antiguedad) 3 else 200},"puedePublicar":${!antiguedad},"publicacionesRestantes":5}""")
                else -> Respuesta(404, "{}")
            }
        }
    }

    private fun controlador(): FeedController {
        val api = ApiMusica({ tokens.de("123456") }, red = { m, u, _, _ -> llamadas.add("$m $u"); responder(m, u) })
        return FeedController(
            act, raiz, enganches, almacen, ReproductorFeed(act, {}),
            abrirVerificacion = { verificaciones++ }, abrirCompartir = {}, abrirLoTuyo = {},
            bloqueados = bloqueados, api = { api }, segundoPlano = { it() }, reloj = { ahora }, disco = { it() }
        )
    }

    private val lista get() = raiz.findViewById<RecyclerView>(R.id.musica_feed)
    private fun filas(): Int = lista.adapter!!.itemCount

    /** El desplazamiento se para: avisa a los oyentes como lo haría la lista (el método es privado). */
    private fun parado() {
        val m = RecyclerView::class.java.getDeclaredMethod("dispatchOnScrollStateChanged", Int::class.javaPrimitiveType)
        m.isAccessible = true
        m.invoke(lista, RecyclerView.SCROLL_STATE_IDLE)
    }

    /** Mide la lista para que haya filas de verdad y se puedan tocar sus botones. */
    private fun fila(i: Int): View {
        lista.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(4000, View.MeasureSpec.EXACTLY))
        lista.layout(0, 0, 1080, 4000)
        return lista.getChildAt(i)
    }

    @Test fun `carga el feed al mostrar`() {
        val f = controlador()
        f.mostrar()
        assertEquals(1, llamadas.count { it.contains("/v1/feed?orden=recientes") })
        assertEquals(2, filas())
        assertEquals(View.VISIBLE, raiz.findViewById<View>(R.id.musica_feed_panel).visibility)
    }

    @Test fun `chip queridas pide orden queridas`() {
        val f = controlador()
        f.mostrar()
        raiz.findViewById<View>(R.id.musica_chip_queridas).performClick()
        assertTrue(llamadas.last(), llamadas.last().contains("orden=queridas"))
        assertTrue(raiz.findViewById<View>(R.id.musica_chip_queridas).isSelected)
        assertFalse(raiz.findViewById<View>(R.id.musica_chip_recientes).isSelected)
    }

    @Test fun `no recarga antes de 30 s`() {
        val f = controlador()
        f.mostrar()
        ahora += 10_000
        f.refrescar(false)
        f.mostrar()
        assertEquals(1, llamadas.count { it.contains("/v1/feed") })
        ahora += 21_000
        f.refrescar(false)
        assertEquals(2, llamadas.count { it.contains("/v1/feed") })
        f.refrescar(true)
        assertEquals(3, llamadas.count { it.contains("/v1/feed") })
    }

    @Test fun `corazon sin token abre verificacion`() {
        val f = controlador()
        f.mostrar()
        fila(0).findViewById<View>(R.id.pub_corazon_ico).performClick()
        assertEquals(1, verificaciones)
        assertTrue(llamadas.none { it.contains("corazon") })
    }

    @Test fun `corazon de cuenta nueva avisa de antiguedad y no llama`() {
        tokens.guardar("123456", "tok"); antiguedad = true
        val f = controlador()
        f.mostrar()
        fila(0).findViewById<View>(R.id.pub_corazon_ico).performClick()
        assertEquals(0, verificaciones)
        assertTrue(llamadas.none { it.contains("corazon") })
        // y la segunda vez no vuelve a preguntar a /yo (cache por cuenta)
        fila(0).findViewById<View>(R.id.pub_corazon_ico).performClick()
        assertEquals(1, llamadas.count { it.endsWith("/v1/yo") })
    }

    @Test fun `corazon optimista y revertido si falla`() {
        tokens.guardar("123456", "tok")
        val f = controlador()
        f.mostrar()
        var visto = ""
        val base = responder
        responder = { m, u ->
            if (u.endsWith("/corazon")) {
                visto = fila(0).findViewById<TextView>(R.id.pub_corazones).text.toString()
                Respuesta(500, """{"error":"x"}""")
            } else base(m, u)
        }
        fila(0).findViewById<View>(R.id.pub_corazon_ico).performClick()
        assertEquals("4", visto)  // ya pintado ANTES de que conteste el servidor
        assertEquals("3", fila(0).findViewById<TextView>(R.id.pub_corazones).text.toString())
    }

    @Test fun `corazon bueno se queda con lo que dice el servidor`() {
        tokens.guardar("123456", "tok")
        val f = controlador()
        f.mostrar()
        val base = responder
        responder = { m, u -> if (u.endsWith("/corazon")) Respuesta(200, """{"corazones":9,"corazon":true}""") else base(m, u) }
        fila(0).findViewById<View>(R.id.pub_corazon_ico).performClick()
        assertEquals("9", fila(0).findViewById<TextView>(R.id.pub_corazones).text.toString())
    }

    @Test fun `al recibir no_existe se quita la fila`() {
        tokens.guardar("123456", "tok")
        val f = controlador()
        f.mostrar()
        val base = responder
        responder = { m, u -> if (u.endsWith("/corazon")) Respuesta(404, """{"error":"no_existe"}""") else base(m, u) }
        fila(0).findViewById<View>(R.id.pub_corazon_ico).performClick()
        assertEquals(1, filas())
    }

    @Test fun `en segundo plano el reloj no pide nada`() {
        val f = controlador()
        f.mostrar()
        f.pausar()
        ahora += 60_000
        org.robolectric.shadows.ShadowLooper.idleMainLooper(60, java.util.concurrent.TimeUnit.SECONDS)
        f.refrescar(false)
        assertEquals(1, llamadas.count { it.contains("/v1/feed") })
        f.reanudar()
        assertEquals(2, llamadas.count { it.contains("/v1/feed") })
    }

    @Test fun `una pagina que falla se pide una sola vez hasta que pasa el tiempo`() {
        responder = { _, u ->
            if (u.contains("cursor=")) Respuesta(503, "{}")
            else Respuesta(200, """{"items":[${item("P1", "Pepe")},${item("P2", "Ana")}],"cursor":"c1"}""")
        }
        val f = controlador()
        f.mostrar()
        fila(0)
        repeat(3) { parado() }
        assertEquals(1, llamadas.count { it.contains("cursor=") })
        ahora += 6_000
        parado()
        assertEquals(2, llamadas.count { it.contains("cursor=") })
    }

    @Test fun `guardar una cancion que no se reconoce avisa`() {
        responder = { _, _ -> feed("""{"id":"P9","autor":"Ana","texto":"x","creada":1,"caduca":9999999999,"corazones":0,"cancion":{"id":"c9","titulo":"T","artista":null,"portada":null,"youtube":"https://raro.example/x","spotify":null,"deezer":null}}""") }
        val f = controlador()
        f.mostrar()
        fila(0).findViewById<View>(R.id.pub_guardar).performClick()
        assertEquals(0, almacen.estado().playlists.size)
        assertEquals(act.getString(R.string.feed_guardar_no), org.robolectric.shadows.ShadowToast.getTextOfLatestToast())
    }

    @Test fun `bloqueado no sale`() {
        bloqueados.bloquear("pepe")
        val f = controlador()
        f.mostrar()
        assertEquals(1, filas())
    }

    @Test fun `servidor apagado no pide nada a la red`() {
        servidor = false
        val f = controlador()
        f.mostrar()
        f.refrescar(true)
        assertTrue(llamadas.isEmpty())
        assertEquals(View.GONE, raiz.findViewById<View>(R.id.musica_feed_panel).visibility)
        assertEquals(View.GONE, raiz.findViewById<View>(R.id.musica_compartir).visibility)
    }

    @Test fun `guardar crea Guardadas del feed una sola vez`() {
        val f = controlador()
        f.mostrar()
        fila(0).findViewById<View>(R.id.pub_guardar).performClick()
        fila(1).findViewById<View>(R.id.pub_guardar).performClick()
        fila(1).findViewById<View>(R.id.pub_guardar).performClick()  // repetida: no duplica
        val e = almacen.estado()
        val g = e.playlists.filter { it.nombre == Musica.GUARDADAS_FEED }
        assertEquals(1, g.size)
        assertEquals(2, g[0].claves.size)
    }

    @Test fun `sin red y sin filas ensena el estado con reintento`() {
        responder = { _, _ -> throw java.io.IOException("sin red") }
        val f = controlador()
        f.mostrar()
        assertEquals(View.VISIBLE, raiz.findViewById<View>(R.id.musica_feed_estado).visibility)
        val t = raiz.findViewById<TextView>(R.id.musica_feed_estado_texto).text.toString()
        assertEquals(VistaFeed.estadoVacio(OrdenFeed.RECIENTES, false), t)
        responder = { _, _ -> feed(item("P1", "Pepe")) }
        raiz.findViewById<View>(R.id.musica_feed_reintentar).performClick()
        assertEquals(View.GONE, raiz.findViewById<View>(R.id.musica_feed_estado).visibility)
        assertEquals(1, filas())
    }
}
