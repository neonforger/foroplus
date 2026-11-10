package com.fcplus.forocoches

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNull
import org.junit.Test

class PublicarPlaylistTest {
    private fun enlaces(n: Int) = (1 until n + 1).map { "https://www.youtube.com/watch?v=v$it" }

    // ---- tandas (puro) ----

    @Test fun veinticincoEnTandasDeDiez() {
        assertEquals(listOf(10, 10, 5), PublicarPlaylist.tandas(enlaces(25), 10).map { it.size })
    }

    @Test fun lasTandasSiguenLaConstante() {
        val t = PublicarPlaylist.tandas(enlaces(25))
        assertTrue(t.dropLast(1).all { it.size == PublicarPlaylist.TANDA })
        assertEquals(25, t.sumOf { it.size })
    }

    @Test fun sinEnlacesNoHayTandas() {
        assertEquals(emptyList<List<String>>(), PublicarPlaylist.tandas(emptyList()))
    }

    @Test fun lasTandasConservanElOrden() {
        assertEquals(enlaces(23), PublicarPlaylist.tandas(enlaces(23), 10).flatten())
    }

    // ---- publicar con un servidor de mentira ----

    private class Llamada(val metodo: String, val ruta: String, val cuerpo: String?, val cabeceras: Map<String, String>)

    private val llamadas = mutableListOf<Llamada>()
    private val guion = ArrayDeque<Respuesta>()
    private val pausas = mutableListOf<Long>()
    private val api = ApiMusica({ "tok" }) { m, u, c, h ->
        llamadas.add(Llamada(m, u.substringAfter("workers.dev"), c, h))
        val r = guion.removeFirstOrNull() ?: Respuesta(200, """{"id":"PUB1","canciones":1,"fallidas":[]}""")
        if (r.status < 0) throw java.io.IOException("sin red")
        r
    }

    private val SIN_RED = Respuesta(-1, "")
    private fun ok(canciones: Int, fallidas: String = "") =
        Respuesta(200, """{"id":"PUB1","canciones":$canciones,"fallidas":[$fallidas]}""")
    private fun fallo(status: Int, codigo: String) = Respuesta(status, """{"error":"$codigo"}""")

    private fun publicar(n: Int, tam: Int = 10, creada: MutableList<String> = mutableListOf()) =
        PublicarPlaylist.publicar(api, "Prueba", enlaces(n), tam, { creada.add(it) }, { pausas.add(it) })

    @Test fun laPrimeraTandaCreaYLasDemasAmplian() {
        guion.add(ok(10)); guion.add(ok(20)); guion.add(ok(25))
        val creada = mutableListOf<String>()
        val r = publicar(25, 10, creada)
        assertEquals(listOf("POST /v1/playlists", "POST /v1/playlists/PUB1/canciones", "POST /v1/playlists/PUB1/canciones"),
            llamadas.map { it.metodo + " " + it.ruta })
        assertEquals(listOf("PUB1"), creada)
        r as PublicarPlaylist.Resultado.Hecho
        assertEquals("PUB1", r.id)
        assertEquals(25, r.canciones)
    }

    @Test fun cadaTandaLlevaSusEnlacesEnOrden() {
        guion.add(ok(10)); guion.add(ok(12))
        publicar(12, 10)
        val a = org.json.JSONObject(llamadas[0].cuerpo!!)
        val b = org.json.JSONObject(llamadas[1].cuerpo!!)
        assertEquals(10, a.getJSONArray("enlaces").length())
        assertEquals("Prueba", a.getString("nombre"))
        assertEquals(2, b.getJSONArray("enlaces").length())
        assertEquals(enlaces(12)[10], b.getJSONArray("enlaces").getString(0))
    }

    @Test fun lasFallidasDeTodasLasTandasSeJuntan() {
        guion.add(ok(2, "\"a\"")); guion.add(ok(3, "\"b\",\"c\""))
        val r = publicar(5, 3) as PublicarPlaylist.Resultado.Hecho
        assertEquals(listOf("a", "b", "c"), r.fallidas)
    }

    @Test fun publicarLaTandaDeCreacionNoSeReintentaNunca() {
        // Un tiempo agotado puede haber creado ya la playlist: repetir la duplicaría (el servidor no
        // impide dos con el mismo nombre). Se avisa de que no se sabe y se deja decidir a quien publica.
        guion.add(SIN_RED)
        val r = publicar(5, 3)
        assertEquals(1, llamadas.size)
        r as PublicarPlaylist.Resultado.Fallo
        assertTrue(r.dudosa)
    }

    @Test fun unErrorDelServidorEnLaCreacionNoEsDudoso() {
        guion.add(fallo(422, "nombre"))
        val r = publicar(5, 3) as PublicarPlaylist.Resultado.Fallo
        assertFalse(r.dudosa)
        assertEquals("nombre", r.error.codigo)
    }

    @Test fun unCincoXXAlCrearEsDudosoYNoSeReintenta() {
        // Workers 1102 / Cloudflare 5xx: el servidor pudo crearla antes de morir.
        guion.add(Respuesta(503, "<html>error</html>"))
        val r = publicar(5, 3) as PublicarPlaylist.Resultado.Fallo
        assertEquals(1, llamadas.size)
        assertTrue(r.dudosa)
    }

    @Test fun unaRespuestaQueNoSeEntiendeAlCrearEsDudosa() {
        guion.add(Respuesta(200, "no es json"))
        val r = publicar(5, 3) as PublicarPlaylist.Resultado.Fallo
        assertEquals("respuesta", r.error.codigo)
        assertTrue(r.dudosa)
    }

    @Test fun unLimiteAlCrearNoEsDudoso() {
        guion.add(fallo(429, "limite"))
        val r = publicar(5, 3) as PublicarPlaylist.Resultado.Fallo
        assertFalse(r.dudosa)
    }

    @Test fun cancelarParaEntreTandasYDejaLaMarca() {
        guion.add(ok(3)); guion.add(ok(6)); guion.add(ok(7))
        val creada = mutableListOf<String>()
        var tandasVistas = 0
        val r = PublicarPlaylist.publicar(api, "P", enlaces(7), 3, { creada.add(it) }, { },
            alTanda = { _, _ -> tandasVistas++ }, cancelado = { llamadas.size >= 2 })
        assertEquals(2, llamadas.size) // nunca a mitad de una petición, ni una tercera
        assertEquals(listOf("PUB1"), creada)
        r as PublicarPlaylist.Resultado.Parcial
        assertEquals(6, r.canciones)
        assertEquals("cancelado", r.error.codigo)
    }

    @Test fun ampliarSiSePuedeReintentarPorqueNoDuplica() {
        guion.add(ok(3)); guion.add(SIN_RED); guion.add(ok(6))
        val r = publicar(6, 3)
        assertTrue(r is PublicarPlaylist.Resultado.Hecho)
        assertEquals(3, llamadas.size)
        assertEquals(1, pausas.size)
    }

    @Test fun ocupadaSeReintentaYAcabaDandoParcial() {
        guion.add(ok(3)); repeat(PublicarPlaylist.INTENTOS) { guion.add(fallo(409, "ocupada")) }
        val r = publicar(6, 3) as PublicarPlaylist.Resultado.Parcial
        assertEquals("PUB1", r.id)
        assertEquals(3, r.canciones)
        assertEquals("ocupada", r.error.codigo)
        assertEquals(1 + PublicarPlaylist.INTENTOS, llamadas.size)
    }

    @Test fun llenaOLimiteNoSeReintentan() {
        guion.add(ok(3)); guion.add(fallo(429, "limite"))
        val r = publicar(9, 3) as PublicarPlaylist.Resultado.Parcial
        assertEquals("limite", r.error.codigo)
        assertEquals(2, llamadas.size)
    }

    @Test fun elProgresoSeCuentaPorTandas() {
        guion.add(ok(3)); guion.add(ok(6)); guion.add(ok(7))
        val vistos = mutableListOf<Pair<Int, Int>>()
        PublicarPlaylist.publicar(api, "P", enlaces(7), 3, {}, { }, { h, t -> vistos.add(h to t) })
        assertEquals(listOf(0 to 3, 1 to 3, 2 to 3, 3 to 3), vistos)
    }

    @Test fun lasLlamadasLargasPidenMasTiempo() {
        guion.add(ok(3)); guion.add(ok(6))
        publicar(6, 3)
        assertTrue(llamadas.all { (it.cabeceras[ApiMusica.CABECERA_TIMEOUT]?.toLong() ?: 0L) >= 30_000L })
    }

    // ---- borrar la cuenta ----

    @Test fun borrarLaCuentaQuitaToken() {
        val borrados = mutableListOf<String>()
        guion.add(Respuesta(200, """{"ok":true}"""))
        val r = BorrarCuentaMusica.borrar(api, "123456", { borrados.add(it) })
        assertEquals(BorrarCuentaMusica.Resultado.HECHO, r)
        assertEquals("DELETE /v1/cuenta", llamadas[0].metodo + " " + llamadas[0].ruta)
        assertEquals(listOf("123456"), borrados)
    }

    // Un 401 es también un token CADUCADO (1 año): la cuenta puede seguir en el servidor, así que no es "hecho".
    @Test fun conTokenMuertoNoSeDaPorBorradaPeroSeTiraElTokenLocal() {
        val borrados = mutableListOf<String>()
        guion.add(fallo(401, "token"))
        assertEquals(BorrarCuentaMusica.Resultado.VERIFICAR, BorrarCuentaMusica.borrar(api, "9", { borrados.add(it) }))
        assertEquals(listOf("9"), borrados)
    }

    @Test fun sinRedNoSeToca() {
        val borrados = mutableListOf<String>()
        guion.add(SIN_RED)
        assertEquals(BorrarCuentaMusica.Resultado.FALLO, BorrarCuentaMusica.borrar(api, "9", { borrados.add(it) }))
        assertTrue(borrados.isEmpty())
    }
}

class TextosShursTest {
    private fun e(codigo: String, status: Int = 400) = ApiMusica.R.Error(codigo, status, null)

    @Test fun cadaCodigoTieneSuTexto() {
        assertTrue(TextosShurs.error(e("red", 0)).contains("conectar"))
        assertTrue(TextosShurs.error(e("nombre", 422)).contains("nombre"))
        assertTrue(TextosShurs.error(e("tope", 429)).contains("50"))
        assertTrue(TextosShurs.error(e("no_existe", 404)).contains("ya no existe"))
        assertTrue(TextosShurs.error(e("token", 401)).contains("verificación"))
        assertTrue(TextosShurs.error(e("baneada", 403)).contains("bloqueada"))
        assertTrue(TextosShurs.error(e("rarisimo", 500)).contains("Prueba en un rato"))
    }

    @Test fun elLimiteDeDenunciasDiceCinco() {
        assertTrue(TextosShurs.errorDenuncia(e("limite", 429)).contains("5 denuncias"))
        assertTrue(TextosShurs.error(e("limite", 429)).contains("seguidas"))
    }
}

class QuitarMarcasPublicadasTest {
    @get:org.junit.Rule val carpeta = org.junit.rules.TemporaryFolder()

    @Test fun borrarLaCuentaQuitaSoloLasMarcasDeEsaCuentaYNadaMas() {
        var hora = 1L
        val a = AlmacenMusica(carpeta.root) { hora++ }
        val p1 = a.crearPlaylist("Una")!!
        val p2 = a.crearPlaylist("Dos")!!
        val p3 = a.crearPlaylist("Tres")!!
        val p4 = a.crearPlaylist("Vieja")!!
        a.marcarPublicada(p1.id, "x1", "111"); a.marcarPublicada(p3.id, "x3", "222"); a.marcarPublicada(p4.id, "x4")
        BorrarCuentaMusica.quitarMarcas(a, "111")
        val e = a.estado()
        assertNull(e.playlists[0].publicadaId)
        assertNull(e.playlists[0].publicadaPor)
        assertEquals("x3", e.playlists[2].publicadaId)  // de otra cuenta
        assertEquals("x4", e.playlists[3].publicadaId)  // marca antigua sin dueña: no se toca a ciegas
        assertEquals(listOf("Una", "Dos", "Tres", "Vieja"), e.playlists.map { it.nombre })
        assertEquals(p2.id, e.playlists[1].id)
    }

    private fun pl(por: String?) = Playlist("i", "n", emptyList(), 0, 0, "pub", por)

    @Test fun laMarcaSoloEsDeLaCuentaQueLaPublico() {
        assertTrue(pl("111").deLaCuenta("111"))
        assertFalse(pl("111").deLaCuenta("222"))
        assertTrue(pl("111").deOtraCuenta("222"))
        assertFalse(pl("111").deOtraCuenta("111"))
        // Marca antigua (sin dueña): se enseña a quien sea, pero solo se actúa si el servidor confirma.
        assertTrue(pl(null).deLaCuenta("222"))
        assertFalse(pl(null).deOtraCuenta("222"))
        assertFalse(Playlist("i", "n", emptyList(), 0, 0).deLaCuenta("111"))
    }

    @Test fun retirarUn404SoloQuitaLaMarcaSiSabemosQueEraDeEstaCuenta() {
        val e404 = ApiMusica.R.Error("no_existe", 404, null)
        assertEquals(RetirarPlaylist.Veredicto.RETIRADA, RetirarPlaylist.veredicto(e404, pl("111"), "111"))
        assertEquals(RetirarPlaylist.Veredicto.NO_ES_DE_ESTA_CUENTA, RetirarPlaylist.veredicto(e404, pl(null), "111"))
        assertEquals(RetirarPlaylist.Veredicto.NO_ES_DE_ESTA_CUENTA, RetirarPlaylist.veredicto(e404, pl("222"), "111"))
        assertEquals(RetirarPlaylist.Veredicto.ERROR, RetirarPlaylist.veredicto(ApiMusica.R.Error("red", 0, null), pl("111"), "111"))
    }
}
