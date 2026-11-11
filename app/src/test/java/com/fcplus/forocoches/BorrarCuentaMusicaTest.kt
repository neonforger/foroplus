package com.fcplus.forocoches

import org.junit.Assert.assertEquals
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

}
