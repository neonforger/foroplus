package com.fcplus.forocoches

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger

/**
 * La red de verdad de [ApiMusica] contra un servidor HTTP mínimo hecho con ServerSocket en
 * 127.0.0.1 (puerto efímero). com.sun.net.httpserver no está en el classpath de estos tests.
 */
class RedRealMusicaTest {
    private lateinit var srv: java.net.ServerSocket
    private val visto = java.util.Collections.synchronizedList(mutableListOf<Map<String, String>>())
    private val cuerposVistos = java.util.Collections.synchronizedList(mutableListOf<Int>())
    private val peticiones = AtomicInteger()
    private val destinoRedireccion = AtomicInteger()
    private lateinit var base: String
    private val todo: (String) -> Boolean = { true }
    private val crlf = "\r\n"

    private fun atender(s: java.net.Socket) {
        s.use {
            val ent = it.getInputStream()
            fun linea(): String {
                val sb = StringBuilder()
                while (true) { val c = ent.read(); if (c < 0 || c == 10) break; if (c != 13) sb.append(c.toChar()) }
                return sb.toString()
            }
            val primera = linea().split(" ")
            if (primera.size < 2) return
            val cab = mutableMapOf<String, String>()
            while (true) {
                val l = linea(); if (l.isEmpty()) break
                cab[l.substringBefore(":").lowercase()] = l.substringAfter(":").trim()
            }
            val n = cab["content-length"]?.toIntOrNull() ?: 0
            var leidos = 0
            while (leidos < n) { if (ent.read() < 0) break; leidos++ }
            val sal = it.getOutputStream()
            fun responder(status: String, extra: String, cuerpo: ByteArray) {
                sal.write(("HTTP/1.1 $status" + crlf + "Content-Length: ${cuerpo.size}" + crlf + "Connection: close" + crlf + extra + crlf).toByteArray())
                sal.write(cuerpo); sal.flush()
            }
            when (primera[1]) {
                "/eco" -> { peticiones.incrementAndGet(); visto.add(cab); cuerposVistos.add(n); responder("200 OK", "", """{"ok":true}""".toByteArray()) }
                "/redir" -> { peticiones.incrementAndGet(); responder("302 Found", "Location: $base/destino" + crlf, ByteArray(0)) }
                "/destino" -> { destinoRedireccion.incrementAndGet(); responder("200 OK", "", ByteArray(0)) }
                "/error" -> { peticiones.incrementAndGet(); responder("429 Too Many Requests", "", """{"error":"tope"}""".toByteArray()) }
                "/grande" -> {
                    peticiones.incrementAndGet()
                    val total = ApiMusica.MAX_CUERPO + 1_000_000
                    sal.write(("HTTP/1.1 200 OK" + crlf + "Content-Length: $total" + crlf + "Connection: close" + crlf + crlf).toByteArray())
                    val t = ByteArray(65536) { 97 }
                    try {
                        var f = 0
                        while (f < total) { val k = minOf(t.size, total - f); sal.write(t, 0, k); f += k }
                    } catch (e: IOException) { /* el cliente cortó */ }
                }
            }
        }
    }

    @Before fun arrancar() {
        srv = java.net.ServerSocket(0, 50, java.net.InetAddress.getByName("127.0.0.1"))
        base = "http://127.0.0.1:${srv.localPort}"
        Thread {
            while (!srv.isClosed) {
                try {
                    val c = srv.accept()
                    Thread { try { atender(c) } catch (e: Exception) { } }.start()
                } catch (e: Exception) { }
            }
        }.apply { isDaemon = true }.start()
    }

    @After fun parar() = srv.close()

    @Test fun laCabeceraInternaDeTiempoNoSaleALaRedYNoHayCookie() {
        val r = ApiMusica.redConGuarda("POST", "$base/eco", "{}",
            mapOf(ApiMusica.CABECERA_TIMEOUT to "30000", "Cookie" to "bbuserid=1", "Authorization" to "Bearer t"), todo)
        assertEquals(200, r.status)
        assertFalse(visto[0].containsKey(ApiMusica.CABECERA_TIMEOUT.lowercase()))
        assertFalse(visto[0].containsKey("cookie"))
        assertEquals("Bearer t", visto[0]["authorization"])
    }

    @Test fun nuncaLlegaUnaCabeceraCookie() {
        val r = ApiMusica.redConGuarda("GET", "$base/eco", null, mapOf("Cookie" to "bbuserid=1", "cookie" to "x", "Accept" to "application/json"), todo)
        assertEquals(200, r.status)
        assertFalse(visto[0].containsKey("cookie"))
        assertEquals("application/json", visto[0]["accept"])
    }

    @Test fun authorizationSoloSiSeLaDan() {
        ApiMusica.redConGuarda("GET", "$base/eco", null, emptyMap(), todo)
        ApiMusica.redConGuarda("GET", "$base/eco", null, mapOf("Authorization" to "Bearer abc"), todo)
        assertNull(visto[0]["authorization"])
        assertEquals("Bearer abc", visto[1]["authorization"])
    }

    @Test fun unaRedireccionNoSeSigue() {
        val r = ApiMusica.redConGuarda("GET", "$base/redir", null, mapOf("Authorization" to "Bearer abc"), todo)
        assertEquals(302, r.status)
        assertEquals(0, destinoRedireccion.get())
    }

    @Test fun unErrorDevuelveElCuerpoDelFlujoDeError() {
        val r = ApiMusica.redConGuarda("GET", "$base/error", null, emptyMap(), todo)
        assertEquals(429, r.status)
        assertEquals("""{"error":"tope"}""", r.cuerpo)
    }

    @Test fun unCuerpoGrandeSeCortaSinFallar() {
        val r = ApiMusica.redConGuarda("GET", "$base/grande", null, emptyMap(), todo)
        assertEquals(200, r.status)
        assertEquals(ApiMusica.MAX_CUERPO, r.cuerpo.length)
    }

    @Test fun elPostLlevaCuerpoYElDeleteNunca() {
        ApiMusica.redConGuarda("POST", "$base/eco", """{"a":1}""", mapOf("Content-Type" to "application/json"), todo)
        val d = ApiMusica.redConGuarda("DELETE", "$base/eco", """{"a":1}""", mapOf("Content-Type" to "application/json"), todo)
        assertEquals(200, d.status)
        assertEquals(7, cuerposVistos[0])
        assertEquals(0, cuerposVistos[1])
    }

    @Test fun unaUrlRechazadaNoConectaNunca() {
        val r = runCatching { ApiMusica.redConGuarda("GET", "$base/eco", null, emptyMap()) { false } }
        assertTrue(r.exceptionOrNull() is IllegalArgumentException)
        // Y la guarda de verdad rechaza lo que no es la BASE de Música.
        assertTrue(runCatching { ApiMusica.redReal("GET", "$base/eco", null, emptyMap()) }.isFailure)
        assertEquals(0, peticiones.get())
    }

    @Test fun elFalloDeDesafioDe401SeConvierteEnToken() {
        val e = IOException("No authentication challenges found")
        assertEquals(Respuesta(401, """{"error":"token"}"""), ApiMusica.respuestaDeFallo(e, true))
        assertNull(ApiMusica.respuestaDeFallo(e, false)) // sin token mandado no es "tu token no vale"
        assertNull(ApiMusica.respuestaDeFallo(IOException("timeout"), true))
        assertNull(ApiMusica.respuestaDeFallo(IOException(), true))
    }
}
