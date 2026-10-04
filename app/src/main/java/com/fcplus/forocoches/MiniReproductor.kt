package com.fcplus.forocoches

import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout

/**
 * Quien guarda el reproductor que el usuario está usando cuando su mensaje se va de pantalla.
 *
 * Existe como interfaz para que [PostAdapter] no tenga que saber nada de la ventanita: él solo
 * dice "guarda este" y "¿tienes el mío?".
 */
interface Aparcadero {

    /** Se queda [ev] vivo. Si ya tenía otro guardado, ese se destruye: solo cabe uno. */
    fun aparcar(ev: EmbedView)

    /** Devuelve el reproductor de [clave] si es el que tiene guardado, o null. */
    fun recuperar(clave: String): EmbedView?

    /** ¿Lo que tiene guardado es de este mensaje? Para saber si hay que devolverlo a su sitio. */
    fun tieneDe(pid: String): Boolean

    /** Cierra y destruye lo que hubiera. Al cambiar de página o salir del hilo. */
    fun soltar()
}

/**
 * El reproductor flotante: una ventanita abajo a la derecha del hilo con el vídeo que estabas
 * viendo, para que **siga sonando y viéndose** mientras sigues leyendo.
 *
 * ## Por qué existe
 *
 * Un tester reportó el 2026-08-22 que el vídeo se detiene en cuanto scrolleas y sale de
 * pantalla. La primera mitad del arreglo fue no DESTRUIR el reproductor al reciclar su mensaje
 * (antes se llamaba a `destroy()`, así que al volver empezaba de cero). Pero eso no bastaba, y
 * se midió: **guardarlo desenganchado de la ventana lo silencia igual**, porque Chromium pausa
 * el medio cuando el WebView deja de estar attached (`dumpsys audio`: `state:started` con el
 * mensaje a la vista, cero reproductores activos en cuanto se recicla).
 *
 * La ventanita es la solución a eso: el WebView sigue **enganchado y visible**, solo que en
 * otro sitio de la pantalla. Y es el MISMO WebView movido de contenedor, no uno nuevo — no se
 * recarga nada, así que no pierde ni el segundo por el que iba.
 *
 * ## Reglas
 *
 * - Solo entra el que el usuario ha TOCADO ([EmbedView.enUso]); los que solo has scrolleado se
 *   destruyen como siempre. Y solo cabe **uno**: en un hilo largo, guardar todos los tocados
 *   sería acumular WebViews sin techo.
 * - Se quita con la ✕, volviendo a su mensaje (se reengancha en su sitio) o saliendo del hilo.
 * - **El botón atrás NO lo cierra**, a propósito: la pila de navegación de esta app es delicada
 *   y meterle un caso más no compensa para algo que ya tiene su propio botón.
 */
class MiniReproductor(
    private val marco: View,
    private val hueco: FrameLayout,
    botonCerrar: View
) : Aparcadero {

    private var actual: EmbedView? = null

    init {
        botonCerrar.setOnClickListener { soltar() }
        marco.visibility = View.GONE
    }

    override fun aparcar(ev: EmbedView) {
        if (actual === ev) return
        soltar()
        // Se apunta ANTES de mudarlo. Y sale del PROPIO reproductor, no del sistema: cuando
        // esto se ejecuta el WebView ya está desenganchado y Chromium ya ha parado el audio,
        // así que preguntarle al AudioManager siempre decía "no" (medido en el dispositivo).
        val seguiaSonando = ev.reproduciendo
        (ev.parent as? ViewGroup)?.removeView(ev)
        // Dentro de la ventanita el reproductor ocupa el hueco entero: el alto que traía es el
        // que le pedía el mensaje, y aquí manda el marco.
        ev.flotando = true
        ev.layoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT
        )
        hueco.addView(ev)
        marco.visibility = View.VISIBLE
        actual = ev
        // Al frame siguiente: el WebView tiene que estar ya enganchado a la ventana para que
        // la orden de seguir tenga a quién llegar.
        // Al frame siguiente: el WebView tiene que estar ya enganchado a la ventana para que
        // la orden de seguir tenga a quién llegar.
        if (seguiaSonando) ev.post { ev.reanudar() }
    }

    override fun tieneDe(pid: String): Boolean =
        actual?.clave?.substringBefore('#') == pid

    override fun recuperar(clave: String): EmbedView? {
        val ev = actual ?: return null
        if (ev.clave != clave) return null
        val seguiaSonando = ev.reproduciendo
        hueco.removeView(ev)
        ev.flotando = false
        marco.visibility = View.GONE
        actual = null
        // La vuelta a su sitio en el mensaje desengancha igual que la ida: mismo remedio.
        if (seguiaSonando) ev.post { ev.reanudar() }
        return ev
    }

    override fun soltar() {
        actual?.let {
            hueco.removeView(it)
            it.release()
        }
        actual = null
        marco.visibility = View.GONE
    }
}
