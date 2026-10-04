package com.fcplus.forocoches

/**
 * La línea gris bajo cada categoría de Opciones: cómo lo tienes ahora, sin tener que entrar.
 * Es lo que hace útil la pantalla de categorías (el patrón de los Ajustes de Android) en vez de
 * un índice mudo. Puro para poder probarlo; lo pinta [OptionsController].
 */
object ResumenOpciones {

    private val LETRA = arrayOf("pequeña", "normal", "grande")
    private val TITULOS = arrayOf("pequeños", "normales", "grandes")

    fun apariencia(tema: String, idxMensajes: Int, idxTitulos: Int): String {
        val nombreTema = when (tema) {
            TemaApp.CLARO -> "Tema claro"
            TemaApp.OSCURO -> "Tema oscuro"
            else -> "Tema del sistema"
        }
        val m = idxMensajes.coerceIn(0, 2)
        val t = idxTitulos.coerceIn(0, 2)
        val partes = mutableListOf(nombreTema, "letra ${LETRA[m]}")
        // Si los títulos van como los mensajes, decirlo otra vez es ruido.
        if (t != m) partes.add("títulos ${TITULOS[t]}")
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
