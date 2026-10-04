package com.fcplus.forocoches

import org.junit.Assert.assertEquals
import org.junit.Test

class CompartirFCTest {

    @Test
    fun `el enlace del hilo no lleva pagina ni parametros de trabajo`() {
        assertEquals(
            "https://forocoches.com/foro/showthread.php?t=10776223",
            CompartirFC.enlaceHilo("10776223")
        )
    }

    @Test
    fun `el enlace del mensaje salta al mensaje`() {
        assertEquals(
            "https://forocoches.com/foro/showthread.php?p=517620100#post517620100",
            CompartirFC.enlaceMensaje("517620100")
        )
    }

    @Test
    fun `un id vacio o con basura no genera un enlace roto`() {
        assertEquals("", CompartirFC.enlaceHilo(""))
        assertEquals("", CompartirFC.enlaceHilo("   "))
        assertEquals("", CompartirFC.enlaceMensaje("abc"))
        // un id con espacios alrededor sí vale
        assertEquals(
            "https://forocoches.com/foro/showthread.php?t=123",
            CompartirFC.enlaceHilo(" 123 ")
        )
    }

    @Test
    fun `el titulo pierde el sufijo de pagina de vBulletin`() {
        assertEquals("Mi hilo", CompartirFC.tituloLimpio("Mi hilo - Página 2"))
        assertEquals("Mi hilo", CompartirFC.tituloLimpio("Mi hilo - Pagina 17  "))
        assertEquals("Mi hilo", CompartirFC.tituloLimpio("Mi hilo – Página 3"))
        // lo que NO es un sufijo de página se respeta
        assertEquals("Página del piloto", CompartirFC.tituloLimpio("Página del piloto"))
        assertEquals("Hilo - Parte 2", CompartirFC.tituloLimpio("Hilo - Parte 2"))
    }

    @Test
    fun `compartir desde la pagina 2 no arrastra el numero de pagina`() {
        val r = CompartirFC.textoHilo("España quiebra en 2027 - Página 2", "10781620")
        assertEquals(true, r.startsWith("España quiebra en 2027"))
        assertEquals(true, r.endsWith("showthread.php?t=10781620"))
        assertEquals(false, r.contains("Página"))
    }

    @Test
    fun `el texto del hilo lleva titulo y enlace`() {
        assertEquals(
            "Peña Real Oviedo\nhttps://forocoches.com/foro/showthread.php?t=99",
            CompartirFC.textoHilo("Peña Real Oviedo", "99")
        )
    }

    @Test
    fun `el texto del mensaje dice de quien es`() {
        assertEquals(
            "@albeeR en «Puntua mi flan»\nhttps://forocoches.com/foro/showthread.php?p=5#post5",
            CompartirFC.textoMensaje("albeeR", "Puntua mi flan", "5")
        )
    }

    @Test
    fun `el arroba no se duplica si el autor ya lo trae`() {
        val r = CompartirFC.textoMensaje("@albeeR", "Hilo", "5")
        assertEquals(true, r.startsWith("@albeeR en"))
    }

    @Test
    fun `sin titulo el texto sigue teniendo sentido`() {
        val r = CompartirFC.textoMensaje("albeeR", "  ", "5")
        assertEquals(true, r.startsWith("@albeeR en ForoCoches"))
    }

    @Test
    fun `se coge la primera foto del mensaje`() {
        val html = """<p>hola</p><img src="https://i.imgur.com/uno.jpg"><img src="https://i.imgur.com/dos.jpg">"""
        assertEquals("https://i.imgur.com/uno.jpg", CompartirFC.primeraImagen(html))
    }

    @Test
    fun `los smileys no cuentan como foto`() {
        val html = """<img src="https://forocoches.com/foro/images/smilies/risa.gif"><img src="https://i.imgur.com/real.png">"""
        assertEquals("https://i.imgur.com/real.png", CompartirFC.primeraImagen(html))
    }

    @Test
    fun `un mensaje sin fotos devuelve vacio`() {
        assertEquals("", CompartirFC.primeraImagen("<p>solo texto</p>"))
        assertEquals("", CompartirFC.primeraImagen(""))
    }

    @Test
    fun `las rutas relativas se ignoran`() {
        assertEquals("", CompartirFC.primeraImagen("""<img src="/foro/images/x.png">"""))
    }

    @Test
    fun `el cuerpo colapsa las rachas de lineas en blanco`() {
        assertEquals("uno\n\ndos", CompartirFC.cuerpoTarjeta("uno\n\n\n\n\ndos"))
    }

    @Test
    fun `los huecos de las imagenes no salen como cajitas`() {
        // Un post que es solo dos fotos llega con los huecos de las imagenes y nada mas:
        // sin limpiarlo, la tarjeta pintaba dos cajitas punteadas con OBJ dentro.
        val soloFotos = "\uFFFC\n\n\n\uFFFC "
        assertEquals("", CompartirFC.cuerpoTarjeta(soloFotos))
        assertEquals("mira esto", CompartirFC.cuerpoTarjeta("\uFFFC mira esto"))
    }

    @Test
    fun `un cuerpo corto se deja intacto`() {
        assertEquals("hola qué tal", CompartirFC.cuerpoTarjeta("hola qué tal"))
    }

    @Test
    fun `un cuerpo largo se corta por palabra entera`() {
        val largo = "palabra ".repeat(200)
        val r = CompartirFC.cuerpoTarjeta(largo, maxCaracteres = 50)
        assertEquals(true, r.length <= 51)          // 50 + el puntito
        assertEquals(true, r.endsWith("…"))
        assertEquals(false, r.contains("palab…"))   // nunca a mitad de palabra
    }

    @Test
    fun `una parrafada sin espacios tambien se corta`() {
        val r = CompartirFC.cuerpoTarjeta("a".repeat(300), maxCaracteres = 40)
        assertEquals(41, r.length)
        assertEquals(true, r.endsWith("…"))
    }
}
