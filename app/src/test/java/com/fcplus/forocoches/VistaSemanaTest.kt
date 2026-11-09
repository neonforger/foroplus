package com.fcplus.forocoches

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VistaSemanaTest {
    private fun pub(id: String, yt: String? = "https://www.youtube.com/watch?v=dQw4w9WgXcQ", sp: String? = null) =
        CancionPub(id, "Tema $id", "Artista", null, yt, sp)

    private fun semana(vararg votadas: String) = Semana(
        "2026-W46", 0L, listOf(pub("A"), pub("B"), pub("C")), votadas.toSet(), null, null, emptyList()
    )

    private fun yo(votos: Int, puede: Boolean = true) = Yo("YoMismo", 400, puede, votos, 3)

    // ---- filas ----

    @Test fun sinYoNingunCorazonActivo() {
        val f = VistaSemana.filas(semana("A"), null)
        assertEquals(3, f.size)
        assertTrue(f.none { it.corazonActivo })
        // Lo votado se sigue marcando como votado (el token pudo valer aunque yo() fallara).
        assertTrue(f[0].votada)
    }

    @Test fun sinVotosSoloLasVotadasSePuedenQuitar() {
        val f = VistaSemana.filas(semana("B"), yo(0))
        assertEquals(listOf(false, true, false), f.map { it.corazonActivo })
        assertEquals(listOf(false, true, false), f.map { it.votada })
    }

    @Test fun sinPermisoDeVotoTodosInactivos() {
        val f = VistaSemana.filas(semana("A", "B"), yo(5, puede = false))
        assertTrue(f.none { it.corazonActivo })
    }

    @Test fun conVotosTodosActivosYSeMantieneElOrden() {
        val f = VistaSemana.filas(semana("C"), yo(4))
        assertTrue(f.all { it.corazonActivo })
        assertEquals(listOf("A", "B", "C"), f.map { it.cancion.id })
    }

    // ---- CancionPub -> Cancion ----

    @Test fun youtubeVaPrimero() {
        val c = VistaSemana.cancionDe(pub("A", sp = "https://open.spotify.com/track/4uLU6hMCjMI75M1A2tKUQC"))!!
        assertEquals(FuenteMusica.YOUTUBE, c.ref.fuente)
        assertEquals("Tema A", c.titulo)
        assertEquals("Artista", c.artista)
    }

    @Test fun siNoHayYoutubeSpotify() {
        val c = VistaSemana.cancionDe(pub("A", yt = null, sp = "https://open.spotify.com/track/4uLU6hMCjMI75M1A2tKUQC"))!!
        assertEquals(FuenteMusica.SPOTIFY, c.ref.fuente)
        assertEquals("4uLU6hMCjMI75M1A2tKUQC", c.ref.id)
    }

    @Test fun sinEnlaceUsableNoHayCancion() {
        assertNull(VistaSemana.cancionDe(pub("A", yt = null, sp = null)))
        assertNull(VistaSemana.cancionDe(pub("A", yt = "https://ejemplo.com/x", sp = null)))
    }

    // ---- textos ----

    private fun err(codigo: String, status: Int) = ApiMusica.R.Error(codigo, status, null)

    @Test fun antiguedadDiceNoventaDias() {
        val t = VistaSemana.textoError(err("antiguedad", 403), VistaSemana.Accion.VOTAR)
        assertTrue(t, t.contains("90 días"))
        assertTrue(t, t.contains("ForoCoches"))
    }

    @Test fun topesSegunLaAccion() {
        assertTrue(VistaSemana.textoError(err("tope", 429), VistaSemana.Accion.VOTAR).contains("10 votos"))
        assertTrue(VistaSemana.textoError(err("tope", 429), VistaSemana.Accion.PROPONER).contains("3 propuestas"))
    }

    @Test fun otrosErrores() {
        assertTrue(VistaSemana.textoError(err("enlace", 400), VistaSemana.Accion.PROPONER).contains("YouTube"))
        assertTrue(VistaSemana.textoError(err("no_es_cancion", 422), VistaSemana.Accion.PROPONER).contains("Spotify"))
        assertTrue(VistaSemana.textoError(err("limite", 429), VistaSemana.Accion.PROPONER).contains("rato"))
        assertTrue(VistaSemana.textoError(err("no_esta", 409), VistaSemana.Accion.VOTAR).contains("semana"))
        assertTrue(VistaSemana.textoError(err("red", 0), VistaSemana.Accion.VOTAR).contains("conectar"))
        assertTrue(VistaSemana.textoError(err("http_500", 500), VistaSemana.Accion.VOTAR).isNotEmpty())
    }

    @Test fun motivoDelCorazonApagado() {
        assertTrue(VistaSemana.motivoSinCorazon(yo(0)).contains("10 votos"))
        assertTrue(VistaSemana.motivoSinCorazon(yo(5, puede = false)).contains("90 días"))
        assertTrue(VistaSemana.motivoSinCorazon(null).isNotEmpty())
    }

    @Test fun restantes() {
        assertEquals("Te quedan 7 votos y 2 propuestas esta semana", VistaSemana.restantes(Yo("YoMismo", 400, true, 7, 2)))
        assertEquals("Te queda 1 voto y 1 propuesta esta semana", VistaSemana.restantes(Yo("YoMismo", 400, true, 1, 1)))
        assertEquals("Has gastado tus votos y tus propuestas de esta semana", VistaSemana.restantes(Yo("YoMismo", 400, true, 0, 0)))
    }

    // ---- carga: token malo ----

    private class Red(val respuestas: ArrayDeque<Respuesta>) {
        val llamadas = mutableListOf<Pair<String, Map<String, String>>>()
        fun fn(m: String, u: String, c: String?, h: Map<String, String>): Respuesta {
            llamadas.add(u to h); return respuestas.removeFirst()
        }
    }

    private val semanaJson = """{"actual":{"id":"2026-W46","cierra":1,"canciones":[{"id":"A","titulo":"T","artista":null,"portada":null,"votada":true}]},"anterior":null}"""
    private val yoJson = """{"nombre":"YoMismo","diasCuenta":400,"puedeVotar":true,"votosRestantes":9,"propuestasRestantes":3}"""

    @Test fun tokenMuertoSeBorraYSeReintentaSinEl() {
        var token: String? = "viejo"
        val red = Red(ArrayDeque(listOf(Respuesta(401, """{"error":"token"}"""), Respuesta(200, semanaJson))))
        val api = ApiMusica({ token }, red::fn)
        val r = CargaSemana.cargar(api, tieneToken = { token != null }, borrarToken = { token = null })
        val d = (r as ApiMusica.R.Ok).v
        assertNull(token)
        assertFalse(d.verificada)
        assertFalse(d.baneada)
        assertNull(d.yo)
        assertEquals(2, red.llamadas.size)
        assertTrue(red.llamadas[0].second.containsKey("Authorization"))
        assertFalse(red.llamadas[1].second.containsKey("Authorization"))
    }

    @Test fun baneadaSeBorraYSeAvisa() {
        var token: String? = "t"
        val red = Red(ArrayDeque(listOf(Respuesta(403, """{"error":"baneada"}"""), Respuesta(200, semanaJson))))
        val d = (CargaSemana.cargar(ApiMusica({ token }, red::fn), { token != null }, { token = null }) as ApiMusica.R.Ok).v
        assertNull(token)
        assertTrue(d.baneada)
        assertFalse(d.verificada)
    }

    @Test fun conTokenBuenoTraeYo() {
        val token = "bueno"
        val red = Red(ArrayDeque(listOf(Respuesta(200, semanaJson), Respuesta(200, yoJson))))
        val d = (CargaSemana.cargar(ApiMusica({ token }, red::fn), { true }, { error("no debe borrarse") }) as ApiMusica.R.Ok).v
        assertTrue(d.verificada)
        assertEquals(9, d.yo!!.votosRestantes)
        assertTrue(d.semana.votadas.contains("A"))
    }

    @Test fun sinTokenNoPideYo() {
        val red = Red(ArrayDeque(listOf(Respuesta(200, semanaJson))))
        val d = (CargaSemana.cargar(ApiMusica({ null }, red::fn), { false }, { }) as ApiMusica.R.Ok).v
        assertFalse(d.verificada)
        assertEquals(1, red.llamadas.size)
    }

    @Test fun siYoFallaSeSigueConLaSemana() {
        val red = Red(ArrayDeque(listOf(Respuesta(200, semanaJson), Respuesta(500, "{}"))))
        val d = (CargaSemana.cargar(ApiMusica({ "t" }, red::fn), { true }, { }) as ApiMusica.R.Ok).v
        assertTrue(d.verificada)
        assertNull(d.yo)
    }

    @Test fun yoConTokenMuertoTambienBorra() {
        var token: String? = "t"
        val red = Red(ArrayDeque(listOf(Respuesta(200, semanaJson), Respuesta(401, """{"error":"token"}"""))))
        val d = (CargaSemana.cargar(ApiMusica({ token }, red::fn), { token != null }, { token = null }) as ApiMusica.R.Ok).v
        assertNull(token)
        assertFalse(d.verificada)
    }

    @Test fun sinRedDevuelveElError() {
        val api = ApiMusica({ null }) { _, _, _, _ -> throw java.io.IOException("sin red") }
        val r = CargaSemana.cargar(api, { false }, { })
        assertEquals("red", (r as ApiMusica.R.Error).codigo)
    }

    // ---- qué hacer tras un error de votar/proponer ----

    @Test fun trasFalloBaneadaBorraYRecuerda() {
        val t = VistaSemana.trasFallo(err("baneada", 403), VistaSemana.Accion.VOTAR)
        assertTrue(t.borrarToken)
        assertTrue(t.baneada)
        assertTrue(t.recargar)
        assertTrue(t.texto, t.texto.contains("bloqueada"))
    }

    @Test fun trasFalloTokenBorraSinBanear() {
        val t = VistaSemana.trasFallo(err("token", 401), VistaSemana.Accion.PROPONER)
        assertTrue(t.borrarToken)
        assertFalse(t.baneada)
        assertTrue(t.recargar)
        assertTrue(t.texto, t.texto.contains("verificarte"))
    }

    @Test fun trasFalloNoEstaRecargaSinTocarElToken() {
        val t = VistaSemana.trasFallo(err("no_esta", 409), VistaSemana.Accion.VOTAR)
        assertFalse(t.borrarToken)
        assertFalse(t.baneada)
        assertTrue(t.recargar)
        assertTrue(t.texto.contains("semana"))
    }

    @Test fun trasFalloAntiguedadNoTocaNadaNiRecarga() {
        val t = VistaSemana.trasFallo(err("antiguedad", 403), VistaSemana.Accion.VOTAR)
        assertFalse(t.borrarToken)
        assertFalse(t.baneada)
        assertFalse(t.recargar)
    }

    // ---- baneada recordada ----

    @Test fun baneadaGuardadaSigueDiciendoseSinToken() {
        val red = Red(ArrayDeque(listOf(Respuesta(200, semanaJson))))
        val d = (CargaSemana.cargar(
            ApiMusica({ null }, red::fn), { false }, { }, baneadaGuardada = { true }, guardarBaneada = { }
        ) as ApiMusica.R.Ok).v
        assertTrue(d.baneada)
        assertFalse(d.verificada)
    }

    @Test fun baneadaNuevaSeGuarda() {
        var guardada = false
        val red = Red(ArrayDeque(listOf(Respuesta(403, """{"error":"baneada"}"""), Respuesta(200, semanaJson))))
        CargaSemana.cargar(
            ApiMusica({ "t" }, red::fn), { true }, { }, baneadaGuardada = { false }, guardarBaneada = { guardada = true }
        )
        assertTrue(guardada)
    }
}
