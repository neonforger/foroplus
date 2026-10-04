package com.fcplus.forocoches

/**
 * La cita de un mensaje en BBCode, tal y como la escribe ForoCoches.
 *
 * La app construía sus citas a mano, pasando el HTML del mensaje a texto plano. Eso perdía
 * **las fotos, los smileys y todo el formato**, y encima dejaba basura visible (gotcha 21).
 * Desde el 2026-08-28 la cita se le pide a FC (`newreply.php?do=newreply&p=<pid>&wysiwyg=0`),
 * que la devuelve hecha:
 *
 * ```
 * [QUOTE=V. Jones;518303666]Estan naranajas :roto2:
 *
 * [IMG]https://i.imgur.com/7kaUa1L.png[/IMG][/QUOTE]
 * ```
 *
 * Reconstruirla nosotros era la opción barata y **está mal**: el smiley de ese mensaje es el
 * fichero `goofy.gif` y su código es `:roto2:`, así que adivinarlo por el nombre del fichero
 * produce un smiley distinto al que puso el autor.
 *
 * Aquí solo vive lo que hay que hacerle a esa cadena: sacarle el envoltorio y volverla legible
 * para la tarjeta del composer.
 */
object CitaBbcode {

    /** Lo que se enseña en lugar de una foto. Ver [previsualizacion] y [sinMarcas]. */
    const val MARCA_IMAGEN = "🖼 imagen"

    /** Quita el `[QUOTE=…]` de fuera y su cierre. Lo de dentro se deja intacto. */
    fun sinEnvoltura(bbcode: String): String {
        val t = bbcode.trim()
        val abre = Regex("""^\[QUOTE[^\]]*\]""", RegexOption.IGNORE_CASE).find(t) ?: return t
        val cuerpo = t.substring(abre.value.length)
        return if (cuerpo.endsWith("[/QUOTE]", ignoreCase = true))
            cuerpo.dropLast("[/QUOTE]".length).trim() else cuerpo.trim()
    }

    /**
     * Versión legible para la tarjeta del composer: sin etiquetas y con las fotos marcadas.
     *
     * Las imágenes se enseñan como **🖼 imagen** y no como su URL: una tarjeta de dos líneas
     * llena de `https://i.imgur.com/7kaUa1L.png` no le dice nada a nadie, y lo que el usuario
     * quiere saber es *qué* está citando.
     */
    fun previsualizacion(bbcode: String): String =
        sinEnvoltura(bbcode)
            .replace(Regex("""\[IMG\][^\[]*\[/IMG\]""", RegexOption.IGNORE_CASE), MARCA_IMAGEN)
            .replace(Regex("""\[/?[A-Za-z*]+(=[^\]]*)?\]"""), " ")
            .replace(Regex("""\s+"""), " ")
            .trim()

    /**
     * Quita las marcas de foto de un texto que ha pasado por manos del usuario.
     *
     * Hace falta porque el diálogo de recortar la cita se rellena con [previsualizacion]: si el
     * usuario recorta y guarda, lo que escriba se publica TAL CUAL, y un "🖼 imagen" literal
     * dentro de un mensaje del foro es basura. Al recortar se pierden las fotos —es lo que pide
     * quien recorta—, pero se pierden en silencio, no dejando un cartel.
     */
    fun sinMarcas(texto: String): String =
        texto.replace(MARCA_IMAGEN, "").replace(Regex("""\s{2,}"""), " ").trim()

    /**
     * Pasa las citas pendientes a la caja de texto, como BBCode editable.
     *
     * Lo pidieron Juan (tres veces), javier hg y Green Floyd (2026-09-21/24): con la cita como
     * tarjeta no se podía recortar con formato, ponerla en negrita ni colocarla en medio de la
     * respuesta. Van TODAS a la vez y en su orden: pasar una sola dejaría las demás como tarjeta
     * y, como las tarjetas se publican delante del texto, el orden saldría al revés.
     *
     * @param bloques el BBCode de cada cita (`[QUOTE=autor;pid]…[/QUOTE]`), en orden.
     * @return el texto nuevo y dónde dejar el cursor: justo debajo de las citas, que es donde
     *   se escribe la respuesta.
     */
    fun aTexto(bloques: List<String>, texto: String): Pair<String, Int> {
        val citas = bloques.filter { it.isNotBlank() }.joinToString("") { it.trimEnd() + "\n" }
        if (citas.isEmpty()) return texto to texto.length
        val resto = texto.trimStart('\n')
        val nuevo = if (resto.isEmpty()) citas + "\n" else citas + "\n" + resto
        return nuevo to citas.length
    }
}
