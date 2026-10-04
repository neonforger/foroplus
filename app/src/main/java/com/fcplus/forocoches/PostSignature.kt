package com.fcplus.forocoches

/**
 * Firma de la app al final de lo que se publica ("Enviado desde ForoPlus").
 *
 * El usuario NO la ve en el editor: se añade justo al enviar. Es OPCIONAL desde Opciones
 * (pidieron los testers poder quitarla), por defecto activada para no cambiarle el mensaje
 * a nadie sin avisar.
 *
 * Función pura a propósito: así se puede probar el montaje del mensaje sin publicar nada
 * real en la cuenta del usuario.
 */
object PostSignature {

    const val TEXT = "[SIZE=1]Enviado desde ForoPlus[/SIZE]"

    /** Aire por encima para que no quede pegada al último párrafo. */
    private const val GAP = "\n\n\n"

    /**
     * Devuelve el mensaje listo para enviar. Con la firma desactivada devuelve el cuerpo
     * TAL CUAL — ni el hueco en blanco, que dejaría saltos de línea sueltos al final.
     */
    fun append(body: String, enabled: Boolean): String =
        if (enabled) body + GAP + TEXT else body
}
