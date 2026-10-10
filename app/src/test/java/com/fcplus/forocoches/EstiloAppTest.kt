package com.fcplus.forocoches

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EstiloAppTest {

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
    fun `sin nada guardado es la compacta`() {
        assertEquals(EstiloApp.COMPACTA, EstiloApp.guardado(Prefs()))
    }

    @Test
    fun `basura guardada cae en la compacta`() {
        assertEquals(EstiloApp.COMPACTA, EstiloApp.guardado(Prefs(hashMapOf(EstiloApp.PREF to "neon"))))
    }

    @Test
    fun `elegir tarjetas lo guarda y dice que ha cambiado`() {
        val p = Prefs()
        assertTrue(EstiloApp.elegir(p, EstiloApp.TARJETAS))
        assertEquals(EstiloApp.TARJETAS, EstiloApp.guardado(p))
    }

    @Test
    fun `elegir lo que ya hay no cambia nada`() {
        val p = Prefs(hashMapOf(EstiloApp.PREF to EstiloApp.TARJETAS))
        assertFalse(EstiloApp.elegir(p, EstiloApp.TARJETAS))
    }

    @Test
    fun `elegir basura guarda la compacta`() {
        val p = Prefs(hashMapOf(EstiloApp.PREF to EstiloApp.TARJETAS))
        assertTrue(EstiloApp.elegir(p, "neon"))
        assertEquals(EstiloApp.COMPACTA, EstiloApp.guardado(p))
    }

    @Test
    fun `la compacta no lleva overlay y tarjetas si`() {
        assertNull(EstiloApp.overlay(EstiloApp.COMPACTA))
        assertEquals(R.style.ThemeOverlay_FC_Tarjetas, EstiloApp.overlay(EstiloApp.TARJETAS))
    }

    @Test
    fun etiquetas() {
        assertEquals("Compacta", EstiloApp.etiqueta(EstiloApp.COMPACTA))
        assertEquals("Tarjetas", EstiloApp.etiqueta(EstiloApp.TARJETAS))
    }
}
