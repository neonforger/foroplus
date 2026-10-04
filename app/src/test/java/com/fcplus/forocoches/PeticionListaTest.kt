package com.fcplus.forocoches

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PeticionListaTest {

    private val general = "https://forocoches.com/foro/forumdisplay.php?f=2"
    private val informatica = "https://forocoches.com/foro/forumdisplay.php?f=17"

    @Test
    fun `la respuesta de otro subforo no se pinta en la pestaña nueva`() {
        // Zorro, 2026-09-13: pestaña General marcada y la lista entera de Videojuegos.
        // Reproducido el 2026-09-25 tocando dos pestañas seguidas.
        assertFalse(PeticionLista.aceptar(general, esperada = informatica))
    }

    @Test
    fun `la respuesta que se esperaba si se pinta`() {
        assertTrue(PeticionLista.aceptar(informatica, esperada = informatica))
    }

    @Test
    fun `una pagina 2 que llega tarde no se cuela en otro subforo`() {
        // DanyCup92, 2026-09-25: hilos de General e Informática mezclados.
        assertFalse(PeticionLista.aceptar("$general&page=2", esperada = informatica))
    }

    @Test
    fun `una pagina 2 que llega despues de recargar no se añade a la pagina 1 nueva`() {
        assertFalse(PeticionLista.aceptar("$general&page=2", esperada = general))
    }

    @Test
    fun `las busquedas no son listas de subforo y pasan si no se espera un subforo`() {
        assertTrue(PeticionLista.aceptar("https://forocoches.com/foro/search.php?searchid=1", esperada = ""))
        assertTrue(PeticionLista.aceptar("", esperada = ""))
    }

    @Test
    fun `un subforo que llega cuando ya no se espera ninguno se tira`() {
        // Te has ido a Mis hilos con la lista del subforo todavía en vuelo.
        assertFalse(PeticionLista.aceptar(general, esperada = ""))
        assertFalse(PeticionLista.aceptar(
            "https://forocoches.com/foro/subscription.php?_fp=1", esperada = ""))
    }

    @Test
    fun `una pagina 1 sale siempre aunque haya otra en vuelo`() {
        // Cambiar de pestaña o recargar es una orden del usuario: tragársela es lo que dejaba
        // la pestaña nueva con los hilos de la vieja, o la lista congelada al recargar.
        assertTrue(PeticionLista.hayQuePedir(pagina = 1, cargando = true))
        assertTrue(PeticionLista.hayQuePedir(pagina = 1, cargando = false))
    }

    @Test
    fun `las paginas siguientes no se amontonan`() {
        // El scroll pide la siguiente a ráfagas: esa sí se frena mientras hay una en vuelo.
        assertFalse(PeticionLista.hayQuePedir(pagina = 2, cargando = true))
        assertTrue(PeticionLista.hayQuePedir(pagina = 2, cargando = false))
    }
}
