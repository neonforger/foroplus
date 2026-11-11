package com.fcplus.forocoches

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BorrarCuentaMusicaTest {
    private val llamadas = mutableListOf<Pair<String, String>>()
    private val guion = ArrayDeque<Respuesta>()
    private val api = ApiMusica({ "tok" }) { m, u, _, _ ->
        llamadas.add(m to u.substringAfter(ServidorMusica.BASE))
        val r = guion.removeFirstOrNull() ?: Respuesta(200, """{"ok":true}""")
        if (r.status < 0) throw java.io.IOException("sin red")
        r
    }
    private val SIN_RED = Respuesta(-1, "")
    private fun fallo(status: Int, codigo: String) = Respuesta(status, """{"error":"$codigo"}""")

    @Test fun borrarLaCuentaQuitaToken() {
        val borrados = mutableListOf<String>()
        guion.add(Respuesta(200, """{"ok":true}"""))
        val r = BorrarCuentaMusica.borrar(api, "123456", { borrados.add(it) })
        assertEquals(BorrarCuentaMusica.Resultado.HECHO, r)
        assertEquals("DELETE" to "/v1/cuenta", llamadas[0])
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
        assertTrue(pl(null).deLaCuenta("222"))
        assertFalse(pl(null).deOtraCuenta("222"))
        assertFalse(Playlist("i", "n", emptyList(), 0, 0).deLaCuenta("111"))
    }
}
