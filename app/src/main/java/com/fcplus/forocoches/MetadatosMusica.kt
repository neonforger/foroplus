package com.fcplus.forocoches

import org.json.JSONObject

/** Lo que cuentan los oEmbed públicos de YouTube y Spotify (sin clave ni sesión). */
data class DatosOembed(val titulo: String, val autor: String?, val portada: String?)

/** Lo que dan los `<meta>` de la página pública de una canción de Spotify. */
data class DatosPaginaSpotify(val artista: String?, val duracionSeg: Int?, val album: String?)

/**
 * Parsers puros: la red va aparte. Medido el 2026-11-07: el oEmbed de Spotify no trae artista
 * ni duración, pero su página pública sí en `music:musician_description` y `music:duration`;
 * `og:description` es "Artista · Álbum · Song · Año". `music:album` es solo una URL, por eso
 * el nombre del álbum sale de `og:description`.
 */
object MetadatosMusica {

    /** YouTube y Spotify; null si no hay `title` (o está vacío) o el JSON no se lee. */
    fun oembed(json: String): DatosOembed? = try {
        val o = JSONObject(json)
        val titulo = o.optString("title", "").trim()
        if (titulo.isEmpty()) null
        else DatosOembed(
            titulo = titulo,
            autor = o.optString("author_name", "").trim().ifEmpty { null },
            portada = o.optString("thumbnail_url", "").trim().ifEmpty { null },
        )
    } catch (e: Exception) {
        null
    }

    /** Nunca lanza: lo que no está, a null. */
    fun paginaSpotify(html: String): DatosPaginaSpotify = try {
        val metas = leerMetas(html)
        val artista = campo(metas, "music:musician_description")
        val duracion = campo(metas, "music:duration")?.toIntOrNull()?.takeIf { it > 0 }
        val album = campo(metas, "og:description")?.split(" · ")?.getOrNull(1)?.trim()?.ifEmpty { null }
        DatosPaginaSpotify(artista, duracion, album)
    } catch (e: Exception) {
        DatosPaginaSpotify(null, null, null)
    }

    private val etiquetaMeta = Regex("<meta\\b[^>]*>", RegexOption.IGNORE_CASE)
    private val atributo = Regex("([a-zA-Z:_-]+)\\s*=\\s*(?:\"([^\"]*)\"|'([^']*)')")

    /** Primer valor de cada `property`/`name`; el orden de los atributos no importa. */
    private fun leerMetas(html: String): Map<String, String> {
        val res = HashMap<String, String>()
        for (m in etiquetaMeta.findAll(html)) {
            var clave: String? = null
            var valor: String? = null
            for (a in atributo.findAll(m.value)) {
                val nombre = a.groupValues[1].lowercase()
                val v = if (a.groups[2] != null) a.groupValues[2] else a.groupValues[3]
                when (nombre) {
                    "property", "name" -> if (clave == null) clave = v
                    "content" -> valor = v
                }
            }
            if (clave != null && valor != null && clave !in res) res[clave] = valor
        }
        return res
    }

    /** Entidad numérica inválida (fuera de rango, sustituto suelto...): se deja el texto tal cual. */
    private fun codepoint(cp: Int?, original: String): String =
        if (cp != null && Character.isValidCodePoint(cp)) {
            try { String(Character.toChars(cp)) } catch (e: Exception) { original }
        } else original

    /** Un campo, decodificado y recortado; si falla, solo ese campo queda a null. */
    private fun campo(metas: Map<String, String>, clave: String): String? = try {
        metas[clave]?.let { decodificar(it) }?.trim()?.ifEmpty { null }
    } catch (e: Exception) {
        null
    }

    private val entidad = Regex("&(#[xX][0-9a-fA-F]+|#[0-9]+|[a-zA-Z]+);")

    /** Entidades HTML de los valores; sin `android.text.Html` para que valga igual en tests. */
    private fun decodificar(s: String): String = entidad.replace(s) { m ->
        val e = m.groupValues[1]
        when {
            e.startsWith("#x") || e.startsWith("#X") -> codepoint(e.substring(2).toIntOrNull(16), m.value)
            e.startsWith("#") -> codepoint(e.substring(1).toIntOrNull(), m.value)
            else -> when (e) {
                "amp" -> "&"; "quot" -> "\""; "apos" -> "'"; "lt" -> "<"; "gt" -> ">"
                "nbsp" -> " "; "middot" -> "·"
                else -> m.value
            }
        }
    }
}
