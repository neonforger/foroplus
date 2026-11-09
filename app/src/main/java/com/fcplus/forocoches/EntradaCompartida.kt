package com.fcplus.forocoches

import android.content.Intent

/**
 * "Guardar en Música": lo que otra app (YouTube, Spotify, WhatsApp…) comparte con ForoPlus por el
 * menú de Android. Lo recibe [GuardarEnMusica], que solo declara `SEND` de `text/plain`, lo acota
 * aquí ([texto]) y se lo pasa a MainActivity en [EXTRA]. MainActivity mira ese extra ANTES que a
 * [IntentRouter]: lo compartido nunca se toma por un enlace de FC, y lo que no lo lleva (el
 * launcher, un enlace de hilo) sigue su camino de siempre.
 *
 * Es dato de otra app: aquí solo se acota (tipo y tamaño); de lo que llega, la hoja "Añadir" usa
 * únicamente las URLs que reconoce [EnlaceMusica]. Android no deja filtrar el menú por dominio,
 * así que "Guardar en Música" sale para cualquier texto (spec, llamada 11): lo que no trae
 * canciones se dice y ya ([traeCanciones]).
 */
object EntradaCompartida {

    /** El texto, ya acotado, de [GuardarEnMusica] a MainActivity. Nombre propio: no es un extra de Android. */
    const val EXTRA = "com.fcplus.forocoches.extra.MUSICA_COMPARTIDA"

    /**
     * Tope de lo que se acepta (Ruling 31). Una playlist compartida con [PlaylistCompartida] gasta
     * ~95 caracteres por canción de YouTube, ~105 por una de Spotify y más con nombres largos: con
     * 20.000 una de 200 (el tope) perdía las últimas. 60.000 caben de sobra en un Intent.
     */
    const val MAX_CARACTERES = 60_000

    /**
     * El texto compartido, o null si el intent no es un `SEND` de texto plano o no trae nada.
     * Con asunto (hay apps que mandan ahí el título) va el asunto y debajo el texto, salvo que el
     * texto ya lo lleve dentro. Como mucho [MAX_CARACTERES] (ver [recortar]).
     */
    fun texto(accion: String?, tipo: String?, extraTexto: CharSequence?, extraAsunto: CharSequence?): String? {
        if (accion != Intent.ACTION_SEND || !tipo.equals("text/plain", ignoreCase = true)) return null
        val texto = extraTexto?.toString()?.trim().orEmpty()
        val asunto = extraAsunto?.toString()?.trim().orEmpty()
        val junto = when {
            asunto.isEmpty() || texto.contains(asunto) -> texto
            texto.isEmpty() -> asunto
            else -> "$asunto\n$texto"
        }
        return if (junto.isEmpty()) null else recortar(junto)
    }

    /**
     * Si el corte cae dentro de una palabra se retrocede hasta el espacio anterior: un enlace de
     * YouTube cortado a medias sigue pareciendo uno ([YoutubeId] acepta ids desde 6 caracteres) y
     * se guardaría OTRO vídeo. Sin ningún espacio, a pelo, pero sin dejar medio emoji al final.
     */
    private fun recortar(t: String): String {
        if (t.length <= MAX_CARACTERES) return t
        if (t[MAX_CARACTERES].isWhitespace()) return t.substring(0, MAX_CARACTERES)
        val espacio = (MAX_CARACTERES - 1 downTo 1).firstOrNull { t[it].isWhitespace() }
        if (espacio != null) return t.substring(0, espacio)
        val corte = if (Character.isHighSurrogate(t[MAX_CARACTERES - 1])) MAX_CARACTERES - 1 else MAX_CARACTERES
        return t.substring(0, corte)
    }

    /**
     * ¿Hay que atender el [EXTRA] con el que llega MainActivity? No si se está recreando (el modo
     * oscuro, el estilo de Opciones o el tamaño de letra del sistema la recrean) ni si viene de
     * Recientes, que relanza el intent de la tarea: el texto sería el de antes y la hoja volvería
     * a salir con lo que ya se guardó. [recreada] es "hay `savedInstanceState`".
     */
    fun esNueva(recreada: Boolean, flags: Int): Boolean =
        !recreada && (flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY) == 0

    /** ¿Trae alguna canción que la hoja "Añadir" sepa leer (también un `spotify.link`, que se resuelve allí)? */
    fun traeCanciones(texto: String): Boolean {
        val p = AnadirMusica.plan(texto)
        return p.refs.isNotEmpty() || p.cortos.isNotEmpty()
    }
}
