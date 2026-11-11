package com.fcplus.forocoches

/** Lo que le queda de vida a una publicación y si ya hay prisa (menos de una hora). */
data class Queda(val texto: String, val urgente: Boolean)

enum class MotivoTexto { VACIO, LARGO, ENLACE }

/**
 * Los textos del feed de Música, puros para poder probarlos sin pantalla: "hace 5 min", "se va en
 * 40 min", por qué un texto no vale y qué decirle a quien falla algo.
 */
object VistaFeed {
    const val TEXTO_MAX = 100

    private const val MIN = 60_000L
    private const val HORA = 60 * MIN

    /** La MISMA regla de enlaces que el servidor (feed.ts): si cambia allí, cambia aquí. */
    private val ENLACE = Regex(
        "(https?://|www\\.|\\b[a-z0-9-]+\\.(com|es|net|org|io|me|ly|gg|tv)\\b)",
        RegexOption.IGNORE_CASE
    )

    fun hace(creada: Long, ahora: Long): String {
        // Reloj del móvil por detrás del servidor: la diferencia sale negativa y es "ahora".
        val d = (ahora - creada).coerceAtLeast(0)
        return when {
            d < MIN -> "ahora"
            d < HORA -> "hace ${d / MIN} min"
            d < 24 * HORA -> "hace ${d / HORA} h"
            else -> "hace ${d / (24 * HORA)} d"
        }
    }

    fun queda(caduca: Long, ahora: Long): Queda {
        val d = caduca - ahora
        if (d < HORA) return Queda("se va en ${(d / MIN).coerceAtLeast(1)} min", true)
        return Queda("${d / HORA} h", false)
    }

    fun motivoTexto(texto: String): MotivoTexto? {
        val t = texto.trim()
        return when {
            t.isEmpty() -> MotivoTexto.VACIO
            t.length > TEXTO_MAX -> MotivoTexto.LARGO
            ENLACE.containsMatchIn(t) -> MotivoTexto.ENLACE
            else -> null
        }
    }

    /** El tope diario y el horario comparten texto a propósito: no se anuncia ninguna cifra. */
    fun textoError(e: ApiMusica.R.Error): String = when (e.codigo) {
        "red" -> "Sin conexión. Comprueba tu red e inténtalo de nuevo."
        "respuesta" -> "El servidor ha contestado algo raro. Inténtalo en un rato."
        "token" -> "Tu sesión ha caducado. Vuelve a abrir Música."
        "baneada" -> "Tu cuenta no puede usar esto."
        "antiguedad" -> "Tu cuenta es demasiado nueva para esto todavía."
        "texto" -> "Ese texto no vale: usa entre 1 y 100 caracteres, sin enlaces ni palabras no permitidas."
        "oculta" -> "Esa publicación ya no está disponible."
        "propia" -> "No puedes dar corazón a tu propia publicación."
        "no_existe" -> "Esa publicación ya no existe."
        "limite" -> "Por hoy ya has compartido muchas canciones; mañana puedes seguir."
        "no_es_cancion" -> "Ese enlace no es de una canción."
        "no_encontrada" -> "No hemos encontrado esa canción."
        "cuerpo" -> "Faltan datos para publicar. Revisa lo que has escrito."
        else -> "No ha salido bien. Inténtalo de nuevo en un rato."
    }

    fun estadoVacio(orden: OrdenFeed, hayRed: Boolean): String = when {
        !hayRed -> "No hemos podido cargar las canciones. Comprueba tu conexión y pulsa Reintentar."
        orden == OrdenFeed.QUERIDAS -> "Todavía no hay canciones con corazones. Dale a alguna de las recientes."
        else -> "Aún no hay canciones compartidas. Sé el primero en recomendar una."
    }
}
