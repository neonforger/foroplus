package com.fcplus.forocoches

import java.text.Normalizer

/**
 * La lupa del hilo: buscar un texto en la página que tienes cargada, como el "Buscar en la
 * página" del navegador. Lo pidió Juan el 2026-10-20 ("me falta una lupa para buscar un texto
 * concreto dentro del mismo hilo que estoy leyendo"); el dueño eligió esto antes que el buscador
 * de FC limitado al hilo, que corta en la ñ y en las tildes (gotcha 62), da extractos y lo frena
 * Cloudflare a las pocas búsquedas.
 *
 * Sin tildes ni mayúsculas: "pena" encuentra "Peña". Se pliega **carácter a carácter**, y no la
 * cadena entera con NFD, para que cada posición del texto plegado sea la misma del original: lo
 * encontrado se resalta en su sitio. Con la NFD de la cadena entera, cada tilde anterior
 * desplazaría el resaltado una letra.
 *
 * Aquí solo está lo que decide; dónde se pinta lo hace [PostAdapter] y la barra
 * [BuscarEnPaginaController].
 */
object BuscarEnPagina {

    /** Un carácter sin tilde y en minúscula. Los blancos (salto de línea, espacio duro) son un espacio. */
    fun plegar(c: Char): Char {
        if (c.isWhitespace() || c == ' ') return ' '
        if (c.code < 128) return c.lowercaseChar()
        return Normalizer.normalize(c.toString(), Normalizer.Form.NFD)[0].lowercaseChar()
    }

    fun plegar(s: CharSequence): String {
        val sb = StringBuilder(s.length)
        for (c in s) sb.append(plegar(c))
        return sb.toString()
    }

    /** La consulta lista para buscar. Vacía (o solo espacios) = no se busca nada. */
    fun preparar(consulta: String): String = plegar(consulta.trim())

    /**
     * Dónde aparece [consulta] (ya [preparar]ada) en [texto], sin solaparse. Se descarta lo que
     * pise una zona de [fuera]: el contenido de un spoiler no se puede resaltar sin destaparlo.
     */
    fun coincidencias(
        texto: CharSequence,
        consulta: String,
        fuera: List<IntRange> = emptyList()
    ): List<IntRange> {
        if (consulta.isEmpty()) return emptyList()
        val plegado = plegar(texto)
        val res = ArrayList<IntRange>()
        var desde = 0
        while (true) {
            val i = plegado.indexOf(consulta, desde)
            if (i < 0) break
            val tramo = i until i + consulta.length
            if (fuera.none { it.first <= tramo.last && tramo.first <= it.last }) {
                res.add(tramo)
                desde = tramo.last + 1
            } else {
                desde = i + 1
            }
        }
        return res
    }

    /** Una coincidencia: el mensaje (su posición en la lista) y cuál de las suyas es. */
    data class Sitio(val mensaje: Int, val n: Int)

    /** Todas las coincidencias de la página, en orden de lectura, según cuántas tiene cada mensaje. */
    fun sitios(porMensaje: List<Int>): List<Sitio> {
        val res = ArrayList<Sitio>()
        for ((m, cuantas) in porMensaje.withIndex()) for (n in 0 until cuantas) res.add(Sitio(m, n))
        return res
    }

    /**
     * Por cuál empezar: la primera en o por debajo del mensaje que estás leyendo, para no
     * mandarte arriba del todo a mitad de página. Si por debajo no hay, la primera. -1 = ninguna.
     */
    fun primeraDesde(sitios: List<Sitio>, mensaje: Int): Int {
        if (sitios.isEmpty()) return -1
        val i = sitios.indexOfFirst { it.mensaje >= mensaje }
        return if (i >= 0) i else 0
    }

    fun siguiente(actual: Int, total: Int): Int =
        if (total <= 0) -1 else if (actual < 0) 0 else (actual + 1) % total

    fun anterior(actual: Int, total: Int): Int =
        if (total <= 0) -1 else if (actual <= 0) total - 1 else actual - 1

    /** Lo que dice la barra. Sin ninguna elegida todavía (`actual` < 0), solo cuántas hay. */
    fun contador(actual: Int, total: Int, consultaVacia: Boolean): String = when {
        consultaVacia -> ""
        total == 0 -> "Sin resultados"
        actual < 0 -> if (total == 1) "1 resultado" else "$total resultados"
        else -> "${actual + 1} de $total"
    }
}
