package com.fcplus.forocoches

/**
 * Quién abrió el hilo (el "OP"), para recuadrar sus mensajes en la vista de hilo.
 *
 * FC **no marca** al creador en ninguna parte del HTML de `showthread`, y la fila de la lista
 * enseña al ÚLTIMO que postea, no al que lo abrió (por eso existe [ThreadCreatorFetcher]).
 * La única señal fiable y gratis es la posición: **el primer post de la página 1 es el suyo**.
 * En cualquier otra página el dato no está, así que se devuelve "" y quien llama conserva el
 * que ya tuviera (o el de [ThreadCreatorCache]).
 *
 * Función pura a propósito: el efecto es puramente visual y no se puede afirmar con un test,
 * pero la REGLA de a quién se marca sí.
 */
object ThreadStarter {

    /** Autor del hilo según la página cargada, o "" si esa página no lo revela. */
    fun of(posts: List<PostItem>, page: Int): String =
        if (page > 1) "" else posts.firstOrNull()?.author?.trim().orEmpty()

    /**
     * ¿Este post es del autor del hilo? Con el autor del hilo desconocido ("") NUNCA marca
     * nada: mejor sin recuadro que con el recuadro en la persona equivocada. Los posts sin
     * autor (anónimos/baneados) tampoco se marcan aunque el creador sea desconocido.
     */
    fun isStarter(author: String, starter: String): Boolean =
        starter.isNotEmpty() && author.isNotEmpty() && author.equals(starter, ignoreCase = true)
}
