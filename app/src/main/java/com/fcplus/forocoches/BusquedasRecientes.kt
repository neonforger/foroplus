package com.fcplus.forocoches

/**
 * Las últimas búsquedas, para no tener que reescribirlas.
 *
 * Lo pidieron Green Floyd y Márquez. En un foro se busca lo mismo una y otra vez (el hilo de
 * turno, un modelo de coche, un nombre), y teclearlo entero cada vez en el móvil es lo que hace
 * que la gente acabe usando el buscador de la web.
 *
 * Se guarda como **una cadena ordenada**, igual que los avisos descartados y las citas vistas:
 * el `StringSet` de `SharedPreferences` no conserva el orden, y aquí el orden ES la función
 * (lo último buscado, primero).
 */
object BusquedasRecientes {

    /** Cuántas se recuerdan. Más de esto es una lista que ya hay que leer, no un atajo. */
    const val TOPE = 8

    /** Separador imposible en una búsqueda real: las comas y los espacios sí se usan. */
    private const val SEP = "\u001f"

    fun leer(guardado: String): List<String> =
        guardado.split(SEP).map { it.trim() }.filter { it.isNotEmpty() }

    fun guardar(lista: List<String>): String = lista.joinToString(SEP)

    /**
     * Deja [consulta] la primera. Si ya estaba, **sube**, no se duplica — y se compara sin
     * mayúsculas para que "Renault" y "renault" no ocupen dos huecos.
     */
    fun recordar(previas: List<String>, consulta: String, tope: Int = TOPE): List<String> {
        val q = consulta.trim()
        if (q.isEmpty()) return previas
        val resto = previas.filter { !it.equals(q, ignoreCase = true) }
        return (listOf(q) + resto).take(tope)
    }

    fun olvidar(previas: List<String>, consulta: String): List<String> =
        previas.filter { !it.equals(consulta, ignoreCase = true) }
}
