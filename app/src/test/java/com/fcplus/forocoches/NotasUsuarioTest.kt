package com.fcplus.forocoches

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NotasUsuarioTest {

    private class MapaAlmacen(val m: MutableMap<String, String> = mutableMapOf()) : NotasUsuario.Almacen {
        override fun leer(clave: String): String? = m[clave]
        override fun escribir(clave: String, valor: String?) {
            if (valor == null) m.remove(clave) else m[clave] = valor
        }
    }

    // --- ClaveNota ---

    @Test fun `clave por uid cuando lo hay`() {
        assertEquals("u:911128", ClaveNota.de("911128", "Pepe"))
    }

    @Test fun `uid vacio o 0 cae al nick en minusculas`() {
        assertEquals("n:pepe", ClaveNota.de("", "Pepe"))
        assertEquals("n:pepe", ClaveNota.de("0", "Pepe"))
    }

    @Test fun `el nick no distingue mayusculas ni espacios de sobra`() {
        assertEquals(ClaveNota.de("", "Pepe"), ClaveNota.de("", "  pEpE  "))
    }

    @Test fun `sin uid ni nick no hay clave`() {
        assertNull(ClaveNota.de("", "   "))
        assertNull(ClaveNota.de("0", ""))
    }

    // --- NotasUsuario ---

    @Test fun `poner y leer por uid`() {
        val n = NotasUsuario(MapaAlmacen())
        n.poner("911128", "Pepe", "me debe 20 del fantasy")
        assertEquals("me debe 20 del fantasy", n.get("911128", "Pepe"))
        assertTrue(n.hay("911128", "Pepe"))
    }

    @Test fun `recorta el texto al guardar`() {
        val n = NotasUsuario(MapaAlmacen())
        n.poner("1", "P", "   hola   ")
        assertEquals("hola", n.get("1", "P"))
    }

    @Test fun `texto vacio o en blanco borra la nota`() {
        val a = MapaAlmacen()
        val n = NotasUsuario(a)
        n.poner("1", "P", "algo")
        n.poner("1", "P", "    ")
        assertEquals("", n.get("1", "P"))
        assertFalse(n.hay("1", "P"))
        assertTrue("no debe quedar basura", a.m.isEmpty())
    }

    @Test fun `el uid manda sobre el nick`() {
        val n = NotasUsuario(MapaAlmacen())
        n.poner("1", "Pepe", "por uid")
        // Mismo uid, otro nick (se renombró): sigue siendo su nota.
        assertEquals("por uid", n.get("1", "OtroNombre"))
        // Mismo nick, otro uid (otra persona que cogió el nick): sin nota.
        assertEquals("", n.get("2", "Pepe"))
    }

    @Test fun `sin uid, dos nicks distintos no se mezclan`() {
        val n = NotasUsuario(MapaAlmacen())
        n.poner("", "Ana", "a")
        n.poner("", "Luis", "l")
        assertEquals("a", n.get("", "Ana"))
        assertEquals("l", n.get("", "Luis"))
    }

    @Test fun `sin identidad no se puede poner nota`() {
        val a = MapaAlmacen()
        NotasUsuario(a).poner("", "   ", "lo que sea")
        assertTrue(a.m.isEmpty())
    }

    @Test fun `sin nota, get da vacio y hay es falso`() {
        val n = NotasUsuario(MapaAlmacen())
        assertEquals("", n.get("99", "Nadie"))
        assertFalse(n.hay("99", "Nadie"))
    }
}
