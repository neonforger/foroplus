package com.fcplus.forocoches

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Enlaces cortos de TikTok. Medido el 2026-10-16 con la vista previa de FC (sin publicar):
 * `[TIKTOK]https://vm.tiktok.com/…[/TIKTOK]` y `…/t/…` dan "[TikTok Error - URL incorrecta]";
 * `…/@usuario/video/ID` (con o sin parámetros) y `…/video/ID` funcionan; `…/@/video/ID` NO.
 */
class TiktokCortoTest {

    @Test
    fun `encuentra los dos tipos de enlace corto`() {
        val t = "mira https://vm.tiktok.com/ZNdAbCdEf/ y [TIKTOK]https://www.tiktok.com/t/ZTAbCd/[/TIKTOK] fin"
        assertEquals(
            listOf("https://vm.tiktok.com/ZNdAbCdEf/", "https://www.tiktok.com/t/ZTAbCd/"),
            TiktokCorto.cortos(t)
        )
    }

    @Test
    fun `los completos no son cortos`() {
        assertEquals(
            emptyList<String>(),
            TiktokCorto.cortos("https://www.tiktok.com/@planeta_asombroso/video/7229670784481053979")
        )
    }

    @Test
    fun `lo que hay dentro de una cita no se toca, es texto de otro`() {
        val t = "[QUOTE=pepe]https://vm.tiktok.com/AAA/[/QUOTE] y el mio https://vm.tiktok.com/BBB/"
        assertEquals(listOf("https://vm.tiktok.com/BBB/"), TiktokCorto.cortos(t))
    }

    @Test
    fun `sin repetidos`() {
        assertEquals(1, TiktokCorto.cortos("https://vm.tiktok.com/A1/ otra vez https://vm.tiktok.com/A1/").size)
    }

    @Test
    fun `la redireccion real trae el usuario vacio y se queda en video-ID`() {
        // Location real de vm.tiktok.com (2026-10-16), recortada.
        val loc = "https://www.tiktok.com/@/video/7515016903421693206?_r=1&_d=secCgYIASAHKAESPgo8&u_code=x"
        assertEquals("https://www.tiktok.com/video/7515016903421693206", TiktokCorto.completa(loc))
    }

    @Test
    fun `con usuario se conserva, sin los parametros de rastreo`() {
        assertEquals(
            "https://www.tiktok.com/@planeta_asombroso/video/7229670784481053979",
            TiktokCorto.completa("https://www.tiktok.com/@planeta_asombroso/video/7229670784481053979?_t=ZN&_r=1")
        )
    }

    @Test
    fun `una redireccion que no lleva a un video no vale`() {
        // Lo que devuelve tiktok.com/t/ con un código que no existe.
        assertNull(TiktokCorto.completa("https://www.tiktok.com/?_r=1"))
        assertNull(TiktokCorto.completa("https://example.com/video/123"))
    }

    @Test
    fun `sustituir cambia cada corto por su completo, tambien dentro de un TIKTOK`() {
        val t = "a https://vm.tiktok.com/A1/ b [TIKTOK]https://vm.tiktok.com/A1/[/TIKTOK]"
        assertEquals(
            "a https://www.tiktok.com/video/1 b [TIKTOK]https://www.tiktok.com/video/1[/TIKTOK]",
            TiktokCorto.sustituir(t, mapOf("https://vm.tiktok.com/A1/" to "https://www.tiktok.com/video/1"))
        )
    }

    @Test
    fun `sustituir respeta las citas`() {
        val t = "[QUOTE]https://vm.tiktok.com/A1/[/QUOTE] https://vm.tiktok.com/A1/"
        assertEquals(
            "[QUOTE]https://vm.tiktok.com/A1/[/QUOTE] https://www.tiktok.com/video/1",
            TiktokCorto.sustituir(t, mapOf("https://vm.tiktok.com/A1/" to "https://www.tiktok.com/video/1"))
        )
    }
}
