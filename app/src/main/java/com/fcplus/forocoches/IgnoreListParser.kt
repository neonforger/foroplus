package com.fcplus.forocoches

import org.jsoup.Jsoup

/**
 * Saca los nombres de la lista de ignorados de la cuenta a partir del HTML de
 * `profile.php?do=ignorelist`.
 *
 * Antes esto era `IgnoreListFetcher`, que además de parsear **pedía la página por HTTP
 * nativo**. Esa petición se mudó al motor el 2026-08-21 (`fcLoadIgnoreList` en
 * extractor.js): el HTTP nativo funcionaba solo mientras Cloudflare se creyera el
 * User-Agent copiado del WebView, y el día que dejara de creérselo la lista habría
 * vuelto vacía sin un solo error visible. La extracción se quedó aquí porque ya estaba
 * cubierta por tests contra markup real y no era lo que había que arreglar.
 */
object IgnoreListParser {

    /**
     * Cada usuario ignorado es un elemento con `id="userN"` (sea `<li>`, `<div>`…), así que
     * se selecciona por id sin depender de la etiqueta, y el nombre sale de su enlace a
     * `member.php`.
     */
    fun parse(html: String): List<String> =
        Jsoup.parse(html).select("[id~=^user\\d+$]")
            .mapNotNull { row ->
                row.selectFirst("a[href*=member.php]")?.text()?.trim()?.ifBlank { null }
            }
}
