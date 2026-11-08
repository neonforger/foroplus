package com.fcplus.forocoches

import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

/**
 * La sección Música (spec del 2026-11-07): lo que hay DENTRO de su panel. Molde de
 * [CuentaPanelController]: MainActivity solo pone los enganches (la pestaña "Música" tras el
 * Popurrí, `listSource = "musica"`, el atrás y apagarlo cuando se va a otra fuente o panel).
 *
 * El panel (`res/layout/panel_musica.xml`) vive dentro del FrameLayout de la lista, encima de
 * `list_refresh`, para que la cabecera y la tira de pestañas sigan a la vista y se pueda
 * deslizar a los subforos como desde el Popurrí o +18.
 *
 * Por pasos del plan: aquí está la portada (tus playlists y el "+"). La hoja de añadir
 * ([abrirAnadir]) es de A10; la pantalla de una playlist con su reproductor, de A11, y con ella
 * llegan el atrás de playlist → portada ([atras]) y parar lo que suene ([ocultar], [pausar]).
 *
 * @param raiz el panel ya metido en la jerarquía (su raíz, `R.id.panel_musica`).
 * @param almacen las playlists de este móvil. Se lee al enseñar la portada: es un JSON pequeño
 *   (≤ 50 playlists) y así lo guardado mientras no se veía (una hoja, Compartir) sale al volver.
 * @param abrirFuera abre una URL fuera de la app, nunca en la capa web (regla de oro). La usa
 *   A11: "Abrir en YouTube" y "Escuchar entera en Spotify".
 */
class MusicaController(
    private val act: AppCompatActivity,
    private val raiz: View,
    private val almacen: AlmacenMusica,
    private val abrirFuera: (String) -> Unit
) {

    /** ¿Se está viendo el panel? (El atrás de MainActivity solo le pregunta entonces.) */
    var visible = false
        private set

    private val tuyas: View = raiz.findViewById(R.id.musica_tuyas)
    private val filas: LinearLayout = raiz.findViewById(R.id.musica_playlists)
    private val vacio: View = raiz.findViewById(R.id.musica_vacio)

    init {
        raiz.findViewById<View>(R.id.musica_anadir).setOnClickListener { abrirAnadir(null) }
    }

    /** La portada: tus playlists y el "+". */
    fun mostrar() {
        visible = true
        raiz.visibility = View.VISIBLE
        pintarPortada()
    }

    /**
     * Quita el panel. MainActivity lo llama al ir a cualquier otra fuente de la lista o a
     * cualquier otro panel (hilo, Opciones, MPs…). A11 parará aquí el reproductor: nada suena
     * con el panel fuera de la vista.
     */
    fun ocultar() {
        visible = false
        raiz.visibility = View.GONE
    }

    /** `onPause` de la actividad. Gancho de A11 (parar el reproductor); la portada no suena. */
    fun pausar() {}

    /**
     * El atrás, antes de que MainActivity deshaga su pila. `true` si lo gestionó (de una
     * playlist a la portada, A11); en la portada es `false` y el atrás hace lo mismo que desde
     * el Popurrí.
     */
    fun atras(): Boolean = false

    /** La hoja "Añadir" (A10), con [texto] ya pegado si llega de Compartir (A12). */
    fun abrirAnadir(texto: String?) {
        HojaAnadirMusica(act, almacen) { mensaje ->
            android.widget.Toast.makeText(act, mensaje, android.widget.Toast.LENGTH_LONG).show()
            if (visible) pintarPortada()
        }.mostrar(texto)
    }

    private fun pintarPortada() {
        val playlists = almacen.estado().playlists
        filas.removeAllViews()
        for (p in playlists) filas.addView(fila(p))
        val hay = playlists.isNotEmpty()
        tuyas.visibility = if (hay) View.VISIBLE else View.GONE
        vacio.visibility = if (hay) View.GONE else View.VISIBLE
    }

    private fun fila(p: Playlist): View {
        val v = act.layoutInflater.inflate(R.layout.item_playlist, filas, false)
        val cuantas = cuantas(p.claves.size)
        v.findViewById<TextView>(R.id.playlist_nombre).text = p.nombre
        v.findViewById<TextView>(R.id.playlist_cuantas).text = cuantas
        // TalkBack lee la fila de una vez ("Mis favoritas, 3 canciones").
        v.contentDescription = "${p.nombre}, $cuantas"
        return v
    }

    companion object {
        /** "Sin canciones", "1 canción", "N canciones": un "0 canciones" se lee como un fallo. */
        fun cuantas(n: Int): String = when (n) {
            0 -> "Sin canciones"
            1 -> "1 canción"
            else -> "$n canciones"
        }
    }
}
