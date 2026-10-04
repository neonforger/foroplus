package com.fcplus.forocoches

/** Cómo hay que enderezar una foto: [grados] en sentido horario y, si acaso, [espejo]. */
data class GiroFoto(val grados: Int, val espejo: Boolean) {
    /** Si no hay nada que hacer se ahorra rehacer el bitmap (y su memoria). */
    val hayQueGirar: Boolean get() = grados != 0 || espejo
}

/**
 * La etiqueta EXIF de orientación, traducida a un giro.
 *
 * **Por qué hace falta.** La cámara del móvil no gira los píxeles: los guarda como los leyó el
 * sensor y anota aparte cómo hay que mirarlos. Quien respeta la etiqueta (la galería, el
 * navegador) ve la foto derecha. Pero al subirla desde la app pasaban dos cosas a la vez:
 * `BitmapFactory` **ignora** la etiqueta al decodificar, y `Bitmap.compress()` **no la escribe**
 * al guardar. O sea que la copia subida sale con los píxeles tumbados **y sin la nota que decía
 * cómo enderezarlos**: torcida para siempre, aunque en el móvil de quien la sube se siga viendo
 * bien. Ese era el "va regulero, tengo que arreglar la rotación" del 2026-09-15.
 *
 * La solución es girar los píxeles ANTES de comprimir, y entonces el JPEG ya no necesita EXIF.
 *
 * Los ocho valores del estándar, en la convención de `Matrix` (girar y luego reflejar en X):
 *
 * | EXIF | Significado | grados | espejo |
 * |---|---|---|---|
 * | 1 | normal | 0 | no |
 * | 2 | reflejada en horizontal | 0 | sí |
 * | 3 | media vuelta | 180 | no |
 * | 4 | reflejada en vertical | 180 | sí |
 * | 5 | diagonal | 90 | sí |
 * | 6 | **vertical de toda la vida** | 90 | no |
 * | 7 | la otra diagonal | 270 | sí |
 * | 8 | vertical al revés | 270 | no |
 *
 * El 6 es el del día a día: foto hecha con el móvil en vertical.
 */
object OrientacionFoto {

    /** El valor que significa "ya está derecha", y también el que se usa si no hay etiqueta. */
    const val NORMAL = 1

    /**
     * Una etiqueta que no conocemos **no puede estropear una foto que estaba bien**, así que
     * cualquier valor fuera de la tabla se trata como normal.
     */
    fun de(orientacionExif: Int): GiroFoto = when (orientacionExif) {
        2 -> GiroFoto(0, true)
        3 -> GiroFoto(180, false)
        4 -> GiroFoto(180, true)
        5 -> GiroFoto(90, true)
        6 -> GiroFoto(90, false)
        7 -> GiroFoto(270, true)
        8 -> GiroFoto(270, false)
        else -> GiroFoto(0, false)
    }
}
