package com.fcplus.forocoches

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

/**
 * Lleva una foto del móvil al servicio de imágenes y devuelve su enlace.
 *
 * El QUÉ (a dónde se sube, qué se acepta como respuesta) vive en [SubidaImagen], que es puro y
 * tiene tests. Aquí solo está el CÓMO: leer, reducir y subir.
 */
object SubidorFotos {

    /** De uno en uno: subir dos fotos a la vez por datos móviles no acelera nada. */
    private val pool = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())

    /** Por encima de esto no se intenta: ni el servicio ni la paciencia de nadie lo aguantan. */
    private const val TOPE_BYTES = 20 * 1024 * 1024

    /**
     * @param onDone (enlace, error). Uno de los dos viene vacío. Siempre en el hilo principal.
     */
    fun subir(context: Context, uri: Uri, host: HostImagenes, onDone: (String, String) -> Unit) {
        pool.execute {
            var enlace = ""
            var error = ""
            try {
                val (bytes, extension) = preparar(context, uri, host.ladoMax)
                when {
                    bytes.isEmpty() -> error = "No se pudo leer la foto"
                    bytes.size > TOPE_BYTES -> error = "La foto es demasiado grande"
                    else -> {
                        val respuesta = enviar(host, bytes, "foto.$extension", tipoMime(extension))
                        enlace = SubidaImagen.enlaceDeRespuesta(respuesta)
                        // El servicio contesta 200 hasta cuando falla, con el motivo en texto
                        // plano: si no es un enlace, ESO es el error y se enseña tal cual.
                        if (enlace.isEmpty()) {
                            error = respuesta.trim().take(120).ifEmpty { "El servidor no devolvió el enlace" }
                        }
                    }
                }
            } catch (e: Throwable) {   // incluye OutOfMemoryError
                error = "No se pudo subir la foto"
            }
            main.post { onDone(enlace, error) }
        }
    }

    /**
     * Deja la foto lista para subir: reducida y comprimida.
     *
     * **Los GIF se suben tal cual**: decodificarlos deja un solo fotograma (gotcha 30) y subir
     * un gif quieto es peor que no subirlo. Los PNG se mantienen PNG mientras no se desmadren
     * —las capturas de pantalla son media mitad de lo que se cuelga en FC, y pasarlas a JPEG
     * las llena de suciedad alrededor del texto—; si pesan demasiado, se cae a JPEG.
     */
    private fun preparar(context: Context, uri: Uri, ladoMax: Int): Pair<ByteArray, String> {
        val crudo = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: return ByteArray(0) to "jpg"
        if (GifAnimado.esGif(crudo)) return crudo to "gif"

        val limites = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(crudo, 0, crudo.size, limites)
        val esc = escaladoPara(limites.outWidth, limites.outHeight, ladoMax)
        val opciones = BitmapFactory.Options().apply {
            inSampleSize = esc.muestreo
            if (esc.ajusteFino) {
                inScaled = true
                inDensity = esc.desde
                inTargetDensity = esc.hasta
            }
        }
        val crudoBmp = BitmapFactory.decodeByteArray(crudo, 0, crudo.size, opciones)
            ?: return ByteArray(0) to "jpg"
        // ANTES de comprimir: al comprimir se pierde el EXIF, así que si la foto no se
        // endereza aquí ya no hay quien la enderece (ver [OrientacionFoto]).
        val bmp = enderezar(crudoBmp, crudo)

        // Por los BYTES y no por el MIME del ContentResolver: con un `file://` éste devuelve
        // null y un PNG acababa subido como JPEG (visto en la primera prueba real).
        val png = esPng(crudo)
        if (png) {
            val salida = ByteArrayOutputStream()
            bmp.compress(Bitmap.CompressFormat.PNG, 100, salida)
            if (salida.size() <= 2 * 1024 * 1024) return salida.toByteArray() to "png"
        }
        val salida = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.JPEG, 85, salida)
        return salida.toByteArray() to "jpg"
    }

    /**
     * Gira los píxeles según la etiqueta EXIF del fichero original.
     *
     * Se lee del ORIGINAL ([crudo]) y no del bitmap, porque el bitmap ya no sabe nada de la
     * etiqueta: `BitmapFactory` la ignora al decodificar. Y hay que hacerlo antes de comprimir
     * porque `compress()` no la escribe en la salida — es la combinación que dejaba las fotos
     * tumbadas en el foro aunque en la galería se vieran bien.
     *
     * Si no hay nada que girar se devuelve el mismo bitmap: rehacerlo costaría otra copia
     * entera en memoria, y por aquí pasan fotos de 7 MB.
     */
    @androidx.annotation.VisibleForTesting
    internal fun enderezar(bmp: Bitmap, crudo: ByteArray): Bitmap {
        val giro = try {
            val exif = ExifInterface(ByteArrayInputStream(crudo))
            OrientacionFoto.de(
                exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, OrientacionFoto.NORMAL)
            )
        } catch (_: Throwable) {
            // Un EXIF ilegible no puede impedir que la foto se suba: peor está torcida que no
            // estar.
            return bmp
        }
        if (!giro.hayQueGirar) return bmp
        val m = Matrix().apply {
            postRotate(giro.grados.toFloat())
            if (giro.espejo) postScale(-1f, 1f)
        }
        return try {
            val girado = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
            // createBitmap devuelve el MISMO objeto si la matriz no cambia nada: reciclarlo
            // entonces dejaría un bitmap muerto en las manos de quien comprime.
            if (girado != bmp) bmp.recycle()
            girado
        } catch (_: OutOfMemoryError) {
            bmp
        }
    }

    /** Firma de un PNG: los ocho primeros bytes, que son fijos. */
    private fun esPng(bytes: ByteArray): Boolean =
        bytes.size > 8 && bytes[0] == 0x89.toByte() && bytes[1] == 'P'.code.toByte() &&
            bytes[2] == 'N'.code.toByte() && bytes[3] == 'G'.code.toByte()

    private fun tipoMime(extension: String) = when (extension) {
        "gif" -> "image/gif"
        "png" -> "image/png"
        else -> "image/jpeg"
    }

    /** POST multipart a mano: una sola petición, sin arrastrar una librería entera por esto. */
    private fun enviar(host: HostImagenes, bytes: ByteArray, nombre: String, mime: String): String {
        val frontera = "----fcplus" + System.currentTimeMillis()
        val conn = (URL(host.url).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = 15_000
            readTimeout = 60_000
            setRequestProperty("Content-Type", "multipart/form-data; boundary=$frontera")
        }
        try {
            DataOutputStream(conn.outputStream).use { out ->
                for ((clave, valor) in host.campos) {
                    out.writeBytes("--$frontera\r\n")
                    out.writeBytes("Content-Disposition: form-data; name=\"$clave\"\r\n\r\n")
                    out.write(valor.toByteArray(Charsets.UTF_8))
                    out.writeBytes("\r\n")
                }
                out.writeBytes("--$frontera\r\n")
                out.writeBytes(
                    "Content-Disposition: form-data; name=\"${host.campoFichero}\"; filename=\"$nombre\"\r\n"
                )
                out.writeBytes("Content-Type: $mime\r\n\r\n")
                out.write(bytes)
                out.writeBytes("\r\n--$frontera--\r\n")
            }
            val flujo = if (conn.responseCode in 200..299) conn.inputStream else conn.errorStream
            return flujo?.use { String(it.readBytes(), Charsets.UTF_8) } ?: ""
        } finally {
            conn.disconnect()
        }
    }
}
