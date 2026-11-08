package com.fcplus.forocoches

import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner

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
    fun `una playlist vacia lo dice sin poner cero canciones`() {
        assertEquals("Sin canciones", MusicaController.cuantas(0))
        assertEquals("1 canción", MusicaController.cuantas(1))
        assertEquals("200 canciones", MusicaController.cuantas(200))
    }

    @Test
    fun `la duracion de una cancion va en minutos y segundos, y con horas si las hay`() {
        assertEquals("0:00", MusicaController.duracion(0))
        assertEquals("0:07", MusicaController.duracion(7))
        assertEquals("3:25", MusicaController.duracion(205))
        assertEquals("1:02:03", MusicaController.duracion(3723))
    }

    @Test
    fun `el resumen solo lleva la duracion total si todas la tienen`() {
        fun con(seg: Int?) = cancion(seg ?: 0).copy(duracionSeg = seg)
        assertEquals("Sin canciones", MusicaController.resumen(emptyList()))
        assertEquals("2 canciones · 7 min", MusicaController.resumen(listOf(con(200), con(220))))
        // Una sin saber: el total sería mentira.
        assertEquals("2 canciones", MusicaController.resumen(listOf(con(200), con(null))))
        // Menos de medio minuto no se queda en "0 min".
        assertEquals("1 canción · 1 min", MusicaController.resumen(listOf(con(10))))
        assertEquals("20 canciones · 1 h 05 min", MusicaController.resumen(List(20) { con(195) }))
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
}
