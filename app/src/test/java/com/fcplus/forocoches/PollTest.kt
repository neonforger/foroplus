package com.fcplus.forocoches

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** org.json es de Android: hace falta Robolectric para ejercitarlo en la JVM. */
@RunWith(RobolectricTestRunner::class)
class PollTest {

    private fun poll(json: String): Poll? = parsePoll(JSONObject(json))

    // ── parsePoll ────────────────────────────────────────────────────────────

    @Test
    fun `hilo sin encuesta`() {
        assertNull(parsePoll(null))
    }

    @Test
    fun `sin array de opciones no hay encuesta`() {
        assertNull(poll("""{"id":"7","question":"¿Cuál?"}"""))
    }

    @Test
    fun `opciones sin texto se descartan y si no queda ninguna no hay encuesta`() {
        assertNull(poll("""{"id":"7","options":[{"num":"1","text":"  "},{"num":"2"}]}"""))
    }

    @Test
    fun `basura dentro del array no rompe el parseo`() {
        val p = poll("""{"id":"7","options":["x",null,{"num":"1","text":"A","votes":3}]}""")!!
        assertEquals(1, p.options.size)
        assertEquals("A", p.options[0].text)
    }

    @Test
    fun `respeta el porcentaje que imprime FC`() {
        val p = poll(
            """{"id":"7","question":"¿Cuál?","totalVotes":1204,"hasResults":true,"options":[
                 {"num":"1","text":"A","votes":748,"pct":62.13},
                 {"num":"2","text":"B","votes":373,"pct":30.98}]}"""
        )!!
        assertEquals(62.13, p.options[0].pct, 0.001)
        assertEquals(30.98, p.options[1].pct, 0.001)
    }

    @Test
    fun `calcula el porcentaje cuando FC no lo da`() {
        val p = poll(
            """{"id":"7","totalVotes":200,"options":[
                 {"num":"1","text":"A","votes":150},
                 {"num":"2","text":"B","votes":50}]}"""
        )!!
        assertEquals(75.0, p.options[0].pct, 0.001)
        assertEquals(25.0, p.options[1].pct, 0.001)
    }

    @Test
    fun `sin total usa la suma de votos como base`() {
        val p = poll(
            """{"id":"7","options":[
                 {"num":"1","text":"A","votes":3},
                 {"num":"2","text":"B","votes":1}]}"""
        )!!
        assertEquals(75.0, p.options[0].pct, 0.001)
        assertEquals(4, p.options[0].votes + p.options[1].votes)
    }

    @Test
    fun `encuesta sin un solo voto no divide por cero`() {
        val p = poll(
            """{"id":"7","canVote":true,"options":[
                 {"num":"1","text":"A","votes":0},{"num":"2","text":"B","votes":0}]}"""
        )!!
        assertEquals(0.0, p.options[0].pct, 0.001)
        assertEquals(0, p.totalVotes)
        assertFalse(p.hasResults)
    }

    @Test
    fun `multirrespuesta usa los votantes como base y puede pasar del cien por cien`() {
        // 100 votantes marcando varias opciones: la suma de votos (170) supera al total.
        val p = poll(
            """{"id":"7","multiple":true,"totalVotes":100,"hasResults":true,"options":[
                 {"num":"1","text":"A","votes":90},
                 {"num":"2","text":"B","votes":80}]}"""
        )!!
        assertTrue(p.multiple)
        assertEquals(90.0, p.options[0].pct, 0.001)
        assertEquals(80.0, p.options[1].pct, 0.001)
    }

    @Test
    fun `encuesta cerrada nunca es votable`() {
        val p = poll("""{"id":"7","closed":true,"canVote":true,"options":[{"num":"1","text":"A"}]}""")!!
        assertTrue(p.closed)
        assertFalse(p.canVote)
    }

    @Test
    fun `sin id no se puede votar porque no hay POST que montar`() {
        val p = poll("""{"canVote":true,"options":[{"num":"1","text":"A"}]}""")!!
        assertFalse(p.canVote)
    }

    @Test
    fun `marca la opcion votada por el usuario`() {
        // FC marca TU opción con <em> en los resultados; extractor.js lo traduce a mine.
        val p = poll(
            """{"id":"7","voted":true,"hasResults":true,"totalVotes":533,"options":[
                 {"num":"1","text":"Comprado al contado","votes":322},
                 {"num":"2","text":"Financiado","votes":149,"mine":true},
                 {"num":"3","text":"Leasing","votes":11}]}"""
        )!!
        assertEquals(listOf("Financiado"), p.options.filter { it.mine }.map { it.text })
    }

    @Test
    fun `sin marca de voto ninguna opcion es tuya`() {
        val p = poll("""{"id":"7","options":[{"num":"1","text":"A"},{"num":"2","text":"B"}]}""")!!
        assertTrue(p.options.none { it.mine })
    }

    @Test
    fun `votos negativos se saturan a cero`() {
        val p = poll("""{"id":"7","totalVotes":-5,"options":[{"num":"1","text":"A","votes":-3}]}""")!!
        assertEquals(0, p.options[0].votes)
        assertEquals(0, p.totalVotes)
    }

    // ── Formato ──────────────────────────────────────────────────────────────

    @Test
    fun `miles con punto como en el foro`() {
        assertEquals("0", formatVotes(0))
        assertEquals("7", formatVotes(7))
        assertEquals("999", formatVotes(999))
        assertEquals("1.204", formatVotes(1204))
        assertEquals("12.345", formatVotes(12345))
        assertEquals("1.234.567", formatVotes(1234567))
    }

    @Test
    fun `etiqueta de votos en singular y plural`() {
        assertEquals("sin votos", votesLabel(0))
        assertEquals("1 voto", votesLabel(1))
        assertEquals("2 votos", votesLabel(2))
        assertEquals("1.204 votos", votesLabel(1204))
    }

    @Test
    fun `resumen de la barra`() {
        val abierta = poll(
            """{"id":"7","totalVotes":1204,"hasResults":true,
                "options":[{"num":"1","text":"A","votes":1204}]}"""
        )!!
        assertEquals("Encuesta · 1.204 votos", pollSummary(abierta))
        val cerrada = poll("""{"id":"7","closed":true,"totalVotes":3,"options":[{"num":"1","text":"A"}]}""")!!
        assertEquals("Encuesta cerrada · 3 votos", pollSummary(cerrada))
    }

    @Test
    fun `la barra no canta sin votos cuando FC oculta los resultados`() {
        // Estado REAL de FC al no haber votado: form sin resultados. La encuesta puede tener
        // cientos de votos; decir "sin votos" seria mentir.
        val votable = poll(
            """{"id":"7","canVote":true,"hasResults":false,"totalVotes":0,
                "options":[{"num":"1","text":"A"},{"num":"2","text":"B"}]}"""
        )!!
        assertEquals("Encuesta · toca para votar", pollSummary(votable))

        // Ni votable ni con resultados (p. ej. invitado): solo la etiqueta, sin cifras.
        val opaca = poll("""{"id":"7","hasResults":false,"options":[{"num":"1","text":"A"}]}""")!!
        assertEquals("Encuesta", pollSummary(opaca))
    }

    @Test
    fun `porcentaje corto no aplasta las opciones minoritarias`() {
        assertEquals("0%", pctText(0.0))
        assertEquals("<1%", pctText(0.4))
        assertEquals("62%", pctText(62.13))
        assertEquals("63%", pctText(62.5))
        assertEquals("100%", pctText(100.0))
    }

    @Test
    fun `fraccion de barra acotada`() {
        assertEquals(0f, barFraction(0.0), 0.0001f)
        assertEquals(0.5f, barFraction(50.0), 0.0001f)
        assertEquals(1f, barFraction(100.0), 0.0001f)
        assertEquals(1f, barFraction(140.0), 0.0001f)
        assertEquals(0f, barFraction(-3.0), 0.0001f)
    }

    @Test
    fun `pie segun el estado de la encuesta`() {
        val puedeVotar = poll(
            """{"id":"7","canVote":true,"hasResults":false,"options":[{"num":"1","text":"A"}]}"""
        )!!
        assertEquals("Vota para ver los resultados", pollFooter(puedeVotar))

        val votada = poll(
            """{"id":"7","voted":true,"hasResults":true,"totalVotes":1204,
                "options":[{"num":"1","text":"A","votes":1204}]}"""
        )!!
        assertEquals("Ya has votado · 1.204 votos", pollFooter(votada))

        val cerrada = poll(
            """{"id":"7","closed":true,"totalVotes":9,"options":[{"num":"1","text":"A","votes":9}]}"""
        )!!
        assertEquals("Encuesta cerrada · 9 votos", pollFooter(cerrada))

        val soloLectura = poll(
            """{"id":"7","hasResults":true,"totalVotes":9,"options":[{"num":"1","text":"A","votes":9}]}"""
        )!!
        assertEquals("9 votos", pollFooter(soloLectura))
    }

    // ── Integración con el payload del hilo ──────────────────────────────────

    @Test
    fun `el payload del hilo arrastra la encuesta`() {
        val t = parseThreadPayload(
            """{"url":"u","tid":"1","title":"t","page":1,"pageCount":1,
                "posts":[{"pid":"9","author":"shur","html":"hola"}],
                "poll":{"id":"7","question":"¿Cuál?","totalVotes":2,
                        "options":[{"num":"1","text":"A","votes":2}]}}"""
        )!!
        assertEquals("7", t.poll!!.id)
        assertEquals("¿Cuál?", t.poll!!.question)
    }

    @Test
    fun `un hilo normal sigue sin encuesta`() {
        val t = parseThreadPayload(
            """{"url":"u","tid":"1","title":"t","page":1,"pageCount":1,
                "posts":[{"pid":"9","author":"shur","html":"hola"}]}"""
        )!!
        assertNull(t.poll)
    }
}
