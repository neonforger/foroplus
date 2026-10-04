package com.fcplus.forocoches

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream

/** Círculo de color con la inicial, para quien no tiene avatar (que en FC son casi todos). */
object AvatarInicial {
    private val paleta = intArrayOf(
        0xFFC8102E.toInt(), 0xFF00695C.toInt(), 0xFF3949AB.toInt(),
        0xFFEF6C00.toInt(), 0xFF6A1B9A.toInt(), 0xFF2E7D32.toInt(),
        0xFF00838F.toInt(), 0xFF8D6E63.toInt()
    )

    fun bitmap(nombre: String, tam: Int): Bitmap {
        val clave = nombre.trim().ifBlank { "?" }
        val bmp = Bitmap.createBitmap(tam, tam, Bitmap.Config.ARGB_8888)
        val lienzo = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.color = paleta[Math.abs(clave.lowercase().hashCode()) % paleta.size]
        lienzo.drawCircle(tam / 2f, tam / 2f, tam / 2f, p)
        p.color = Color.WHITE
        p.textSize = tam * 0.45f
        p.textAlign = Paint.Align.CENTER
        val fm = p.fontMetrics
        lienzo.drawText(
            clave.first().uppercase(), tam / 2f, tam / 2f - (fm.ascent + fm.descent) / 2f, p
        )
        return bmp
    }
}

/**
 * Dibuja un mensaje del foro como una tarjeta compartible (la "cajita" de Reddit/Instagram).
 *
 * NO es una captura de pantalla: se infla una plantilla propia ([R.layout.card_share_post])
 * fuera de la vista, se rellena y se pinta sobre un lienzo en memoria. Así la tarjeta no
 * arrastra el ⋮, la fila de ＋/Citar ni el resaltado de la interfaz, y puede llevar la marca
 * de la app — que circulando por WhatsApp es publicidad gratis.
 */
object TarjetaCompartir {

    /**
     * Ancho de la tarjeta en dp. Se mide en dp y no en píxeles fijos para que el texto guarde
     * la proporción con el ancho: forzando 1080 px, en un móvil de densidad baja las letras
     * saldrían gigantes respecto a la caja.
     */
    private const val ANCHO_DP = 380

    fun crear(
        ctx: Context,
        autor: String,
        fecha: String,
        avatar: Bitmap?,
        cuerpo: String,
        foto: Bitmap?,
        tituloHilo: String
    ): Bitmap {
        val v = LayoutInflater.from(ctx).inflate(R.layout.card_share_post, null)
        val nombre = autor.trim().removePrefix("@")

        v.findViewById<TextView>(R.id.card_author).text = if (nombre.isEmpty()) "" else "@$nombre"
        v.findViewById<TextView>(R.id.card_date).text = fecha.trim()
        // Un mensaje que es SOLO una cita o solo un vídeo se queda sin texto: si el TextView
        // se deja visible, la tarjeta sale con un agujero enorme en medio (visto en el móvil).
        v.findViewById<TextView>(R.id.card_body).apply {
            text = cuerpo
            visibility = if (cuerpo.isBlank()) View.GONE else View.VISIBLE
        }
        v.findViewById<TextView>(R.id.card_thread).text = tituloHilo.trim()

        val tamAvatar = (52 * ctx.resources.displayMetrics.density).toInt().coerceAtLeast(1)
        v.findViewById<ImageView>(R.id.card_avatar).setImageBitmap(
            // El avatar real viene cuadrado; en la app se ve redondo, así que aquí también.
            avatar?.let { AvatarRedondo.recorte(it, tamAvatar) } ?: AvatarInicial.bitmap(nombre, tamAvatar)
        )

        v.findViewById<ImageView>(R.id.card_photo).apply {
            if (foto != null) { setImageBitmap(foto); visibility = View.VISIBLE }
            else visibility = View.GONE
        }

        return dibujar(ctx, v)
    }

    /**
     * Tarjeta de un HILO. Distinta de [crear] a propósito: aquí manda el TÍTULO, porque es lo
     * que identifica a un hilo — el primer mensaje es solo cómo empezó. Copiando la tarjeta de
     * mensaje, un hilo que abre con tres párrafos daba una imagen larguísima con lo importante
     * en pequeño arriba. El extracto va acotado por `maxLines` en el layout, que es lo que
     * permite que aguanten igual de bien un hilo que abre con una frase y uno que abre con un
     * tocho.
     */
    fun crearHilo(
        ctx: Context,
        titulo: String,
        autor: String,
        fecha: String,
        avatar: Bitmap?,
        cuerpo: String,
        foto: Bitmap?
    ): Bitmap {
        val v = LayoutInflater.from(ctx).inflate(R.layout.card_share_thread, null)
        val nombre = autor.trim().removePrefix("@")

        v.findViewById<TextView>(R.id.card_title).text = titulo.trim()
        v.findViewById<TextView>(R.id.card_author).text = if (nombre.isEmpty()) "" else "@$nombre"
        v.findViewById<TextView>(R.id.card_date).apply {
            text = fecha.trim()
            visibility = if (fecha.isBlank()) View.GONE else View.VISIBLE
        }
        v.findViewById<TextView>(R.id.card_body).apply {
            text = cuerpo
            visibility = if (cuerpo.isBlank()) View.GONE else View.VISIBLE
        }

        val tamAvatar = (44 * ctx.resources.displayMetrics.density).toInt().coerceAtLeast(1)
        v.findViewById<ImageView>(R.id.card_avatar).setImageBitmap(
            avatar?.let { AvatarRedondo.recorte(it, tamAvatar) } ?: AvatarInicial.bitmap(nombre, tamAvatar)
        )
        v.findViewById<ImageView>(R.id.card_photo).apply {
            if (foto != null) { setImageBitmap(foto); visibility = View.VISIBLE }
            else visibility = View.GONE
        }
        return dibujar(ctx, v)
    }

    /** Mide, coloca y dibuja una tarjeta ya rellenada sobre su propio lienzo. */
    private fun dibujar(ctx: Context, v: View): Bitmap {
        // La vista no está en ninguna jerarquía, así que nadie va a medirla por nosotros.
        // Alto UNSPECIFIED = "lo que necesites"; los topes del texto y de la foto son los que
        // impiden que salga una tarjeta interminable.
        val ancho = (ANCHO_DP * ctx.resources.displayMetrics.density).toInt()
        v.measure(
            View.MeasureSpec.makeMeasureSpec(ancho, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )
        v.layout(0, 0, v.measuredWidth, v.measuredHeight)
        val bmp = Bitmap.createBitmap(
            v.measuredWidth.coerceAtLeast(1), v.measuredHeight.coerceAtLeast(1),
            Bitmap.Config.ARGB_8888
        )
        Canvas(bmp).apply {
            drawColor(Color.WHITE)   // el fondo del layout ya es blanco; esto cubre el borde
            v.draw(this)
        }
        return bmp
    }


    /**
     * Guarda la tarjeta y devuelve un `content://` que otras apps puedan leer.
     *
     * Android no deja compartir un `file://` a pelo desde hace años (FileUriExposedException):
     * hay que cederlo por el FileProvider declarado en el manifest, que da permiso temporal
     * solo a quien recibe el intent. Nombre fijo: no interesa ir dejando tarjetas en la caché.
     */
    fun guardar(ctx: Context, bmp: Bitmap): Uri? = try {
        val dir = File(ctx.cacheDir, "compartir").apply { mkdirs() }
        val f = File(dir, "mensaje.png")
        FileOutputStream(f).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", f)
    } catch (_: Throwable) {
        null
    }
}
