package com.fcplus.forocoches

import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/**
 * Qué clase de imagen son unos bytes, para guardarla o compartirla **tal cual vino**.
 *
 * Se decide por la CABECERA del fichero y no por la URL, porque en FC la URL casi nunca lo dice:
 * los adjuntos son `attachment.php?attachmentid=…` y las fotos de dominios que FC no conoce
 * llegan por su proxy (`images.forocoches.com?url=…`, gotcha 42). Si un GIF se guardara con
 * extensión `.jpg` o mime `image/jpeg`, la galería y WhatsApp lo tratarían como foto fija.
 *
 * Puro a propósito: lo que decide va aquí, con tests; lo que toca Android, en [GuardarImagen].
 */
enum class TipoImagen(val mime: String, val extension: String) {
    GIF("image/gif", "gif"),
    PNG("image/png", "png"),
    JPEG("image/jpeg", "jpg"),
    WEBP("image/webp", "webp");

    /** Nombre del fichero guardado: `ForoPlus_AAAAMMDD_HHMMSS.ext`, a la hora de [zona]. */
    fun nombre(ahora: Long, zona: TimeZone = TimeZone.getDefault()): String {
        val fecha = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).apply { timeZone = zona }
        return "ForoPlus_${fecha.format(ahora)}.$extension"
    }

    companion object {
        /**
         * El tipo de [b], o null si no es una imagen que se sepa guardar. Un null es lo que se
         * recibe cuando el servidor contesta con una página de error en vez de la foto: guardar
         * eso como imagen dejaría un fichero roto en la galería.
         */
        fun de(b: ByteArray): TipoImagen? = when {
            empieza(b, 0, "GIF87a") || empieza(b, 0, "GIF89a") -> GIF
            empieza(b, 0, 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A) -> PNG
            empieza(b, 0, 0xFF, 0xD8, 0xFF) -> JPEG
            // RIFF lo usan también WAV y AVI: solo es WEBP si lo dice en el byte 8.
            empieza(b, 0, "RIFF") && empieza(b, 8, "WEBP") -> WEBP
            else -> null
        }

        private fun empieza(b: ByteArray, desde: Int, texto: String) =
            empieza(b, desde, *texto.map { it.code }.toIntArray())

        private fun empieza(b: ByteArray, desde: Int, vararg firma: Int): Boolean {
            if (b.size < desde + firma.size) return false
            return firma.indices.all { (b[desde + it].toInt() and 0xFF) == firma[it] }
        }
    }
}
