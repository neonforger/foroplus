package com.fcplus.forocoches

import android.content.ComponentName
import android.content.Intent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/**
 * "Compartir en Música" (Ruling 30): una actividad de paso que nunca es una segunda MainActivity.
 * Lo que se fija: pasa el texto a LA MainActivity que haya (NEW_TASK + CLEAR_TOP + SINGLE_TOP:
 * sin SINGLE_TOP, CLEAR_TOP la destruiría y la volvería a crear, borrador incluido) con un extra
 * propio y un intent de launcher, nunca un SEND. Aunque lo compartido no traiga canciones pasa igual:
 * la pantalla de compartir sale con el texto en el buscador (lo decide MusicaController).
 */
@RunWith(RobolectricTestRunner::class)
class GuardarEnMusicaTest {

    private fun compartir(texto: String?, asunto: String? = null): GuardarEnMusica {
        val i = Intent(Intent.ACTION_SEND).setType("text/plain")
            .setComponent(ComponentName("com.foroplus.app.v2", GuardarEnMusica::class.java.name))
        if (texto != null) i.putExtra(Intent.EXTRA_TEXT, texto)
        if (asunto != null) i.putExtra(Intent.EXTRA_SUBJECT, asunto)
        return Robolectric.buildActivity(GuardarEnMusica::class.java, i).create().get()
    }

    @Test
    fun `con una cancion pasa el texto a MainActivity sin abrir otra y se cierra`() {
        val paso = compartir("https://youtu.be/dQw4w9WgXcQ?si=x", "Rick Astley - Never Gonna Give You Up")

        val abre = shadowOf(paso).nextStartedActivity
        assertEquals(MainActivity::class.java.name, abre.component!!.className)
        // Un intent de launcher (MAIN + LAUNCHER), no un SEND: si pasa a ser el de la tarea, el
        // icono la sigue reconociendo como la misma y no apila otra MainActivity encima.
        assertEquals(Intent.ACTION_MAIN, abre.action)
        assertTrue(abre.hasCategory(Intent.CATEGORY_LAUNCHER))
        val flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        assertEquals(flags, abre.flags and flags)
        assertEquals(
            "Rick Astley - Never Gonna Give You Up\nhttps://youtu.be/dQw4w9WgXcQ?si=x",
            abre.getStringExtra(EntradaCompartida.EXTRA)
        )
        // Nada que IntentRouter pueda tomar por un enlace de FC.
        assertNull(abre.getStringExtra("url"))
        assertNull(abre.data)
        assertTrue(paso.isFinishing)
    }

    @Test
    fun `un SEND sin texto no abre nada`() {
        val paso = compartir(null)
        assertNull(shadowOf(paso).nextStartedActivity)
        assertTrue(paso.isFinishing)
    }
}
