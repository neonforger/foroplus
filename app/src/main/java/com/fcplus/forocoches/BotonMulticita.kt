package com.fcplus.forocoches

/**
 * Cómo se ve el botón de multicita de un mensaje. Dice su estado con palabras: antes era un
 * "＋" que pasaba a "✓", y quien no sabía qué era la multicita no lo descubría nunca.
 * Marcado se ve como una pastilla rojo suave (un botón ACTIVADO), sin marcar como los demás.
 */
object BotonMulticita {

    data class Aspecto(
        val texto: String,
        val descripcion: String,
        val icono: Int,
        val color: Int,
        val fondo: Int?
    )

    fun aspecto(marcado: Boolean): Aspecto =
        if (marcado) Aspecto("En multicita", "Quitar de la multicita",
            R.drawable.ic_marcado, R.color.fc_rojo, R.drawable.bg_pastilla_suave)
        else Aspecto("Multicita", "Añadir a la multicita",
            R.drawable.ic_multicita, R.color.fc_texto_2, null)
}
