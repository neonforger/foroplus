package com.fcplus.forocoches

/**
 * Convierte un enlace pegado a pelo en la **tarjeta** que pinta ForoCoches.
 *
 * Lo pidieron Miguel y Márquez: pegas un enlace de Instagram o YouTube y en el foro queda un
 * enlace azul y feo, mientras que el mismo contenido puesto desde el botón ▶ del composer sale
 * como tarjeta. La diferencia **no está en la URL, está en la etiqueta**: FC solo incrusta lo
 * que le llega envuelto en su BBCode.
 *
 * Las cuatro etiquetas están **verificadas contra mensajes reales del foro** (2026-09-12,
 * pidiéndole a FC la cita en BBCode de posts que ya tenían tarjeta, que es de solo lectura):
 * ```
 * [IG]https://www.instagram.com/reel/Dc-bz1jA5wd/?stkn=cHFjajk4czVtY3pn[/IG]
 * [TWEET]https://twitter.com/ItsmeskylerB/status/1284105262496804864?s=20[/TWEET]
 * [TIKTOK]https://www.tiktok.com/@planeta_asombroso/video/7229670784481053979[/TIKTOK]
 * [YOUTUBE]dxk3IKcPgYU[/YOUTUBE]
 * ```
 * De ahí dos cosas que hay que respetar: las tres primeras **tragan la URL entera** (con sus
 * parámetros, y `/reel/` vale perfectamente — no hay que "arreglar" la URL), y **YouTube quiere
 * solo el ID**.
 *
 * Lo que NO se toca, y es la mitad del trabajo: nada que ya esté dentro de un BBCode. Envolver
 * dos veces rompería la tarjeta, y reescribir lo que hay dentro de una cita sería meterse en el
 * mensaje de otro.
 */
object EnlacesEmbebibles {

    private val URL = Regex("""https?://[^\s\[\]<>"]+""")

    /** Tramos intocables: lo que ya vive dentro de una etiqueta. */
    private val PROTEGIDO = Regex(
        """\[(quote|code|ig|tweet|tiktok|youtube|fb|vocaroo|img|url)[^\]]*].*?\[/\1]""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    )

    private val IG = Regex("""instagram[.]com/(?:p|reel|reels|tv)/[A-Za-z0-9_-]+""", RegexOption.IGNORE_CASE)
    private val TWEET = Regex("""(?:twitter|x)[.]com/[^/\s]+/status/\d+""", RegexOption.IGNORE_CASE)
    private val TIKTOK = Regex("""(?:tiktok[.]com/@[^/\s]+/video/\d+|vm[.]tiktok[.]com/[A-Za-z0-9]+)""", RegexOption.IGNORE_CASE)
    private val YT = Regex(
        """(?:youtube[.]com/(?:watch\?(?:[^ ]*&)?v=|shorts/|embed/)|youtu[.]be/)([A-Za-z0-9_-]{6,})""",
        RegexOption.IGNORE_CASE
    )

    /** El BBCode de [url], o null si no es de ninguna plataforma que FC sepa incrustar. */
    fun tarjetaDe(url: String): String? = when {
        YT.containsMatchIn(url) -> "[YOUTUBE]" + YT.find(url)!!.groupValues[1] + "[/YOUTUBE]"
        IG.containsMatchIn(url) -> "[IG]$url[/IG]"
        TWEET.containsMatchIn(url) -> "[TWEET]$url[/TWEET]"
        TIKTOK.containsMatchIn(url) -> "[TIKTOK]$url[/TIKTOK]"
        else -> null
    }

    fun conTarjetas(texto: String): String {
        val intocables = PROTEGIDO.findAll(texto).map { it.range }.toList()
        fun protegido(i: Int) = intocables.any { i in it }
        val sb = StringBuilder()
        var desde = 0
        for (m in URL.findAll(texto)) {
            if (protegido(m.range.first)) continue
            val tarjeta = tarjetaDe(m.value) ?: continue
            sb.append(texto, desde, m.range.first).append(tarjeta)
            desde = m.range.last + 1
        }
        if (desde == 0) return texto
        sb.append(texto, desde, texto.length)
        return sb.toString()
    }
}
