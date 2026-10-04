package com.fcplus.forocoches

/**
 * Qué se ve en una barra y en qué orden, cuando lo decide el usuario.
 *
 * Sirve para las **dos**: las pestañas de subforo de arriba y los botones de abajo. Son el
 * mismo problema —"déjame decidir qué veo y en qué orden"— y tenerlo dos veces con reglas
 * distintas era justo el lío que se quería evitar. Por eso aquí no hay ni subforos ni botones,
 * solo **claves**: el fid para las pestañas, el nombre del botón para la barra de abajo.
 *
 * Las cuatro reglas que importan, y por qué:
 *
 * 1. **Se guarda lo OCULTO, nunca lo permitido.** Si guardásemos la lista de lo que se ve, un
 *    subforo que FC añada mañana —o un botón que traiga una versión nueva— sería invisible para
 *    siempre y nadie sabría por qué. Lo desconocido va al final y **se ve**. Misma familia que
 *    el gotcha 26: no apuntar un "no" que no se puede desmentir.
 * 2. **Lo que ya no existe no deja fantasmas.** El orden guardado se cruza con el catálogo de
 *    ahora, así que un subforo que FC quite no puede pintar una pestaña muerta.
 * 3. **La barra no se puede quedar vacía**, y lo marcado como fijo no se puede ocultar. Una
 *    barra sin nada es una pantalla sin salida, y el botón de Inicio es la raíz de toda la
 *    navegación de la app.
 * 4. **En la pantalla de organizar salen TODOS**, ocultos incluidos. Si lo oculto desapareciera
 *    también de ahí, la única forma de recuperarlo sería restablecerlo todo.
 */
object OrdenBarra {

    /** Lo que se pinta en la barra: el catálogo puesto en orden y sin lo oculto. */
    fun visibles(catalogo: List<String>, orden: List<String>, ocultos: Set<String>): List<String> =
        completo(catalogo, orden).filterNot { it in ocultos }

    /**
     * El catálogo entero en el orden del usuario, con lo que no conocía **al final**.
     *
     * Es lo que enseña la pantalla de organizar, y también la base de [visibles]: así el orden
     * es el mismo se vea lo que se vea.
     */
    fun completo(catalogo: List<String>, orden: List<String>): List<String> {
        val hay = catalogo.toSet()
        val puestos = orden.filter { it in hay }.distinct()
        return puestos + catalogo.filterNot { it in puestos }
    }

    /**
     * Oculta o vuelve a enseñar [clave]. Devuelve **null** si el cambio se rechaza —porque
     * dejaría la barra vacía o porque es un elemento fijo—, para que quien llame pueda decir
     * por qué en vez de dejar un toque sin efecto.
     */
    fun alternarOculto(
        catalogo: List<String>,
        ocultos: Set<String>,
        clave: String,
        fijos: Set<String>
    ): Set<String>? {
        if (clave in ocultos) return ocultos - clave      // volver a enseñar siempre se puede
        if (clave in fijos) return null
        val quedarian = catalogo.filterNot { it in ocultos || it == clave }
        if (quedarian.isEmpty()) return null
        return ocultos + clave
    }

    /** Mueve la fila [de] a la posición [a]. Fuera de rango, la lista se queda como estaba. */
    fun mover(lista: List<String>, de: Int, a: Int): List<String> {
        if (de !in lista.indices || a !in lista.indices || de == a) return lista
        val m = lista.toMutableList()
        m.add(a, m.removeAt(de))
        return m
    }

    fun leer(csv: String): List<String> =
        csv.split(',').map { it.trim() }.filter { it.isNotEmpty() }.distinct()

    fun guardar(claves: List<String>): String = claves.joinToString(",")
}
