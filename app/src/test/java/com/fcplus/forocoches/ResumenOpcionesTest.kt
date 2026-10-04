package com.fcplus.forocoches

import org.junit.Assert.assertEquals
import org.junit.Test

class ResumenOpcionesTest {

    @Test
    fun `apariencia con los titulos igual que los mensajes no repite el tamaño`() {
        assertEquals("Tema oscuro · letra normal",
            ResumenOpciones.apariencia(TemaApp.OSCURO, 1, 1))
    }

    @Test
    fun `apariencia con los titulos distintos lo dice`() {
        assertEquals("Tema claro · letra grande · títulos pequeños",
            ResumenOpciones.apariencia(TemaApp.CLARO, 2, 0))
    }

    @Test
    fun `el tema del sistema se nombra como tal`() {
        assertEquals("Tema del sistema · letra pequeña · títulos grandes",
            ResumenOpciones.apariencia(TemaApp.SISTEMA, 0, 2))
    }

    @Test
    fun `un indice fuera de rango se recorta como al leer la preferencia`() {
        // Mismo coerceIn que OptionsController.fontSp: el resumen dice lo que se aplica.
        assertEquals("Tema oscuro · letra grande · títulos pequeños",
            ResumenOpciones.apariencia(TemaApp.OSCURO, 7, -3))
    }

    @Test
    fun `filtros cuenta lo que hay, con singulares`() {
        assertEquals("12 palabras · 1 usuario · 3 hilos",
            ResumenOpciones.filtros(palabras = 12, palabrasActivas = true, usuarios = 1, hilos = 3))
        assertEquals("1 palabra · 2 usuarios · 1 hilo",
            ResumenOpciones.filtros(palabras = 1, palabrasActivas = true, usuarios = 2, hilos = 1))
    }

    @Test
    fun `el filtro de palabras apagado se avisa`() {
        // Tener 18 palabras guardadas y el filtro apagado no es lo mismo que filtrarlas.
        assertEquals("18 palabras (apagado)",
            ResumenOpciones.filtros(palabras = 18, palabrasActivas = false, usuarios = 0, hilos = 0))
    }

    @Test
    fun `sin nada se dice sin filtros`() {
        assertEquals("Sin filtros",
            ResumenOpciones.filtros(palabras = 0, palabrasActivas = true, usuarios = 0, hilos = 0))
    }

    @Test
    fun `personalizar menciona el popurri solo si tiene subforos`() {
        assertEquals("Popurrí con 3 subforos · barras de arriba y de abajo",
            ResumenOpciones.personalizar(3))
        assertEquals("Popurrí con 1 subforo · barras de arriba y de abajo",
            ResumenOpciones.personalizar(1))
        assertEquals("Popurrí y barras de arriba y de abajo", ResumenOpciones.personalizar(0))
    }

    @Test
    fun `publicar dice si va la firma`() {
        assertEquals("Con la firma «Enviado desde ForoPlus»", ResumenOpciones.publicar(true))
        assertEquals("Sin firma", ResumenOpciones.publicar(false))
    }
}
