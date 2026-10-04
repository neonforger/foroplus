package com.fcplus.forocoches

/**
 * Hilos que el usuario ha mandado callar.
 *
 * Lo pidió Nacho ("ignorar un hilo concreto") y lo apoyó Miguel. Es distinto de ignorar a una
 * persona o una palabra: hay hilos que no molestan por quién escribe ni por su tema, sino
 * porque llevan tres semanas en portada y ya no quieres verlos.
 *
 * Se guarda el **tid y el título**, separados por `|`, y una entrada por línea. El título no es
 * un adorno: sin él, la lista de Opciones sería una columna de números y nadie sabría qué está
 * desocultando. Y poder desocultar no es opcional — ignorar algo por un mal toque y no tener
 * vuelta atrás es peor que no tener la función.
 */
object HilosIgnorados {

    /** Tope. Pasado eso se olvida el más viejo: son hilos, envejecen solos. */
    const val TOPE = 200

    data class Hilo(val tid: String, val titulo: String)

    fun leer(guardado: String): List<Hilo> = guardado.split("\n")
        .mapNotNull { linea ->
            val t = linea.trim()
            if (t.isEmpty()) return@mapNotNull null
            val i = t.indexOf('|')
            val tid = (if (i >= 0) t.substring(0, i) else t).trim()
            if (tid.isEmpty()) null else Hilo(tid, if (i >= 0) t.substring(i + 1).trim() else "")
        }

    fun guardar(hilos: List<Hilo>): String =
        hilos.joinToString("\n") { it.tid + "|" + it.titulo.replace("\n", " ").replace("|", " ") }

    /** Añade (o actualiza el título de) un hilo. El más reciente queda el primero. */
    fun ignorar(previos: List<Hilo>, tid: String, titulo: String, tope: Int = TOPE): List<Hilo> {
        if (tid.isBlank()) return previos
        val resto = previos.filter { it.tid != tid }
        return (listOf(Hilo(tid, titulo)) + resto).take(tope)
    }

    fun olvidar(previos: List<Hilo>, tid: String): List<Hilo> = previos.filter { it.tid != tid }

    fun estaIgnorado(hilos: List<Hilo>, tid: String): Boolean =
        tid.isNotBlank() && hilos.any { it.tid == tid }
}
