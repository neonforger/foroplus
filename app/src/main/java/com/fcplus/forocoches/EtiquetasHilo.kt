package com.fcplus.forocoches

/**
 * Las etiquetas que la gente pone en el TÍTULO de un hilo de FC (+18, +16, +14, +prv, +hd) y
 * las peñas. Es lo único que mira masdieciocho y lo único que mira el lector del servidor: los
 * dos usan los mismos casos (`contrato/etiquetas-casos.json`), así que si uno cambia el patrón y
 * el otro no, falla un test.
 *
 * `Vol.58+18` es una etiqueta real (hasta el 2026-10-09 el patrón exigía que no hubiera un número
 * delante y la perdía); `+180` y `+1888` no lo son.
 */
object EtiquetasHilo {

    val TODAS = listOf("+18", "+16", "+14", "+prv", "+hd", "peña")

    private val EDAD = Regex("""\+\s?(18|16|14)(?!\d)""", RegexOption.IGNORE_CASE)
    private val OTRAS = Regex("""\+\s?(prv|hd)\b""", RegexOption.IGNORE_CASE)
    private val PENA = Regex("""\bpe(ñ|ny)a\b""", RegexOption.IGNORE_CASE)

    fun de(titulo: String): Set<String> {
        val r = LinkedHashSet<String>()
        EDAD.findAll(titulo).forEach { r.add("+" + it.groupValues[1]) }
        OTRAS.findAll(titulo).forEach { r.add("+" + it.groupValues[1].lowercase()) }
        if (PENA.containsMatchIn(titulo)) r.add("peña")
        return r
    }

    /** Lo que decide si un hilo es de la sección: cualquiera menos la peña sola. */
    fun entraEnMas18(titulo: String): Boolean = de(titulo).any { it != "peña" }

    /** Para "Ocultar hilos +18/+16 en las listas". */
    fun esMas18o16(titulo: String): Boolean = de(titulo).let { "+18" in it || "+16" in it }
}
