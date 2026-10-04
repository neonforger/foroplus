package com.fcplus.forocoches

import kotlin.math.max
import kotlin.math.min

/**
 * Reglas del zoom del visor de imágenes. Sin Android: son tres decisiones con esquinas sutiles
 * (qué escala deja la foto entera a la vista, hasta dónde se deja ampliar, y hasta dónde se
 * puede arrastrar sin que aparezcan huecos negros) y así se prueban sin encender un móvil.
 */
object ZoomMath {

    /** Cuánto se deja ampliar sobre el tamaño de ajuste. 4x cubre leer un texto en una captura. */
    const val MAX_FACTOR = 4f

    /** Escala del doble toque. Ni tanto que te pierdas, ni tan poco que no valga la pena. */
    const val FACTOR_DOBLE_TOQUE = 2.5f

    /**
     * Escala que hace que la imagen quepa ENTERA en la vista, respetando su proporción.
     * Es el punto de partida del visor y el suelo del zoom: por debajo no se baja, o la foto
     * quedaría flotando más pequeña de lo necesario.
     */
    fun escalaDeAjuste(anchoImg: Int, altoImg: Int, anchoVista: Int, altoVista: Int): Float {
        if (anchoImg <= 0 || altoImg <= 0 || anchoVista <= 0 || altoVista <= 0) return 1f
        return min(anchoVista.toFloat() / anchoImg, altoVista.toFloat() / altoImg)
    }

    /** Limita la escala entre el ajuste y su múltiplo máximo. */
    fun limitarEscala(escala: Float, ajuste: Float, maxFactor: Float = MAX_FACTOR): Float =
        escala.coerceIn(ajuste, ajuste * maxFactor)

    /**
     * Desplazamiento válido en UN eje.
     *
     * Si el contenido cabe en la vista se **centra** (y no se deja arrastrar: mover una foto
     * que ya se ve entera solo la despega del centro sin ganar nada). Si no cabe, se limita
     * para que ningún borde se despegue y deje una franja vacía.
     */
    fun limitarDesplazamiento(desplazamiento: Float, tamContenido: Float, tamVista: Float): Float {
        if (tamContenido <= tamVista) return (tamVista - tamContenido) / 2f
        return desplazamiento.coerceIn(tamVista - tamContenido, 0f)
    }

    /**
     * Escala a la que salta el doble toque: si ya estás ampliado, vuelve al ajuste; si no,
     * amplía. Un doble toque que solo ampliara obligaría a hacer pinza para volver.
     */
    fun escalaTrasDobleToque(escalaActual: Float, ajuste: Float): Float {
        val ampliada = limitarEscala(ajuste * FACTOR_DOBLE_TOQUE, ajuste)
        // Margen del 1%: tras una pinza la escala nunca cae EXACTAMENTE en el ajuste.
        return if (escalaActual > ajuste * 1.01f) ajuste else ampliada
    }

    /**
     * Reduce el lado máximo al que decodificar para el visor. La lista usa 1600 px porque las
     * fotos se ven a ~840; ampliando eso se ve papilla, así que el visor decodifica más grande.
     * No se sube sin freno: una foto de 4000x3000 a resolución completa son ~48 MB de bitmap.
     */
    fun ladoMaximoVisor(anchoPantalla: Int): Int = max(1600, min(anchoPantalla * 3, 2560))
}
