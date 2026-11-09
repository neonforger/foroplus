package com.fcplus.forocoches

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Intent
import android.os.Parcelable
import android.text.InputFilter
import android.view.View
import android.view.WindowManager
import android.widget.EditText
import android.widget.FrameLayout
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.PopupMenu

/**
 * Los menús y diálogos de una playlist (⋮ de una canción, ⋮ de la playlist, añadir a otra,
 * renombrar, borrar, compartir). Salieron de [MusicaController] sin cambiar conducta (A11 los
 * dejó con ~500 líneas): el controlador sigue siendo quien sabe qué playlist está abierta y quien
 * escribe en el almacén ([Anfitrion]); esta clase solo pregunta y avisa.
 */
class MenusMusica(
    private val act: AppCompatActivity,
    private val almacen: AlmacenMusica,
    private val abrirFuera: (String) -> Unit,
    private val d: Anfitrion
) {
    /** Lo que estos menús necesitan saber del controlador y lo que le piden que haga. */
    interface Anfitrion {
        fun abierta(): String?
        fun canciones(): List<Cancion>
        fun estado(): EstadoMusica
        fun nombreAbierta(): String
        fun volverAPortada()
        fun aviso(texto: String)
        fun <T : Any> escribir(trabajo: () -> T?, siFalla: String, siVa: (T) -> Unit = {})
    }

    fun menuCancion(i: Int, ancla: View) {
        val id = d.abierta() ?: return
        val c = d.canciones().getOrNull(i) ?: return
        val nombre = d.nombreAbierta()
        val menu = PopupMenu(act, ancla)
        menu.menu.add(0, 1, 1, "Quitar de la playlist")
        menu.menu.add(0, 2, 2, "Añadir a otra playlist…")
        menu.menu.add(0, 3, 3, if (c.ref.fuente == FuenteMusica.YOUTUBE) "Abrir en YouTube" else "Abrir en Spotify")
        menu.setOnMenuItemClickListener { mi ->
            when (mi.itemId) {
                1 -> d.escribir({ almacen.quitar(id, c.ref.clave).takeIf { it } }, "No se ha podido quitar.") {
                    d.aviso("Quitada de «$nombre»")
                }
                2 -> anadirAOtra(c)
                3 -> abrirFuera(c.ref.url())
            }
            true
        }
        menu.show()
    }

    private fun anadirAOtra(c: Cancion) {
        val otras = d.estado().playlists.filter { it.id != d.abierta() }
        val opciones = otras.map { if (c.ref.clave in it.claves) "${it.nombre} (ya está)" else it.nombre } +
            "Nueva playlist…"
        AlertDialog.Builder(act)
            .setTitle("Añadir a otra playlist")
            .setItems(opciones.toTypedArray()) { _, cual ->
                val p = otras.getOrNull(cual)
                when {
                    p == null -> pedirNombre("Nueva playlist", "", "Crear") { nombre ->
                        d.escribir({
                            almacen.crearPlaylist(nombre)?.takeIf { almacen.guardar(c, listOf(it.id)) }
                        }, "No se ha podido crear la playlist (el tope es ${Musica.MAX_PLAYLISTS}).") {
                            d.aviso("Añadida a «${it.nombre}»")
                        }
                    }
                    c.ref.clave in p.claves -> d.aviso("Ya está en «${p.nombre}»")
                    else -> d.escribir(
                        { almacen.guardar(c, listOf(p.id)).takeIf { it } },
                        "«${p.nombre}» está llena (el tope es ${Musica.MAX_CANCIONES} canciones)."
                    ) { d.aviso("Añadida a «${p.nombre}»") }
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    /** Compartir: la playlist abierta como texto ([PlaylistCompartida]) por el menú de Android. */
    fun compartir() {
        if (d.canciones().isEmpty()) return
        val envio = Intent(Intent.ACTION_SEND)
            .setType("text/plain")
            .putExtra(Intent.EXTRA_TEXT, PlaylistCompartida.texto(d.nombreAbierta(), d.canciones()))
        // Sin el "Guardar en Música" de esta misma app: solo la duplicaría.
        val menu = Intent.createChooser(envio, "Compartir playlist").putExtra(
            Intent.EXTRA_EXCLUDE_COMPONENTS,
            arrayOf<Parcelable>(ComponentName(act, GuardarEnMusica::class.java))
        )
        try {
            act.startActivity(menu)
        } catch (_: ActivityNotFoundException) {
            d.aviso("No hay ninguna app con la que compartir")
        }
    }

    fun menuPlaylist(ancla: View) {
        val id = d.abierta() ?: return
        val nombre = d.nombreAbierta()
        val menu = PopupMenu(act, ancla)
        menu.menu.add(0, 1, 1, "Renombrar")
        menu.menu.add(0, 2, 2, "Borrar playlist")
        menu.setOnMenuItemClickListener { mi ->
            when (mi.itemId) {
                1 -> pedirNombre("Renombrar playlist", nombre, "Guardar") { nuevo ->
                    d.escribir({ almacen.renombrar(id, nuevo).takeIf { it } }, "Ya tienes otra playlist con ese nombre.")
                }
                2 -> confirmarBorrado(id, nombre)
            }
            true
        }
        menu.show()
    }

    private fun confirmarBorrado(id: String, nombre: String) {
        AlertDialog.Builder(act)
            .setTitle("¿Borrar «$nombre»?")
            .setMessage(when (val n = d.canciones().size) {
                0 -> "Se borra de este móvil."
                1 -> "Se borra de este móvil con su canción. Si está también en otra playlist, allí se queda."
                else -> "Se borra de este móvil con sus $n canciones. Las que estén también en otras playlists se quedan allí."
            })
            .setPositiveButton("Borrar") { _, _ ->
                // Se para ya; la portada sale al terminar de escribir, ya sin ella.
                d.volverAPortada()
                d.escribir({ almacen.borrarPlaylist(id).takeIf { it } }, "No se ha podido borrar la playlist.") {
                    d.aviso("Playlist «$nombre» borrada")
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    /** Pide un nombre de playlist (≤ [Musica.MAX_NOMBRE]); [alAceptar] lo recibe ya limpio. */
    private fun pedirNombre(titulo: String, inicial: String, boton: String, alAceptar: (String) -> Unit) {
        val densidad = act.resources.displayMetrics.density
        // El aspecto de @style/Campo a mano: con `EditText(act, null, 0, R.style.Campo)` se pierde
        // el editTextStyle de la plataforma y el campo no coge el foco al tocarlo (medido: ni
        // teclado ni borrar).
        val caja = EditText(act).apply {
            setBackgroundResource(R.drawable.bg_campo)
            val h = (14 * densidad).toInt()
            val v = (12 * densidad).toInt()
            setPadding(h, v, h, v)
            textSize = 15f
            setTextColor(act.getColor(R.color.fc_texto))
            setHintTextColor(act.getColor(R.color.fc_texto_3))
            hint = "Nombre de la playlist"
            isSingleLine = true
            filters = arrayOf(InputFilter.LengthFilter(Musica.MAX_NOMBRE))
            // El texto y el cursor, DESPUÉS de isSingleLine: cambiar a una línea vuelve a poner
            // el texto y deja el cursor al principio (medido: escribir añadía delante del nombre).
            setText(inicial)
            setSelection(text.length)
        }
        val marco = FrameLayout(act).apply {
            val m = (20 * densidad).toInt()
            setPadding(m, (8 * densidad).toInt(), m, 0)
            addView(caja)
        }
        val dlg = AlertDialog.Builder(act)
            .setTitle(titulo)
            .setView(marco)
            .setPositiveButton(boton, null)
            .setNegativeButton("Cancelar", null)
            .create()
        dlg.setOnShowListener {
            // El botón se maneja a mano para que un nombre vacío no cierre el diálogo.
            dlg.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val limpio = Musica.nombreLimpio(caja.text.toString())
                if (limpio == null) {
                    caja.error = "Escribe un nombre"
                    return@setOnClickListener
                }
                dlg.dismiss()
                alAceptar(limpio)
            }
        }
        dlg.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
        dlg.show()
        caja.requestFocus()
    }

}
