package com.fcplus.forocoches

/**
 * Números que pinta la barra de páginas del hilo, centrados en la página actual.
 * Con menos páginas que el hueco disponible las devuelve todas; en los extremos desplaza
 * la ventana para no dejar huecos (nunca menos de `span` números si los hay).
 */
/**
 * Cuántos números caben sin que la barra se salga de pantalla. Los chips crecen con las
 * cifras (medido en el dispositivo: ~105px con una cifra, ~126px con dos), y con `« ‹ … › »`
 * ocupando cuatro huecos fijos, cinco números de tres cifras no caben en 1080px: `»` se iría
 * fuera y habría que arrastrar la barra para llegar a la última página.
 */
fun pageSpan(total: Int): Int = if (total >= 100) 3 else 5

fun pageWindow(current: Int, total: Int, span: Int = 5): List<Int> {
    if (total <= 1) return listOf(1)
    val cur = current.coerceIn(1, total)
    if (total <= span) return (1..total).toList()
    val half = span / 2
    var start = cur - half
    if (start < 1) start = 1
    if (start + span - 1 > total) start = total - span + 1
    return (start until start + span).toList()
}
