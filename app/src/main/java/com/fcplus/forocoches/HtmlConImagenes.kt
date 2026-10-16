package com.fcplus.forocoches

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.ColorDrawable
import android.text.Html
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.ImageSpan
import android.widget.TextView
import androidx.core.text.HtmlCompat

/**
 * Las imágenes que van DENTRO del texto de un mensaje: cómo se piden, se miden y se colocan.
 *
 * Vivía dentro de `PostAdapter` y por eso solo lo tenían los hilos. Los privados pasaban su HTML
 * por `fromHtml` **sin `ImageGetter`**, y Android pone entonces, por cada `<img>`, su icono
 * genérico de "imagen desconocida": un cuadradito gris en lugar de la foto, el smiley de FC o el
 * GIF. Se vio el 2026-10-15 al mandar un emoticono propio por privado a un tester. Sacarlo aquí
 * es para que hilos y privados pinten igual y no vuelvan a separarse.
 */
object HtmlConImagenes {

    /**
     * El `ImageGetter` de los mensajes. [bmpDe] dice qué bitmap hay ya para una URL; si no hay
     * ninguno, se pide a [PostImages] y [alLlegar] se llama cuando llega, para repintar.
     */
    fun getter(
        tv: TextView,
        maxW: Int,
        bmpDe: (String) -> Bitmap?,
        alLlegar: () -> Unit
    ): Html.ImageGetter {
        val densidad = tv.context.resources.displayMetrics.density
        return Html.ImageGetter { src ->
            val bmp = bmpDe(src)
            if (bmp != null) {
                // El bitmap viene ya reducido al ancho de dibujo, así que NO sirve para medir:
                // manda el tamaño del FICHERO (un píxel de imagen es un CSS px, ver abajo).
                val (anchoReal, altoReal) = PostImages.tamano(src) ?: (bmp.width to bmp.height)
                // Se escala por la densidad: un pixel declarado en HTML es un CSS px, que
                // es un dp. Sin esto un smiley de 20x15 px se pintaba a 20 px FISICOS
                // (6,7dp en un movil de densidad 3) y por eso se veian diminutos.
                val (w, hh) = ImagenEnTexto.medida(anchoReal, altoReal, densidad, maxW)
                // Si la imagen es un GIF y este Android sabe animarlo, se pinta el fichero
                // entero en vez del primer fotograma (ver [GifAnimado]).
                val animado = PostImages.gif(src)?.let { GifAnimado.decodificar(it) }
                if (animado != null) {
                    animado.setBounds(0, 0, w, hh)
                    GifAnimado.animar(animado, tv)
                    animado
                } else {
                    BitmapDrawable(tv.context.resources, bmp).apply { setBounds(0, 0, w, hh) }
                }
            } else {
                PostImages.load(src) { alLlegar() }
                ColorDrawable(Color.TRANSPARENT).apply { setBounds(0, 0, 2, 2) }
            }
        }
    }

    /**
     * Los iconos pequenos (smilies, el logo de Vocaroo...) se centran en el renglon.
     * Html.fromHtml los pega a la BASE del texto, asi que cuelgan por debajo de la linea
     * y se ven torcidos. Solo los pequenos: una foto ocupa su propio renglon y ahi la
     * alineacion no cambia nada.
     */
    fun centrarIconos(texto: Spannable, bmpDe: (String) -> Bitmap?) {
        for (img in texto.getSpans(0, texto.length, ImageSpan::class.java)) {
            val d = img.drawable ?: continue
            // Se mide el bitmap ORIGINAL, no el dibujo: los limites del drawable ya vienen
            // multiplicados por la densidad y el umbral esta en pixeles del fichero.
            val fuente = img.source.orEmpty()
            val bmp = bmpDe(fuente) ?: continue
            val (anchoReal, altoReal) = PostImages.tamano(fuente) ?: (bmp.width to bmp.height)
            if (!ImagenEnTexto.esIcono(anchoReal, altoReal)) continue
            val ini = texto.getSpanStart(img)
            val fin = texto.getSpanEnd(img)
            val flags = texto.getSpanFlags(img)
            texto.removeSpan(img)
            texto.setSpan(IconoCentradoSpan(d, img.source.orEmpty()), ini, fin, flags)
        }
    }
}

/**
 * Un TextView que enseña UN mensaje con imágenes fuera de una lista (el detalle de un privado).
 *
 * Guarda el HTML para poder repintarse solo cuando llegan las imágenes, agrupando las que
 * llegan juntas en un repintado, como hace `PostAdapter.pedirRepintado`. [adornar] es lo
 * propio de cada pantalla (las citas, por ejemplo) y se aplica antes de centrar los iconos,
 * en el mismo orden que en los hilos.
 */
class CuerpoConImagenes(
    private val tv: TextView,
    private val adornar: (SpannableStringBuilder) -> Unit
) {
    private var html = ""
    private var repintado: Runnable? = null

    fun pintar(html: String) {
        this.html = html
        cancelar()
        render()
    }

    /** Texto sin imágenes ("Cargando…", un error). Para los GIF que siguieran animándose. */
    fun texto(texto: CharSequence) {
        html = ""
        cancelar()
        GifAnimado.parar(tv)
        tv.text = texto
    }

    private fun render() {
        // Lo que hubiera animándose es de la pasada anterior: si no se para, sigue encolando
        // repintados sobre un texto que ya no está.
        GifAnimado.parar(tv)
        val maxW = ImagenEnTexto.anchoMaximo(tv.context.resources.displayMetrics.widthPixels)
        val getter = HtmlConImagenes.getter(tv, maxW, PostImages::get) { pedirRepintado() }
        val cuerpo = SpannableStringBuilder(
            HtmlCompat.fromHtml(html, HtmlCompat.FROM_HTML_MODE_LEGACY, getter, null)
        )
        TamanoLetra.aplicar(cuerpo)
        adornar(cuerpo)
        HtmlConImagenes.centrarIconos(cuerpo, PostImages::get)
        tv.text = cuerpo
    }

    private fun pedirRepintado() {
        if (html.isEmpty()) return
        cancelar()
        val tarea = Runnable {
            repintado = null
            if (html.isNotEmpty()) render()
        }
        repintado = tarea
        tv.postDelayed(tarea, PostAdapter.ESPERA_TANDA)
    }

    private fun cancelar() {
        repintado?.let { tv.removeCallbacks(it) }
        repintado = null
    }
}
