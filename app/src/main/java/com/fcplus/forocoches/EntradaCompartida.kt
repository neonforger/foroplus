package com.fcplus.forocoches

import android.content.Intent

/**
 * "Guardar en Música": lo que otra app (YouTube, Spotify, WhatsApp…) comparte con ForoPlus por el
 * menú de Android. Entra por el `activity-alias` `GuardarEnMusica` del manifiesto, que solo
 * declara `SEND` de `text/plain`; MainActivity pregunta aquí ANTES que a [IntentRouter], así que
 * un SEND nunca se toma por un enlace de FC ni por una notificación, y lo que no es un SEND de
 * texto (el launcher, un enlace de hilo) sigue su camino de siempre.
 *
 * Es dato de otra app: aquí solo se acota (tipo y tamaño); de lo que llega, la hoja "Añadir" usa
 * únicamente las URLs que reconoce [EnlaceMusica]. Android no deja filtrar el menú por dominio,
 * así que "Guardar en Música" sale para cualquier texto (spec, llamada 11): lo que no trae
 * canciones se dice y ya ([traeCanciones]).
 */
object EntradaCompartida {

    /** El `activity-alias` del manifiesto (el nombre que sale en el menú de compartir). */
    const val ALIAS = "com.fcplus.forocoches.GuardarEnMusica"

    /**
     * Tope de lo que se acepta (plan de A12). Una playlist compartida con [PlaylistCompartida]
     * gasta ~95 caracteres por canción de YouTube y ~105 por una de Spotify: cabe una de 200 (el
     * tope) salvo con títulos muy largos o casi todo Spotify, y entonces se pierden las últimas.
     */
    const val MAX_CARACTERES = 20_000

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
     * ¿Hay que atender el `SEND` con el que se ha CREADO la actividad? Solo si es nueva. Recreada
     * (el modo oscuro, el estilo de Opciones o el tamaño de letra del sistema la recrean) o
     * relanzada desde Recientes, `getIntent()` sigue siendo aquel mismo SEND: la hoja volvería a
     * salir con lo que ya se guardó. [recreada] es "hay `savedInstanceState`".
     */
    fun esNueva(recreada: Boolean, flags: Int): Boolean =
        !recreada && (flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY) == 0

    /** ¿Trae alguna canción que la hoja "Añadir" sepa leer (también un `spotify.link`, que se resuelve allí)? */
    fun traeCanciones(texto: String): Boolean {
        val p = AnadirMusica.plan(texto)
        return p.refs.isNotEmpty() || p.cortos.isNotEmpty()
    }
}
