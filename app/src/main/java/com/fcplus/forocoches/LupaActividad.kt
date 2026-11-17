package com.fcplus.forocoches

/**
 * La lupa de la actividad de un usuario: buscar texto en "@x · mensajes" y en los títulos de
 * "@x · hilos suyos" / "@x · participa". Lo pidió Miguel el 2026-11-13 (*"para buscar entre
 * los 3000 mensajes que tengo"*).
 *
 * No es una búsqueda nueva: es la de FC acotada a ese usuario, la misma que el campo "Usuario"
 * del buscador ([BusquedaPorUsuario], `fcSearch` con `searchuser`). Aquí solo se decide QUÉ se
 * pide y qué se le dice a quien busca; la vista vive en [LupaActividadController].
 */
object LupaActividad {

    /** Lo que se le pide al foro, o por qué no se puede. */
    sealed class Peticion {
        /**
         * @param porTitulos por títulos salen hilos; por mensajes, los mensajes uno a uno.
         * @param participa solo por títulos: los hilos en los que ESCRIBIÓ (`starteronly=0`),
         *   no solo los que abrió.
         */
        data class Buscar(
            val texto: String,
            val usuario: String,
            val porTitulos: Boolean,
            val participa: Boolean
        ) : Peticion()

        data class NoVale(val motivo: String) : Peticion()
    }

    /**
     * La lupa sale solo donde la búsqueda de FC puede contestar: con sesión (gotcha 3: de
     * invitado devuelve la home sin dar error), en una de las tres listas, y NO en "sus mensajes
     * en este hilo" (eso ya va acotado con `searchthreadid`, que `fcSearch` no lleva).
     */
    fun disponible(logueado: Boolean, modo: String, enHilo: String): Boolean =
        logueado && modo in MODOS && enHilo.isEmpty()

    /** [modo] es el de [Screen.UserActivity]: "posts", "started" o "threads". */
    fun decidir(texto: String, usuario: String, modo: String): Peticion {
        val t = texto.trim()
        val u = BusquedaPorUsuario.limpiarUsuario(usuario)
        if (u.isEmpty() || modo !in MODOS) return Peticion.NoVale("No sé de quién buscar")
        if (t.isEmpty()) return Peticion.NoVale("Escribe qué buscar")
        // El mínimo es de FC, no nuestro (ver BusquedaPorUsuario.MINIMO).
        if (t.length < BusquedaPorUsuario.MINIMO) {
            return Peticion.NoVale("Escribe al menos ${BusquedaPorUsuario.MINIMO} caracteres")
        }
        return Peticion.Buscar(t, u, porTitulos = modo != "posts", participa = modo == "threads")
    }

    /** El texto gris del campo: dice DÓNDE se busca, que no es lo mismo en las tres listas. */
    fun pista(modo: String): String = when (modo) {
        "posts" -> "Buscar en sus mensajes"
        "started" -> "Buscar en los títulos de sus hilos"
        else -> "Buscar en los títulos donde participa"
    }

    /**
     * Las dos cosas que el buscador de FC no hace y la gente da por hechas (medidas, no
     * supuestas): no ve palabras de menos de 3 letras ni la ñ y las tildes (gotcha 62: Sphinx
     * con un índice solo ASCII), y no tiene todos los hilos de cada usuario (gotcha 65: la
     * ficha de Electrik dice 173 y su propio enlace de FC trae 29). Lo segundo solo se dice
     * donde salen los hilos DE UN USUARIO.
     */
    fun limites(hilosDeUsuario: Boolean): String =
        "El buscador de ForoCoches no encuentra palabras de menos de 3 letras ni las que llevan " +
            "ñ o tilde" + if (hilosDeUsuario) ", y no tiene todos los hilos de cada usuario." else "."

    /**
     * Estado vacío de una búsqueda: qué no se ha encontrado y, debajo, los límites del
     * buscador, para que "no sale" no se lea como "no existe". Sin usuario es la búsqueda de
     * todo el foro.
     */
    fun vacio(texto: String, usuario: String, porTitulos: Boolean): String {
        val u = BusquedaPorUsuario.limpiarUsuario(usuario)
        val de = if (u.isEmpty()) "" else " de @$u"
        val que = if (porTitulos) "Ningún hilo$de tiene «$texto» en el título"
                  else "Ningún mensaje$de contiene «$texto»"
        return "$que\n\n${limites(hilosDeUsuario = porTitulos && u.isNotEmpty())}"
    }

    /**
     * Cabecera de los resultados por títulos en los que participa. Las otras dos son las del
     * buscador ([BusquedaPorUsuario.cabecera]): es la misma búsqueda, y se llama igual.
     */
    fun cabeceraParticipa(texto: String, usuario: String): String =
        "\"$texto\" · donde participa @${BusquedaPorUsuario.limpiarUsuario(usuario)}"

    /**
     * Qué búsqueda es, para saber al volver atrás si lo cargado sigue siendo ESA. Como
     * [BusquedaPorUsuario.clave], y además "participa" no es "hilos suyos" aunque la palabra y
     * el usuario coincidan.
     */
    fun clave(texto: String, usuario: String, participa: Boolean): String {
        val base = BusquedaPorUsuario.clave(texto, usuario)
        return if (participa) "$base\u0000participa" else base
    }

    private val MODOS = setOf("posts", "started", "threads")
}

/**
 * Lo que había cargado en "@x · mensajes" / "hilos suyos" / "participa" al lanzar la lupa, para
 * que el atrás desde los resultados te devuelva a la lista entera tal cual (con sus páginas y
 * por donde ibas) en vez de volver a pedírsela a FC desde la primera.
 *
 * Se usa UNA vez ([ActividadGuardada.tomar]): cualquier otra vuelta posterior a esa pantalla
 * la rehace como siempre, que es lo que hacía antes de la lupa. Así nunca se enseña una foto
 * vieja de una lista que ya se había seguido bajando.
 */
data class ActividadGuardada<T>(
    val usuario: String,
    val modo: String,
    val enHilo: String,
    val filas: List<T>,
    /** Página hasta la que se había cargado. */
    val pagina: Int,
    /** La URL con `searchid` sobre la que se pide la página siguiente, o null si no hay. */
    val base: String?,
    val agotada: Boolean,
    /** Primera fila visible y su desplazamiento, para volver a ella. */
    val posicion: Int,
    val desplazamiento: Int
) {
    fun esDe(usuario: String, modo: String, enHilo: String): Boolean =
        this.usuario == usuario && this.modo == modo && this.enHilo == enHilo && filas.isNotEmpty()

    companion object {
        /** La guardada si es de ESTA pantalla, o null. Quien llama la olvida después. */
        fun <T> tomar(g: ActividadGuardada<T>?, usuario: String, modo: String, enHilo: String): ActividadGuardada<T>? =
            g?.takeIf { it.esDe(usuario, modo, enHilo) }
    }
}

/**
 * Cuándo una lista de búsqueda (hilos) se da por acabada aunque FC no lo diga (gotcha 37).
 */
object FinDeLista {
    /**
     * Una página siguiente que llega SIN hilos ("empty": p. ej. la sesión se cayó a media lista
     * y FC devolvió la home) también es el final. Sin esto, cada gesto hacia abajo volvía a
     * pedir la misma página, para siempre. Un error de red NO: ese se reintenta al deslizar.
     */
    fun agotadaTrasError(motivo: String, pagina: Int, hayFilas: Boolean): Boolean =
        motivo == "empty" && pagina > 1 && hayFilas
}
