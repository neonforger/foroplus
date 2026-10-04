package com.fcplus.forocoches

/**
 * Enlaces y textos para compartir un hilo o un mensaje.
 *
 * Puro y sin Android porque aquí es donde se cuela la basura: la app navega con URLs de trabajo
 * (`&page=7`, el `_fp=` que esquiva la caché de Varnish, el `s=` de sesión de vBulletin) y
 * ninguna de esas debe salir de la app. Lo que se comparte tiene que ser el enlace limpio.
 */
object CompartirFC {

    private const val BASE = "https://forocoches.com/foro/"

    /**
     * Quita el "- Página N" que vBulletin le pega al título de la página.
     *
     * La cabecera del hilo enseña ese título tal cual, y colándolo en lo que se comparte queda
     * "Mi hilo - Página 2" acompañando a un enlace que apunta al hilo ENTERO: se contradicen.
     * Visto en el móvil compartiendo desde la página 2 (2026-08-19).
     */
    fun tituloLimpio(titulo: String): String =
        titulo.trim().replace(Regex("""\s*[-–]\s*P[áa]gina\s+\d+\s*$""", RegexOption.IGNORE_CASE), "").trim()

    /** Enlace canónico a un hilo. Sin página: se comparte el hilo, no "la página 7". */
    fun enlaceHilo(tid: String): String {
        val limpio = tid.trim().filter { it.isDigit() }
        return if (limpio.isEmpty()) "" else "${BASE}showthread.php?t=$limpio"
    }

    /** Enlace directo a UN mensaje. El `#postNNN` hace que el navegador salte a él. */
    fun enlaceMensaje(pid: String): String {
        val limpio = pid.trim().filter { it.isDigit() }
        return if (limpio.isEmpty()) "" else "${BASE}showthread.php?p=$limpio#post$limpio"
    }

    /**
     * Texto que acompaña a la tarjeta. **Siempre va el enlace**: sin él, quien lo recibe tiene
     * una foto de un mensaje y ninguna forma de llegar al original.
     */
    fun textoHilo(titulo: String, tid: String): String {
        val enlace = enlaceHilo(tid)
        val t = tituloLimpio(titulo)
        return listOf(t, enlace).filter { it.isNotEmpty() }.joinToString("\n")
    }

    fun textoMensaje(autor: String, titulo: String, pid: String): String {
        val enlace = enlaceMensaje(pid)
        val quien = autor.trim().removePrefix("@")
        val t = tituloLimpio(titulo)
        val cabecera = when {
            quien.isNotEmpty() && t.isNotBlank() -> "@$quien en «$t»"
            quien.isNotEmpty() -> "@$quien en ForoCoches"
            else -> t
        }
        return listOf(cabecera, enlace).filter { it.isNotEmpty() }.joinToString("\n")
    }

    /**
     * Primera foto del mensaje, para ilustrar la tarjeta. Se saltan los smileys (son `<img>`
     * igual que las fotos) y cualquier cosa que no sea una URL absoluta.
     *
     * Solo la PRIMERA a propósito: un post con nueve fotos daría una tarjeta absurda, y quien
     * la reciba tiene el enlace para ver el resto.
     */
    fun primeraImagen(html: String): String {
        val re = Regex("""<img[^>]+src=["']([^"']+)["']""", RegexOption.IGNORE_CASE)
        for (m in re.findAll(html)) {
            val src = m.groupValues[1].trim()
            if (src.contains("smilies", ignoreCase = true)) continue
            if (src.startsWith("http", ignoreCase = true)) return src
        }
        return ""
    }

    /**
     * Prepara el cuerpo del mensaje para la tarjeta.
     *
     * Dos cosas: los posts vienen con rachas de líneas en blanco (la gente separa a base de
     * Enter) que en una tarjeta se ven como un agujero, y hay mensajes larguísimos — una
     * tarjeta de 8.000 píxeles no se ve en ningún sitio. Se corta por la última palabra
     * entera, que cortar a mitad de palabra canta muchísimo.
     */
    fun cuerpoTarjeta(texto: String, maxCaracteres: Int = 500): String {
        val compacto = texto
            .replace("\r\n", "\n")
            // U+FFFC es el hueco que deja Android donde había una imagen al pasar el HTML a
            // texto. En la app no se ve (ahí va la foto), pero en la tarjeta se dibuja como
            // una cajita punteada con "OBJ" dentro. Visto en el móvil el 2026-08-19.
            .replace("\uFFFC", "")
            .replace(Regex("[ \t]+"), " ")
            .replace(Regex("\n{3,}"), "\n\n")
            .trim()
        if (compacto.length <= maxCaracteres) return compacto
        val corte = compacto.take(maxCaracteres)
        val ultimoEspacio = corte.lastIndexOf(' ')
        val base = if (ultimoEspacio > maxCaracteres / 2) corte.take(ultimoEspacio) else corte
        return base.trimEnd().trimEnd('.', ',', ';', ':') + "…"
    }
}
