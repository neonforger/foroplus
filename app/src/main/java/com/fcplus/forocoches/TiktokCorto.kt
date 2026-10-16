package com.fcplus.forocoches

/**
 * Enlaces cortos de TikTok (`vm.tiktok.com/…`, `tiktok.com/t/…`), que son los que da el botón de
 * compartir de TikTok en el móvil.
 *
 * **FC no los entiende** (medido el 2026-10-16 con su vista previa, sin publicar): metidos en
 * `[TIKTOK]` sale "[TikTok Error - URL incorrecta]", porque necesita el id del vídeo y el enlace
 * corto no lo lleva. Lo reportó Márquez: ni pegando el enlace ni con BBCode. Y la app lo
 * empeoraba, porque [EnlacesEmbebibles] envolvía el enlace corto en `[TIKTOK]` ella sola.
 *
 * Antes de publicar se resuelve cada uno siguiendo su redirección ([ResolverTiktok]) y se cambia
 * por la forma larga. TikTok redirige a `/@/video/ID` con el usuario **vacío**, y esa forma FC
 * tampoco la acepta; `/video/ID` sí, y es la que se usa cuando no hay usuario.
 */
object TiktokCorto {

    private val CORTO = Regex(
        """https?://(?:(?:vm|vt)[.]tiktok[.]com/[A-Za-z0-9]+|(?:www[.]|m[.])?tiktok[.]com/t/[A-Za-z0-9]+)/?""",
        RegexOption.IGNORE_CASE
    )

    /** Citas: el texto de otro no se reescribe. */
    private val CITA = Regex(
        """\[quote[^\]]*].*?\[/quote]""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    )

    private val VIDEO = Regex(
        """^https?://(?:www[.]|m[.])?tiktok[.]com/(?:@([^/?#]*)/)?video/(\d+)""",
        RegexOption.IGNORE_CASE
    )

    private fun fueraDeCitas(texto: String): List<MatchResult> {
        val citas = CITA.findAll(texto).map { it.range }.toList()
        return CORTO.findAll(texto).filter { m -> citas.none { m.range.first in it } }.toList()
    }

    /** Los enlaces cortos de [texto] que hay que resolver, sin repetir. */
    fun cortos(texto: String): List<String> = fueraDeCitas(texto).map { it.value }.distinct()

    /**
     * La forma larga que acepta FC a partir de adónde redirige el enlace corto, o null si no
     * lleva a un vídeo (un código que no existe manda a la portada de TikTok).
     */
    fun completa(redireccion: String): String? {
        val m = VIDEO.find(redireccion) ?: return null
        val usuario = m.groupValues[1]
        val id = m.groupValues[2]
        return if (usuario.isEmpty()) "https://www.tiktok.com/video/$id"
        else "https://www.tiktok.com/@$usuario/video/$id"
    }

    /** Cambia cada corto por su forma larga según [resueltos]; los que falten se quedan igual. */
    fun sustituir(texto: String, resueltos: Map<String, String>): String {
        if (resueltos.isEmpty()) return texto
        val sb = StringBuilder()
        var desde = 0
        for (m in fueraDeCitas(texto)) {
            val largo = resueltos[m.value] ?: continue
            sb.append(texto, desde, m.range.first).append(largo)
            desde = m.range.last + 1
        }
        return sb.append(texto, desde, texto.length).toString()
    }
}
