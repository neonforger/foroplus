package com.fcplus.forocoches

import android.app.Dialog
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat

/**
 * "Compartir una canción" (A6): pantalla completa donde se busca una canción (o se pega un enlace
 * de YouTube o Spotify), se escribe por qué y se publica en el feed. Las reglas del texto son las
 * de [VistaFeed]; los errores del servidor salen por [VistaFeed.textoError].
 *
 * - La búsqueda ([BuscadorDeezer]) espera [ESPERA_MS] desde la última tecla, va fuera del hilo de
 *   la interfaz y solo vale la respuesta de la ÚLTIMA consulta: cada tecla sube [generacion] y lo
 *   que llega con otra generación se tira (una respuesta lenta no pisa a una más nueva).
 * - Un enlace de [EnlaceMusica] no se busca: se elige al instante y se publica como
 *   [OrigenCancion.Enlace].
 * - El tope diario no se mira antes ni se cuenta: la hoja se abre siempre y solo el 429 `limite`
 *   del servidor lo explica (dueño: los topes no se anuncian).
 * - El servidor contesta en cuanto guarda la publicación (201), sin esperar a la conversión a
 *   Spotify/YouTube: aquí no hay nada que esperar. La nueva sale arriba del feed al momento
 *   ([alPublicar]); a los demás les sale cuando se convierte o pasados 8 s. Cualquier error
 *   devuelve el botón a "Compartir", nunca se queda en "Publicando…".
 *
 * Los parámetros con valor por defecto son las costuras de los tests (red, hilo, espera).
 *
 * @param alPublicar en el hilo de la interfaz, con la publicación ya creada; la hoja se cierra después.
 */
class HojaCompartirCancion(
    private val act: AppCompatActivity,
    private val enganches: EnganchesMusica,
    private val alPublicar: (ItemFeed) -> Unit,
    private val api: () -> ApiMusica = { enganches.api() },
    private val buscar: (String) -> List<ResultadoDeezer> = { BuscadorDeezer.buscar(it) },
    private val segundoPlano: (() -> Unit) -> Unit = { Thread(it, "compartir-cancion").start() },
    private val esperar: (Long, () -> Unit) -> Unit = { ms, r -> Handler(Looper.getMainLooper()).postDelayed(r, ms) }
) {
    /** Lo elegido: de dónde sale la canción y cómo se enseña. */
    private class Eleccion(val origen: OrigenCancion, val titulo: String, val artista: String, val portada: String?)

    private var dialog: Dialog? = null

    /** La vista inflada (solo la usan los tests). */
    internal var vista: View? = null
        private set

    val abierta: Boolean get() = dialog != null

    private var eleccion: Eleccion? = null
    private var resultados: List<ResultadoDeezer> = emptyList()
    private var publicando = false

    /** Sube con cada tecla: lo que llega de otra generación (espera o respuesta) ya no vale. */
    private var generacion = 0

    private lateinit var buscarCampo: EditText
    private lateinit var lista: LinearLayout
    private lateinit var vacio: TextView
    private lateinit var panel: View
    private lateinit var motivo: EditText
    private lateinit var contador: TextView
    private lateinit var error: TextView
    private lateinit var boton: TextView

    fun mostrar(enlaceInicial: String? = null) {
        if (dialog != null) return
        // Una hoja reabierta empieza limpia: un "Publicando…" de la vez anterior no la deja bloqueada.
        publicando = false
        eleccion = null
        resultados = emptyList()
        val d = Dialog(act, R.style.FC_PantallaCompleta)
        val v = LayoutInflater.from(d.context).inflate(R.layout.hoja_compartir_cancion, null)
        vista = v
        buscarCampo = v.findViewById(R.id.compartir_buscar)
        lista = v.findViewById(R.id.compartir_lista)
        vacio = v.findViewById(R.id.compartir_vacio)
        panel = v.findViewById(R.id.compartir_panel)
        motivo = v.findViewById(R.id.compartir_motivo)
        contador = v.findViewById(R.id.compartir_contador)
        error = v.findViewById(R.id.compartir_error)
        boton = v.findViewById(R.id.compartir_boton)

        v.findViewById<View>(R.id.compartir_cerrar).setOnClickListener { cerrar() }
        buscarCampo.addTextChangedListener(alCambiar { alEscribirBusqueda(it) })
        motivo.addTextChangedListener(alCambiar { alEscribirMotivo() })
        boton.setOnClickListener { if (boton.isEnabled) publicar() }

        d.setContentView(v, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        ajustarVentana(d, v)
        d.setOnDismissListener {
            generacion++  // nada de lo que venga ya
            (act.getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as? InputMethodManager)
                ?.hideSoftInputFromWindow(buscarCampo.windowToken, 0)
            dialog = null
        }
        dialog = d
        actualizarMotivo()
        if (!enlaceInicial.isNullOrBlank()) buscarCampo.setText(enlaceInicial.trim())
        d.show()
        if (enlaceInicial.isNullOrBlank()) buscarCampo.requestFocus()
    }

    /** A pantalla completa y sin recorte: las barras del sistema y el teclado se aplican como relleno (como [SelectorSitio]). */
    private fun ajustarVentana(d: Dialog, v: View) {
        val w = d.window
        if (w != null) {
            WindowCompat.setDecorFitsSystemWindows(w, false)
            w.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            w.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
            val oscuro = (act.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
                android.content.res.Configuration.UI_MODE_NIGHT_YES
            WindowCompat.getInsetsController(w, v).isAppearanceLightStatusBars = !oscuro
            WindowCompat.getInsetsController(w, v).isAppearanceLightNavigationBars = !oscuro
        }
        ViewCompat.setOnApplyWindowInsetsListener(v) { vista, ins ->
            val b = ins.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime())
            vista.setPadding(b.left, b.top, b.right, b.bottom)
            WindowInsetsCompat.CONSUMED
        }
    }

    /** Cierra ya: [abierta] baja al momento (el aviso de cierre del Dialog llega después, por su Handler). */
    private fun cerrar() {
        val d = dialog ?: return
        dialog = null
        generacion++
        d.dismiss()
    }

    // ---- búsqueda ----

    private fun alEscribirBusqueda(texto: String) {
        val mia = ++generacion
        val enlace = enlaceDe(texto)
        if (enlace != null) {
            // Un enlace no se busca: se elige tal cual y el servidor lo resuelve.
            val fuente = if (EnlaceMusica.de(enlace)?.fuente == FuenteMusica.SPOTIFY) "Spotify" else "YouTube"
            elegir(Eleccion(OrigenCancion.Enlace(enlace), "Canción de $fuente", enlace, null))
            limpiarResultados()
            return
        }
        // Se ha cambiado un enlace por otra cosa: ya no hay canción elegida de él.
        if (eleccion?.origen is OrigenCancion.Enlace) elegir(null)
        // Tope de 100: lo mismo que acepta el buscador del servidor sin pasarse de largo.
        val q = texto.trim().take(MAX_CONSULTA).trim()
        if (q.length < MIN_CONSULTA) { limpiarResultados(); return }
        esperar(ESPERA_MS) { if (mia == generacion && abierta) buscarAhora(q, mia) }
    }

    private fun buscarAhora(q: String, mia: Int) {
        segundoPlano {
            val r = try { buscar(q) } catch (_: Exception) { emptyList() }
            act.runOnUiThread { if (mia == generacion && abierta) pintarResultados(r) }
        }
    }

    private fun limpiarResultados() {
        resultados = emptyList()
        lista.removeAllViews()
        vacio.visibility = View.GONE
    }

    private fun pintarResultados(r: List<ResultadoDeezer>) {
        resultados = r
        lista.removeAllViews()
        vacio.text = "No hemos encontrado nada. Prueba con otro título o pega un enlace de Spotify o YouTube."
        vacio.visibility = if (r.isEmpty()) View.VISIBLE else View.GONE
        val elegidaId = (eleccion?.origen as? OrigenCancion.Deezer)?.id
        for (c in r) {
            val fila = LayoutInflater.from(act).inflate(R.layout.item_compartir_resultado, lista, false)
            fila.findViewById<TextView>(R.id.res_titulo).text = c.titulo
            fila.findViewById<TextView>(R.id.res_artista).text = c.artista
            PortadasMusica.pintar(act, fila.findViewById<ImageView>(R.id.res_portada), c.portada)
            fila.findViewById<View>(R.id.res_marca).visibility = if (c.id == elegidaId) View.VISIBLE else View.INVISIBLE
            fila.contentDescription = "${c.titulo}, ${c.artista}" + if (c.id == elegidaId) ", elegida" else ""
            fila.setOnClickListener {
                elegir(Eleccion(OrigenCancion.Deezer(c.id), c.titulo, c.artista, c.portada))
                pintarResultados(resultados)  // la marca pasa a la nueva
            }
            lista.addView(fila)
        }
    }

    // ---- lo elegido y publicar ----

    private fun elegir(e: Eleccion?) {
        eleccion = e
        error.visibility = View.GONE
        if (e == null) { panel.visibility = View.GONE; actualizarBoton(); return }
        panel.visibility = View.VISIBLE
        vista!!.findViewById<TextView>(R.id.compartir_elegida_titulo).text = e.titulo
        vista!!.findViewById<TextView>(R.id.compartir_elegida_artista).text = e.artista
        PortadasMusica.pintar(act, vista!!.findViewById(R.id.compartir_elegida_portada), e.portada)
        // Borra el error de arriba, pero la frase puede seguir llevando un enlace: se explica otra vez.
        actualizarMotivo()
    }

    private fun alEscribirMotivo() {
        error.visibility = View.GONE
        actualizarMotivo()
    }

    /** El contador `n/100` y, si la frase lleva un enlace, por qué no vale (las reglas son las de [VistaFeed]). */
    private fun actualizarMotivo() {
        val n = motivo.text.toString().trim().length
        contador.text = "$n/${VistaFeed.TEXTO_MAX}"
        contador.setTextColor(act.getColor(if (n > VistaFeed.TEXTO_MAX) R.color.fc_rojo else R.color.fc_texto_2))
        if (VistaFeed.motivoTexto(motivo.text.toString()) == MotivoTexto.ENLACE) {
            error.text = "La frase no puede llevar enlaces."
            error.visibility = View.VISIBLE
        }
        actualizarBoton()
    }

    private fun actualizarBoton() {
        val vale = eleccion != null && !publicando && VistaFeed.motivoTexto(motivo.text.toString()) == null
        boton.isEnabled = vale
        boton.alpha = if (vale) 1f else 0.4f
    }

    private fun publicar() {
        val e = eleccion ?: return
        if (publicando || VistaFeed.motivoTexto(motivo.text.toString()) != null) return
        val texto = motivo.text.toString().trim()
        val uid = enganches.uidActivo()
        publicando = true
        boton.text = "Publicando…"
        error.visibility = View.GONE
        actualizarBoton()
        segundoPlano {
            val r = try { api().publicar(texto, e.origen) } catch (_: Exception) { ApiMusica.R.Error("red", 0, null) }
            if (r is ApiMusica.R.Error) try { enganches.tokens.trasError(uid, r) } catch (_: Exception) { }
            act.runOnUiThread { alTerminar(r) }
        }
    }

    private fun alTerminar(r: ApiMusica.R<ItemFeed>) {
        publicando = false
        boton.text = "Compartir"
        when (r) {
            // Aunque se haya cerrado la hoja mientras tanto, la publicación existe: el feed tiene que saberlo.
            is ApiMusica.R.Ok -> { alPublicar(r.v); cerrar() }
            is ApiMusica.R.Error -> {
                error.text = VistaFeed.textoError(r)
                error.visibility = View.VISIBLE
            }
        }
        actualizarBoton()
    }

    private fun alCambiar(f: (String) -> Unit) = object : TextWatcher {
        override fun afterTextChanged(s: Editable?) { f(s?.toString().orEmpty()) }
        override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
        override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
    }

    companion object {
        /** Desde la última tecla hasta buscar. */
        const val ESPERA_MS = 300L
        private const val MAX_CONSULTA = 100
        private const val MIN_CONSULTA = 2

        /** El primer trozo del texto que [EnlaceMusica] reconoce como canción de YouTube o Spotify. */
        fun enlaceDe(texto: String): String? =
            texto.trim().split(Regex("\\s+")).firstOrNull { EnlaceMusica.de(it) != null }
    }
}
