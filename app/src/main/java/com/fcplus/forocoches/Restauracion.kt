package com.fcplus.forocoches

/**
 * Cuándo el atrás puede quedarse con lo que ya hay pintado y cuándo tiene que traer otra
 * página del hilo.
 *
 * Existe aparte y con tests porque este atrás ya se ha roto TRES veces de forma invisible: una
 * por el targetSdk 35, y otra —medida el 2026-09-11 en `t=10802213`, página 3— porque bastaba
 * con que fuera "el mismo hilo". No lo es: la paginación de esta app **sustituye** la lista en
 * vez de acumular, así que tras saltar a una cita de otra página el post por el que ibas
 * leyendo ya no está cargado. Con la regla vieja, `restoreThread` no encontraba el ancla
 * (`idx=-1`) y salía en silencio: el atrás no hacía absolutamente nada y te dejaba clavado en
 * la cita.
 */
object Restauracion {

    /**
     * @param hiloEnPantalla el hilo que se restaura es el que ya está pintado.
     * @param hayAncla la entrada guarda el post por el que ibas leyendo.
     * @param anclaCargada ese post sigue entre los cargados.
     * @param paginaGuardada la página de la entrada a la que se vuelve.
     * @param paginaCargada la página que hay pintada ahora.
     * @return true si hay que pedir la página guardada; false si basta con desplazarse.
     */
    fun hayQueRecargar(
        hiloEnPantalla: Boolean,
        hayAncla: Boolean,
        anclaCargada: Boolean,
        paginaGuardada: Int,
        paginaCargada: Int
    ): Boolean = when {
        !hiloEnPantalla -> true
        hayAncla -> !anclaCargada
        // Sin ancla NO vale "cualquier sitio": la entrada se apila al saltar de página y solo
        // coge ancla si luego haces scroll. Pasando páginas sin bajar (1 -> 2 -> 3), la de la 2
        // se quedaba vacía, el atrás daba por buena la 3 que había pintada y no hacía nada; el
        // siguiente saltaba a la 1 (Green Floyd y Márquez, 2026-09-26).
        else -> paginaGuardada != paginaCargada
    }

    /**
     * ¿Hay que volver a lanzar la búsqueda al retroceder a una lista de resultados?
     *
     * Solo si lo que está pintado ya no es esa búsqueda. Repetirla siempre sería lento y
     * castigaría al buscador de FC, que va justo; no repetirla nunca fue el fallo que
     * reportaron Green Floyd y Rober, porque al volver veías el subforo.
     *
     * @param queryGuardada la de la pantalla a la que se vuelve; vacía = pila antigua o
     *   entrada por notificación, y entonces no hay nada que rehacer.
     */
    fun hayQueRehacerBusqueda(
        queryGuardada: String,
        sourceCargado: String,
        queryCargada: String,
        listaCargada: Boolean
    ): Boolean {
        if (queryGuardada.isEmpty()) return false
        val yaEstaba = sourceCargado == "search" && queryCargada == queryGuardada && listaCargada
        return !yaEstaba
    }

    /**
     * ¿Hay que volver a pedir la lista al retroceder a un subforo, al TOP o al Popurrí?
     *
     * Solo si lo pintado no es ya esa lista. Antes la pila guardaba siempre "home" y el atrás
     * rehacía Inicio, así que entrar a un hilo desde los hilos del momento y volver te dejaba
     * en el General (javier hg, 2026-09-26).
     *
     * @param fidGuardado 0 = la entrada no sabe de qué subforo era; vale el que haya.
     */
    fun hayQueRehacerLista(
        sourceGuardado: String,
        fidGuardado: Int,
        sourceCargado: String,
        fidCargado: Int,
        listaCargada: Boolean
    ): Boolean = when {
        !listaCargada -> true
        sourceGuardado != sourceCargado -> true
        // El Popurrí mezcla varios subforos: la pestaña en la que estuvieras no lo define.
        sourceGuardado == "popurri" -> false
        else -> fidGuardado > 0 && fidGuardado != fidCargado
    }
}
