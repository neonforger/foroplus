package com.fcplus.forocoches

/** Por qué se está refrescando el hilo abierto. */
enum class Motivo {
    /** Acabas de editar un mensaje tuyo. */
    EDITAR,

    /** Acabas de borrar un mensaje tuyo. */
    BORRAR,

    /** Cambiaron los ignorados o las palabras filtradas y hay que repintar el hilo. */
    FILTROS
}

/** Qué página hay que pedir y a qué mensaje saltar al recargar. */
data class Recarga(val pagina: Int, val pidDestacado: String)

/**
 * Qué volver a pedir cuando el hilo abierto tiene que refrescarse.
 *
 * Existe porque `reloadCurrentThread()` pedía **siempre la página 1**, y eso era el bug que
 * reportó Green Floyd el 2026-09-13: editabas un mensaje en la página 12 y la app te escupía
 * arriba del todo del hilo, sin ver siquiera el cambio que acababas de hacer. No era un
 * descuido escondido — estaba en el comentario de la función, así que aquí queda la regla
 * escrita y con tests para que no vuelva.
 *
 * La paginación de esta app **sustituye** la lista (no acumula), así que "la página cargada" es
 * la única que hay en pantalla: volver a pedirla es volver exactamente a donde estabas.
 */
object RecargaHilo {

    fun tras(motivo: Motivo, paginaCargada: Int, pid: String): Recarga = Recarga(
        pagina = paginaCargada.coerceAtLeast(1),
        // Solo al editar hay algo que enseñar. Tras borrar el post ya no existe, y saltar a un
        // pid que no está deja la lista quieta sin que nada explique por qué.
        pidDestacado = if (motivo == Motivo.EDITAR) pid else ""
    )
}
