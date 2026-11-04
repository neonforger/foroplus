package com.fcplus.forocoches

/**
 * ¿El dedo que se mueve sobre un embed va DE LADO? Entonces el gesto es del embed (pasar fotos
 * de un carrusel de Instagram, arrastrar la barra de un vídeo) y la lista del hilo no se lo
 * puede quedar. Ver el comentario de `setOnTouchListener` en [EmbedView].
 *
 * Se decide con **la mitad del touchSlop**: la lista intercepta en cuanto el dedo pasa el slop
 * entero en vertical, así que hay que llegar antes. Y lo horizontal tiene que **dominar** (más
 * del doble que lo vertical, no solo "algo más"): si no, un scroll vertical que empieza con un
 * poco de lado se quedaría clavado en el embed y el hilo dejaría de bajar.
 */
object GestoEmbed {
    fun esHorizontal(dx: Float, dy: Float, touchSlop: Int): Boolean =
        dx > touchSlop / 2f && dx > dy * 2f
}
