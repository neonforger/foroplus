package com.fcplus.forocoches

/** Una canción leída de un texto compartido; los nombres solo vienen si la línea numerada los traía. */
data class LineaImportada(val ref: RefCancion, val cancion: String?, val artista: String?)

data class Importada(val nombre: String?, val lineas: List<LineaImportada>)

/**
 * Una playlist como texto plano con enlaces (sale por el menú de compartir a WhatsApp, Telegram…)
 * y el camino de vuelta: pegar ese texto, o cualquiera con enlaces, y sacar las canciones.
 */
object PlaylistCompartida {
    private const val SEPARADOR = " – "
    private val CABECERA = Regex("""^Playlist «(.+)» \(\d+ canci(?:ón|ones)\)$""")
    private val NUMERADA = Regex("""^\d+\. (.+)$""")

    fun texto(nombre: String, canciones: List<Cancion>): String {
        val n = canciones.size
        val sb = StringBuilder("Playlist «$nombre» ($n ${if (n == 1) "canción" else "canciones"})\n\n")
        canciones.forEachIndexed { i, c ->
            val nom = if (c.artista.isNullOrBlank()) c.titulo else "${c.artista}$SEPARADOR${c.titulo}"
            sb.append("${i + 1}. $nom\n${c.ref.url()}\n")
        }
        sb.append("\nHecha con ForoPlus Música")
        return sb.toString()
    }

    fun leer(texto: String): Importada {
        var nombre: String? = null
        val vistas = HashSet<RefCancion>()
        val lineas = ArrayList<LineaImportada>()
        // Nombres de la última línea numerada, a la espera del enlace que va justo debajo.
        var pendiente: Pair<String?, String>? = null // artista, canción

        fun anadir(ref: RefCancion, cancion: String?, artista: String?) {
            if (vistas.add(ref)) lineas.add(LineaImportada(ref, cancion, artista))
        }

        for (cruda in texto.split('\n')) {
            val l = cruda.trim() // quita también el \r de Windows y los espacios que mete WhatsApp
            val enlaces = EnlaceMusica.todas(l)
            if (enlaces.isNotEmpty()) {
                enlaces.forEachIndexed { i, ref ->
                    val p = pendiente.takeIf { i == 0 }
                    anadir(ref, p?.second, p?.first)
                }
                pendiente = null
                continue
            }
            pendiente = null
            val cab = CABECERA.matchEntire(l)
            if (cab != null) {
                if (nombre == null) nombre = cab.groupValues[1]
                continue
            }
            val num = NUMERADA.matchEntire(l) ?: continue
            val resto = num.groupValues[1].trim()
            val i = resto.indexOf(SEPARADOR)
            val artista = if (i > 0) resto.substring(0, i).trim() else ""
            val cancion = if (i > 0) resto.substring(i + SEPARADOR.length).trim() else ""
            pendiente = if (artista.isNotEmpty() && cancion.isNotEmpty()) artista to cancion else null to resto
        }
        return Importada(nombre, lineas)
    }
}
