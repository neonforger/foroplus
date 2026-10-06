package com.fcplus.forocoches

import android.graphics.ImageDecoder
import android.graphics.drawable.AnimatedImageDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.SystemClock
import android.text.Spanned
import android.text.style.ImageSpan
import android.widget.TextView
import java.nio.ByteBuffer

/**
 * GIFs animados dentro del texto de un mensaje.
 *
 * Los reportó un tester el 2026-08-24: "los gifs no funcionan, se ven solo en estático". Y es
 * literal: todas las imágenes de los posts se decodifican con `BitmapFactory`, que devuelve un
 * `Bitmap` — o sea **un fotograma**. No había nada roto que arreglar; es que nunca hubo
 * animación.
 *
 * Para animar hace falta un `Drawable` que sepa pasar fotogramas ([AnimatedImageDrawable], API
 * 28) y, además, que **alguien repinte la vista**: dentro de un `ImageSpan` no hay ningún
 * `ImageView` que haga de anfitrión, así que el drawable se queda sin `Callback` y, aunque
 * arranque, nadie vuelve a dibujarlo nunca. Por eso [animar] engancha el TextView a mano.
 *
 * Por debajo de la API 28 se queda el fotograma de siempre: no hay decodificador de GIF en el
 * sistema y meter una biblioteca por Android 7/8 no compensa.
 */
object GifAnimado {

    /** Lado máximo al decodificar, igual de generoso que el de las fotos. */
    private const val LADO_MAX = 1600

    /** ¿Estos bytes son un GIF? Los seis primeros son `GIF87a` o `GIF89a`. */
    fun esGif(bytes: ByteArray): Boolean =
        bytes.size >= 6 &&
            bytes[0] == 'G'.code.toByte() &&
            bytes[1] == 'I'.code.toByte() &&
            bytes[2] == 'F'.code.toByte() &&
            bytes[3] == '8'.code.toByte()

    /** ¿Puede este móvil animar un GIF? */
    fun soportado(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P

    /**
     * Monta el drawable animado de [bytes], o null si no se puede (versión de Android antigua,
     * GIF corrupto, o un GIF de un solo fotograma — ahí `ImageDecoder` devuelve una imagen
     * normal y no hay nada que animar).
     */
    fun decodificar(bytes: ByteArray): AnimatedImageDrawable? {
        if (!soportado()) return null
        return try {
            val fuente = ImageDecoder.createSource(ByteBuffer.wrap(bytes))
            val d = ImageDecoder.decodeDrawable(fuente) { decoder, info, _ ->
                // Mismo criterio que las fotos: un GIF enorme decodificado entero se come la
                // memoria, y encima se va a pintar a menos de 900 px de ancho.
                val lado = maxOf(info.size.width, info.size.height)
                var muestra = 1
                while (lado / muestra > LADO_MAX) muestra *= 2
                if (muestra > 1) decoder.setTargetSampleSize(muestra)
            }
            d as? AnimatedImageDrawable
        } catch (_: Throwable) {   // incluye OutOfMemoryError, que no es Exception
            null
        }
    }

    /**
     * Deja [d] animándose dentro de [tv].
     *
     * El `Callback` es la pieza que falta cuando un drawable animado vive en un `ImageSpan`:
     * sin él, `AnimatedImageDrawable` avanza sus fotogramas y no se entera nadie.
     */
    fun animar(d: AnimatedImageDrawable, tv: TextView) {
        // En la práctica nunca llega aquí por debajo de Android 9 (decodificar devuelve null),
        // pero sin la guarda Lint no lo puede saber, y es gratis.
        if (!soportado()) return
        d.callback = object : Drawable.Callback {
            override fun invalidateDrawable(who: Drawable) = tv.invalidate()
            override fun scheduleDrawable(who: Drawable, what: Runnable, cuando: Long) {
                tv.postDelayed(what, cuando - SystemClock.uptimeMillis())
            }
            override fun unscheduleDrawable(who: Drawable, what: Runnable) {
                tv.removeCallbacks(what)
            }
        }
        // Casi todos los GIF ya declaran bucle infinito, pero los que no lo hacen se quedarían
        // congelados tras la primera pasada, que es indistinguible del fallo que se arregla.
        d.repeatCount = AnimatedImageDrawable.REPEAT_INFINITE
        d.start()
    }

    /**
     * Para los GIFs que estuvieran animándose en [tv] y los desengancha.
     *
     * Obligatorio al reciclar el mensaje o al volver a pintarlo: el drawable viejo sigue
     * encolando repintados sobre un TextView que ya está enseñando OTRO mensaje.
     */
    fun parar(tv: TextView) {
        if (!soportado()) return
        val t = tv.text as? Spanned ?: return
        for (s in t.getSpans(0, t.length, ImageSpan::class.java)) {
            (s.drawable as? AnimatedImageDrawable)?.let {
                it.stop()
                it.callback = null
            }
        }
    }
}
