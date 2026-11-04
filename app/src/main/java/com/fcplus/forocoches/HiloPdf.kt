package com.fcplus.forocoches

import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Una copia descargada convertida en un HTML para imprimir o guardar como PDF (Juan,
 * 2026-10-30: "a ver si habilitas la exportación a pdf"). Lo pinta un WebView sin red y lo
 * imprime Android: ver [ExportarPdf].
 *
 * Parte del JSON de la copia tal cual (el mismo del motor, ver [AlmacenDescargas]) y de lo
 * medido en una copia real el 2026-11-04:
 * - **Citas**: `div.squote > blockquote`, con un `div` interior de `max-height: 410px;
 *   overflow-y: auto` (inline). En papel eso CORTA las citas largas: se anula con `!important`.
 * - **Fotos**: `img.imgpost`. Las guardadas (miniaturas de 400 px) se sirven desde la copia por
 *   una URL propia ([URL_IMAGEN] + índice) — la del HTML puede llevar `&amp;` y no casaría con
 *   la clave con la que se guardó. Las que no se guardaron salen como enlace.
 * - **Emoticonos**: `img.inlineimg` de `/images/smilies/`. No se guardan: se dejan tal cual y
 *   el WebView los pide (es lo único que sale a la red).
 * - **Reproductores**: una marca `U+E002 n U+E003` en el texto ([TramosEmbed]) + la lista
 *   `embeds`. Un PDF no reproduce nada: cada uno se cambia por una línea con su enlace.
 * - **Spoilers** (gotcha 27): texto del color del fondo con el CSS de FC. Aquí no hay ese CSS y
 *   en papel no se pueden destapar, así que salen visibles, en un recuadro.
 */
object HiloPdf {

    /** Prefijo de las fotos guardadas: [ExportarPdf] las sirve desde la copia. */
    const val URL_IMAGEN = "https://copia.foroplus.invalid/img/"

    /** El HTML y, por índice, la URL original de cada foto guardada que usa. */
    class Documento(val html: String, val imagenes: List<String>)

    private val IMG = Regex("<img\\b[^>]*>", RegexOption.IGNORE_CASE)
    private val SRC = Regex("\\bsrc\\s*=\\s*\"([^\"]*)\"", RegexOption.IGNORE_CASE)
    private val MARCA = Regex("${TramosEmbed.ABRE}(\\d+)${TramosEmbed.CIERRA}")
    private val SCRIPT = Regex("<script\\b[\\s\\S]*?</script>", RegexOption.IGNORE_CASE)
    private val IFRAME = Regex("<iframe\\b[^>]*>(?:[\\s\\S]*?</iframe>)?", RegexOption.IGNORE_CASE)

    /**
     * @param copiaJson el `hilo.json` de la copia.
     * @param guardada si una foto (por su URL, tal como sale en el HTML) está en la copia.
     * @param ahora para la línea "Generado el…" (inyectable para los tests).
     */
    fun documento(copiaJson: String, guardada: (String) -> Boolean, ahora: Long = System.currentTimeMillis()): Documento? {
        val raiz = try { JSONObject(copiaJson) } catch (_: Exception) { return null }
        val paginas = raiz.optJSONArray("contenido") ?: return null
        val tid = raiz.optString("tid")
        val titulo = raiz.optString("titulo").ifBlank { "Hilo $tid" }
        val imagenes = ArrayList<String>()

        val cuerpo = StringBuilder()
        var n = 0
        for (p in 0 until paginas.length()) {
            val posts = paginas.optJSONObject(p)?.optJSONArray("posts") ?: continue
            for (i in 0 until posts.length()) {
                val o = posts.optJSONObject(i) ?: continue
                n++
                cuerpo.append(mensaje(n, o, guardada, imagenes))
            }
        }
        if (n == 0) return null

        val guardadoEn = raiz.optLong("guardadoEn", 0L)
        val enlace = "https://forocoches.com/foro/showthread.php?t=${Descargas.carpetaDe(tid)}"
        val cabecera = buildString {
            append("<header><h1>").append(esc(titulo)).append("</h1><p class=\"meta\">")
            append(Descargas.resumen(raiz.optInt("paginas", paginas.length()), n))
            if (guardadoEn > 0) append(" · copia del ").append(fecha(guardadoEn))
            append("<br><a href=\"").append(esc(enlace)).append("\">").append(esc(enlace)).append("</a>")
            append("</p></header>")
        }
        val pie = "<footer>Generado con ForoPlus el ${fecha(ahora)}</footer>"
        val html = "<!doctype html><html lang=\"es\"><head><meta charset=\"utf-8\">" +
            "<title>${esc(titulo)}</title><style>$CSS</style></head><body>" +
            cabecera + cuerpo + pie + "</body></html>"
        return Documento(html, imagenes)
    }

    private fun mensaje(n: Int, o: JSONObject, guardada: (String) -> Boolean, imagenes: MutableList<String>): String {
        val autor = o.optString("author").trim().ifEmpty { "Anónimo" }
        val fecha = o.optString("date").trim()
        val editadoPor = o.optString("editBy").trim()
        val editadoCuando = o.optString("editDate").trim()
        val embeds = o.optJSONArray("embeds")
        val usados = HashSet<Int>()

        var html = o.optString("html")
        html = SCRIPT.replace(html, "")
        html = IFRAME.replace(html, "")
        html = MARCA.replace(html) { m ->
            val i = m.groupValues[1].toIntOrNull() ?: return@replace ""
            val e = embeds?.optJSONObject(i) ?: return@replace ""
            if (!usados.add(i)) return@replace ""
            lineaEmbed(e)
        }
        html = IMG.replace(html) { m -> imagen(m.value, guardada, imagenes) }
        // Reproductores sin marca en el texto: al final, como hace la app.
        val sobrantes = StringBuilder()
        if (embeds != null) for (i in 0 until embeds.length()) {
            if (i !in usados) embeds.optJSONObject(i)?.let { sobrantes.append(lineaEmbed(it)) }
        }

        return buildString {
            append("<article><div class=\"autor\"><span class=\"num\">#").append(n).append("</span> ")
            append("<b>@").append(esc(autor)).append("</b>")
            if (fecha.isNotEmpty()) append(" <span class=\"fecha\">· ").append(esc(fecha)).append("</span>")
            append("</div><div class=\"texto\">").append(html.trim()).append(sobrantes).append("</div>")
            if (editadoCuando.isNotEmpty() || editadoPor.isNotEmpty()) {
                append("<div class=\"editado\">Editado")
                if (editadoPor.isNotEmpty()) append(" por ").append(esc(editadoPor))
                if (editadoCuando.isNotEmpty()) append(" · ").append(esc(editadoCuando))
                append("</div>")
            }
            append("</article>")
        }
    }

    private fun imagen(tag: String, guardada: (String) -> Boolean, imagenes: MutableList<String>): String {
        val src = SRC.find(tag)?.groupValues?.get(1)?.trim().orEmpty()
        if (src.isEmpty()) return ""
        if (Descargas.esSmiley(src)) return tag
        if (guardada(src)) {
            var i = imagenes.indexOf(src)
            if (i < 0) { imagenes.add(src); i = imagenes.size - 1 }
            return "<img class=\"foto\" src=\"$URL_IMAGEN$i\" alt=\"\">"
        }
        // Sin copia de la foto no hay nada que pintar sin salir a la red: queda el enlace.
        val url = src.replace("&amp;", "&")
        return "<a class=\"sinfoto\" href=\"${esc(url)}\">[foto] ${esc(url)}</a>"
    }

    private fun lineaEmbed(e: JSONObject): String {
        val url = e.optString("url").trim()
        if (url.isEmpty()) return ""
        val tipo = when (e.optString("kind")) {
            "youtube" -> "Vídeo de YouTube"
            "twitter" -> "Publicación de X"
            "instagram" -> "Publicación de Instagram"
            "tiktok" -> "Vídeo de TikTok"
            "video" -> "Vídeo"
            else -> "Contenido"
        }
        return "<p class=\"embed\">▶ $tipo: <a href=\"${esc(url)}\">${esc(url)}</a></p>"
    }

    /**
     * El nombre que propone "Guardar como PDF": el del hilo, sin los caracteres que un sistema
     * de ficheros no acepta y sin pasarse de largo. **Sin punto final**: medido en el Samsung, un
     * título acabado en "." daba `…palabras.PDF` (el selector lo tomaba como la extensión).
     */
    fun nombreArchivo(titulo: String): String {
        val limpio = titulo.replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}]"), " ")
            .replace(Regex("\\s+"), " ").trim().take(80).trimEnd('.', ' ', '…').trim()
        return if (limpio.isEmpty()) "ForoPlus - hilo" else "ForoPlus - $limpio"
    }

    private fun fecha(ms: Long): String =
        SimpleDateFormat("d 'de' MMMM 'de' yyyy, HH:mm", Locale("es", "ES")).format(Date(ms))

    private fun esc(s: String): String = s
        .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

    private val CSS = """
        @page { size: A4; margin: 16mm 14mm; }
        body { font-family: sans-serif; font-size: 11pt; line-height: 1.45; color: #111; margin: 0; }
        header { border-bottom: 2px solid #333; margin-bottom: 14px; padding-bottom: 8px; }
        h1 { font-size: 17pt; margin: 0 0 6px; }
        .meta { color: #555; font-size: 9.5pt; margin: 0; }
        .meta a { color: #555; word-break: break-all; }
        article { border-bottom: 1px solid #ccc; padding: 9px 0 11px; }
        .autor { break-after: avoid; page-break-after: avoid; margin-bottom: 4px; }
        .num { color: #777; font-size: 9pt; }
        .fecha { color: #666; font-size: 9pt; }
        .texto { overflow-wrap: anywhere; }
        .editado { color: #777; font-size: 8.5pt; font-style: italic; margin-top: 4px; }
        .squote, blockquote { margin: 6px 0; }
        blockquote { border-left: 3px solid #999; background: #f2f2f2; padding: 6px 10px; margin-left: 0; font-size: 10pt; }
        blockquote div { max-height: none !important; overflow: visible !important; margin-bottom: 0 !important; }
        blockquote a { color: #333; text-decoration: none; }
        img.foto { max-width: 60%; height: auto; display: block; margin: 6px 0; break-inside: avoid; }
        img.inlineimg { height: 1.25em; vertical-align: middle; }
        a { color: #1a4fa0; }
        .sinfoto, .embed a { word-break: break-all; }
        .embed { background: #f6f6f6; border: 1px solid #ddd; padding: 5px 8px; font-size: 9.5pt; }
        .spoiler { background: #ececec !important; color: #111 !important; border: 1px dashed #999; padding: 4px 6px; }
        footer { color: #888; font-size: 8.5pt; margin-top: 14px; text-align: center; }
    """.trimIndent()
}
