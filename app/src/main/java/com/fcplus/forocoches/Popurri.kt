package com.fcplus.forocoches

import java.util.Calendar
import java.util.Locale

/**
 * El **Popurrí**: una sola lista con los hilos de los subforos que tú elijas, mezclados y
 * ordenados por lo último que se ha escrito.
 *
 * Lo pidió Márquez el 2026-08-21 ("en la Ayuda, un popurrí de los foros que elijas: Videojuegos,
 * electrónica...") y volvió a recordarlo el 26. El nombre es suyo; el foro no tiene nada
 * parecido, así que no hereda ninguna palabra de FC.
 *
 * Aquí vive solo la POLÍTICA —qué hora es cada fila y en qué orden van—, que es lo único con
 * enjundia. Traer las páginas es cosa del motor y pintarlas del adaptador de siempre.
 */
object Popurri {

    /** Cuántos subforos caben. Cada uno es una petición cada vez que abres la pestaña. */
    const val TOPE = 5

    /** Cómo se guarda la elección en las preferencias. */
    const val PREF = "popurri_fids"

    private val MESES = listOf(
        "ene", "feb", "mar", "abr", "may", "jun",
        "jul", "ago", "sep", "oct", "nov", "dic"
    )

    /**
     * Convierte la hora que pinta FC en una fila del listado a un instante comparable.
     *
     * Los tres formatos son los del gotcha 14, verificados por CDP: **`Hoy 10:57`**,
     * **`Ayer 13:32`** y, en hilos viejos, **`01-jul-2026 14:24`** — mes ABREVIADO y con
     * guiones, no `dd/mm/aa`.
     *
     * Devuelve null si no se entiende. Quien no se entiende va al final: mejor abajo que
     * arriba, porque una fila sin fecha colada la primera parecería lo más reciente.
     */
    fun momento(texto: String, ahora: Long): Long? {
        val t = texto.trim().lowercase(Locale("es"))
        if (t.isEmpty()) return null
        val hm = Regex("""(\d{1,2}):(\d{2})""").find(t) ?: return null
        val hora = hm.groupValues[1].toInt()
        val minuto = hm.groupValues[2].toInt()
        if (hora > 23 || minuto > 59) return null

        val cal = Calendar.getInstance()

        val fecha = Regex("""(\d{1,2})-([a-zé]{3,4})-(\d{2,4})""").find(t)
        when {
            fecha != null -> {
                val mes = MESES.indexOfFirst { fecha.groupValues[2].startsWith(it) }
                if (mes < 0) return null
                var anio = fecha.groupValues[3].toInt()
                if (anio < 100) anio += 2000
                cal.clear()
                cal.set(anio, mes, fecha.groupValues[1].toInt(), hora, minuto)
            }
            t.startsWith("hoy") || t.startsWith("ayer") -> {
                cal.timeInMillis = ahora
                if (t.startsWith("ayer")) cal.add(Calendar.DAY_OF_YEAR, -1)
                cal.set(Calendar.HOUR_OF_DAY, hora)
                cal.set(Calendar.MINUTE, minuto)
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
            }
            else -> return null
        }
        return cal.timeInMillis
    }

    /**
     * Mezcla las listas de varios subforos en una sola, de lo más reciente a lo más antiguo.
     *
     * - Un mismo hilo que salga en dos subforos aparece **una vez** (se compara por tid).
     * - Las filas cuya hora no se entiende van al final, conservando el orden en el que
     *   venían: no se inventa una posición para ellas.
     */
    fun mezclar(listas: List<List<ThreadItem>>, ahora: Long): List<ThreadItem> {
        val vistos = HashSet<String>()
        val unicos = ArrayList<ThreadItem>()
        for (lista in listas) {
            for (h in lista) if (h.tid.isEmpty() || vistos.add(h.tid)) unicos.add(h)
        }
        // sortedByDescending es ESTABLE, así que las filas sin hora (todas con la misma clave)
        // mantienen el orden de llegada en vez de barajarse en cada refresco.
        return unicos.sortedByDescending { momento(it.time, ahora) ?: Long.MIN_VALUE }
    }

    /**
     * Apunta en cada hilo de qué subforo viene, para la miga de la fila. Va ANTES de [mezclar]:
     * si un hilo sale en dos subforos, la mezcla se queda con su primera aparición, y así el
     * subforo viaja con ella.
     */
    fun conForo(fid: Int, hilos: List<ThreadItem>): List<ThreadItem> =
        if (fid <= 0) hilos else hilos.map { it.copy(foroFid = fid) }

    /**
     * Lo que se pinta encima del título: el nombre del subforo tal y como lo escribe FC
     * ("Electrónica / Informática"). "" si el hilo no trae subforo (fuera del Popurrí) o si el
     * nombre aún no se conoce: mejor sin miga que con un número.
     */
    fun etiqueta(fid: Int, nombres: Map<Int, String>): String =
        if (fid <= 0) "" else nombres[fid]?.trim().orEmpty()

    /** Los subforos elegidos, tal y como se guardan: "2,45,109". */
    fun leer(guardado: String): List<Int> =
        guardado.split(',')
            .mapNotNull { it.trim().toIntOrNull() }
            .distinct()
            .take(TOPE)

    /** Da la vuelta: de la lista a la cadena que se guarda. */
    fun guardar(fids: List<Int>): String = fids.distinct().take(TOPE).joinToString(",")

    /**
     * Marca o desmarca un subforo, respetando el tope.
     *
     * Al pasarse del tope **no se ignora la pulsación en silencio**: se saca el más antiguo de
     * la selección y entra el nuevo. Un botón que no hace nada y no dice por qué es peor que
     * un cambio que se ve.
     */
    fun alternar(actuales: List<Int>, fid: Int): List<Int> {
        if (fid in actuales) return actuales.filter { it != fid }
        val nuevos = actuales + fid
        return if (nuevos.size <= TOPE) nuevos else nuevos.drop(nuevos.size - TOPE)
    }
}
