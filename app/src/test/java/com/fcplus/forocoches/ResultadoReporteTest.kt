package com.fcplus.forocoches

import com.fcplus.forocoches.ResultadoReporte.Resultado
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * FC rechaza en el servidor un reporte sin comentario, con cualquier motivo (2026-11-04). La
 * app solo lo pedía con "Otros" y daba el reporte por enviado sin mirar la respuesta.
 */
class ResultadoReporteTest {

    @Test
    fun `sin motivo no se envia`() {
        assertEquals(ResultadoReporte.SIN_MOTIVO, ResultadoReporte.validar(null, "spam de referidos"))
    }

    @Test
    fun `cualquier motivo exige comentario, no solo Otros`() {
        for (motivo in listOf("+18", "Spam", "Troll", "Flood", "Contenido", "Otros")) {
            assertEquals(motivo, ResultadoReporte.SIN_COMENTARIO, ResultadoReporte.validar(motivo, ""))
            assertEquals(motivo, ResultadoReporte.SIN_COMENTARIO, ResultadoReporte.validar(motivo, "   \n "))
        }
    }

    @Test
    fun `con motivo y comentario se envia`() {
        assertNull(ResultadoReporte.validar("Spam", "Anuncia su web en cada mensaje"))
    }

    @Test
    fun `ir al hilo despues de enviar es exito`() {
        assertTrue(ResultadoReporte.esRedireccionDeExito(
            "https://forocoches.com/foro/showthread.php?p=521687103#post521687103"))
        assertEquals(Resultado.Enviado, ResultadoReporte.clasificar(
            "https://forocoches.com/foro/showthread.php?p=1#post1", "", false))
    }

    @Test
    fun `quedarse en report php no es exito`() {
        assertFalse(ResultadoReporte.esRedireccionDeExito("https://forocoches.com/foro/report.php?do=sendemail"))
        assertFalse(ResultadoReporte.esRedireccionDeExito(null))
    }

    @Test
    fun `la pagina de gracias es exito`() {
        assertEquals(Resultado.Enviado, ResultadoReporte.clasificar(
            "https://forocoches.com/foro/report.php?do=sendemail",
            "Gracias por reportar este mensaje. Serás redirigido…", false))
    }

    @Test
    fun `un error saca su linea, no el menu ni las cookies`() {
        val texto = """
            Los MEJORES PRECIOS en TELES
            Ya es BLACK FRIDAY en Samsung
            Debes indicar un motivo para el reporte.
            Legal
            Usamos cookies propias y de terceros para análisis y publicidad personalizada.
        """.trimIndent()
        assertEquals(
            Resultado.Rechazado("Debes indicar un motivo para el reporte."),
            ResultadoReporte.clasificar("https://forocoches.com/foro/report.php?do=sendemail", texto, false)
        )
    }

    @Test
    fun `el aviso real de FC sin motivo es un rechazo con su frase`() {
        // Medido el 2026-11-04: 200 en report.php?do=sendemail, sin redirección, con el aviso en
        // #st-error-main-error-message (lleva saltos y sangría del HTML).
        assertEquals(
            Resultado.Rechazado("Por favor proporciona una razón para reportar este mensaje."),
            ResultadoReporte.clasificar(
                "https://forocoches.com/foro/report.php?do=sendemail",
                "neonforger\n424 Posts\nMi perfil",
                false,
                "\n        Por favor proporciona una razón para reportar este mensaje.\n    "
            )
        )
    }

    @Test
    fun `el aviso de FC manda sobre un gracias que haya por la pagina`() {
        assertEquals(
            Resultado.Rechazado("Por favor proporciona una razón para reportar este mensaje."),
            ResultadoReporte.clasificar("https://forocoches.com/foro/report.php?do=sendemail",
                "Gracias por usar Forocoches", false,
                "Por favor proporciona una razón para reportar este mensaje.")
        )
    }

    @Test
    fun `sin aviso de FC se sigue buscando la linea del error`() {
        assertEquals(
            Resultado.Rechazado("Debes esperar 30 segundos."),
            ResultadoReporte.clasificar("https://forocoches.com/foro/report.php?do=sendemail",
                "Legal\nDebes esperar 30 segundos.", false, "")
        )
    }

    @Test
    fun `un error que no se reconoce da el generico`() {
        assertEquals(
            Resultado.Rechazado(ResultadoReporte.RECHAZADO_GENERICO),
            ResultadoReporte.clasificar("https://forocoches.com/foro/report.php?do=sendemail", "Legal\nContacto", false)
        )
    }

    @Test
    fun `una pagina vacia no se sabe`() {
        assertEquals(Resultado.Desconocido,
            ResultadoReporte.clasificar("https://forocoches.com/foro/report.php?do=sendemail", "  ", false))
    }
}
