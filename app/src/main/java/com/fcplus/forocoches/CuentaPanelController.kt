package com.fcplus.forocoches

import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

/**
 * El panel del avatar (rediseño del 2026-10-02, ver BarraAbajo): tu ficha resumida y las filas
 * de lo que no cabe en la barra de abajo. Salió de MainActivity tal cual (regla 10 de CLAUDE.md,
 * 2026-10-10). MainActivity lo crea, lo abre desde el avatar y le dice QUÉ filas lleva con
 * [rellenar], porque eso depende de la barra, las insignias y las cuentas guardadas.
 *
 * @param nombre el nombre con el que se pinta la cabecera.
 * @param lineaPerfil la línea de tu ficha ("Usuario · 17 mensajes · …"), vacía si aún no llegó.
 * @param pedirPerfil se llama al abrir si todavía no hay línea (sale de member.php).
 * @param pintarAvatar repinta los avatares (el de la cabecera y el de [avatar]).
 */
class CuentaPanelController(
    private val activity: AppCompatActivity,
    private val nombre: () -> String,
    private val lineaPerfil: () -> String,
    private val pedirPerfil: () -> Unit,
    private val pintarAvatar: () -> Unit,
    private val alVerFicha: () -> Unit,
    private val alSalir: () -> Unit,
    private val rellenar: Filas.() -> Unit
) {

    /** Lo que [rellenar] puede poner en el panel. */
    interface Filas {
        fun fila(icono: Int, texto: String, insignia: Int = 0, alTocar: () -> Unit)
        fun separador()
    }

    private lateinit var panelCuenta: View
    private lateinit var cuentaCajon: View
    private lateinit var cuentaVelo: View
    private lateinit var cuentaFilas: LinearLayout
    private lateinit var cuentaNombre: TextView
    private lateinit var cuentaStats: TextView

    /** El avatar del panel, para que MainActivity lo pinte junto al de la cabecera. */
    var avatar: android.widget.ImageView? = null
        private set

    /** ¿Está abierto? (El botón atrás lo cierra antes que nada.) */
    var visible = false
        private set

    private fun color(id: Int) = androidx.core.content.ContextCompat.getColor(activity, id)

    fun configurar() {
        // Encima de TODA la actividad: la barra de abajo está fuera del contenedor de pantallas
        // y el velo tiene que taparla también.
        panelCuenta = activity.layoutInflater.inflate(R.layout.panel_cuenta, null)
        activity.addContentView(panelCuenta, android.view.ViewGroup.LayoutParams(
            android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.MATCH_PARENT))
        cuentaCajon = panelCuenta.findViewById(R.id.cuenta_cajon)
        cuentaVelo = panelCuenta.findViewById(R.id.cuenta_velo)
        cuentaFilas = panelCuenta.findViewById(R.id.cuenta_filas)
        avatar = panelCuenta.findViewById(R.id.cuenta_avatar)
        cuentaNombre = panelCuenta.findViewById(R.id.cuenta_nombre)
        cuentaStats = panelCuenta.findViewById(R.id.cuenta_stats)
        panelCuenta.findViewById<android.widget.ImageView>(R.id.cuenta_salir_icono)
            .setColorFilter(color(R.color.fc_rojo))
        // Pantalla de borde a borde (targetSdk 35+): el cajón no puede meterse debajo de la barra
        // de estado ni de la de gestos. Medido en el Samsung: sin esto "Cerrar sesión" quedaba
        // encima de la zona de gestos (y 2178-2340 de 2340). Mismo patrón que la raíz.
        ViewCompat.setOnApplyWindowInsetsListener(cuentaCajon) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(0, bars.top, 0, bars.bottom)
            insets
        }
        cuentaVelo.setOnClickListener { cerrar() }
        panelCuenta.findViewById<View>(R.id.cuenta_ficha).setOnClickListener {
            cerrar(animar = false)
            alVerFicha()
        }
        panelCuenta.findViewById<View>(R.id.cuenta_salir).setOnClickListener {
            cerrar(animar = false)
            alSalir()
        }
    }

    /** Se abre SOLO tocando el avatar (deslizar desde el borde se pelearía con el swipe de subforos). */
    fun abrir() {
        if (visible) return
        visible = true
        pintar()
        panelCuenta.visibility = View.VISIBLE
        val ancho = 316 * activity.resources.displayMetrics.density
        cuentaCajon.translationX = ancho
        cuentaVelo.alpha = 0f
        cuentaCajon.animate().translationX(0f).setDuration(220).start()
        cuentaVelo.animate().alpha(1f).setDuration(220).start()
        // La línea de tu ficha sale de member.php: se pide la primera vez que hace falta.
        if (lineaPerfil().isEmpty()) pedirPerfil()
    }

    /**
     * @param animar `false` cuando se cierra para ir a otra pantalla: así no se ve el cajón
     *   deslizándose por encima de la pantalla nueva.
     */
    fun cerrar(animar: Boolean = true) {
        if (!visible) return
        visible = false
        if (!animar) { panelCuenta.visibility = View.GONE; return }
        val ancho = 316 * activity.resources.displayMetrics.density
        cuentaVelo.animate().alpha(0f).setDuration(180).start()
        cuentaCajon.animate().translationX(ancho).setDuration(180)
            .withEndAction { if (!visible) panelCuenta.visibility = View.GONE }
            .start()
    }

    /** Las filas del panel: lo que no va en la barra, y luego lo de la cuenta y la app. */
    fun pintar() {
        cuentaNombre.text = nombre()
        val linea = lineaPerfil()
        cuentaStats.text = linea
        cuentaStats.visibility = if (linea.isEmpty()) View.GONE else View.VISIBLE
        pintarAvatar()
        cuentaFilas.removeAllViews()
        val filas = object : Filas {
            override fun fila(icono: Int, texto: String, insignia: Int, alTocar: () -> Unit) {
                val v = activity.layoutInflater.inflate(R.layout.item_panel_cuenta, cuentaFilas, false)
                v.findViewById<android.widget.ImageView>(R.id.cuenta_fila_icono).apply {
                    setImageResource(icono)
                    setColorFilter(color(R.color.fc_texto_2))
                }
                v.findViewById<TextView>(R.id.cuenta_fila_texto).text = texto
                v.findViewById<TextView>(R.id.cuenta_fila_badge).apply {
                    visibility = if (insignia > 0) View.VISIBLE else View.GONE
                    text = if (insignia > 99) "99+" else insignia.toString()
                }
                v.setOnClickListener { cerrar(animar = false); alTocar() }
                cuentaFilas.addView(v)
            }

            override fun separador() {
                val d = activity.resources.displayMetrics.density
                cuentaFilas.addView(View(activity).apply {
                    setBackgroundColor(color(R.color.fc_divisoria))
                    layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                        d.toInt().coerceAtLeast(1)).apply {
                        val m = (20 * d).toInt()
                        setMargins(m, m / 3, m, m / 3)
                    }
                })
            }
        }
        filas.rellenar()
    }
}
