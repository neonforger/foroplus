package com.fcplus.forocoches

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ApiMusicaTest {
    private class Llamada(val metodo: String, val url: String, val cuerpo: String?, val cabeceras: Map<String, String>)

    private val llamadas = mutableListOf<Llamada>()
    private var respuesta = Respuesta(200, "{}")
    private var token: String? = null
    private val api = ApiMusica({ token }) { m, u, c, h ->
        llamadas.add(Llamada(m, u, c, h)); respuesta
    }

    private fun responde(status: Int, json: String) { respuesta = Respuesta(status, json) }
    private fun <T> ok(r: ApiMusica.R<T>): T = (r as ApiMusica.R.Ok).v
    private fun error(r: ApiMusica.R<*>): ApiMusica.R.Error = r as ApiMusica.R.Error

    private val cancionJson = """{"id":"01JABC","titulo":"Cancion X","artista":"Alguien","portada":"https://i.ytimg.com/vi/abc/hq.jpg","youtube":"https://www.youtube.com/watch?v=abc","spotify":"https://open.spotify.com/track/xyz"}"""
    private val cancionSinNada = """{"id":"01JDEF","titulo":"Sola","artista":null,"portada":null}"""

    @Test fun sinCookieNunca() {
        token = "tok"
        api.reto("123456"); api.comprobar("R"); api.yo(); api.semana(); api.proponer("https://x")
        api.votar("C", true); api.votar("C", false)
        api.playlists("recientes", null); api.playlist("P"); api.publicar("n", listOf("a")); api.anadirAPublicada("P", listOf("a"))
        api.retirar("P"); api.guardarPublicada("P"); api.denunciar("playlist", "P", "m"); api.borrarCuenta()
        assertEquals(15, llamadas.size)
        for (l in llamadas) assertTrue(l.cabeceras.keys.none { it.equals("Cookie", true) })
    }

    @Test fun authSoloConToken() {
        token = null
        api.yo(); api.semana()
        assertTrue(llamadas.all { !it.cabeceras.containsKey("Authorization") })
        llamadas.clear()
        token = "abc"
        api.yo(); api.semana(); api.proponer("e"); api.borrarCuenta()
        assertTrue(llamadas.all { it.cabeceras["Authorization"] == "Bearer abc" })
    }

    @Test fun lasRutasPublicasNoMandanElToken() {
        token = "abc"
        api.reto("123456"); api.comprobar("R"); api.playlists("recientes", null); api.playlist("P")
        assertTrue(llamadas.all { !it.cabeceras.containsKey("Authorization") })
    }

    @Test fun metodosYUrls() {
        token = "t"
        api.reto("123456"); api.comprobar("R"); api.yo(); api.semana(); api.proponer("e")
        api.votar("C", true); api.votar("C", false)
        api.playlists("guardadas", "cur+sor/="); api.playlist("P1")
        api.publicar("n", listOf("a")); api.anadirAPublicada("P1", listOf("a")); api.retirar("P1")
        api.guardarPublicada("P1"); api.denunciar("cancion", "C", "m"); api.borrarCuenta()
        val b = ServidorMusica.BASE
        val esperado = listOf(
            "POST $b/v1/verificacion/reto", "POST $b/v1/verificacion/comprobar", "GET $b/v1/yo", "GET $b/v1/semana",
            "POST $b/v1/semana/proponer", "POST $b/v1/semana/voto", "DELETE $b/v1/semana/voto?cancion=C",
            "GET $b/v1/playlists?orden=guardadas&cursor=cur%2Bsor%2F%3D", "GET $b/v1/playlists/P1",
            "POST $b/v1/playlists", "POST $b/v1/playlists/P1/canciones", "DELETE $b/v1/playlists/P1",
            "POST $b/v1/playlists/P1/guardar", "POST $b/v1/denuncias", "DELETE $b/v1/cuenta"
        )
        assertEquals(esperado, llamadas.map { "${it.metodo} ${it.url}" })
        assertTrue(llamadas.all { ServidorMusica.aceptada(it.url) })
    }

    @Test fun cuerposJson() {
        api.reto("123456"); api.publicar("Mi lista", listOf("a", "b")); api.votar("C1", false); api.denunciar("playlist", "P", "mal")
        assertEquals("123456", JSONObject(llamadas[0].cuerpo!!).getString("uid"))
        val p = JSONObject(llamadas[1].cuerpo!!)
        assertEquals("Mi lista", p.getString("nombre")); assertEquals(2, p.getJSONArray("enlaces").length())
        assertNull(llamadas[2].cuerpo) // quitar el voto: sin cuerpo, la canción va en la URL
        assertTrue(llamadas[2].url.endsWith("?cancion=C1"))
        val d = JSONObject(llamadas[3].cuerpo!!)
        assertEquals("playlist", d.getString("tipo")); assertEquals("P", d.getString("objetivo")); assertEquals("mal", d.getString("motivo"))
        assertEquals("application/json", llamadas[0].cabeceras["Content-Type"])
    }

    // ---- una respuesta de ejemplo por ruta (copiadas del contrato de B5-B7) ----

    @Test fun parseaReto() {
        responde(200, """{"reto":"01J0RETO","coche":"Seat Ibiza 1.9 TDI 2004","caduca":1762560000000}""")
        assertEquals(Reto("01J0RETO", "Seat Ibiza 1.9 TDI 2004", 1762560000000L), ok(api.reto("123456")))
    }

    @Test fun parseaComprobacion() {
        responde(200, """{"token":"tok43","nombre":"YoMismo","diasCuenta":162,"puedeVotar":true}""")
        assertEquals(Comprobacion(false, "tok43", "YoMismo", true, 162), ok(api.comprobar("R")))
        responde(200, """{"token":"t","nombre":"YoMismo","diasCuenta":null,"puedeVotar":false}""")
        val c2 = ok(api.comprobar("R"))
        assertNull(c2.diasCuenta); assertFalse(c2.puedeVotar)
    }

    @Test fun comprobar202() {
        responde(202, """{"estado":"pendiente"}""")
        val c = ok(api.comprobar("R"))
        assertTrue(c.pendiente); assertNull(c.token)
    }

    @Test fun parseaYo() {
        responde(200, """{"nombre":"YoMismo","diasCuenta":162,"puedeVotar":true,"votosRestantes":10,"propuestasRestantes":3}""")
        assertEquals(Yo("YoMismo", 162, true, 10, 3), ok(api.yo()))
        responde(200, """{"nombre":"YoMismo","diasCuenta":null,"puedeVotar":false,"votosRestantes":10,"propuestasRestantes":3}""")
        assertNull(ok(api.yo()).diasCuenta)
    }

    @Test fun parseaSemanaConAnterior() {
        responde(200, """{"actual":{"id":"2026-W45","cierra":1762729200000,"canciones":[$cancionJson,$cancionSinNada]},
            "anterior":{"id":"2026-W44","cancionSemana":{"id":"01JABC","titulo":"Cancion X","artista":"Alguien","portada":null,"votos":7},
            "top":[{"id":"01JABC","titulo":"Cancion X","artista":"Alguien","portada":null,"votos":7},{"id":"01JDEF","titulo":"Sola","artista":null,"portada":null,"votos":2}]}}""")
        val s = ok(api.semana())
        assertEquals("2026-W45", s.actualId); assertEquals(1762729200000L, s.cierra)
        assertEquals(2, s.actual.size)
        assertEquals("https://www.youtube.com/watch?v=abc", s.actual[0].youtube)
        assertNull(s.actual[1].artista); assertNull(s.actual[1].youtube)
        assertTrue(s.votadas.isEmpty())
        assertEquals("2026-W44", s.anteriorId)
        assertEquals("01JABC", s.cancionSemana!!.id)
        assertEquals(listOf(7, 2), s.top.map { it.second })
        assertEquals("Sola", s.top[1].first.titulo)
    }

    @Test fun parseaSemanaConVotadasYSinAnterior() {
        token = "t"
        responde(200, """{"actual":{"id":"2026-W45","cierra":1,"canciones":[{"id":"A","titulo":"a","artista":null,"portada":null,"votada":true},{"id":"B","titulo":"b","artista":null,"portada":null,"votada":false}]},"anterior":null}""")
        val s = ok(api.semana())
        assertEquals(setOf("A"), s.votadas)
        assertNull(s.anteriorId); assertNull(s.cancionSemana); assertTrue(s.top.isEmpty())
    }

    @Test fun semanaCerradaSinVotos() {
        responde(200, """{"actual":{"id":"2026-W45","cierra":1,"canciones":[]},"anterior":{"id":"2026-W44","cancionSemana":null,"top":[]}}""")
        val s = ok(api.semana())
        assertEquals("2026-W44", s.anteriorId); assertNull(s.cancionSemana); assertTrue(s.top.isEmpty())
    }

    @Test fun parseaProponer() {
        responde(200, """{"cancion":$cancionJson,"yaEstaba":false,"propuestasRestantes":2}""")
        val p = ok(api.proponer("https://youtu.be/abc"))
        assertEquals("01JABC", p.cancion.id); assertFalse(p.yaEstaba); assertEquals(2, p.propuestasRestantes)
    }

    @Test fun parseaVoto() {
        responde(200, """{"votada":true,"votosRestantes":9}""")
        assertEquals(Voto(true, 9), ok(api.votar("C", true)))
        responde(200, """{"votada":false,"votosRestantes":10}""")
        assertEquals(Voto(false, 10), ok(api.votar("C", false)))
    }

    @Test fun parseaPaginaDePlaylists() {
        responde(200, """{"playlists":[{"id":"P1","nombre":"Para el coche","autor":"YoMismo","canciones":12,"guardados":3,"portada":"https://i.ytimg.com/a.jpg"},{"id":"P2","nombre":"Otra","autor":"Otro","canciones":1,"guardados":0,"portada":null}],"siguiente":"eyJwIjoxfQ"}""")
        val p = ok(api.playlists("recientes", null))
        assertEquals(2, p.lista.size); assertEquals("eyJwIjoxfQ", p.siguiente)
        assertEquals(PlaylistResumen("P1", "Para el coche", "YoMismo", 12, 3, "https://i.ytimg.com/a.jpg"), p.lista[0])
        assertNull(p.lista[1].portada)
        responde(200, """{"playlists":[],"siguiente":null}""")
        assertNull(ok(api.playlists("recientes", "x")).siguiente)
    }

    @Test fun parseaPlaylist() {
        responde(200, """{"id":"P1","nombre":"Para el coche","autor":"YoMismo","guardados":3,"canciones":[$cancionJson]}""")
        val p = ok(api.playlist("P1"))
        assertEquals("P1", p.id); assertEquals(3, p.guardados); assertEquals("Cancion X", p.canciones[0].titulo)
    }

    @Test fun parseaPublicadaYAmpliada() {
        responde(200, """{"id":"P9","canciones":8,"fallidas":["https://x/1","https://x/2"]}""")
        assertEquals(Publicada("P9", 8, listOf("https://x/1", "https://x/2")), ok(api.publicar("n", listOf("a"))))
        responde(200, """{"canciones":20,"fallidas":[]}""")
        assertEquals(Publicada("P9", 20, emptyList()), ok(api.anadirAPublicada("P9", listOf("a"))))
    }

    @Test fun parseaGuardarRetirarDenunciarYBorrar() {
        responde(200, """{"guardados":4}""")
        assertEquals(4, ok(api.guardarPublicada("P1")))
        responde(200, """{"ok":true}""")
        assertEquals(Unit, ok(api.retirar("P1")))
        assertEquals(Unit, ok(api.denunciar("playlist", "P1", "m")))
        assertEquals(Unit, ok(api.borrarCuenta()))
    }

    @Test fun ignoraCamposDeMas() {
        responde(200, """{"nombre":"YoMismo","diasCuenta":1,"puedeVotar":true,"votosRestantes":1,"propuestasRestantes":1,"nuevo":{"a":1}}""")
        assertEquals(1, ok(api.yo()).votosRestantes)
    }

    // ---- errores ----

    @Test fun errorConCodigo() {
        responde(409, """{"error":"no_coincide","intentosRestantes":2}""")
        val e = error(api.comprobar("R"))
        assertEquals("no_coincide", e.codigo); assertEquals(409, e.status)
        assertEquals(2, e.datos!!.getInt("intentosRestantes"))
    }

    @Test fun retoCaducadoSonDosCodigosYUnSoloSignificado() {
        responde(404, """{"error":"reto"}""")
        assertTrue(error(api.comprobar("R")).retoCaducado)
        responde(410, """{"error":"caducado"}""")
        assertTrue(error(api.comprobar("R")).retoCaducado)
        responde(404, """{"error":"no_existe"}""")
        assertFalse(error(api.comprobar("R")).retoCaducado)
    }

    @Test fun challengeSeDistingue() {
        responde(503, """{"error":"challenge"}""")
        assertTrue(error(api.reto("123456")).esChallenge)
        responde(409, """{"error":"no_coincide"}""")
        assertFalse(error(api.comprobar("R")).esChallenge)
    }

    @Test fun tokenInservibleSeDistingue() {
        token = "t"
        responde(401, """{"error":"token"}""")
        assertTrue(error(api.semana()).tokenInservible)
        responde(403, """{"error":"baneada"}""")
        assertTrue(error(api.yo()).tokenInservible)
        responde(403, """{"error":"antiguedad"}""")
        assertFalse(error(api.proponer("e")).tokenInservible)
        responde(429, """{"error":"tope"}""")
        assertFalse(error(api.votar("C", true)).tokenInservible)
    }

    @Test fun errorSinCuerpoLegible() {
        responde(502, "<html>Bad gateway</html>")
        val e = error(api.yo())
        assertEquals(502, e.status); assertEquals("http_502", e.codigo); assertNull(e.datos)
    }

    @Test fun exitoConCuerpoRoto() {
        responde(200, "no es json")
        val e = error(api.yo())
        assertEquals("respuesta", e.codigo); assertEquals(200, e.status)
    }

    @Test fun exitoSinCamposObligatorios() {
        responde(200, "{}")
        assertEquals("respuesta", error(api.reto("123456")).codigo)
    }

    @Test fun fallaDeRedNoLanza() {
        val rota = ApiMusica({ null }) { _, _, _, _ -> throw java.io.IOException("sin red") }
        val e = error(rota.semana())
        assertEquals("red", e.codigo); assertEquals(0, e.status)
    }
}
