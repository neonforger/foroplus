package com.fcplus.forocoches

/**
 * Qué datos son **de la cuenta** y cuáles **del dispositivo**.
 *
 * El criterio: detrás de dos cuentas suele haber **una sola persona**. Si cambias de cuenta no
 * quieres que la app cambie de color ni se te reordenen las pestañas — eso es del móvil. Lo que
 * sí es de la cuenta son las marcas de leído, las citas y lo que FC ya guarda por cuenta.
 *
 * `fc_filtro` (el caché de los usuarios ignorados) **no es un detalle**: en FC los ignorados ya
 * van por cuenta, así que sin separarlo estarías filtrando con los de la otra, y nadie lo
 * relacionaría jamás con el cambio de cuenta.
 *
 * La firma va por cuenta por decisión del dueño: tiene sentido anunciarla en la cuenta principal
 * y no en otra.
 */
object ClavesPorCuenta {

    /** Ficheros de preferencias enteros que se separan por cuenta. */
    val FICHEROS = listOf("fc_leidos", "fc_filtro", "fc_notifications")

    /**
     * Claves sueltas de `shell_prefs` que se separan por cuenta.
     *
     * `mensajes_publicados` NO está aquí a propósito: pese al nombre, es el contador de la
     * tarjeta de valoración de Play (ver `MainActivity.tantearValoracion`), no "mensajes
     * tuyos" — la tarjeta es de la APP, no de la cuenta, así que compartirlo entre cuentas es
     * inofensivo y es lo correcto.
     */
    val CLAVES = listOf(
        "citas_vistas", "menciones_vistas", "vistas_quotes", "vistas_mentions",
        "busquedas_recientes", "post_signature"
    )

    /**
     * Con [uid] vacío devuelve el nombre de siempre: así la app funciona sin sesión, y es lo que
     * la migración lee para encontrar los datos de antes.
     */
    fun fichero(base: String, uid: String): String = if (uid.isEmpty()) base else "${base}_$uid"

    fun clave(base: String, uid: String): String = if (uid.isEmpty()) base else "${base}_$uid"
}
