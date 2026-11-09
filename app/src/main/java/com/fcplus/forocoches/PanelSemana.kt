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

/**
 * La canción de la semana dentro de la portada de Música (C3): la ganadora de la semana cerrada,
 * el top de la semana en curso con un corazón en cada canción, los votos que te quedan y
 * "Proponer una canción". Las decisiones (qué corazón se puede tocar, qué texto sale con cada
 * error, qué hacer con un token muerto) están en [VistaSemana] y [CargaSemana]; esto pinta y
 * habla con el servidor en un hilo aparte.
 *
 * Solo existe con el servidor encendido ([EnganchesMusica.servidorActivo]): sin él, [mostrar]
 * deja el bloque oculto y la portada es la de A9 tal cual. Sin token (cuenta sin verificar) en
 * lugar del top no sale nada aquí: la tarjeta "Vota la canción de la semana" es la entrada de
 * la portada y lleva a la hoja de C2.
 *
 * Suena con su propio [ReproductorMusica] en su hueco de la portada, que solo se enseña al tocar
 * una canción. Quien saque la portada de la vista llama a [parar] (nada suena fuera).
 *
 * @param raiz el panel de Música (para encontrar su bloque).
 * @param misCanciones las canciones guardadas en este móvil, para "elegir de tus canciones".
 * @param alCargar tras cada carga, en el hilo de la interfaz: el token pudo borrarse y la
 *   portada decide si ofrece verificarse.
 */
class PanelSemana(
    private val act: AppCompatActivity,
    raiz: View,
    private val enganches: EnganchesMusica,
    abrirFuera: (String) -> Unit,
    private val misCanciones: () -> Collection<Cancion>,
    private val abrirVerificacion: () -> Unit,
    private val alCargar: () -> Unit
) {
    private val contenedor: View = raiz.findViewById(R.id.musica_semana)
    /** El hueco del reproductor de la semana: fijo arriba del panel, fuera del scroll. */
    val huecoReproductor: FrameLayout = raiz.findViewById(R.id.musica_semana_reproductor)
    private val tituloGanadora: View = raiz.findViewById(R.id.semana_titulo_ganadora)
    private val ganadora: LinearLayout = raiz.findViewById(R.id.semana_ganadora)
    private val tituloTop: View = raiz.findViewById(R.id.semana_titulo_top)
    private val nota: View = raiz.findViewById(R.id.semana_nota)
    private val top: LinearLayout = raiz.findViewById(R.id.semana_top)
    private val vacia: View = raiz.findViewById(R.id.semana_vacia)
    private val pie: TextView = raiz.findViewById(R.id.semana_pie)
    private val proponer: View = raiz.findViewById(R.id.semana_proponer)
    private val bloqueada: View = raiz.findViewById(R.id.semana_bloqueada)
    private val error: View = raiz.findViewById(R.id.semana_error)

    private val reproductor = ReproductorMusica(huecoReproductor, abrirFuera)

    /** Lo último cargado y de qué cuenta; null = nada (o de otra cuenta). Solo hilo de la interfaz. */
    private var datos: DatosSemana? = null
    private var uidDatos = ""
    private var ultimaCarga = 0L
    private var cargando = false
    private var repetir = false
    private var votando = false
    private var proponiendo = false

    /** La cola del reproductor y si es la del top (entonces sigue el orden en vivo). */
    private var cola: List<Cancion> = emptyList()
    private var colaEsTop = false
    private var sonando: String? = null

    init {
        proponer.setOnClickListener { elegirComoProponer() }
        error.setOnClickListener { error.visibility = View.GONE; refrescar(forzar = true) }
        reproductor.alCambiar = { i ->
            sonando = i?.let { cola.getOrNull(it)?.ref?.clave }
            marcar()
        }
    }

    /** ¿Suena algo de la semana ahora? (El gesto de cambiar de pestaña no puede empezar sobre el reproductor.) */
    val reproduciendo: Boolean get() = reproductor.enMarcha

    /** ¿La cuenta activa está verificada? Según lo último cargado (leer el token cuesta el keystore). */
    val verificada: Boolean get() = datos?.verificada == true && uidDatos == enganches.uidActivo()

    /** Enseña el bloque (o lo apaga con la config apagada) y trae lo nuevo si lo que hay es viejo. */
    fun mostrar() {
        if (!enganches.servidorActivo()) {
            parar()
            datos = null
            contenedor.visibility = View.GONE
            return
        }
        if (uidDatos != enganches.uidActivo()) datos = null
        if (datos != null) pintar() else contenedor.visibility = View.GONE
        refrescar(forzar = false)
    }

    /** Nada suena con la portada fuera de la vista. */
    fun parar() {
        reproductor.parar()
        huecoReproductor.visibility = View.GONE
    }

    /** Vuelve a pedir la semana (tras verificarse, votar o proponer); sin [forzar], solo si pasaron 30 s. */
    fun refrescar(forzar: Boolean) {
        val uid = enganches.uidActivo()
        if (!enganches.servidorActivo()) return
        if (!forzar && datos != null && uid == uidDatos && System.currentTimeMillis() - ultimaCarga < REFRESCO_MS) return
        if (cargando) { if (forzar) repetir = true; return }
        cargando = true
        hilo.execute {
            val r = try {
                CargaSemana.cargar(
                    enganches.api(),
                    tieneToken = { uid.isNotEmpty() && enganches.tokens.de(uid) != null },
                    borrarToken = { enganches.tokens.borrar(uid) },
                    baneadaGuardada = { enganches.tokens.baneada(uid) },
                    guardarBaneada = { enganches.tokens.marcarBaneada(uid) }
                )
            } catch (_: Exception) { null }
            act.runOnUiThread { cargado(uid, r) }
        }
    }

    private fun cargado(uid: String, r: ApiMusica.R<DatosSemana>?) {
        cargando = false
        if (!enganches.servidorActivo()) return
        // La cuenta cambió mientras llegaba: eso es de la otra. Se pide lo de la de ahora.
        if (uid != enganches.uidActivo()) { datos = null; refrescar(true); return }
        if (r is ApiMusica.R.Ok) {
            datos = r.v
            uidDatos = uid
            ultimaCarga = System.currentTimeMillis()
            pintar()
        } else if (datos == null) pintarError()
        alCargar()
        if (repetir) { repetir = false; refrescar(true) }
    }

    // ── Pintar ───────────────────────────────────────────────────────────────

    private fun pintar() {
        val d = datos ?: return
        contenedor.visibility = View.VISIBLE
        error.visibility = View.GONE
        val s = d.semana

        ganadora.removeAllViews()
        val g = s.cancionSemana
        tituloGanadora.visibility = if (g != null) View.VISIBLE else View.GONE
        if (g != null) {
            val votos = s.top.firstOrNull { it.first.id == g.id }?.second
            val extra = votos?.let { if (it == 1) "1 voto" else "$it votos" }
            ganadora.addView(fila(g, extra, null) { reproducir(listOf(g), g, enTop = false) })
        }

        top.removeAllViews()
        val ver = d.verificada
        tituloTop.visibility = if (ver) View.VISIBLE else View.GONE
        nota.visibility = if (ver) View.VISIBLE else View.GONE
        proponer.visibility = if (ver) View.VISIBLE else View.GONE
        bloqueada.visibility = if (d.baneada) View.VISIBLE else View.GONE
        if (!ver) {
            vacia.visibility = View.GONE
            pie.visibility = View.GONE
            return
        }
        val filas = VistaSemana.filas(s, d.yo)
        vacia.visibility = if (filas.isEmpty()) View.VISIBLE else View.GONE
        val tocables = filas.map { it.cancion }
        for (f in filas) top.addView(fila(f.cancion, null, f) { reproducir(tocables, f.cancion, enTop = true) })
        pie.visibility = if (d.yo != null) View.VISIBLE else View.GONE
        d.yo?.let { pie.text = VistaSemana.restantes(it) }
        // El orden en vivo también manda en lo que suena: la cola sigue al top.
        if (reproductor.enMarcha && colaEsTop) {
            cola = tocables.mapNotNull { VistaSemana.cancionDe(it) }
            reproductor.actualizar(cola)
        }
    }

    /** Sin nada cargado y la carga fallo: solo el aviso, que se toca para reintentar. */
    private fun pintarError() {
        contenedor.visibility = View.VISIBLE
        for (v in listOf(tituloGanadora, tituloTop, nota, vacia, pie, proponer, bloqueada)) v.visibility = View.GONE
        ganadora.removeAllViews()
        top.removeAllViews()
        error.visibility = View.VISIBLE
    }

    /** Una fila: la misma tarjeta que las canciones de una playlist, con el corazón en lugar del ⋮. */
    private fun fila(c: CancionPub, extra: String?, corazon: FilaSemana?, alTocar: () -> Unit): View {
        val v = act.layoutInflater.inflate(R.layout.item_cancion, null, false)
        val clave = VistaSemana.cancionDe(c)?.ref?.clave
        v.tag = clave
        val titulo = v.findViewById<TextView>(R.id.cancion_titulo)
        val detalle = v.findViewById<TextView>(R.id.cancion_detalle)
        titulo.text = c.titulo
        val txt = listOfNotNull(c.artista?.takeIf { it.isNotBlank() }, extra).joinToString(" · ")
        detalle.text = txt
        detalle.visibility = if (txt.isEmpty()) View.GONE else View.VISIBLE
        v.findViewById<View>(R.id.cancion_asa).visibility = View.GONE
        val menu = v.findViewById<ImageView>(R.id.cancion_menu)
        if (corazon == null) menu.visibility = View.GONE else pintarCorazon(menu, c, corazon)
        v.contentDescription = listOfNotNull(c.titulo, txt.ifEmpty { null }).joinToString(", ")
        pintarPortada(v.findViewById(R.id.cancion_portada), c.portada)
        v.setOnClickListener { alTocar() }
        marcarFila(v)
        return v
    }

    private fun pintarCorazon(corazon: ImageView, c: CancionPub, f: FilaSemana) {
        corazon.setImageResource(if (f.votada) R.drawable.ic_corazon_on else R.drawable.ic_corazon)
        corazon.contentDescription = (if (f.votada) "Quitar el voto a «" else "Votar «") + c.titulo + "»"
        // Apagado se sigue tocando (dice por qué no), pero se ve apagado.
        corazon.alpha = if (f.corazonActivo) 1f else 0.4f
        corazon.setOnClickListener {
            if (!f.corazonActivo) aviso(VistaSemana.motivoSinCorazon(datos?.yo), largo = true)
            else votar(f)
        }
    }

    private fun pintarPortada(iv: ImageView, url: String?) {
        val bmp = url?.let { PostImages.get(it) }
        if (bmp != null) {
            iv.setImageBitmap(bmp)
            return
        }
        val pad = (12 * act.resources.displayMetrics.density).toInt()
        iv.setImageResource(R.drawable.ic_musica)
        iv.setPadding(pad, pad, pad, pad)
        iv.imageTintList = ContextCompat.getColorStateList(act, R.color.fc_rojo)
        if (url != null) PostImages.load(url) {
            PostImages.get(url)?.let { b ->
                iv.setPadding(0, 0, 0, 0)
                iv.imageTintList = null
                iv.setImageBitmap(b)
            }
        }
    }

    /** Lo que suena: el título en rojo y la marca sobre la portada, sin repintar nada más. */
    private fun marcar() {
        for (grupo in listOf(ganadora, top)) for (i in 0 until grupo.childCount) marcarFila(grupo.getChildAt(i))
    }

    private fun marcarFila(v: View) {
        val suena = v.tag != null && v.tag == sonando && reproductor.enMarcha
        v.findViewById<TextView>(R.id.cancion_titulo)
            .setTextColor(ContextCompat.getColor(act, if (suena) R.color.fc_rojo else R.color.fc_texto))
        v.findViewById<View>(R.id.cancion_sonando).visibility = if (suena) View.VISIBLE else View.GONE
    }

    // ── Reproducir ───────────────────────────────────────────────────────────

    private fun reproducir(lista: List<CancionPub>, tocada: CancionPub, enTop: Boolean) {
        val canciones = lista.mapNotNull { VistaSemana.cancionDe(it) }
        val clave = VistaSemana.cancionDe(tocada)?.ref?.clave ?: return
        val i = canciones.indexOfFirst { it.ref.clave == clave }
        if (i < 0) return
        cola = canciones
        colaEsTop = enTop
        huecoReproductor.visibility = View.VISIBLE
        reproductor.reproducir(canciones, i)
    }

    // ── Votar ────────────────────────────────────────────────────────────────

    private fun votar(f: FilaSemana) {
        if (votando) return
        votando = true
        val uid = enganches.uidActivo()
        hilo.execute {
            val r = enganches.api().votar(f.cancion.id, !f.votada)
            if (r is ApiMusica.R.Error) tirarToken(uid, VistaSemana.trasFallo(r, VistaSemana.Accion.VOTAR))
            act.runOnUiThread {
                votando = false
                when (r) {
                    is ApiMusica.R.Ok -> votado(uid, f.cancion.id, r.v)
                    is ApiMusica.R.Error -> fallo(r, VistaSemana.Accion.VOTAR)
                }
            }
        }
    }

    /** El corazón cambia YA con lo que contestó el servidor; el orden nuevo llega con la carga. */
    private fun votado(uid: String, cancion: String, v: Voto) {
        val d = datos
        if (d != null && uid == uidDatos) {
            val votadas = if (v.votada) d.semana.votadas + cancion else d.semana.votadas - cancion
            datos = d.copy(
                semana = d.semana.copy(votadas = votadas),
                yo = d.yo?.copy(votosRestantes = v.votosRestantes)
            )
            pintar()
        }
        refrescar(forzar = true)
    }

    /** Un error de una acción: el texto y, si toca, volver a cargar (el token ya se tiró en el hilo de fondo). */
    private fun fallo(e: ApiMusica.R.Error, accion: VistaSemana.Accion) {
        val t = VistaSemana.trasFallo(e, accion)
        aviso(t.texto, largo = true)
        if (t.recargar) refrescar(forzar = true)
    }

    /** En el hilo de fondo: un token muerto se borra, y un baneo se recuerda para no ofrecer verificarse. */
    private fun tirarToken(uid: String, t: VistaSemana.TrasFallo) {
        if (t.borrarToken) enganches.tokens.borrar(uid)
        if (t.baneada) enganches.tokens.marcarBaneada(uid)
    }

    // ── Proponer ─────────────────────────────────────────────────────────────

    private fun elegirComoProponer() {
        if (!verificada) { abrirVerificacion(); return }
        AlertDialog.Builder(act)
            .setTitle("Proponer una canción")
            .setItems(arrayOf("Pegar un enlace…", "Elegir de mis canciones…")) { _, cual ->
                if (cual == 0) pedirEnlace() else elegirDeMisCanciones()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun elegirDeMisCanciones() {
        // Las más recientes primero, hasta el tope de una playlist: una lista de miles no se elige.
        val mias = misCanciones().sortedByDescending { it.anadida }.take(Musica.MAX_CANCIONES)
        if (mias.isEmpty()) {
            aviso("Aún no tienes canciones guardadas. Pega un enlace", largo = true)
            return
        }
        val textos = mias.map { c -> listOfNotNull(c.titulo, c.artista?.takeIf { it.isNotBlank() }).joinToString(" · ") }
        AlertDialog.Builder(act)
            .setTitle("Elegir de mis canciones")
            .setItems(textos.toTypedArray()) { _, i -> proponerCancion(mias[i]) }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun pedirEnlace() {
        val densidad = act.resources.displayMetrics.density
        // Aspecto de @style/Campo a mano, como en MenusMusica.pedirNombre (con el estilo por
        // constructor el campo no coge el foco).
        val caja = EditText(act).apply {
            setBackgroundResource(R.drawable.bg_campo)
            val h = (14 * densidad).toInt()
            val v = (12 * densidad).toInt()
            setPadding(h, v, h, v)
            textSize = 15f
            setTextColor(act.getColor(R.color.fc_texto))
            setHintTextColor(act.getColor(R.color.fc_texto_3))
            hint = "Enlace de YouTube o de Spotify"
            isSingleLine = true
        }
        val marco = FrameLayout(act).apply {
            val m = (20 * densidad).toInt()
            setPadding(m, (8 * densidad).toInt(), m, 0)
            addView(caja)
        }
        val dlg = AlertDialog.Builder(act)
            .setTitle("Proponer una canción")
            .setView(marco)
            .setPositiveButton("Proponer", null)
            .setNegativeButton("Cancelar", null)
            .create()
        dlg.setOnShowListener {
            // A mano para que un enlace que no vale no cierre el diálogo.
            dlg.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val ref = EnlaceMusica.de(caja.text.toString())
                if (ref == null) {
                    caja.error = "Pega el enlace de una canción de YouTube o de Spotify"
                    return@setOnClickListener
                }
                dlg.dismiss()
                proponerRef(ref)
            }
        }
        dlg.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
        dlg.show()
        caja.requestFocus()
    }

    /** También lo llama el menú ⋮ de una canción de tus playlists ("Proponer para la semana"). */
    fun proponerCancion(c: Cancion) = proponerRef(c.ref)

    private fun proponerRef(ref: RefCancion) {
        if (!verificada) { abrirVerificacion(); return }
        if (proponiendo) return
        proponiendo = true
        val uid = enganches.uidActivo()
        hilo.execute {
            // El enlace canónico, no el texto pegado: es lo único que el servidor tiene que leer.
            val r = enganches.api().proponer(ref.url())
            if (r is ApiMusica.R.Error) tirarToken(uid, VistaSemana.trasFallo(r, VistaSemana.Accion.PROPONER))
            act.runOnUiThread {
                proponiendo = false
                when (r) {
                    is ApiMusica.R.Ok -> {
                        aviso(
                            if (r.v.yaEstaba) "Esa canción ya estaba propuesta esta semana"
                            else "Propuesta enviada. " + propuestasQuedan(r.v.propuestasRestantes),
                            largo = true
                        )
                        refrescar(forzar = true)
                    }
                    is ApiMusica.R.Error -> fallo(r, VistaSemana.Accion.PROPONER)
                }
            }
        }
    }

    private fun propuestasQuedan(n: Int) = when (n) {
        0 -> "Has gastado tus propuestas de esta semana"
        1 -> "Te queda 1 propuesta esta semana"
        else -> "Te quedan $n propuestas esta semana"
    }

    private fun aviso(texto: String, largo: Boolean = false) {
        Toast.makeText(act, texto, if (largo) Toast.LENGTH_LONG else Toast.LENGTH_SHORT).show()
    }

    private companion object {
        /** Entre carga y carga al volver a la portada: el servidor cachea 60 s sin token y esto no hace falta más a menudo. */
        const val REFRESCO_MS = 30_000L

        /** Un solo hilo para hablar con el servidor desde la portada: votos y cargas en orden. */
        val hilo = Executors.newSingleThreadExecutor()
    }
}
