package com.fcplus.forocoches

import android.app.Activity
import com.google.android.play.core.review.ReviewManagerFactory

/**
 * Valorar la app **sin salir de la app**.
 *
 * Detrás de una interfaz por el mismo motivo que [Actualizador]: hoy la tarjeta la pinta Google
 * Play, pero si algún día se distribuye fuera, cambia la implementación y no quien la llama.
 *
 * **Lo que hay que saber antes de tocar esto**, porque no se comporta como cualquier diálogo:
 * - **No controlas si aparece.** Google impone una cuota por usuario: si no toca, la llamada no
 *   hace absolutamente nada. Ni tarjeta, ni error.
 * - **No se sabe si ha valorado**, ni si llegó a verla. El final del flujo se cumple igual en
 *   los tres casos, así que no se puede premiar ni contar nada.
 * - **No se puede preguntar antes** ("¿te gusta la app?") ni enseñarla solo a quien contesta
 *   que sí: eso es filtrar reseñas y es motivo de retirada de Play.
 * - **No se puede probar sin publicar**: solo funciona instalada desde Play.
 *
 * No hay entrada en Opciones a propósito: el panel ya tiene bastante, y una fila más que casi
 * nadie va a tocar no compensa (decisión del dueño, 2026-09-12).
 */
interface Valoracion {

    /**
     * Intenta enseñar la tarjeta. Silencioso a propósito: si Google no la da, el usuario no
     * tiene por qué enterarse de que lo hemos intentado.
     *
     * @param alCompletar se invoca **solo si el flujo llegó a completarse**. Es lo que permite
     *   pedirla UNA vez en la vida sin arriesgarse a quemar ese único intento: si en ese
     *   momento no había red o Play no la dio, no se marca nada y habrá otra oportunidad.
     *   OJO: completarse **no** significa que haya valorado — eso no se puede saber.
     */
    fun pedir(activity: Activity, alCompletar: () -> Unit)
}

class ValoracionPlay : Valoracion {

    override fun pedir(activity: Activity, alCompletar: () -> Unit) {
        try {
            val manager = ReviewManagerFactory.create(activity)
            manager.requestReviewFlow().addOnCompleteListener { tarea ->
                // Si no hay flujo (sin Play, sin conexión, cuota agotada) no se insiste — pero
                // tampoco se da por pedida: una tarjeta de valoración no merece ni un reintento
                // ni un mensaje de error, y quemar el único intento por no tener red sí dol
                if (!tarea.isSuccessful) return@addOnCompleteListener
                try {
                    manager.launchReviewFlow(activity, tarea.result)
                        .addOnCompleteListener { alCompletar() }
                } catch (_: Throwable) {
                }
            }
        } catch (_: Throwable) {
        }
    }

}
