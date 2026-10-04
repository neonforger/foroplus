package com.fcplus.forocoches

/**
 * La cita del mensaje al que estás respondiendo, en un MP.
 *
 * Lo pidió el dueño el 2026-08-27: al responder un privado no se veía a qué estabas
 * respondiendo, y en el foro sí sale citado. La cita **no la construimos nosotros**: FC ya
 * rellena la textarea de `private.php?do=newpm&pmid=N` con un `[QUOTE=...]…[/QUOTE]` hecho
 * (medido: 1.459 caracteres en un MP real). Hasta ahora la app la tiraba a la basura, porque
 * al enviar sustituía el campo `message` entero por lo que hubieras escrito.
 *
 * Aquí solo hay dos cuentas: sacar el texto legible para enseñarlo en la tarjeta, y volver a
 * juntar la cita con la respuesta al enviar.
 */
object CitaPrivada {

    /** Quién escribió lo citado: `[QUOTE=Fulano;12345]` → `Fulano`. "" si no se puede leer. */
    fun autor(bbcode: String): String {
        val m = Regex("""\[QUOTE=([^;\]]+)""", RegexOption.IGNORE_CASE).find(bbcode)
        return m?.groupValues?.get(1)?.trim().orEmpty()
    }

    /**
     * Texto legible de la cita, para la tarjeta: **solo lo que escribió quien te responde**.
     *
     * Aquí está la parte que importa. FC no manda una cita plana, manda la CADENA entera:
     * ```
     * [QUOTE=Iznogud]           ← el mensaje al que respondes
     *   [QUOTE=neonforger]…[/QUOTE]   ← tu mensaje anterior, anidado dentro
     *   …lo que él escribió…
     * [/QUOTE]
     * ```
     * y el anidado empieza **pegado al principio** (medido: en la posición 15 de 1.460). Si se
     * quitan las etiquetas y se enseñan las tres primeras líneas, lo que sale es **tu propio
     * mensaje viejo**, y parece que estás respondiendo al primer mensaje de la conversación en
     * vez de al último. Es justo lo que reportó el dueño el 2026-08-27.
     *
     * Así que se desenvuelve la cita de fuera y se tiran las de dentro **con su contenido**.
     * Lo que se ENVÍA sigue siendo el BBCode tal cual lo escribe FC — esto es solo para mirar.
     */
    fun previsualizacion(bbcode: String): String {
        var t = desenvolver(bbcode.trim())
        // Las anidadas se quitan de dentro hacia fuera hasta que no queda ninguna. El patrón
        // solo casa las que NO contienen otra apertura, o sea las más internas.
        val anidada = Regex(
            """\[QUOTE[^\]]*\](?:(?!\[QUOTE)[\s\S])*?\[/QUOTE\]""", RegexOption.IGNORE_CASE
        )
        var antes: String
        do { antes = t; t = anidada.replace(t, " ") } while (t != antes)
        return t
            .replace(Regex("""\[/?QUOTE[^\]]*\]""", RegexOption.IGNORE_CASE), " ")
            .replace(Regex("""\[/?[A-Za-z*]+(=[^\]]*)?\]"""), " ")
            .replace(Regex("""\s+"""), " ")
            .trim()
    }

    /** Quita el `[QUOTE=…]` de fuera y su cierre, si los hay. */
    private fun desenvolver(bbcode: String): String {
        val abre = Regex("""^\[QUOTE[^\]]*\]""", RegexOption.IGNORE_CASE).find(bbcode)
            ?: return bbcode
        if (!bbcode.endsWith("[/QUOTE]", ignoreCase = true)) return bbcode.substring(abre.value.length)
        return bbcode.substring(abre.value.length, bbcode.length - "[/QUOTE]".length)
    }

    /**
     * El mensaje que se manda: la cita delante y la respuesta detrás, como hace el foro.
     *
     * Si la cita está vacía (el usuario la quitó con la ✕) va solo la respuesta, **sin dejar
     * los saltos de línea sueltos** — el mismo cuidado que con la firma en [PostSignature]:
     * el fallo clásico es condicionar el texto y no el pegamento.
     */
    fun montar(cita: String, respuesta: String): String {
        val c = cita.trim()
        val r = respuesta.trim()
        return when {
            c.isEmpty() -> r
            r.isEmpty() -> c
            else -> "$c\n\n$r"
        }
    }
}
