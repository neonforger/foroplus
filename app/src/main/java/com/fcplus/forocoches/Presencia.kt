package com.fcplus.forocoches

/**
 * "Conectado ahora": el punto verde que FC pone en el avatar de cada mensaje.
 *
 * Medido por CDP el 2026-10-07: es el ÚNICO sitio donde FC lo dice. La ficha no lo enseña (ni
 * "última actividad"), `online.php` está desactivada, y no hay marca de "desconectado": solo la
 * presencia del punto, que además no sale para quien usa el modo invisible. Por eso aquí nunca
 * se pinta nada para el que no lo lleva.
 */
object Presencia {

    /**
     * Lo que se enseña de cada mensaje. En una copia descargada la marca es de cuando se bajó el
     * hilo, a veces de hace días, y no dice nada de ahora: se apaga.
     */
    fun paraMostrar(posts: List<PostItem>, esCopia: Boolean): List<PostItem> =
        if (!esCopia) posts else posts.map { if (it.conectado) it.copy(conectado = false) else it }
}
