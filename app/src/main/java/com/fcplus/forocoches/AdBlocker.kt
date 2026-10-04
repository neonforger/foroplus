package com.fcplus.forocoches

object AdBlocker {

    private val blockedDomains = setOf(
        // OptiDigital: capa que ORQUESTA la publicidad en FC (inyecta los slots por JS y
        // llama al resto de redes). Bloquear su loader corta la publi de raíz, en cualquier
        // skin y dentro de hilos (donde el ocultado por CSS/clases-hash se quedaba corto).
        "opti-digital.com",
        "optidigital.com",
        "sddan.com",
        "presage.io",
        "doubleclick.net",
        "googlesyndication.com",
        "googletagmanager.com",
        "googletagservices.com",
        "google-analytics.com",
        "adnxs.com",
        "adsafeprotected.com",
        "adservice.google.com",
        "amazon-adsystem.com",
        "bidswitch.net",
        "criteo.com",
        "criteo.net",
        "openx.net",
        "pubmatic.com",
        "rubiconproject.com",
        "scorecardresearch.com",
        "taboola.com",
        "outbrain.com",
        "yieldmanager.com",
        "zedo.com",
        "trafficjunky.net",
        "exoclick.com",
        "adform.net",
        "smartadserver.com",
        "rlcdn.com",
        "casalemedia.com",
        "indexww.com",
        "sharethrough.com",
        "triplelift.com",
        "33across.com"
    )

    fun shouldBlock(url: String): Boolean = blockedDomains.any { url.contains(it) }
}
