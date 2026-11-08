package com.fcplus.forocoches

import org.json.JSONObject
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConfigMusicaTest {
    private val real = "https://foroplus-api.ejemplo.workers.dev"

    @Test fun sinBloqueEstaApagado() {
        assertFalse(ConfigMusica.servidorActivo(null, real))
        assertFalse(ConfigMusica.servidorActivo(JSONObject("{}"), real))
        assertFalse(ConfigMusica.servidorActivo(JSONObject("""{"musica":{}}"""), real))
    }

    @Test fun falseApaga() {
        assertFalse(ConfigMusica.servidorActivo(JSONObject("""{"musica":{"servidor":false}}"""), real))
    }

    @Test fun trueEnciendeConUnaDireccionReal() {
        assertTrue(ConfigMusica.servidorActivo(JSONObject("""{"musica":{"servidor":true}}"""), real))
    }

    @Test fun conLaDireccionProvisionalNuncaEstaActivo() {
        // Mientras BASE lleve PENDIENTE (hasta la tarea D1) ninguna config puede encenderlo.
        assertTrue(ServidorMusica.BASE.contains("PENDIENTE"))
        assertFalse(ConfigMusica.servidorActivo(JSONObject("""{"musica":{"servidor":true}}""")))
    }
}
