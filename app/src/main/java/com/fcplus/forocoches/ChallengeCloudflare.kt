package com.fcplus.forocoches

/**
 * ¿La página que tiene cargada el motor es el challenge de Cloudflare?
 *
 * Hace falta saberlo en Kotlin, no solo en `extractor.js`: el 2026-11-07 FC puso el challenge
 * en TODO el foro y cada página que terminaba de cargar en el motor —**incluido el propio
 * challenge**— contaba como "motor listo" y volvía a pedir el listado. Ese 403 abría otra vez
 * el challenge, que al cargar volvía a pedir… un bucle cada medio segundo que no dejaba
 * resolverlo nunca (medido en el Samsung: `pedir home` / `ERROR home: cloudflare` sin fin).
 *
 * Mismos títulos que reconoce el motor (`/just a moment|attention required|un momento/i`), que
 * Cloudflare sirve en el idioma del navegador, pero más estricto: aquí el título es el de la
 * página que se esté viendo, y un hilo que se llame "Un momento de gloria" dejaría la app
 * esperando un challenge que no existe. El de Cloudflare EMPIEZA así y nunca lleva el nombre
 * del foro; las páginas de FC siempre lo llevan.
 */
object ChallengeCloudflare {
    private val TITULO = Regex("^\\s*(just a moment|attention required|un momento)", RegexOption.IGNORE_CASE)

    fun esTitulo(titulo: String?): Boolean =
        !titulo.isNullOrBlank() && TITULO.containsMatchIn(titulo) &&
            !titulo.contains("forocoches", ignoreCase = true)
}
