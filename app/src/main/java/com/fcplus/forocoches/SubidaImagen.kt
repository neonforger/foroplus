package com.fcplus.forocoches

import org.json.JSONObject

/**
 * A dónde se suben las fotos y qué se hace con la respuesta.
 *
 * @param url endpoint del servicio.
 * @param campoFichero nombre del campo multipart donde va el fichero.
 * @param campos campos de formulario fijos que hay que acompañar.
 * @param ladoMax lado mayor al que se reduce ANTES de subir.
 */
data class HostImagenes(
    val url: String,
    val campoFichero: String,
    val campos: Map<String, String>,
    val ladoMax: Int
)

/**
 * Subir una foto desde la app y pegar su `[IMG]` en el mensaje.
 *
 * Nace de un fallo que **causamos nosotros** (V, 2026-09-08): como no se pueden subir fotos
 * desde la app, la gente se iba a la web, pero los enlaces del foro **abren la app**, y vuelta a
 * empezar. Su conclusión fue *"tengo que desinstalar la app para poder subir una foto"*.
 *
 * **Por qué catbox y no Imgur**, medido el 2026-08-29: el tier gratuito de Imgur excluye apps
 * con donaciones, y esta tiene el botón del café. Catbox no pide clave —no hay secreto que
 * filtrar— y conserva la extensión del fichero, que es lo que exige FC. No hace falta servidor
 * propio: un proxy sería igual de público y pondría todas las fotos de los usuarios a pasar por
 * una máquina del dueño.
 *
 * **El host vive en `fc_config.json` desde el primer día**, y esto no es sobrediseño: si catbox
 * cierra, banea o caduca, cambiar de proveedor es editar un fichero — sin compilar, sin Play y
 * sin dejar a la gente sin subir fotos durante una semana de revisión.
 */
object SubidaImagen {

    /**
     * Lo que se usa si la config remota no dice nada. Medido: una foto de móvil de 7,26 MB
     * tarda 5,58 s; reducida a 1600 px son 1,26 MB y 3,2 s. **Reducir divide por seis**, que
     * con datos móviles es la diferencia entre ir y no ir.
     */
    val CATBOX = HostImagenes(
        url = "https://catbox.moe/user/api.php",
        campoFichero = "fileToUpload",
        campos = mapOf("reqtype" to "fileupload"),
        ladoMax = 1600
    )

    /** Host a usar: el del bloque `imagenes` de la config, o catbox. */
    fun host(config: JSONObject?): HostImagenes {
        val o = config?.optJSONObject("imagenes") ?: return CATBOX
        val url = o.optString("url").trim()
        if (url.isEmpty()) return CATBOX
        val campos = LinkedHashMap<String, String>()
        o.optJSONObject("campos")?.let { c ->
            for (k in c.keys()) campos[k] = c.optString(k)
        }
        return HostImagenes(
            url = url,
            campoFichero = o.optString("campoFichero").ifBlank { CATBOX.campoFichero },
            campos = if (campos.isEmpty()) CATBOX.campos else campos,
            ladoMax = o.optInt("ladoMax", CATBOX.ladoMax).coerceIn(320, 4096)
        )
    }

    /**
     * El enlace que devuelve el servicio, o "" si eso no es un enlace.
     *
     * Catbox contesta **texto plano**: la URL si va bien, y un mensaje de error en la misma
     * respuesta 200 si va mal. Por eso no basta con mirar el código HTTP — hay que comprobar
     * que lo que ha llegado es de verdad una URL, o se pegaría el texto del error dentro de un
     * `[IMG]` y el mensaje saldría publicado con un churro.
     */
    fun enlaceDeRespuesta(cuerpo: String): String {
        val t = cuerpo.trim()
        if (!t.startsWith("http://") && !t.startsWith("https://")) return ""
        // Una URL no lleva espacios ni saltos: si los hay, esto es prosa, no un enlace.
        if (t.any { it.isWhitespace() }) return ""
        return t
    }

    /** El BBCode que se inserta en el mensaje. */
    fun bbcode(enlace: String): String = "[IMG]$enlace[/IMG]"
}
