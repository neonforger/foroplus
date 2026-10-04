package com.fcplus.forocoches

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OrdenBarraTest {

    /** Como lo daría la fuente: General, Electrónica, Videojuegos, Ayuda. */
    private val catalogo = listOf("2", "17", "43", "12")

    // ── Qué se ve en la barra ─────────────────────────────────────────────────

    @Test
    fun `sin nada guardado se respeta el orden de la fuente`() {
        assertEquals(catalogo, OrdenBarra.visibles(catalogo, emptyList(), emptySet()))
    }

    @Test
    fun `el orden del usuario manda sobre el de la fuente`() {
        assertEquals(
            listOf("43", "2", "17", "12"),
            OrdenBarra.visibles(catalogo, listOf("43", "2", "17", "12"), emptySet())
        )
    }

    @Test
    fun `lo que aparece nuevo va al final y se VE`() {
        // FC añade un subforo (o una versión nueva añade un botón). Si guardáramos la lista de
        // lo PERMITIDO, lo nuevo sería invisible para siempre y nadie sabría por qué.
        val conNuevo = catalogo + "99"
        assertEquals(
            listOf("43", "2", "17", "12", "99"),
            OrdenBarra.visibles(conNuevo, listOf("43", "2", "17", "12"), emptySet())
        )
    }

    @Test
    fun `lo que ya no existe no deja fantasmas`() {
        // El usuario ordenó un subforo que FC ha quitado: no puede salir una pestaña muerta.
        assertEquals(
            listOf("43", "2"),
            OrdenBarra.visibles(listOf("2", "43"), listOf("43", "666", "2"), emptySet())
        )
    }

    @Test
    fun `los ocultos no salen en la barra`() {
        assertEquals(
            listOf("2", "43"),
            OrdenBarra.visibles(catalogo, emptyList(), setOf("17", "12"))
        )
    }

    @Test
    fun `pero en la pantalla de organizar salen TODOS, para poder devolverlos`() {
        // Si un elemento oculto desapareciera también del editor, no habría forma de
        // recuperarlo salvo restablecerlo todo.
        assertEquals(
            listOf("43", "2", "17", "12"),
            OrdenBarra.completo(catalogo, listOf("43", "2", "17", "12"))
        )
    }

    // ── Qué NO se deja hacer ──────────────────────────────────────────────────

    @Test
    fun `no se puede dejar la barra vacia`() {
        // Ocultar el último deja una barra sin nada: una pantalla sin salida.
        val casiTodo = setOf("17", "43", "12")
        assertNull(OrdenBarra.alternarOculto(catalogo, casiTodo, "2", emptySet()))
    }

    @Test
    fun `lo fijo no se puede ocultar por mucho que quede sitio`() {
        // Inicio es la raíz de la app: sin él no se vuelve al listado desde ninguna parte.
        assertNull(OrdenBarra.alternarOculto(catalogo, emptySet(), "2", fijos = setOf("2")))
    }

    @Test
    fun `ocultar y volver a enseñar`() {
        val tras = OrdenBarra.alternarOculto(catalogo, emptySet(), "17", emptySet())
        assertEquals(setOf("17"), tras)
        assertEquals(emptySet<String>(), OrdenBarra.alternarOculto(catalogo, tras!!, "17", emptySet()))
    }

    @Test
    fun `enseñar de nuevo lo fijo tampoco hace falta, pero no revienta`() {
        assertEquals(emptySet<String>(), OrdenBarra.alternarOculto(catalogo, setOf("2"), "2", setOf("2")))
    }

    // ── Mover filas ───────────────────────────────────────────────────────────

    @Test
    fun `mover una fila hacia arriba y hacia abajo`() {
        assertEquals(listOf("17", "2", "43", "12"), OrdenBarra.mover(catalogo, 1, 0))
        assertEquals(listOf("17", "43", "12", "2"), OrdenBarra.mover(catalogo, 0, 3))
    }

    @Test
    fun `mover fuera de rango deja la lista como estaba`() {
        assertEquals(catalogo, OrdenBarra.mover(catalogo, 0, 9))
        assertEquals(catalogo, OrdenBarra.mover(catalogo, -1, 0))
        assertEquals(catalogo, OrdenBarra.mover(catalogo, 2, 2))
    }

    @Test
    fun `mover no pierde ni duplica nada`() {
        val r = OrdenBarra.mover(catalogo, 3, 1)
        assertEquals(catalogo.size, r.size)
        assertEquals(catalogo.toSet(), r.toSet())
    }

    // ── Lo que se guarda ──────────────────────────────────────────────────────

    @Test
    fun `las preferencias van y vienen`() {
        assertEquals("2,17,43", OrdenBarra.guardar(listOf("2", "17", "43")))
        assertEquals(listOf("2", "17", "43"), OrdenBarra.leer("2,17,43"))
        assertEquals(emptyList<String>(), OrdenBarra.leer(""))
        assertEquals(listOf("2"), OrdenBarra.leer(",2, ,"))
    }

    @Test
    fun `una preferencia con repetidos no duplica pestañas`() {
        assertEquals(listOf("2", "17"), OrdenBarra.leer("2,17,2"))
    }
}
