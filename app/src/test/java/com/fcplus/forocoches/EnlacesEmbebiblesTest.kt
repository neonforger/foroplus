package com.fcplus.forocoches

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Las cuatro etiquetas salen de mensajes REALES del foro (ver EnlacesEmbebibles). */
class EnlacesEmbebiblesTest {

    @Test
    fun `instagram se envuelve tal cual, reel incluido`() {
        assertEquals(
            "[IG]https://www.instagram.com/reel/Dc-bz1jA5wd/?stkn=abc[/IG]",
            EnlacesEmbebibles.conTarjetas("https://www.instagram.com/reel/Dc-bz1jA5wd/?stkn=abc")
        )
    }

    @Test
    fun `un tweet se envuelve con su URL entera`() {
        assertEquals(
            "[TWEET]https://twitter.com/ItsmeskylerB/status/1284105262496804864?s=20[/TWEET]",
            EnlacesEmbebibles.conTarjetas("https://twitter.com/ItsmeskylerB/status/1284105262496804864?s=20")
        )
    }

    @Test
    fun `tambien vale x punto com`() {
        assertEquals(
            "[TWEET]https://x.com/alguien/status/123[/TWEET]",
            EnlacesEmbebibles.conTarjetas("https://x.com/alguien/status/123")
        )
    }

    @Test
    fun `tiktok se envuelve entero`() {
        assertEquals(
            "[TIKTOK]https://www.tiktok.com/@planeta_asombroso/video/7229670784481053979[/TIKTOK]",
            EnlacesEmbebibles.conTarjetas("https://www.tiktok.com/@planeta_asombroso/video/7229670784481053979")
        )
    }

    @Test
    fun `youtube va con el ID pelado y NO con la url`() {
        assertEquals(
            "[YOUTUBE]dxk3IKcPgYU[/YOUTUBE]",
            EnlacesEmbebibles.conTarjetas("https://www.youtube.com/watch?v=dxk3IKcPgYU")
        )
        assertEquals(
            "[YOUTUBE]dxk3IKcPgYU[/YOUTUBE]",
            EnlacesEmbebibles.conTarjetas("https://youtu.be/dxk3IKcPgYU")
        )
        assertEquals(
            "[YOUTUBE]dxk3IKcPgYU[/YOUTUBE]",
            EnlacesEmbebibles.conTarjetas("https://www.youtube.com/shorts/dxk3IKcPgYU")
        )
    }

    @Test
    fun `el texto de alrededor se respeta`() {
        assertEquals(
            "mirad esto [IG]https://instagram.com/p/ABC[/IG] que bueno",
            EnlacesEmbebibles.conTarjetas("mirad esto https://instagram.com/p/ABC que bueno")
        )
    }

    @Test
    fun `lo que YA esta envuelto no se toca`() {
        // Envolver dos veces romperia la tarjeta.
        val ya = "[IG]https://instagram.com/p/ABC[/IG]"
        assertEquals(ya, EnlacesEmbebibles.conTarjetas(ya))
    }

    @Test
    fun `no se mete dentro de una cita`() {
        // Reescribir lo que hay dentro de una cita es meterse en el mensaje de otro.
        val t = "[QUOTE=Pak;1]https://instagram.com/p/ABC[/QUOTE] yo digo otra cosa"
        assertEquals(t, EnlacesEmbebibles.conTarjetas(t))
    }

    @Test
    fun `un enlace dentro de URL o IMG se respeta`() {
        val t = "[URL=https://youtu.be/dxk3IKcPgYU]mira[/URL] y [IMG]https://x.example/a.png[/IMG]"
        assertEquals(t, EnlacesEmbebibles.conTarjetas(t))
    }

    @Test
    fun `un enlace normal no se convierte en nada`() {
        val t = "https://elpais.com/noticia y https://forocoches.com/foro/showthread.php?t=1"
        assertEquals(t, EnlacesEmbebibles.conTarjetas(t))
    }

    @Test
    fun `varios enlaces en el mismo mensaje`() {
        assertEquals(
            "[YOUTUBE]abc123[/YOUTUBE] y [TIKTOK]https://vm.tiktok.com/ZXYW[/TIKTOK] fin",
            EnlacesEmbebibles.conTarjetas("https://youtu.be/abc123 y https://vm.tiktok.com/ZXYW fin")
        )
    }

    @Test
    fun `un perfil de instagram NO es una publicacion`() {
        // instagram.com/usuario no se puede incrustar: envolverlo dejaria una tarjeta rota.
        assertNull(EnlacesEmbebibles.tarjetaDe("https://www.instagram.com/algunusuario/"))
    }
}
