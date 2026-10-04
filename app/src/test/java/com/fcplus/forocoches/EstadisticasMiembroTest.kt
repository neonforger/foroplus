package com.fcplus.forocoches

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class EstadisticasMiembroTest {

    /** 27 de agosto de 2026, para que las cuentas de días sean fijas. */
    private val hoy = Calendar.getInstance().apply { clear(); set(2026, 7, 27) }.timeInMillis

    @Test
    fun `el punto de FC es separador de miles, no decimal`() {
        // Gotcha 11: "1.680" son mil seiscientos ochenta. Leerlo como 1,68 daría una media
        // de mensajes al día absurda justo en los usuarios más activos.
        assertEquals(7253, EstadisticasMiembro.numero("7.253"))
        assertEquals(1680, EstadisticasMiembro.numero("1.680"))
        assertEquals(38, EstadisticasMiembro.numero("38"))
    }

    @Test
    fun `un texto que no es un numero no cuela`() {
        assertNull(EstadisticasMiembro.numero(""))
        assertNull(EstadisticasMiembro.numero("muchos"))
        assertNull(EstadisticasMiembro.numero("12a"))
    }

    @Test
    fun `la fecha de registro de FC se entiende con su mes abreviado`() {
        // Formato real, sondeado el 2026-08-27 en la ficha de cosworth66.
        val dias = EstadisticasMiembro.diasDesde("30-nov-2004", hoy)!!
        assertTrue("deberían ser unos 21 años", dias in 7900..8000)
    }

    @Test
    fun `una fecha ilegible no inventa nada`() {
        assertNull(EstadisticasMiembro.diasDesde("", hoy))
        assertNull(EstadisticasMiembro.diasDesde("ayer", hoy))
        assertNull(EstadisticasMiembro.diasDesde("30/11/2004", hoy))
        assertNull(EstadisticasMiembro.diasDesde("30-xxx-2004", hoy))
    }

    @Test
    fun `registrarse hoy no divide por cero`() {
        assertEquals(1, EstadisticasMiembro.diasDesde("27-ago-2026", hoy))
    }

    @Test
    fun `el veterano y el sospechoso se distinguen de un vistazo`() {
        // El caso real que motivó esto: los dos tienen "muchos" mensajes, y solo la media
        // al día dice cuál de los dos huele a bot.
        val veterano = EstadisticasMiembro.linea("7.253", "38", "30-nov-2004", hoy)
        val sospechoso = EstadisticasMiembro.linea("400", "70", "01-ago-2026", hoy)

        assertTrue(veterano, veterano.contains("7.253 mensajes"))
        // La fecha ENTERA, como en FC: la pidió Juan (Telegram 2211) — con solo mes y año no
        // se distingue al que se registró ayer del que lleva tres semanas.
        assertTrue(veterano, veterano.contains("desde 30-nov-2004"))
        assertTrue(veterano, veterano.contains("0,9 al día"))

        assertTrue(sospechoso, sospechoso.contains("desde 01-ago-2026"))
        assertTrue(sospechoso, sospechoso.contains("15,4 al día"))
    }

    @Test
    fun `sin datos no se pinta una linea con huecos`() {
        assertEquals("", EstadisticasMiembro.linea("", "", "", hoy))
    }

    @Test
    fun `con datos a medias se enseña lo que haya`() {
        // De invitado, o si FC cambia la ficha, puede faltar alguno. Mejor tres cuartos de
        // línea que ninguna.
        val soloMensajes = EstadisticasMiembro.linea("120", "", "", hoy)
        assertEquals("120 mensajes", soloMensajes)

        val sinFecha = EstadisticasMiembro.linea("120", "4", "", hoy)
        assertEquals("120 mensajes · 4 hilos", sinFecha)
    }

    @Test
    fun `el rango va delante de las estadisticas`() {
        assertEquals("Miembro · 7.253 mensajes", EstadisticasMiembro.conRango("Miembro", "7.253 mensajes"))
    }

    @Test
    fun `sin rango o sin estadisticas no quedan separadores sueltos`() {
        assertEquals("7.253 mensajes", EstadisticasMiembro.conRango("  ", "7.253 mensajes"))
        assertEquals("Usuario", EstadisticasMiembro.conRango("Usuario", ""))
        assertEquals("", EstadisticasMiembro.conRango("", ""))
    }
}
