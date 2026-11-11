package com.fcplus.forocoches

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BuscadorDeezerTest {

    private fun fixture(nombre: String): String =
        javaClass.classLoader!!.getResource("deezer/$nombre")!!.readText()

    @Test fun parseaTituloArtistaPortadaYDuracion() {
        val r = BuscadorDeezer.parsearBusqueda(fixture("search_highway.json"))
        assertEquals(10, r.size)
        val primero = r.first()
        assertEquals(92719900L, primero.id)
        assertEquals("Highway to Hell", primero.titulo)
        assertEquals("AC/DC", primero.artista)
        assertEquals(206, primero.duracionSeg)
        assertNotNull(primero.portada)
        assertTrue(primero.portada!!.startsWith("https://cdn-images.dzcdn.net/images/cover/"))
    }

    @Test fun descartaResultadosSinIdOSinTitulo() {
        val json = """{"data":[
            {"title":"Sin id","artist":{"name":"A"},"duration":10},
            {"id":2,"title":"  ","artist":{"name":"A"}},
            {"id":3,"title":"Bueno","artist":{"name":"B"}}]}"""
        val r = BuscadorDeezer.parsearBusqueda(json)
        assertEquals(1, r.size)
        assertEquals(3L, r[0].id)
        assertNull(r[0].duracionSeg)
        assertNull(r[0].portada)
    }

    @Test fun jsonRotoOSinDataDaListaVacia() {
        assertTrue(BuscadorDeezer.parsearBusqueda("no es json").isEmpty())
        assertTrue(BuscadorDeezer.parsearBusqueda("""{"error":{"type":"Exception"}}""").isEmpty())
    }

    @Test fun portadaDeHostRaroEsNull() {
        val json = """{"data":[{"id":1,"title":"T","artist":{"name":"A"},
            "album":{"cover_medium":"https://evil.com/x.jpg"}}]}"""
        assertNull(BuscadorDeezer.parsearBusqueda(json)[0].portada)
    }

    @Test fun fragmentoDelFixtureEsDelHostConocido() {
        val f = BuscadorDeezer.parsearFragmento(fixture("track_92719900.json"))
        assertNotNull(f)
        assertTrue(f!!.startsWith("https://cdnt-preview.dzcdn.net/"))
    }

    @Test fun fragmentoConHostRaroEsNull() {
        assertNull(BuscadorDeezer.parsearFragmento("""{"preview":"https://evil.com/a.mp3"}"""))
        assertNull(BuscadorDeezer.parsearFragmento("""{"preview":"https://cdnt-preview.dzcdn.net.evil.com/a.mp3"}"""))
    }

    @Test fun fragmentoHttpEsNull() {
        assertNull(BuscadorDeezer.parsearFragmento("""{"preview":"http://cdnt-preview.dzcdn.net/a.mp3"}"""))
    }

    @Test fun fragmentoVacioOSinCampoEsNull() {
        assertNull(BuscadorDeezer.parsearFragmento("""{"preview":""}"""))
        assertNull(BuscadorDeezer.parsearFragmento("""{"id":1}"""))
        assertNull(BuscadorDeezer.parsearFragmento("basura"))
    }

    @Test fun buscarConUnaLetraNoLlamaALaRed() {
        var llamadas = 0
        val r = BuscadorDeezer.buscar(" a ") { llamadas++; null }
        assertTrue(r.isEmpty())
        assertEquals(0, llamadas)
    }

    @Test fun buscarCodificaYRecortaLaConsulta() {
        val urls = mutableListOf<String>()
        BuscadorDeezer.buscar("  highway to hell ") { urls += it; fixture("search_highway.json") }
        assertEquals(listOf("https://api.deezer.com/search/track?q=highway+to+hell&limit=10"), urls)
    }

    @Test fun buscarSinRespuestaDaListaVacia() {
        assertTrue(BuscadorDeezer.buscar("highway") { null }.isEmpty())
    }

    @Test fun fragmentoPideSiempreTrackId() {
        // La URL `preview` de la búsqueda va firmada y caduca: nunca se reutiliza.
        val urls = mutableListOf<String>()
        val f = BuscadorDeezer.fragmento(92719900L) { urls += it; fixture("track_92719900.json") }
        assertEquals(listOf("https://api.deezer.com/track/92719900"), urls)
        assertNotNull(f)
    }

    @Test fun fragmentoSinRedEsNull() {
        assertNull(BuscadorDeezer.fragmento(1L) { null })
    }

    @Test fun hostsMedidosConRespuestasReales() {
        assertTrue("api.deezer.com" in BuscadorDeezer.HOSTS)
        assertTrue("cdnt-preview.dzcdn.net" in BuscadorDeezer.HOSTS_FRAGMENTO)
        assertTrue("cdn-images.dzcdn.net" in BuscadorDeezer.HOSTS_PORTADA)
    }
}
