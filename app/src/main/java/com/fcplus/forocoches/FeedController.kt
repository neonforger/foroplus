package com.fcplus.forocoches

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.PopupMenu
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.util.concurrent.Executors

/**
 * El feed de canciones de 24 h, que es TODA la sección Música: chips Recientes / Top, la lista de
 * tarjetas ([FeedAdapter]), corazón, ⋮ (denunciar, no ver a, borrar) y el estado vacío/error con su
 * reintento. Con el chip Top y sin cursor, la primera fila es "Top de la semana pasada"
 * ([PaginaFeed.masQuerida]): histórica, sin corazón ni menú. Quien lo monta es [MusicaController];
 * este controlador solo conoce las vistas de `panel_musica.xml` que empiezan por
 * `musica_feed`/`musica_chip`.
 *
 * Cuándo pide a la red: al [mostrar] (si lleva más de [REFRESCO_MS] sin cargar), al tocar un chip,
 * con "Reintentar" y cada [REFRESCO_MS] MIENTRAS está a la vista; nunca en segundo plano
 * ([ocultar] apaga el reloj). Si el usuario ya ha paginado, el refresco periódico no le tira lo
 * cargado (le movería la lista bajo el dedo): solo [refrescar] con `forzar` o un chip reinician.
 *
 * Hilos: la red y el disco van por [segundoPlano]; las vistas solo se tocan en el hilo de la
 * interfaz. Una respuesta de una carga anterior (otro chip, otro reinicio) se tira ([gen]).
 *
 * @param api el cliente, con el token de la cuenta activa en CADA llamada (por defecto el de [enganches]).
 */
class FeedController(
    private val act: AppCompatActivity,
    private val raiz: View,
    private val enganches: EnganchesMusica,
    private val reproductor: ReproductorFeed,
    private val abrirVerificacion: () -> Unit,
    private val abrirCompartir: () -> Unit,
    private val bloqueados: BloqueadosMusica =
        BloqueadosMusica(act.getSharedPreferences(BloqueadosMusica.FICHERO, Context.MODE_PRIVATE)),
    private val api: () -> ApiMusica = { enganches.api() },
    private val segundoPlano: (() -> Unit) -> Unit = { red.execute(it) },
    private val reloj: () -> Long = System::currentTimeMillis,
) {
    private val panel: View = raiz.findViewById(R.id.musica_feed_panel)
    private val fab: View = raiz.findViewById(R.id.musica_compartir)
    private val chipRecientes: View = raiz.findViewById(R.id.musica_chip_recientes)
    private val chipTop: View = raiz.findViewById(R.id.musica_chip_top)
    private val lista: RecyclerView = raiz.findViewById(R.id.musica_feed)
    private val estado: View = raiz.findViewById(R.id.musica_feed_estado)
    private val estadoTexto: TextView = raiz.findViewById(R.id.musica_feed_estado_texto)
    private val cargandoVista: View = raiz.findViewById(R.id.musica_feed_cargando)

    private val adaptador = FeedAdapter(
        alCorazon = { corazon(it) },
        alPlay = { item, hueco -> reproductor.tocar(item, hueco) },
        alMenu = { item, ancla -> menu(item, ancla) },
        alAutorCorazones = { quienes(it) },
        alSalirDePantalla = { reproductor.pararSi(it) },
        montarSpotify = { item, contenedor, marcador -> reproductor.montarSpotify(item, contenedor, marcador) },
        soltarSpotify = { reproductor.soltarSpotify(it) }
    )

    private var mostrado = false
    private var orden = OrdenFeed.RECIENTES

    /** Todo lo cargado, sin filtrar; lo bloqueado se quita al pintar (desbloquear lo recuperaría). */
    private var items: List<ItemFeed> = emptyList()
    private var cursor: String? = null

    /**
     * La más querida de la semana pasada: la fila de arriba del chip Top. Solo viene con la primera
     * página (sin cursor) y solo se enseña con Top; con Recientes no existe.
     */
    private var destacada: MasQuerida? = null
    private var paginas = 0
    private var ultima = 0L
    private var cargando = false
    private var uidCarga = ""

    /** App en segundo plano (onPause): ni reloj ni cargas hasta [reanudar]. */
    private var pausado = false

    /** Cuándo falló la última página que se pidió al paginar (0 = no ha fallado): no se reintenta sola en bucle. */
    private var falloPagina = 0L

    /** Sube con cada carga que reinicia: lo que llegue de una anterior no se pinta. */
    private var gen = 0
    private val enVuelo = HashSet<String>()

    /** `puedePublicar` por cuenta, de `/yo`: una vez por sesión, no en cada corazón. */
    private val puede = java.util.concurrent.ConcurrentHashMap<String, Boolean>()

    private val handler = Handler(Looper.getMainLooper())
    private val tic = object : Runnable {
        override fun run() {
            if (!mostrado || pausado) return
            refrescar(false)
            handler.postDelayed(this, REFRESCO_MS)
        }
    }

    init {
        lista.layoutManager = LinearLayoutManager(act)
        lista.adapter = adaptador
        // Paginar al parar el desplazamiento, NUNCA desde onScrolled (gotcha: tocar vistas ahí
        // rompe la lista). Aquí solo se lee la posición.
        lista.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(rv: RecyclerView, nuevo: Int) {
                if (nuevo == RecyclerView.SCROLL_STATE_IDLE) pedirMas()
            }
        })
        chipRecientes.setOnClickListener { elegir(OrdenFeed.RECIENTES) }
        chipTop.setOnClickListener { elegir(OrdenFeed.QUERIDAS) }
        raiz.findViewById<View>(R.id.musica_feed_reintentar).setOnClickListener { refrescar(true) }
        fab.setOnClickListener { abrirCompartir() }
        marcarChips()
    }

    /** Enseña el feed y lo carga si hace falta. Con el servidor apagado no hace nada: ni red ni vistas. */
    fun mostrar() {
        if (!enganches.servidorActivo()) {
            panel.visibility = View.GONE
            fab.visibility = View.GONE
            return
        }
        mostrado = true
        pausado = false
        panel.visibility = View.VISIBLE
        fab.visibility = View.VISIBLE
        handler.removeCallbacks(tic)
        handler.postDelayed(tic, REFRESCO_MS)
        refrescar(false)
    }

    /** `onPause`: el reloj se apaga y no se pide nada más hasta [reanudar] (nada en segundo plano). */
    fun pausar() {
        pausado = true
        handler.removeCallbacks(tic)
    }

    /** `onResume`: si el feed estaba a la vista, vuelve el reloj y se refresca si toca. */
    fun reanudar() {
        if (!mostrado || !pausado) return
        pausado = false
        handler.removeCallbacks(tic)
        handler.postDelayed(tic, REFRESCO_MS)
        refrescar(false)
    }

    /** ¿Sigue a la vista y con la actividad viva? Lo que llega tarde no pinta ni abre diálogos si no. */
    private fun viva() = mostrado && !act.isFinishing && !act.isDestroyed

    /** Quita el feed: se calla lo que suena y se apaga el reloj (nada en segundo plano). */
    fun ocultar() {
        mostrado = false
        handler.removeCallbacks(tic)
        reproductor.parar()
        panel.visibility = View.GONE
        fab.visibility = View.GONE
    }

    /** Tras verificarse o borrar la cuenta: lo que se sabía de la cuenta ya no vale. */
    fun olvidarCuenta() {
        puede.clear()
    }

    /**
     * Pide la primera página. Sin [forzar] respeta los [REFRESCO_MS] desde la última carga, y no
     * reinicia si ya se paginó. Fuera de pantalla no hace nada.
     */
    fun refrescar(forzar: Boolean) {
        if (!mostrado || pausado || !enganches.servidorActivo()) return
        val t = reloj()
        // Otra cuenta activa: lo pintado (corazones, "mías") era de la anterior.
        val sinMiramientos = forzar || enganches.uidActivo() != uidCarga
        if (!sinMiramientos) {
            if (ultima != 0L && t - ultima < REFRESCO_MS) return
            if (paginas > 1) { ultima = t; return }
        }
        pedir(null, reiniciar = true)
    }

    /**
     * Recién publicada desde la pantalla de compartir: con Recientes se mete ARRIBA al instante (el
     * autor la ve aunque el servidor aún la esté convirtiendo y aunque la red tarde) y, además, se
     * pide el feed de nuevo para que quede con su sitio y su hora reales. Con Top no se mete a mano:
     * con 0 corazones su sitio no es arriba; ya la traerá el refresco.
     */
    fun publicada(item: ItemFeed) {
        if (orden == OrdenFeed.RECIENTES && items.none { it.id == item.id }) {
            items = listOf(item) + items
            if (viva()) {
                pintar(null)
                lista.scrollToPosition(0)
            }
        }
        refrescar(true)
    }

    private fun elegir(o: OrdenFeed) {
        if (o == orden && items.isNotEmpty()) return
        orden = o
        marcarChips()
        items = emptyList()
        cursor = null
        destacada = null
        paginas = 0
        adaptador.poner(emptyList(), reloj())
        refrescar(true)
    }

    private fun marcarChips() {
        chipRecientes.isSelected = orden == OrdenFeed.RECIENTES
        chipTop.isSelected = orden == OrdenFeed.QUERIDAS
    }

    private fun pedirMas() {
        val c = cursor ?: return
        if (cargando || !mostrado || pausado) return
        // Una página que falló no se reintenta sola en bucle: hasta 5 s después y con otro gesto.
        if (falloPagina != 0L && reloj() - falloPagina < ESPERA_TRAS_FALLO_MS) return
        val ultimo = (lista.layoutManager as? LinearLayoutManager)?.findLastVisibleItemPosition() ?: return
        if (ultimo >= adaptador.itemCount - 3) pedir(c, reiniciar = false)
    }

    private fun pedir(desde: String?, reiniciar: Boolean) {
        if (cargando && !reiniciar) return
        if (reiniciar) gen++
        val mi = gen
        val ord = orden
        cargando = true
        uidCarga = enganches.uidActivo()
        if (reiniciar && items.isEmpty()) { cargandoVista.visibility = View.VISIBLE; estado.visibility = View.GONE }
        segundoPlano {
            val r: ApiMusica.R<PaginaFeed> = try { api().feed(ord, desde) } catch (_: Exception) { ApiMusica.R.Error("red", 0, null) }
            act.runOnUiThread { llego(mi, r, reiniciar) }
        }
    }

    private fun llego(mi: Int, r: ApiMusica.R<PaginaFeed>, reiniciar: Boolean) {
        if (mi != gen) return  // de una carga anterior (otro chip, otro reinicio)
        cargando = false
        if (!viva()) return  // llegó con el feed ya fuera de pantalla
        cargandoVista.visibility = View.GONE
        if (!reiniciar) {
            if (r is ApiMusica.R.Error) {
                falloPagina = reloj()
                aviso(act.getString(R.string.feed_mas_fallo))
                return
            }
            falloPagina = 0L
        }
        when (r) {
            is ApiMusica.R.Ok -> {
                val p = r.v
                items = if (reiniciar) p.items else items + p.items.filter { n -> items.none { it.id == n.id } }
                cursor = p.cursor
                if (reiniciar) destacada = if (orden == OrdenFeed.QUERIDAS) p.masQuerida else null
                paginas = if (reiniciar) 1 else paginas + 1
                if (reiniciar) ultima = reloj()
                pintar(null)
            }
            is ApiMusica.R.Error -> pintar(r)
        }
    }

    private fun nombresBloqueados(): Set<String> = bloqueados.nombres().map { it.lowercase() }.toSet()

    /** Lo que se pinta: la destacada (solo con Top) y, debajo, lo cargado sin lo bloqueado. */
    private fun filas(): List<ItemFeed> {
        val fuera = nombresBloqueados()
        val top = destacada?.takeIf { orden == OrdenFeed.QUERIDAS && it.autor.lowercase() !in fuera }?.comoItem()
        return listOfNotNull(top) + items.filter { it.mia || it.autor.lowercase() !in fuera }
    }

    /** Pinta lo cargado sin lo bloqueado; sin filas, el estado (con el error si [fallo] es uno). */
    private fun pintar(fallo: ApiMusica.R.Error?) {
        val visibles = filas()
        adaptador.poner(visibles, reloj())
        if (visibles.isEmpty()) {
            estadoTexto.text = when {
                fallo == null -> VistaFeed.estadoVacio(orden, true)
                fallo.codigo == "red" -> VistaFeed.estadoVacio(orden, false)
                else -> VistaFeed.textoError(fallo)
            }
            estado.visibility = View.VISIBLE
        } else {
            estado.visibility = View.GONE
        }
        // Si la página cabe entera en la pantalla no habrá desplazamiento que dispare la siguiente.
        lista.post { pedirMas() }
    }

    // ── Corazón ──────────────────────────────────────────────────────────────

    private enum class Cuenta { SIN_TOKEN, BANEADA, NUEVA, OK }

    private fun corazon(item: ItemFeed) {
        if (item.mia) { aviso(VistaFeed.textoError(ApiMusica.R.Error("propia", 403, null))); return }
        if (!enVuelo.add(item.id)) return  // dos toques seguidos: uno solo viaja
        val uid = enganches.uidActivo()
        segundoPlano {
            val c = cuenta(uid)
            act.runOnUiThread {
                when (c) {
                    Cuenta.SIN_TOKEN -> { enVuelo.remove(item.id); abrirVerificacion() }
                    Cuenta.BANEADA -> { enVuelo.remove(item.id); aviso(VistaFeed.textoError(ApiMusica.R.Error("baneada", 403, null))) }
                    Cuenta.NUEVA -> { enVuelo.remove(item.id); aviso(VistaFeed.textoError(ApiMusica.R.Error("antiguedad", 403, null))) }
                    Cuenta.OK -> darCorazon(item.id)
                }
            }
        }
    }

    /** Token y antigüedad de la cuenta activa. En el hilo de fondo: abrir las prefs cifradas cuesta el keystore. */
    private fun cuenta(uid: String): Cuenta {
        val token = try { enganches.tokens.de(uid) } catch (_: Exception) { null }
        if (token == null) return if (uid.isNotEmpty() && baneada(uid)) Cuenta.BANEADA else Cuenta.SIN_TOKEN
        puede[uid]?.let { return if (it) Cuenta.OK else Cuenta.NUEVA }
        return when (val r = try { api().yo() } catch (_: Exception) { null }) {
            is ApiMusica.R.Ok -> { puede[uid] = r.v.puedePublicar; if (r.v.puedePublicar) Cuenta.OK else Cuenta.NUEVA }
            is ApiMusica.R.Error -> {
                enganches.tokens.trasError(uid, r)
                if (r.tokenInservible) { if (r.codigo == "baneada") Cuenta.BANEADA else Cuenta.SIN_TOKEN } else Cuenta.OK  // sin red: que decida el servidor
            }
            null -> Cuenta.OK
        }
    }

    private fun baneada(uid: String) = try { enganches.tokens.baneada(uid) } catch (_: Exception) { false }

    /** Ya pintado el cambio (optimista) antes de que conteste el servidor; si falla se deshace. */
    private fun darCorazon(id: String) {
        val actual = items.firstOrNull { it.id == id } ?: run { enVuelo.remove(id); return }
        val antes = EstadoCorazon(actual.corazones, actual.corazon)
        val quiere = !actual.corazon
        poner(id, EstadoCorazon((actual.corazones + if (quiere) 1 else -1).coerceAtLeast(0), quiere))
        val uid = enganches.uidActivo()
        segundoPlano {
            val r: ApiMusica.R<EstadoCorazon> = try { api().corazon(id, quiere) } catch (_: Exception) { ApiMusica.R.Error("red", 0, null) }
            act.runOnUiThread {
                enVuelo.remove(id)
                when (r) {
                    is ApiMusica.R.Ok -> poner(id, r.v)
                    is ApiMusica.R.Error -> {
                        if (r.codigo == "no_existe" || r.codigo == "oculta") quitar(id) else poner(id, antes)
                        if (r.tokenInservible) enganches.tokens.trasError(uid, r)
                        if (r.codigo == "antiguedad") puede[uid] = false
                        aviso(VistaFeed.textoErrorCorazon(r))
                    }
                }
            }
        }
    }

    private fun poner(id: String, e: EstadoCorazon) {
        items = items.map { if (it.id == id) it.copy(corazones = e.corazones, corazon = e.corazon) else it }
        adaptador.cambiarCorazon(id, e)
    }

    /** La publicación ya no existe (o se borró): fuera de la lista y, si sonaba, callada. */
    private fun quitar(id: String) {
        items = items.filterNot { it.id == id }
        adaptador.quitar(id)
        reproductor.pararSi(id)
        if (filas().isEmpty()) pintar(null)
    }

    // ── ⋮, denunciar, borrar, quién dio corazón ──────────────────────────────

    private fun menu(item: ItemFeed, ancla: View) {
        val m = PopupMenu(act, ancla)
        if (item.mia) {
            m.menu.add(0, 1, 0, R.string.feed_borrar)
        } else {
            m.menu.add(0, 2, 0, R.string.feed_denunciar)
            m.menu.add(0, 3, 1, act.getString(R.string.feed_no_ver, item.autor))
        }
        m.setOnMenuItemClickListener {
            when (it.itemId) {
                1 -> confirmarBorrar(item)
                2 -> denunciar(item)
                3 -> {
                    bloqueados.bloquear(item.autor)
                    pintar(null)
                    aviso(act.getString(R.string.feed_no_ver_hecho, item.autor))
                }
            }
            true
        }
        m.show()
    }

    private fun confirmarBorrar(item: ItemFeed) {
        reproductor.parar()
        AlertDialog.Builder(act)
            .setTitle(R.string.feed_borrar_titulo)
            .setPositiveButton(R.string.feed_borrar) { _, _ ->
                segundoPlano {
                    val r: ApiMusica.R<Unit> = try { api().borrarPublicacion(item.id) } catch (_: Exception) { ApiMusica.R.Error("red", 0, null) }
                    act.runOnUiThread {
                        when (r) {
                            is ApiMusica.R.Ok -> { quitar(item.id); aviso(act.getString(R.string.feed_borrar_hecho)) }
                            is ApiMusica.R.Error ->
                                if (r.codigo == "no_existe") quitar(item.id) else aviso(VistaFeed.textoError(r))
                        }
                    }
                }
            }
            .setNegativeButton(R.string.feed_cancelar, null)
            .show()
    }

    private fun denunciar(item: ItemFeed) {
        val uid = enganches.uidActivo()
        segundoPlano {
            val tiene = try { enganches.tokens.de(uid) != null } catch (_: Exception) { false }
            act.runOnUiThread {
                if (!tiene) { abrirVerificacion(); return@runOnUiThread }
                if (!viva()) return@runOnUiThread
                reproductor.parar()
                val campo = EditText(act).apply {
                    hint = act.getString(R.string.feed_denunciar_pista)
                    filters = arrayOf(android.text.InputFilter.LengthFilter(MAX_MOTIVO))
                    setPadding(dp(20), dp(12), dp(20), dp(12))
                }
                AlertDialog.Builder(act)
                    .setTitle(R.string.feed_denunciar_titulo)
                    .setView(campo)
                    .setPositiveButton(R.string.feed_denunciar) { _, _ -> enviarDenuncia(item, campo.text.toString()) }
                    .setNegativeButton(R.string.feed_cancelar, null)
                    .show()
            }
        }
    }

    private fun enviarDenuncia(item: ItemFeed, motivo: String) {
        segundoPlano {
            val r: ApiMusica.R<Unit> = try { api().denunciar("publicacion", item.id, motivo.trim()) } catch (_: Exception) { ApiMusica.R.Error("red", 0, null) }
            act.runOnUiThread {
                when (r) {
                    is ApiMusica.R.Ok -> aviso(act.getString(R.string.feed_denunciar_hecho))
                    is ApiMusica.R.Error -> {
                        if (r.codigo == "no_existe") quitar(item.id)
                        aviso(VistaFeed.textoError(r))
                    }
                }
            }
        }
    }

    /** El número de MI publicación: quién dio corazón (solo lo contesta el servidor al autor). */
    private fun quienes(item: ItemFeed) {
        segundoPlano {
            val r: ApiMusica.R<List<String>> = try { api().quienes(item.id) } catch (_: Exception) { ApiMusica.R.Error("red", 0, null) }
            act.runOnUiThread {
                when (r) {
                    is ApiMusica.R.Error -> aviso(VistaFeed.textoError(r))
                    is ApiMusica.R.Ok -> {
                        if (!viva()) return@runOnUiThread
                        reproductor.parar()
                        val b = AlertDialog.Builder(act).setTitle(R.string.feed_quienes_titulo)
                            .setPositiveButton(R.string.feed_cerrar, null)
                        if (r.v.isEmpty()) b.setMessage(R.string.feed_quienes_nadie)
                        else b.setItems(r.v.toTypedArray(), null)
                        b.show()
                    }
                }
            }
        }
    }

    private fun aviso(texto: String) = Toast.makeText(act, texto, Toast.LENGTH_SHORT).show()

    private fun dp(v: Int) = (v * act.resources.displayMetrics.density).toInt()

    companion object {
        /**
         * Cada cuánto se refresca como mucho mientras el feed está a la vista. Un minuto: las
         * publicaciones viven 24 h, y lo propio ya sale al instante al publicar ([publicada]).
         */
        const val REFRESCO_MS = 60_000L
        private const val MAX_MOTIVO = 300  // el tope de cuenta.ts

        /** Un par de hilos para la red del feed: un corazón no espera a una carga lenta. */
        private val red = Executors.newFixedThreadPool(3)
        private const val ESPERA_TRAS_FALLO_MS = 5_000L
    }
}
