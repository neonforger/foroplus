package com.fcplus.forocoches

import android.text.SpannableStringBuilder
import android.text.style.RelativeSizeSpan
import android.text.style.TypefaceSpan

/**
 * El [SIZE] de ForoCoches.
 *
 * FC convierte `[SIZE=N]` en `<font size="N">`, y `Html.fromHtml` solo lee `color` y `face` de
 * un `<font>`: el tamaño se perdía y todo salía igual (lo reportó Juan el 2026-10-16). El
 * extractor (`marcarTamanos`) lo pasa a `face="fcsize-N"`, que fromHtml sí convierte en un
 * [TypefaceSpan] y cierra bien aunque vaya anidado, y aquí se cambia ese span por un
 * [RelativeSizeSpan]. Así el texto no lleva ninguna marca: lo que se copia o se cita sale limpio.
 */
object TamanoLetra {

    private const val PREFIJO = "fcsize-"

    /**
     * Cuánto crece el tamaño N (1-7) respecto al texto normal del mensaje.
     *
     * Son los píxeles que da un navegador a cada `size` (10, 13, 16, 18, 24, 32 y 48) divididos
     * entre los del 2, que es el que pone **por defecto** el editor de vBulletin: el texto que
     * nadie ha agrandado no puede salir encogido. El 7 se queda en 3x (en un navegador sería
     * 3,7x) para que una palabra suelta quepa en el ancho de un móvil.
     */
    fun factor(n: Int): Float = when (n.coerceIn(1, 7)) {
        1 -> 0.77f
        2 -> 1f
        3 -> 1.23f
        4 -> 1.38f
        5 -> 1.85f
        6 -> 2.46f
        else -> 3f
    }

    /** Cambia los `fcsize-N` que deja fromHtml por su tamaño. Devuelve cuántos había. */
    fun aplicar(sb: SpannableStringBuilder): Int {
        var n = 0
        for (span in sb.getSpans(0, sb.length, TypefaceSpan::class.java)) {
            val familia = span.family ?: continue
            if (!familia.startsWith(PREFIJO)) continue
            val ini = sb.getSpanStart(span)
            val fin = sb.getSpanEnd(span)
            val flags = sb.getSpanFlags(span)
            sb.removeSpan(span)
            val tam = familia.removePrefix(PREFIJO).toIntOrNull() ?: continue
            if (ini < fin) {
                sb.setSpan(RelativeSizeSpan(factor(tam)), ini, fin, flags)
                n++
            }
        }
        return n
    }
}
