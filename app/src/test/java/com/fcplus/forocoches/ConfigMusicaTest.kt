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
        // Una compilación con la dirección provisional no puede llamar a un host que no existe.
        val provisional = "https://foroplus-api.PENDIENTE.workers.dev"
        assertFalse(ConfigMusica.servidorActivo(JSONObject("""{"musica":{"servidor":true}}"""), provisional))
    }

    @Test fun conLaDireccionDesplegadaMandaLaConfig() {
        // Desde la tarea D1 la BASE es la real: decide el interruptor de fc_config.json.
        assertFalse(ServidorMusica.BASE.contains("PENDIENTE"))
        assertTrue(ConfigMusica.servidorActivo(JSONObject("""{"musica":{"servidor":true}}""")))
        assertFalse(ConfigMusica.servidorActivo(JSONObject("{}")))
    }

    @Test fun `servidor apagado no hay pestana Musica`() {
        // MainActivity pinta la pestaña "Música" SOLO si servidorActivo() (pintarPestanas): Música es solo el
        // feed y sin servidor no hay nada que enseñar. Estos son todos los modos de "apagado".
        assertFalse(ConfigMusica.servidorActivo(null))
        assertFalse(ConfigMusica.servidorActivo(JSONObject("""{"musica":{"servidor":false}}""")))
        assertFalse(ConfigMusica.servidorActivo(JSONObject("""{"musica":{}}""")))
        assertFalse(ConfigMusica.servidorActivo(JSONObject("""{"musica":{"servidor":true}}"""), "https://x.PENDIENTE.dev"))
    }
}
