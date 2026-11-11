package com.fcplus.forocoches

/**
 * Una canción tal como la leen el buscador de la hoja de compartir ([BuscadorMusica]) y los
 * metadatos. La identidad es [ref] (su `clave`); lo demás es solo para pintarla. Ya no se guarda
 * en el móvil: Música es solo el feed (decisión del dueño, 2026-11-11).
 */
data class Cancion(
    val ref: RefCancion,
    val titulo: String,
    val artista: String?,
    val album: String?,
    val duracionSeg: Int?,
    val portada: String?,
    val anadida: Long
)
