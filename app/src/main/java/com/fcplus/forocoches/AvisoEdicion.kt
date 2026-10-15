package com.fcplus.forocoches

/**
 * El aviso de "editado" al pie de un mensaje.
 *
 * Lo pidió un tester hace tiempo y se volvió a reportar el 2026-10-15: la app no decía si un
 * mensaje se había editado. FC sí lo sabe, pero en la plantilla móvil lo esconde en un
 * comentario HTML (lo lee `ultimaEdicion` en extractor.js).
 *
 * El "por X" solo se dice cuando lo editó OTRO (un moderador, típicamente): que el autor edite
 * lo suyo es lo normal y repetir su nombre solo ocupa sitio.
 */
object AvisoEdicion {

    /** Texto del aviso, o "" si el mensaje no está editado. */
    fun texto(por: String, cuando: String, autor: String): String {
        val fecha = cuando.trim()
        if (fecha.isEmpty()) return ""
        val quien = por.trim()
        return if (quien.isEmpty() || quien.equals(autor.trim(), ignoreCase = true)) {
            "Editado · $fecha"
        } else {
            "Editado por $quien · $fecha"
        }
    }
}
