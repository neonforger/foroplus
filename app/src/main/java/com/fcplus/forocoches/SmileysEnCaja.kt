package com.fcplus.forocoches

import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.text.Editable
import android.text.Spanned
import android.text.TextWatcher
import android.widget.EditText

/**
 * Los smileys se ven como emoticono mientras escribes (`:roto2:` → la imagen).
 *
 * Lo pidieron los testers el 2026-09-24. **El texto no cambia**: debajo de la imagen sigue el
 * código tal cual, que es lo que se publica, así que el mensaje sale idéntico al de antes. Solo
 * se le pone encima un [Marca] (el mismo centrado que los smileys de los mensajes).
 *
 * Dos detalles que no son adorno:
 * - **Se borra entero.** Sin esto, la tecla de borrar se comía el último `:` y quedaba
 *   `:roto2` a medias, que ya no es un smiley y se publica como texto.
 * - **La imagen sale de [PostImages]**, la misma caché de los mensajes: los smileys que ya has
 *   visto en un hilo salen al instante, y los que no, se piden y se repinta al llegar.
 */
class SmileysEnCaja(private val input: EditText) : TextWatcher {

    /** El dibujo de un smiley en la caja. Clase propia para no tocar ningún otro span. */
    class Marca(dibujo: Drawable, val codigo: String, origen: String) : IconoCentradoSpan(dibujo, origen)

    /** código → url de la imagen. Vacío hasta que el motor trae la lista de FC. */
    private var porCodigo: Map<String, String> = emptyMap()

    /** Smiley que el usuario está borrando: tras el borrado se quita lo que quede de él. */
    private var borrando: Marca? = null
    private var ocupado = false

    fun conLista(lista: List<Smiley>) {
        porCodigo = lista.associate { it.code to it.src }
        repintar()
    }

    override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {
        borrando = null
        if (ocupado || count <= 0 || after != 0 || s !is Spanned) return
        // Se está borrando algo que cae DENTRO de un smiley, sin llevárselo entero.
        borrando = s.getSpans(start, start + count, Marca::class.java).firstOrNull { m ->
            val a = s.getSpanStart(m)
            val b = s.getSpanEnd(m)
            a < start + count && b > start && !(start <= a && start + count >= b)
        }
    }

    override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {}

    override fun afterTextChanged(s: Editable) {
        if (ocupado) return
        ocupado = true
        try {
            borrando?.let { m ->
                val a = s.getSpanStart(m)
                val b = s.getSpanEnd(m)
                s.removeSpan(m)
                if (a in 0 until b) s.delete(a, b)
            }
            borrando = null
            marcar(s)
        } finally {
            ocupado = false
        }
    }

    private fun repintar() {
        val e = input.text ?: return
        ocupado = true
        try { marcar(e) } finally { ocupado = false }
    }

    /** Quita todas las marcas y vuelve a poner las de los códigos que hay ahora. */
    private fun marcar(s: Editable) {
        for (m in s.getSpans(0, s.length, Marca::class.java)) s.removeSpan(m)
        if (porCodigo.isEmpty()) return
        val res = input.resources
        val densidad = res.displayMetrics.density
        val anchoMax = (res.displayMetrics.widthPixels * 0.5f).toInt()
        for (t in SmileysEnTexto.buscar(s, porCodigo.keys)) {
            val src = porCodigo[t.codigo] ?: continue
            val bmp = PostImages.get(src)
            if (bmp == null) {
                // Llega en un momento: entonces se repinta la caja entera (son pocos).
                PostImages.load(src) { input.post { repintar() } }
                continue
            }
            val (w0, h0) = PostImages.tamano(src) ?: (bmp.width to bmp.height)
            val (w, h) = ImagenEnTexto.medida(w0, h0, densidad, anchoMax)
            if (w <= 0 || h <= 0) continue
            val d = BitmapDrawable(res, bmp).apply { setBounds(0, 0, w, h) }
            s.setSpan(Marca(d, t.codigo, src), t.inicio, t.fin, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
    }
}
