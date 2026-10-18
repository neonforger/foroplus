package com.fcplus.forocoches

/**
 * La línea gris bajo cada categoría de Opciones: cómo lo tienes ahora, sin tener que entrar.
 * Es lo que hace útil la pantalla de categorías (el patrón de los Ajustes de Android) en vez de
 * un índice mudo. Puro para poder probarlo; lo pinta [OptionsController].
 */
object ResumenOpciones {


    /** [spMensajes] y [spTitulos], ya leídos con [TamanoTexto.leer]. */
    fun apariencia(tema: String, spMensajes: Int, spTitulos: Int, estilo: String = EstiloApp.COMPACTA): String {
        val nombreTema = when (tema) {
            TemaApp.CLARO -> "Tema claro"
            TemaApp.OSCURO -> "Tema oscuro"
            else -> "Tema del sistema"
        }
        val partes = mutableListOf(nombreTema)
        // La compacta es la de fábrica: decirlo sería ruido.
        if (estilo == EstiloApp.TARJETAS) partes.add("estilo tarjetas")
        val letra = TamanoTexto.palabra(spMensajes)
        partes.add("letra $letra")
        // Si los títulos van como los mensajes, decirlo otra vez es ruido.
        if (TamanoTexto.palabra(spTitulos) != letra) {
            partes.add("títulos ${TamanoTexto.palabra(spTitulos, plural = true)}")
        }
        return partes.joinToString(" · ")
    }

    private fun contar(n: Int, singular: String, plural: String) =
        "$n ${if (n == 1) singular else plural}"

    fun filtros(palabras: Int, palabrasActivas: Boolean, usuarios: Int, hilos: Int): String {
        val partes = ArrayList<String>()
        if (palabras > 0) {
            val p = contar(palabras, "palabra", "palabras")
            partes.add(if (palabrasActivas) p else "$p (apagado)")
        }
        if (usuarios > 0) partes.add(contar(usuarios, "usuario", "usuarios"))
        if (hilos > 0) partes.add(contar(hilos, "hilo", "hilos"))
        return if (partes.isEmpty()) "Sin filtros" else partes.joinToString(" · ")
    }

    fun personalizar(subforosPopurri: Int): String =
        if (subforosPopurri > 0)
            "Popurrí con ${contar(subforosPopurri, "subforo", "subforos")} · barras de arriba y de abajo"
        else "Popurrí y barras de arriba y de abajo"

    fun publicar(firma: Boolean): String =
        if (firma) "Con la firma «Enviado desde ForoPlus»" else "Sin firma"
}
