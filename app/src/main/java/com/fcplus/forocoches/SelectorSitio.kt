package com.fcplus.forocoches

import android.app.Dialog
import android.content.Context
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat

/**
 * Selector de sitio a pantalla completa: se abre tocando el título de la lista nativa. Busca,
 * enseña "EN TU BARRA" y "TODOS LOS SUBFOROS" y avisa de la elección; quién navega es
 * MainActivity. Qué filas salen lo decide [ListaSitios] (puro, con tests).
 */
object SelectorSitio {

    fun mostrar(
        ctx: Context,
        enBarra: List<Sitio>,
        conocidos: List<Sitio>,
        actual: Int,
        alElegir: (Int) -> Unit,
    ) {
        val d = Dialog(ctx, R.style.FC_PantallaCompleta)
        val v = LayoutInflater.from(d.context).inflate(R.layout.dialog_selector_sitio, null)
        val lista = v.findViewById<LinearLayout>(R.id.sel_lista)
        val vacio = v.findViewById<TextView>(R.id.sel_vacio)
        val buscar = v.findViewById<EditText>(R.id.sel_buscar)
        val rojo = ContextCompat.getColor(ctx, R.color.fc_rojo)

        fun pintar(q: String) {
            lista.removeAllViews()
            val secciones = ListaSitios.secciones(enBarra, conocidos, actual, q)
            vacio.visibility = if (secciones.isEmpty()) View.VISIBLE else View.GONE
            for (s in secciones) {
                val cab = LayoutInflater.from(d.context).inflate(R.layout.item_selector_seccion, lista, false) as TextView
                cab.text = s.titulo
                ViewCompat.setAccessibilityHeading(cab, true)
                lista.addView(cab)
                for (f in s.filas) {
                    val fila = LayoutInflater.from(d.context).inflate(R.layout.item_selector_sitio, lista, false) as Button
                    fila.text = if (f.actual) "${f.nombre}  ✓" else f.nombre
                    if (f.actual) {
                        fila.setTextColor(rojo)
                        fila.contentDescription = "${f.nombre}, sitio actual"
                    }
                    fila.setOnClickListener { d.dismiss(); alElegir(f.clave) }
                    lista.addView(fila)
                }
            }
        }
        pintar("")
        buscar.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) { pintar(s?.toString().orEmpty()) }
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
        })
        v.findViewById<View>(R.id.sel_cerrar).setOnClickListener { d.dismiss() }

        d.setContentView(v, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        val w = d.window
        if (w != null) {
            // A pantalla completa y sin recorte: los márgenes de las barras del sistema y del
            // teclado se aplican como relleno de la raíz.
            WindowCompat.setDecorFitsSystemWindows(w, false)
            w.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            w.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
            val oscuro = (ctx.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
                android.content.res.Configuration.UI_MODE_NIGHT_YES
            WindowCompat.getInsetsController(w, v).isAppearanceLightStatusBars = !oscuro
            WindowCompat.getInsetsController(w, v).isAppearanceLightNavigationBars = !oscuro
        }
        ViewCompat.setOnApplyWindowInsetsListener(v) { vista, ins ->
            val b = ins.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime())
            vista.setPadding(b.left, b.top, b.right, b.bottom)
            WindowInsetsCompat.CONSUMED
        }
        d.setOnDismissListener {
            (ctx.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager)
                ?.hideSoftInputFromWindow(buscar.windowToken, 0)
        }
        d.show()
    }
}
