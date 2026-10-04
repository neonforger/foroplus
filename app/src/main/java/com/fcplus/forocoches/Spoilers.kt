package com.fcplus.forocoches

import android.text.SpannableStringBuilder
import android.text.Spanned

/**
 * Marca el tramo de un spoiler dentro del mensaje ya renderizado. No pinta nada: solo sirve
 * para localizarlo después de que el resto de pasadas hayan movido los índices.
 *
 * [orden] es su posición entre los spoilers del mensaje, contada **en el HTML original**. Es lo
 * que identifica al spoiler cuando el usuario lo destapa, y por eso no vale usar el índice de
 * la lista que quede al final: si una cita larga se pliega y se lleva por delante un spoiler,
 * esa lista se acorta y el que estaba destapado pasaría a ser otro.
 */
class SpoilerSpan(val orden: Int)

/**
 * Spoilers de ForoCoches.
 *
 * **Cómo son de verdad** (sondeado en producción el 2026-08-26, con sesión):
 * ```html
 * <div onclick="this.style.color='#000000';" class="spoiler">lo que se quiere tapar</div>
 * ```
 * No es un desplegable: FC lo tapa **pintando el texto del mismo color que el fondo**
 * (`.spoiler { background-color: var(--background-color); color: var(--background-color) }`) y
 * lo revela con ese `onclick`. Como el render nativo tira el CSS de clases, en la app el
 * spoiler salía **destapado** — que es justo lo que reportó un tester el 2026-08-25.
 *
 * Aquí se tapa de otra manera, la misma que ya usan las citas largas: el contenido se sustituye
 * por un aviso tocable y se vuelve a pintar entero al tocarlo. En un móvil es más claro que
 * dejar un hueco de texto invisible, y de paso las fotos que haya dentro no se descargan hasta
 * que el usuario decide mirar (en FC sí se ven: el truco del color solo tapa el texto).
 *
 * ## Por qué marcas de texto y no una etiqueta `<spoiler>`
 *
 * Era el plan y **no funciona**. Medido con el parser real (TagSoup, el que usa
 * `Html.fromHtml`): una etiqueta que no conoce no se puede cerrar donde tú la cierras. Con
 * `<spoiler>uno</spoiler> y <spoiler>dos</spoiler>` la traza de llamadas al TagHandler es
 * ```
 * OPEN @0 · OPEN @6 · CLOSE @9 · CLOSE @9
 * ```
 * — o sea, ignora los cierres, **anida** las dos y las cierra las dos al final del documento.
 * Así no se puede delimitar nada. Con dos caracteres del Área de Uso Privado (que ni FC ni
 * nadie escribe) el tramo llega intacto pase lo que pase con las etiquetas.
 */
object Spoilers {

    /** Marca de apertura que emite `extractor.js`. Área de Uso Privado: nadie la teclea. */
    const val ABRE = '\uE000'

    /** Marca de cierre. */
    const val CIERRA = '\uE001'

    /** Lo que se ve en lugar del contenido tapado. */
    const val AVISO = "▮ Spoiler · toca para verlo"

    /**
     * Quita las marcas de [sb] y deja un [SpoilerSpan] cubriendo lo que había entre ellas.
     *
     * Se marca con spans **antes** que nada porque el resto de pasadas del render (plegar
     * citas, enlaces) editan el texto y mueven los índices; los spans se ajustan solos y unas
     * posiciones sueltas no.
     *
     * Devuelve cuántos spoilers se marcaron.
     */
    fun marcar(sb: SpannableStringBuilder): Int {
        // Las marcas se borran de atrás hacia delante para no invalidar las posiciones que
        // quedan por procesar.
        val abiertas = ArrayList<Int>()
        val tramos = ArrayList<Pair<Int, Int>>()
        val huerfanas = ArrayList<Int>()
        var i = 0
        while (i < sb.length) {
            when (sb[i]) {
                ABRE -> abiertas.add(i)
                CIERRA ->
                    // Se empareja con la ÚLTIMA abierta: los spoilers pueden venir anidados y
                    // emparejar con la primera daría un tramo cruzado.
                    if (abiertas.isNotEmpty()) tramos.add(abiertas.removeAt(abiertas.size - 1) to i)
                    else huerfanas.add(i)
            }
            i++
        }
        // Una marca sin pareja (mensaje cortado, HTML raro) no puede quedarse en el texto: se
        // dibujaría como una cajita vacía.
        huerfanas.addAll(abiertas)

        // Posiciones de TODAS las marcas, de mayor a menor: al borrar hay que ir por detrás.
        val marcas = (tramos.flatMap { listOf(it.first, it.second) } + huerfanas).sortedDescending()
        for (p in marcas) sb.delete(p, p + 1)

        // Tras borrar, cada tramo pierde 1 por su propia marca de apertura y 1 más por cada
        // marca anterior a él.
        for ((n, t) in tramos.sortedBy { it.first }.withIndex()) {
            val (a, c) = t
            val ini = a - marcas.count { it < a } 
            val fin = c - marcas.count { it < c }
            if (ini in 0 until fin && fin <= sb.length) {
                sb.setSpan(SpoilerSpan(n), ini, fin, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        }
        return tramos.size
    }
}
