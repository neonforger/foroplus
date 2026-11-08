package com.fcplus.forocoches

import org.json.JSONObject

/** El interruptor de Música en `fc_config.json` → `"musica": {"servidor": true}`. Sin bloque, apagado. */
object ConfigMusica {
    fun servidorActivo(config: JSONObject?): Boolean = servidorActivo(config, ServidorMusica.BASE)

    /**
     * Con la dirección provisional (lleva "PENDIENTE") dice siempre que no, diga lo que diga la
     * config: así ninguna compilación puede llamar a un host que no existe. El parámetro [base]
     * existe para poder probar el caso "ya desplegado" sin tocar la constante.
     */
    internal fun servidorActivo(config: JSONObject?, base: String): Boolean {
        if (base.contains("PENDIENTE")) return false
        return config?.optJSONObject("musica")?.optBoolean("servidor", false) ?: false
    }
}
