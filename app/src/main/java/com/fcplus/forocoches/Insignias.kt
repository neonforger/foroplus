package com.fcplus.forocoches

/**
 * Qué número enseñar en la barra de abajo (citas y menciones).
 *
 * El contador lo da ForoCoches, y **no siempre cuadra con lo que la app puede enseñar**: la
 * app filtra por usuarios ignorados y palabras clave, y FC no. Por eso sale el "1 cita sin
 * leer" que al entrar no tiene nada (reportado por V el 2026-09-06). Peor aún: como abrir el
 * panel no cambia nada, ese 1 se quedaba ahí para siempre.
 *
 * La regla es la misma que ya manda en las negritas de la lista: **manda la memoria local**.
 * Se apunta cuánto decía FC la última vez que miraste el panel, y solo se enseña lo que haya
 * POR ENCIMA de eso.
 */
object Insignias {

    /**
     * La marca de agua sigue a FC hacia abajo.
     *
     * Sin esto, leer las citas desde el ordenador (FC baja a 0) dejaría la marca alta y la
     * siguiente cita de verdad no se vería: era el mismo error que esconder un aviso nuevo
     * detrás de uno viejo.
     */
    fun vistoAjustado(deFc: Int, yaVisto: Int): Int = minOf(yaVisto, maxOf(0, deFc))

    /** Lo que se pinta: lo que dice FC menos lo que ya miraste. Nunca negativo. */
    fun aMostrar(deFc: Int, yaVisto: Int): Int =
        maxOf(0, deFc - vistoAjustado(deFc, yaVisto))
}
