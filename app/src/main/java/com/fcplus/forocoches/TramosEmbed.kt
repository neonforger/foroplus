package com.fcplus.forocoches

import android.text.SpannableStringBuilder

/**
 * Coloca cada reproductor (YouTube, tweet, Vocaroo…) **en su sitio** dentro del mensaje.
 *
 * Un reproductor es una vista y no cabe dentro del texto, así que hasta ahora se pintaban
 * todos al FINAL del mensaje y en su lugar quedaba un hueco en blanco. Lo reportó Juan el
 * 2026-10-20 con un post de "texto, YouTube, «Y de propina», YouTube": en la app salían los dos
 * textos con un agujero entre ellos y los dos vídeos apilados debajo; en la web, cada uno donde
 * iba.
 *
 * `extractor.js` deja en el sitio de cada embed una marca de texto `<n>`, con `n` su
 * posición en la lista de embeds del mensaje. El mensaje se pinta ENTERO como siempre (citas,
 * spoilers, enlaces, fotos: todas las pasadas ven el texto completo) y solo al final se parte
 * por las marcas: el primer trozo va en el TextView de siempre y detrás de cada reproductor va
 * el texto que le sigue.
 *
 * Son caracteres del Área de Uso Privado por lo mismo que los spoilers ([Spoilers]): nadie los
 * teclea y `fromHtml` los deja pasar intactos. ``/`` ya son de los spoilers.
 */
object TramosEmbed {

    const val ABRE = ''
    const val CIERRA = ''

    private val MARCA = Regex("$ABRE(\\d+)$CIERRA")

    /**
     * El mensaje partido: [cabeza] es lo que va antes del primer reproductor y [tras] dice, en
     * orden de aparición, qué texto va detrás de cada uno (por su índice en la lista de embeds).
     */
    class Partido(val cabeza: CharSequence, val tras: Map<Int, CharSequence>)

    /**
     * Parte [texto] por sus marcas. Solo cuentan las de un embed que exista (`0 until
     * cuantos`) y la primera vez que sale cada una; el resto se borran sin partir nada, porque
     * dibujadas serían una cajita vacía.
     *
     * Se quita el espacio en blanco que toca a cada marca: el `<div>` en el que FC envuelve el
     * reproductor deja saltos de línea alrededor, y con el reproductor ya en medio serían un
     * hueco de más.
     */
    fun partir(texto: CharSequence, cuantos: Int): Partido {
        val vistas = HashSet<Int>()
        val buenas = MARCA.findAll(texto).filter { m ->
            val n = m.groupValues[1].toIntOrNull()
            n != null && n < cuantos && vistas.add(n)
        }.toList()
        if (buenas.isEmpty()) return Partido(limpiar(texto, 0, texto.length, false, false), emptyMap())
        val cabeza = limpiar(texto, 0, buenas.first().range.first, false, true)
        val tras = LinkedHashMap<Int, CharSequence>()
        for ((i, m) in buenas.withIndex()) {
            val desde = m.range.last + 1
            val siguiente = buenas.getOrNull(i + 1)
            val hasta = siguiente?.range?.first ?: texto.length
            tras[m.groupValues[1].toInt()] = limpiar(texto, desde, hasta, true, siguiente != null)
        }
        return Partido(cabeza, tras)
    }

    /**
     * Índices de los embeds en el orden en que aparecen en [html], y detrás los que no tengan
     * marca (un mensaje guardado antes de existir las marcas, por ejemplo), que se quedan al
     * final como siempre.
     */
    fun orden(html: String, cuantos: Int): List<Int> {
        val orden = LinkedHashSet<Int>()
        for (m in MARCA.findAll(html)) {
            val n = m.groupValues[1].toIntOrNull() ?: continue
            if (n < cuantos) orden.add(n)
        }
        for (n in 0 until cuantos) orden.add(n)
        return orden.toList()
    }

    /** [s] sin marcas. Para el texto plano de un mensaje (citar, copiar), que se PUBLICA. */
    fun sinMarcas(s: String): String = MARCA.replace(s, "")

    /** El tramo `[desde, hasta)` sin las marcas que sobren y sin el blanco que toque a otra. */
    private fun limpiar(
        texto: CharSequence, desde: Int, hasta: Int, inicio: Boolean, fin: Boolean
    ): CharSequence {
        var a = desde
        var b = hasta
        if (inicio) while (a < b && esBlanco(texto[a])) a++
        if (fin) while (b > a && esBlanco(texto[b - 1])) b--
        val trozo = texto.subSequence(a, b)
        if (MARCA.find(trozo) == null) return trozo
        val sb = SpannableStringBuilder(trozo)
        for (m in MARCA.findAll(trozo).toList().asReversed()) sb.delete(m.range.first, m.range.last + 1)
        return sb
    }

    private fun esBlanco(c: Char) = c.isWhitespace() || c == ' '
}
