package com.fcplus.forocoches

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Una copia guardada, tal como se enseña en la pantalla de Descargados. */
data class HiloDescargado(
    val tid: String,
    val titulo: String,
    val paginas: Int,
    val mensajes: Int,
    val guardadoEn: Long,
    val bytes: Long
)

/**
 * Dónde viven los hilos descargados: `filesDir/descargas/<tid>/hilo.json`.
 *
 * **`filesDir` y no `cacheDir`**, y no es un detalle: lo que vive en la caché se lo lleva
 * "borrar caché" del móvil, que es justo una de las tres cosas de las que había que
 * protegerse. Aquí aguanta eso y aguanta las actualizaciones de la app; se pierde solo al
 * borrar datos o desinstalar, que es el precio de no tener servidor (decisión del dueño).
 *
 * **Común a todas las cuentas**, sin pasar por [ClavesPorCuenta]: un hilo es contenido
 * público, no algo tuyo, y guardar una copia por cada multi solo gastaría espacio.
 *
 * El fichero es el MISMO JSON que produce el motor y que come `onThreadJson`, con las páginas
 * una detrás de otra. Así una copia se pinta con el render nativo de siempre y se ve igual que
 * un hilo vivo, sin una rama de dibujo aparte.
 */
class AlmacenDescargas(context: Context) {

    private val raiz = File(context.applicationContext.filesDir, "descargas")

    private fun carpeta(tid: String) = File(raiz, Descargas.carpetaDe(tid))

    private fun fichero(tid: String) = File(carpeta(tid), "hilo.json")

    fun existe(tid: String): Boolean = Descargas.tidValido(tid) && fichero(tid).isFile()

    /**
     * Guarda la copia entera. [paginas] son los payloads del motor, en orden.
     *
     * Se escribe en un temporal y se renombra al final: si la app muere a mitad, la copia
     * anterior sigue entera. Media copia es peor que ninguna — parecería buena.
     */
    fun guardar(tid: String, titulo: String, paginas: List<String>): Boolean {
        if (!Descargas.tidValido(tid) || paginas.isEmpty()) return false
        val dir = carpeta(tid)
        if (!dir.isDirectory && !dir.mkdirs()) return false
        val arr = JSONArray()
        var mensajes = 0
        for (p in paginas) {
            try {
                val o = JSONObject(p)
                mensajes += o.optJSONArray("posts")?.length() ?: 0
                arr.put(o)
            } catch (_: Exception) {
                // Una página ilegible no puede tumbar la copia entera: se salta y se cuenta
                // lo que sí vino. Mejor una copia incompleta y avisada que ninguna.
            }
        }
        if (arr.length() == 0) return false
        val raizJson = JSONObject()
            .put("tid", tid)
            .put("titulo", titulo)
            .put("paginas", arr.length())
            .put("mensajes", mensajes)
            .put("guardadoEn", System.currentTimeMillis())
            .put("version", 1)
            .put("contenido", arr)
        return try {
            val tmp = File(dir, "hilo.json.tmp")
            tmp.writeText(raizJson.toString())
            val fin = fichero(tid)
            if (fin.exists()) fin.delete()
            tmp.renameTo(fin)
        } catch (_: Exception) {
            false
        }
    }

    /** Cuántos mensajes tiene la copia guardada, o 0 si no hay. Lo usa la guarda al sustituir. */
    fun mensajesGuardados(tid: String): Int = leerCabecera(tid)?.mensajes ?: 0

    private fun leerCabecera(tid: String): HiloDescargado? {
        if (!existe(tid)) return null
        return try {
            val o = JSONObject(fichero(tid).readText())
            HiloDescargado(
                tid = o.optString("tid", tid),
                titulo = o.optString("titulo"),
                paginas = o.optInt("paginas"),
                mensajes = o.optInt("mensajes"),
                guardadoEn = o.optLong("guardadoEn"),
                bytes = tamanoDe(carpeta(tid))
            )
        } catch (_: Exception) {
            null
        }
    }

    /** Todas las copias, de la más reciente a la más vieja. */
    fun listar(): List<HiloDescargado> {
        val dirs = raiz.listFiles() ?: return emptyList()
        return dirs.filter { it.isDirectory }
            .mapNotNull { leerCabecera(it.name) }
            .sortedByDescending { it.guardadoEn }
    }

    /** El JSON completo de una copia, o null. Lo pinta el render de siempre. */
    fun leer(tid: String): String? =
        if (!existe(tid)) null else try { fichero(tid).readText() } catch (_: Exception) { null }

    fun borrar(tid: String): Boolean =
        Descargas.tidValido(tid) && carpeta(tid).deleteRecursively()

    /** Lo que ocupan TODAS las copias. Es lo que se enseña arriba en la pantalla. */
    fun bytesTotales(): Long = tamanoDe(raiz)

    // ── Imágenes de una copia ───────────────────────────────────────────────

    private fun carpetaImagenes(tid: String) = File(carpeta(tid), "img")

    /**
     * Guarda una imagen como MINIATURA, no como original.
     *
     * Decisión del dueño: miniaturas y punto. Un hilo normal de 60 páginas pasa de ~18 MB en
     * originales a ~900 KB así, y uno muy cargado de 90-180 MB a 5-9 MB. **Consecuencia que hay
     * que tener presente: la miniatura de un GIF es un fotograma, así que en una copia los GIF
     * se ven quietos.**
     */
    fun guardarImagen(tid: String, url: String, bytes: ByteArray, ladoMax: Int = 400): Boolean {
        val dir = carpetaImagenes(tid)
        if (!dir.isDirectory && !dir.mkdirs()) return false
        return try {
            val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
            android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            val opts = android.graphics.BitmapFactory.Options().apply {
                inSampleSize = sampleSizeFor(bounds.outWidth, bounds.outHeight, ladoMax)
            }
            val bmp = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
                ?: return false
            val f = File(dir, Descargas.nombreDeImagen(url))
            f.outputStream().use { bmp.compress(android.graphics.Bitmap.CompressFormat.JPEG, 80, it) }
            bmp.recycle()
            true
        } catch (_: Throwable) {   // incluye OutOfMemoryError
            false
        }
    }

    /** Los bytes guardados de una imagen de esta copia, o null. */
    fun imagen(tid: String, url: String): ByteArray? {
        val f = File(carpetaImagenes(tid), Descargas.nombreDeImagen(url))
        return if (f.isFile) try { f.readBytes() } catch (_: Exception) { null } else null
    }

    private fun tamanoDe(f: File): Long {
        if (!f.exists()) return 0
        if (f.isFile) return f.length()
        return f.listFiles()?.sumOf { tamanoDe(it) } ?: 0
    }
}
