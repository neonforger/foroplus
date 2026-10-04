package com.fcplus.forocoches

/**
 * Qué citas sobreviven a cerrar el composer **sin enviar**.
 *
 * Hay dos formas de citar y esperan cosas distintas, y confundirlas es el fallo que Juan
 * reportó el 2026-08-14 y otro tester volvió a reportar el 2026-08-28:
 *
 * - **"Citar"** en un mensaje: abre el composer y significa *"quiero responder a ESTE"*. Si te
 *   arrepientes y sales, la cita no debe quedarse pegada — si se queda, el siguiente "Citar"
 *   sobre otro mensaje acaba respondiendo a los dos.
 * - **"＋"** (multicita): significa *"ve guardando este para luego"*, y **debe sobrevivir** a
 *   salir del hilo y volver. Eso es una función pedida a propósito (sesión 2026-08-13), no un
 *   descuido, así que no vale limpiar a ciegas al cerrar.
 *
 * La distinción está en la ACCIÓN, no en el momento: se apunta qué citas entraron por "Citar"
 * en esta sesión de composer, y son esas —y solo esas— las que se caen al salir sin enviar.
 */
object CitasPendientes {

    /**
     * Marca [pid] como provisional al pulsar "Citar", **salvo que ya estuviera citado**: si el
     * usuario lo había añadido antes con el ＋, esa decisión es suya y no se descarta por haber
     * abierto y cerrado el composer.
     */
    fun alCitar(yaCitadas: Set<String>, provisionales: Set<String>, pid: String): Set<String> =
        if (pid in yaCitadas) provisionales else provisionales + pid

    /** Las que quedan al cerrar sin enviar: todas menos las provisionales. */
    fun alCerrarSinEnviar(citadas: Set<String>, provisionales: Set<String>): Set<String> =
        citadas - provisionales
}
