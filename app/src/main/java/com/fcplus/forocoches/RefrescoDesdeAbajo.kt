package com.fcplus.forocoches

import android.animation.ValueAnimator
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.core.view.OneShotPreDrawListener
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.CircularProgressDrawable
import kotlin.math.roundToInt

/**
 * "Tirar para actualizar" desde abajo: el gesto de arriba de cualquier app, pero al final de la
 * última página de un hilo (dueño, 2026-11-17: "como en Instagram, suave y limpio").
 *
 * Al llegar al fondo no pasa nada. Si el dedo SIGUE subiendo, el gesto es nuestro: la lista sube
 * con resistencia ([TirarDesdeAbajo.desplazamiento]) y en el hueco que deja debajo nace la rueda
 * (la misma CircularProgressDrawable del SwipeRefreshLayout de arriba), que se va dibujando con el
 * dedo. Soltando pasado el umbral se queda girando y se recarga; antes, todo vuelve a su sitio.
 * Cuando llega la respuesta, lo nuevo entra justo donde estaba la rueda.
 *
 * Por qué así y no de otra forma (probado y descartado el mismo día):
 * - Rueda flotante pegada al borde: quedaba medio tapada por la barra de responder, que va
 *   superpuesta. Aquí la rueda va en el hueco entre el último mensaje y la barra.
 * - Observar el gesto sin quedárselo: la lista rebotaba por su cuenta y no se podía dar al dedo
 *   un recorrido suave. Aquí se lo queda el OnItemTouchListener (la lista no llega a moverse).
 * - Las coordenadas van en `rawY` (pantalla): la lista se mueve bajo el dedo, y en las suyas el
 *   dedo "volvería" según sube ella.
 *
 * @param contenedor donde se pinta la rueda (el que envuelve la lista, debajo de las barras).
 * @param puedeEmpezar si el gesto vale ahora (última página, nada cargando…).
 * @param alTirar se soltó pasado el umbral: hay que recargar y luego llamar a [terminar].
 */
class RefrescoDesdeAbajo(
    private val lista: RecyclerView,
    private val contenedor: FrameLayout,
    private val puedeEmpezar: () -> Boolean,
    private val alTirar: () -> Unit
) {
    private val d = lista.resources.displayMetrics.density
    private val max = TirarDesdeAbajo.MAX_DP * d
    private val umbral = TirarDesdeAbajo.UMBRAL_DP * d
    private val slop = ViewConfiguration.get(lista.context).scaledTouchSlop
    private val tamano = (40 * d).roundToInt()

    private val rueda = CircularProgressDrawable(lista.context).apply {
        setStyle(CircularProgressDrawable.DEFAULT)
        setColorSchemeColors(color(androidx.appcompat.R.attr.colorPrimary))
        arrowEnabled = true
    }
    private val indicador = ImageView(lista.context).apply {
        setImageDrawable(rueda)
        val p = (8 * d).roundToInt()
        setPadding(p, p, p, p)
        background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(color(com.google.android.material.R.attr.colorSurface))
        }
        elevation = 4 * d
        contentDescription = "Actualizar"
        visibility = View.GONE
    }

    /** El dedo se apoyó con todo listo para tirar; aún no se sabe si va a tirar. */
    private var armado = false
    /** El gesto ya es nuestro: la lista sigue al dedo. */
    private var arrastrando = false
    private var apoyoY = 0f
    private var inicioY = 0f
    private var apoyoT = 0L
    /** Lo que ha subido la lista, en píxeles. */
    private var desplazado = 0f
    private var cargando = false
    private var animador: ValueAnimator? = null
    private var ultimoMovimiento = 0L
    /** Por si el motor no contesta nunca: la rueda no se puede quedar girando para siempre. */
    private val seguro = Runnable { terminar() }

    init {
        contenedor.addView(indicador, FrameLayout.LayoutParams(tamano, tamano, Gravity.TOP or Gravity.CENTER_HORIZONTAL))
        // Solo se apunta la hora: desde onScrolled no se toca ninguna vista (gotcha de Android).
        lista.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(rv: RecyclerView, dx: Int, dy: Int) {
                if (dy != 0) ultimoMovimiento = android.os.SystemClock.uptimeMillis()
            }
        })
        lista.addOnItemTouchListener(object : RecyclerView.OnItemTouchListener {
            override fun onInterceptTouchEvent(rv: RecyclerView, e: MotionEvent) = interceptar(e)
            override fun onTouchEvent(rv: RecyclerView, e: MotionEvent) = tocar(e)
            override fun onRequestDisallowInterceptTouchEvent(disallowIntercept: Boolean) {
                // NO se desarma: el texto de cada mensaje pide el gesto al apoyar el dedo (para la
                // pulsación larga) y lo devuelve en cuanto el dedo se arrastra. Desarmando aquí, el
                // tirón solo funcionaba apoyando el dedo ENTRE mensajes (medido en el Samsung). Un
                // hijo que se lo quede de verdad (un reproductor) no nos deja ver más eventos.
            }
        })
    }

    private fun interceptar(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                arrastrando = false
                apoyoY = e.rawY
                apoyoT = e.eventTime
                armado = !cargando && !lista.canScrollVertically(1) && puedeEmpezar() &&
                    TirarDesdeAbajo.puedeArmar(android.os.SystemClock.uptimeMillis(), ultimoMovimiento)
            }
            MotionEvent.ACTION_MOVE -> {
                if (!armado) return false
                val sube = apoyoY - e.rawY
                if (sube < -slop) { armado = false; return false }   // baja: desplazamiento normal
                if (sube > slop && !lista.canScrollVertically(1)) {
                    arrastrando = true
                    inicioY = e.rawY
                    animador?.cancel()
                    lista.parent?.requestDisallowInterceptTouchEvent(true)
                    return true
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> armado = false
        }
        return false
    }

    private fun tocar(e: MotionEvent) {
        if (!arrastrando) return
        when (e.actionMasked) {
            MotionEvent.ACTION_MOVE -> {
                desplazado = TirarDesdeAbajo.desplazamiento((inicioY - e.rawY) / d, TirarDesdeAbajo.MAX_DP) * d
                pintar()
            }
            MotionEvent.ACTION_UP -> soltar(e.eventTime - apoyoT)
            MotionEvent.ACTION_CANCEL -> soltar(0L)
        }
    }

    private fun soltar(duracionMs: Long) {
        arrastrando = false
        armado = false
        if (TirarDesdeAbajo.recarga(desplazado, umbral, duracionMs)) {
            cargando = true
            rueda.arrowEnabled = false
            rueda.setStartEndTrim(0f, 0.8f)
            rueda.alpha = 255
            rueda.start()
            animarA(umbral)
            lista.removeCallbacks(seguro)
            lista.postDelayed(seguro, 20_000)
            alTirar()
        } else {
            animarA(0f)
        }
    }

    /**
     * La recarga acabó (bien o mal; quien recarga ya ha añadido lo nuevo a la lista). Se espera
     * al siguiente dibujo, cuando lo nuevo ya está colocado, para saber si hay algo debajo.
     */
    fun terminar() {
        if (!cargando) return
        cargando = false
        lista.removeCallbacks(seguro)
        OneShotPreDrawListener.add(lista) { cerrar() }
        lista.invalidate()
    }

    private fun cerrar() {
        rueda.stop()
        rueda.arrowEnabled = true
        if (desplazado > 0f && lista.canScrollVertically(1)) {
            // Llegó algo. "Lista subida" se cambia por "lista desplazada" en el mismo dibujo (se ve
            // exactamente igual) y lo nuevo queda justo donde estaba la rueda, que se apaga.
            // Con scrollToPositionWithOffset y NO con scrollBy: scrollBy avisa a las barras como si
            // desplazara el dedo (nested scroll) y la de responder se escondía de golpe.
            val off = desplazado.roundToInt()
            animador?.cancel()
            desplazado = 0f
            val lm = lista.layoutManager as? LinearLayoutManager
            val primera = lm?.findFirstVisibleItemPosition() ?: RecyclerView.NO_POSITION
            val vista = if (primera != RecyclerView.NO_POSITION) lm?.findViewByPosition(primera) else null
            lista.translationY = 0f
            if (lm != null && vista != null) {
                lm.scrollToPositionWithOffset(primera, vista.top - off - lista.paddingTop)
            } else {
                lista.scrollBy(0, off)
            }
            indicador.animate().alpha(0f).scaleX(0.3f).scaleY(0.3f).setDuration(200)
                .withEndAction { indicador.visibility = View.GONE }.start()
        } else {
            animarA(0f)
        }
    }

    /** Todo a cero sin animar: al cambiar de página o de hilo. */
    fun reiniciar() {
        animador?.cancel()
        lista.removeCallbacks(seguro)
        cargando = false
        arrastrando = false
        armado = false
        rueda.stop()
        rueda.arrowEnabled = true
        desplazado = 0f
        lista.translationY = 0f
        indicador.animate().cancel()
        indicador.visibility = View.GONE
    }

    private fun animarA(destino: Float) {
        animador?.cancel()
        animador = ValueAnimator.ofFloat(desplazado, destino).apply {
            duration = 220
            interpolator = DecelerateInterpolator()
            addUpdateListener { desplazado = it.animatedValue as Float; pintar() }
            start()
        }
    }

    private fun pintar() {
        lista.translationY = -desplazado
        if (desplazado <= 0.5f) {
            indicador.visibility = View.GONE
            return
        }
        indicador.animate().cancel()
        indicador.visibility = View.VISIBLE
        val p = TirarDesdeAbajo.progreso(desplazado, umbral)
        // En medio del hueco que deja la lista entre su último mensaje y la barra de abajo.
        indicador.translationY = bordeContenido() - desplazado / 2f - tamano / 2f
        val escala = 0.4f + 0.6f * p
        indicador.scaleX = escala
        indicador.scaleY = escala
        indicador.alpha = p
        if (!cargando) {
            // Como la de arriba: la flecha se va cerrando y girando con el dedo.
            rueda.setStartEndTrim(0f, 0.8f * p)
            rueda.arrowScale = p
            rueda.progressRotation = (-0.25f + 0.4f * p) * 0.5f
            rueda.alpha = (76 + 179 * p).roundToInt()
        }
    }

    /**
     * Dónde acaba el último mensaje SIN contar lo que ha subido la lista, en coordenadas del
     * contenedor. Por encima de la barra de abajo: la lista le deja su hueco con el padding.
     */
    private fun bordeContenido(): Float {
        var arriba = lista.top
        var v = lista.parent as? View
        while (v != null && v !== contenedor) {
            arriba += v.top
            v = v.parent as? View
        }
        val n = lista.adapter?.itemCount ?: 0
        val ultimo = lista.layoutManager?.findViewByPosition(n - 1)
        val abajo = ultimo?.bottom ?: (lista.height - lista.paddingBottom)
        return (arriba + abajo).toFloat()
    }

    private fun color(attr: Int): Int {
        val tv = TypedValue()
        lista.context.theme.resolveAttribute(attr, tv, true)
        return tv.data
    }
}
