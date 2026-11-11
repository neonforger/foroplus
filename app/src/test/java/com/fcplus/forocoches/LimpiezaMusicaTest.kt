package com.fcplus.forocoches

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/** La carpeta de las playlists que ya no existen se borra una sola vez (2026-11-11). */
@RunWith(RobolectricTestRunner::class)
class LimpiezaMusicaTest {
    @get:Rule val carpeta = TemporaryFolder()

    private val prefs get() = ApplicationProvider.getApplicationContext<Context>()
        .getSharedPreferences("t_limpieza", Context.MODE_PRIVATE)

    @Before fun limpiar() { prefs.edit().clear().commit() }

    private fun conPlaylists(): File {
        val m = File(carpeta.root, "musica").apply { mkdirs() }
        File(m, "musica.json").writeText("""{"v":1}""")
        File(m, "musica.json.roto").writeText("x")
        return m
    }

    @Test fun `borrado de la carpeta musica una sola vez`() {
        val m = conPlaylists()
        assertTrue(LimpiezaMusica.carpetaDePlaylists(carpeta.root, prefs))
        assertFalse(m.exists())
        assertTrue(prefs.getBoolean(LimpiezaMusica.CLAVE, false))

        // Si reaparece una carpeta con ese nombre (otra cosa), no se vuelve a tocar: ya se hizo.
        val otra = conPlaylists()
        assertFalse(LimpiezaMusica.carpetaDePlaylists(carpeta.root, prefs))
        assertTrue(otra.exists())
    }

    @Test fun `sin carpeta no hace nada pero se acuerda de que ya miro`() {
        assertFalse(LimpiezaMusica.carpetaDePlaylists(carpeta.root, prefs))
        assertTrue(prefs.getBoolean(LimpiezaMusica.CLAVE, false))
        assertEquals(0, carpeta.root.list()!!.size)
    }

    @Test fun `no toca otras carpetas del almacenamiento interno`() {
        val otra = File(carpeta.root, "descargas").apply { mkdirs() }
        File(otra, "a.bin").writeText("a")
        conPlaylists()
        LimpiezaMusica.carpetaDePlaylists(carpeta.root, prefs)
        assertTrue(File(otra, "a.bin").exists())
    }
}
