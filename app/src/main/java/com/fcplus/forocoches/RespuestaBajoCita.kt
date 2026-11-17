package com.fcplus.forocoches

/**
 * Responder bajo cada cita al multicitar: `[QUOTE A] respuesta a A [QUOTE B] respuesta a B` + cierre.
 *
 * Es un ajuste de Opciones, **apagado por defecto** (ver [OptionsController.responderBajoCita]),
 * y solo cambia algo con **dos citas o más** (javier hg, 2026-11-16: con una cita normal, la caja
 * de siempre ya es "la respuesta a esa cita"; partirla solo añade peso).
 *
 * Lo que se publica es BBCode normal de FC: en la web se ve como cualquier mensaje que alguien
 * haya escrito intercalando a mano. No hay nada propio de la app en el resultado.
 */
object RespuestaBajoCita {

    /** Si el composer pinta un hueco de respuesta bajo cada cita. */
    fun activo(ajuste: Boolean, citas: Int): Boolean = ajuste && citas >= 2

    /**
     * El mensaje: cada bloque de cita (ya en BBCode) seguido de su respuesta, y el cierre al final.
     * Una respuesta vacía deja la cita sola, que es lo mismo que publicaría el composer de siempre.
     * Las respuestas se recortan para que una línea en blanco de más no se publique como hueco.
     */
    fun armar(bloques: List<Pair<String, String>>, cierre: String): String {
        val sb = StringBuilder()
        for ((cita, respuesta) in bloques) {
            if (cita.isBlank()) continue
            sb.append(cita.trimEnd()).append('\n')
            val r = respuesta.trim()
            if (r.isNotEmpty()) sb.append(r).append("\n\n")
        }
        val c = cierre.trim()
        if (c.isNotEmpty()) sb.append(c)
        return sb.toString().trim()
    }

    /**
     * Al dejar de estar activo (se quita una cita y queda una, o se pasan a texto) lo escrito bajo
     * cada cita **no se pierde**: pasa a la caja de siempre, delante de lo que hubiera en ella, en
     * el orden de las citas. Perder texto escrito por quitar una cita sería imperdonable.
     */
    fun aLaCaja(respuestas: List<String>, caja: String): String =
        (respuestas.map { it.trim() } + caja.trim()).filter { it.isNotEmpty() }.joinToString("\n\n")
}
