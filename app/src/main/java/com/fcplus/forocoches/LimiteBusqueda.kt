package com.fcplus.forocoches

/**
 * El freno de Cloudflare a `search.php` (medido el 2026-11-17: 429 con `Retry-After: 96`).
 * El motor no insiste mientras dura (ver `fetchBusqueda` en extractor.js) y entrega el motivo
 * como `ratelimit:<segundos que faltan>`. Aquí solo se decide qué se le dice a quien lo alcanza:
 * el tope no se anuncia de antemano, se explica cuando toca.
 */
object LimiteBusqueda {

    private const val PREFIJO = "ratelimit:"

    /** Segundos que faltan, o null si el motivo no es este freno. */
    fun segundos(motivo: String): Int? {
        if (!motivo.startsWith(PREFIJO)) return null
        return (motivo.removePrefix(PREFIJO).toIntOrNull() ?: 60).coerceAtLeast(1)
    }

    fun mensaje(motivo: String): String? {
        val s = segundos(motivo) ?: return null
        return "ForoCoches ha frenado las búsquedas un momento por hacer muchas seguidas. " +
            "Vuelve a probar ${cuando(s)}."
    }

    fun cuando(s: Int): String = when {
        s < 10 -> "en unos segundos"
        s < 60 -> "en $s segundos"
        s < 90 -> "en un minuto"
        else -> "en ${(s + 59) / 60} minutos"
    }
}
