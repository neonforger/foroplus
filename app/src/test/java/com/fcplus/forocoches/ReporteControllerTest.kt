package com.fcplus.forocoches

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * El motivo que elige el usuario se traduce al valor del radio `tipo` del formulario REAL de
 * report.php (verificado por CDP). Si el mapa se descuadra, el reporte sale con otro motivo
 * sin que nada falle a la vista.
 */
class ReporteControllerTest {

    @Test
    fun `cada motivo lleva el tipo del formulario de FC`() {
        assertEquals("6", ReporteController.tipoDe("+18"))
        assertEquals("1", ReporteController.tipoDe("Spam"))
        assertEquals("2", ReporteController.tipoDe("Troll"))
        assertEquals("5", ReporteController.tipoDe("Flood"))
        assertEquals("3", ReporteController.tipoDe("Contenido"))
        assertEquals("4", ReporteController.tipoDe("Otros"))
    }

    @Test
    fun `un motivo desconocido cae en Otros`() {
        assertEquals("4", ReporteController.tipoDe("??"))
    }
}
