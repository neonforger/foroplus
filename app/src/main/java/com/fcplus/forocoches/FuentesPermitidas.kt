package com.fcplus.forocoches

/**
 * De dónde puede descargar la app la lista de la sección +18. Es el requisito de transparencia:
 * la app NO se conecta a ningún servidor nuestro, ni aunque se cambie la config remota. Si un día
 * la lista se muda (un CDN), su prefijo se añade AQUÍ, en el código, y se ve en el diff público.
 */
object FuentesPermitidas {
    private val PREFIJOS = listOf("https://raw.githubusercontent.com/neonforger/foroplus-mas18/")

    fun aceptada(url: String): Boolean = PREFIJOS.any { url.startsWith(it) }
}
