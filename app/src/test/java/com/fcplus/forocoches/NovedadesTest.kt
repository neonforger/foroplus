package com.fcplus.forocoches

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/** Robolectric por `org.json`: en la JVM pelada es un stub que lo devuelve todo vacío. */
@RunWith(RobolectricTestRunner::class)
class NovedadesTest {

    private val json = """
        {"versiones": [
          {"code": 42, "nombre": "1.7.0", "fecha": "2026-09-21", "notas": ["Descargas", "  "]},
          {"code": 43, "nombre": "1.8.0", "fecha": "2026-09-24", "notas": ["Encuestas", "Citas"]},
          {"code": 44, "nombre": "1.9.0", "fecha": "2026-10-01", "notas": ["Futuro"]}
        ]}
    """.trimIndent()

    @Test
    fun `se leen de la mas nueva a la mas vieja y sin notas vacias`() {
        val v = Novedades.leer(json)
        assertEquals(listOf(44, 43, 42), v.map { it.codigo })
        assertEquals(listOf("Descargas"), v.last().notas)
    }

    @Test
    fun `no se enseñan versiones posteriores a la instalada`() {
        // Una versión en el fichero que aún no se ha publicado no es "novedad" de nadie.
        val v = Novedades.hasta(Novedades.leer(json), 43)
        assertEquals(listOf(43, 42), v.map { it.codigo })
    }

    @Test
    fun `sin saber la version instalada se enseña todo`() {
        assertEquals(3, Novedades.hasta(Novedades.leer(json), 0).size)
    }

    @Test
    fun `un fichero roto no tumba la pantalla`() {
        assertTrue(Novedades.leer("no es json").isEmpty())
        assertTrue(Novedades.leer("""{"versiones": [{"nombre": "sin code"}]}""").isEmpty())
    }

    @Test
    fun `fecha legible en español`() {
        assertEquals("24 sep 2026", Novedades.fechaLegible("2026-09-24"))
        assertEquals("1 ago 2026", Novedades.fechaLegible("2026-08-01"))
        assertEquals("", Novedades.fechaLegible("ayer"))
    }

    @Test
    fun `cabecera con fecha y sin ella`() {
        assertEquals("1.8.0 · 24 sep 2026",
            Novedades.cabecera(VersionNotas(43, "1.8.0", "2026-09-24", emptyList())))
        assertEquals("1.8.0", Novedades.cabecera(VersionNotas(43, "1.8.0", "", emptyList())))
    }

    /**
     * El guardián del ritual de publicación: la versión que se compila TIENE que estar en el
     * historial que ve la gente. Si esto falla al subir el `versionCode`, falta añadir su
     * bloque a `assets/novedades.json` (las mismas notas que van a `fc_config.json`).
     */
    @Test
    fun `la version que se compila tiene sus notas en el historial`() {
        val gradle = File("build.gradle")
        val fichero = File("src/main/assets/novedades.json")
        if (!gradle.isFile || !fichero.isFile) return   // otro directorio de trabajo
        val codigo = Regex("""versionCode\s+(\d+)""").find(gradle.readText())!!
            .groupValues[1].toInt()
        val versiones = Novedades.leer(fichero.readText())
        val esta = versiones.firstOrNull { it.codigo == codigo }
        assertTrue("assets/novedades.json no tiene la versión $codigo", esta != null)
        assertTrue("la versión $codigo no tiene notas", esta!!.notas.isNotEmpty())
    }
}
