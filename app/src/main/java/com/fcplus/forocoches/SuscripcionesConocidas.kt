package com.fcplus.forocoches

/**
 * Qué hilos sabemos que están suscritos, para que **la estrella del hilo no mienta**.
 *
 * Lo destapó un vídeo de Márquez (2026-09-16): abría un hilo al que ESTABA suscrito, la estrella
 * salía hueca, la tocaba y la app contestaba *"Quitado de suscripciones"* — o sea que el icono
 * decía lo contrario de la verdad y el primer toque hacía lo contrario de lo que él quería.
 *
 * El motivo está desde el primer día en un comentario de `openThreadNative`: **el botón de
 * suscripción de ForoCoches es estático** (siempre `addsubscription`, estés suscrito o no,
 * verificado por CDP el 2026-07-22), así que la página del hilo no dice el estado y la app
 * pintaba una estrella neutra… que se lee como "no suscrito".
 *
 * Aquí se recuerda lo que la app SÍ ha podido saber, sin una sola petición extra:
 *  - cada página de **Suscripciones** que el usuario abre (ya se descarga y se parsea), y
 *  - el resultado **verificado** de cada toggle (`fcToggleFavorite` relee la lista antes de
 *    contestar, así que su `fav` es la verdad de FC en ese momento).
 *
 * **Solo prueba pertenencia, nunca ausencia.** La lista de Suscripciones viene paginada: de una
 * página se deduce "estos sí", jamás "el resto no". Por eso [anotar] solo suma y el único que
 * puede dar de baja es [marcar], que viene del toggle. Un tid desconocido se sigue pintando con
 * la estrella hueca — exactamente lo de antes, así que en el peor caso no empeora nada.
 *
 * **Limitación asumida**: si te das de baja desde la web, aquí seguirá constando hasta que
 * toques la estrella. Lo contrario —pedir la lista entera al abrir cada hilo— es una descarga
 * por hilo abierto para arreglar un icono.
 *
 * Se guarda como cadena ordenada y **por cuenta** (ver [ClavesPorCuenta]), igual que las
 * búsquedas recientes: el `StringSet` de `SharedPreferences` no conserva el orden y aquí el
 * orden decide a quién se lleva el tope.
 */
object SuscripcionesConocidas {

    /** Tope de hilos recordados. Por encima de esto ya no es un atajo, es una segunda lista. */
    const val TOPE = 500

    private const val SEP = ","

    fun leer(guardado: String): List<String> =
        guardado.split(SEP).map { it.trim() }.filter { it.isNotEmpty() }

    fun guardar(lista: List<String>): String = lista.joinToString(SEP)

    fun esta(previas: List<String>, tid: String): Boolean =
        tid.isNotEmpty() && previas.contains(tid)

    /** Suma los tids de una página de Suscripciones. Nunca quita: la lista está paginada. */
    fun anotar(previas: List<String>, tids: List<String>): List<String> {
        val nuevos = tids.map { it.trim() }.filter { it.isNotEmpty() && !previas.contains(it) }
        if (nuevos.isEmpty()) return previas
        return (nuevos + previas).take(TOPE)
    }

    /** Alta o baja de UN hilo, con el resultado verificado del toggle. */
    fun marcar(previas: List<String>, tid: String, suscrito: Boolean): List<String> {
        val t = tid.trim()
        if (t.isEmpty()) return previas
        val resto = previas.filter { it != t }
        return if (suscrito) (listOf(t) + resto).take(TOPE) else resto
    }
}
