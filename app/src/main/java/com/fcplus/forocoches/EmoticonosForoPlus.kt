package com.fcplus.forocoches

import java.net.URLDecoder

/**
 * Los emoticonos propios de ForoPlus: la pestaña "ForoPlus" del selector.
 *
 * Se publican como una imagen normal, `[IMG]https://neonforger.github.io/foroplus/emoticonos/
 * <fichero>.gif[/IMG]`, porque es lo que FC ya sabe pintar (gotcha 59): en la web salen por su
 * proxy, dentro de la línea y animados, y quien lee desde el navegador los ve igual. Se alojan en
 * el GitHub Pages del repo público (carpeta `docs/emoticonos/`) y no en catbox, que es el servicio
 * que se cae al subir fotos: si el origen desaparece, el emoticono muere en la web en todos los
 * mensajes viejos.
 *
 * La app **no los baja**: los lleva dentro (`assets/emoticonos/`) y, al ver su URL, los pinta
 * desde ahí, sin red. Se descartó meter el GIF en base64 dentro de la URL: el SEÑORADETODOS
 * animado son 3.436 caracteres de ristra en cada mensaje (y en cada cita que lo arrastre).
 *
 * Un emoticono que salga más adelante no lo lleva una versión vieja de la app: esa lo baja por el
 * proxy de FC como cualquier imagen, y por eso [esDeForoPlus] reconoce la CARPETA entera y no
 * solo la lista: así al menos se pinta como emoticono y no como foto.
 */
object EmoticonosForoPlus {

    const val CARPETA = "https://neonforger.github.io/foroplus/emoticonos/"

    /**
     * [version] va en la URL que se publica (`?v=N`) y la app la ignora al reconocerla. Existe
     * porque **el proxy de FC cachea también los 404**, con `max-age=691200` (8 días): el
     * 2026-10-18 la URL sin versión y la `?v=1` se abrieron en la web antes de que el GIF
     * estuviera en Pages y se quedaron envenenadas. Si un emoticono cambia de dibujo, o su URL se vuelve a envenenar, se sube la
     * versión: FC firma la URL entera, así que es otra imagen para su caché.
     */
    data class Emoticono(val nombre: String, val fichero: String, val version: Int) {
        val url: String get() = "$CARPETA$fichero?v=$version"
        /** Lo que se mete en el mensaje. Es también su "código" en la caja de escribir. */
        val bbcode: String get() = "[IMG]$url[/IMG]"
    }

    val TODOS: List<Emoticono> = listOf(
        Emoticono("SEÑORADETODOS", "senoradetodos.gif", version = 2),
    )

    /**
     * Lee los bytes de un fichero de `assets/emoticonos/`. Lo pone MainActivity al arrancar;
     * aquí no hay Context.
     */
    @Volatile
    var lector: ((String) -> ByteArray?)? = null

    private val FICHERO = Regex("""^[a-z0-9_-]+\.gif$""")

    /**
     * El fichero de nuestra carpeta al que apunta [url], o null si no es nuestro.
     *
     * En el HTML de un mensaje la URL no llega tal cual: FC la sirve por su proxy,
     * `https://images.forocoches.com?url=<la nuestra, codificada>fcsigsigsigsig=<firma>` (la
     * firma va pegada SIN `&`, ver gotcha 59), así que se desenvuelve antes de mirar.
     */
    fun ficheroDe(url: String): String? {
        val directa = desenvolverProxy(url) ?: url
        if (!directa.startsWith(CARPETA)) return null
        val fichero = directa.substring(CARPETA.length).substringBefore('?').substringBefore('#')
        return fichero.takeIf { FICHERO.matches(it) }
    }

    /** ¿Es de nuestra carpeta, aunque esta versión de la app no lo lleve dentro? */
    fun esDeForoPlus(url: String): Boolean = ficheroDe(url) != null

    /** El emoticono que esta versión lleva dentro para [url], o null. */
    fun deUrl(url: String): Emoticono? {
        val f = ficheroDe(url) ?: return null
        return TODOS.firstOrNull { it.fichero == f }
    }

    /** Los bytes del GIF de [url] si lo llevamos dentro; null si hay que ir a la red. */
    fun bytesDe(url: String): ByteArray? {
        val e = deUrl(url) ?: return null
        return try { lector?.invoke(e.fichero) } catch (_: Exception) { null }
    }

    private fun desenvolverProxy(url: String): String? {
        if (!url.startsWith("https://images.forocoches.com")) return null
        val i = url.indexOf("url=")
        if (i < 0) return null
        val crudo = url.substring(i + 4).substringBefore('&').substringBefore("fcsigsigsigsig=")
        return try { URLDecoder.decode(crudo, "UTF-8") } catch (_: Exception) { null }
    }
}
