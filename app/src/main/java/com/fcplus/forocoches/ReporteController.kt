package com.fcplus.forocoches

import android.view.View
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity

/**
 * Reportar un mensaje: el diálogo nativo (motivo + comentario) y el envío por report.php, que
 * está tras un Cloudflare interactivo (gotcha 12). Salió de MainActivity tal cual (regla 10 de
 * CLAUDE.md, 2026-10-10); MainActivity solo lo crea, abre el diálogo desde el menú del mensaje
 * y le pregunta [abierto] / llama a [cerrar] desde el botón atrás.
 *
 * @param userAgent el del motor (el WebView del reporte tiene que parecer el mismo navegador).
 * @param cambiandoDeCuenta a medio cambiar de cuenta, el reporte podría salir a nombre de otro.
 * @param escaparJs el mismo escapado que usa MainActivity para meter texto en JavaScript.
 */
class ReporteController(
    private val activity: AppCompatActivity,
    private val userAgent: () -> String,
    private val cambiandoDeCuenta: () -> Boolean,
    private val toast: (String) -> Unit,
    private val escaparJs: (String) -> String
) {

    companion object {
        // Motivo (etiqueta del diálogo) → valor del radio 'tipo' del form real de FC (verificado por CDP).
        private val reportTipo = mapOf(
            "+18" to "6", "Spam" to "1", "Troll" to "2", "Flood" to "5", "Contenido" to "3", "Otros" to "4"
        )

        /** El `tipo` del formulario para un motivo; lo desconocido va como "Otros". */
        fun tipoDe(motivo: String): String = reportTipo[motivo] ?: "4"
    }

    /** Hay un envío en marcha (la capa está puesta): el botón atrás lo cancela. */
    val abierto: Boolean get() = reportOverlay != null

    /**
     * Diálogo NATIVO de reporte (replica report.php de FC: comentario + motivo). Al confirmar,
     * el envío pasa el Cloudflare por un WebView tapado con capa nativa y auto-envía el form real.
     */
    fun mostrarDialogo(post: PostItem) {
        val view = activity.layoutInflater.inflate(R.layout.dialog_report, null)
        val comment = view.findViewById<EditText>(R.id.report_comment)
        val commentBox = view.findViewById<View>(R.id.report_comment_box)
        val group = view.findViewById<android.widget.RadioGroup>(R.id.report_reasons)
        // Etiqueta FC de cada motivo: el auto-submit la empareja con el radio real del formulario.
        val reasonLabels = mapOf(
            R.id.reason_18 to "+18", R.id.reason_spam to "Spam", R.id.reason_troll to "Troll",
            R.id.reason_flood to "Flood", R.id.reason_content to "Contenido", R.id.reason_other to "Otros"
        )
        // El comentario solo tiene sentido en "Otros" (detalle del reporte): aparece al marcarlo.
        group.setOnCheckedChangeListener { _, checkedId ->
            commentBox.visibility = if (checkedId == R.id.reason_other) View.VISIBLE else View.GONE
        }
        androidx.appcompat.app.AlertDialog.Builder(activity)
            .setTitle("Reportar mensaje de ${post.author}")
            .setView(view)
            .setPositiveButton("Enviar reporte", null) // se sobreescribe abajo para validar sin cerrar
            .setNegativeButton("Cancelar", null)
            .create().apply {
                setOnShowListener {
                    getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                        val reason = reasonLabels[group.checkedRadioButtonId]
                        if (reason == null) { toast("Elige un motivo"); return@setOnClickListener }
                        val txt = comment.text.toString().trim()
                        if (reason == "Otros" && txt.isEmpty()) { toast("Escribe el motivo del reporte"); return@setOnClickListener }
                        dismiss()
                        submitReport(post, reason, txt)
                    }
                }
            }.show()
    }

    // ── Reporte: WebView invisible (tapado por capa nativa) que resuelve el Cloudflare ────────
    private var reportOverlay: android.widget.FrameLayout? = null
    private var reportWeb: android.webkit.WebView? = null
    private var reportPoll: Runnable? = null
    private var reportCover: View? = null

    private fun submitReport(post: PostItem, reason: String, comment: String) {
        // El reporte pasa por el Cloudflare interactivo con las cookies que haya AHORA MISMO en
        // el CookieManager: a medio cambiar, podría salir a nombre de quien no debe.
        if (cambiandoDeCuenta()) { toast("Espera a que termine el cambio de cuenta"); return }
        startReportFlow(post, reason, comment)
    }

    /**
     * report.php está tras un Cloudflare challenge por-ruta que un fetch NO puede resolver (solo un
     * navegador VISIBLE que renderice). Truco: un WebView a pantalla completa que SÍ renderiza (así
     * el challenge se resuelve) pero TAPADO por una capa nativa opaca → el usuario nunca ve el foro
     * web (regla de oro). Cuando cae el formulario real, [FASE 3] se auto-rellena y envía.
     */
    private fun startReportFlow(post: PostItem, reason: String, comment: String) {
        // El content FrameLayout (NO el LinearLayout vertical root_container): un hijo a pantalla
        // completa se superpone limpiamente sin descolocar la barra inferior.
        val root = activity.findViewById<android.view.ViewGroup>(android.R.id.content)
        val overlay = android.widget.FrameLayout(activity)
        val wv = object : android.webkit.WebView(activity) {
            // Chromium throttla el render de un WebView que Android considera NO visible; al taparlo
            // con una capa opaca, onVisibilityAggregated pasa a false y el challenge de CF se congela.
            // Forzamos "visible" para que siga renderizando por debajo del cover y resuelva el CF.
            override fun onVisibilityAggregated(isVisible: Boolean) { super.onVisibilityAggregated(true) }
        }.apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.databaseEnabled = true
            settings.userAgentString = userAgent()
            // Sin WebViewClient, el redirect post-reporte (a showthread) abriría CHROME. Este lo
            // mantiene TODO dentro del WebView tapado (devolver false = lo carga el propio WebView).
            webViewClient = object : android.webkit.WebViewClient() {
                override fun shouldOverrideUrlLoading(
                    view: android.webkit.WebView, req: android.webkit.WebResourceRequest
                ): Boolean = false
            }
        }
        overlay.addView(wv, android.widget.FrameLayout.LayoutParams(-1, -1))
        val cover = buildReportCover(post)
        // El cover arranca ARRIBA (tapando). Solo se BAJA cuando hay un Cloudflare INTERACTIVO que el
        // usuario debe resolver (excepción de la regla de oro). Así el formulario/foro NUNCA se ven:
        // si no hay CF, el cover no se baja jamás; si lo hay, se baja solo para pulsar la casilla.
        cover.visibility = View.VISIBLE
        overlay.addView(cover, android.widget.FrameLayout.LayoutParams(-1, -1))
        root.addView(overlay, android.view.ViewGroup.LayoutParams(-1, -1))
        reportOverlay = overlay; reportWeb = wv; reportCover = cover

        wv.loadUrl("https://forocoches.com/foro/report.php?do=report&p=${post.pid}")
        val started = System.currentTimeMillis()
        var submitted = false
        val poll = object : Runnable {
            override fun run() {
                val w = reportWeb ?: return
                w.evaluateJavascript(
                    """(function(){
                        var b=document.body?document.body.innerText:'';
                        var cf=/Un momento|Just a moment|Verificaci.n de seguridad|Verifique que/i.test((document.title||'')+b);
                        var form=!!document.querySelector('textarea[name="reason"]');
                        return JSON.stringify({cf:cf,form:form});
                    })()"""
                ) { res ->
                    val r = try {
                        val inner = org.json.JSONTokener(res).nextValue() as? String
                        if (inner != null) org.json.JSONObject(inner) else null
                    } catch (e: Exception) { null }
                    val cf = r?.optBoolean("cf") == true
                    val form = r?.optBoolean("form") == true
                    val elapsed = System.currentTimeMillis() - started
                    // Cover ARRIBA salvo cuando hay CF interactivo (y aún no cayó el form): solo
                    // entonces se baja para que el usuario pulse la casilla del Cloudflare.
                    reportCover?.visibility = if (cf && !form) View.GONE else View.VISIBLE
                    when {
                        form && !submitted -> { submitted = true; onReportFormReady(post, reason, comment) }
                        elapsed > 90000 -> { toast("No se pudo completar la verificación"); cerrar() }
                        else -> reportHandler.postDelayed(this, 150)
                    }
                }
            }
        }
        reportPoll = poll
        reportHandler.postDelayed(poll, 250)
    }

    private val reportHandler by lazy { android.os.Handler(activity.mainLooper) }

    private fun dp(v: Int): Int = (v * activity.resources.displayMetrics.density).toInt()

    private fun color(id: Int) = androidx.core.content.ContextCompat.getColor(activity, id)

    /** Capa nativa opaca que oculta el WebView del report (el usuario solo ve ESTO). */
    private fun buildReportCover(post: PostItem): View {
        val ll = android.widget.LinearLayout(activity).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            gravity = android.view.Gravity.CENTER
            setBackgroundColor(color(R.color.fc_fondo))
            isClickable = true; isFocusable = true // absorbe toques: no se toca el WebView de debajo
            setPadding(dp(32), dp(32), dp(32), dp(32))
        }
        ll.addView(android.widget.ProgressBar(activity))
        ll.addView(android.widget.TextView(activity).apply {
            text = "Enviando reporte…"
            setTextColor(color(R.color.fc_texto))
            textSize = 16f
            gravity = android.view.Gravity.CENTER
            setPadding(0, dp(18), 0, dp(6))
        })
        ll.addView(android.widget.TextView(activity).apply {
            text = "Verificando con ForoCoches"
            setTextColor(color(R.color.fc_texto_3))
            textSize = 13f
            gravity = android.view.Gravity.CENTER
        })
        val cancel = android.widget.TextView(activity).apply {
            text = "Cancelar"
            setTextColor(color(R.color.fc_rojo))
            textSize = 15f
            setPadding(dp(20), dp(24), dp(20), dp(10))
            setOnClickListener { cerrar() }
        }
        ll.addView(cancel)
        return ll
    }

    private fun onReportFormReady(post: PostItem, reason: String, comment: String) {
        // Formulario cargado → TAPAR ya para que el foro web no se vea mientras se auto-envía.
        reportCover?.visibility = View.VISIBLE
        val tipo = tipoDe(reason)
        // Se rellena el textarea 'reason', se marca el radio 'tipo' y se envía el form REAL (ya trae
        // securitytoken/s/postid…). No reimplementamos el POST: reutilizamos el form con su token.
        val js = """(function(){
            var f=document.querySelector('form[action*="do=sendemail"]')||document.querySelector('form');
            if(!f) return 'no-form';
            var ta=f.querySelector('textarea[name="reason"]'); if(ta) ta.value='${escaparJs(comment)}';
            var r=f.querySelector('input[name="tipo"][value="$tipo"]'); if(!r) return 'no-tipo'; r.checked=true;
            f.submit(); return 'ok';
        })()"""
        reportWeb?.evaluateJavascript(js) { res ->
            if (res.contains("ok")) {
                // f.submit() ya ha enviado el POST → el reporte queda hecho en el servidor. NO cargamos
                // el redirect a showthread: bajo el cover (WebView ocluido) relanza un Cloudflare que
                // no puede renderizar y colgaba el flujo. Cerramos con un timeout INDEPENDIENTE (no
                // atado al callback de evaluateJavascript, que era lo que se quedaba sin responder).
                reportPoll?.let { reportHandler.removeCallbacks(it) }; reportPoll = null
                reportHandler.postDelayed({ toast("Reporte enviado ✓"); cerrar() }, 2500)
            } else { toast("No se pudo enviar el reporte"); cerrar() }
        }
    }

    /**
     * Cancela el envío en marcha y quita la capa (botón atrás, "Cancelar", fin o error).
     *
     * Primero FUERA DE LA PANTALLA y luego destruir, un frame después: al revés, `destroy()`
     * revienta el renderer, el `removeView` no llega y la capa se queda puesta — con la tapa
     * quitada para el Cloudflare, el foro a la vista (gotcha de Android, el mismo que tuvo el
     * login; `cerrarVerificacionLogin` de MainActivity hace esto mismo). Se aplaza también
     * porque se llama desde callbacks del propio WebView.
     */
    fun cerrar() {
        reportPoll?.let { reportHandler.removeCallbacks(it) }; reportPoll = null
        val overlay = reportOverlay
        val wv = reportWeb
        reportOverlay = null; reportWeb = null; reportCover = null
        reportHandler.post {
            // 1) fuera de la pantalla   2) fuera del WebView de dentro   3) destruir
            (overlay?.parent as? android.view.ViewGroup)?.removeView(overlay)
            wv?.let {
                it.stopLoading()
                it.loadUrl("about:blank")
                (it.parent as? android.view.ViewGroup)?.removeView(it)
                it.destroy()
            }
        }
    }
}
