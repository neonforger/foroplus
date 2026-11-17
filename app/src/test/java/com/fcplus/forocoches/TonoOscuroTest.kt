package com.fcplus.forocoches

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TonoOscuroTest {

    /** SharedPreferences de mentira: solo hace falta guardar y leer una cadena. */
    private class Prefs(private val mapa: HashMap<String, String?> = HashMap()) :
        android.content.SharedPreferences {
        override fun getString(key: String, defValue: String?) = mapa.getOrDefault(key, defValue)
        override fun edit() = object : android.content.SharedPreferences.Editor {
            override fun putString(key: String, value: String?) = apply { mapa[key] = value }
            override fun apply() {}
            override fun commit() = true
            override fun putStringSet(k: String, v: MutableSet<String>?) = this
            override fun putInt(k: String, v: Int) = this
            override fun putLong(k: String, v: Long) = this
            override fun putFloat(k: String, v: Float) = this
            override fun putBoolean(k: String, v: Boolean) = this
            override fun remove(k: String) = this
            override fun clear() = this
        }
        override fun getAll() = emptyMap<String, Any>()
        override fun getStringSet(k: String, d: MutableSet<String>?) = d
        override fun getInt(k: String, d: Int) = d
        override fun getLong(k: String, d: Long) = d
        override fun getFloat(k: String, d: Float) = d
        override fun getBoolean(k: String, d: Boolean) = d
        override fun contains(k: String) = mapa.containsKey(k)
        override fun registerOnSharedPreferenceChangeListener(l: android.content.SharedPreferences.OnSharedPreferenceChangeListener?) {}
        override fun unregisterOnSharedPreferenceChangeListener(l: android.content.SharedPreferences.OnSharedPreferenceChangeListener?) {}
    }

    @Test
    fun `sin nada guardado es el gris de siempre`() {
        assertEquals(TonoOscuro.GRIS, TonoOscuro.guardado(Prefs()))
    }

    @Test
    fun `basura guardada cae en el gris`() {
        assertEquals(TonoOscuro.GRIS, TonoOscuro.guardado(Prefs(hashMapOf(TonoOscuro.PREF to "morado"))))
    }

    @Test
    fun `elegir negro lo guarda y dice que ha cambiado`() {
        val p = Prefs()
        assertTrue(TonoOscuro.elegir(p, TonoOscuro.NEGRO))
        assertEquals(TonoOscuro.NEGRO, TonoOscuro.guardado(p))
        assertFalse("elegir lo mismo no recrea", TonoOscuro.elegir(p, TonoOscuro.NEGRO))
    }

    @Test
    fun `elegir basura vuelve al gris`() {
        val p = Prefs(hashMapOf(TonoOscuro.PREF to TonoOscuro.OSCURO))
        assertTrue(TonoOscuro.elegir(p, "morado"))
        assertEquals(TonoOscuro.GRIS, TonoOscuro.guardado(p))
    }

    @Test
    fun `el gris no lleva overlay y los otros dos si, distintos`() {
        assertNull(TonoOscuro.overlay(TonoOscuro.GRIS))
        assertEquals(R.style.ThemeOverlay_FC_Tono_Oscuro, TonoOscuro.overlay(TonoOscuro.OSCURO))
        assertEquals(R.style.ThemeOverlay_FC_Tono_Negro, TonoOscuro.overlay(TonoOscuro.NEGRO))
        assertNull(TonoOscuro.overlay("morado"))
    }

    @Test
    fun `tres opciones del mas claro al mas negro`() {
        assertEquals(listOf(TonoOscuro.GRIS, TonoOscuro.OSCURO, TonoOscuro.NEGRO), TonoOscuro.OPCIONES)
        assertEquals(listOf("Gris oscuro", "Oscuro", "Negro puro"), TonoOscuro.OPCIONES.map(TonoOscuro::etiqueta))
    }

    @Test
    fun `el resumen calla el gris y el tema claro`() {
        assertNull(TonoOscuro.resumen(TonoOscuro.GRIS, TemaApp.OSCURO))
        assertNull("de día el tono no se ve", TonoOscuro.resumen(TonoOscuro.NEGRO, TemaApp.CLARO))
        assertEquals("negro puro", TonoOscuro.resumen(TonoOscuro.NEGRO, TemaApp.OSCURO))
        assertEquals("tono oscuro", TonoOscuro.resumen(TonoOscuro.OSCURO, TemaApp.SISTEMA))
        assertNull(TonoOscuro.resumen("morado", TemaApp.OSCURO))
    }
}
