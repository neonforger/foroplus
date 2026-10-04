package com.fcplus.forocoches

/**
 * Dónde hay códigos de smiley (`:roto2:`) en lo que se está escribiendo.
 *
 * Lo pidieron los testers (2026-09-24): que al terminar de escribir `:roto2:` en el composer
 * se vea el emoticono. El texto NO se cambia —por debajo sigue siendo `:roto2:`, que es lo que
 * se publica—; solo se pinta la imagen encima (ver [SmileysEnCaja]). Así no hay forma de colar
 * un `U+FFFC` en el mensaje (ver [TextoPlano]).
 *
 * Puro: la lista de códigos es la que da FC (`misc.php?do=getsmilies`), no una inventada.
 */
object SmileysEnTexto {

    data class Tramo(val inicio: Int, val fin: Int, val codigo: String)

    /**
     * Los códigos de [texto], sin solaparse. Si dos códigos empiezan en el mismo sitio gana el
     * más largo (`:roto2:` antes que un hipotético `:roto`), y se avanza de izquierda a derecha.
     */
    fun buscar(texto: CharSequence, codigos: Collection<String>): List<Tramo> {
        if (texto.isEmpty() || codigos.isEmpty()) return emptyList()
        val s = texto.toString()
        // Por primer carácter, para no probar cientos de códigos en cada posición.
        val porInicial = codigos.filter { it.isNotEmpty() }
            .groupBy { it[0] }
            .mapValues { (_, l) -> l.sortedByDescending { it.length } }
        val out = ArrayList<Tramo>()
        var i = 0
        while (i < s.length) {
            val candidatos = porInicial[s[i]]
            val hallado = candidatos?.firstOrNull { s.startsWith(it, i) }
            if (hallado != null) {
                out.add(Tramo(i, i + hallado.length, hallado))
                i += hallado.length
            } else {
                i++
            }
        }
        return out
    }
}
