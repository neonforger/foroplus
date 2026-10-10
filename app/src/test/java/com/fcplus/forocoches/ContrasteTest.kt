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

    @Test
    fun `lo que va encima de un video o una capa oscura es blanco siempre`() {
        for (p in listOf(claro, oscuro)) {
            if (p.isEmpty()) continue
            assertTrue(contraste(p.getValue("fc_sobre_capa"), "#000000") > 15)
        }
    }
}
