package com.fcplus.forocoches

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

// Robolectric: así corre el org.json de Android (el de la JVM no, y difieren con los null).
@RunWith(RobolectricTestRunner::class)
class PaginaMas18Test {

    private fun ejemplo() = javaClass.classLoader!!.getResource("contrato/mas18-ejemplo.json").readText()
    private fun bien(json: String) = (PaginaMas18Parser.leer(json) as LecturaMas18.Bien).pagina

    /** 2026-10-08 20:30 en Madrid (18:30 UTC, horario de verano). */
    private val ahora = 1791484200000L

    @Test fun `lee el ejemplo del contrato, ignorando campos que no conoce`() {
        val p = bien(ejemplo())
        assertEquals(1, p.pagina)
        assertEquals(87, p.totalPaginas)
        assertEquals("2025-04", p.historicoHasta)
        assertEquals(2, p.hilos.size)
        val h = p.hilos[0]
        assertEquals(10829267L, h.tid)
        assertEquals(setOf("+18"), h.etiquetas)
        assertEquals("Otro", h.ultimoAutor)
        assertEquals(520320626L, h.ultimoPid)
        assertNull(p.hilos[1].ultimoFecha)
    }

    @Test fun `una version mayor no se pinta y lo dice`() {
        val r = PaginaMas18Parser.leer(ejemplo().replace("\"v\": 1", "\"v\": 2"))
        assertTrue(r is LecturaMas18.Mal)
        assertTrue((r as LecturaMas18.Mal).motivo.contains("actualiza", ignoreCase = true))
    }

    @Test fun `html de error de GitHub en vez de JSON`() {
        assertTrue(PaginaMas18Parser.leer("<html>404: Not Found</html>") is LecturaMas18.Mal)
        assertTrue(PaginaMas18Parser.leer("") is LecturaMas18.Mal)
    }

    @Test fun `total_paginas cero o negativo se queda en la pagina pedida`() {
        val p = bien(ejemplo().replace("\"total_paginas\": 87", "\"total_paginas\": 0"))
        assertEquals(1, p.totalPaginas)
    }

    @Test fun `hilo sin tid o sin titulo se descarta, no rompe la pagina`() {
        val roto = ejemplo().replace("\"tid\": 9876543,", "")
        assertEquals(1, bien(roto).hilos.size)
    }

    @Test fun `etiquetas vacias se recalculan del titulo`() {
        val p = bien(ejemplo().replace("\"etiquetas\": [\"+18\"]", "\"etiquetas\": []"))
        assertEquals(setOf("+18"), p.hilos[0].etiquetas)
    }

    @Test fun `autor null de JSON no sale como la palabra null`() {
        val j = """{"v":1,"pagina":1,"hilos":[{"tid":5,"titulo":"x +18","autor":null,
            "ultimo":{"autor":null,"fecha":"2026-10-08T17:55:00Z","pid":9}}]}"""
        val h = bien(j).hilos[0]
        assertEquals("", h.autor)
        assertEquals("", h.ultimoAutor)
    }

    @Test fun `fecha ISO con Z, con offset y con fracciones`() {
        fun f(s: String) = bien("""{"v":1,"generado":"$s","hilos":[]}""").generado
        val esperado = 1791484200000L
        assertEquals(esperado, f("2026-10-08T18:30:00Z"))
        assertEquals(esperado, f("2026-10-08T18:30:00+00:00"))
        assertEquals(esperado, f("2026-10-08T18:30:00.123Z"))
        assertEquals(esperado, f("2026-10-08T20:30:00+02:00"))
        assertEquals(0L, f("basura"))
    }

    @Test fun `etiquetas del JSON se normalizan y las desconocidas se recalculan del titulo`() {
        fun et(arr: String) = bien("""{"v":1,"hilos":[{"tid":5,"titulo":"algo +18","etiquetas":$arr}]}""").hilos[0].etiquetas
        assertEquals(setOf("+hd"), et("""["+HD"]"""))
        assertEquals(setOf("peña"), et("""["penya"]"""))
        assertEquals(setOf("+18"), et("""["+raro"]"""))
    }

    @Test fun `la version nueva se marca como tal`() {
        val r = PaginaMas18Parser.leer(ejemplo().replace("\"v\": 1", "\"v\": 2")) as LecturaMas18.Mal
        assertTrue(r.version)
        assertTrue(!(PaginaMas18Parser.leer("<html>") as LecturaMas18.Mal).version)
    }

    @Test fun `hora de hoy, como la escribe FC, en hora de Madrid`() {
        val h = bien(ejemplo()).hilos[0]
        assertEquals("Hoy 19:55", PaginaMas18Parser.horaFila(h, ahora))
    }

    @Test fun `el historico sin ultimo dice el mes de creacion`() {
        val h = bien(ejemplo()).hilos[1]
        assertEquals("creado abr 2024", PaginaMas18Parser.horaFila(h, ahora))
    }

    @Test fun `a ThreadItem - url del hilo y enlace al ultimo solo si hay pid`() {
        val p = bien(ejemplo())
        val t = PaginaMas18Parser.aThreadItem(p.hilos[0], ahora)
        assertEquals("10829267", t.tid)
        assertEquals("https://forocoches.com/foro/showthread.php?t=10829267", t.url)
        assertEquals("https://forocoches.com/foro/showthread.php?p=520320626#post520320626", t.lastPostUrl)
        assertEquals("49", t.replies)
        assertEquals("", PaginaMas18Parser.aThreadItem(p.hilos[1], ahora).lastPostUrl)
    }
}
