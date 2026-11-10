package com.fcplus.forocoches

import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextPaint
import android.text.TextUtils
import android.text.style.AbsoluteSizeSpan
import android.text.style.ForegroundColorSpan
import android.util.TypedValue
import android.widget.TextView
import androidx.core.content.ContextCompat

/**
 * La cabecera de la lista nativa. Dos modos, y TODO el que quiera cambiar el texto pasa por aquí:
 *  - [plano]: texto tal cual, sin ▾ y sin poder tocarse (Suscripciones, Mis hilos, búsquedas…).
 *  - [sitio]: selector de sitio. En UNA línea, "ForoPlus" en rojo a su tamaño de siempre, dos
 *    espacios y el sitio más pequeño en el color del texto con " ▾". Si no cabe se acorta SOLO
 *    el nombre del sitio (ver [TituloSitio.recortar]); "ForoPlus" y el ▾ siempre se ven. Tocar
 *    el título llama a [alTocar]. Se mide con el mismo tamaño que el span (dip, no sp) y se
 *    recalcula cuando cambia el ancho de la cabecera (aparece o desaparece el botón TOP).
 */
class TituloCabecera(private val vista: TextView, private val alTocar: () -> Unit) {

    private var nombre: String? = null

    init {
        vista.addOnLayoutChangeListener { _, l, _, r, _, ol, _, or, _ ->
            if (r - l != or - ol) pintar()
        }
    }

    fun plano(texto: String) {
        nombre = null
        vista.setOnClickListener(null)
        vista.isClickable = false
        vista.contentDescription = null
        vista.maxLines = Int.MAX_VALUE
        vista.ellipsize = null
        vista.text = texto
    }

    fun sitio(nombre: String) {
        this.nombre = nombre
        vista.maxLines = 1
        vista.ellipsize = null
        vista.setOnClickListener { alTocar() }
        vista.contentDescription = TituloSitio.descripcion(nombre)
        pintar()
    }

    private fun pintar() {
        val n = nombre ?: return
        val marca = TituloSitio.MARCA
        val sep = TituloSitio.SEPARADOR
        val chev = TituloSitio.CHEVRON
        val dm = vista.resources.displayMetrics
        // Mismo tamaño que el span (15 dip) y misma tipografía que la cabecera.
        val pSitio = TextPaint(vista.paint).apply {
            textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, TAM_SITIO_DIP, dm)
        }
        val ancho = vista.width - vista.paddingLeft - vista.paddingRight
        val corto = if (ancho <= 0) n else {
            val disponible = ancho - vista.paint.measureText(marca) -
                pSitio.measureText(sep) - pSitio.measureText(chev) - 1f
            TituloSitio.recortar(n, disponible) { pSitio.measureText(it) }
        }
        val sb = SpannableStringBuilder(marca).append(sep).append(corto).append(chev)
        val ini = marca.length + sep.length
        sb.setSpan(ForegroundColorSpan(ContextCompat.getColor(vista.context, R.color.fc_rojo)),
            0, marca.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        sb.setSpan(AbsoluteSizeSpan(TAM_SITIO_DIP.toInt(), true), marca.length, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        sb.setSpan(ForegroundColorSpan(ContextCompat.getColor(vista.context, R.color.fc_texto)),
            ini, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        // Sin repintar si no cambia: pintar() se llama también desde el layout.
        if (!TextUtils.equals(vista.text, sb)) vista.text = sb
    }

    private companion object {
        const val TAM_SITIO_DIP = 15f
    }
}
