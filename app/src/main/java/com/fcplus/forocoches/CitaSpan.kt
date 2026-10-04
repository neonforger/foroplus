package com.fcplus.forocoches

import android.graphics.Canvas
import android.graphics.Paint
import android.text.Layout
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.QuoteSpan
import android.text.style.LeadingMarginSpan
import android.text.style.LineBackgroundSpan

/**
 * Adorno de una cita dentro de un mensaje: barra vertical de color + fondo suave.
 *
 * Sustituye al [android.text.style.QuoteSpan] que monta `Html.fromHtml` a partir del
 * `<blockquote>` del extractor. Ese es el motivo del fallo que reportaron los testers el
 * 2026-08-22 ("en modo oscuro no se aprecia qué parte del post es cita"): el QuoteSpan de
 * fábrica pinta una raya de 2 px en **azul fijo `0xff0000ff`**, un color escrito a mano dentro
 * del framework que no sabe nada de nuestra paleta — sobre el fondo `#121214` del modo oscuro
 * no se ve, y encima no hay fondo que delimite dónde acaba la cita y empieza la respuesta.
 * El constructor con color existe, pero es de **API 28** y la app sale desde la 24.
 *
 * Aquí la barra y el fondo son tokens de la paleta, así que se adaptan solos a claro/oscuro.
 *
 * OJO al orden de pintado: el fondo (`LineBackgroundSpan`) se dibuja en la pasada de fondos y
 * la barra (`LeadingMarginSpan`) en la del texto, o sea DESPUÉS — por eso la barra no queda
 * tapada por su propio fondo.
 */
class CitaSpan(
    private val barra: Int,
    private val fondo: Int,
    densidad: Float
) : LeadingMarginSpan, LineBackgroundSpan {

    /** Grosor de la barra. 3dp, no 2px: la de fábrica se perdía y eso era medio problema. */
    private val ancho = (ANCHO_DP * densidad).toInt().coerceAtLeast(2)

    /** Aire entre la barra y el texto citado. */
    private val hueco = (HUECO_DP * densidad).toInt().coerceAtLeast(4)

    override fun getLeadingMargin(first: Boolean): Int = ancho + hueco

    override fun drawLeadingMargin(
        c: Canvas, p: Paint, x: Int, dir: Int, top: Int, baseline: Int, bottom: Int,
        text: CharSequence, start: Int, end: Int, first: Boolean, layout: Layout
    ) {
        // El Paint es COMPARTIDO con el dibujo del texto: hay que devolverlo como estaba o la
        // línea siguiente se pinta del color de la barra.
        val colorAntes = p.color
        val estiloAntes = p.style
        p.style = Paint.Style.FILL
        p.color = barra
        c.drawRect(
            x.toFloat(), top.toFloat(), (x + dir * ancho).toFloat(), bottom.toFloat(), p
        )
        p.color = colorAntes
        p.style = estiloAntes
    }

    override fun drawBackground(
        c: Canvas, p: Paint, left: Int, right: Int, top: Int, baseline: Int, bottom: Int,
        text: CharSequence, start: Int, end: Int, lineNumber: Int
    ) {
        val colorAntes = p.color
        p.color = fondo
        c.drawRect(left.toFloat(), top.toFloat(), right.toFloat(), bottom.toFloat(), p)
        p.color = colorAntes
    }

    companion object {
        const val ANCHO_DP = 3f
        const val HUECO_DP = 10f

        /**
         * La cita tiene que ser el PRIMER margen del párrafo, el de más a la izquierda.
         *
         * El Layout pinta los `LeadingMarginSpan` de un párrafo en el orden en que los devuelve
         * `getSpans`, avanzando la x con cada uno. `fromHtml` mete la viñeta de cada `<li>`
         * (`BulletSpan`) ANTES que la cita —cierra el `<li>` antes que el `<blockquote>`— y
         * nuestro span, al sustituir al de fábrica, entraba el último. Resultado (Juan,
         * 2026-09-22): en una cita con lista, **las viñetas salían a la izquierda de la raya**
         * y la raya se partía en trozos, desplazada en cada renglón con viñeta. La prioridad
         * del span es lo único que manda sobre ese orden sin tocar los flags de párrafo.
         */
        private const val PRIORIDAD = 100

        internal fun conPrioridad(flags: Int): Int =
            (flags and Spanned.SPAN_PRIORITY.inv()) or (PRIORIDAD shl Spanned.SPAN_PRIORITY_SHIFT)

        /**
         * Cambia los `QuoteSpan` de fábrica de [sb] por los nuestros.
         *
         * Se conservan los **flags originales**: `Html.fromHtml` marca la cita como estilo de
         * PÁRRAFO, y volver a ponerla como `EXCLUSIVE_EXCLUSIVE` haría que el Layout se saltara
         * el adorno en cuanto el tramo no empiece justo detrás de un salto de línea.
         */
        fun vestir(sb: SpannableStringBuilder, barra: Int, fondo: Int, densidad: Float) {
            for (q in sb.getSpans(0, sb.length, QuoteSpan::class.java)) {
                val ini = sb.getSpanStart(q)
                val fin = sb.getSpanEnd(q)
                val flags = sb.getSpanFlags(q)
                sb.removeSpan(q)
                if (ini < 0 || fin > sb.length || ini >= fin) continue
                sb.setSpan(CitaSpan(barra, fondo, densidad), ini, fin, conPrioridad(flags))
            }
        }
    }
}
