package com.fcplus.forocoches

import java.text.Normalizer

/** Un sitio al que se puede ir: una pestaña de la barra (clave = su tag) o un subforo suelto. */
data class Sitio(val clave: Int, val nombre: String)

data class FilaSitio(val clave: Int, val nombre: String, val actual: Boolean)

data class SeccionSitios(val titulo: String, val filas: List<FilaSitio>)

/** Lo que enseña el selector de sitio (puro): las dos secciones, el filtro y la marca. */
object ListaSitios {
    const val EN_BARRA = "EN TU BARRA"
    const val TODOS = "TODOS LOS SUBFOROS"

    private val MARCAS = Regex("\\p{M}+")

    /** Sin tildes ni mayúsculas, para comparar y ordenar. */
    fun normalizar(s: String): String =
        Normalizer.normalize(s, Normalizer.Form.NFD).replace(MARCAS, "").lowercase()

    /**
     * [enBarra]: las pestañas, en el orden de la barra. [conocidos]: todos los subforos que se
     * conocen; los que no estén en la barra forman "TODOS LOS SUBFOROS", alfabéticos. [actual]
     * es la clave del sitio en el que se está. Una sección sin filas (por el filtro o porque no
     * hay nada) no sale.
     */
    fun secciones(enBarra: List<Sitio>, conocidos: List<Sitio>, actual: Int, consulta: String): List<SeccionSitios> {
        val q = normalizar(consulta.trim())
        val claves = enBarra.map { it.clave }.toSet()
        val resto = conocidos.filter { it.clave !in claves }.sortedBy { normalizar(it.nombre) }
        fun seccion(titulo: String, sitios: List<Sitio>) = SeccionSitios(
            titulo,
            sitios.filter { q.isEmpty() || normalizar(it.nombre).contains(q) }
                .map { FilaSitio(it.clave, it.nombre, it.clave == actual) },
        )
        return listOf(seccion(EN_BARRA, enBarra), seccion(TODOS, resto)).filter { it.filas.isNotEmpty() }
    }
}
