package com.fcplus.forocoches

import org.json.JSONObject
import java.net.URLDecoder

/**
 * Los emoticonos propios de ForoPlus: la pestaña "ForoPlus" del selector.
 *
 * Se publican como una imagen normal, `[IMG]https://neonforger.github.io/foroplus/emoticonos/
 * <fichero>.gif?v=N[/IMG]`, porque es lo que FC ya sabe pintar (gotcha 59): en la web salen por su
 * proxy, dentro de la línea y animados, y quien lee desde el navegador los ve igual. Se alojan en
 * el GitHub Pages del repo público (carpeta `docs/emoticonos/`) y no en catbox, que es el servicio
 * que se cae al subir fotos: si el origen desaparece, el emoticono muere en la web en todos los
 * mensajes viejos. Se descartó meter el GIF en base64 dentro de la URL: el SEÑORADETODOS animado
 * son 3.436 caracteres de ristra en cada mensaje (y en cada cita que lo arrastre).
 *
 * De dónde salen, y la app **nunca los pide a GitHub Pages**:
 * - [DENTRO]: van en el APK (`assets/emoticonos/`). Funcionan sin red y sin config.
 * - [remotos]: los que anuncia `fc_config.json` (`"emoticonos": [{"nombre", "fichero", "v"}]`),
 *   para estrenar emoticonos SIN publicar versión. [EmoticonosRemotos] baja cada GIF una vez de
 *   `raw.githubusercontent.com` (la misma carpeta `docs/emoticonos/` del repo) y lo guarda en el
 *   móvil; hasta que no está guardado, no sale en el selector.
 *
 * **Para estrenar uno, el orden importa (gotcha 63):** primero el GIF en `docs/emoticonos/`, push,
 * y comprobar que GitHub Pages lo sirve; SOLO entonces añadirlo a `fc_config.json`. Al revés,
 * alguien lo publica y lo abre en la web antes de que exista, y el proxy de FC guarda ese 404
 * ocho días.
 *
 * Una versión vieja de la app que no conozca un emoticono lo baja por el proxy de FC como cualquier
 * imagen, y [esDeForoPlus] reconoce la CARPETA entera y no solo la lista: así al menos se pinta
 * como emoticono y no como foto.
 */
object EmoticonosForoPlus {

    const val CARPETA = "https://neonforger.github.io/foroplus/emoticonos/"

    /**
     * [version] va en la URL que se publica (`?v=N`) y la app la ignora al reconocerla. Existe
     * porque **el proxy de FC cachea también los 404**, con `max-age=691200` (8 días): el
     * 2026-10-18 la URL sin versión y la `?v=1` se abrieron en la web antes de que el GIF
     * estuviera en Pages y se quedaron envenenadas. Si un emoticono cambia de dibujo, o su URL se
     * vuelve a envenenar, se sube la versión: FC firma la URL entera, así que es otra imagen para
     * su caché.
     */
    data class Emoticono(val nombre: String, val fichero: String, val version: Int) {
        val url: String get() = "$CARPETA$fichero?v=$version"
        /** Lo que se mete en el mensaje. Es también su "código" en la caja de escribir. */
        val bbcode: String get() = "[IMG]$url[/IMG]"
    }

    /** Los que lleva el APK. */
    val DENTRO: List<Emoticono> = listOf(
        Emoticono("SEÑORADETODOS", "senoradetodos.gif", version = 2),
    )

    /** Los de la config remota, ya validados. Los pone [EmoticonosRemotos]. */
    @Volatile
    var remotos: List<Emoticono> = emptyList()

    /**
     * Lee los bytes de un fichero de `assets/emoticonos/`. Lo pone MainActivity al arrancar;
     * aquí no hay Context.
     */
    @Volatile
    var lector: ((String) -> ByteArray?)? = null

    /** Lee un GIF ya bajado de la config remota, o null si aún no está. Lo pone [EmoticonosRemotos]. */
    @Volatile
    var disco: ((String) -> ByteArray?)? = null

    /** ¿Está ya bajado ese fichero? (sin leerlo: se consulta en cada tecla de la caja de escribir) */
    @Volatile
    var enDisco: ((String) -> Boolean)? = null

    private val FICHERO = Regex("""^[a-z0-9_-]{1,40}\.gif$""")

    /** ¿Es un nombre de fichero aceptable? Lo que no case no se baja ni se reconoce. */
    fun ficheroValido(fichero: String): Boolean = FICHERO.matches(fichero)

    /**
     * Los emoticonos que anuncia la config. Se descarta, sin romper el resto, cualquier entrada
     * sin nombre, con un nombre de más de 40 letras, con un fichero que no sea `[a-z0-9_-].gif` o
     * con una versión menor que 1; y los ficheros repetidos (gana el primero).
     */
    fun deConfig(config: JSONObject?): List<Emoticono> {
        val lista = config?.optJSONArray("emoticonos") ?: return emptyList()
        val out = ArrayList<Emoticono>()
        for (i in 0 until lista.length()) {
            val o = lista.optJSONObject(i) ?: continue
            val nombre = o.optString("nombre").trim()
            val fichero = o.optString("fichero").trim()
            val v = o.optInt("v", 1)
            if (nombre.isEmpty() || nombre.length > 40 || !ficheroValido(fichero) || v < 1) continue
            if (out.any { it.fichero == fichero }) continue
            out.add(Emoticono(nombre, fichero, v))
        }
        return out
    }

    /**
     * Todos los que conoce esta app: los de dentro primero y luego los nuevos de la config. Si la
     * config trae uno de los de dentro con una versión mayor, se usa esa versión en la URL (para
     * poder desenvenenarlo sin publicar), pero el dibujo sigue saliendo del APK.
     */
    fun disponibles(): List<Emoticono> {
        val rem = remotos
        val dentro = DENTRO.map { d ->
            val r = rem.firstOrNull { it.fichero == d.fichero }
            if (r != null && r.version > d.version) d.copy(version = r.version) else d
        }
        return dentro + rem.filter { r -> DENTRO.none { it.fichero == r.fichero } }
    }

    /** Los que se pueden poner YA: los de dentro y los remotos que ya están bajados. */
    fun listos(): List<Emoticono> = disponibles().filter { estaDentro(it.fichero) || enDisco?.invoke(it.fichero) == true }

    private fun estaDentro(fichero: String) = DENTRO.any { it.fichero == fichero }

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
        return fichero.takeIf { ficheroValido(it) }
    }

    /** ¿Es de nuestra carpeta, aunque esta versión de la app no lo conozca? */
    fun esDeForoPlus(url: String): Boolean = ficheroDe(url) != null

    /** El emoticono que esta app conoce (de dentro o de la config) para [url], o null. */
    fun deUrl(url: String): Emoticono? {
        val f = ficheroDe(url) ?: return null
        return disponibles().firstOrNull { it.fichero == f }
    }

    /**
     * Los bytes del GIF de [url] sin ir a la red: del APK o de lo ya bajado. null si no lo
     * tenemos; entonces quien pinta un mensaje lo pide por el proxy de FC, como cualquier imagen.
     */
    fun bytesDe(url: String): ByteArray? {
        val e = deUrl(url) ?: return null
        return try {
            if (estaDentro(e.fichero)) lector?.invoke(e.fichero) else disco?.invoke(e.fichero)
        } catch (_: Exception) { null }
    }

    private fun desenvolverProxy(url: String): String? {
        if (!url.startsWith("https://images.forocoches.com")) return null
        val i = url.indexOf("url=")
        if (i < 0) return null
        val crudo = url.substring(i + 4).substringBefore('&').substringBefore("fcsigsigsigsig=")
        return try { URLDecoder.decode(crudo, "UTF-8") } catch (_: Exception) { null }
    }
}
