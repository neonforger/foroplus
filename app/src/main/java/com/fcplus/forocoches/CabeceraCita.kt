package com.fcplus.forocoches

/**
 * La cabecera de una cita ("Fulano dijo:") que pone el extractor. Es un enlace (lleva al
 * mensaje citado) pero no debe PARECER un enlace del texto: va en rojo y negrita, sin subrayar
 * (fase 2 del rediseño). OJO: el texto "X dijo:" no se cambia nunca, el filtro de ignorados de
 * MainActivity busca esa cadena exacta.
 */
object CabeceraCita {
    fun es(texto: CharSequence): Boolean = texto.trim().endsWith(" dijo:")
}
