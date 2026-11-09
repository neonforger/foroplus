package com.fcplus.forocoches

import android.view.View
import android.view.WindowManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import java.util.concurrent.Executors

/** La portada de una canción (la del servidor) o, mientras llega, el icono de música. */
object PortadasMusica {
    fun pintar(ctx: android.content.Context, iv: ImageView, url: String?) {
        val bmp = url?.let { PostImages.get(it) }
        if (bmp != null) {
            iv.setImageBitmap(bmp)
            return
        }
        val pad = (12 * ctx.resources.displayMetrics.density).toInt()
        iv.setImageResource(R.drawable.ic_musica)
        iv.setPadding(pad, pad, pad, pad)
        iv.imageTintList = ContextCompat.getColorStateList(ctx, R.color.fc_rojo)
        if (url != null) PostImages.load(url) {
            PostImages.get(url)?.let { b ->
                iv.setPadding(0, 0, 0, 0)
                iv.imageTintList = null
                iv.setImageBitmap(b)
            }
        }
    }
}

/**
 * "Playlists de los shurs" (C4): la lista pública (recientes / más guardadas, de 20 en 20) y el
 * detalle de una playlist ajena, de solo lectura: se oye entera, se guarda como si fuera tuya y se
 * denuncia. Listar y ver son públicas (no mandan token); guardar y denunciar cuentan para una
 * cuenta, y sin verificar solo se ofrece verificarse (denunciar) o se guarda solo en el móvil.
 *
 * Tiene su PROPIO [ReproductorMusica], en su hueco del detalle: el de la semana y el de las
 * playlists propias los para quien abre esto (MusicaController). Los listados públicos tardan hasta
 * 60 s en reflejar un cambio (caché del servidor, B7): no es un fallo.
 */
class PanelShurs(
    private val act: AppCompatActivity,
    raiz: View,
    private val enganches: EnganchesMusica,
    private val almacen: AlmacenMusica,
    abrirFuera: (String) -> Unit,
    private val abrirVerificacion: () -> Unit,
    /** Tras guardar una playlist en el móvil: la portada tiene una más. */
    private val alGuardar: () -> Unit
) {
    private val contenedor: View = raiz.findViewById(R.id.musica_shurs)
    private val lista: View = raiz.findViewById(R.id.shurs_lista)
    private val detalle: View = raiz.findViewById(R.id.shurs_detalle)
    private val filas: LinearLayout = raiz.findViewById(R.id.shurs_filas)
    private val estado: TextView = raiz.findViewById(R.id.shurs_estado)
    private val mas: View = raiz.findViewById(R.id.shurs_mas)
    private val recientes: TextView = raiz.findViewById(R.id.shurs_recientes)
    private val guardadas: TextView = raiz.findViewById(R.id.shurs_guardadas)
    private val nombre: TextView = raiz.findViewById(R.id.shurs_det_nombre)
    private val resumen: TextView = raiz.findViewById(R.id.shurs_det_resumen)
    private val canciones: LinearLayout = raiz.findViewById(R.id.shurs_det_canciones)
    private val hueco: FrameLayout = raiz.findViewById(R.id.shurs_reproductor)
    private val reproductor = ReproductorMusica(hueco, abrirFuera)
    /** Lo que queda debajo, tapado: se esconde también a TalkBack (el fondo opaco solo tapa la vista). */
    private val debajo: List<View> = listOf(raiz.findViewById(R.id.musica_portada), raiz.findViewById(R.id.musica_anadir))

    var visible = false
        private set

    private var orden = "recientes"
    private var siguiente: String? = null
    private var hayFilas = false
    private var cargando = false
    /** Cada cambio de orden la sube: lo que llega de una carga vieja se tira. */
    private var generacion = 0

    private var abierta: PlaylistPub? = null
    private var resumenAbierta: PlaylistResumen? = null
    private var cola: List<Cancion> = emptyList()
    private var sonando: String? = null
    private var guardando = false
    private var denunciando = false

    init {
        raiz.findViewById<View>(R.id.shurs_volver).setOnClickListener { cerrar() }
        raiz.findViewById<View>(R.id.shurs_det_volver).setOnClickListener { volverALista() }
        recientes.setOnClickListener { elegirOrden("recientes") }
        guardadas.setOnClickListener { elegirOrden("guardadas") }
        mas.setOnClickListener { cargar(reiniciar = false) }
        estado.setOnClickListener { if (!cargando) cargar(reiniciar = !hayFilas) }
        raiz.findViewById<View>(R.id.shurs_det_reproducir).setOnClickListener { reproducir(0) }
        raiz.findViewById<View>(R.id.shurs_det_guardar).setOnClickListener { guardar() }
        raiz.findViewById<View>(R.id.shurs_det_denunciar).setOnClickListener { denunciar() }
        reproductor.alCambiar = { i ->
            sonando = i?.let { cola.getOrNull(it)?.ref?.clave }
            marcar()
        }
        pintarOrden()
    }

    /** ¿Suena algo aquí? (El gesto de cambiar de pestaña no puede empezar sobre el reproductor.) */
    val reproduciendo: Boolean get() = reproductor.enMarcha
    val huecoReproductor: FrameLayout get() = hueco

    /** Enseña la lista (la trae si no hay nada). Quien llama ya ha parado lo demás. */
    fun abrir() {
        visible = true
        for (v in debajo) v.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        contenedor.visibility = View.VISIBLE
        if (resumenAbierta == null) { lista.visibility = View.VISIBLE; detalle.visibility = View.GONE }
        if (!hayFilas && !cargando) cargar(reiniciar = true)
    }

    /** El atrás: del detalle a la lista y de la lista a la portada. `false` si esto no estaba a la vista. */
    fun atras(): Boolean {
        if (!visible) return false
        if (resumenAbierta != null) volverALista() else cerrar()
        return true
    }

    fun cerrar() {
        parar()
        visible = false
        contenedor.visibility = View.GONE
        for (v in debajo) v.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_AUTO
    }

    /** Nada suena con esto fuera de la vista o con la app en segundo plano. */
    fun parar() {
        reproductor.parar()
        hueco.visibility = View.GONE
    }

    // ── La lista ─────────────────────────────────────────────────────────────

    private fun elegirOrden(nuevo: String) {
        if (nuevo == orden && hayFilas) return
        orden = nuevo
        pintarOrden()
        cargar(reiniciar = true)
    }

    private fun pintarOrden() {
        for ((v, activo) in listOf(recientes to (orden == "recientes"), guardadas to (orden == "guardadas"))) {
            val h = v.paddingStart
            v.setBackgroundResource(if (activo) R.drawable.bg_boton_primario else R.drawable.bg_boton_secundario)
            v.setTextColor(ContextCompat.getColor(act, if (activo) R.color.fc_sobre_rojo else R.color.fc_rojo))
            v.setPaddingRelative(h, 0, h, 0)
            v.isSelected = activo
        }
    }

    private fun cargar(reiniciar: Boolean) {
        if (reiniciar) {
            generacion++
            filas.removeAllViews()
            siguiente = null
            hayFilas = false
            cargando = false
            mas.visibility = View.GONE
        }
        if (cargando) return
        cargando = true
        val g = generacion
        val cursor = siguiente
        val o = orden
        if (!hayFilas) { estado.text = "Cargando…"; estado.visibility = View.VISIBLE }
        hilo.execute {
            val r = try { enganches.api().playlists(o, cursor) } catch (_: Exception) { null }
            act.runOnUiThread {
                if (g != generacion) return@runOnUiThread
                cargando = false
                when (r) {
                    is ApiMusica.R.Ok -> llegada(r.v)
                    else -> {
                        if (hayFilas) Toast.makeText(act, "No se han podido cargar más. Prueba en un rato", Toast.LENGTH_SHORT).show()
                        else { estado.text = "No se han podido cargar las playlists. Toca para reintentar"; estado.visibility = View.VISIBLE }
                    }
                }
            }
        }
    }

    private fun llegada(p: PaginaPlaylists) {
        for (r in p.lista) filas.addView(fila(r))
        hayFilas = filas.childCount > 0
        siguiente = p.siguiente
        mas.visibility = if (siguiente != null) View.VISIBLE else View.GONE
        if (hayFilas) estado.visibility = View.GONE
        else {
            estado.text = "Todavía no hay playlists publicadas. Publica la tuya desde el menú ⋮ de una de tus playlists."
            estado.visibility = View.VISIBLE
        }
    }

    private fun fila(r: PlaylistResumen): View {
        val v = act.layoutInflater.inflate(R.layout.item_playlist, filas, false)
        val texto = "${r.autor} · ${Musica.cuantas(r.canciones)} · ${guardados(r.guardados)}"
        v.findViewById<TextView>(R.id.playlist_nombre).text = r.nombre
        v.findViewById<TextView>(R.id.playlist_cuantas).text = texto
        v.contentDescription = "${r.nombre}, de $texto"
        v.setOnClickListener { abrirDetalle(r) }
        return v
    }

    private fun guardados(n: Int) = if (n == 1) "1 guardado" else "$n guardados"

    // ── El detalle ───────────────────────────────────────────────────────────

    private fun abrirDetalle(r: PlaylistResumen) {
        resumenAbierta = r
        abierta = null
        cola = emptyList()
        sonando = null
        lista.visibility = View.GONE
        detalle.visibility = View.VISIBLE
        nombre.text = r.nombre
        resumen.text = "${r.autor} · ${Musica.cuantas(r.canciones)} · ${guardados(r.guardados)}"
        canciones.removeAllViews()
        hueco.visibility = View.GONE
        hilo.execute {
            val p = try { enganches.api().playlist(r.id) } catch (_: Exception) { null }
            act.runOnUiThread {
                if (resumenAbierta?.id != r.id) return@runOnUiThread
                when (p) {
                    is ApiMusica.R.Ok -> pintarDetalle(p.v)
                    else -> {
                        val noExiste = p is ApiMusica.R.Error && p.status == 404
                        Toast.makeText(
                            act,
                            if (noExiste) "Esa playlist ya no existe" else "No se ha podido abrir la playlist. Prueba en un rato",
                            Toast.LENGTH_SHORT
                        ).show()
                        volverALista()
                        if (noExiste) cargar(reiniciar = true)
                    }
                }
            }
        }
    }

    private fun pintarDetalle(p: PlaylistPub) {
        abierta = p
        cola = p.canciones.mapNotNull { VistaSemana.cancionDe(it) }
        resumen.text = "${p.autor} · ${Musica.cuantas(p.canciones.size)} · ${guardados(p.guardados)}"
        canciones.removeAllViews()
        cola.forEachIndexed { i, c -> canciones.addView(filaCancion(c, i)) }
        reproductor.preparar(cola)
    }

    private fun filaCancion(c: Cancion, i: Int): View {
        val v = act.layoutInflater.inflate(R.layout.item_cancion, canciones, false)
        v.tag = c.ref.clave
        v.findViewById<TextView>(R.id.cancion_titulo).text = c.titulo
        val detalleTxt = v.findViewById<TextView>(R.id.cancion_detalle)
        detalleTxt.text = c.artista.orEmpty()
        detalleTxt.visibility = if (c.artista.isNullOrBlank()) View.GONE else View.VISIBLE
        v.findViewById<View>(R.id.cancion_asa).visibility = View.GONE
        v.findViewById<View>(R.id.cancion_menu).visibility = View.GONE
        v.contentDescription = listOfNotNull(c.titulo, c.artista?.takeIf { it.isNotBlank() }).joinToString(", ")
        PortadasMusica.pintar(act, v.findViewById(R.id.cancion_portada), c.portada)
        v.setOnClickListener { reproducir(i) }
        marcarFila(v)
        return v
    }

    private fun reproducir(i: Int) {
        if (cola.isEmpty()) return
        hueco.visibility = View.VISIBLE
        reproductor.reproducir(cola, i)
    }

    private fun marcar() {
        for (i in 0 until canciones.childCount) marcarFila(canciones.getChildAt(i))
    }

    private fun marcarFila(v: View) {
        val suena = v.tag != null && v.tag == sonando && reproductor.enMarcha
        v.findViewById<TextView>(R.id.cancion_titulo)
            .setTextColor(ContextCompat.getColor(act, if (suena) R.color.fc_rojo else R.color.fc_texto))
        v.findViewById<View>(R.id.cancion_sonando).visibility = if (suena) View.VISIBLE else View.GONE
    }

    private fun volverALista() {
        parar()
        resumenAbierta = null
        abierta = null
        cola = emptyList()
        sonando = null
        detalle.visibility = View.GONE
        lista.visibility = View.VISIBLE
    }

    // ── Guardar ──────────────────────────────────────────────────────────────

    /**
     * Copia la playlist al móvil (queda como una más, sin enlace con la pública) y, con cuenta,
     * suma uno a sus guardados. Lo local va primero y no depende de la red: si el servidor falla,
     * la copia ya está.
     */
    private fun guardar() {
        val p = abierta ?: return
        if (cola.isEmpty() || guardando) return
        guardando = true
        val uid = enganches.uidActivo()
        val aGuardar = cola
        hilo.execute {
            val local = try { almacen.importar(p.nombre, aGuardar) } catch (_: Exception) { null }
            var contador: ApiMusica.R<Int>? = null
            if (local != null && uid.isNotEmpty() && try { enganches.tokens.de(uid) != null } catch (_: Exception) { false }) {
                contador = enganches.api().guardarPublicada(p.id)
                if (contador is ApiMusica.R.Error && contador.tokenInservible) enganches.tokens.borrar(uid)
            }
            act.runOnUiThread {
                guardando = false
                if (local == null) {
                    Toast.makeText(act, "No se ha podido guardar (el tope es ${Musica.MAX_PLAYLISTS} playlists)", Toast.LENGTH_LONG).show()
                    return@runOnUiThread
                }
                Toast.makeText(act, "Guardada en tus playlists como «${local.nombre}»", Toast.LENGTH_LONG).show()
                if (contador is ApiMusica.R.Ok && resumenAbierta?.id == p.id) {
                    resumen.text = "${p.autor} · ${Musica.cuantas(p.canciones.size)} · ${guardados(contador.v)}"
                }
                alGuardar()
            }
        }
    }

    // ── Denunciar ────────────────────────────────────────────────────────────

    private fun denunciar() {
        val p = abierta ?: return
        if (denunciando) return
        denunciando = true
        val uid = enganches.uidActivo()
        hilo.execute {
            val tiene = uid.isNotEmpty() && try { enganches.tokens.de(uid) != null } catch (_: Exception) { false }
            act.runOnUiThread {
                denunciando = false
                if (!tiene) {
                    Toast.makeText(act, "Para denunciar tienes que verificar tu cuenta de ForoCoches", Toast.LENGTH_LONG).show()
                    abrirVerificacion()
                } else pedirMotivo(p)
            }
        }
    }

    private fun pedirMotivo(p: PlaylistPub) {
        val densidad = act.resources.displayMetrics.density
        // El aspecto de @style/Campo a mano (ver MenusMusica.pedirNombre: con el estilo por
        // constructor el campo no coge el foco).
        val caja = EditText(act).apply {
            setBackgroundResource(R.drawable.bg_campo)
            val h = (14 * densidad).toInt()
            val v = (12 * densidad).toInt()
            setPadding(h, v, h, v)
            textSize = 15f
            setTextColor(act.getColor(R.color.fc_texto))
            setHintTextColor(act.getColor(R.color.fc_texto_3))
            hint = "Motivo (por ejemplo: contenido ofensivo)"
            filters = arrayOf(android.text.InputFilter.LengthFilter(MAX_MOTIVO))
            minLines = 2
            maxLines = 5
            gravity = android.view.Gravity.TOP or android.view.Gravity.START
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE or
                android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        }
        val marco = FrameLayout(act).apply {
            val m = (20 * densidad).toInt()
            setPadding(m, (8 * densidad).toInt(), m, 0)
            addView(caja)
        }
        val dlg = AlertDialog.Builder(act)
            .setTitle("Denunciar «${p.nombre}»")
            .setView(marco)
            .setPositiveButton("Denunciar", null)
            .setNegativeButton("Cancelar", null)
            .create()
        dlg.setOnShowListener {
            dlg.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val motivo = caja.text.toString().trim()
                if (motivo.isEmpty()) { caja.error = "Cuéntanos el motivo"; return@setOnClickListener }
                dlg.dismiss()
                enviarDenuncia(p, motivo)
            }
        }
        dlg.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
        dlg.show()
        caja.requestFocus()
    }

    private fun enviarDenuncia(p: PlaylistPub, motivo: String) {
        val uid = enganches.uidActivo()
        hilo.execute {
            val r = enganches.api().denunciar("playlist", p.id, motivo)
            if (r is ApiMusica.R.Error && r.tokenInservible) enganches.tokens.borrar(uid)
            act.runOnUiThread {
                Toast.makeText(
                    act,
                    when (r) {
                        is ApiMusica.R.Ok -> "Denuncia enviada. Gracias por avisar"
                        is ApiMusica.R.Error -> TextosShurs.errorDenuncia(r)
                    },
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private companion object {
        const val MAX_MOTIVO = 300

        /** Un solo hilo para hablar con el servidor desde aquí: las cargas y las acciones en orden. */
        val hilo = Executors.newSingleThreadExecutor()
    }
}
