package com.fcplus.forocoches

/**
 * Lo que dice FC del hilo en su fila del listado, además de si hay algo sin leer.
 *
 * Viene del estado de vBulletin (el viejo sufijo del `statusicon`: `thread_dot_hot_lock_new.gif`),
 * que FC ya no pinta como imagen pero sigue dejando en un COMENTARIO justo antes del icono de la
 * fila: `<!-- _dot_hot-->`. Lo lee `estadoDeFila` en extractor.js y llega aquí tal cual.
 *
 * - `_dot`: has escrito en el hilo. Medido el 2026-09-27 publicando en Pruebas: la fila pasó de
 *   `<!-- -->` a `<!-- _dot-->`.
 * - `_lock`: cerrado. Es vBulletin de fábrica, pero NO se ha visto en vivo (0 de 420 filas).
 * - `_new` y `_hot` no se usan: la novedad la decide [isThreadUnread] (FC + lo que has leído en
 *   la app), y el sobre tiene que decir lo mismo que la negrita del título.
 */
data class EstadoHilo(
    val participado: Boolean = false,
    val cerrado: Boolean = false
) {
    companion object {
        fun de(crudo: String): EstadoHilo {
            val marcas = crudo.trim().split('_').filter { it.isNotEmpty() }.toSet()
            return EstadoHilo(participado = "dot" in marcas, cerrado = "lock" in marcas)
        }
    }
}
