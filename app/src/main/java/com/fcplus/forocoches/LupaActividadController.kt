package com.fcplus.forocoches

import android.view.LayoutInflater
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout

/**
 * La barra de la lupa de "@x · mensajes" / "hilos suyos" / "participa" ([LupaActividad]).
 *
 * Se mete sola bajo la cabecera del panel ([contenedor], en la posición [indice]) para no tocar
 * el layout grande. Hay dos: una en el panel de avisos (mensajes) y otra en el listado (hilos).
 *
 * Dos estados, y los pinta SOLO [pintar]:
 *  - En la lista entera: el icono de la lupa se ve y la barra está cerrada hasta que lo tocas.
 *    Atrás o la ✕ la cierran sin más ([cerrarSiSoloCampo]).
 *  - En los resultados: la barra se queda abierta con lo buscado, para afinar; la ✕ vuelve a la
 *    lista entera ([alCerrarResultados], que es lo mismo que atrás).
 */
class LupaActividadController(
    contenedor: LinearLayout,
    indice: Int,
    /** El icono de la cabecera que abre la barra; null si quien llama gestiona el suyo. */
    private val icono: View?,
    private val alBuscar: (String) -> Unit,
    private val alCerrarResultados: () -> Unit
) {
    private val barra: View = LayoutInflater.from(contenedor.context)
        .inflate(R.layout.barra_lupa_actividad, contenedor, false)
    private val campo: EditText = barra.findViewById(R.id.lupa_actividad_texto)

    /** Hay algo que buscar aquí (la lupa sale en esta pantalla). */
    var disponible = false
        private set

    /** Lo que se ve es el resultado de una búsqueda de la lupa, no la lista entera. */
    var enResultados = false
        private set

    val abierta: Boolean get() = barra.visibility == View.VISIBLE

    private var claveActual = ""

    init {
        contenedor.addView(barra, indice)
        icono?.setOnClickListener { alternar() }
        campo.setOnEditorActionListener { _, accion, _ ->
            if (accion != EditorInfo.IME_ACTION_SEARCH) return@setOnEditorActionListener false
            esconderTeclado()
            alBuscar(campo.text.toString())
            true
        }
        barra.findViewById<View>(R.id.lupa_actividad_cerrar).setOnClickListener {
            if (enResultados) { esconderTeclado(); alCerrarResultados() } else cerrar()
        }
    }

    /**
     * Pone la lupa como toca en la pantalla que se acaba de enseñar.
     * @param disponible si no, ni icono ni barra.
     * @param resultados con lo que se buscó (abierta y rellena), o null para la lista entera
     *   (cerrada y vacía).
     * @param clave qué pantalla es (usuario y lista). Repintar la MISMA lista entera (llega un
     *   contador, se recarga) no cierra el campo que estás escribiendo.
     */
    fun pintar(disponible: Boolean, pista: String, resultados: String?, clave: String = "") {
        val misma = disponible && resultados == null && this.disponible && !enResultados &&
            clave == claveActual
        claveActual = clave
        this.disponible = disponible
        enResultados = disponible && resultados != null
        icono?.visibility = if (disponible) View.VISIBLE else View.GONE
        campo.hint = pista
        if (misma) return
        if (!disponible || resultados == null) {
            esconderTeclado()
            campo.setText("")
            barra.visibility = View.GONE
            return
        }
        campo.setText(resultados)
        campo.setSelection(campo.text.length)
        barra.visibility = View.VISIBLE
    }

    fun alternar() {
        if (!disponible) return
        if (abierta && !enResultados) cerrar() else abrir()
    }

    fun abrir() {
        if (!disponible) return
        barra.visibility = View.VISIBLE
        campo.requestFocus()
        campo.selectAll()
        // En el frame siguiente: pedir el teclado justo al poner la vista VISIBLE no hace nada
        // (gotcha de Android, el mismo que en la lupa del hilo).
        campo.post {
            campo.context.getSystemService(InputMethodManager::class.java)
                ?.showSoftInput(campo, InputMethodManager.SHOW_IMPLICIT)
        }
    }

    /** Atrás con el campo abierto sobre la lista entera: se cierra y no se navega. */
    fun cerrarSiSoloCampo(): Boolean {
        // isShown: la barra y todo lo que la contiene se ven (su panel está delante).
        if (!abierta || enResultados || !barra.isShown) return false
        cerrar()
        return true
    }

    private fun cerrar() {
        esconderTeclado()
        campo.setText("")
        campo.clearFocus()
        barra.visibility = View.GONE
    }

    private fun esconderTeclado() {
        campo.context.getSystemService(InputMethodManager::class.java)
            ?.hideSoftInputFromWindow(campo.windowToken, 0)
    }

    companion object {
        /**
         * La lupa del panel de avisos ("@x · mensajes"): pone su propio icono en la fila de la
         * cabecera ([cabecera] es el título; el icono va justo a su derecha) y la barra debajo.
         */
        fun bajoCabeceraConIcono(
            cabecera: View,
            descripcion: String,
            alBuscar: (String) -> Unit,
            alCerrarResultados: () -> Unit
        ): LupaActividadController {
            val fila = cabecera.parent as LinearLayout
            val panel = fila.parent as LinearLayout
            val ctx = fila.context
            val d = ctx.resources.displayMetrics.density
            val fondo = android.util.TypedValue().also {
                ctx.theme.resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, it, true)
            }.resourceId
            val icono = ImageView(ctx).apply {
                layoutParams = LinearLayout.LayoutParams((44 * d).toInt(), (44 * d).toInt())
                val p = (10 * d).toInt()
                setPadding(p, p, p, p)
                setImageResource(R.drawable.ic_search)
                setBackgroundResource(fondo)
                contentDescription = descripcion
                visibility = View.GONE
            }
            fila.addView(icono, fila.indexOfChild(cabecera) + 1)
            return LupaActividadController(panel, panel.indexOfChild(fila) + 1, icono, alBuscar, alCerrarResultados)
        }

        /** La lupa del listado: el icono ya está en la cabecera (lo gestiona quien llama). */
        fun bajoCabecera(
            cabecera: View,
            alBuscar: (String) -> Unit,
            alCerrarResultados: () -> Unit
        ): LupaActividadController {
            val fila = cabecera.parent as LinearLayout
            val panel = fila.parent as LinearLayout
            return LupaActividadController(panel, panel.indexOfChild(fila) + 1, null, alBuscar, alCerrarResultados)
        }
    }
}
