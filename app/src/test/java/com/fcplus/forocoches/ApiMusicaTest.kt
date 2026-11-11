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

    private val cancionJson = """{"id":"01JABC","titulo":"Cancion X","artista":"Alguien","portada":"https://i.ytimg.com/vi/abc/hq.jpg","youtube":"https://www.youtube.com/watch?v=abc","spotify":"https://open.spotify.com/track/xyz","deezer":3135556}"""
    private val cancionSinNada = """{"id":"01JDEF","titulo":"Sola","artista":null,"portada":null}"""
    private fun itemJson(id: String = "P1", extra: String = ""): String =
        """{"id":"$id","autor":"YoMismo","texto":"Para el coche","creada":1762560000000,"caduca":1762646400000,"corazones":3$extra,"cancion":$cancionJson}"""

    @Test fun sinCookieNunca() {
        token = "tok"
        api.reto("123456"); api.comprobar("R"); api.yo(); api.feed(OrdenFeed.RECIENTES, null)
        api.publicar("hola", OrigenCancion.Deezer(1)); api.borrarPublicacion("P"); api.corazon("P", true); api.corazon("P", false)
        api.quienes("P"); api.mias(); api.denunciar("publicacion", "P", "m"); api.borrarCuenta()
        assertEquals(12, llamadas.size)
        for (l in llamadas) assertTrue(l.cabeceras.keys.none { it.equals("Cookie", true) })
    }

    @Test fun authSoloConToken() {
        token = null
        api.yo(); api.feed(OrdenFeed.RECIENTES, null)
        assertTrue(llamadas.all { !it.cabeceras.containsKey("Authorization") })
        llamadas.clear()
        token = "abc"
        api.yo(); api.feed(OrdenFeed.QUERIDAS, null); api.publicar("t", OrigenCancion.Enlace("e")); api.borrarCuenta()
        assertTrue(llamadas.all { it.cabeceras["Authorization"] == "Bearer abc" })
    }

    @Test fun lasRutasPublicasNoMandanElToken() {
        token = "abc"
        api.reto("123456"); api.comprobar("R")
        assertTrue(llamadas.all { !it.cabeceras.containsKey("Authorization") })
    }

    @Test fun metodosYUrls() {
        token = "t"
        api.reto("123456"); api.comprobar("R"); api.yo()
        api.feed(OrdenFeed.QUERIDAS, "cur+sor/="); api.feed(OrdenFeed.RECIENTES, null)
        api.publicar("t", OrigenCancion.Deezer(5)); api.borrarPublicacion("P1")
        api.corazon("P1", true); api.corazon("P1", false); api.quienes("P1"); api.mias()
        api.denunciar("cancion", "C", "m"); api.borrarCuenta()
        val b = ServidorMusica.BASE
        val esperado = listOf(
            "POST $b/v1/verificacion/reto", "POST $b/v1/verificacion/comprobar", "GET $b/v1/yo",
            "GET $b/v1/feed?orden=queridas&cursor=cur%2Bsor%2F%3D", "GET $b/v1/feed?orden=recientes",
            "POST $b/v1/publicaciones", "DELETE $b/v1/publicaciones/P1",
            "POST $b/v1/publicaciones/P1/corazon", "DELETE $b/v1/publicaciones/P1/corazon",
            "GET $b/v1/publicaciones/P1/corazones", "GET $b/v1/publicaciones/mias",
            "POST $b/v1/denuncias", "DELETE $b/v1/cuenta"
        )
        assertEquals(esperado, llamadas.map { "${it.metodo} ${it.url}" })
        assertTrue(llamadas.all { ServidorMusica.aceptada(it.url) })
    }

    @Test fun cuerposJson() {
        api.reto("123456"); api.denunciar("publicacion", "P", "mal")
        assertEquals("123456", JSONObject(llamadas[0].cuerpo!!).getString("uid"))
        val d = JSONObject(llamadas[1].cuerpo!!)
        assertEquals("publicacion", d.getString("tipo")); assertEquals("P", d.getString("objetivo")); assertEquals("mal", d.getString("motivo"))
        assertEquals("application/json", llamadas[0].cabeceras["Content-Type"])
    }

    @Test fun `publicar manda deezer o enlace, nunca los dos`() {
        responde(201, """{"publicacion":${itemJson()}}""")
        api.publicar("Para el coche", OrigenCancion.Deezer(3135556))
        api.publicar("Otra", OrigenCancion.Enlace("https://youtu.be/abc"))
        val d = JSONObject(llamadas[0].cuerpo!!)
        assertEquals("Para el coche", d.getString("texto")); assertEquals(3135556L, d.getLong("deezer")); assertFalse(d.has("enlace"))
        val e = JSONObject(llamadas[1].cuerpo!!)
        assertEquals("https://youtu.be/abc", e.getString("enlace")); assertFalse(e.has("deezer"))
        assertEquals(ApiMusica.TIMEOUT_LARGO_MS.toString(), llamadas[0].cabeceras[ApiMusica.CABECERA_TIMEOUT])
    }

    @Test fun `publicar con 422 texto da Error texto 422`() {
        responde(422, """{"error":"texto"}""")
        val e = error(api.publicar("https://x.com", OrigenCancion.Deezer(1)))
        assertEquals("texto", e.codigo); assertEquals(422, e.status)
    }

    @Test fun publicarParseaLaPublicacion() {
        responde(201, """{"publicacion":${itemJson(extra = ""","corazon":false,"mia":true""")}}""")
        val p = ok(api.publicar("Para el coche", OrigenCancion.Deezer(3135556)))
        assertEquals("P1", p.id); assertTrue(p.mia); assertEquals(3135556L, p.cancion.deezer)
    }

    @Test fun `corazon DELETE no lleva cuerpo`() {
        responde(200, """{"corazones":2,"corazon":false}""")
        assertEquals(EstadoCorazon(2, false), ok(api.corazon("P1", false)))
        assertEquals("DELETE", llamadas[0].metodo); assertNull(llamadas[0].cuerpo)
        responde(200, """{"corazones":3,"corazon":true}""")
        assertEquals(EstadoCorazon(3, true), ok(api.corazon("P1", true)))
        assertEquals("POST", llamadas[1].metodo)
    }

    @Test fun `quienes parsea nombres`() {
        responde(200, """{"nombres":["Ana","Luis"]}""")
        assertEquals(listOf("Ana", "Luis"), ok(api.quienes("P1")))
        responde(200, """{"nombres":[]}""")
        assertTrue(ok(api.quienes("P1")).isEmpty())
    }

    @Test fun borrarPublicacionAceptaUn204SinCuerpo() {
        responde(204, "")
        assertEquals(Unit, ok(api.borrarPublicacion("P1")))
    }

    @Test fun miasParseaLasPublicaciones() {
        responde(200, """{"items":[${itemJson("A", ""","corazon":false,"mia":true""")},${itemJson("B")}]}""")
        val m = ok(api.mias())
        assertEquals(listOf("A", "B"), m.map { it.id }); assertTrue(m[0].mia); assertFalse(m[1].mia)
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

    @Test fun `yo parsea publicacionesRestantes`() {
        responde(200, """{"nombre":"YoMismo","diasCuenta":162,"puedePublicar":true,"publicacionesRestantes":4}""")
        assertEquals(Yo("YoMismo", 162, true, 4), ok(api.yo()))
        responde(200, """{"nombre":"YoMismo","diasCuenta":null,"puedePublicar":false,"publicacionesRestantes":6}""")
        assertNull(ok(api.yo()).diasCuenta)
    }

    @Test fun `feed parsea items, cursor y masQuerida`() {
        token = "t"
        responde(200, """{"items":[${itemJson("A", ""","corazon":true,"mia":false""")},{"id":"B","autor":"Otro","texto":"x","creada":5,"caduca":9,"corazones":0,"cancion":$cancionSinNada}],
            "cursor":"abc","masQuerida":{"semana":"2026-W44","autor":"Ana","texto":"la mejor","corazones":12,"cancion":$cancionJson}}""")
        val p = ok(api.feed(OrdenFeed.RECIENTES, null))
        assertEquals(2, p.items.size); assertEquals("abc", p.cursor)
        val a = p.items[0]
        assertEquals("YoMismo", a.autor); assertEquals(3, a.corazones); assertTrue(a.corazon); assertFalse(a.mia)
        assertEquals(1762560000000L, a.creada); assertEquals(1762646400000L, a.caduca)
        assertEquals(CancionFeed("01JABC", "Cancion X", "Alguien", "https://i.ytimg.com/vi/abc/hq.jpg",
            "https://open.spotify.com/track/xyz", "https://www.youtube.com/watch?v=abc", 3135556L), a.cancion)
        val b = p.items[1]
        assertFalse(b.corazon); assertFalse(b.mia); assertNull(b.cancion.deezer); assertNull(b.cancion.youtube); assertNull(b.cancion.artista)
        assertEquals("Ana", p.masQuerida!!.autor); assertEquals(12, p.masQuerida!!.corazones)
        responde(200, """{"items":[],"cursor":null,"masQuerida":null}""")
        val v = ok(api.feed(OrdenFeed.QUERIDAS, "x"))
        assertTrue(v.items.isEmpty()); assertNull(v.cursor); assertNull(v.masQuerida)
    }

    @Test fun `feed con token muerto repite sin token`() {
        token = "viejo"
        val respuestas = ArrayDeque(listOf(Respuesta(401, """{"error":"token"}"""), Respuesta(200, """{"items":[],"cursor":null,"masQuerida":null}""")))
        val a = ApiMusica({ token }) { m, u, c, h -> llamadas.add(Llamada(m, u, c, h)); respuestas.removeFirst() }
        assertTrue(a.feed(OrdenFeed.RECIENTES, null) is ApiMusica.R.Ok)
        assertEquals(2, llamadas.size)
        assertEquals("Bearer viejo", llamadas[0].cabeceras["Authorization"])
        assertFalse(llamadas[1].cabeceras.containsKey("Authorization"))
        assertEquals(llamadas[0].url, llamadas[1].url)
    }

    @Test fun denunciarYBorrarCuentaDanUnit() {
        responde(200, """{"ok":true}""")
        assertEquals(Unit, ok(api.denunciar("publicacion", "P1", "m")))
        assertEquals(Unit, ok(api.borrarCuenta()))
    }

    @Test fun ignoraCamposDeMas() {
        responde(200, """{"nombre":"YoMismo","diasCuenta":1,"puedePublicar":true,"publicacionesRestantes":1,"nuevo":{"a":1}}""")
        assertEquals(1, ok(api.yo()).publicacionesRestantes)
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
        assertTrue(error(api.yo()).tokenInservible)
        responde(403, """{"error":"baneada"}""")
        assertTrue(error(api.yo()).tokenInservible)
        responde(403, """{"error":"antiguedad"}""")
        assertFalse(error(api.publicar("t", OrigenCancion.Deezer(1))).tokenInservible)
        responde(429, """{"error":"tope"}""")
        assertFalse(error(api.corazon("C", true)).tokenInservible)
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
        val e = error(rota.feed(OrdenFeed.RECIENTES, null))
        assertEquals("red", e.codigo); assertEquals(0, e.status)
    }
}
