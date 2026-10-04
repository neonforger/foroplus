package com.fcplus.forocoches

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import androidx.appcompat.widget.AppCompatImageView

/**
 * Imagen con pinza y doble toque para ampliar. Toda la aritmética vive en [ZoomMath]; aquí solo
 * se traducen los gestos a escala y desplazamiento, y se aplican con una `Matrix`.
 *
 * Se escribió a mano en vez de traer una biblioteca (PhotoView y compañía) porque son ~80 líneas
 * y este proyecto tiene muy pocas dependencias: una más para esto no sale a cuenta.
 */
class VisorImagenView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : AppCompatImageView(context, attrs) {

    /** Un toque simple cierra, como en cualquier galería. */
    var onCerrar: () -> Unit = {}

    private val matriz = Matrix()
    private var ajuste = 1f      // escala a la que la foto se ve entera
    private var escala = 1f
    private var despX = 0f
    private var despY = 0f

    private val escalador = ScaleGestureDetector(
        context,
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(d: ScaleGestureDetector): Boolean {
                escalarSobre(escala * d.scaleFactor, d.focusX, d.focusY)
                return true
            }
        }
    )

    private val gestos = GestureDetector(
        context,
        object : GestureDetector.SimpleOnGestureListener() {
            // Confirmado = no era la primera mitad de un doble toque.
            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                onCerrar(); return true
            }

            override fun onDoubleTap(e: MotionEvent): Boolean {
                escalarSobre(ZoomMath.escalaTrasDobleToque(escala, ajuste), e.x, e.y)
                return true
            }

            override fun onScroll(
                e1: MotionEvent?, e2: MotionEvent, distanciaX: Float, distanciaY: Float
            ): Boolean {
                despX -= distanciaX
                despY -= distanciaY
                aplicar()   // si la foto cabe entera, limitarDesplazamiento la devuelve al centro
                return true
            }
        }
    )

    init {
        scaleType = ScaleType.MATRIX
    }

    override fun setImageBitmap(bm: Bitmap?) {
        super.setImageBitmap(bm)
        // Foto nueva (o el reemplazo en alta resolución): se vuelve al encuadre inicial.
        encuadrar()
    }

    override fun onSizeChanged(w: Int, h: Int, anchoAnt: Int, altoAnt: Int) {
        super.onSizeChanged(w, h, anchoAnt, altoAnt)
        encuadrar()
    }

    /** Encaja la foto entera y centrada. Es el estado de partida y el suelo del zoom. */
    fun encuadrar() {
        val d = drawable ?: return
        ajuste = ZoomMath.escalaDeAjuste(d.intrinsicWidth, d.intrinsicHeight, width, height)
        escala = ajuste
        despX = 0f
        despY = 0f
        aplicar()
    }

    /** ¿Ampliada respecto al ajuste? (con margen: tras una pinza nunca cae en el valor exacto) */
    fun estaAmpliada(): Boolean = escala > ajuste * 1.01f

    private fun escalarSobre(nueva: Float, focoX: Float, focoY: Float) {
        val limitada = ZoomMath.limitarEscala(nueva, ajuste)
        if (limitada == escala) return
        val razon = limitada / escala
        // El punto que hay bajo los dedos se queda donde está: si no, la foto "huye" al ampliar.
        despX = focoX - (focoX - despX) * razon
        despY = focoY - (focoY - despY) * razon
        escala = limitada
        aplicar()
    }

    private fun aplicar() {
        val d = drawable ?: return
        despX = ZoomMath.limitarDesplazamiento(despX, d.intrinsicWidth * escala, width.toFloat())
        despY = ZoomMath.limitarDesplazamiento(despY, d.intrinsicHeight * escala, height.toFloat())
        matriz.reset()
        matriz.postScale(escala, escala)
        matriz.postTranslate(despX, despY)
        imageMatrix = matriz
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        escalador.onTouchEvent(event)
        gestos.onTouchEvent(event)
        return true
    }
}
