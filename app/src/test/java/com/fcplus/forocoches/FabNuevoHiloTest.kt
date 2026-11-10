package com.fcplus.forocoches

import com.fcplus.forocoches.FabNuevoHilo.Accion
import org.junit.Assert.assertEquals
import org.junit.Test

class FabNuevoHiloTest {
    @Test fun `quieta arriba extiende`() = assertEquals(Accion.EXTENDER, FabNuevoHilo.decidir(false, true))
    @Test fun `moviendose lejos del tope encoge`() = assertEquals(Accion.ENCOGER, FabNuevoHilo.decidir(true, false))
    @Test fun `quieta abajo no cambia nada`() = assertEquals(Accion.NADA, FabNuevoHilo.decidir(false, false))
    @Test fun `moviendose en el tope no cambia nada`() = assertEquals(Accion.NADA, FabNuevoHilo.decidir(true, true))
}
