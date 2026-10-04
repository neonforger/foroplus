package com.fcplus.forocoches

/**
 * Convierte en un aviso corto el motivo por el que ForoCoches rechaza un mensaje.
 *
 * Hasta 2026-08-15 el usuario veía literalmente **"http 200"** al intentar responder dos veces
 * seguidas: el POST volvía con 200, FC no publicaba, y como el motivo no se sabía leer del HTML
 * se enseñaba el código de estado. Ahora `extractErr` (extractor.js) sí trae el texto de FC —
 * el más frecuente es el límite entre mensajes:
 *
 *   "Debes esperar al menos 30 segundos entre cada envío de nuevos mensajes.
 *    Inténtalo de nuevo en 6 segundos."
 *
 * Correcto pero largo para un aviso, así que de ahí se saca lo único accionable: **cuánto falta**.
 * Lo que no se reconoce se pasa tal cual (FC explica sus propios errores mejor que nosotros);
 * lo que es un código técnico se cambia por una frase, que "no-form" no le dice nada a nadie.
 */
object ErrorPublicar {

    /** Aviso genérico: FC dijo que no, pero no en un idioma que sirva para enseñar. */
    const val GENERICO = "FC no aceptó el mensaje. Inténtalo de nuevo"

    private val CADA = Regex("""al menos\s+(\d+)\s*segundos""", RegexOption.IGNORE_CASE)
    private val FALTAN = Regex("""de nuevo en\s+(\d+)\s*segundos""", RegexOption.IGNORE_CASE)

    /**
     * @param siNoSeSabe qué decir cuando FC no da un motivo legible. Cada pantalla pasa el suyo
     *   ("No se pudo crear el hilo"…): si el motivo no sirve, al menos se sabe QUÉ ha fallado.
     */
    fun corto(bruto: String, siNoSeSabe: String = GENERICO): String {
        val t = bruto.replace(Regex("\\s+"), " ").trim()
        if (esTecnico(t)) return siNoSeSabe

        val faltan = FALTAN.find(t)?.groupValues?.get(1)
        val cada = CADA.find(t)?.groupValues?.get(1)
        if (faltan != null || cada != null) {
            return when {
                faltan != null && cada != null -> "Espera $faltan s: FC solo deja publicar cada $cada s"
                faltan != null -> "Espera $faltan s para publicar otra vez"
                else -> "FC solo deja publicar cada $cada s"
            }
        }
        // Mensaje propio de FC (duplicado, hilo cerrado, sin permisos…): se respeta entero.
        return if (t.length <= 140) t else t.take(137).trimEnd() + "…"
    }

    /**
     * Códigos internos y excepciones: "http 200", "no-form", "TypeError: Failed to fetch".
     * Una sola palabra ya delata un código — los mensajes de FC son frases.
     */
    private fun esTecnico(t: String): Boolean =
        t.isEmpty() ||
            t.startsWith("http ", ignoreCase = true) ||
            !t.contains(' ') ||
            t.startsWith("TypeError", ignoreCase = true) ||
            t.startsWith("Error", ignoreCase = true)
}
