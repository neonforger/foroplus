package com.fcplus.forocoches

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
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
 * Dos pantallas: la portada (tus playlists y el "+", que abre la hoja de A10; el feed llega en A4) y la de una
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
 * @param enganches lo del servidor de Música (cuenta, config, verificación). Null en los tests
 *   de la parte local: entonces no hay nada del servidor.
 */
class MusicaController(
    private val act: AppCompatActivity,
    private val raiz: View,
    private val almacen: AlmacenMusica,
    private val enganches: EnganchesMusica? = null,
    /** Solo los tests lo cambian: el cliente del feed (por defecto, el de [enganches]). */
    apiFeed: (() -> ApiMusica)? = null,
    private val abrirFuera: (String) -> Unit
) {

    /** ¿Se está viendo el panel? (El atrás de MainActivity solo le pregunta entonces.) */
    var visible = false
        private set

    private val portada: View = raiz.findViewById(R.id.musica_portada)
    private val verificar: View = raiz.findViewById(R.id.musica_verificar)
    private val borrarCuenta: View = raiz.findViewById(R.id.musica_borrar_cuenta)
    private val anadir: View = raiz.findViewById(R.id.musica_anadir)
    private val tuyas: View = raiz.findViewById(R.id.musica_tuyas)
    private val filas: LinearLayout = raiz.findViewById(R.id.musica_playlists)
    private val vacio: View = raiz.findViewById(R.id.musica_vacio)
    private val pantallaPlaylist: View = raiz.findViewById(R.id.musica_playlist)
    private val lista: RecyclerView = raiz.findViewById(R.id.musica_canciones)

    private val huecoReproductor: FrameLayout = raiz.findViewById(R.id.musica_reproductor)
    private val reproductor = ReproductorMusica(huecoReproductor, abrirFuera)

    private val menus = MenusMusica(act, almacen, abrirFuera, object : MenusMusica.Anfitrion {
        override fun abierta() = abierta
        override fun canciones() = canciones
        override fun estado() = estado
        override fun pausar() = this@MusicaController.pausar()
        override fun nombreAbierta() = this@MusicaController.nombreAbierta()
        override fun volverAPortada() = this@MusicaController.volverAPortada()
        override fun aviso(texto: String) = this@MusicaController.aviso(texto)
        override fun <T : Any> escribir(trabajo: () -> T?, siFalla: String, siVa: (T) -> Unit) =
            this@MusicaController.escribir(trabajo, siFalla, siVa)
    })

    /**
     * El feed (A4): la portada cuando el servidor está encendido. Solo existe con [enganches]: sin
     * ellos (los tests de la parte local) no hay servidor ni feed. El reproductor de sus tarjetas
     * es OTRO que el de las playlists: nunca suenan a la vez (cada uno calla al otro al empezar,
     * y además viven en pantallas distintas).
     */
    private val reproductorFeed: ReproductorFeed? = enganches?.let { ReproductorFeed(act, abrirFuera) }
    private val feed: FeedController? = if (enganches == null || reproductorFeed == null) null else FeedController(
        act, raiz, enganches, almacen, reproductorFeed,
        api = apiFeed ?: { enganches.api() },
        abrirVerificacion = { abrirVerificacion() },
        abrirCompartir = { alCompartir() },
        abrirLoTuyo = { irALoTuyo() },
        alCambiarAlmacen = { if (visible && enLoTuyo) recargar() else recargarSilencioso() },
        antesDeSonar = { reproductor.parar() },
        disco = { disco.execute(it) }
    )

    /**
     * "Compartir canción" (A6 lo sustituye por la hoja de publicar). Hasta entonces el botón avisa
     * en vez de estar muerto.
     */
    var alCompartir: () -> Unit = { aviso("Compartir canciones llega muy pronto.") }

    /**
     * Estando el feed encendido, ¿se está viendo "Lo tuyo" (tus playlists) en vez del feed? Es el
     * paso provisional de A4: A7 rehace "Lo tuyo" y este botón lo abrirá desde ahí.
     */
    private var enLoTuyo = false

    /** Borrar la cuenta de Música. Tras borrarla la portada se repinta. */
    private val flujos: FlujosMusica? = enganches?.let {
        FlujosMusica(act, it, almacen, abrirVerificacion = { abrirVerificacion() }, alCambiar = { feed?.olvidarCuenta(); recargar(); feed?.refrescar(true) })
    }

    private val adaptador = PlaylistAdapter(
        alTocar = { i -> tocarCancion(i) },
        alMenu = { i, ancla -> menus.menuCancion(i, ancla) },
        alMover = { desde, hasta -> mover(desde, hasta) },
        alVolver = { cerrarPlaylist() },
        alReproducir = { if (canciones.isNotEmpty()) { reproductorFeed?.parar(); reproductor.reproducir(canciones, 0) } },
        alMenuPlaylist = { ancla -> menus.menuPlaylist(ancla) },
        alCompartir = { menus.compartir() }
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
        verificar.setOnClickListener { abrirVerificacion() }
        borrarCuenta.setOnClickListener { flujos?.borrarCuenta() }
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
        feed?.ocultar()
        // La playlist abierta se queda; el feed, en cambio, es lo primero que se ve al volver.
        if (abierta == null) enLoTuyo = false
    }

    /** `onDestroy` de la actividad: el reloj del feed y lo que suene, fuera. */
    fun destruir() {
        feed?.ocultar()
        pausar()
    }

    /** `onPause` de la actividad: "La música se para al salir de la app". */
    fun pausar() {
        reproductor.parar()
        reproductorFeed?.parar()
    }

    /** `onPause` de la actividad (no las hojas: esas solo callan lo que suena): además el reloj del feed se apaga. */
    fun alSegundoPlano() {
        pausar()
        feed?.pausar()
    }

    /** `onResume` de la actividad: el reloj del feed vuelve solo si el feed sigue a la vista. */
    fun reanudar() {
        if (visible) feed?.reanudar()
    }

    /**
     * El atrás, antes de que MainActivity deshaga su pila. `true` si lo gestionó (de una
     * playlist a la portada); en la portada es `false` y el atrás hace lo mismo que desde el
     * Popurrí.
     */
    fun atras(): Boolean {
        if (abierta == null) {
            // De "Lo tuyo" al feed (solo si el feed existe: sin él, "Lo tuyo" ES la portada).
            if (!enLoTuyo || !usaFeed()) return false
            enLoTuyo = false
            pintar()
            return true
        }
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
        if (abierta == null) {
            // Feed: la barra de Spotify y la del fragmento se arrastran de lado, como en una playlist.
            if (usaFeed() && !enLoTuyo && reproductorFeed?.sonando() != null) {
                val rv = raiz.findViewById<RecyclerView>(R.id.musica_feed)
                for (i in 0 until rv.childCount) {
                    val hueco = rv.getChildAt(i).findViewById<View>(R.id.pub_hueco) ?: continue
                    if (dentro(hueco, x, y)) return false
                }
            }
            return true
        }
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
        pausar()  // la hoja tapa el reproductor, y YouTube no puede sonar tapado
        // El portapapeles se lee AQUÍ, con la actividad en primer plano (en Android 10+ solo se lee
        // con foco); dentro de la hoja recién mostrada podía salir vacío. Solo si no llega texto de Compartir.
        val copiado = if (texto == null) leerPortapapeles() else null
        HojaAnadirMusica(act, almacen) { mensaje ->
            Toast.makeText(act, mensaje, Toast.LENGTH_LONG).show()
            if (visible) recargar()
        }.mostrar(texto, copiado)
    }

    /**
     * La hoja de verificación (C2). Solo con el servidor encendido en la config; al verificarse,
     * la portada se repinta (la entrada desaparece).
     */
    fun abrirVerificacion() {
        val e = enganches ?: return
        if (!e.servidorActivo()) return
        pausar()  // la hoja tapa el reproductor, y YouTube no puede sonar tapado
        HojaVerificarMusica(act, e) {
            feed?.olvidarCuenta()
            if (visible) recargar()
            feed?.refrescar(true)  // ahora con token: trae los corazones y las publicaciones propias
        }.mostrar()
    }

    /**
     * La entrada de la portada: servidor encendido y la cuenta activa (o ninguna) sin token. El
     * token se mira fuera del hilo de la interfaz: la primera lectura abre las preferencias
     * cifradas, y eso es el keystore. Mientras tanto la entrada se queda como estaba (sin parpadeo).
     */
    private fun pintarEntradaVerificar() {
        val e = enganches
        if (e == null || !e.servidorActivo()) {
            verificar.visibility = View.GONE
            borrarCuenta.visibility = View.GONE
            return
        }
        val uid = e.uidActivo()
        disco.execute {
            val tiene = uid.isNotEmpty() && try { e.tokens.de(uid) != null } catch (_: Exception) { false }
            val ofrecer = uid.isEmpty() || try { !tiene && !e.tokens.baneada(uid) } catch (_: Exception) { true }
            act.runOnUiThread {
                verificar.visibility = if (ofrecer) View.VISIBLE else View.GONE
                borrarCuenta.visibility = if (tiene) View.VISIBLE else View.GONE
            }
        }
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
            if (usaFeed() && !enLoTuyo) pintarFeed() else pintarPortada(e.playlists)
        } else pintarPlaylist(p, e)
    }

    // ── Portada ──────────────────────────────────────────────────────────────

    /** ¿Es el feed la portada? Con el servidor encendido en la config y los enganches puestos. */
    private fun usaFeed(): Boolean = feed != null && enganches?.servidorActivo?.invoke() == true

    /** La portada con el feed: ni "Lo tuyo", ni el "+" de añadir, ni una playlist. */
    private fun pintarFeed() {
        pantallaPlaylist.visibility = View.GONE
        portada.visibility = View.GONE
        anadir.visibility = View.GONE
        feed?.mostrar()
    }

    /** "Lo tuyo": tus playlists (la portada de siempre). */
    private fun irALoTuyo() {
        enLoTuyo = true
        pintar()
    }

    /** Releer lo guardado sin pintar nada si "Lo tuyo" no se ve (un guardado desde el feed). */
    private fun recargarSilencioso() {
        disco.execute {
            val e = try { almacen.estado() } catch (_: Exception) { null } ?: return@execute
            act.runOnUiThread { estado = e }
        }
    }

    private fun pintarPortada(playlists: List<Playlist>) {
        feed?.ocultar()
        pantallaPlaylist.visibility = View.GONE
        portada.visibility = View.VISIBLE
        anadir.visibility = View.VISIBLE
        pintarEntradaVerificar()
        filas.removeAllViews()
        for (p in playlists) filas.addView(fila(p))
        val hay = playlists.isNotEmpty()
        tuyas.visibility = if (hay) View.VISIBLE else View.GONE
        vacio.visibility = if (hay) View.GONE else View.VISIBLE
    }

    private fun fila(p: Playlist): View {
        val v = act.layoutInflater.inflate(R.layout.item_playlist, filas, false)
        val cuantas = Musica.cuantas(p.claves.size)
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
        feed?.ocultar()
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
        else { reproductorFeed?.parar(); reproductor.reproducir(canciones, i) }
    }

    private fun mover(desde: Int, hasta: Int) {
        val id = abierta ?: return
        // El reproductor sigue el orden nuevo ya, sin esperar al disco.
        canciones = adaptador.canciones()
        reproductor.actualizar(canciones)
        escribir({ almacen.mover(id, desde, hasta).takeIf { it } }, "No se ha podido guardar el orden.")
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
    }
}
