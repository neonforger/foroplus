package com.fcplus.forocoches

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VistaFeedTest {
    private val min = 60_000L
    private val h = 60 * min

    @Test fun `hace 59 s es ahora`() = assertEquals("ahora", VistaFeed.hace(0, 59_000))
    @Test fun `hace 5 min`() = assertEquals("hace 5 min", VistaFeed.hace(0, 5 * min))
    @Test fun `hace 2 h`() = assertEquals("hace 2 h", VistaFeed.hace(0, 2 * h + 10 * min))
    @Test fun `hace con reloj adelantado es ahora`() = assertEquals("ahora", VistaFeed.hace(10_000, 0))

    @Test fun `queda 22 h`() {
        val q = VistaFeed.queda(22 * h + 20 * min, 0)
        assertEquals("22 h", q.texto)
        assertFalse(q.urgente)
    }

    @Test fun `queda se va en 40 min urgente`() {
        val q = VistaFeed.queda(40 * min + 5_000, 0)
        assertEquals("se va en 40 min", q.texto)
        assertTrue(q.urgente)
    }

    @Test fun `queda ya pasada no baja de 1 min`() {
        val q = VistaFeed.queda(0, 5 * min)
        assertEquals("se va en 1 min", q.texto)
        assertTrue(q.urgente)
    }

    @Test fun `motivoTexto 101 es LARGO`() = assertEquals(MotivoTexto.LARGO, VistaFeed.motivoTexto("a".repeat(101)))
    @Test fun `motivoTexto 100 vale`() = assertNull(VistaFeed.motivoTexto("a".repeat(100)))
    @Test fun `motivoTexto vacio con espacios es VACIO`() = assertEquals(MotivoTexto.VACIO, VistaFeed.motivoTexto("   "))
    @Test fun `motivoTexto con www es ENLACE`() = assertEquals(MotivoTexto.ENLACE, VistaFeed.motivoTexto("mira www.x"))
    @Test fun `motivoTexto con dominio es ENLACE`() = assertEquals(MotivoTexto.ENLACE, VistaFeed.motivoTexto("en Foo.COM suena"))
    @Test fun `motivoTexto normal vale`() = assertNull(VistaFeed.motivoTexto("Para el domingo de resaca"))

    @Test fun `estadoVacio sin red pide reintentar`() {
        assertTrue(VistaFeed.estadoVacio(OrdenFeed.RECIENTES, false).contains("Reintentar"))
    }

    @Test fun `estadoVacio con red distingue ordenes`() {
        val a = VistaFeed.estadoVacio(OrdenFeed.RECIENTES, true)
        val b = VistaFeed.estadoVacio(OrdenFeed.QUERIDAS, true)
        assertTrue(a.isNotBlank() && b.isNotBlank() && a != b)
    }

    private fun err(c: String, s: Int = 0) = ApiMusica.R.Error(c, s, null)

    @Test fun `textoError limite no habla de tope ni de numeros`() {
        assertEquals(
            "Por hoy ya has compartido muchas canciones; mañana puedes seguir.",
            VistaFeed.textoError(err("limite", 429))
        )
    }

    @Test fun `textoError cubre todos los codigos con texto propio`() {
        val codigos = listOf(
            "red", "respuesta", "token", "baneada", "antiguedad", "texto", "oculta", "propia",
            "no_existe", "limite", "no_es_cancion", "no_encontrada", "cuerpo", "deezer"
        )
        val textos = codigos.map { VistaFeed.textoError(err(it)) }
        assertTrue(textos.all { it.isNotBlank() })
        assertEquals(textos.size, textos.toSet().size)
        assertTrue(textos.none { it.contains("Deezer", ignoreCase = true) })
    }

    @Test fun `el tope de corazones tiene su texto, sin cifras, y lo demas es como textoError`() {
        val t = VistaFeed.textoErrorCorazon(err("limite", 429))
        assertTrue(t.contains("corazones") && t.none { it.isDigit() })
        assertEquals(VistaFeed.textoError(err("propia")), VistaFeed.textoErrorCorazon(err("propia")))
    }
}
