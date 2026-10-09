package com.fcplus.forocoches

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import java.io.File

/**
 * Guardar o compartir una foto del foro con sus **bytes originales**, no con el bitmap del visor.
 *
 * El visor pinta una versión decodificada (reducida, y de un GIF solo el primer fotograma,
 * gotcha 30): guardar eso sería guardar una foto peor y un GIF quieto. Aquí se escribe el
 * fichero tal y como llegó, con su tipo real ([TipoImagen]).
 */
object GuardarImagen {

    /** Carpeta dentro de Imágenes donde acaban las fotos guardadas. */
    const val CARPETA = "ForoPlus"

    /**
     * Guardar en la galería solo es directo desde Android 10: antes hace falta el permiso de
     * almacenamiento, y en vez de pedírselo a todo el mundo por una función así, en esas
     * versiones se usa el "guardar como" del sistema ([escribirEn]).
     */
    val directoEnGaleria: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q

    /**
     * Android 10+: la escribe en `Imágenes/ForoPlus` por MediaStore, sin permisos. Mientras se
     * escribe va marcada como pendiente para que la galería no enseñe un fichero a medias, y si
     * algo falla se borra la entrada en vez de dejar una foto vacía.
     */
    fun enGaleria(ctx: Context, bytes: ByteArray, tipo: TipoImagen, nombre: String): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false
        val resolver = ctx.contentResolver
        val datos = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, nombre)
            put(MediaStore.Images.Media.MIME_TYPE, tipo.mime)
            put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/$CARPETA")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, datos) ?: return false
        return try {
            resolver.openOutputStream(uri)?.use { it.write(bytes) } ?: throw java.io.IOException("sin salida")
            resolver.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
            true
        } catch (_: Exception) {
            resolver.delete(uri, null, null)
            false
        }
    }

    /** Android 7-9: escribe en el sitio que la persona eligió en el "guardar como". */
    fun escribirEn(ctx: Context, destino: Uri, bytes: ByteArray): Boolean = try {
        ctx.contentResolver.openOutputStream(destino)?.use { it.write(bytes) } != null
    } catch (_: Exception) {
        false
    }

    /**
     * Copia para el compartir de Android, cedida por el FileProvider (la misma carpeta
     * `compartir/` de la caché que usan las tarjetas). Un nombre fijo por tipo: no interesa ir
     * acumulando fotos en la caché, cada compartir pisa la anterior.
     */
    fun paraCompartir(ctx: Context, bytes: ByteArray, tipo: TipoImagen): Uri? = try {
        val dir = File(ctx.cacheDir, "compartir").apply { mkdirs() }
        val f = File(dir, "imagen.${tipo.extension}")
        f.writeBytes(bytes)
        FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", f)
    } catch (_: Exception) {
        null
    }
}
