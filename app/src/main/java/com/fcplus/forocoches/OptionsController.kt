package com.fcplus.forocoches

import android.content.SharedPreferences
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.widget.SwitchCompat
import androidx.core.content.ContextCompat

/**
 * Controlador del panel de Opciones (nativo): tamaño de fuente, cuenta (avatar/perfil),
 * filtro de palabras y usuarios ignorados. La lógica de filtrado ya existe en los repos;
 * esto solo le pone UI de gestión.
 */
class OptionsController(
    private val panel: View,
    private val ignoreRepo: IgnoreListRepository,
    private val keywordRepo: KeywordRepository,
    private val prefs: SharedPreferences,
    private val onFontChanged: () -> Unit,
    /** El tamaño de los títulos del listado ha cambiado: hay que repintar las filas. */
    private val onTitleFontChanged: () -> Unit = {},
    private val onListsChanged: () -> Unit,
    /** El tema ha cambiado: quien nos crea decide qué hacer (recrear la pantalla). */
    private val onTemaChanged: () -> Unit = {},
    /** Hilos que el usuario mandó callar, y cómo devolverles la voz. */
    private val hilosIgnorados: () -> List<HilosIgnorados.Hilo> = { emptyList() },
    private val onDesignorarHilo: (String) -> Unit = {},
    /**
     * Escribe el cambio en la lista de ignorados de ForoCoches (accion: "add" / "remove").
     * Devuelve `true` si se ha encargado —hay sesión y la petición ha salido—, y entonces
     * este panel NO toca el almacén local: espera a que el servidor conteste y se repinta
     * con lo que diga FC. Si devuelve `false` (sin sesión) se hace la edición local de
     * siempre, que sin sesión no la sincroniza nadie y por tanto no la pisa nadie.
     */
    private val onIgnoreWrite: (String, String) -> Boolean = { _, _ -> false },
    /**
     * Los subforos que existen (fid → nombre), para poder elegir los del Popurrí. Salen del
     * índice REAL del foro, así que si FC añade o quita subforos esto se adapta solo.
     */
    private val subforos: () -> List<Pair<Int, String>> = { emptyList() },
    /** La selección del Popurrí ha cambiado: hay que rehacer las pestañas y la lista. */
    private val onPopurriChanged: () -> Unit = {},
    /** Se ha encendido o apagado el "último que escribe" del listado: repintar las filas. */
    private val onUltimoChanged: () -> Unit = {},
    /** Se ha tocado la sección +18 o el ocultar +18/+16 de las listas. */
    private val onMas18Changed: () -> Unit = {}
) {
    companion object {
        const val PREF_FONT_IDX = "post_font_idx"
        private val SP = floatArrayOf(13f, 15f, 18f)   // pequeña / normal / grande
        fun fontSp(prefs: SharedPreferences): Float =
            SP[prefs.getInt(PREF_FONT_IDX, 1).coerceIn(0, 2)]

        /**
         * Tamaño de los títulos del listado, APARTE del de los mensajes (lo pidió Márquez): se
         * puede querer leer los mensajes grandes y ver más hilos de un vistazo, o al revés.
         * "Normal" es el 15sp que tenían desde siempre en `item_thread`.
         */
        const val PREF_TITLE_FONT_IDX = "title_font_idx"
        fun tituloSp(prefs: SharedPreferences): Float =
            SP[prefs.getInt(PREF_TITLE_FONT_IDX, 1).coerceIn(0, 2)]

        /**
         * "Último que escribe" bajo la hora de cada fila del listado. Encendido por defecto (lo
         * pidió el grupo), apagable porque cuesta una petición por hilo. Ver [UltimosPosteadores].
         */
        const val PREF_ULTIMO = "ultimo_en_lista"
        fun ultimoEnLista(prefs: SharedPreferences): Boolean = prefs.getBoolean(PREF_ULTIMO, true)

        const val PREF_MAS18 = "mas18_seccion"
        const val PREF_OCULTAR_MAS18 = "ocultar_mas18_listas"
        /** Apagada por defecto: la sección +18 es opcional (spec, "2. La app"). */
        fun mas18Activa(prefs: SharedPreferences): Boolean = prefs.getBoolean(PREF_MAS18, false)
        fun ocultarMas18(prefs: SharedPreferences): Boolean = prefs.getBoolean(PREF_OCULTAR_MAS18, false)

        const val PREF_SIGNATURE = "post_signature"
        /**
         * Firma "Enviado desde ForoPlus" al publicar. **Por defecto APAGADA desde el
         * 2026-08-21.** Nació encendida (era como se había comportado siempre), pero un tester
         * señaló el problema real: la firma le está diciendo al staff de ForoCoches quién usa
         * un cliente de terceros, y esa factura la pagan los usuarios, no la app. Promocionarse
         * a costa del riesgo de otro no se sostiene, así que ahora hay que encenderla a
         * propósito. Quien ya la tuviera puesta a mano conserva su ajuste (la preferencia solo
         * se escribe al tocar el interruptor).
         *
         * Va por cuenta (ver ClavesPorCuenta): tiene sentido anunciarla en la cuenta principal
         * y no en otra. Este controlador no tiene el uid activo en memoria (no es MainActivity),
         * así que se lee de disco como hace IgnoreListRepository: `prefs` AQUÍ es siempre
         * `shell_prefs` (mismo fichero que MainActivity usa para `uid_activo`), lo pasen desde
         * la instancia o, como en [signatureEnabled], desde fuera con `shellPrefs`.
         */
        private fun uidActivo(prefs: SharedPreferences): String =
            prefs.getString("uid_activo", "") ?: ""

        fun signatureEnabled(prefs: SharedPreferences): Boolean =
            prefs.getBoolean(ClavesPorCuenta.clave(PREF_SIGNATURE, uidActivo(prefs)), false)
    }

    private val fontSmall: TextView = panel.findViewById(R.id.opt_font_small)
    private val fontNormal: TextView = panel.findViewById(R.id.opt_font_normal)
    private val fontLarge: TextView = panel.findViewById(R.id.opt_font_large)
    private val titleSmall: TextView = panel.findViewById(R.id.opt_title_small)
    private val titleNormal: TextView = panel.findViewById(R.id.opt_title_normal)
    private val titleLarge: TextView = panel.findViewById(R.id.opt_title_large)
    private val temaClaro: TextView = panel.findViewById(R.id.opt_tema_claro)
    private val temaOscuro: TextView = panel.findViewById(R.id.opt_tema_oscuro)
    private val temaSistema: TextView = panel.findViewById(R.id.opt_tema_sistema)
    private val kwSwitch: SwitchCompat = panel.findViewById(R.id.opt_kw_switch)
    private val kwInput: EditText = panel.findViewById(R.id.opt_kw_input)
    private val kwChips: LinearLayout = panel.findViewById(R.id.opt_kw_chips)
    private val igInput: EditText = panel.findViewById(R.id.opt_ig_input)
    private val igChips: LinearLayout = panel.findViewById(R.id.opt_ig_chips)
    private val signSwitch: SwitchCompat = panel.findViewById(R.id.opt_sign_switch)
    private val ultimoSwitch: SwitchCompat = panel.findViewById(R.id.opt_ultimo_switch)
    private val mas18Switch: SwitchCompat = panel.findViewById(R.id.opt_mas18_switch)
    private val ocultarMas18Switch: SwitchCompat = panel.findViewById(R.id.opt_ocultar_mas18_switch)
    private val popurriChips: LinearLayout = panel.findViewById(R.id.opt_popurri_chips)
    private val kwToggle: TextView = panel.findViewById(R.id.opt_kw_toggle)
    private val hilosToggle: TextView = panel.findViewById(R.id.opt_hilos_toggle)
    private val hilosChips: LinearLayout = panel.findViewById(R.id.opt_hilos_chips)
    private var hilosAbierto = false
    private val igToggle: TextView = panel.findViewById(R.id.opt_ig_toggle)
    private val popurriToggle: TextView = panel.findViewById(R.id.opt_popurri_toggle)

    // Dos niveles: la pantalla de categorías (opt_home) y la subpantalla de cada una. Ver el
    // comentario de opt_home en activity_main.xml.
    private val home: View = panel.findViewById(R.id.opt_home)
    private val titulo: TextView = panel.findViewById(R.id.options_title)
    private val scroll: android.widget.ScrollView = panel.findViewById(R.id.options_scroll)
    private val catApariencia: View = panel.findViewById(R.id.opt_cat_apariencia)
    private val catFiltros: View = panel.findViewById(R.id.opt_cat_filtros)
    private val catPersonalizar: View = panel.findViewById(R.id.opt_cat_personalizar)
    private val catPublicar: View = panel.findViewById(R.id.opt_cat_publicar)
    private val catAcerca: View = panel.findViewById(R.id.opt_novedades)
    /** Subpantalla abierta, o null si se está en la de categorías. */
    private var seccion: View? = null

    // Las tres listas arrancan PLEGADAS cada vez que se abre Opciones, y eso no se recuerda
    // entre visitas a propósito: recordar que dejaste abierta una lista de doscientos
    // ignorados devolvería la pared que esto viene a quitar. Abrirla nunca cuesta más de un
    // toque, y el número ya se ve de fuera.
    private var kwAbierto = false
    private var igAbierto = false
    private var popurriAbierto = false

    init {
        // Nada que repintar: la firma solo se consulta al enviar, así que no hace falta callback.
        signSwitch.setOnCheckedChangeListener { _, checked ->
            prefs.edit().putBoolean(ClavesPorCuenta.clave(PREF_SIGNATURE, uidActivo(prefs)), checked).apply()
        }
        ultimoSwitch.setOnCheckedChangeListener { _, checked ->
            if (checked == ultimoEnLista(prefs)) return@setOnCheckedChangeListener   // lo pinta bind()
            prefs.edit().putBoolean(PREF_ULTIMO, checked).apply()
            onUltimoChanged()
        }
        mas18Switch.setOnCheckedChangeListener { _, checked ->
            if (checked == mas18Activa(prefs)) return@setOnCheckedChangeListener   // lo pinta bind()
            prefs.edit().putBoolean(PREF_MAS18, checked).apply()
            onMas18Changed()
        }
        ocultarMas18Switch.setOnCheckedChangeListener { _, checked ->
            if (checked == ocultarMas18(prefs)) return@setOnCheckedChangeListener
            prefs.edit().putBoolean(PREF_OCULTAR_MAS18, checked).apply()
            onMas18Changed()
        }
        fontSmall.setOnClickListener { setFont(0) }
        fontNormal.setOnClickListener { setFont(1) }
        fontLarge.setOnClickListener { setFont(2) }
        titleSmall.setOnClickListener { setTitleFont(0) }
        titleNormal.setOnClickListener { setTitleFont(1) }
        titleLarge.setOnClickListener { setTitleFont(2) }

        temaClaro.setOnClickListener { setTema(TemaApp.CLARO) }
        temaOscuro.setOnClickListener { setTema(TemaApp.OSCURO) }
        temaSistema.setOnClickListener { setTema(TemaApp.SISTEMA) }
        paintTema(TemaApp.guardado(prefs))

        kwSwitch.setOnCheckedChangeListener { _, checked -> keywordRepo.setEnabled(checked) }
        panel.findViewById<View>(R.id.opt_kw_add).setOnClickListener { addKeyword() }
        panel.findViewById<View>(R.id.opt_ig_add).setOnClickListener { addIgnored() }

        kwToggle.setOnClickListener { kwAbierto = !kwAbierto; renderKeywords() }
        hilosToggle.setOnClickListener { hilosAbierto = !hilosAbierto; renderHilos() }
        igToggle.setOnClickListener { igAbierto = !igAbierto; renderIgnored() }
        popurriToggle.setOnClickListener { popurriAbierto = !popurriAbierto; pintarPopurri() }

        categoria(catApariencia, R.drawable.ic_opt_apariencia, "Apariencia")
        categoria(catFiltros, R.drawable.ic_opt_filtros, "Filtros")
        categoria(catPersonalizar, R.drawable.ic_opt_personalizar, "Personalizar")
        categoria(catPublicar, R.drawable.ic_opt_publicar, "Al publicar")
        categoria(panel.findViewById(R.id.opt_enlaces), R.drawable.ic_opt_enlaces,
            "Abrir enlaces del foro aquí",
            "Que los enlaces de ForoCoches se abran en la app y no en el navegador")
        // Las dos que salen de la app no llevan ›: no abren una pantalla nuestra.
        categoria(panel.findViewById(R.id.opt_telegram), R.drawable.ic_opt_comunidad,
            "Comunidad en Telegram", "Novedades, fallos y propuestas", flecha = false)
        categoria(panel.findViewById(R.id.opt_coffee), R.drawable.ic_opt_cafe,
            "Invítame a un café", "ForoPlus es gratis y sin publicidad", flecha = false)
        categoria(catAcerca, R.drawable.ic_opt_acerca, "Acerca de", "Novedades de cada versión")

        catApariencia.setOnClickListener { abrir(R.id.opt_sec_apariencia, "Apariencia") }
        catFiltros.setOnClickListener { abrir(R.id.opt_sec_filtros, "Filtros") }
        catPersonalizar.setOnClickListener { abrir(R.id.opt_sec_personalizar, "Personalizar") }
        catPublicar.setOnClickListener { abrir(R.id.opt_sec_publicar, "Al publicar") }
    }

    private fun categoria(
        fila: View, icono: Int, texto: String, resumen: String = "", flecha: Boolean = true
    ) {
        fila.findViewById<android.widget.ImageView>(R.id.cat_icono).setImageResource(icono)
        fila.findViewById<TextView>(R.id.cat_titulo).text = texto
        ponerResumen(fila, resumen)
        fila.findViewById<View>(R.id.cat_flecha).visibility = if (flecha) View.VISIBLE else View.GONE
    }

    private fun ponerResumen(fila: View, resumen: String) {
        val tv = fila.findViewById<TextView>(R.id.cat_resumen)
        tv.text = resumen
        tv.visibility = if (resumen.isEmpty()) View.GONE else View.VISIBLE
    }

    /** La versión instalada, que la sabe MainActivity (pregunta al sistema). */
    fun ponerVersion(texto: String) = ponerResumen(catAcerca, "$texto · novedades de cada versión")

    /** Cómo lo tienes, bajo cada categoría. Se repinta al volver a la pantalla de categorías. */
    private fun pintarResumenes() {
        ponerResumen(catApariencia, ResumenOpciones.apariencia(
            TemaApp.guardado(prefs), prefs.getInt(PREF_FONT_IDX, 1), prefs.getInt(PREF_TITLE_FONT_IDX, 1)))
        ponerResumen(catFiltros, ResumenOpciones.filtros(
            palabras = keywordRepo.getKeywords().size,
            palabrasActivas = keywordRepo.isEnabled(),
            usuarios = ignoreRepo.getIgnoredUsers().size,
            hilos = hilosIgnorados().size))
        ponerResumen(catPersonalizar, ResumenOpciones.personalizar(
            Popurri.leer(prefs.getString(Popurri.PREF, "") ?: "").size))
        ponerResumen(catPublicar, ResumenOpciones.publicar(signatureEnabled(prefs)))
    }

    private fun abrir(idSeccion: Int, nombre: String) {
        val s = panel.findViewById<View>(idSeccion)
        seccion = s
        home.visibility = View.GONE
        s.visibility = View.VISIBLE
        titulo.text = nombre
        scroll.scrollTo(0, 0)
    }

    private fun alInicio() {
        seccion?.visibility = View.GONE
        seccion = null
        home.visibility = View.VISIBLE
        titulo.text = "Opciones"
        pintarResumenes()
        scroll.scrollTo(0, 0)
    }

    /**
     * Atrás dentro de Opciones: de una subpantalla vuelve a las categorías y devuelve `true`
     * (atrás consumido). En las categorías devuelve `false` y quien llama cierra Opciones.
     */
    fun volver(): Boolean {
        if (seccion == null) return false
        // Filtros tiene dos cajas de texto: que el teclado no se quede flotando sobre la otra pantalla.
        val imm = panel.context.getSystemService(android.content.Context.INPUT_METHOD_SERVICE)
            as android.view.inputmethod.InputMethodManager
        imm.hideSoftInputFromWindow(panel.windowToken, 0)
        alInicio()
        return true
    }

    /** Refresca todo el panel con el estado actual (llamar al abrirlo). */
    fun bind() {
        alInicio()   // abrir Opciones es entrar por las categorías, no por la última subpantalla
        kwAbierto = false
        igAbierto = false
        popurriAbierto = false
        paintFont(prefs.getInt(PREF_FONT_IDX, 1))
        paintChips(listOf(titleSmall, titleNormal, titleLarge), prefs.getInt(PREF_TITLE_FONT_IDX, 1))
        signSwitch.isChecked = signatureEnabled(prefs)
        ultimoSwitch.isChecked = ultimoEnLista(prefs)
        mas18Switch.isChecked = mas18Activa(prefs)
        ocultarMas18Switch.isChecked = ocultarMas18(prefs)
        kwSwitch.isChecked = keywordRepo.isEnabled()
        renderKeywords()
        renderHilos()
        renderIgnored()
        pintarPopurri()
    }

    private fun setFont(idx: Int) {
        prefs.edit().putInt(PREF_FONT_IDX, idx).apply()
        paintFont(idx)
        onFontChanged()
    }

    private fun setTitleFont(idx: Int) {
        prefs.edit().putInt(PREF_TITLE_FONT_IDX, idx).apply()
        paintChips(listOf(titleSmall, titleNormal, titleLarge), idx)
        onTitleFontChanged()
    }

    private fun setTema(modo: String) {
        paintTema(modo)
        // Cambiar el modo recrea la actividad (Android rehace todas las vistas con los
        // recursos del otro tema). Por eso se pinta ANTES: el chip marcado tiene que ser el
        // último estado visible del panel viejo, o al volver se ve un parpadeo con el
        // anterior seleccionado.
        if (TemaApp.elegir(prefs, modo)) onTemaChanged()
    }

    private fun paintTema(modo: String) {
        val chips = listOf(temaClaro to TemaApp.CLARO, temaOscuro to TemaApp.OSCURO,
                           temaSistema to TemaApp.SISTEMA)
        chips.forEach { (tv, m) ->
            val on = m == modo
            tv.setBackgroundColor(col(if (on) R.color.fc_rojo else R.color.fc_chip))
            tv.setTextColor(col(if (on) R.color.fc_sobre_rojo else R.color.fc_texto_2))
        }
    }

    private fun paintFont(idx: Int) = paintChips(listOf(fontSmall, fontNormal, fontLarge), idx)

    private fun paintChips(chips: List<TextView>, idx: Int) {
        chips.forEachIndexed { i, tv ->
            val on = i == idx
            tv.setBackgroundColor(col(if (on) R.color.fc_rojo else R.color.fc_chip))
            tv.setTextColor(col(if (on) R.color.fc_sobre_rojo else R.color.fc_texto_2))
        }
    }

    private fun addKeyword() {
        val w = kwInput.text.toString().trim()
        if (w.isEmpty()) return
        kwAbierto = true
        keywordRepo.addKeyword(w)
        kwInput.setText("")
        renderKeywords()
        renderHilos()
        onListsChanged()
    }

    /** Color de la paleta (respeta el modo oscuro; un 0xFF… a pelo NO). */
    private fun col(id: Int) =
        androidx.core.content.ContextCompat.getColor(panel.context, id)

    private fun addIgnored() {
        val u = igInput.text.toString().trim().removePrefix("@")
        if (u.isEmpty()) return
        igInput.setText("")
        // Acabas de añadir a alguien: enseñar la lista es la confirmación de que ha entrado.
        igAbierto = true
        if (onIgnoreWrite("add", u)) return
        val cur = ignoreRepo.getIgnoredUsers().toMutableList()
        if (cur.none { it.equals(u, ignoreCase = true) }) cur.add(u)
        ignoreRepo.setIgnoredUsers(cur)
        renderIgnored()
        onListsChanged()
    }

    /** Repinta los chips con lo que haya en el almacén (lo llama MainActivity al confirmar FC). */
    fun refrescarIgnorados() = renderIgnored()

    /** Los hilos callados, para poder devolverles la voz. Ver [HilosIgnorados]. */
    private fun renderHilos() {
        hilosChips.removeAllViews()
        val hilos = hilosIgnorados()
        plegar(hilosToggle, hilosChips, hilos.size, "hilo", "hilos", hilosAbierto)
        if (!hilosAbierto) return
        for (h in hilos) {
            val etiqueta = h.titulo.ifBlank { "Hilo " + h.tid }
            hilosChips.addView(chipRow(etiqueta) {
                onDesignorarHilo(h.tid)
                renderHilos()
            })
        }
    }

    private fun renderKeywords() {
        kwChips.removeAllViews()
        val palabras = keywordRepo.getKeywords().sortedBy { it.lowercase() }
        plegar(kwToggle, kwChips, palabras.size, "palabra", "palabras", kwAbierto)
        if (!kwAbierto) return
        for (w in palabras) {
            kwChips.addView(chipRow(w) {
                keywordRepo.removeKeyword(w)
                renderKeywords()
                onListsChanged()
            })
        }
        // "Quitar todas": la lista viene con 18 palabras de fábrica y borrarlas de una en una
        // es un castigo (lo pidió Miguel). Va al FINAL de la lista abierta — no arriba — para
        // que no se toque sin querer al abrir el desplegable.
        if (palabras.isNotEmpty()) kwChips.addView(filaQuitarTodas(palabras.size))
    }

    private fun filaQuitarTodas(cuantas: Int): View {
        val ctx = panel.context
        val densidad = ctx.resources.displayMetrics.density
        return TextView(ctx).apply {
            text = if (cuantas == 1) "Quitar la palabra" else "Quitar las $cuantas palabras"
            textSize = 14f
            setTextColor(androidx.core.content.ContextCompat.getColor(ctx, R.color.fc_rojo))
            val v = (10 * densidad).toInt()
            setPadding(0, v, 0, v)
            setOnClickListener {
                keywordRepo.clearKeywords()
                renderKeywords()
                onListsChanged()
            }
        }
    }

    private fun renderIgnored() {
        igChips.removeAllViews()
        val users = ignoreRepo.getIgnoredUsers().sortedBy { it.lowercase() }
        plegar(igToggle, igChips, users.size, "ignorado", "ignorados", igAbierto)
        if (!igAbierto) return
        for (u in users) {
            igChips.addView(chipRow("@$u") {
                if (onIgnoreWrite("remove", u)) return@chipRow
                ignoreRepo.setIgnoredUsers(users.filterNot { it == u })
                renderIgnored()
                onListsChanged()
            })
        }
    }

    /**
     * La lista de subforos del Popurrí, cada uno con su marca de elegido.
     *
     * Se repinta entera en cada cambio en vez de tocar solo la fila pulsada: el estado tiene
     * que cuadrar con lo GUARDADO, no con lo que creamos recordar — y al pasarse del tope
     * [Popurri.alternar] saca uno de la selección, así que cambian dos filas, no una.
     */
    fun pintarPopurri() {
        popurriChips.removeAllViews()
        val todos = subforos()
        if (todos.isEmpty()) {
            // Sin subforos no hay nada que plegar, y este aviso explica por qué está vacío:
            // esconderlo dejaría un hueco mudo justo donde el usuario espera una lista.
            popurriToggle.visibility = View.GONE
            popurriChips.visibility = View.VISIBLE
            popurriChips.addView(
                textoSuelto("Abre el listado una vez para que la app conozca los subforos.")
            )
            return
        }
        plegar(popurriToggle, popurriChips, todos.size, "subforo", "subforos", popurriAbierto)
        if (!popurriAbierto) return
        val elegidos = Popurri.leer(prefs.getString(Popurri.PREF, "") ?: "")
        for ((fid, nombre) in todos) {
            val marcado = fid in elegidos
            popurriChips.addView(TextView(panel.context).apply {
                text = (if (marcado) "☑  " else "☐  ") + nombre
                textSize = 15f
                setTextColor(
                    ContextCompat.getColor(
                        panel.context, if (marcado) R.color.fc_rojo else R.color.fc_texto
                    )
                )
                setPadding(0, dp(10), 0, dp(10))
                setOnClickListener {
                    val nuevos = Popurri.alternar(
                        Popurri.leer(prefs.getString(Popurri.PREF, "") ?: ""), fid
                    )
                    prefs.edit().putString(Popurri.PREF, Popurri.guardar(nuevos)).apply()
                    pintarPopurri()
                    onPopurriChanged()
                }
            })
        }
    }

    /**
     * Deja el botón y su lista coherentes. Los dos se encienden y se apagan **juntos y solo
     * desde aquí**, que es la lección que dejó la barra de páginas: tocar una de las dos
     * piezas por su cuenta deja la otra huérfana (allí, una raya divisoria delimitando un
     * hueco vacío) y el fallo no lo canta nadie.
     */
    private fun plegar(
        toggle: TextView, lista: View, cuantos: Int,
        singular: String, plural: String, abierto: Boolean
    ) {
        val rotulo = Plegable.rotulo(cuantos, singular, plural, abierto)
        toggle.text = rotulo
        toggle.visibility = if (rotulo.isEmpty()) View.GONE else View.VISIBLE
        lista.visibility = if (abierto && cuantos > 0) View.VISIBLE else View.GONE
    }

    private fun textoSuelto(t: String) = TextView(panel.context).apply {
        text = t
        textSize = 13f
        setTextColor(ContextCompat.getColor(panel.context, R.color.fc_texto_3))
        setPadding(0, dp(8), 0, dp(8))
    }

    private fun dp(v: Int) = (v * panel.resources.displayMetrics.density).toInt()

    private fun chipRow(label: String, onRemove: () -> Unit): View {
        val row = LayoutInflater.from(panel.context).inflate(R.layout.item_opt_chip, kwChips, false)
        row.findViewById<TextView>(R.id.chip_label).text = label
        row.findViewById<View>(R.id.chip_remove).setOnClickListener { onRemove() }
        return row
    }
}
