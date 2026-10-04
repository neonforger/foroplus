package com.fcplus.forocoches

/**
 * Qué va en la barra de abajo y qué va en el panel del avatar.
 *
 * Hasta la 45 la barra tenía nueve botones con scroll lateral, y para llegar a Perfil había que
 * deslizarla. Rediseño aprobado por el dueño el 2026-10-02 (maqueta en MEMORY.md): **abajo solo
 * [HUECOS] botones**, lo que se usa a diario; todo lo demás vive en el panel que abre el avatar
 * de arriba a la derecha. Es lo que hacen Reddit y X, y lo que pide Material (3 a 5 destinos).
 *
 * Se apoya en [OrdenBarra] y en las MISMAS preferencias de siempre (orden + "ocultos"), con un
 * solo cambio de significado: **"oculto" ya no es "no se ve", es "va al panel"**. Por eso nada
 * desaparece nunca: lo que no cabe abajo sigue a un toque.
 *
 * Reglas:
 * 1. Abajo van los primeros [HUECOS] no ocultos, en el orden del usuario; los que sobren pasan
 *    al panel. Así, un botón nuevo que traiga una versión (regla 1 de [OrdenBarra]: lo
 *    desconocido se ve) entra abajo si hay hueco y si no, al panel.
 * 2. Inicio ([FIJOS]) va siempre abajo: es la raíz de la navegación.
 * 3. **Migración de la barra vieja**, que lee lo guardado por las versiones anteriores sin
 *    escribir nada: Citas (`quotes`) y Menciones (`notif`) se funden en **Avisos** (`avisos`),
 *    en el sitio de la primera de las dos, y solo va al panel si las dos estaban ocultas;
 *    Perfil (`profile`) sale del catálogo, porque ahora es el propio avatar.
 */
object BarraAbajo {

    const val HUECOS = 4

    /** El catálogo en el orden de fábrica: los cuatro primeros son la barra por defecto. */
    val CATALOGO = listOf(
        "home", "avisos", "pm", "favs",
        "mythreads", "participated", "mismensajes", "descargas"
    )

    val FIJOS = setOf("home")

    data class Reparto(val abajo: List<String>, val panel: List<String>)

    private val VIEJAS_AVISOS = setOf("quotes", "notif")

    /** El orden guardado, traducido a las claves de ahora. */
    fun migrarOrden(orden: List<String>): List<String> =
        orden.mapNotNull {
            when (it) {
                in VIEJAS_AVISOS -> "avisos"
                "profile" -> null
                else -> it
            }
        }.distinct()

    /** Lo que iba "oculto", traducido: Avisos al panel solo si Citas Y Menciones lo estaban. */
    fun migrarOcultos(ocultos: Set<String>): Set<String> {
        val habiaViejas = ocultos.any { it in VIEJAS_AVISOS }
        val resto = ocultos.filterNot { it in VIEJAS_AVISOS || it == "profile" }.toMutableSet()
        if (habiaViejas && ocultos.containsAll(VIEJAS_AVISOS)) resto.add("avisos")
        return resto
    }

    /** Lo que se pinta: abajo y en el panel, a partir de lo guardado (viejo o nuevo). */
    fun reparto(orden: List<String>, ocultos: Set<String>): Reparto {
        val todo = OrdenBarra.completo(CATALOGO, migrarOrden(orden))
        val enPanel = migrarOcultos(ocultos)
        val fijosAbajo = todo.count { it in FIJOS }
        val abajo = ArrayList<String>(HUECOS)
        var libres = HUECOS - fijosAbajo
        for (c in todo) {
            when {
                c in FIJOS -> abajo.add(c)
                c !in enPanel && libres > 0 -> { abajo.add(c); libres-- }
            }
        }
        return Reparto(abajo, todo.filterNot { it in abajo })
    }

    /** Cómo se guarda: el orden completo (abajo primero) y "ocultos" = lo que va al panel. */
    fun orden(r: Reparto): List<String> = r.abajo + r.panel
    fun ocultos(r: Reparto): Set<String> = r.panel.toSet()

    /**
     * Pasa [clave] al panel. null si no se puede: es fija, o no está abajo. Va al PRINCIPIO del
     * panel, que es donde la buscará quien acaba de quitarla.
     */
    fun alPanel(r: Reparto, clave: String): Reparto? {
        if (clave in FIJOS || clave !in r.abajo) return null
        return Reparto(r.abajo - clave, listOf(clave) + (r.panel - clave))
    }

    /**
     * Baja [clave] del panel a la barra. Con hueco libre va al final de la barra. Con la barra
     * llena hace falta [sustituye] (uno de abajo que no sea fijo), que ocupa el sitio de [clave]
     * en el panel; sin él devuelve null y quien llama pregunta cuál.
     */
    fun ponerAbajo(r: Reparto, clave: String, sustituye: String? = null): Reparto? {
        if (clave !in r.panel) return null
        if (r.abajo.size < HUECOS) return Reparto(r.abajo + clave, r.panel - clave)
        if (sustituye == null || sustituye in FIJOS || sustituye !in r.abajo) return null
        return Reparto(
            r.abajo.map { if (it == sustituye) clave else it },
            r.panel.map { if (it == clave) sustituye else it }
        )
    }

    /** Los que se pueden mandar al panel para hacer sitio (todo lo de abajo menos Inicio). */
    fun sustituibles(r: Reparto): List<String> = r.abajo.filterNot { it in FIJOS }

    /**
     * Tras arrastrar en la pantalla de organizar. La lista es abajo + panel, y lo que decide es
     * la POSICIÓN: los [r].abajo.size primeros van abajo. Arrastrar uno del panel hacia arriba lo
     * mete en la barra y empuja al último de abajo al panel, que es lo que se ve hacer con el dedo.
     * Inicio no puede salir de la barra: si se arrastra fuera, vuelve y sale el último.
     */
    fun trasArrastrar(r: Reparto, nuevoOrden: List<String>): Reparto {
        val todo = OrdenBarra.completo(CATALOGO, nuevoOrden)
        val n = r.abajo.size.coerceIn(1, HUECOS)
        val abajo = todo.take(n).toMutableList()
        for (f in FIJOS) {
            if (f in todo && f !in abajo) {
                val sale = abajo.lastOrNull { it !in FIJOS } ?: continue
                abajo[abajo.indexOf(sale)] = f
            }
        }
        val ordenAbajo = todo.filter { it in abajo }
        return Reparto(ordenAbajo, todo.filterNot { it in abajo })
    }
}
