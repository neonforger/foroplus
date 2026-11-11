package com.fcplus.forocoches

/**
 * La única dirección con la que habla Música. Va FIJA en el código a propósito: `fc_config.json`
 * solo enciende o apaga (ver [ConfigMusica]); si pudiera decir "habla con esta otra URL", quien
 * controlara la config (o un MITM del raw de GitHub) podría mandar los tokens a donde quisiera.
 */
object ServidorMusica {
    /** El Worker desplegado en la cuenta de Cloudflare de ForoPlus (tarea D1, 2026-11-09). */
    const val BASE = "https://foroplus-api.foroplus-musica.workers.dev"

    fun url(ruta: String): String = BASE + (if (ruta.startsWith("/")) ruta else "/$ruta")

    /** Solo `BASE + "/v1/..."`: el prefijo lleva la barra, así `BASE.evil.com` no cuela. */
    fun aceptada(url: String): Boolean = url.startsWith("$BASE/v1/")
}
