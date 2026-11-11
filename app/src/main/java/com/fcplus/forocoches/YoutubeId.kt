package com.fcplus.forocoches

/**
 * El id de vídeo de un enlace de YouTube (watch, shorts, embed, youtu.be; también music.youtube.com).
 * Sale de [EnlacesEmbebibles], que lo usa para el BBCode `[YOUTUBE]id[/YOUTUBE]`, y lo comparte
 * [EnlaceMusica] para que las dos cosas reconozcan exactamente los mismos enlaces.
 */
object YoutubeId {
    private val YT = Regex(
        """(?:youtube[.]com/(?:watch\?(?:[^ ]*&)?v=|shorts/|embed/)|youtu[.]be/)([A-Za-z0-9_-]{6,})""",
        RegexOption.IGNORE_CASE
    )

    fun de(url: String): String? = YT.find(url)?.groupValues?.get(1)
}
