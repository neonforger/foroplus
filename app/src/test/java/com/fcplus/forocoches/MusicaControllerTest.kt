package com.fcplus.forocoches

import android.content.ComponentName
import android.content.Intent
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.RecyclerView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/**
 * El panel de Música (pestaña tras el Popurrí). Lo que se fija aquí es lo que ve quien entra:
 * sin nada guardado, un texto que explica cómo llegan las canciones (pegar un enlace o
 * compartir desde Spotify/YouTube) y ninguna fila; con playlists, una fila por cada una con su
 * nombre y cuántas canciones lleva. Y que en la portada el atrás NO es suyo: lo gestiona la
 * pila de MainActivity, igual que desde el Popurrí.
 */
@RunWith(RobolectricTestRunner::class)
class MusicaControllerTest {

    @get:Rule val carpeta = TemporaryFolder()

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

    private fun cancion(n: Int) = Cancion(
        RefCancion(FuenteMusica.YOUTUBE, "id%08d".format(n)),
        "Tema $n", "Artista", null, 200, null, 0L
    )

    @Test
    fun `sin nada guardado explica como anadir y no pinta filas`() {
        val a = actividad()
        val raiz = panel(a)
        val musica = MusicaController(a, raiz, AlmacenMusica(carpeta.root)) {}
        assertFalse(musica.visible)
        assertEquals(View.GONE, raiz.visibility)

        musica.mostrar()

        assertTrue(musica.visible)
        assertEquals(View.VISIBLE, raiz.visibility)
        assertEquals(0, raiz.findViewById<LinearLayout>(R.id.musica_playlists).childCount)
        assertEquals(View.VISIBLE, raiz.findViewById<View>(R.id.musica_vacio).visibility)
        val texto = raiz.findViewById<TextView>(R.id.musica_vacio_texto).text.toString()
        assertTrue(texto, texto.contains("enlace"))
        assertTrue(texto, texto.contains("YouTube"))
        assertTrue(texto, texto.contains("Spotify"))
        assertTrue(texto, texto.contains("Guardar en Música"))
        assertFalse(musica.atras())
    }

    @Test
    fun `con dos playlists pinta dos filas con su nombre y cuantas canciones`() {
        val a = actividad()
        val raiz = panel(a)
        val almacen = AlmacenMusica(carpeta.root)
        assertTrue(almacen.guardar(cancion(1), emptyList()))          // crea "Mis favoritas"
        val coche = almacen.crearPlaylist("Para el coche")!!
        assertTrue(almacen.guardar(cancion(2), listOf(coche.id)))
        assertTrue(almacen.guardar(cancion(3), listOf(coche.id)))
        val musica = MusicaController(a, raiz, almacen) {}

        musica.mostrar()

        val filas = raiz.findViewById<LinearLayout>(R.id.musica_playlists)
        assertEquals(2, filas.childCount)
        fun nombre(i: Int) = filas.getChildAt(i).findViewById<TextView>(R.id.playlist_nombre).text.toString()
        fun cuantas(i: Int) = filas.getChildAt(i).findViewById<TextView>(R.id.playlist_cuantas).text.toString()
        assertEquals(Musica.FAVORITAS, nombre(0))
        assertEquals("1 canción", cuantas(0))
        assertEquals("Para el coche", nombre(1))
        assertEquals("2 canciones", cuantas(1))
        assertEquals(View.GONE, raiz.findViewById<View>(R.id.musica_vacio).visibility)
        assertFalse(musica.atras())
    }

    @Test
    fun `tocar una playlist abre su pantalla y el atras vuelve a la portada`() {
        val a = actividad()
        val raiz = panel(a)
        val almacen = AlmacenMusica(carpeta.root)
        assertTrue(almacen.guardar(cancion(1), emptyList()))
        val musica = MusicaController(a, raiz, almacen) {}
        musica.mostrar()
        assertEquals(View.VISIBLE, raiz.findViewById<View>(R.id.musica_portada).visibility)

        raiz.findViewById<LinearLayout>(R.id.musica_playlists).getChildAt(0).performClick()

        assertEquals(View.VISIBLE, raiz.findViewById<View>(R.id.musica_playlist).visibility)
        assertEquals(View.GONE, raiz.findViewById<View>(R.id.musica_portada).visibility)
        // El "+" es de la portada: en la playlist taparía el asa de la última canción.
        assertEquals(View.GONE, raiz.findViewById<View>(R.id.musica_anadir).visibility)

        // Ocultar y volver deja la playlist abierta.
        musica.ocultar()
        musica.mostrar()
        assertEquals(View.VISIBLE, raiz.findViewById<View>(R.id.musica_playlist).visibility)

        assertTrue(musica.atras())
        assertEquals(View.VISIBLE, raiz.findViewById<View>(R.id.musica_portada).visibility)
        assertEquals(View.GONE, raiz.findViewById<View>(R.id.musica_playlist).visibility)
        assertFalse(musica.atras())
    }

    /** La cabecera de la playlist abierta: es la fila 0 de la lista, y sin medir no existe. */
    private fun cabecera(raiz: View): View {
        val lista = raiz.findViewById<RecyclerView>(R.id.musica_canciones)
        raiz.measure(
            View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(4000, View.MeasureSpec.EXACTLY)
        )
        raiz.layout(0, 0, 1080, 4000)
        return lista.findViewHolderForAdapterPosition(0)!!.itemView
    }

    @Test
    fun `compartir una playlist abre el menu de Android con su texto y sin Guardar en Musica`() {
        val a = actividad()
        val raiz = panel(a)
        val almacen = AlmacenMusica(carpeta.root)
        val coche = almacen.crearPlaylist("Para el coche")!!
        assertTrue(almacen.guardar(cancion(1), listOf(coche.id)))
        assertTrue(almacen.guardar(cancion(2), listOf(coche.id)))
        val musica = MusicaController(a, raiz, almacen) {}
        musica.mostrar()
        raiz.findViewById<LinearLayout>(R.id.musica_playlists).getChildAt(0).performClick()

        val boton = cabecera(raiz).findViewById<View>(R.id.pl_compartir)
        assertEquals(View.VISIBLE, boton.visibility)
        assertTrue(boton.isEnabled)
        boton.performClick()

        val menu = shadowOf(a).nextStartedActivity
        assertEquals(Intent.ACTION_CHOOSER, menu.action)
        @Suppress("DEPRECATION")
        val envio = menu.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)!!
        assertEquals(Intent.ACTION_SEND, envio.action)
        assertEquals("text/plain", envio.type)
        assertEquals(
            PlaylistCompartida.texto("Para el coche", listOf(cancion(1), cancion(2))),
            envio.getStringExtra(Intent.EXTRA_TEXT)
        )
        // Compartirla con uno mismo solo la duplicaría: GuardarEnMusica volvería a esta misma
        // MainActivity (CLEAR_TOP cerraría este menú, que está encima) con la playlist otra vez.
        @Suppress("DEPRECATION")
        val fuera = menu.getParcelableArrayExtra(Intent.EXTRA_EXCLUDE_COMPONENTS)!!.map { it as ComponentName }
        assertEquals(listOf(ComponentName(a.packageName, "com.fcplus.forocoches.GuardarEnMusica")), fuera)
    }

    @Test
    fun `una playlist sin canciones no se puede compartir`() {
        val a = actividad()
        val raiz = panel(a)
        val almacen = AlmacenMusica(carpeta.root)
        almacen.crearPlaylist("Vacía")!!
        val musica = MusicaController(a, raiz, almacen) {}
        musica.mostrar()
        raiz.findViewById<LinearLayout>(R.id.musica_playlists).getChildAt(0).performClick()

        val boton = cabecera(raiz).findViewById<View>(R.id.pl_compartir)
        assertFalse(boton.isEnabled)
        boton.performClick()
        assertNull(shadowOf(a).nextStartedActivity)
    }

    @Test
    fun `ocultar lo quita y volver a mostrar repinta lo guardado entre medias`() {
        val a = actividad()
        val raiz = panel(a)
        val almacen = AlmacenMusica(carpeta.root)
        val musica = MusicaController(a, raiz, almacen) {}
        musica.mostrar()
        musica.ocultar()
        assertFalse(musica.visible)
        assertEquals(View.GONE, raiz.visibility)

        // Lo que se guarda mientras el panel no se ve (A10, Compartir) sale al volver.
        almacen.guardar(cancion(1), emptyList())
        musica.mostrar()
        assertEquals(1, raiz.findViewById<LinearLayout>(R.id.musica_playlists).childCount)
        assertEquals(View.GONE, raiz.findViewById<View>(R.id.musica_vacio).visibility)
    }

    // ── El feed como portada (A4) ────────────────────────────────────────────

    private fun enganches(servidor: Boolean): EnganchesMusica {
        val ctx = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        return EnganchesMusica(
            servidorActivo = { servidor }, uidActivo = { "123456" }, cambiandoDeCuenta = { false },
            motorCoche = MotorCocheFC({ }),
            tokens = lazy { TokensMusica(ctx.getSharedPreferences("t_mc_tokens", android.content.Context.MODE_PRIVATE)) },
            pendientes = PendientesCoche(ctx.getSharedPreferences("t_mc_pend", android.content.Context.MODE_PRIVATE)), entrar = { }
        )
    }

    private val sinRed = ApiMusica({ null }, red = { _, _, _, _ -> throw java.io.IOException("sin red") })

    @Test
    fun `con el servidor encendido la portada es el feed y Lo tuyo lleva a las playlists`() {
        val a = actividad()
        val raiz = panel(a)
        val musica = MusicaController(a, raiz, AlmacenMusica(carpeta.root), enganches(true), { sinRed }) {}

        musica.mostrar()

        assertEquals(View.VISIBLE, raiz.findViewById<View>(R.id.musica_feed_panel).visibility)
        assertEquals(View.VISIBLE, raiz.findViewById<View>(R.id.musica_compartir).visibility)
        assertEquals(View.GONE, raiz.findViewById<View>(R.id.musica_portada).visibility)
        assertEquals(View.GONE, raiz.findViewById<View>(R.id.musica_anadir).visibility)

        raiz.findViewById<View>(R.id.musica_ir_lo_tuyo).performClick()
        assertEquals(View.GONE, raiz.findViewById<View>(R.id.musica_feed_panel).visibility)
        assertEquals(View.GONE, raiz.findViewById<View>(R.id.musica_compartir).visibility)
        assertEquals(View.VISIBLE, raiz.findViewById<View>(R.id.musica_portada).visibility)
        assertEquals(View.VISIBLE, raiz.findViewById<View>(R.id.musica_anadir).visibility)

        // El atrás de "Lo tuyo" vuelve al feed; el del feed ya no es de Música.
        assertTrue(musica.atras())
        assertEquals(View.VISIBLE, raiz.findViewById<View>(R.id.musica_feed_panel).visibility)
        assertFalse(musica.atras())
    }

    @Test
    fun `con el servidor apagado no hay feed y se ve Lo tuyo directamente`() {
        val a = actividad()
        val raiz = panel(a)
        val musica = MusicaController(a, raiz, AlmacenMusica(carpeta.root), enganches(false), { sinRed }) {}

        musica.mostrar()

        assertEquals(View.GONE, raiz.findViewById<View>(R.id.musica_feed_panel).visibility)
        assertEquals(View.GONE, raiz.findViewById<View>(R.id.musica_compartir).visibility)
        assertEquals(View.VISIBLE, raiz.findViewById<View>(R.id.musica_portada).visibility)
        assertFalse(musica.atras())
    }
}
