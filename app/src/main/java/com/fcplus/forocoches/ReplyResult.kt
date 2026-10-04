package com.fcplus.forocoches

/**
 * Extrae el pid del mensaje recién publicado a partir de la URL FINAL a la que redirige
 * vBulletin tras un POST correcto ("showthread.php?p=NNN#postNNN").
 *
 * Se mira primero el ancla "#postNNN" y luego el parámetro "p=", que es más ambiguo.
 * Devuelve "" si la URL no identifica ningún post (p. ej. el interstitial "gracias por su
 * mensaje" o una URL con solo t=/page=); quien llama debe caer al último post cargado.
 */
fun replyPidFromUrl(url: String): String {
    Regex("#post(\\d+)").find(url)?.let { return it.groupValues[1] }
    Regex("[?&]p=(\\d+)").find(url)?.let { return it.groupValues[1] }
    return ""
}

/**
 * Tras publicar: ¿hay que pedir OTRA página para encontrar tu mensaje? Devuelve cuál, o null.
 *
 * Al publicar se pide la última página que se CONOCÍA antes de hacerlo. Si tu mensaje abrió una
 * nueva (era el primero de la 7), llega la 6 y no está en ella: te dejaba en el último mensaje
 * de la 6 (Juan, 2026-09-21; Green Floyd, 2026-09-23). FC sí dice en esa misma respuesta que ya
 * hay 7 páginas, así que basta con reintentar la nueva última.
 *
 * @param estaTuMensaje si el mensaje buscado está en la página que acaba de llegar. Sin pid en
 *   la redirección, "está" = la página trajo algo nuevo al final.
 */
fun paginaDeMiMensaje(estaTuMensaje: Boolean, pagina: Int, paginasTotales: Int): Int? =
    if (!estaTuMensaje && paginasTotales > pagina) paginasTotales else null
