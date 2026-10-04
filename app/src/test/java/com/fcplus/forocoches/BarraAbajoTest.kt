package com.fcplus.forocoches

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BarraAbajoTest {

    private fun r(orden: List<String> = emptyList(), ocultos: Set<String> = emptySet()) =
        BarraAbajo.reparto(orden, ocultos)

    @Test
    fun `de fabrica - Inicio, Avisos, Privados y Suscripciones abajo`() {
        val x = r()
        assertEquals(listOf("home", "avisos", "pm", "favs"), x.abajo)
        assertEquals(listOf("mythreads", "participated", "mismensajes", "descargas"), x.panel)
    }

    @Test
    fun `nada desaparece - abajo mas panel es siempre el catalogo entero`() {
        for (x in listOf(r(), r(listOf("descargas", "pm")), r(ocultos = setOf("pm", "favs", "avisos")))) {
            assertEquals(BarraAbajo.CATALOGO.toSet(), (x.abajo + x.panel).toSet())
            assertEquals(BarraAbajo.CATALOGO.size, x.abajo.size + x.panel.size)
        }
    }

    @Test
    fun `nunca mas de cuatro abajo, aunque no haya nada oculto`() {
        assertEquals(4, r(BarraAbajo.CATALOGO.reversed()).abajo.size)
    }

    @Test
    fun `la barra vieja de fabrica se migra a la nueva de fabrica`() {
        // Quien nunca tocó la barra no tiene orden guardado: le toca la nueva por defecto.
        assertEquals(listOf("home", "avisos", "pm", "favs"), r(emptyList(), emptySet()).abajo)
    }

    @Test
    fun `Citas y Menciones se funden en Avisos en el sitio de la primera`() {
        val viejo = listOf("home", "pm", "notif", "quotes", "profile", "favs", "mythreads")
        assertEquals(listOf("home", "pm", "avisos", "favs"), r(viejo).abajo)
    }

    @Test
    fun `Avisos va al panel solo si Citas y Menciones estaban ocultas`() {
        val viejo = listOf("home", "quotes", "notif", "pm", "favs", "mythreads")
        assertTrue("avisos" in r(viejo, setOf("quotes")).abajo)
        assertTrue("avisos" in r(viejo, setOf("notif")).abajo)
        assertTrue("avisos" in r(viejo, setOf("quotes", "notif")).panel)
    }

    @Test
    fun `lo que el usuario escondio en la barra vieja va al panel, no desaparece`() {
        val x = r(listOf("home", "quotes", "notif", "pm", "favs", "descargas"), setOf("descargas", "pm"))
        assertTrue("descargas" in x.panel)
        assertTrue("pm" in x.panel)
        assertEquals(listOf("home", "avisos", "favs", "mythreads"), x.abajo)
    }

    @Test
    fun `Perfil sale del catalogo - ahora es el avatar`() {
        val x = r(listOf("profile", "home"), setOf())
        assertTrue("profile" !in x.abajo + x.panel)
    }

    @Test
    fun `Inicio va siempre abajo aunque se hubiera guardado oculto`() {
        assertTrue("home" in r(ocultos = setOf("home")).abajo)
    }

    @Test
    fun `guardar y volver a leer da lo mismo`() {
        val x = r(listOf("descargas", "home", "pm"), setOf("avisos"))
        assertEquals(x, BarraAbajo.reparto(BarraAbajo.orden(x), BarraAbajo.ocultos(x)))
    }

    @Test
    fun `pasar al panel - va al principio del panel, Inicio no se puede`() {
        val x = BarraAbajo.alPanel(r(), "pm")!!
        assertEquals(listOf("home", "avisos", "favs"), x.abajo)
        assertEquals("pm", x.panel.first())
        assertNull(BarraAbajo.alPanel(r(), "home"))
        assertNull(BarraAbajo.alPanel(r(), "descargas"))   // no está abajo
    }

    @Test
    fun `poner abajo con hueco va al final de la barra`() {
        val conHueco = BarraAbajo.alPanel(r(), "pm")!!
        val x = BarraAbajo.ponerAbajo(conHueco, "descargas")!!
        assertEquals(listOf("home", "avisos", "favs", "descargas"), x.abajo)
    }

    @Test
    fun `poner abajo con la barra llena pide a quien sustituye`() {
        assertNull(BarraAbajo.ponerAbajo(r(), "descargas"))
        assertNull(BarraAbajo.ponerAbajo(r(), "descargas", sustituye = "home"))
        val x = BarraAbajo.ponerAbajo(r(), "descargas", sustituye = "pm")!!
        assertEquals(listOf("home", "avisos", "descargas", "favs"), x.abajo)
        assertEquals(listOf("mythreads", "participated", "mismensajes", "pm"), x.panel)
        assertEquals(listOf("avisos", "pm", "favs"), BarraAbajo.sustituibles(r()))
    }

    @Test
    fun `arrastrar uno del panel arriba lo mete y empuja al ultimo de abajo`() {
        val antes = r()
        val arrastrado = listOf("home", "descargas", "avisos", "pm", "favs", "mythreads", "participated", "mismensajes")
        val x = BarraAbajo.trasArrastrar(antes, arrastrado)
        assertEquals(listOf("home", "descargas", "avisos", "pm"), x.abajo)
        assertEquals("favs", x.panel.first())
    }

    @Test
    fun `Inicio arrastrado fuera de la barra vuelve`() {
        val antes = r()
        val arrastrado = listOf("avisos", "pm", "favs", "mythreads", "home", "participated", "mismensajes", "descargas")
        val x = BarraAbajo.trasArrastrar(antes, arrastrado)
        assertTrue("home" in x.abajo)
        assertEquals(4, x.abajo.size)
        assertTrue("mythreads" in x.panel)
    }
}
