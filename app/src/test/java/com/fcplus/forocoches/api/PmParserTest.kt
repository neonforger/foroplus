package com.fcplus.forocoches.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests de [PmParser] contra HTML real de FC
 * (`pm_inbox_mobile.html` = bandeja, `pm_show_mobile.html` = detalle de un MP).
 */
class PmParserTest {

    private fun fixture(name: String): String =
        javaClass.classLoader!!.getResourceAsStream(name)!!
            .bufferedReader(Charsets.UTF_8).readText()

    private val inbox by lazy { PmParser.parseInbox(fixture("pm_inbox_mobile.html")) }

    @Test
    fun `parsea las conversaciones de la bandeja`() {
        assertEquals(11, inbox.size)
    }

    @Test
    fun `el MP mas reciente trae remitente, asunto y fecha`() {
        val pm = inbox.first()
        assertEquals(52799018L, pm.pmid)
        assertEquals("Hola", pm.subject)
        assertEquals("neonforger", pm.sender)
        assertEquals(911128L, pm.senderId)
        assertTrue("fecha vacía", pm.dateText.isNotEmpty())
    }

    @Test
    fun `parsea remitentes distintos (no solo el propio usuario)`() {
        val malak = inbox.firstOrNull { it.sender == "MalakianRocks" }
        assertTrue("no se encontró el MP de otro usuario", malak != null)
        assertEquals(797899L, malak!!.senderId)
    }

    @Test
    fun `toda conversacion tiene remitente y asunto`() {
        for (pm in inbox) {
            assertTrue("asunto vacío en ${pm.pmid}", pm.subject.isNotEmpty())
            assertTrue("remitente vacío en ${pm.pmid}", pm.sender.isNotEmpty())
            assertTrue("pmid inválido", pm.pmid > 0)
        }
    }

    @Test
    fun `extrae el cuerpo del MP desde showpm`() {
        val body = PmParser.parseBody(fixture("pm_show_mobile.html"))
        assertEquals("X", body)
    }

    @Test
    fun `skin antiguo - parsea la bandeja (remitente antes del asunto)`() {
        val old = PmParser.parseInbox(fixture("pm_inbox_old.html"))
        assertTrue("bandeja vieja vacía", old.isNotEmpty())
        for (pm in old) {
            assertTrue("remitente vacío en ${pm.pmid}", pm.sender.isNotEmpty())
            assertTrue("asunto vacío en ${pm.pmid}", pm.subject.isNotEmpty())
        }
        assertTrue("no se halló pmid conocido", old.any { it.pmid == 52799055L })
    }

    /**
     * El bug que reportó un tester el 2026-08-12: la notificación de un MP nuevo le nombraba
     * a quien le había mandado el ANTERIOR. Si una fila no trae remitente, el emparejamiento
     * no puede tirar del vecino: en una bandeja ordenada de nuevo a viejo, el vecino es
     * literalmente el mensaje anterior.
     */
    @Test
    fun `una fila sin remitente NO hereda el de la fila siguiente`() {
        val html = """
            <div>
              <div class="fila">
                <a href="private.php?do=showpm&pmid=111"><strong>Sin remitente</strong></a>
              </div>
              <div class="fila">
                <a href="private.php?do=showpm&pmid=222"><strong>Con remitente</strong></a>
                <span onclick="window.location='member.php?u=42';">Vecino</span>
              </div>
            </div>
        """.trimIndent()
        val pms = PmParser.parseInbox(html)
        assertEquals(2, pms.size)
        val huerfano = pms.first { it.pmid == 111L }
        assertEquals("", huerfano.sender)
        assertEquals(0L, huerfano.senderId)
        // Y el de al lado conserva el suyo.
        val vecino = pms.first { it.pmid == 222L }
        assertEquals("Vecino", vecino.sender)
        assertEquals(42L, vecino.senderId)
    }
}
