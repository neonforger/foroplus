package com.fcplus.forocoches

import org.junit.Assert.assertEquals
import org.junit.Test

class InsigniasTest {

    @Test
    fun `sin nada visto se ensena lo que diga FC`() {
        assertEquals(3, Insignias.aMostrar(deFc = 3, yaVisto = 0))
    }

    @Test
    fun `la cita fantasma deja de dar la lata en cuanto la miras`() {
        // FC dice 1, entras y no hay nada (filtrada por ignorados): al salir se apunta 1.
        assertEquals(0, Insignias.aMostrar(deFc = 1, yaVisto = 1))
    }

    @Test
    fun `una cita nueva por encima de la fantasma SI se ensena`() {
        assertEquals(1, Insignias.aMostrar(deFc = 2, yaVisto = 1))
    }

    @Test
    fun `leerlas desde el ordenador no ciega el contador`() {
        // La marca se guarda ajustada en CADA actualizacion, asi que esto son dos pasos.
        // 1) Viste 5 y las lees en el PC: FC baja a 0 y la marca baja con el.
        val marca = Insignias.vistoAjustado(deFc = 0, yaVisto = 5)
        assertEquals(0, marca)
        // 2) Llega una cita nueva: se tiene que ver, aunque antes hubieras visto cinco.
        assertEquals(1, Insignias.aMostrar(deFc = 1, yaVisto = marca))
    }

    @Test
    fun `datos absurdos no pintan numeros raros`() {
        assertEquals(0, Insignias.aMostrar(deFc = 0, yaVisto = 0))
        assertEquals(0, Insignias.aMostrar(deFc = -1, yaVisto = 0))
    }
}
