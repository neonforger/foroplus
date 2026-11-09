package com.fcplus.forocoches

import android.content.Intent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * "Guardar en Música": lo que otra app comparte con ForoPlus (YouTube, Spotify, WhatsApp…). Es
 * dato de fuera: solo cuenta un SEND de texto plano, y con tope de tamaño. La hoja "Añadir" ya
 * se queda solo con los enlaces que reconoce EnlaceMusica.
 */
class EntradaCompartidaTest {

    private val enlace = "https://youtu.be/dQw4w9WgXcQ?si=x"

    @Test
    fun `un SEND de texto plano devuelve el texto`() {
        assertEquals(enlace, EntradaCompartida.texto(Intent.ACTION_SEND, "text/plain", enlace, null))
    }

    @Test
    fun `con asunto va el asunto y debajo el texto`() {
        assertEquals(
            "Rick Astley - Never Gonna Give You Up\n$enlace",
            EntradaCompartida.texto(Intent.ACTION_SEND, "text/plain", enlace, "Rick Astley - Never Gonna Give You Up")
        )
    }

    @Test
    fun `un asunto que ya va dentro del texto no se repite`() {
        val texto = "Playlist «Para la autovía» (1 canción)\n\n1. Estopa – Como Camarón\n$enlace"
        assertEquals(texto, EntradaCompartida.texto(Intent.ACTION_SEND, "text/plain", texto, "Playlist «Para la autovía»"))
    }

    @Test
    fun `sin texto vale el asunto y sin nada es null`() {
        assertEquals(enlace, EntradaCompartida.texto(Intent.ACTION_SEND, "text/plain", null, enlace))
        assertNull(EntradaCompartida.texto(Intent.ACTION_SEND, "text/plain", null, null))
        assertNull(EntradaCompartida.texto(Intent.ACTION_SEND, "text/plain", "  \n ", ""))
    }

    @Test
    fun `lo que no es un SEND es null aunque traiga texto`() {
        // Un enlace de FC (VIEW) o el launcher (MAIN) siguen su camino de siempre, IntentRouter.
        assertNull(EntradaCompartida.texto(Intent.ACTION_VIEW, "text/plain", enlace, null))
        assertNull(EntradaCompartida.texto(Intent.ACTION_MAIN, null, enlace, null))
        assertNull(EntradaCompartida.texto(null, "text/plain", enlace, null))
    }

    @Test
    fun `un SEND que no es texto plano es null`() {
        assertNull(EntradaCompartida.texto(Intent.ACTION_SEND, "image/png", enlace, null))
        assertNull(EntradaCompartida.texto(Intent.ACTION_SEND, null, enlace, null))
        assertNull(EntradaCompartida.texto(Intent.ACTION_SEND_MULTIPLE, "text/plain", enlace, null))
    }

    @Test
    fun `un texto enorme se corta a 20000 caracteres`() {
        val largo = "a".repeat(30_000)
        assertEquals(20_000, EntradaCompartida.texto(Intent.ACTION_SEND, "text/plain", largo, null)!!.length)
    }

    @Test
    fun `el corte no parte un emoji por la mitad`() {
        // 19.999 letras y un emoji (dos char): cortar a 20.000 dejaría medio emoji suelto.
        val largo = "a".repeat(19_999) + "🎵" + "b".repeat(100)
        val r = EntradaCompartida.texto(Intent.ACTION_SEND, "text/plain", largo, null)!!
        assertEquals(19_999, r.length)
        assertFalse(Character.isHighSurrogate(r.last()))
    }

    @Test
    fun `el corte no deja un enlace a medias`() {
        // Un enlace de YouTube cortado sigue pareciendo uno (sus ids valen desde 6 caracteres) y
        // se guardaría otro vídeo: se corta en el espacio de antes.
        val relleno = "x".repeat(19_990)
        val r = EntradaCompartida.texto(Intent.ACTION_SEND, "text/plain", "$relleno $enlace y más", null)!!
        assertEquals(relleno, r)
        assertFalse(EntradaCompartida.traeCanciones(r))
    }

    @Test
    fun `solo se atiende el SEND de una actividad nueva`() {
        assertTrue(EntradaCompartida.esNueva(recreada = false, flags = Intent.FLAG_ACTIVITY_NEW_TASK))
        // Recreada (modo oscuro, estilo, tamaño de letra) el intent es el mismo de antes: la hoja
        // volvería a salir con lo que ya se guardó.
        assertFalse(EntradaCompartida.esNueva(recreada = true, flags = 0))
        // Y lo mismo al volver desde Recientes, que relanza el intent con el que se abrió.
        assertFalse(EntradaCompartida.esNueva(recreada = false, flags = Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY))
    }

    @Test
    fun `trae canciones si hay un enlace de YouTube o de Spotify, aunque sea corto`() {
        assertTrue(EntradaCompartida.traeCanciones("Mira esto $enlace"))
        assertTrue(EntradaCompartida.traeCanciones("https://open.spotify.com/track/4uLU6hMCjMI75M1A2tKUQC?si=1"))
        assertTrue(EntradaCompartida.traeCanciones("https://spotify.link/AbCdEf123"))
        assertFalse(EntradaCompartida.traeCanciones("https://forocoches.com/foro/showthread.php?t=1"))
        assertFalse(EntradaCompartida.traeCanciones("hola"))
    }
}
