package com.fcplus.forocoches

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * La hoja "Añadir canciones" (A10): se pega un enlace, varios o una playlist compartida y se
 * guarda. Las decisiones están en [AnadirMusica]; esto solo pinta y reparte el trabajo.
 *
 * **Hilos.** Nada de red en el hilo de la interfaz: los `spotify.link` y los datos de cada
 * canción se piden en un pool de 4 hilos ([BuscadorMusica.datos] bloquea). Y el cerrojo de
 * [AlmacenMusica] (compartido con quien escriba en segundo plano) solo se toma en cada llamada
 * al almacén, nunca a través de una petición: así la portada, que lee `musica.json` en el hilo de
 * la interfaz, como mucho espera a una escritura de milisegundos. Leer las playlists al abrir y
 * guardar también se hacen fuera del hilo de la interfaz.
 *
 * @param alGuardar se llama en el hilo de la interfaz con el mensaje a enseñar; el controlador
 *   repinta la portada.
 */
class HojaAnadirMusica(
    private val act: AppCompatActivity,
    private val almacen: AlmacenMusica,
    private val alGuardar: (String) -> Unit
) {
    private val ui = Handler(Looper.getMainLooper())
    private var pool: ExecutorService? = null
    private var dialog: BottomSheetDialog? = null

    /** Cada análisis nuevo sube esto; lo que llega de uno viejo se descarta. */
    private var generacion = 0
    private var analizar: Runnable? = null

    // Vistas.
    private lateinit var texto: EditText
    private lateinit var estado: TextView
    private lateinit var una: View
    private lateinit var cancion: EditText
    private lateinit var artista: EditText
    private lateinit var listas: LinearLayout
    private lateinit var nuevaFila: View
    private lateinit var nueva: EditText
    private lateinit var varias: View
    private lateinit var variasTitulo: TextView
    private lateinit var nombreLista: EditText
    private lateinit var progreso: TextView
    private lateinit var error: TextView
    private lateinit var guardar: TextView

    // Estado del análisis actual.
    private var plan: PlanAnadir? = null
    private var refs: List<RefCancion> = emptyList()
    private val datos = HashMap<String, Cancion?>() // solo se toca desde el hilo de la interfaz
    private var pendientes = 0
    private var guardando = false

    // Playlists existentes (cargadas en segundo plano) y sus casillas. id == null es "Mis favoritas" aún sin crear.
    private class Destino(val id: String?, val nombre: String, val casilla: CheckBox)
    private val destinos = ArrayList<Destino>()
    private var destinosCargados = false

    /**
     * @param portapapeles lo copiado, leído por quien abre la hoja con la actividad en primer plano
     *   (en Android 10+ el portapapeles solo se lee con foco: dentro de la hoja recién mostrada
     *   podía devolver nada). Se ofrece si trae canciones.
     */
    fun mostrar(inicial: String?, portapapeles: String? = null) {
        val vista = LayoutInflater.from(act).inflate(R.layout.hoja_anadir_musica, null)
        val sheet = BottomSheetDialog(act)
        sheet.setContentView(vista)
        // Sin esto el botón queda bajo la barra de navegación (gotcha BottomSheet).
        val base = vista.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(vista) { v, insets ->
            val barras = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(v.paddingLeft, v.paddingTop, v.paddingRight, base + barras.bottom)
            insets
        }
        sheet.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        sheet.behavior.skipCollapsed = true
        sheet.behavior.state = BottomSheetBehavior.STATE_EXPANDED

        texto = vista.findViewById(R.id.anadir_texto)
        estado = vista.findViewById(R.id.anadir_estado)
        una = vista.findViewById(R.id.anadir_una)
        cancion = vista.findViewById(R.id.anadir_cancion)
        artista = vista.findViewById(R.id.anadir_artista)
        listas = vista.findViewById(R.id.anadir_playlists)
        nuevaFila = vista.findViewById(R.id.anadir_nueva_fila)
        nueva = vista.findViewById(R.id.anadir_nueva)
        varias = vista.findViewById(R.id.anadir_varias)
        variasTitulo = vista.findViewById(R.id.anadir_varias_titulo)
        nombreLista = vista.findViewById(R.id.anadir_nombre_lista)
        progreso = vista.findViewById(R.id.anadir_progreso)
        error = vista.findViewById(R.id.anadir_error)
        guardar = vista.findViewById(R.id.anadir_guardar)

        pool = Executors.newFixedThreadPool(MAX_A_LA_VEZ)
        dialog = sheet
        sheet.setOnDismissListener {
            generacion++
            analizar?.let { ui.removeCallbacks(it) }
            pool?.shutdownNow()
            pool = null
            dialog = null
        }

        texto.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) {
                // Pegar es un golpe, escribir son muchos: se espera a que pare para no pedir en cada letra.
                analizar?.let { ui.removeCallbacks(it) }
                val r = Runnable { analizarTexto(s?.toString().orEmpty()) }
                analizar = r
                ui.postDelayed(r, 350)
            }
        })
        nuevaFila.setOnClickListener {
            nuevaFila.visibility = View.GONE
            nueva.visibility = View.VISIBLE
            nueva.requestFocus()
            nueva.post { teclado(nueva) }
            actualizarGuardar()
        }
        val vigila = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) { actualizarGuardar() }
        }
        cancion.addTextChangedListener(vigila)
        nueva.addTextChangedListener(vigila)
        nombreLista.addTextChangedListener(vigila)
        guardar.setOnClickListener { guardarTodo() }

        cargarPlaylists()
        sheet.show()

        if (!inicial.isNullOrBlank()) {
            texto.setText(inicial)
        } else {
            texto.requestFocus()
            // showSoftInput justo tras mostrar no hace nada (gotcha): en el frame siguiente.
            texto.post {
                ofrecerPortapapeles(vista, portapapeles)
                teclado(texto)
            }
        }
    }

    private fun teclado(v: View) {
        val imm = act.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showSoftInput(v, InputMethodManager.SHOW_IMPLICIT)
    }

    /** Si lo copiado trae una canción (o varias), un toque para pegarlo. */
    private fun ofrecerPortapapeles(vista: View, copiado: String?) {
        if (copiado.isNullOrBlank() || texto.text.isNotEmpty()) return
        val p = AnadirMusica.plan(copiado)
        val n = p.refs.size + p.cortos.size
        if (n == 0) return
        val oferta = vista.findViewById<TextView>(R.id.anadir_portapapeles)
        oferta.text = if (n == 1) "Pegar la canción que has copiado" else "Pegar las $n canciones que has copiado"
        oferta.visibility = View.VISIBLE
        oferta.setOnClickListener {
            oferta.visibility = View.GONE
            texto.setText(copiado)
            texto.setSelection(texto.text.length)
        }
    }

    // ── Playlists existentes ─────────────────────────────────────────────────

    private fun cargarPlaylists() {
        val p = pool ?: return
        p.execute {
            val existentes = almacen.estado().playlists
            enUi(null) { pintarDestinos(existentes) }
        }
    }

    private fun pintarDestinos(existentes: List<Playlist>) {
        destinos.clear()
        listas.removeAllViews()
        val filas = ArrayList<Triple<String?, String, Int>>()
        val fav = existentes.firstOrNull { it.nombre == Musica.FAVORITAS }
        // "Mis favoritas" siempre a la vista y marcada: es donde va lo que se guarda sin pensar.
        if (fav == null) filas.add(Triple(null, Musica.FAVORITAS, 0))
        existentes.sortedBy { if (it === fav) 0 else 1 }.forEach { filas.add(Triple(it.id, it.nombre, it.claves.size)) }
        for ((id, nombre, n) in filas) {
            val c = CheckBox(act)
            c.text = "$nombre · ${MusicaController.cuantas(n)}"
            c.textSize = 15f
            c.minHeight = (48 * act.resources.displayMetrics.density).toInt()
            c.setTextColor(act.getColor(R.color.fc_texto))
            c.isChecked = nombre == Musica.FAVORITAS
            c.setOnCheckedChangeListener { _, _ -> actualizarGuardar() }
            listas.addView(c)
            destinos.add(Destino(id, nombre, c))
        }
        destinosCargados = true
        actualizarGuardar()
    }

    // ── Análisis del texto ───────────────────────────────────────────────────

    private fun analizarTexto(t: String) {
        val gen = ++generacion
        datos.clear()
        pendientes = 0
        mostrarSolo(null)
        error.visibility = View.GONE
        val p = AnadirMusica.plan(t)
        plan = p
        refs = p.refs
        if (t.isBlank()) { estado.visibility = View.GONE; actualizarGuardar(); return }
        if (p.cortos.isNotEmpty()) {
            avisar("Buscando la canción de ${if (p.cortos.size == 1) "ese enlace" else "esos enlaces"} de Spotify…")
            val pl = pool ?: return
            pl.execute {
                // Cada corto exige red (resolverCorto bloquea); aquí, fuera de la interfaz.
                val resueltas = p.cortos.map { BuscadorMusica.resolverCorto(it) }
                enUi(gen) {
                    refs = (p.refs + resueltas.filterNotNull()).distinct()
                    val fallidos = resueltas.count { it == null }
                    empezar(gen, fallidos)
                }
            }
        } else empezar(gen, 0)
    }

    private fun empezar(gen: Int, cortosFallidos: Int) {
        val p = plan ?: return
        val notaCortos = if (cortosFallidos > 0)
            "No he podido abrir ${if (cortosFallidos == 1) "un enlace corto" else "$cortosFallidos enlaces cortos"} de Spotify. Pega el enlace largo (open.spotify.com)."
        else null
        // Más de las que caben en una playlist: se dice cuántas se han dejado fuera (AnadirMusica.plan).
        val nota = listOfNotNull(notaCortos, AnadirMusica.avisoDescartadas(p.descartadas))
            .joinToString("\n").ifEmpty { null }
        when {
            refs.isEmpty() -> {
                avisar(nota ?: "No he encontrado ninguna canción de YouTube o Spotify en ese texto.")
                actualizarGuardar()
            }
            refs.size == 1 -> empezarUna(gen, refs[0], p, nota)
            else -> empezarVarias(gen, p, nota)
        }
    }

    private fun empezarUna(gen: Int, ref: RefCancion, p: PlanAnadir, nota: String?) {
        mostrarSolo(una)
        if (nota != null) avisar(nota) else estado.visibility = View.GONE
        val delTexto = p.nombres[ref.clave]
        cancion.setText(delTexto?.first.orEmpty())
        artista.setText(delTexto?.second.orEmpty())
        pendientes = 1
        avisar("Leyendo la canción…")
        pool?.execute {
            val c = BuscadorMusica.datos(ref)
            enUi(gen) {
                pendientes = 0
                datos[ref.clave] = c
                if (c == null) {
                    avisar("No he podido leer la canción (¿sin conexión?). Escribe el título y el artista a mano.")
                } else {
                    estado.visibility = View.GONE
                    // Lo que ya haya escrito el usuario (o venga del texto) no se pisa.
                    if (cancion.text.isBlank()) cancion.setText(c.titulo)
                    if (artista.text.isBlank()) artista.setText(c.artista.orEmpty())
                }
                actualizarGuardar()
            }
        }
        actualizarGuardar()
    }

    private fun empezarVarias(gen: Int, p: PlanAnadir, nota: String?) {
        mostrarSolo(varias)
        if (nota != null) avisar(nota) else estado.visibility = View.GONE
        variasTitulo.text = "Se han encontrado ${refs.size} canciones"
        if (nombreLista.text.isBlank()) nombreLista.setText(p.nombre.orEmpty())
        pendientes = refs.size
        pintarProgreso()
        for (ref in refs) {
            pool?.execute {
                val c = BuscadorMusica.datos(ref)
                enUi(gen) {
                    datos[ref.clave] = c
                    pendientes--
                    pintarProgreso()
                    actualizarGuardar()
                }
            }
        }
        actualizarGuardar()
    }

    private fun pintarProgreso() {
        val total = refs.size
        progreso.text = if (pendientes > 0) "Leyendo las canciones… ${total - pendientes}/$total"
        else "Listo: se guardarán en una playlist nueva."
    }

    private fun mostrarSolo(v: View?) {
        una.visibility = if (v === una) View.VISIBLE else View.GONE
        varias.visibility = if (v === varias) View.VISIBLE else View.GONE
    }

    private fun avisar(msg: String) {
        estado.text = msg
        estado.visibility = View.VISIBLE
    }

    // ── Guardar ──────────────────────────────────────────────────────────────

    private fun actualizarGuardar() {
        val ua = una.visibility == View.VISIBLE
        val va = varias.visibility == View.VISIBLE
        guardar.visibility = if (ua || va) View.VISIBLE else View.GONE
        guardar.text = if (va) "Guardar todas" else "Guardar"
        val listo = when {
            guardando -> false
            ua -> destinosCargados && pendientes == 0 && cancion.text.isNotBlank() &&
                (destinos.any { it.casilla.isChecked } || nueva.text.isNotBlank())
            va -> pendientes == 0 && refs.isNotEmpty() && AnadirMusica.nombreParaGuardar(nombreLista.text.toString()) != null
            else -> false
        }
        guardar.isEnabled = listo
        guardar.alpha = if (listo) 1f else 0.45f
    }

    /** La canción lista para guardar de una de la playlist pegada; con título provisional si ni la red ni el texto la nombraron (ver [AnadirMusica.combinar]). */
    private fun cancionDe(ref: RefCancion): Cancion {
        val n = plan?.nombres?.get(ref.clave)
        return AnadirMusica.combinar(ref, datos[ref.clave], n?.first, n?.second, System.currentTimeMillis())
    }

    private fun guardarTodo() {
        if (guardando) return
        guardando = true
        actualizarGuardar()
        error.visibility = View.GONE
        if (una.visibility == View.VISIBLE) guardarUna() else guardarVarias()
    }

    private fun guardarUna() {
        val ref = refs.firstOrNull() ?: run { fallo("No hay canción que guardar."); return }
        val c = AnadirMusica.editar(ref, datos[ref.clave], cancion.text.toString(), artista.text.toString(), System.currentTimeMillis())
        if (c == null) { fallo("Falta el título de la canción."); return }
        val marcadas = destinos.filter { it.casilla.isChecked }
        val nombreNueva = nueva.text.toString().takeIf { nueva.visibility == View.VISIBLE }?.let { AnadirMusica.nombreParaGuardar(it) }
        // El almacén se toca en un hilo suelto, nunca en el de la interfaz.
        Thread {
            val ids = marcadas.mapNotNull { it.id }.toMutableList()
            var nombres = marcadas.map { it.nombre }
            var ok = true
            var motivo = "No se ha podido guardar (¿una playlist llena? El tope es ${Musica.MAX_CANCIONES} canciones)."
            if (nombreNueva != null) {
                val p = almacen.crearPlaylist(nombreNueva)
                if (p == null) { ok = false; motivo = "No se ha podido crear la playlist (el tope es ${Musica.MAX_PLAYLISTS})." }
                else { ids.add(p.id); nombres = nombres + p.nombre }
            }
            if (ok && marcadas.any { it.id == null }) {
                // "Mis favoritas" aún no existe: guardar con la lista vacía la crea.
                ok = almacen.guardar(c, emptyList())
            }
            if (ok && ids.isNotEmpty()) ok = almacen.guardar(c, ids)
            alTerminarGuardado {
                if (ok) terminar("Guardada en ${nombres.joinToString(", ").ifEmpty { Musica.FAVORITAS }}")
                else fallo(motivo)
            }
        }.start()
    }

    private fun guardarVarias() {
        val nombre = AnadirMusica.nombreParaGuardar(nombreLista.text.toString())
        if (nombre == null) { fallo("Ponle nombre a la playlist."); return }
        val todas = refs.map { cancionDe(it) }
        val sinLeer = refs.count { AnadirMusica.sinDatos(datos[it.clave], plan?.nombres?.get(it.clave)?.first, null) }
        Thread {
            val p = almacen.importar(nombre, todas)
            alTerminarGuardado {
                if (p == null) fallo("No se ha podido crear la playlist (el tope es ${Musica.MAX_PLAYLISTS}).")
                else {
                    terminar("Playlist «${p.nombre}» creada con ${p.claves.size} canciones.${AnadirMusica.avisoSinDatos(sinLeer)}")
                }
            }
        }.start()
    }

    private fun fallo(msg: String) {
        guardando = false
        error.text = msg
        error.visibility = View.VISIBLE
        actualizarGuardar()
    }

    private fun terminar(msg: String) {
        guardando = false
        dialog?.dismiss()
        alGuardar(msg) // siempre: es lo que repinta la portada, haya o no hoja
    }

    /**
     * El final de un guardado va al hilo de la interfaz SIEMPRE: no depende de que el texto siga
     * igual (generacion) ni de que la hoja siga abierta, o se perdía el resultado y `guardando` se
     * quedaba puesto. Con la hoja ya cerrada solo se cuenta lo guardado (toast y portada).
     */
    private fun alTerminarGuardado(f: () -> Unit) {
        act.runOnUiThread(f)
    }

    /** Ejecuta [f] en el hilo de la interfaz si la hoja sigue abierta y (con [gen]) el análisis no ha cambiado. */
    private fun enUi(gen: Int?, f: () -> Unit) {
        act.runOnUiThread {
            if (dialog == null) return@runOnUiThread
            if (gen != null && gen != generacion) return@runOnUiThread
            f()
        }
    }

    private companion object {
        /** Peticiones de datos a la vez (spec: máximo 4). */
        const val MAX_A_LA_VEZ = 4
    }
}
