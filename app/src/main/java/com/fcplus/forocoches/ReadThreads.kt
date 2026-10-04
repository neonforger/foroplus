package com.fcplus.forocoches

import android.content.Context
import android.content.SharedPreferences

/**
 * ¿Se pinta el título en negrita? **Manda la memoria de la app, no ForoCoches.**
 *
 * Antes decidía FC cuando el hilo no se había abierto aquí, y era la fuente equivocada: el
 * estado de leído de FC es **de la cuenta, no del dispositivo**. Quien lee el foro también en
 * el ordenador llegaba a la app y lo veía casi todo en gris, porque para FC ya estaba leído.
 * Lo reportaron Márquez y Green Floyd el 2026-09-10, los dos usuarios de modo clásico en PC.
 *
 * Ahora la regla es una sola y vale para TODAS las listas —incluidas las que salen de una
 * búsqueda, donde FC ni siquiera manda la marca (gotcha 2)—: **negrita salvo que lo hayas
 * abierto en la app y no haya crecido desde entonces**. Más fácil de explicar ("la app lleva
 * su propia cuenta") y sin depender de un estado que el usuario cambia desde otro sitio sin
 * enterarse.
 *
 * El precio, asumido: si lees mucho en el ordenador, aquí saldrá en negrita lo que allí ya
 * leíste.
 *
 * @param readReplies nº de respuestas que tenía el hilo cuando lo abriste EN LA APP, o null si
 *                    nunca lo has abierto aquí.
 * @param replies     nº de respuestas actual, tal cual viene del extractor ("", "42"…).
 */
fun isThreadUnread(readReplies: Int?, replies: String): Boolean {
    if (readReplies == null) return true
    val now = replies.toIntOrNull() ?: return false
    return now > readReplies
}


/**
 * Estimación CONSERVADORA (nunca por encima del valor real) del nº de respuestas de un
 * hilo, para marcarlo leído cuando se abre por un camino que NO pasa por la lista
 * (notificación, cita/mención, deep link) — ahí no llega ninguna fila de forumdisplay con
 * el contador real, solo lo que trae la página del hilo que se acaba de cargar.
 *
 * Asunción de paginación de FC (la misma que usa el resto de la app para saltar a la
 * última página con page=<total>): SOLO la última página puede tener menos posts que el
 * tamaño fijo de página; ninguna página tiene MÁS que ese tamaño. Por tanto
 * `postsOnPage` (los posts de la página que ha llegado, sea o no la última) multiplicado
 * por `page` es, como mucho, el nº de posts acumulados hasta esa página — nunca más — y
 * como puede haber páginas por delante (pageCount > page) que no se han contado, el hilo
 * completo tiene COMO POCO esa cifra. "Respuestas" excluye el primer mensaje de apertura,
 * de ahí el -1.
 *
 * Puede infraestimar mucho si el link aterriza lejos de la última página (p.ej. una cita
 * antigua en un hilo con muchas páginas después): el hilo puede volver a verse en negrita
 * antes de tiempo. Es el coste asumido, la dirección segura — nunca al revés (sobreestimar
 * dejaría el hilo mudo para siempre, el bug que esto reemplaza).
 */
fun estimateReadReplies(page: Int, pageCount: Int, postsOnPage: Int): Int {
    val safePage = page.coerceAtLeast(1).coerceAtMost(pageCount.coerceAtLeast(1))
    val safePosts = postsOnPage.coerceAtLeast(0)
    return (safePage * safePosts - 1).coerceAtLeast(0)
}

/**
 * Memoria local de hilos leídos: por cada hilo abierto en la app guarda cuántas respuestas
 * tenía en ese momento.
 *
 * Se guarda como UNA cadena "tid:replies,tid:replies,…" en vez de un StringSet porque hace
 * falta ORDEN: el desalojo es por antigüedad (el más viejo primero) y un StringSet no la
 * conserva. El hilo releído vuelve al final de la cola.
 */
/**
 * Las marcas de leído son de la cuenta, no del dispositivo: el fichero se resuelve por uid
 * activo en cada acceso (ver [prefs] más abajo).
 */
class ReadThreadsRepository(context: Context) {

    private val ctx = context.applicationContext

    companion object {
        /** Tope de hilos recordados. Por encima se desaloja el más antiguo. */
        const val MAX = 500
        private const val KEY = "read_threads"
    }

    /**
     * El fichero se resuelve EN CADA ACCESO, no al construir: el uid de la cuenta activa no se
     * conoce hasta que el usuario entra, y cambia al cambiar de cuenta. Resolverlo aquí evita
     * tener que recrear este repositorio (y volver a cablear a quien lo usa) en cada cambio, y
     * con ello la clase de bug "el repositorio se quedó apuntando a la cuenta anterior".
     * `getSharedPreferences` está cacheado por nombre en Android, así que esto es barato.
     */
    private val prefs: SharedPreferences
        get() = ctx.getSharedPreferences(
            ClavesPorCuenta.fichero("fc_leidos", uidActivo()), Context.MODE_PRIVATE
        )

    /** Uid de la cuenta activa, leído de disco: lo escribe MainActivity y lo leen también los
     *  procesos en segundo plano, que no ven la memoria de la app. */
    private fun uidActivo(): String =
        ctx.getSharedPreferences("shell_prefs", Context.MODE_PRIVATE)
            .getString("uid_activo", "") ?: ""

    // Cache en memoria del mapa ya parseado. Sin ella, `readReplies` — llamado una vez POR
    // FILA en cada bind del RecyclerView de la lista — releía y reparsía la cadena entera de
    // SharedPreferences (hasta MAX=500 entradas) en el hilo de UI en cada scroll. Se rellena
    // en el primer acceso (perezosa) y se mantiene al día en `markRead` (misma instancia
    // mutada + persistida). Se invalida sola en `mapNow()` si el uid activo ya no coincide con
    // [cacheUid] — ver el comentario de ese campo.
    private var cache: LinkedHashMap<String, Int>? = null

    /**
     * Uid con el que se llenó [cache]. Al cambiar de cuenta la caché de la anterior no vale, y
     * lo peligroso no es solo leer de más: el siguiente `markRead` persistiría ese mapa
     * heredado en el fichero de la cuenta NUEVA, mezclando los leídos de las dos. Se compara
     * en cada acceso en vez de exponer un `invalidar()`, para que no dependa de que alguien se
     * acuerde de llamarlo.
     */
    private var cacheUid: String? = null

    private fun mapNow(): LinkedHashMap<String, Int> {
        val uid = uidActivo()
        val actual = cache
        if (actual != null && cacheUid == uid) return actual
        return load().also { cache = it; cacheUid = uid }
    }

    private fun load(): LinkedHashMap<String, Int> {
        val raw = prefs.getString(KEY, "") ?: ""
        val map = LinkedHashMap<String, Int>()
        for (entry in raw.split(',')) {
            if (entry.isEmpty()) continue
            val sep = entry.lastIndexOf(':')
            if (sep <= 0) continue
            val tid = entry.substring(0, sep)
            val n = entry.substring(sep + 1).toIntOrNull() ?: continue
            map[tid] = n
        }
        return map
    }

    private fun save(map: LinkedHashMap<String, Int>) {
        prefs.edit()
            .putString(KEY, map.entries.joinToString(",") { "${it.key}:${it.value}" })
            .apply()
    }

    /** Marca el hilo como leído AHORA, con el nº de respuestas que tiene en este momento. */
    fun markRead(tid: String, replies: String) {
        if (tid.isEmpty()) return
        // "1.680" → 1680: FC pone separador de miles a partir de 1.000 respuestas.
        val n = replies.replace(".", "").replace(",", "").toIntOrNull() ?: 0
        val map = mapNow()
        map.remove(tid)          // fuera y dentro otra vez = vuelve al final de la cola
        map[tid] = n
        while (map.size > MAX) map.remove(map.keys.first())
        save(map)
        cache = map
    }

    /** Respuestas que tenía el hilo al abrirlo, o null si nunca se abrió en la app. */
    fun readReplies(tid: String): Int? = mapNow()[tid]
}
