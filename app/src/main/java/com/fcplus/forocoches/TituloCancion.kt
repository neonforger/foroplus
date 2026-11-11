package com.fcplus.forocoches

import java.text.Normalizer

data class CancionArtista(val cancion: String, val artista: String?)

/**
 * Limpia títulos de YouTube y los parte en canción y artista. Las listas de etiquetas y los casos
 * viven en contrato/musica-titulos.json, que comparte el servidor (su clave de canción usa lo mismo).
 */
object TituloCancion {
    val ETIQUETAS_VIDEO: List<String> = listOf(
        "official video", "official music video", "official audio", "official lyric video",
        "lyric video", "lyrics", "letra", "con letra", "audio", "visualizer", "videoclip oficial",
        "video oficial", "vídeo oficial", "hd", "hq", "4k", "4k remaster"
    )

    // "{año}" son 4 dígitos. Solo las usa el servidor para la clave; viven aquí por el contrato. limpiar() NO las quita: en pantalla se ve el título tal cual.
    val ETIQUETAS_VERSION: List<String> = listOf(
        "remastered", "remaster", "remasterizado", "remasterizada",
        "remastered {año}", "{año} remaster", "{año} remastered"
    )

    private val GRUPO = Regex("""\(([^()]*)\)|\[([^\[\]]*)]""")
    private val SEPARADORES = listOf(" - ", " – ", " — ")
    private val ESPACIOS = Regex("""\s+""")
    private val MARCAS = Regex("""\p{M}+""")

    private fun norm(s: String): String =
        Normalizer.normalize(s, Normalizer.Form.NFD).replace(MARCAS, "")
            .lowercase().trim().replace(ESPACIOS, " ")

    private val VIDEO_NORM = ETIQUETAS_VIDEO.map { norm(it) }.toSet()

    private fun esEtiqueta(contenido: String): Boolean {
        val n = norm(contenido)
        return n in VIDEO_NORM
    }

    private fun colapsar(s: String) = s.replace(ESPACIOS, " ").trim()

    /** Quita los (…) y […] cuyo contenido ENTERO es una etiqueta; el resto (p. ej. "(Directo)") se queda. */
    fun limpiar(titulo: String): String {
        val sin = GRUPO.replace(titulo) { m ->
            val c = m.groups[1]?.value ?: m.groups[2]?.value ?: ""
            if (esEtiqueta(c)) "" else m.value
        }
        return colapsar(sin)
    }

    fun limpiarCanal(canal: String): String {
        var c = colapsar(canal)
        val sufijos = listOf("vevo", " - topic", " oficial", " official")
        val bajo = c.lowercase()
        for (s in sufijos) {
            if (bajo.endsWith(s) && c.length > s.length) {
                c = c.substring(0, c.length - s.length)
                break
            }
        }
        return c.trim()
    }

    fun separar(titulo: String, canal: String?): CancionArtista {
        val t = limpiar(titulo)
        val pos = SEPARADORES.map { s -> t.indexOf(s) to s }.filter { it.first >= 0 }.minByOrNull { it.first }
        if (pos != null) {
            val artista = colapsar(t.substring(0, pos.first))
            val cancion = colapsar(t.substring(pos.first + pos.second.length))
            if (artista.isNotEmpty() && cancion.isNotEmpty()) return CancionArtista(cancion, artista)
        }
        val a = canal?.let { limpiarCanal(it) }?.takeIf { it.isNotEmpty() }
        return CancionArtista(t, a)
    }
}
