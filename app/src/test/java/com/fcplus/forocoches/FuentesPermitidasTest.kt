package com.fcplus.forocoches

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FuentesPermitidasTest {

    private val BUENA = "https://raw.githubusercontent.com/neonforger/foroplus-mas18/datos/paginas/"

    @Test fun `la del repo de datos vale`() = assertTrue(FuentesPermitidas.aceptada(BUENA + "1.json"))
    @Test fun `http a secas no`() = assertFalse(FuentesPermitidas.aceptada(BUENA.replace("https", "http")))
    @Test fun `otro usuario de GitHub no`() =
        assertFalse(FuentesPermitidas.aceptada("https://raw.githubusercontent.com/otro/foroplus-mas18/datos/1.json"))
    @Test fun `un dominio que EMPIEZA igual no`() =
        assertFalse(FuentesPermitidas.aceptada("https://raw.githubusercontent.com.evil.com/neonforger/foroplus-mas18/x"))
    @Test fun `un repo que empieza igual no`() =
        assertFalse(FuentesPermitidas.aceptada("https://raw.githubusercontent.com/neonforger/foroplus-mas18-falso/x"))
    @Test fun `vacia no`() = assertFalse(FuentesPermitidas.aceptada(""))

    private fun cfg(s: String) = JSONObject(s)

    @Test fun `sin bloque no hay seccion`() = assertNull(ConfigMas18Parser.de(cfg("""{"version":2}""")))
    @Test fun `config nula no hay seccion`() = assertNull(ConfigMas18Parser.de(null))
    @Test fun `apagada no hay seccion`() =
        assertNull(ConfigMas18Parser.de(cfg("""{"mas18":{"activo":false,"url":"$BUENA"}}""")))
    @Test fun `url no permitida no hay seccion`() =
        assertNull(ConfigMas18Parser.de(cfg("""{"mas18":{"activo":true,"url":"https://mi-servidor.com/p/"}}""")))

    @Test fun `activa y permitida, y la pagina se compone bien con o sin barra final`() {
        val c = ConfigMas18Parser.de(cfg("""{"mas18":{"activo":true,"url":"$BUENA"}}"""))!!
        assertEquals(BUENA + "3.json", c.urlPagina(3))
        val sinBarra = ConfigMas18Parser.de(cfg("""{"mas18":{"activo":true,"url":"${BUENA.trimEnd('/')}"}}"""))!!
        assertEquals(BUENA + "1.json", sinBarra.urlPagina(1))
    }
}
