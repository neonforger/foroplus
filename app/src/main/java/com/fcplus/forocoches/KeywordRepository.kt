package com.fcplus.forocoches

import android.content.Context

class KeywordRepository(context: Context) {

    private val prefs = context.getSharedPreferences("fc_keywords", Context.MODE_PRIVATE)

    companion object {
        val DEFAULT_KEYWORDS = setOf(
            "PP", "PSOE", "VOX", "Podemos", "Sumar", "Ciudadanos",
            "política", "político", "políticos", "elecciones", "gobierno",
            "Congreso", "Senado", "Sánchez", "Feijóo", "Abascal",
            "independencia", "Cataluña", "separatismo"
        )
    }

    fun isEnabled(): Boolean = prefs.getBoolean("enabled", true)

    /**
     * Deja fijado el valor de fábrica la primera vez, y **no es lo mismo para todos**.
     *
     * Miguel pidió que el filtro no venga encendido: filtrar política a quien no lo ha pedido es
     * decidir por él. Pero cambiar el valor por defecto a secas se lo quitaría también a los
     * cientos que YA tienen la app con el filtro puesto, de golpe y sin avisar — y para varios
     * de ellos es justo por lo que la instalaron. Así que:
     *  - **instalación nueva** → apagado (y ahí está Opciones para encenderlo).
     *  - **actualización** → encendido, que es lo que tenían.
     *
     * Solo actúa si nadie ha tocado el interruptor nunca: quien ya eligió, manda.
     */
    fun fijarValorDeFabrica(esInstalacionNueva: Boolean) {
        if (prefs.contains("enabled")) return
        prefs.edit().putBoolean("enabled", !esInstalacionNueva).apply()
    }

    /** Deja la lista vacía: el "quitar todas" que pedía Miguel, sin borrarlas una a una. */
    fun clearKeywords() {
        prefs.edit().putStringSet("keywords", emptySet()).apply()
    }

    fun setEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("enabled", enabled).apply()
    }

    fun getKeywords(): Set<String> {
        if (!prefs.contains("keywords")) return DEFAULT_KEYWORDS
        return prefs.getStringSet("keywords", DEFAULT_KEYWORDS) ?: DEFAULT_KEYWORDS
    }

    fun addKeyword(keyword: String) {
        val current = getKeywords().toMutableSet()
        current.add(keyword)
        prefs.edit().putStringSet("keywords", current).apply()
    }

    fun removeKeyword(keyword: String) {
        val current = getKeywords().toMutableSet()
        current.remove(keyword)
        prefs.edit().putStringSet("keywords", current).apply()
    }

    fun resetToDefaults() {
        prefs.edit().remove("keywords").apply()
    }
}
