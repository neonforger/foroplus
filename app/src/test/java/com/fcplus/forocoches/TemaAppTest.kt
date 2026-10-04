package com.fcplus.forocoches

import androidx.appcompat.app.AppCompatDelegate
import org.junit.Assert.assertEquals
import org.junit.Test

class TemaAppTest {

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
    fun `sin nada guardado sigue al sistema`() {
        assertEquals(TemaApp.SISTEMA, TemaApp.guardado(Prefs()))
    }

    @Test
    fun `un valor corrupto no deja la app en un modo raro`() {
        val p = Prefs(hashMapOf(TemaApp.PREF to "azul"))
        assertEquals(TemaApp.SISTEMA, TemaApp.guardado(p))
    }

    @Test
    fun `cada modo se traduce a su constante de AppCompat`() {
        assertEquals(AppCompatDelegate.MODE_NIGHT_NO, TemaApp.modoDelegate(TemaApp.CLARO))
        assertEquals(AppCompatDelegate.MODE_NIGHT_YES, TemaApp.modoDelegate(TemaApp.OSCURO))
        assertEquals(
            AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM,
            TemaApp.modoDelegate(TemaApp.SISTEMA)
        )
        // Lo desconocido NUNCA deja la app a oscuras por accidente.
        assertEquals(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM, TemaApp.modoDelegate("loquesea"))
    }

    @Test
    fun `elegir el modo que ya estaba no cuenta como cambio`() {
        val p = Prefs()
        // Falso: ya seguía al sistema. Si devolviera true, la actividad se recrearía sin
        // motivo cada vez que alguien toca el chip que ya estaba marcado.
        assertEquals(false, TemaApp.elegir(p, TemaApp.SISTEMA))
        assertEquals(true, TemaApp.elegir(p, TemaApp.OSCURO))
        assertEquals(false, TemaApp.elegir(p, TemaApp.OSCURO))
        assertEquals(TemaApp.OSCURO, TemaApp.guardado(p))
    }

    @Test
    fun `se guarda una palabra nuestra, no el entero de AppCompat`() {
        val p = Prefs()
        TemaApp.elegir(p, TemaApp.CLARO)
        // Si guardáramos el int de AppCompatDelegate y esas constantes cambiaran de valor en
        // una versión futura, la gente se encontraría la app en un modo que no eligió.
        assertEquals("claro", p.getString(TemaApp.PREF, null))
    }

    @Test
    fun `se sabe si la app se esta pintando en oscuro`() {
        val NOCHE = android.content.res.Configuration.UI_MODE_NIGHT_YES
        val DIA = android.content.res.Configuration.UI_MODE_NIGHT_NO
        // El uiMode mezcla el tipo de aparato en los bits altos (movil, coche, TV...): hay que
        // quedarse solo con la mascara de noche o un movil normal daria siempre "claro".
        val MOVIL = android.content.res.Configuration.UI_MODE_TYPE_NORMAL
        assertEquals(true, TemaApp.esOscuro(MOVIL or NOCHE))
        assertEquals(false, TemaApp.esOscuro(MOVIL or DIA))
        // Sin informacion (0) se asume claro: es el lado seguro, porque de ahi solo sale el
        // color de los iconos de la barra de estado y el fondo por defecto del sistema es claro.
        assertEquals(false, TemaApp.esOscuro(0))
    }

    @Test
    fun `las tres opciones se enseñan con nombre legible`() {
        assertEquals(listOf("Claro", "Oscuro", "Sistema"), TemaApp.OPCIONES.map { TemaApp.etiqueta(it) })
    }
}
