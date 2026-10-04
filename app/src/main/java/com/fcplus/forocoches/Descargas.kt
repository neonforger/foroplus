package com.fcplus.forocoches

/**
 * Las decisiones de "hilos descargados", sin tocar disco ni Android.
 *
 * Un hilo descargado es una copia del hilo tal como estaba el día que lo guardaste, para que
 * sobreviva a que ForoCoches lo borre — que es la razón por la que lo pidieron. Vive en el
 * almacenamiento privado de la app, así que **aguanta actualizaciones y limpiezas de caché**,
 * y se pierde solo al borrar datos o desinstalar (decisión del dueño: sin servidor).
 *
 * **Son COMUNES a todas las cuentas**, al revés que casi todo lo demás de la app: un hilo es
 * contenido público, no algo tuyo, y duplicarlo por cada multi solo gastaría espacio.
 */
object Descargas {

    /** Carpeta de un hilo dentro del almacén. El `tid` es su identidad. */
    fun carpetaDe(tid: String): String = tid.filter { it.isDigit() }

    /** Un tid que no sea un número no identifica nada: mejor no guardar que guardar mal. */
    fun tidValido(tid: String): Boolean = carpetaDe(tid).isNotEmpty()

    /**
     * ¿Hay que preguntar antes de sustituir una copia por otra?
     *
     * Sustituir es lo normal y no pregunta nada. Pero si la copia nueva trae **menos**
     * mensajes que la guardada, re-descargar borraría justo lo que fuiste a salvar — y es
     * fácil que pase sin querer, pulsando descargar por costumbre sobre un hilo al que le han
     * quitado mensajes. Ese es el único caso en que se pregunta.
     */
    fun hayQuePreguntar(mensajesGuardados: Int, mensajesNuevos: Int): Boolean =
        mensajesGuardados > 0 && mensajesNuevos in 0 until mensajesGuardados

    /**
     * Tamaño en la unidad que toca. Se enseña en la pantalla de Descargados y en cada fila:
     * sin esto, alguien ve "ForoPlus: 2 GB" en los ajustes de Android y desinstala sin saber
     * por qué.
     */
    fun tamanoLegible(bytes: Long): String = when {
        bytes < 0 -> "0 KB"
        bytes < 1024 -> "$bytes B"
        bytes < 1024L * 1024 -> "${(bytes + 512) / 1024} KB"
        bytes < 10L * 1024 * 1024 -> {
            val decimas = (bytes * 10 + 524288) / (1024L * 1024)
            "${decimas / 10},${decimas % 10} MB"
        }
        bytes < 1024L * 1024 * 1024 -> "${(bytes + 524288) / (1024L * 1024)} MB"
        else -> {
            val decimas = (bytes * 10 + 536870912) / (1024L * 1024 * 1024)
            "${decimas / 10},${decimas % 10} GB"
        }
    }

    /** Resumen de una copia para su fila: "7 páginas · 210 mensajes". */
    fun resumen(paginas: Int, mensajes: Int): String {
        val p = if (paginas == 1) "1 página" else "$paginas páginas"
        val m = if (mensajes == 1) "1 mensaje" else "$mensajes mensajes"
        return "$p · $m"
    }

    /**
     * Las páginas que quedan por pedir, en orden.
     *
     * La página que ya estás viendo entra igual: pedirla nuevamente cuesta una petición y
     * evita tener que mezclar lo que hay en pantalla con lo descargado, que es de donde salen
     * los fallos raros. Un `pageCount` que FC no sepa dar se trata como 1.
     */
    fun paginasAPedir(pageCount: Int): List<Int> =
        (1..pageCount.coerceAtLeast(1)).toList()

    /** Los `<img src>` de un trozo de HTML de mensaje. */
    private val IMG = Regex("""<img[^>]+src=["']([^"']+)["']""", RegexOption.IGNORE_CASE)

    /**
     * Qué imágenes hay que bajarse de un hilo, sin repetir y en orden de aparición.
     *
     * **Los smilies quedan fuera**: son mobiliario de la app, vienen de FC y los hay a decenas
     * por página (medido: 10 smilies por cada 8 imágenes de verdad). Bajarlos sería engordar la
     * copia con iconos que ya trae la app.
     *
     * Tampoco entran los `data:` —ya están dentro del HTML— ni nada que no sea http(s).
     */
    fun urlsDeImagen(htmlDeCadaMensaje: List<String>): List<String> {
        val fuera = LinkedHashSet<String>()
        for (html in htmlDeCadaMensaje) {
            for (m in IMG.findAll(html)) {
                val u = m.groupValues[1].trim()
                if (!u.startsWith("http://") && !u.startsWith("https://")) continue
                if (esSmiley(u)) continue
                fuera.add(u)
            }
        }
        return fuera.toList()
    }

    fun esSmiley(url: String): Boolean =
        url.contains("/images/smilies/", true) || url.contains("/smilies/", true)

    /** Nombre del fichero de una imagen dentro de la copia. La url es su identidad. */
    fun nombreDeImagen(url: String): String {
        val md = java.security.MessageDigest.getInstance("SHA-1")
        return md.digest(url.toByteArray()).joinToString("") { "%02x".format(it) } + ".img"
    }
}
