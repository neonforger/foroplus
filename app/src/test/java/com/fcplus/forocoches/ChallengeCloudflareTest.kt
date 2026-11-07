package com.fcplus.forocoches

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChallengeCloudflareTest {
    @Test fun `el challenge en ingles`() = assertTrue(ChallengeCloudflare.esTitulo("Just a moment..."))

    @Test fun `el challenge en castellano`() = assertTrue(ChallengeCloudflare.esTitulo("Un momento…"))

    @Test fun `el bloqueo de cloudflare`() =
        assertTrue(ChallengeCloudflare.esTitulo("Attention Required! | Cloudflare"))

    @Test fun `una pagina normal del foro no es el challenge`() =
        assertFalse(ChallengeCloudflare.esTitulo("ForoCoches"))

    @Test fun `un hilo normal no es el challenge`() =
        assertFalse(ChallengeCloudflare.esTitulo("Lo que el juanker está intentando hacer +SSRF - Forocoches"))

    @Test fun `un hilo que empieza por un momento no es el challenge`() =
        assertFalse(ChallengeCloudflare.esTitulo("Un momento de gloria - ForoCoches"))

    @Test fun `sin titulo no es el challenge`() {
        assertFalse(ChallengeCloudflare.esTitulo(null))
        assertFalse(ChallengeCloudflare.esTitulo(""))
    }
}
