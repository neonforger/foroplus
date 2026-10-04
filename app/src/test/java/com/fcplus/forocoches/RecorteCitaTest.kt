package com.fcplus.forocoches

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RecorteCitaTest {

    @Test
    fun `una cita corta no se toca`() {
        // La mediana real son 102 caracteres: la inmensa mayoria no debe cambiar en nada.
        val t = "Fulano dijo: pues yo no lo veo asi"
        assertEquals(t.length, RecorteCita.corte(t, 0, t.length, 600))
    }

    @Test
    fun `una cita larga se corta y no parte una palabra`() {
        val t = "palabra ".repeat(200)          // 1600 caracteres
        val c = RecorteCita.corte(t, 0, t.length, 600)
        assertTrue("deberia cortar", c < t.length)
        assertTrue("no puede pasarse del limite", c <= 600)
        // El caracter anterior al corte es un espacio: no queda "pala" colgando.
        assertTrue(t[c - 1].isWhitespace())
    }

    @Test
    fun `un texto sin espacios se corta igual`() {
        // Un enlace larguisimo o japones: retroceder buscando un espacio se comeria la cita
        // entera y dejaria un recuadro vacio, que es peor que partir por donde sea.
        val t = "x".repeat(1000)
        assertEquals(600, RecorteCita.corte(t, 0, t.length, 600))
    }

    @Test
    fun `el corte respeta el tramo que se le pasa`() {
        // La cita no empieza en 0: delante va el texto de quien responde.
        val t = "antes de la cita " + "palabra ".repeat(200)
        val ini = 17
        val c = RecorteCita.corte(t, ini, t.length, 600)
        assertTrue(c > ini)
        assertTrue(c - ini <= 600)
    }

    @Test
    fun `no se comen los saltos que cierran la cita`() {
        // El fondo de la cita es un estilo de parrafo: sin el salto final se derrama sobre la
        // respuesta de abajo.
        val t = "cita\n\n"
        assertEquals(4, RecorteCita.sinSaltosFinales(t, 0, t.length))
        // Y un tramo que es SOLO saltos no puede devolver un final por delante del principio.
        assertEquals(0, RecorteCita.sinSaltosFinales("\n\n", 0, 2))
    }

    @Test
    fun `el enlace tocable esta dentro del texto que se anade`() {
        assertTrue(RecorteCita.INICIO_ENLACE > 0)
        assertEquals("Ver cita completa", RecorteCita.MAS.substring(RecorteCita.INICIO_ENLACE))
    }
}

/** Lo que se a\u00f1adi\u00f3 al arreglar "una cita con fotos repite el mensaje entero". */
class RecorteCitaImagenesTest {

    /** Cita corta de texto con fotos intercaladas: el U+FFFC de cada una. */
    private fun conFotos(texto: String) = texto.replace('#', '\uFFFC')

    @Test
    fun `una foto ocupa lo que muchas lineas de texto`() {
        // Foto de 418px con renglones de 54px: ~7,7 lineas * 44 caracteres.
        assertEquals(340, RecorteCita.costeDeImagen(418, 54))
    }

    @Test
    fun `un smiley del renglon no cuenta como una foto`() {
        // 54px de alto en un renglon de 54: una linea.
        assertEquals(44, RecorteCita.costeDeImagen(54, 54))
    }

    @Test
    fun `medidas absurdas no disparan el coste`() {
        assertEquals(1, RecorteCita.costeDeImagen(0, 54))
        assertEquals(1, RecorteCita.costeDeImagen(418, 0))
    }

    @Test
    fun `sin costes la regla es la de siempre`() {
        val t = "a".repeat(1000)
        assertEquals(1000, RecorteCita.corte(t, 0, 1000, limite = 2000))
        // Sin espacios manda el corte seco, en el limite exacto.
        assertEquals(600, RecorteCita.corte(t, 0, 1000))
    }

    @Test
    fun `una cita de cuatro fotos y poco texto SI se pliega`() {
        // El fallo reportado: cuatro fotos son cuatro caracteres y nunca llegaban al limite.
        val t = conFotos("mira # esto # y # esto # ")
        val costes = { i: Int -> if (t[i] == '\uFFFC') 340 else 1 }
        val corte = RecorteCita.corte(t, 0, t.length, costeDe = costes)
        assertTrue("deberia plegarse", corte < t.length)
    }

    @Test
    fun `una cita de una sola foto no se pliega`() {
        val t = conFotos("mira # ")
        val costes = { i: Int -> if (t[i] == '\uFFFC') 340 else 1 }
        assertEquals(t.length, RecorteCita.corte(t, 0, t.length, costeDe = costes))
    }

    @Test
    fun `una cita con cuatro smileys tampoco se pliega`() {
        val t = conFotos("jajaja # # # # que bueno")
        val costes = { i: Int -> if (t[i] == '\uFFFC') 44 else 1 }
        assertEquals(t.length, RecorteCita.corte(t, 0, t.length, costeDe = costes))
    }

    @Test
    fun `el corte nunca deja la cita vacia`() {
        val t = conFotos("#texto de relleno para que haya donde cortar")
        val costes = { i: Int -> if (t[i] == '\uFFFC') 9999 else 1 }
        assertTrue(RecorteCita.corte(t, 0, t.length, costeDe = costes) > 0)
    }

    // ── Tramos: qué es UNA cita y qué son DOS ────────────────────────────────
    //
    // El bug de "los mensajes se cortan por la derecha" (Green Floyd, 2026-09-14, hilo
    // 10806006). Los rangos de abajo son los REALES de ese post, medidos con Robolectric
    // sobre el HTML que la app recibe (`post_citas_cortadas.html`, 7 citas seguidas).
    //
    // `Html.fromHtml` deja EXACTAMENTE un salto de linea entre dos <blockquote> seguidos,
    // así que la
    // regla vieja ("si empieza a un carácter del final del anterior, es la misma cita")
    // fusionaba cinco citas distintas en un solo tramo. El plegado cortaba ese tramo por la
    // mitad y con un solo `replace` borraba las citas 2 a 5 enteras: sus CitaSpan colapsaban
    // a longitud CERO y —esto es lo que se veía— **un span vacío sigue siendo un
    // LeadingMarginSpan y sigue reservando su margen**. Medido en el Samsung: 5 márgenes de
    // 39 px = 195 px empujando el texto, y 83 px de desborde.

    private val CITAS_REALES = listOf(
        971 to 1678, 1679 to 1824, 1825 to 1945, 1946 to 2050,
        2051 to 2199, 2203 to 2257, 2258 to 2574
    )

    @Test
    fun `siete citas seguidas son siete tramos, no uno`() {
        assertEquals(7, RecorteCita.tramos(CITAS_REALES).size)
    }

    @Test
    fun `dos citas separadas por un solo salto NO son la misma cita`() {
        // Este es el caso exacto que rompia: 1679 == 1678 + 1.
        assertEquals(
            listOf(971 to 1678, 1679 to 1824),
            RecorteCita.tramos(listOf(971 to 1678, 1679 to 1824))
        )
    }

    @Test
    fun `plegar una cita no puede meterse en la siguiente`() {
        val tramos = RecorteCita.tramos(CITAS_REALES)
        // La primera cita es la larga (707 caracteres, por encima del LIMITE de 600).
        val (ini, fin) = tramos[0]
        assertTrue("la primera cita deberia plegarse", fin - ini > LIMITE_DE_REFERENCIA)
        // Y su tramo acaba ANTES de que empiece la segunda: el replace del plegado no puede
        // alcanzar a las demas.
        assertTrue(fin <= tramos[1].first)
    }

    @Test
    fun `los spans vacios no cuentan como tramo`() {
        // Aunque alguno colapse, no debe generar un tramo (ni, por tanto, un plegado).
        assertEquals(
            listOf(971 to 1678),
            RecorteCita.tramos(listOf(971 to 1678, 1589 to 1589, 1589 to 1589))
        )
    }

    @Test
    fun `los tramos salen ordenados aunque lleguen desordenados`() {
        assertEquals(
            listOf(100 to 200, 300 to 400),
            RecorteCita.tramos(listOf(300 to 400, 100 to 200))
        )
    }

    private val LIMITE_DE_REFERENCIA = RecorteCita.LIMITE
}
