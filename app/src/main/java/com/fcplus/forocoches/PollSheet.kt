package com.fcplus.forocoches

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.widget.CheckBox
import android.widget.CompoundButton
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.bottomsheet.BottomSheetDialog

/**
 * Hoja con la encuesta del hilo: pregunta, opciones, resultados y voto.
 *
 * Vive fuera de MainActivity (ya rondando las 2.300 líneas) y no sabe nada de red: recibe un
 * [Poll] ya parseado y avisa por [onVote] con los números de opción elegidos. Quien la usa
 * llama a [update] con la encuesta devuelta por FC, o a [showError] si el voto falló.
 */
class PollSheet(
    private val context: Context,
    /** Números de opción elegidos (uno, o varios en las multirrespuesta). */
    private val onVote: (List<String>) -> Unit,
    /** Encuesta abierta sin sesión: la app lleva a SU login, nunca a la capa web. */
    private val onLoginNeeded: () -> Unit
) {
    private var dialog: BottomSheetDialog? = null
    private var root: View? = null
    private var poll: Poll? = null
    private var loggedIn = true
    private var sending = false

    /** Opciones marcadas. Con [Poll.multiple] false solo puede haber una. */
    private val selected = LinkedHashSet<String>()

    /** Botón de cada fila, para repintar las marcas sin reconstruir la lista. */
    private val boxes = ArrayList<Pair<String, CompoundButton>>()

    fun isShowing(): Boolean = dialog?.isShowing == true

    fun show(p: Poll, loggedIn: Boolean) {
        this.poll = p
        this.loggedIn = loggedIn
        this.sending = false
        selected.clear()

        val view = LayoutInflater.from(context).inflate(R.layout.sheet_poll, null)
        root = view
        val sheet = BottomSheetDialog(context)
        sheet.setContentView(view)
        // Sin esto el botón de votar queda DEBAJO de la barra de navegación del sistema
        // (la hoja se dibuja a pantalla completa).
        val basePad = view.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(view) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(v.paddingLeft, v.paddingTop, v.paddingRight, basePad + bars.bottom)
            insets
        }
        sheet.setOnDismissListener { dialog = null; root = null }
        dialog = sheet
        render()
        sheet.show()
    }

    /** Repinta con la encuesta ya actualizada (típicamente, después de votar). */
    fun update(p: Poll) {
        poll = p
        sending = false
        selected.clear()
        if (root != null) render()
    }

    /** Mensaje de error de FC dentro de la propia hoja; el botón vuelve a estar activo. */
    fun showError(msg: String) {
        sending = false
        val v = root ?: return
        val err = v.findViewById<TextView>(R.id.poll_error)
        err.text = if (msg.isNotEmpty()) msg else "No se pudo enviar el voto"
        err.visibility = View.VISIBLE
        paintVoteButton()
    }

    fun dismiss() {
        dialog?.dismiss()
        dialog = null
        root = null
    }

    // ── Pintado ──────────────────────────────────────────────────────────────

    private fun render() {
        val v = root ?: return
        val p = poll ?: return
        v.findViewById<TextView>(R.id.poll_question).text =
            if (p.question.isNotEmpty()) p.question else "Encuesta"
        v.findViewById<TextView>(R.id.poll_error).visibility = View.GONE
        v.findViewById<TextView>(R.id.poll_footer).text = pollFooter(p)

        val hint = v.findViewById<TextView>(R.id.poll_hint)
        if (p.multiple && p.canVote) {
            hint.text = "Puedes marcar varias opciones"
            hint.visibility = View.VISIBLE
        } else {
            hint.visibility = View.GONE
        }

        renderOptions(v.findViewById(R.id.poll_options), p)
        paintVoteButton()
    }

    private fun renderOptions(container: LinearLayout, p: Poll) {
        container.removeAllViews()
        boxes.clear()
        val inflater = LayoutInflater.from(context)
        for (opt in p.options) {
            val row = inflater.inflate(R.layout.item_poll_option, container, false)
            row.findViewById<TextView>(R.id.poll_opt_text).text = opt.text

            val pct = row.findViewById<TextView>(R.id.poll_opt_pct)
            val result = row.findViewById<View>(R.id.poll_opt_result)
            if (p.hasResults) {
                pct.text = pctText(opt.pct)
                pct.visibility = View.VISIBLE
                result.visibility = View.VISIBLE
                // FC marca en cursiva la opción que votaste; aquí se dice con todas las letras.
                row.findViewById<TextView>(R.id.poll_opt_votes).text =
                    if (opt.mine) "${votesLabel(opt.votes)} · tu voto" else votesLabel(opt.votes)
                val fill = row.findViewById<View>(R.id.poll_opt_bar_fill)
                val rest = row.findViewById<View>(R.id.poll_opt_bar_rest)
                val f = barFraction(opt.pct)
                (fill.layoutParams as LinearLayout.LayoutParams).weight = f
                (rest.layoutParams as LinearLayout.LayoutParams).weight = 1f - f
            } else {
                // FC oculta los resultados hasta votar: ni barra ni porcentaje inventados.
                pct.visibility = View.GONE
                result.visibility = View.GONE
            }

            if (p.canVote && loggedIn) {
                val radio = row.findViewById<RadioButton>(R.id.poll_opt_radio)
                val check = row.findViewById<CheckBox>(R.id.poll_opt_check)
                val box: CompoundButton = if (p.multiple) check else radio
                box.visibility = View.VISIBLE
                boxes.add(opt.num to box)
                // El clic va en la fila entera (área cómoda); el botón es solo el indicador.
                row.setOnClickListener { toggle(opt.num, p.multiple) }
            } else {
                row.isClickable = false
                row.background = null
            }
            container.addView(row)
        }
        paintBoxes()
    }

    private fun toggle(num: String, multiple: Boolean) {
        if (sending || num.isEmpty()) return
        if (multiple) {
            if (!selected.remove(num)) selected.add(num)
        } else {
            selected.clear()
            selected.add(num)
        }
        paintBoxes()
        paintVoteButton()
    }

    private fun paintBoxes() {
        for ((num, box) in boxes) box.isChecked = selected.contains(num)
    }

    private fun paintVoteButton() {
        val v = root ?: return
        val p = poll ?: return
        val btn = v.findViewById<TextView>(R.id.poll_vote)
        when {
            // Encuesta abierta pero sin sesión: se ofrece entrar, no se esconde el motivo.
            !loggedIn && !p.closed && !p.voted -> {
                btn.visibility = View.VISIBLE
                btn.text = "Inicia sesión para votar"
                btn.isEnabled = true
                btn.alpha = 1f
                btn.setOnClickListener { dismiss(); onLoginNeeded() }
            }
            !p.canVote -> btn.visibility = View.GONE
            else -> {
                btn.visibility = View.VISIBLE
                btn.text = if (sending) "Votando…" else "Votar"
                // Sin nada marcado el botón se ve apagado, pero sigue pulsable para poder
                // decir POR QUÉ no pasa nada en vez de quedarse mudo.
                btn.isEnabled = !sending
                btn.alpha = if (sending || selected.isEmpty()) 0.6f else 1f
                btn.setOnClickListener {
                    if (sending) return@setOnClickListener
                    if (selected.isEmpty()) {
                        showError(
                            if (p.multiple) "Marca al menos una opción" else "Elige una opción"
                        )
                        return@setOnClickListener
                    }
                    sending = true
                    v.findViewById<TextView>(R.id.poll_error).visibility = View.GONE
                    paintVoteButton()
                    onVote(selected.toList())
                }
            }
        }
    }
}
