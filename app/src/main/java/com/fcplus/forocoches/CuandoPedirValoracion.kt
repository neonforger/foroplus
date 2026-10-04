package com.fcplus.forocoches

/**
 * Cuándo enseñar la tarjeta de valoración.
 *
 * Google ya limita por su cuenta cuántas veces puede aparecer, pero esa cuota no se ve desde
 * aquí: si se llama a lo loco, unas veces sale y otras no y no hay forma de saberlo. Así que la
 * decisión de **cuándo pedirla** la toma la app, y es esta.
 *
 * Dos condiciones, y las dos tienen motivo:
 * - **Haber publicado ya unas cuantas veces.** A quien acaba de instalar la app no se le pide
 *   que la valore: no la conoce, y una valoración así no vale para nada. Se cuenta por
 *   mensajes publicados y no por días ni aperturas porque es lo único que demuestra que la
 *   persona la está usando de verdad para lo que es.
 * - **Una sola vez por instalación.** Ver [INTENTOS_MAXIMOS]: Google no garantiza que no se
 *   la enseñe a quien ya valoró, así que se pide lo mínimo.
 *
 * Y el momento: **justo después de publicar con éxito**. La persona acaba de hacer algo que le
 * ha salido bien y no se le interrumpe la lectura. Lo que NO se hace nunca es preguntar antes
 * si le gusta la app para enseñarla solo a los contentos: eso es filtrar reseñas y Play lo
 * castiga con la retirada.
 */
object CuandoPedirValoracion {

    /**
     * Mensajes publicados antes de pedir nada. Quince, no tres: con quince mensajes ya sabes
     * si la app te gusta, y una valoración de alguien que la usó tres veces no vale gran cosa.
     */
    const val MENSAJES_MINIMOS = 15

    /**
     * **UNA sola vez por instalación**, y la decisión es del dueño con motivo.
     *
     * Comprobado en la documentación de Google (2026-09-12): la API **no dice** si el usuario
     * valoró, ni si la tarjeta llegó a enseñarse, y **en ningún sitio promete** que no se le
     * vuelva a mostrar a quien ya valoró — solo limita por "haberla visto hace poco", con una
     * cuota que es "un detalle de implementación y puede cambiar sin avisar".
     *
     * O sea: la posibilidad de molestar a alguien que ya valoró **no se puede descartar**, solo
     * reducir pidiéndola menos. Pedirla una vez es el lado seguro: el coste de que alguien no
     * la vea es invisible; el de dar la lata a quien ya valoró se nota y es tu marca.
     */
    const val INTENTOS_MAXIMOS = 1

    /**
     * @param intentos veces que el flujo **llegó a completarse**. Se cuenta al completarse y no
     *   al lanzarse: si falla por no haber red, no se gasta el único intento. Ojo, completarse
     *   **no** significa que se haya enseñado (medido, y documentado por Google).
     */
    fun toca(mensajesPublicados: Int, intentos: Int): Boolean =
        intentos < INTENTOS_MAXIMOS && mensajesPublicados >= MENSAJES_MINIMOS
}
