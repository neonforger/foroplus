package com.fcplus.forocoches

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.PagerSnapHelper
import androidx.recyclerview.widget.RecyclerView

/**
 * La introducción (ver [Introduccion]): cinco páginas deslizables con capturas reales, "Saltar"
 * siempre a la vista y, en la última, los interruptores de Música y +18. Pantalla propia y no un
 * panel de MainActivity: no comparte nada con ella salvo dos preferencias, que MainActivity
 * vuelve a leer al recibir el resultado.
 */
class IntroActivity : AppCompatActivity() {

    private lateinit var paginas: RecyclerView
    private lateinit var lm: LinearLayoutManager
    private lateinit var puntos: LinearLayout
    private lateinit var saltar: TextView
    private lateinit var anterior: View
    private lateinit var principal: TextView
    private val prefs by lazy { getSharedPreferences("shell_prefs", MODE_PRIVATE) }
    private var actual = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Mismo estilo y tono que la app: la introducción no puede verse distinta de lo que enseña.
        EstiloApp.overlay(EstiloApp.guardado(prefs))?.let { theme.applyStyle(it, true) }
        AtributosTema.aplicarTono(this)
        setContentView(R.layout.activity_intro)
        paginas = findViewById(R.id.intro_paginas)
        puntos = findViewById(R.id.intro_puntos)
        saltar = findViewById(R.id.intro_saltar)
        anterior = findViewById(R.id.intro_anterior)
        principal = findViewById(R.id.intro_principal)

        lm = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        paginas.layoutManager = lm
        paginas.adapter = Adaptador()
        PagerSnapHelper().attachToRecyclerView(paginas)
        paginas.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(rv: RecyclerView, estado: Int) {
                if (estado != RecyclerView.SCROLL_STATE_IDLE) return
                val i = lm.findFirstCompletelyVisibleItemPosition()
                if (i >= 0 && i != actual) { actual = i; pintar() }
            }
        })
        actual = savedInstanceState?.getInt("pagina", 0) ?: 0
        if (actual > 0) paginas.scrollToPosition(actual)

        for (i in Introduccion.PAGINAS.indices) {
            puntos.addView(View(this).apply {
                setBackgroundResource(R.drawable.bg_intro_punto)
                layoutParams = LinearLayout.LayoutParams(dp(6), dp(6)).apply { marginEnd = dp(6) }
            })
        }
        saltar.setOnClickListener { terminar() }
        anterior.setOnClickListener { if (actual > 0) ir(actual - 1) }
        principal.setOnClickListener { Introduccion.siguiente(actual)?.let { ir(it) } ?: terminar() }
        // Atrás: página anterior; en la primera, sale (cuenta como vista, igual que "Saltar").
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { if (actual > 0) ir(actual - 1) else terminar() }
        })
        pintar()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt("pagina", actual)
    }

    private fun ir(i: Int) {
        actual = i
        paginas.smoothScrollToPosition(i)
        pintar()
    }

    private fun pintar() {
        for (i in 0 until puntos.childCount) {
            val p = puntos.getChildAt(i)
            p.isActivated = i == actual
            p.layoutParams = (p.layoutParams as LinearLayout.LayoutParams).apply { width = dp(if (i == actual) 22 else 6) }
        }
        puntos.contentDescription = "Página ${actual + 1} de ${Introduccion.PAGINAS.size}"
        saltar.visibility = if (Introduccion.haySaltar(actual)) View.VISIBLE else View.INVISIBLE
        anterior.visibility = if (actual > 0) View.VISIBLE else View.GONE
        principal.text = Introduccion.botonPrincipal(actual)
    }

    private fun terminar() {
        Introduccion.marcarVista(prefs)
        setResult(RESULT_OK)
        finish()
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private inner class Vista(v: View) : RecyclerView.ViewHolder(v) {
        val icono: ImageView = v.findViewById(R.id.intro_icono)
        val captura: ImageView = v.findViewById(R.id.intro_captura)
        val titulo: TextView = v.findViewById(R.id.intro_titulo)
        val texto: TextView = v.findViewById(R.id.intro_texto)
        val donde: TextView = v.findViewById(R.id.intro_donde)
        val extras: View = v.findViewById(R.id.intro_extras)
    }

    private inner class Adaptador : RecyclerView.Adapter<Vista>() {
        override fun getItemCount() = Introduccion.PAGINAS.size

        override fun onCreateViewHolder(parent: ViewGroup, tipo: Int): Vista {
            val v = LayoutInflater.from(parent.context).inflate(R.layout.item_intro_pagina, parent, false)
            // Cada página ocupa la pantalla entera: el inflado con el padre horizontal le daría wrap.
            v.layoutParams = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            return Vista(v)
        }

        override fun onBindViewHolder(h: Vista, i: Int) {
            val p = Introduccion.PAGINAS[i]
            h.titulo.text = p.titulo
            h.texto.text = p.texto
            h.donde.text = p.donde
            h.donde.visibility = if (p.donde.isEmpty()) View.GONE else View.VISIBLE
            h.icono.visibility = if (p.imagen == 0) View.VISIBLE else View.GONE
            h.captura.visibility = if (p.imagen == 0) View.GONE else View.VISIBLE
            if (p.imagen != 0) {
                h.captura.setImageResource(p.imagen)
                h.captura.clipToOutline = true
            }
            h.titulo.gravity = if (p.imagen == 0) android.view.Gravity.CENTER else android.view.Gravity.START
            h.texto.gravity = h.titulo.gravity
            h.donde.gravity = h.titulo.gravity
            val ultima = i == Introduccion.PAGINAS.lastIndex
            // En la última, la captura más baja: lo que importa ahí son los interruptores, y con
            // la captura entera quedaban por debajo del pliegue (medido en el Samsung S22).
            h.captura.maxHeight = dp(if (ultima) 190 else 340)
            h.extras.visibility = if (ultima) View.VISIBLE else View.GONE
            if (ultima) pintarExtras(h.itemView)
        }
    }

    /** Los interruptores escriben los MISMOS ajustes que Opciones; MainActivity los relee al volver. */
    private fun pintarExtras(v: View) {
        val config = RemoteConfig.cached(this)
        val filaMusica = v.findViewById<View>(R.id.intro_fila_musica)
        val swMusica = v.findViewById<SwitchCompat>(R.id.intro_sw_musica)
        filaMusica.visibility = if (ConfigMusica.servidorActivo(config)) View.VISIBLE else View.GONE
        swMusica.setOnCheckedChangeListener(null)
        swMusica.isChecked = Introduccion.musicaVisible(prefs.getString(PREF_TABS_OCULTOS, "") ?: "")
        swMusica.setOnCheckedChangeListener { _, on ->
            val csv = prefs.getString(PREF_TABS_OCULTOS, "") ?: ""
            prefs.edit().putString(PREF_TABS_OCULTOS, Introduccion.ocultosConMusica(csv, on)).apply()
        }
        filaMusica.setOnClickListener { swMusica.toggle() }

        val filaMas18 = v.findViewById<View>(R.id.intro_fila_mas18)
        val swMas18 = v.findViewById<SwitchCompat>(R.id.intro_sw_mas18)
        filaMas18.visibility = if (ConfigMas18Parser.de(config) != null) View.VISIBLE else View.GONE
        swMas18.setOnCheckedChangeListener(null)
        swMas18.isChecked = OptionsController.mas18Activa(prefs)
        swMas18.setOnCheckedChangeListener { _, on ->
            prefs.edit().putBoolean(OptionsController.PREF_MAS18, on).apply()
        }
        filaMas18.setOnClickListener { swMas18.toggle() }

        val telegram = v.findViewById<TextView>(R.id.intro_telegram)
        telegram.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_opt_comunidad, 0, 0, 0)
        telegram.setOnClickListener {
            // Fuera, nunca la capa web (regla de oro).
            try {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/foroplus"))
                    .addCategory(Intent.CATEGORY_BROWSABLE))
            } catch (_: ActivityNotFoundException) { }
        }
    }

    companion object {
        /** La misma preferencia que MainActivity.PREF_TABS_OCULTOS (ocultos de la barra de arriba). */
        const val PREF_TABS_OCULTOS = "tabs_ocultos"
    }
}
