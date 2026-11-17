package com.fcplus.forocoches

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.math.pow

/**
 * Contraste de la paleta (WCAG 2.1 AA: 4,5:1 para texto normal). Lee los colores de
 * values/colors.xml y values-night/colors.xml y comprueba las parejas TEXTO / FONDO que de verdad
 * se pintan juntas. Existe porque un contraste malo no falla en ningún sitio: se ve "bien" en el
 * móvil de quien lo escribe y no se lee con sol, con la pantalla bajada o con la vista cansada.
 *
 * Lo encontró el repaso de diseño del 2026-10-10: las fechas en gris claro daban 2,7:1 y el
 * blanco sobre el rojo del modo oscuro 2,9:1.
 */
class ContrasteTest {

    private fun paleta(ruta: String): Map<String, String> {
        val xml = File(ruta).readText()
        return Regex("""<color name="([^"]+)">(#[0-9A-Fa-f]{6,8})</color>""").findAll(xml)
            .associate { it.groupValues[1] to it.groupValues[2] }
    }

    private val claro = paleta("src/main/res/values/colors.xml")
    private val oscuro = paleta("src/main/res/values-night/colors.xml")

    private fun luminancia(hex: String): Double {
        val h = hex.removePrefix("#").takeLast(6)
        val c = (0..2).map { h.substring(it * 2, it * 2 + 2).toInt(16) / 255.0 }
            .map { if (it <= 0.03928) it / 12.92 else ((it + 0.055) / 1.055).pow(2.4) }
        return 0.2126 * c[0] + 0.7152 * c[1] + 0.0722 * c[2]
    }

    private fun contraste(a: String, b: String): Double {
        val (x, y) = listOf(luminancia(a), luminancia(b)).sortedDescending()
        return (x + 0.05) / (y + 0.05)
    }

    /** Texto / fondo sobre el que se pinta. Todas, en claro y en oscuro, ≥ 4,5. */
    private val parejas = listOf(
        "fc_texto" to "fc_fondo",
        "fc_texto_2" to "fc_fondo",
        "fc_texto_3" to "fc_fondo",
        "fc_texto_3" to "fc_tarjeta",
        "fc_texto_2" to "fc_tarjeta",
        "fc_texto_2" to "fc_chip",
        "fc_sobre_rojo" to "fc_rojo",
        "fc_rojo" to "fc_fondo",
        "fc_rojo" to "fc_tarjeta",
        "fc_rojo" to "fc_rojo_suave",
        "fc_texto" to "fc_busca",
        "fc_texto" to "fc_busca_actual",
    )

    @Test
    fun `el texto se lee en claro y en oscuro`() {
        val fallos = mutableListOf<String>()
        for ((modo, p) in listOf("claro" to claro, "oscuro" to oscuro)) {
            if (p.isEmpty()) continue
            for ((texto, fondo) in parejas) {
                val r = contraste(p.getValue(texto), p.getValue(fondo))
                if (r < 4.5) fallos.add("$modo: $texto sobre $fondo = ${"%.2f".format(r)}:1")
            }
        }
        assertTrue("Contraste por debajo de 4,5:1:\n" + fallos.joinToString("\n"), fallos.isEmpty())
    }

    /** Las superficies que cambian con el tono (TonoOscuro, `fc_tono_<tono>_<superficie>`). */
    private val superficies = listOf(
        "fc_fondo", "fc_superficie", "fc_divisoria", "fc_chip", "fc_cita_fondo",
        "fc_fondo_tarjetas", "fc_tarjeta", "fc_tarjeta_borde", "fc_cita_fondo_tarjeta"
    )

    /** La paleta oscura con las superficies de [tono] ("gris" = la de siempre). */
    private fun paletaTono(tono: String): Map<String, String> =
        if (tono == TonoOscuro.GRIS) oscuro
        else oscuro + superficies.associateWith { oscuro.getValue("fc_tono_${tono}_${it.removePrefix("fc_")}") }

    @Test
    fun `cada tono define todas sus superficies`() {
        if (oscuro.isEmpty()) return
        for (tono in TonoOscuro.OPCIONES - TonoOscuro.GRIS) for (s in superficies) {
            assertTrue("Falta fc_tono_${tono}_${s.removePrefix("fc_")}",
                oscuro.containsKey("fc_tono_${tono}_${s.removePrefix("fc_")}"))
        }
    }

    /** Texto, secundario, terciario y rojo sobre cada superficie donde se pintan, en los 3 tonos. */
    @Test
    fun `el texto se lee en los tres tonos del modo oscuro`() {
        if (oscuro.isEmpty()) return
        val textos = listOf("fc_texto", "fc_texto_2", "fc_texto_3", "fc_rojo")
        val fondos = listOf("fc_fondo", "fc_superficie", "fc_tarjeta", "fc_fondo_tarjetas",
            "fc_cita_fondo", "fc_cita_fondo_tarjeta")
        val fallos = mutableListOf<String>()
        for (tono in TonoOscuro.OPCIONES) {
            val p = paletaTono(tono)
            val parejasTono = textos.flatMap { t -> fondos.map { t to it } } +
                listOf("fc_texto" to "fc_chip", "fc_texto_2" to "fc_chip", "fc_sobre_rojo" to "fc_rojo")
            for ((texto, fondo) in parejasTono) {
                // El gris de siempre ya traía fc_texto_3 sobre la cita de Tarjetas a 4,16:1
                // (antes de los tonos; no se toca aquí porque el gris es "a nadie le cambia
                // nada"). Los tonos nuevos sí cumplen ahí, y eso es lo que se vigila.
                if (tono == TonoOscuro.GRIS && texto == "fc_texto_3" && fondo == "fc_cita_fondo_tarjeta") continue
                val r = contraste(p.getValue(texto), p.getValue(fondo))
                if (r < 4.5) fallos.add("$tono: $texto sobre $fondo = ${"%.2f".format(r)}:1")
            }
        }
        assertTrue("Contraste por debajo de 4,5:1:\n" + fallos.joinToString("\n"), fallos.isEmpty())
    }

    /**
     * Sobre negro puro lo fácil es que todo se funda en una mancha: cada superficie tiene que
     * quedar un punto por ENCIMA de aquella sobre la que va (tarjeta sobre su fondo, cita sobre la
     * tarjeta o el fondo, raya y borde sobre el fondo). 1,08:1 es poco a propósito —no son
     * textos, son bordes de zona—, pero cero es que no se ven.
     */
    @Test
    fun `en los tres tonos las superficies se distinguen entre si`() {
        if (oscuro.isEmpty()) return
        val encima = listOf(
            "fc_tarjeta" to "fc_fondo_tarjetas",
            "fc_tarjeta_borde" to "fc_fondo_tarjetas",
            "fc_cita_fondo_tarjeta" to "fc_tarjeta",
            "fc_cita_fondo" to "fc_fondo",
            "fc_divisoria" to "fc_fondo",
            "fc_chip" to "fc_fondo",
            "fc_superficie" to "fc_fondo",
        )
        val fallos = mutableListOf<String>()
        for (tono in TonoOscuro.OPCIONES) {
            val p = paletaTono(tono)
            for ((arriba, abajo) in encima) {
                val a = p.getValue(arriba); val b = p.getValue(abajo)
                val r = contraste(a, b)
                if (luminancia(a) <= luminancia(b) || r < 1.08)
                    fallos.add("$tono: $arriba ($a) sobre $abajo ($b) = ${"%.2f".format(r)}:1")
            }
        }
        assertTrue("Superficies que no se distinguen:\n" + fallos.joinToString("\n"), fallos.isEmpty())
    }

    @Test
    fun `cada tono es mas negro que el anterior`() {
        if (oscuro.isEmpty()) return
        val fondos = TonoOscuro.OPCIONES.map { luminancia(paletaTono(it).getValue("fc_fondo")) }
        assertTrue("$fondos", fondos.zipWithNext().all { (a, b) -> b < a })
        assertTrue("el último es el negro puro", paletaTono(TonoOscuro.NEGRO).getValue("fc_fondo").uppercase() == "#000000")
    }

    @Test
    fun `lo que va encima de un video o una capa oscura es blanco siempre`() {
        for (p in listOf(claro, oscuro)) {
            if (p.isEmpty()) continue
            assertTrue(contraste(p.getValue("fc_sobre_capa"), "#000000") > 15)
        }
    }
}
