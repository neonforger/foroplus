package com.fcplus.forocoches

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PaginacionMensajesTest {

    private fun fila(pid: String) = NoticeItem(
        url = "https://forocoches.com/foro/showthread.php?p=$pid&highlight=#post$pid",
        title = "Hilo", who = "", text = ""
    )

    @Test
    fun `se pagina sobre el searchid al que redirige FC`() {
        val base = PaginacionMensajes.base("https://forocoches.com/foro/search.php?searchid=987654")
        assertEquals("https://forocoches.com/foro/search.php?searchid=987654", base)
        assertEquals(
            "https://forocoches.com/foro/search.php?searchid=987654&page=3",
            PaginacionMensajes.pagina(base!!, 3)
        )
    }

    @Test
    fun `sin searchid no se pagina - seria lanzar otra busqueda entera por pagina`() {
        assertNull(
            PaginacionMensajes.base(
                "https://forocoches.com/foro/search.php?do=process&searchuser=Yo&exactname=1&showposts=1"
            )
        )
        assertNull(PaginacionMensajes.base(""))
    }

    @Test
    fun `la base no arrastra la pagina que ya traia`() {
        assertEquals(
            "https://forocoches.com/foro/search.php?searchid=5",
            PaginacionMensajes.base("https://forocoches.com/foro/search.php?searchid=5&page=4")
        )
    }

    @Test
    fun `una pagina repetida no aporta nada - la lista esta agotada (gotcha 37)`() {
        val pintados = listOf("1", "2", "3").map { fila(it).url }
        assertTrue(PaginacionMensajes.nuevos(pintados, listOf(fila("2"), fila("3"))).isEmpty())
    }

    @Test
    fun `se queda solo con lo nuevo, en su orden y sin repetirlo`() {
        val pintados = listOf(fila("1").url)
        val llegan = listOf(fila("1"), fila("4"), fila("5"), fila("4"))
        assertEquals(
            listOf("4", "5"),
            PaginacionMensajes.nuevos(pintados, llegan).map { PaginacionMensajes.clave(it.url) }
        )
    }

    @Test
    fun `se compara por numero de mensaje aunque cambie el highlight`() {
        val pintados = listOf("https://forocoches.com/foro/showthread.php?p=7&highlight=hola#post7")
        val llega = NoticeItem(
            url = "https://forocoches.com/foro/showthread.php?p=7&highlight=#post7",
            title = "", who = "", text = ""
        )
        assertTrue(PaginacionMensajes.nuevos(pintados, listOf(llega)).isEmpty())
    }
}
