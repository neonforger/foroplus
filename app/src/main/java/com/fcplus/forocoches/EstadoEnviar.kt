package com.fcplus.forocoches

/**
 * ¿Hay algo que enviar? Pinta el botón de enviar "listo" (rojo) o apagado (gris), en la barra
 * rápida y en el editor (fase 2 del rediseño). Con citas pendientes SÍ hay algo: la barra
 * rápida las lleva al editor completo y el editor las publica.
 *
 * Es solo el ASPECTO: el botón sigue respondiendo siempre (si no hay nada, avisa). Un estado
 * desactivado que se quedara rancio bloquearía un envío, y las citas cambian desde muchos sitios.
 */
object EstadoEnviar {
    fun listo(texto: CharSequence, citasPendientes: Int): Boolean =
        texto.isNotBlank() || citasPendientes > 0
}
