package com.fcplus.forocoches

/**
 * inSampleSize (potencia de 2) para que ninguna dimensión supere [maxDim] al decodificar.
 * Función pura, sin dependencias de Android → testeable en la JVM.
 */
fun sampleSizeFor(width: Int, height: Int, maxDim: Int): Int {
    if (width <= 0 || height <= 0 || maxDim <= 0) return 1
    var sample = 1
    while (width / sample > maxDim || height / sample > maxDim) sample *= 2
    return sample
}

/**
 * Cómo decodificar una imagen para que ocupe en memoria lo que se va a DIBUJAR y ni un byte más.
 *
 * `inSampleSize` solo sabe dividir por potencias de 2, y eso aquí no vale: las fotos del foro
 * rondan los 1263 px y se pintan a ~842, así que el salto de 2 las dejaría en 631 y habría que
 * estirarlas (borrosas). El decodificador sabe además escalar fino con `inDensity` /
 * `inTargetDensity`, así que se combinan: el muestreo hace el desbaste **sin bajar nunca del
 * objetivo** y el escalado fino clava el tamaño exacto.
 *
 * Medido el 2026-09-11 en `t=10736632`: sin esto, una foto de 1263x627 ocupa 3,17 MB (ARGB_8888)
 * para dibujarse a 842 px. Veinte fotos así no caben en la caché (64 MB en el Samsung) y el
 * resultado era **1.668 expulsiones en un minuto** y 2.447 descargas para 37 imágenes distintas.
 */
data class Escalado(val muestreo: Int, val desde: Int, val hasta: Int) {
    /** Hay que rematar con el escalado fino del decodificador. */
    val ajusteFino: Boolean get() = desde > hasta
}

fun escaladoPara(ancho: Int, alto: Int, ladoMax: Int): Escalado {
    if (ancho <= 0 || alto <= 0 || ladoMax <= 0) return Escalado(1, 0, 0)
    val mayor = maxOf(ancho, alto)
    // Ya cabe: ni muestreo ni escalado. Los smileys (25x18) pasan por aquí intactos, que es
    // justo lo que debe pasar (ver ImagenEnTexto: encogerlos los devolvería a "diminutos").
    if (mayor <= ladoMax) return Escalado(1, mayor, mayor)
    var muestreo = 1
    // OJO al `/ 2`: se para ANTES de cruzar el objetivo, nunca por debajo.
    while (mayor / (muestreo * 2) >= ladoMax) muestreo *= 2
    return Escalado(muestreo, mayor / muestreo, ladoMax)
}
