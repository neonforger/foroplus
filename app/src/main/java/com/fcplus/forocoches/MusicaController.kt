package com.fcplus.forocoches

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Parcelable
import android.text.InputFilter
import android.view.View
import android.view.WindowManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.PopupMenu
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.util.concurrent.Executors

/**
 * La sección Música (spec del 2026-11-07): lo que hay DENTRO de su panel. Molde de
 * [CuentaPanelController]: MainActivity solo pone los enganches (la pestaña "Música" tras el
 * Popurrí, `listSource = "musica"`, el atrás, apagarlo cuando se va a otra fuente o panel y
 * `onPause`).
 *
 * El panel (`res/layout/panel_musica.xml`) vive dentro del FrameLayout de la lista, encima de
 * `list_refresh`, para que la cabecera y la tira de pestañas sigan a la vista y se pueda
 * deslizar a los subforos como desde el Popurrí o +18.
 *
 * Dos pantallas: la portada (tus playlists y el "+", que abre la hoja de A10) y la de una
 * playlist (A11): el reproductor arriba ([ReproductorMusica]) y la lista ([PlaylistAdapter]).
 * Lo que suena se para en [ocultar], [pausar] y al volver a la portada: nada suena fuera de
 * esta pantalla ni con la app en segundo plano.
 *
 * @param raiz el panel ya metido en la jerarquía (su raíz, `R.id.panel_musica`).
 * @param almacen las playlists de este móvil. Se lee al pintar (es un JSON pequeño: ≤ 50
 *   playlists) y así lo guardado mientras no se veía (una hoja, Compartir) sale al volver. Lo que
 *   se escribe desde aquí va en un hilo aparte, en orden ([disco]).
 * @param abrirFuera abre una URL fuera de la app, nunca en la capa web (regla de oro): "Abrir
 *   en YouTube/Spotify" y "Escuchar entera en Spotify". MainActivity le pasa [abrirEnSuApp], que
 *   deja elegir la app de YouTube o de Spotify en vez de fijar el navegador.
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

    private val portada: View = raiz.findViewById(R.id.musica_portada)
    private val anadir: View = raiz.findViewById(R.id.musica_anadir)
    private val tuyas: View = raiz.findViewById(R.id.musica_tuyas)
    private val filas: LinearLayout = raiz.findViewById(R.id.musica_playlists)
    private val vacio: View = raiz.findViewById(R.id.musica_vacio)
    private val pantallaPlaylist: View = raiz.findViewById(R.id.musica_playlist)
    private val lista: RecyclerView = raiz.findViewById(R.id.musica_canciones)

    private val huecoReproductor: FrameLayout = raiz.findViewById(R.id.musica_reproductor)
    private val reproductor = ReproductorMusica(huecoReproductor, abrirFuera)

    private val adaptador = PlaylistAdapter(
        alTocar = { i -> tocarCancion(i) },
        alMenu = { i, ancla -> menuCancion(i, ancla) },
        alMover = { desde, hasta -> mover(desde, hasta) },
        alVolver = { cerrarPlaylist() },
        alReproducir = { if (canciones.isNotEmpty()) reproductor.reproducir(canciones, 0) },
        alMenuPlaylist = { ancla -> menuPlaylist(ancla) },
        alCompartir = { compartir() }
    )

    /** La playlist abierta (su id); null en la portada. */
    private var abierta: String? = null

    /** Las canciones de la playlist abierta, en el orden que tiene también el reproductor. */
    private var canciones: List<Cancion> = emptyList()

    /** La que suena (su clave), según el último aviso del reproductor. */
    private var sonando: String? = null

    /** Lo último leído del almacén: lo que hay pintado. */
    private var estado = EstadoMusica(emptyMap(), emptyList())

    init {
        anadir.setOnClickListener { abrirAnadir(null) }
        lista.layoutManager = LinearLayoutManager(act)
        lista.adapter = adaptador
        val helper = ItemTouchHelper(adaptador.callbackDeArrastre())
        helper.attachToRecyclerView(lista)
        adaptador.touchHelper = helper
        reproductor.alCambiar = { i ->
            sonando = i?.let { canciones.getOrNull(it)?.ref?.clave }
            adaptador.marcar(sonando, reproductor.fallidas)
        }
    }

    /** Enseña el panel con la pantalla en la que se quedó (la portada o una playlist). */
    fun mostrar() {
        visible = true
        raiz.visibility = View.VISIBLE
        pintar()
    }

    /**
     * Quita el panel. MainActivity lo llama al ir a cualquier otra fuente de la lista o a
     * cualquier otro panel (hilo, Opciones, MPs…): nada suena con el panel fuera de la vista. La
     * playlist abierta se queda abierta (al volver, el reproductor ofrece "Seguir con…").
     */
    fun ocultar() {
        visible = false
        raiz.visibility = View.GONE
        reproductor.parar()
    }

    /** `onPause` de la actividad: "La música se para al salir de la app". */
    fun pausar() {
        reproductor.parar()
    }

    /**
     * `onResume` de la actividad: con el panel a la vista, vuelve a leer lo guardado. Lo que llega
     * por "Guardar en Música" lo guarda OTRA MainActivity (Android la abre encima de esta, o en la
     * tarea de la app que comparte), y al volver aquí la portada seguiría sin ello.
     */
    fun refrescar() {
        if (visible) recargar()
    }

    /**
     * El atrás, antes de que MainActivity deshaga su pila. `true` si lo gestionó (de una
     * playlist a la portada); en la portada es `false` y el atrás hace lo mismo que desde el
     * Popurrí.
     */
    fun atras(): Boolean {
        if (abierta == null) return false
        cerrarPlaylist()
        return true
    }

    /**
     * ¿Puede empezar en este punto de la pantalla ([x], [y] en crudo) el gesto de deslizar a otra
     * pestaña? MainActivity lo pregunta al apoyar el dedo. En todas partes menos en dos sitios de
     * una playlist: el reproductor mientras hay algo montado (la barra de YouTube y la de Spotify
     * se arrastran de lado) y las asas de arrastrar (ahí empieza mover una canción).
     *
     * No se excluye la playlist entera a propósito: medido en el Samsung, sin el gesto de pestaña
     * un deslizamiento de lado sobre una tarjeta acababa en un toque normal y PONÍA esa canción;
     * con el gesto, MainActivity manda un CANCEL a la fila y no se toca nada.
     */
    fun dejaDeslizarDesde(x: Int, y: Int): Boolean {
        if (abierta == null) return true
        if (reproductor.enMarcha && dentro(huecoReproductor, x, y)) return false
        for (i in 0 until lista.childCount) {
            val asa = lista.getChildAt(i).findViewById<View>(R.id.cancion_asa) ?: continue
            if (dentro(asa, x, y)) return false
        }
        return true
    }

    private fun dentro(v: View, x: Int, y: Int): Boolean {
        if (!v.isShown) return false
        val loc = IntArray(2)
        v.getLocationOnScreen(loc)
        return x in loc[0]..(loc[0] + v.width) && y in loc[1]..(loc[1] + v.height)
    }

    /** La hoja "Añadir" (A10), con [texto] ya pegado si llega de Compartir (A12). */
    fun abrirAnadir(texto: String?) {
        // El portapapeles se lee AQUÍ, con la actividad en primer plano (en Android 10+ solo se lee
        // con foco); dentro de la hoja recién mostrada podía salir vacío. Solo si no llega texto de Compartir.
        val copiado = if (texto == null) leerPortapapeles() else null
        HojaAnadirMusica(act, almacen) { mensaje ->
            Toast.makeText(act, mensaje, Toast.LENGTH_LONG).show()
            if (visible) recargar()
        }.mostrar(texto, copiado)
    }

    private fun leerPortapapeles(): String? = try {
        val cb = act.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cb.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(act)?.toString()
    } catch (_: Exception) {
        null
    }

    /**
     * Pinta con lo guardado, leído AQUÍ, en el hilo de la interfaz: solo al enseñar el panel
     * ([mostrar], como la portada de A9, que es lo que ve quien entra sin esperar). Después de
     * escribir no se vuelve a leer aquí: lo lee el hilo que escribe ([escribir], [recargar]),
     * que es el que tiene el cerrojo.
     */
    private fun pintar() = pintarCon(almacen.estado())

    /** Vuelve a leer lo guardado fuera del hilo de la interfaz y lo pinta al llegar. */
    private fun recargar() {
        disco.execute {
            val e = try { almacen.estado() } catch (_: Exception) { null } ?: return@execute
            act.runOnUiThread { if (visible) pintarCon(e) else estado = e }
        }
    }

    /** Pinta la pantalla que toca con [e]. Si la playlist abierta ya no existe, la portada. */
    private fun pintarCon(e: EstadoMusica) {
        estado = e
        val id = abierta
        val p = id?.let { e.playlists.firstOrNull { it.id == id } }
        if (p == null) {
            if (id != null) volverAPortada()
            pintarPortada(e.playlists)
        } else pintarPlaylist(p, e)
    }

    // ── Portada ──────────────────────────────────────────────────────────────

    private fun pintarPortada(playlists: List<Playlist>) {
        pantallaPlaylist.visibility = View.GONE
        portada.visibility = View.VISIBLE
        anadir.visibility = View.VISIBLE
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
        v.setOnClickListener { abrirPlaylist(p.id) }
        return v
    }

    // ── Una playlist ─────────────────────────────────────────────────────────

    /** Con lo ya pintado ([estado]): la portada que se toca es justo eso. */
    private fun abrirPlaylist(id: String) {
        val e = estado
        val p = e.playlists.firstOrNull { it.id == id } ?: return recargar()
        abierta = id
        reproductor.preparar(p.claves.mapNotNull { e.canciones[it] })
        pintarPlaylist(p, e)
        lista.scrollToPosition(0)
    }

    private fun pintarPlaylist(p: Playlist, e: EstadoMusica) {
        portada.visibility = View.GONE
        anadir.visibility = View.GONE
        pantallaPlaylist.visibility = View.VISIBLE
        canciones = p.claves.mapNotNull { e.canciones[it] }
        adaptador.pintar(p.nombre, canciones)
        reproductor.actualizar(canciones)
        adaptador.marcar(sonando, reproductor.fallidas)
    }

    /** Lo que suena se para y la cola se olvida: la portada no suena. */
    private fun volverAPortada() {
        abierta = null
        canciones = emptyList()
        sonando = null
        reproductor.preparar(emptyList())
    }

    private fun cerrarPlaylist() {
        volverAPortada()
        pintarCon(estado)
    }

    private fun tocarCancion(i: Int) {
        val c = canciones.getOrNull(i) ?: return
        // Aquí YouTube no la deja sonar: lo que queda es abrirla fuera.
        if (c.ref.clave in reproductor.fallidas) abrirFuera(c.ref.url())
        else reproductor.reproducir(canciones, i)
    }

    private fun mover(desde: Int, hasta: Int) {
        val id = abierta ?: return
        // El reproductor sigue el orden nuevo ya, sin esperar al disco.
        canciones = adaptador.canciones()
        reproductor.actualizar(canciones)
        escribir({ almacen.mover(id, desde, hasta).takeIf { it } }, "No se ha podido guardar el orden.")
    }

    private fun menuCancion(i: Int, ancla: View) {
        val id = abierta ?: return
        val c = canciones.getOrNull(i) ?: return
        val nombre = nombreAbierta()
        val menu = PopupMenu(act, ancla)
        menu.menu.add(0, 1, 1, "Quitar de la playlist")
        menu.menu.add(0, 2, 2, "Añadir a otra playlist…")
        menu.menu.add(0, 3, 3, if (c.ref.fuente == FuenteMusica.YOUTUBE) "Abrir en YouTube" else "Abrir en Spotify")
        menu.setOnMenuItemClickListener { mi ->
            when (mi.itemId) {
                1 -> escribir({ almacen.quitar(id, c.ref.clave).takeIf { it } }, "No se ha podido quitar.") {
                    aviso("Quitada de «$nombre»")
                }
                2 -> anadirAOtra(c)
                3 -> abrirFuera(c.ref.url())
            }
            true
        }
        menu.show()
    }

    private fun anadirAOtra(c: Cancion) {
        val otras = estado.playlists.filter { it.id != abierta }
        val opciones = otras.map { if (c.ref.clave in it.claves) "${it.nombre} (ya está)" else it.nombre } +
            "Nueva playlist…"
        AlertDialog.Builder(act)
            .setTitle("Añadir a otra playlist")
            .setItems(opciones.toTypedArray()) { _, cual ->
                val p = otras.getOrNull(cual)
                when {
                    p == null -> pedirNombre("Nueva playlist", "", "Crear") { nombre ->
                        escribir({
                            almacen.crearPlaylist(nombre)?.takeIf { almacen.guardar(c, listOf(it.id)) }
                        }, "No se ha podido crear la playlist (el tope es ${Musica.MAX_PLAYLISTS}).") {
                            aviso("Añadida a «${it.nombre}»")
                        }
                    }
                    c.ref.clave in p.claves -> aviso("Ya está en «${p.nombre}»")
                    else -> escribir(
                        { almacen.guardar(c, listOf(p.id)).takeIf { it } },
                        "«${p.nombre}» está llena (el tope es ${Musica.MAX_CANCIONES} canciones)."
                    ) { aviso("Añadida a «${p.nombre}»") }
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    /** Compartir: la playlist abierta como texto ([PlaylistCompartida]) por el menú de Android. */
    private fun compartir() {
        if (canciones.isEmpty()) return
        val envio = Intent(Intent.ACTION_SEND)
            .setType("text/plain")
            .putExtra(Intent.EXTRA_TEXT, PlaylistCompartida.texto(nombreAbierta(), canciones))
        // Sin el "Guardar en Música" de esta misma app: solo la duplicaría, y apilando otra
        // MainActivity encima de esta.
        val menu = Intent.createChooser(envio, "Compartir playlist").putExtra(
            Intent.EXTRA_EXCLUDE_COMPONENTS,
            arrayOf<Parcelable>(ComponentName(act.packageName, EntradaCompartida.ALIAS))
        )
        try {
            act.startActivity(menu)
        } catch (_: ActivityNotFoundException) {
            aviso("No hay ninguna app con la que compartir")
        }
    }

    private fun menuPlaylist(ancla: View) {
        val id = abierta ?: return
        val nombre = nombreAbierta()
        val menu = PopupMenu(act, ancla)
        menu.menu.add(0, 1, 1, "Renombrar")
        menu.menu.add(0, 2, 2, "Borrar playlist")
        menu.setOnMenuItemClickListener { mi ->
            when (mi.itemId) {
                1 -> pedirNombre("Renombrar playlist", nombre, "Guardar") { nuevo ->
                    escribir({ almacen.renombrar(id, nuevo).takeIf { it } }, "Ya tienes otra playlist con ese nombre.")
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
            .setMessage(when (val n = canciones.size) {
                0 -> "Se borra de este móvil."
                1 -> "Se borra de este móvil con su canción. Si está también en otra playlist, allí se queda."
                else -> "Se borra de este móvil con sus $n canciones. Las que estén también en otras playlists se quedan allí."
            })
            .setPositiveButton("Borrar") { _, _ ->
                // Se para ya; la portada sale al terminar de escribir, ya sin ella.
                volverAPortada()
                escribir({ almacen.borrarPlaylist(id).takeIf { it } }, "No se ha podido borrar la playlist.") {
                    aviso("Playlist «$nombre» borrada")
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

    /** El nombre que ya se está enseñando (sin volver a leer el disco). */
    private fun nombreAbierta(): String = if (abierta == null) "" else adaptador.nombre()

    /**
     * Escribe en el almacén fuera del hilo de la interfaz y en orden (un solo hilo para todos:
     * dos arrastres seguidos no pueden llegar al disco al revés), lee ahí mismo cómo ha quedado
     * y lo pinta en la interfaz: el hilo de la interfaz no espera al cerrojo ni vuelve a
     * parsear `musica.json`. [trabajo] devuelve null si no se pudo; entonces se enseña [siFalla].
     */
    private fun <T : Any> escribir(trabajo: () -> T?, siFalla: String, siVa: (T) -> Unit = {}) {
        disco.execute {
            val r = try { trabajo() } catch (_: Exception) { null }
            val e = try { almacen.estado() } catch (_: Exception) { null }
            act.runOnUiThread {
                if (r != null) siVa(r) else aviso(siFalla)
                if (e != null) { if (visible) pintarCon(e) else estado = e }
            }
        }
    }

    private fun aviso(texto: String) {
        Toast.makeText(act, texto, Toast.LENGTH_SHORT).show()
    }

    companion object {
        /** Un solo hilo para escribir en `musica.json`, compartido por todas las instancias. */
        private val disco = Executors.newSingleThreadExecutor()

        /**
         * Abre una canción FUERA, en la app que reclame el enlace (YouTube, Spotify) o, si no hay,
         * en el navegador. No pasa por el `openExternal` de MainActivity, que fija el navegador
         * por defecto (bien para los enlaces de un hilo, mal aquí: "Escuchar entera en Spotify"
         * acabaría en Chrome aunque la app de Spotify estuviera instalada). Sin bucle posible:
         * ForoPlus solo reclama enlaces de FC. Y nunca la capa web (regla de oro).
         */
        fun abrirEnSuApp(ctx: Context, url: String) {
            if (!url.startsWith("https://")) return
            val i = Intent(Intent.ACTION_VIEW, Uri.parse(url)).addCategory(Intent.CATEGORY_BROWSABLE)
            if (ctx !is Activity) i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try {
                ctx.startActivity(i)
            } catch (_: ActivityNotFoundException) {
                Toast.makeText(ctx, "No hay ninguna app para abrir este enlace", Toast.LENGTH_SHORT).show()
            }
        }

        /** "Sin canciones", "1 canción", "N canciones": un "0 canciones" se lee como un fallo. */
        fun cuantas(n: Int): String = when (n) {
            0 -> "Sin canciones"
            1 -> "1 canción"
            else -> "$n canciones"
        }

        /** Duración de una canción: "3:25", y con horas "1:02:03". */
        fun duracion(seg: Int): String {
            val s = seg.coerceAtLeast(0)
            val h = s / 3600
            val m = (s % 3600) / 60
            val r = s % 60
            return if (h > 0) "%d:%02d:%02d".format(h, m, r) else "%d:%02d".format(m, r)
        }

        /**
         * La línea de la cabecera: "12 canciones · 48 min". La duración total solo si TODAS la
         * tienen (spec): con una sin saber, el total sería mentira. Se redondea al minuto.
         */
        fun resumen(canciones: List<Cancion>): String {
            val n = cuantas(canciones.size)
            if (canciones.isEmpty() || canciones.any { it.duracionSeg == null }) return n
            val minutos = ((canciones.sumOf { it.duracionSeg ?: 0 } + 30) / 60).coerceAtLeast(1)
            val total = if (minutos >= 60) "${minutos / 60} h ${"%02d".format(minutos % 60)} min" else "$minutos min"
            return "$n · $total"
        }
    }
}
