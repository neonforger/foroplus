package com.fcplus.forocoches

/**
 * Qué petición de la lista vale y cuál llega tarde.
 *
 * EL BUG (Zorro 2026-09-13, DanyCup92 y Juan 2026-09-25): la pestaña General marcada y la lista
 * de otro subforo, o hilos de dos subforos mezclados. Reproducido el 2026-09-25 tocando dos
 * pestañas con 150 ms de separación: salían dos "pedir" y UNA "llega", y la que llegaba era
 * la del subforo anterior. Dos fallos sumados:
 *  1. Con una petición en vuelo, la siguiente se TRAGABA en silencio (`loadingPage`), también
 *     la de la pestaña nueva.
 *  2. La respuesta se pintaba sin mirar de qué era, así que la del subforo viejo acababa en la
 *     pestaña nueva — y, con el scroll infinito, una página 2 atrasada se mezclaba con las del
 *     subforo nuevo.
 *
 * Las listas de subforo (y Suscripciones) devuelven la URL que se pidió, así que se sabe con
 * exactitud si una respuesta es la que se está esperando.
 */
object PeticionLista {

    private fun esListaDeSubforo(url: String) =
        url.contains("forumdisplay.php") || url.contains("subscription.php")

    /**
     * @param esperada la URL de la última lista de subforo pedida, o "" si lo que hay en
     *   pantalla no es una lista de subforo (búsqueda, Mis hilos…).
     */
    fun aceptar(urlRespuesta: String, esperada: String): Boolean =
        if (esperada.isNotEmpty()) urlRespuesta == esperada
        else !esListaDeSubforo(urlRespuesta)

    /**
     * La página 1 es siempre una orden del usuario (cambiar de pestaña, recargar, entrar en
     * una lista) y sale aunque haya otra en vuelo. Solo las páginas siguientes, que el scroll
     * pide a ráfagas, esperan a que acabe la anterior.
     */
    fun hayQuePedir(pagina: Int, cargando: Boolean): Boolean = pagina <= 1 || !cargando
}
