package com.fcplus.forocoches

/**
 * Pantalla de contenido a la que se puede VOLVER con el botón atrás.
 *
 * Solo entran aquí las pantallas que enseñan contenido. Lo que se **descarta** (el overlay del
 * reporte, el vídeo a pantalla completa, la capa web de Cloudflare, el composer, el login y
 * Opciones) NO va a la pila: para eso el atrás sigue siendo "cierra esto", como hasta ahora. Si
 * entraran, el atrás podría hacer reaparecer la capa web, que es violar la regla de oro.
 */
sealed class Screen {
    /**
     * Listado nativo. [source] es "home"/"favs"/"mine"/"participated"/"search".
     *
     * [query] solo lo usa "search", y **tiene que viajar en la pila**: una lista de
     * resultados no se puede reconstruir sin saber qué se buscó. Antes la búsqueda no se
     * apilaba siquiera, así que debajo del hilo quedaba el subforo y el atrás te devolvía
     * ahí (reportado por Green Floyd y por Rober, 2026-09-13/14).
     */
    data class ThreadList(
        val source: String,
        val forumId: Int = 0,
        val query: String = "",
        /**
         * Solo "search": la búsqueda por MENSAJES no pinta en el listado, pinta en el panel de
         * avisos. Sin esto, volver atrás a una búsqueda por mensajes la reconstruía como
         * búsqueda por hilos y te cambiaba los resultados bajo los pies.
         */
        val porMensajes: Boolean = false
    ) : Screen()

    /** Citas o menciones aisladas. [kind] es "quotes" o "mentions". */
    data class Notices(val kind: String) : Screen()

    /**
     * Hilo abierto. Guarda lo justo para **reconstruirlo**: su URL, la página en la que
     * estabas y el post por el que ibas leyendo — sin eso, volver a un hilo te devolvería
     * arriba del todo de la página 1.
     */
    data class Thread(
        val url: String,
        val page: Int = 1,
        val anchorPid: String = "",
        /** Desplazamiento fino del ancla: sin él vuelves un post más arriba de donde estabas. */
        val anchorOffset: Int = 0
    ) : Screen()

    /**
     * La actividad de otro usuario: lo que FC enlaza desde su ficha.
     * [modo] es "started" (hilos que abrió), "threads" (en los que participa) o "posts".
     *
     * [enHilo] es el tid al que está acotada la lista, o "" si son todos sus mensajes. **Va
     * en la pila a propósito**: sin él, volver atrás desde un mensaje reconstruía la pantalla
     * en modo global y te sacaba "sus mensajes en CUALQUIER hilo" en vez de los de este. Lo
     * reportó Alberto el 2026-09-14, y la cabecera lo cantaba sola al cambiar de "en este
     * hilo" a "mensajes".
     */
    data class UserActivity(
        val usuario: String,
        val modo: String,
        val enHilo: String = ""
    ) : Screen()

    /** Perfil de otro usuario. */
    data class Member(val uid: String) : Screen()

    /** Bandeja de mensajes privados. */
    object PmInbox : Screen()

    /** Un MP concreto. El asunto viaja con la entrada: la cabecera lo pinta al volver. */
    data class PmDetail(val pmid: String, val subject: String = "") : Screen()

    /** Perfil propio. */
    object Profile : Screen()
}

/**
 * Pila de navegación de la app.
 *
 * Reglas, que son las que hacen que el atrás se sienta "de app y no de maqueta":
 * - Las pestañas de la barra inferior **se apilan sobre Inicio**, y la pila se rehace en cada
 *   salto (Inicio + esta). Así el atrás desde Menciones, Citas o Perfil lleva a Inicio — antes
 *   eran raíces y **se salía de la app**, que es lo que se cambió el 2026-09-12 — y a la vez el
 *   atrás no recorre hacia atrás todas las pestañas que hayas tocado, que era el motivo de
 *   haberlas hecho raíces. De la app solo se sale desde Inicio.
 * - No se apila la pantalla en la que ya estás (evita tener que dar dos veces al atrás).
 * - La pila tiene tope: navegar en círculos (hilo → perfil → hilo → perfil…) no puede crecer
 *   sin límite.
 *
 * Es pura a propósito: el atrás de esta app ya se rompió una vez de forma invisible
 * (targetSdk 35), así que la REGLA se prueba con tests y el pintado se deja aparte.
 */
class NavStack(private val maxDepth: Int = 20) {

    private val entries = ArrayList<Screen>()

    /** Pantalla actual, o null si no hay ninguna (app recién arrancada). */
    val current: Screen? get() = entries.lastOrNull()

    /** Cuántas pantallas hay apiladas. */
    val size: Int get() = entries.size

    /** ¿Estamos en la raíz? Ahí el atrás sale de la app. */
    val atRoot: Boolean get() = entries.size <= 1

    /** Pestaña: descarta todo lo anterior y deja [screen] como única pantalla. */
    fun root(screen: Screen) {
        entries.clear()
        entries.add(screen)
    }

    /** Entra en una pantalla nueva. Repetir la actual no apila. */
    fun push(screen: Screen) {
        if (entries.lastOrNull() == screen) return
        entries.add(screen)
        while (entries.size > maxDepth) entries.removeAt(0)
    }

    /**
     * Actualiza la pantalla actual sin apilar: para cuando cambias de página dentro de un
     * hilo o de post visible. Sin esto, volver al hilo te devolvería a donde entraste y no a
     * donde lo dejaste.
     */
    fun replaceTop(screen: Screen) {
        if (entries.isEmpty()) entries.add(screen) else entries[entries.size - 1] = screen
    }

    /**
     * Atrás: quita la pantalla actual y devuelve a dónde hay que ir. `null` = no queda nada
     * detrás, que quien llame debe traducir en salir de la app.
     */
    fun pop(): Screen? {
        if (entries.size <= 1) return null
        entries.removeAt(entries.size - 1)
        return entries.last()
    }

    /** Estado completo, de la raíz a la actual (para depurar y para los tests). */
    fun snapshot(): List<Screen> = entries.toList()
}
