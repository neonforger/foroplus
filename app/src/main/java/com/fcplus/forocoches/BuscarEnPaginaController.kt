package com.fcplus.forocoches

import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.text.style.BackgroundColorSpan
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.TextView
import androidx.core.view.doOnNextLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

/**
 * Lo encontrado por la lupa dentro del texto de un mensaje. [actual] es el que estás mirando,
 * que va en un color más fuerte. Lo pone [PostAdapter]; lo busca [PostAdapter.dondeEstaLaActual].
 */
class MarcaBusqueda(val actual: Boolean, color: Int) : BackgroundColorSpan(color)

/**
 * La barra de la lupa del hilo: buscar en la página cargada, con "3 de 7" y flechas, como el
 * "Buscar en la página" del navegador. Qué se encuentra y en qué orden lo decide
 * [BuscarEnPagina]; el resaltado lo pinta [PostAdapter].
 *
 * La barra y su raya se encienden y apagan juntas y SOLO desde [pintarBarra] (misma lección que
 * la barra de páginas: media señal se lee como adorno).
 */
class BuscarEnPaginaController(
    private val barra: View,
    private val raya: View,
    private val adapter: PostAdapter,
    private val lista: RecyclerView,
    /** Altura de pantalla donde empieza lo que tapa la lista por abajo (las barras de lectura). */
    private val limiteInferior: () -> Int,
    /** Esconde la cabecera y las barras de abajo: con el teclado fuera no queda sitio para leer. */
    private val hacerSitio: () -> Unit,
    /** Al cerrar, las barras de abajo vuelven (la de responder la primera). */
    private val devolverBarras: () -> Unit
) {
    private val campo: EditText = barra.findViewById(R.id.busca_texto)
    private val cuenta: TextView = barra.findViewById(R.id.busca_cuenta)
    private val handler = Handler(Looper.getMainLooper())

    var abierta = false
        private set

    private var sitios: List<BuscarEnPagina.Sitio> = emptyList()
    private var actual = -1

    /** Se busca al dejar de teclear un momento, no a cada letra. */
    private val buscarAhora = Runnable { rehacer() }

    init {
        campo.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (!abierta) return
                handler.removeCallbacks(buscarAhora)
                handler.postDelayed(buscarAhora, ESPERA)
            }
        })
        // "Buscar" en el teclado: ya has escrito, ahora quieres leer. Se esconde el teclado y,
        // si aún no había ninguna elegida, se va a la primera.
        campo.setOnEditorActionListener { _, accion, _ ->
            if (accion != EditorInfo.IME_ACTION_SEARCH) return@setOnEditorActionListener false
            handler.removeCallbacks(buscarAhora)
            if (BuscarEnPagina.preparar(campo.text.toString()) != adapter.busqueda) rehacer()
            else if (actual < 0) ir(BuscarEnPagina.siguiente(actual, sitios.size))
            esconderTeclado()
            true
        }
        barra.findViewById<View>(R.id.busca_siguiente).setOnClickListener {
            ir(BuscarEnPagina.siguiente(actual, sitios.size))
        }
        barra.findViewById<View>(R.id.busca_anterior).setOnClickListener {
            ir(BuscarEnPagina.anterior(actual, sitios.size))
        }
        barra.findViewById<View>(R.id.busca_cerrar).setOnClickListener { cerrar() }
        // La lista cambia de alto cuando sale o se va el teclado: lo encontrado puede quedar
        // tapado, así que se vuelve a colocar (si ya se ve, afinar no mueve nada).
        lista.addOnLayoutChangeListener { _, _, arriba, _, abajo, _, antesArriba, _, antesAbajo ->
            if (abierta && actual >= 0 && abajo - arriba != antesAbajo - antesArriba) {
                val i = actual
                sitios.getOrNull(i)?.let { s -> lista.post { afinar(s.mensaje, i) } }
            }
        }
    }

    fun abrir() {
        abierta = true
        pintarBarra()
        hacerSitio()
        campo.requestFocus()
        campo.selectAll()
        // En el frame siguiente: pedir el teclado justo al poner la vista VISIBLE no hace nada
        // (gotcha de Android).
        campo.post {
            val imm = campo.context.getSystemService(InputMethodManager::class.java)
            imm?.showSoftInput(campo, InputMethodManager.SHOW_IMPLICIT)
        }
        if (campo.text.isNotBlank()) rehacer()
    }

    /** Cierra la lupa y quita el resaltado. Lo que habías escrito se queda para la próxima vez. */
    fun cerrar() {
        if (!abierta) return
        abierta = false
        handler.removeCallbacks(buscarAhora)
        esconderTeclado()
        campo.clearFocus()
        sitios = emptyList()
        actual = -1
        adapter.buscar("")
        pintarBarra()
        devolverBarras()
    }

    /**
     * Ha llegado otra página (o la misma recargada): se vuelve a buscar en ella. Con [saltar],
     * a la primera, que es para lo que se cambia de página con la lupa abierta; sin él (la
     * carga ya te lleva a un mensaje concreto) solo se marca y se cuenta.
     */
    fun paginaNueva(saltar: Boolean) {
        if (!abierta) return
        handler.removeCallbacks(buscarAhora)
        // La página nueva llega con todo a la vista (onThreadJson): se vuelve a hacer sitio.
        hacerSitio()
        // Desde arriba: la lista aún tiene medidas las posiciones de la página anterior.
        rehacer(leyendo = 0, saltar = saltar)
    }

    private fun rehacer(leyendo: Int? = null, saltar: Boolean = true) {
        val consulta = BuscarEnPagina.preparar(campo.text.toString())
        adapter.buscar(consulta)
        sitios = BuscarEnPagina.sitios(adapter.contarCoincidencias())
        actual = -1
        val lm = lista.layoutManager as? LinearLayoutManager
        val desde = leyendo ?: (lm?.findFirstVisibleItemPosition() ?: 0).coerceAtLeast(0)
        val primera = BuscarEnPagina.primeraDesde(sitios, desde)
        if (saltar && primera >= 0) ir(primera) else pintarCuenta()
    }

    private fun ir(i: Int) {
        val sitio = sitios.getOrNull(i) ?: return
        actual = i
        pintarCuenta()
        adapter.marcarActual(adapter.pidAt(sitio.mensaje), sitio.n)
        val lm = lista.layoutManager as LinearLayoutManager
        // Si el mensaje no está en pantalla se trae arriba; luego, ya pintado, se afina para que
        // el renglón encontrado quede a la vista (un mensaje largo no cabe entero).
        if (lm.findViewByPosition(sitio.mensaje) == null) lm.scrollToPositionWithOffset(sitio.mensaje, 0)
        lista.doOnNextLayout { lista.post { afinar(sitio.mensaje, i) } }
        lista.requestLayout()
    }

    /** Desplaza lo justo para que el renglón de la coincidencia quede en el tercio de arriba. */
    private fun afinar(pos: Int, i: Int) {
        if (!abierta || actual != i) return
        val h = lista.findViewHolderForAdapterPosition(pos) as? PostAdapter.Holder ?: return
        val (tv, y) = adapter.dondeEstaLaActual(h) ?: return
        val donde = IntArray(2)
        tv.getLocationOnScreen(donde)
        val renglon = donde[1] + y
        val visible = Rect()
        if (!lista.getGlobalVisibleRect(visible)) return
        val arriba = visible.top
        val abajo = minOf(visible.bottom, limiteInferior())
        val margen = tv.lineHeight * 2
        if (renglon in (arriba + margen)..(abajo - margen)) return
        lista.scrollBy(0, renglon - (arriba + (abajo - arriba) / 3))
    }

    private fun pintarCuenta() {
        val vacia = BuscarEnPagina.preparar(campo.text.toString()).isEmpty()
        cuenta.text = BuscarEnPagina.contador(actual, sitios.size, vacia)
        val hay = sitios.isNotEmpty()
        barra.findViewById<View>(R.id.busca_anterior).isEnabled = hay
        barra.findViewById<View>(R.id.busca_siguiente).isEnabled = hay
        barra.findViewById<View>(R.id.busca_anterior).alpha = if (hay) 1f else 0.35f
        barra.findViewById<View>(R.id.busca_siguiente).alpha = if (hay) 1f else 0.35f
    }

    private fun pintarBarra() {
        val v = if (abierta) View.VISIBLE else View.GONE
        barra.visibility = v
        raya.visibility = v
        pintarCuenta()
    }

    private fun esconderTeclado() {
        val imm = campo.context.getSystemService(InputMethodManager::class.java)
        imm?.hideSoftInputFromWindow(campo.windowToken, 0)
    }

    private companion object {
        const val ESPERA = 250L
    }
}
