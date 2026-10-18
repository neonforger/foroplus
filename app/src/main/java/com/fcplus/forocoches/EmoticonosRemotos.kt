package com.fcplus.forocoches

import android.content.Context
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Los emoticonos de ForoPlus que anuncia `fc_config.json`: se bajan una vez y se guardan en el
 * móvil (`files/emoticonos/`), para estrenar emoticonos sin publicar versión (ver
 * [EmoticonosForoPlus]).
 *
 * Se bajan de `raw.githubusercontent.com`, del repo público (la misma carpeta `docs/emoticonos/`
 * que sirve GitHub Pages para la web), y NO de GitHub Pages: así la app no habla con ningún sitio
 * nuevo, solo con el de la config y la sección +18. El prefijo está en [FuentesPermitidas].
 *
 * Cada fichero se guarda con su versión en el nombre (`v2_senoradetodos.gif`): si la config sube
 * la versión de uno (dibujo nuevo), se baja otra vez y se borra el viejo.
 */
object EmoticonosRemotos {

    const val ORIGEN = "https://raw.githubusercontent.com/neonforger/foroplus/main/docs/emoticonos/"

    /** Un emoticono de 88x20 animado pesa 2,5 KB: 64 KB es un tope muy holgado. */
    private const val MAX_BYTES = 64 * 1024

    private fun carpeta(ctx: Context) = File(ctx.filesDir, "emoticonos")

    /** Engancha el disco a [EmoticonosForoPlus]. Llamar al arrancar, antes de pintar nada. */
    fun iniciar(ctx: Context) {
        val app = ctx.applicationContext
        EmoticonosForoPlus.disco = { f -> guardado(app, f)?.readBytes() }
        EmoticonosForoPlus.enDisco = { f -> guardado(app, f) != null }
    }

    /**
     * Toma la lista de [config] y baja, en segundo plano, los que falten o hayan cambiado de
     * versión. Sin config o sin red no pasa nada: se queda con lo que ya tuviera.
     */
    fun sincronizar(ctx: Context, config: JSONObject?) {
        val app = ctx.applicationContext
        val lista = EmoticonosForoPlus.deConfig(config)
        EmoticonosForoPlus.remotos = lista
        val pendientes = lista.filter { e ->
            EmoticonosForoPlus.DENTRO.none { it.fichero == e.fichero } &&
                versionGuardada(app, e.fichero) < e.version
        }
        if (pendientes.isEmpty()) return
        Thread {
            for (e in pendientes) {
                try { bajar(app, e) } catch (ex: Exception) {
                    android.util.Log.d("FC_SHELL", "emoticono ${e.fichero} no bajado: $ex")
                }
            }
        }.start()
    }

    private fun bajar(app: Context, e: EmoticonosForoPlus.Emoticono) {
        val url = ORIGEN + e.fichero
        if (!FuentesPermitidas.aceptada(url)) return
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 8_000
        conn.readTimeout = 8_000
        conn.instanceFollowRedirects = false
        try {
            if (conn.responseCode != 200) return
            if (conn.contentLengthLong > MAX_BYTES) return
            val bytes = conn.inputStream.use { entrada ->
                val out = java.io.ByteArrayOutputStream()
                val buf = ByteArray(8 * 1024)
                while (true) {
                    val n = entrada.read(buf)
                    if (n < 0) break
                    if (out.size() + n > MAX_BYTES) return
                    out.write(buf, 0, n)
                }
                out.toByteArray()
            }
            // Solo GIF: un 404 de HTML o cualquier otra cosa no se guarda como emoticono.
            if (!GifAnimado.esGif(bytes)) return
            val dir = carpeta(app).apply { mkdirs() }
            val tmp = File(dir, "${e.fichero}.tmp")
            tmp.writeBytes(bytes)
            // Se borran las versiones viejas y se coloca la nueva de una vez (sin ficheros a medias).
            // versionDe y no un endsWith: "v1_x_roto.gif" es de x_roto.gif, no una versión de roto.gif.
            dir.listFiles()?.filter { versionDe(it.name, e.fichero) > 0 }?.forEach { it.delete() }
            tmp.renameTo(File(dir, "v${e.version}_${e.fichero}"))
        } finally {
            conn.disconnect()
        }
    }

    /** El fichero guardado de [fichero] (la versión más alta), o null si no hay. */
    private fun guardado(app: Context, fichero: String): File? {
        if (!EmoticonosForoPlus.ficheroValido(fichero)) return null
        return carpeta(app).listFiles()
            ?.filter { versionDe(it.name, fichero) > 0 }
            ?.maxByOrNull { versionDe(it.name, fichero) }
    }

    private fun versionGuardada(app: Context, fichero: String): Int =
        guardado(app, fichero)?.let { versionDe(it.name, fichero) } ?: 0

    /** `v3_x.gif` → 3; cualquier otra cosa → 0. */
    internal fun versionDe(nombre: String, fichero: String): Int {
        if (!nombre.startsWith("v") || !nombre.endsWith("_$fichero")) return 0
        return nombre.substring(1, nombre.length - fichero.length - 1).toIntOrNull()?.takeIf { it > 0 } ?: 0
    }
}
