package com.fcplus.forocoches

/**
 * Páginas siguientes de una lista de MENSAJES sacada de la búsqueda de FC: "sus mensajes" de
 * un usuario (o los tuyos) y el buscador por mensajes.
 *
 * Hasta el 2026-10-02 solo se pedía la primera página (25 mensajes), y un tester lo notó: *"en
 * el foro te muestran como máximo 12 páginas de 30, pero aquí salen muy pocos"* (Green Floyd,
 * Telegram 2240). Las listas de HILOS ya paginaban; esta no.
 *
 * Dos reglas que vienen de medir, no de suponer:
 * - **Solo se pagina sobre un `searchid`.** La primera petición es `search.php?do=process…`, y
 *   FC la redirige a `search.php?searchid=N`. Si por lo que sea no hubiera redirección,
 *   añadirle `&page=2` a la URL original lanzaría OTRA búsqueda entera en FC por cada página
 *   (y la búsqueda tiene anti-flood). Sin `searchid` no se pide nada más.
 * - **FC no dice cuándo se acaba: repite la última página para siempre** (gotcha 37). La única
 *   señal fiable es que una página no aporte ni un mensaje nuevo; entonces la lista está agotada.
 *   Se compara por número de mensaje (`p=`), no por la URL entera, que lleva `highlight=`.
 */
object PaginacionMensajes {

    private val SEARCHID = Regex("[?&]searchid=\\d+")
    private val PAGINA = Regex("&page=\\d+")
    private val PID = Regex("[?&]p=(\\d+)")

    /** La URL sobre la que se pide la página N, o null si no se puede paginar sin riesgo. */
    fun base(urlFinal: String): String? {
        val u = urlFinal.trim()
        if (!SEARCHID.containsMatchIn(u)) return null
        return u.replace(PAGINA, "")
    }

    fun pagina(base: String, n: Int): String = "$base&page=$n"

    /** Número del mensaje de una fila; la URL entera si no lo trae (no se pierde la fila). */
    fun clave(url: String): String = PID.find(url)?.groupValues?.get(1) ?: url

    /**
     * Lo que de verdad es nuevo en la página que llega. Vacío = la búsqueda ya no da más
     * (gotcha 37): quien llama marca la lista como agotada y deja de pedir.
     */
    fun nuevos(yaPintados: List<String>, llegan: List<NoticeItem>): List<NoticeItem> {
        val vistos = yaPintados.mapTo(HashSet()) { clave(it) }
        return llegan.filter { vistos.add(clave(it.url)) }
    }

    /** Cuántas pantallas por delante de quien lee se van pidiendo páginas, como mucho (dueño, 2026-11-17). */
    const val PANTALLAS_ADELANTO = 6

    /**
     * ¿Se pide ya la página siguiente? Solo si lo que queda sin ver por debajo cabe en
     * [PANTALLAS_ADELANTO] pantallas. Así, quien deja la búsqueda abierta no la va bajando entera
     * él solo (eso es lo que hace un bot, y lo que gasta el límite de Cloudflare): las páginas se
     * piden porque alguien baja, solo que un poco antes de que llegue.
     */
    fun hayQuePedir(total: Int, ultimaVisible: Int, filasPorPantalla: Int): Boolean {
        if (total <= 0) return false
        val sinVer = total - 1 - ultimaVisible.coerceAtLeast(0)
        return sinVer < PANTALLAS_ADELANTO * filasPorPantalla.coerceAtLeast(1)
    }
}
