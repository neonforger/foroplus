package com.fcplus.forocoches

import android.content.SharedPreferences

/**
 * La introducción para quien acaba de instalar la app (dueño, 2026-11-17: "la app empieza a
 * parecer Odoo con tantos módulos"). Cinco páginas, opcional ("Saltar" siempre a la vista), con
 * capturas REALES de la app recortadas (datos de muestra inventados: ver `privado/capturas`).
 *
 * Solo sale en una **instalación nueva** y una vez: a quien ya usaba la app no se le planta
 * delante una presentación de algo que conoce. Se puede volver a ver desde Opciones → Acerca de.
 */
object Introduccion {

    const val PREF_VISTA = "intro_vista"

    /** Si hay que enseñarla al arrancar: instalación nueva y todavía no vista (ni saltada). */
    fun alArrancar(instalacionNueva: Boolean, yaVista: Boolean): Boolean = instalacionNueva && !yaVista

    fun yaVista(prefs: SharedPreferences): Boolean = prefs.getBoolean(PREF_VISTA, false)

    fun marcarVista(prefs: SharedPreferences) {
        prefs.edit().putBoolean(PREF_VISTA, true).apply()
    }

    /** [imagen] 0 = sin captura (la portada lleva el icono de la app). */
    data class Pagina(val titulo: String, val texto: String, val donde: String, val imagen: Int)

    /** Las páginas, en orden. La última (lo extra) añade sus interruptores aparte. */
    val PAGINAS = listOf(
        Pagina(
            "Es ForoCoches, pero cómodo",
            "Una app hecha a mano para leer y escribir en el foro desde el móvil. Te enseñamos lo básico en un minuto.",
            "App no oficial. Tu sesión va directa a forocoches.com y se queda en tu móvil.",
            0
        ),
        Pagina(
            "Arriba, solo los subforos que lees",
            "Escóndelos, ordénalos arrastrando y júntalos en el Popurrí para leer varios de un vistazo. La llama abre lo que arde ahora.",
            "Opciones → Personalizar → Subforos de arriba",
            R.drawable.intro_subforos
        ),
        Pagina(
            "Lee sin ruido",
            "Aquí ignorar es de verdad: usuarios, hilos o palabras desaparecen del todo. Y puedes apuntarte notas privadas sobre quien quieras; solo las ves tú.",
            "Toca ⋮ en cualquier mensaje",
            R.drawable.intro_ignorar
        ),
        Pagina(
            "Responde como en el foro, pero más fácil",
            "Marca varios mensajes con Multicita, incluso de hilos distintos, y responde a todos a la vez. Toca una cita para quedarte solo con la frase que te interesa.",
            "¿Prefieres responder bajo cada cita? Opciones → Al publicar",
            R.drawable.intro_citas
        ),
        Pagina(
            "Lo extra, si te apetece",
            "Quédate con tu versión de la app: lo que apagues aquí no estorba, y se vuelve a encender en Opciones cuando quieras.",
            "",
            R.drawable.intro_musica
        )
    )

    /** Texto del botón principal en la página [i]. */
    fun botonPrincipal(i: Int): String = when (i) {
        0 -> "Empezar"
        PAGINAS.lastIndex -> "Entrar a ForoPlus"
        else -> "Siguiente"
    }

    /** "Saltar" sobra en la última: ahí el botón principal ya es entrar. */
    fun haySaltar(i: Int): Boolean = i < PAGINAS.lastIndex

    /** La clave de Música en los ocultos de la barra de arriba (la misma que usa MainActivity). */
    const val CLAVE_MUSICA = "musica"

    /**
     * Los ocultos de la barra de arriba (CSV de `tabs_ocultos`) con Música vista o escondida. El
     * interruptor de la última página es el MISMO ajuste que la fila de Música de "Subforos de
     * arriba": no hay dos sitios que puedan decir cosas distintas.
     */
    fun ocultosConMusica(csv: String, visible: Boolean): String {
        val resto = OrdenBarra.leer(csv).filterNot { it == CLAVE_MUSICA }
        return OrdenBarra.guardar(if (visible) resto else resto + CLAVE_MUSICA)
    }

    fun musicaVisible(csv: String): Boolean = CLAVE_MUSICA !in OrdenBarra.leer(csv)

    /** Página a la que lleva el botón principal; null = se acaba la introducción. */
    fun siguiente(i: Int): Int? = if (i < PAGINAS.lastIndex) i + 1 else null
}
