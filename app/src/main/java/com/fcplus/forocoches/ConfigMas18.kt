package com.fcplus.forocoches

import org.json.JSONObject

/** El bloque `mas18` de fc_config.json, ya validado. Si existe, la sección existe. */
data class ConfigMas18(val base: String) {
    fun urlPagina(n: Int): String = (if (base.endsWith("/")) base else "$base/") + "$n.json"
}

object ConfigMas18Parser {
    /** null = la sección no existe: sin bloque, apagada desde el servidor o con una URL no permitida. */
    fun de(config: JSONObject?): ConfigMas18? {
        val b = config?.optJSONObject("mas18") ?: return null
        if (!b.optBoolean("activo", false)) return null
        val url = b.optString("url").trim()
        val base = if (url.endsWith("/")) url else "$url/"
        if (!FuentesPermitidas.aceptada(base)) return null
        return ConfigMas18(base)
    }
}
