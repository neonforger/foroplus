package com.fcplus.forocoches

import org.junit.Assert.assertEquals
import org.junit.Test

class ErrorPublicarTest {

    /** El caso real capturado del foro el 2026-08-15 (dos respuestas seguidas). */
    private val FLOOD_REAL =
        "Los siguientes errores ocurrieron al enviar este mensaje: Debes esperar al menos 30 " +
            "segundos entre cada envío de nuevos mensajes. Inténtalo de nuevo en 6 segundos."

    @Test
    fun `el limite entre mensajes se resume en lo que falta`() {
        assertEquals("Espera 6 s: FC solo deja publicar cada 30 s", ErrorPublicar.corto(FLOOD_REAL))
    }

    @Test
    fun `sin la cuenta atras se dice al menos cada cuanto`() {
        assertEquals(
            "FC solo deja publicar cada 30 s",
            ErrorPublicar.corto("Debes esperar al menos 30 segundos entre cada envío.")
        )
    }

    @Test
    fun `sin el intervalo se dice solo lo que falta`() {
        assertEquals(
            "Espera 12 s para publicar otra vez",
            ErrorPublicar.corto("Inténtalo de nuevo en 12 segundos.")
        )
    }

    @Test
    fun `http 200 ya no se le ensena a nadie`() {
        assertEquals(ErrorPublicar.GENERICO, ErrorPublicar.corto("http 200"))
    }

    @Test
    fun `los codigos internos se traducen`() {
        assertEquals(ErrorPublicar.GENERICO, ErrorPublicar.corto("no-form"))
        assertEquals(ErrorPublicar.GENERICO, ErrorPublicar.corto(""))
        assertEquals(ErrorPublicar.GENERICO, ErrorPublicar.corto("TypeError: Failed to fetch"))
    }

    @Test
    fun `un mensaje propio de FC se respeta entero`() {
        val dup = "El mensaje que has introducido es idéntico al que acabas de enviar."
        assertEquals(dup, ErrorPublicar.corto(dup))
    }

    @Test
    fun `los saltos de linea del html no se cuelan`() {
        assertEquals(
            "Espera 6 s: FC solo deja publicar cada 30 s",
            ErrorPublicar.corto("Debes esperar al menos 30\n  segundos.\n\tInténtalo de nuevo en 6 segundos.")
        )
    }

    @Test
    fun `un texto larguisimo se corta sin dejar el aviso ilegible`() {
        val largo = "FC dice que no " + "y ".repeat(200)
        val r = ErrorPublicar.corto(largo)
        // Se corta con puntos suspensivos y nunca deja un espacio colgando antes de ellos.
        assertEquals(true, r.length <= 140)
        assertEquals(true, r.endsWith("…"))
        assertEquals(false, r.endsWith(" …"))
    }
}
