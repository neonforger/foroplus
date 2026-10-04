package com.fcplus.forocoches

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Aritmética de la barra de desplazamiento arrastrable.
 *
 * Los números son los de una pantalla real: barra de 1.400 px de alto, pulgar de 156 px
 * (52dp a densidad 3) y 18 px de margen.
 */
class PosicionBarraTest {

    private val alto = 1400
    private val pulgar = 156
    private val margen = 18

    @Test
    fun `arriba del todo el pulgar se pega al margen, no al borde`() {
        assertEquals(margen, PosicionBarra.arriba(0f, alto, pulgar, margen))
    }

    @Test
    fun `abajo del todo el pulgar llega justo al final, sin salirse`() {
        val arriba = PosicionBarra.arriba(1f, alto, pulgar, margen)
        assertEquals(alto - margen, arriba + pulgar)
    }

    @Test
    fun `a mitad de pagina el pulgar esta a mitad de recorrido`() {
        val recorrido = PosicionBarra.recorrido(alto, pulgar, margen)
        assertEquals(margen + recorrido / 2, PosicionBarra.arriba(0.5f, alto, pulgar, margen))
    }

    @Test
    fun `arrastrar y soltar en el mismo sitio no mueve nada`() {
        // La ida y la vuelta tienen que ser la misma cuenta, o el pulgar se escaparía del dedo.
        for (f in listOf(0f, 0.25f, 0.5f, 0.75f, 1f)) {
            val arriba = PosicionBarra.arriba(f, alto, pulgar, margen)
            val centro = arriba + pulgar / 2f
            assertEquals(f, PosicionBarra.fraccionDe(centro, alto, pulgar, margen), 0.005f)
        }
    }

    @Test
    fun `el dedo por encima o por debajo de la barra no saca al pulgar de su sitio`() {
        assertEquals(0f, PosicionBarra.fraccionDe(-500f, alto, pulgar, margen), 0f)
        assertEquals(1f, PosicionBarra.fraccionDe(9999f, alto, pulgar, margen), 0f)
    }

    @Test
    fun `una barra mas corta que el pulgar no revienta ni divide por cero`() {
        assertEquals(0, PosicionBarra.recorrido(100, pulgar, margen))
        assertEquals(margen, PosicionBarra.arriba(0.5f, 100, pulgar, margen))
        assertEquals(0f, PosicionBarra.fraccionDe(50f, 100, pulgar, margen), 0f)
    }

    @Test
    fun `la fraccion se reparte entre los mensajes de la pagina`() {
        assertEquals(0, PosicionBarra.indice(0f, 30))
        assertEquals(29, PosicionBarra.indice(1f, 30))
        assertEquals(15, PosicionBarra.indice(0.5f, 30))
    }

    @Test
    fun `con un solo mensaje, o ninguno, no hay a donde saltar`() {
        assertEquals(0, PosicionBarra.indice(0.9f, 1))
        assertEquals(0, PosicionBarra.indice(0.9f, 0))
    }

    @Test
    fun `el indice nunca se sale de la lista`() {
        // Una fracción imposible no puede acabar en un scrollToPosition fuera de rango.
        assertEquals(29, PosicionBarra.indice(5f, 30))
        assertEquals(0, PosicionBarra.indice(-5f, 30))
    }

    @Test
    fun `el pulgar se agarra con holgura, pero fuera de el el toque no es de la barra`() {
        val arriba = PosicionBarra.arriba(0.5f, alto, pulgar, margen)
        assertTrue(PosicionBarra.tocaElPulgar(arriba + 10f, arriba, pulgar, 36))
        // Justo por encima, dentro de la holgura del dedo: sigue contando.
        assertTrue(PosicionBarra.tocaElPulgar(arriba - 20f, arriba, pulgar, 36))
        // Lejos: el toque tiene que llegar al mensaje que hay debajo, no comérselo la barra.
        assertFalse(PosicionBarra.tocaElPulgar(arriba - 300f, arriba, pulgar, 36))
        assertFalse(PosicionBarra.tocaElPulgar(arriba + pulgar + 300f, arriba, pulgar, 36))
    }
}
