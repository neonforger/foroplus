package com.fcplus.forocoches

/**
 * Quién escribió el ÚLTIMO mensaje de cada hilo del listado.
 *
 * Lo pidió Juan (Telegram 2049, 2147, 2215) y lo pide medio grupo: saber si un hilo se ha movido
 * porque contestó alguien que te interesa. **FC no lo trae en ninguna página barata** (gotcha 18,
 * revisado el 2026-10-02): ni la fila móvil, ni la de escritorio moderna (solo el `@` del que
 * abrió el hilo y un `title` con el arranque del primer post), ni los resultados de búsqueda, ni
 * "mensajes de hoy" (sus 25 posts son SEGUNDOS del foro entero, y los últimos mensajes de las 40
 * filas del General abarcan ~16.000 posts), ni `printthread`/`archive` (redirigen o dan la home).
 *
 * Lo que SÍ funciona, medido: la fila trae gratis el número del último mensaje
 * (`showthread.php?p=NNN`), y FC respeta **`pp=1`** (mensajes por página): `showthread.php?p=NNN&pp=1`
 * devuelve la página con ESE mensaje solo. En la plantilla móvil son **~33 KB por el cable y
 * ~0,2 s**. Barato para una fila, caro para cuarenta, y de ahí las reglas de esta clase:
 *
 * - **Se guarda por número de mensaje, no por hilo.** El autor de un `p=` no cambia jamás, así
 *   que lo ya sabido no se vuelve a pedir nunca; un hilo solo cuesta otra petición cuando alguien
 *   escribe en él.
 * - **Solo lo que se ve.** Se pide al pintar la fila, no al llegar la lista.
 * - **Como mucho [enVuelo] a la vez**, y la cola se queda con las [colaMax] filas más recientes:
 *   si deslizas de golpe hasta la fila 40, las 30 que pasaron de largo no se piden.
 * - **Un fallo no se reintenta en bucle** (lección de las fotos que se pedían sin fin): se apunta
 *   y solo se vuelve a probar tras [reintentarFallidos], que se llama al recargar la lista.
 *
 * Pura y sin Android: la llama MainActivity y la prueba [UltimosPosteadoresTest].
 */
class UltimosPosteadores(
    private val enVuelo: Int = 2,
    private val colaMax: Int = 12,
    private val maxRecordados: Int = 600
) {
    private val conocidos = object : LinkedHashMap<String, String>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>?) =
            size > maxRecordados
    }
    private val pidiendo = LinkedHashSet<String>()
    private val cola = ArrayDeque<String>()
    private val fallidos = HashSet<String>()

    /** El autor del mensaje `pid`, o null si todavía no se sabe. */
    fun autor(pid: String): String? = conocidos[pid]

    /**
     * Una fila que se ha pintado necesita el autor de `pid`. Devuelve los pids que hay que
     * pedir YA (puede ser ninguno: ya se sabe, ya va de camino, o no queda hueco y espera).
     */
    fun quiero(pid: String): List<String> {
        if (pid.isEmpty() || conocidos.containsKey(pid) || pid in pidiendo || pid in fallidos) {
            return emptyList()
        }
        // Lo último que se pinta es lo que está en pantalla: va delante.
        cola.remove(pid)
        cola.addFirst(pid)
        while (cola.size > colaMax) cola.removeLast()
        return lanzar()
    }

    /**
     * Llega la respuesta del motor. `autor` vacío = no se pudo saber (fallo de red, hilo +HD de
     * invitado, o el que escribió es alguien a quien ignoras y FC manda el muñón, gotcha 23).
     * Devuelve los siguientes pids que hay que pedir.
     */
    fun llego(pid: String, autor: String): List<String> {
        pidiendo.remove(pid)
        val limpio = autor.trim()
        if (limpio.isEmpty()) fallidos.add(pid) else conocidos[pid] = limpio
        return lanzar()
    }

    /** Lo que estaba esperando turno ya no se ve (otra lista, otro subforo). */
    fun olvidarCola() = cola.clear()

    /** Al recargar la lista se da otra oportunidad a lo que falló. */
    fun reintentarFallidos() = fallidos.clear()

    /**
     * Lista nueva: se olvida la cola, lo que falló y también lo que iba "de camino". Lo último
     * importa: si el motor se recarga con una petición en vuelo, su respuesta no llega nunca, y
     * sin soltarla esa plaza quedaría ocupada para siempre y no se pediría nada más. Si la
     * respuesta llega tarde, [llego] la apunta igual. Lo ya sabido se conserva.
     */
    fun reiniciar() {
        cola.clear()
        pidiendo.clear()
        fallidos.clear()
    }

    private fun lanzar(): List<String> {
        val salen = ArrayList<String>()
        while (pidiendo.size < enVuelo && cola.isNotEmpty()) {
            val pid = cola.removeFirst()
            if (conocidos.containsKey(pid) || pid in pidiendo) continue
            pidiendo.add(pid)
            salen.add(pid)
        }
        return salen
    }

    companion object {
        private val PID = Regex("[?&]p=(\\d+)")

        /** Número del último mensaje a partir del enlace de la hora de la fila. */
        fun pidDe(lastPostUrl: String): String = PID.find(lastPostUrl)?.groupValues?.get(1) ?: ""

        /**
         * Quién escribió el último mensaje SIN preguntar a nadie: en un hilo sin respuestas es
         * el que lo abrió, que la fila ya trae (gotcha 18). null = hay que preguntar.
         */
        fun sinPreguntar(replies: String, creador: String): String? =
            if (replies.trim() == "0" && creador.isNotBlank()) creador.trim() else null
    }
}
