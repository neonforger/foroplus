package com.fcplus.forocoches

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlin.math.roundToInt

/**
 * Aritmética de la barra de desplazamiento. Vive aparte de la vista para poder probarla.
 */
object PosicionBarra {

    /** Largo del pulgar, constante. Mismo criterio que [ScrollThumb]: el porqué, allí. */
    const val PULGAR_DP = 52f

    /** Aire arriba y abajo para que el pulgar no llegue a tocar los bordes. */
    const val MARGEN_DP = 6f

    /** Recorrido que le queda al pulgar dentro de la barra. */
    fun recorrido(altoBarra: Int, altoPulgar: Int, margen: Int): Int =
        (altoBarra - 2 * margen - altoPulgar).coerceAtLeast(0)

    /** Dónde empieza el pulgar para una fracción recorrida. */
    fun arriba(fraccion: Float, altoBarra: Int, altoPulgar: Int, margen: Int): Int =
        margen + (fraccion.coerceIn(0f, 1f) * recorrido(altoBarra, altoPulgar, margen)).roundToInt()

    /**
     * Fracción a la que corresponde poner el **centro** del pulgar en [y].
     *
     * Se usa el centro y no el borde de arriba para que el pulgar no pegue un salto bajo el
     * dedo en cuanto lo agarras por la parte de abajo.
     */
    fun fraccionDe(y: Float, altoBarra: Int, altoPulgar: Int, margen: Int): Float {
        val recorrido = recorrido(altoBarra, altoPulgar, margen)
        if (recorrido <= 0) return 0f
        return ((y - margen - altoPulgar / 2f) / recorrido).coerceIn(0f, 1f)
    }

    /** Mensaje al que llevar la lista para una fracción dada. */
    fun indice(fraccion: Float, total: Int): Int {
        if (total <= 1) return 0
        return (fraccion.coerceIn(0f, 1f) * (total - 1)).roundToInt().coerceIn(0, total - 1)
    }

    /** ¿Cae [y] sobre el pulgar, con algo de holgura para el dedo? */
    fun tocaElPulgar(y: Float, arribaPulgar: Int, altoPulgar: Int, holgura: Int): Boolean =
        y >= arribaPulgar - holgura && y <= arribaPulgar + altoPulgar + holgura
}

/**
 * Barra de desplazamiento del hilo, **que ahora sí se puede arrastrar**.
 *
 * Un tester se quejó el 2026-08-24 de que la barra "no funciona" en hilos donde la gente cita
 * mensajes enormes, y hay que recorrerse la página entera a dedo. Tenía razón, aunque la causa
 * no es un fallo sino un malentendido: lo que había era `android:scrollbars="vertical"`, que en
 * Android es **solo un indicador**. Esa barra no tiene gestión táctil de ninguna clase; no es
 * que estuviera rota, es que nunca se pudo tocar.
 *
 * La alternativa de fábrica era el `FastScroller` del `RecyclerView`, y **aquí no vale**:
 * calcula cuánto desplazar en las unidades que devuelve `computeVerticalScrollRange()` y se lo
 * pasa a `scrollBy()` como si fueran píxeles. Con [FixedThumbLayoutManager] ese rango es un
 * número inventado y constante (10.000) — que es justo lo que consigue que el pulgar no cambie
 * de tamaño mientras cargan las fotos —, así que el arrastre saldría disparatado. O pulgar
 * fijo, o FastScroller; y el pulgar fijo se arregló en su día por un motivo.
 *
 * Por eso esta barra:
 *   · el pulgar mide siempre lo mismo, como hasta ahora;
 *   · se puede agarrar y arrastrar;
 *   · **solo se queda el gesto si empieza sobre el pulgar**. Si no, devuelve el toque al
 *     mensaje de debajo: una franja invisible que se tragara los toques del borde derecho
 *     rompería los enlaces y las fotos que caigan por ahí.
 */
class BarraDesplazamiento @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    private val pintura = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()
    private var lista: RecyclerView? = null
    private var arrastrando = false

    /** Fracción bajo el dedo mientras se arrastra: el pulgar sigue al dedo, no a la lista. */
    private var fraccionArrastre = 0f

    private val altoPulgar = dp(PosicionBarra.PULGAR_DP)
    private val margen = dp(PosicionBarra.MARGEN_DP)
    private val holgura = dp(12f)
    private val anchoPulgar = dp(4f)
    private val radio = anchoPulgar / 2f

    private fun dp(v: Float) = (v * resources.displayMetrics.density).toInt()

    private val alDesplazar = object : RecyclerView.OnScrollListener() {
        override fun onScrolled(rv: RecyclerView, dx: Int, dy: Int) {
            // Aquí solo se INVALIDA, que es leer. Escribir en vistas desde onScrolled es lo
            // que dejó la barra de páginas medida a 0x0 en su día (ver gotchas de la guía).
            if (!arrastrando) invalidate()
        }
    }

    fun enlazar(rv: RecyclerView) {
        lista?.removeOnScrollListener(alDesplazar)
        lista = rv
        rv.addOnScrollListener(alDesplazar)
        invalidate()
    }

    /** Fracción recorrida de la página: 0 arriba del todo, 1 abajo del todo. */
    private fun fraccion(): Float {
        val rv = lista ?: return 0f
        val recorrible = rv.computeVerticalScrollRange() - rv.computeVerticalScrollExtent()
        if (recorrible <= 0) return 0f
        return (rv.computeVerticalScrollOffset().toFloat() / recorrible).coerceIn(0f, 1f)
    }

    /** ¿Hay algo que recorrer? Con todo el contenido en pantalla la barra sobra. */
    private fun haceFalta(): Boolean {
        val rv = lista ?: return false
        if ((rv.adapter?.itemCount ?: 0) <= 1) return false
        return rv.computeVerticalScrollRange() > rv.computeVerticalScrollExtent()
    }

    private fun arribaDelPulgar(): Int = PosicionBarra.arriba(
        if (arrastrando) fraccionArrastre else fraccion(), height, altoPulgar, margen
    )

    override fun onDraw(canvas: Canvas) {
        if (!haceFalta()) return
        val arriba = arribaDelPulgar()
        val x = (width - anchoPulgar) / 2f
        rect.set(x, arriba.toFloat(), x + anchoPulgar, (arriba + altoPulgar).toFloat())
        pintura.color = ContextCompat.getColor(context, R.color.fc_scroll_pulgar)
        canvas.drawRoundRect(rect, radio, radio, pintura)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val rv = lista ?: return false
        if (!haceFalta()) return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                // Fuera del pulgar el toque NO es nuestro: se devuelve al mensaje de debajo.
                if (!PosicionBarra.tocaElPulgar(event.y, arribaDelPulgar(), altoPulgar, holgura)) {
                    return false
                }
                arrastrando = true
                fraccionArrastre = fraccion()
                // Sin esto, el RecyclerView (y el SwipeRefreshLayout que lo envuelve) se
                // llevan el gesto en cuanto el dedo se mueve un poco.
                parent?.requestDisallowInterceptTouchEvent(true)
                invalidate()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (!arrastrando) return false
                fraccionArrastre = PosicionBarra.fraccionDe(event.y, height, altoPulgar, margen)
                llevarLista(rv, fraccionArrastre)
                invalidate()
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (!arrastrando) return false
                arrastrando = false
                parent?.requestDisallowInterceptTouchEvent(false)
                performClick()
                invalidate()
                return true
            }
        }
        return false
    }

    /**
     * Lleva la lista a la fracción pedida.
     *
     * Se salta POR MENSAJE (`scrollToPositionWithOffset`) y no por píxeles: los mensajes de un
     * hilo miden cosas muy distintas —dos líneas, o una foto a pantalla completa— y el alto
     * total es una estimación que además cambia según van cargando las imágenes. Saltar al
     * mensaje número N es exacto y no depende de eso.
     */
    private fun llevarLista(rv: RecyclerView, fraccion: Float) {
        val lm = rv.layoutManager as? LinearLayoutManager ?: return
        val total = rv.adapter?.itemCount ?: return
        lm.scrollToPositionWithOffset(PosicionBarra.indice(fraccion, total), 0)
    }
}
