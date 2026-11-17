package com.fcplus.forocoches

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ContactosTest {

    // Esqueleto real de profile.php?do=buddylist (medido el 2026-11-17, anonimizado): la lista,
    // el form de añadir por nombre y, oculta, la de peticiones de amistad con el MISMO molde.
    private fun pagina(filas: String, peticiones: String = "") = """
        <html><body>
        <span>Total: <span id="buddycount">0</span></span>
        <form action="profile.php?do=updatelist&amp;userlist=buddy" method="post" id="buddylist_change_form">
          <input type="hidden" name="securitytoken" value="0000000000-0000000000000000000000000000000000000000" />
          <input type="hidden" name="do" value="updatelist" />
          <input type="hidden" name="userlist" value="buddy" />
          <div id="buddylist">$filas</div>
        </form>
        <form action="profile.php?do=updatelist&amp;userlist=buddy" method="post" id="buddylist_add_form">
          <input type="text" name="username" id="buddylist_add_txt" value="" />
        </form>
        <div style="display: none"><ul id="incomingreqs">$peticiones</ul></div>
        </body></html>
    """.trimIndent()

    // La fila, con el molde de la de ignorados (userlist_bit de vBulletin).
    private fun fila(uid: String, nombre: String) = """
        <div id="user$uid">
          <input type="checkbox" name="listbits[buddy][$uid]" id="usercheck_$uid" value="$uid" checked="checked" />
          <a href="member.php?u=$uid">$nombre</a>
          <input type="hidden" name="listbits[buddy_original][$uid]" value="$uid" />
        </div>
    """

    @Test
    fun `lista vacia - la que se midio`() {
        assertEquals(emptyList<Contacto>(), ContactosParser.parse(pagina("")))
    }

    @Test
    fun `lee uid y nombre de cada fila`() {
        val r = ContactosParser.parse(pagina(fila("111", "Fulanito") + fila("222", "Menganita-46")))
        assertEquals(listOf(Contacto("111", "Fulanito"), Contacto("222", "Menganita-46")), r)
    }

    @Test
    fun `las peticiones de amistad no son contactos`() {
        val r = ContactosParser.parse(pagina(fila("111", "Fulanito"), peticiones = fila("333", "Zutano")))
        assertEquals(listOf(Contacto("111", "Fulanito")), r)
    }

    @Test
    fun `si la fila pierde el id, el checkbox basta`() {
        val sinId = """<div><input type="checkbox" name="listbits[buddy][444]" value="444" checked />
            <a href="member.php?u=444">Perengano</a></div>"""
        assertEquals(listOf(Contacto("444", "Perengano")), ContactosParser.parse(pagina(sinId)))
    }

    @Test
    fun `sin la lista en la pagina, nada (de invitado o si FC la cambia)`() {
        assertEquals(emptyList<Contacto>(), ContactosParser.parse("<html><body>${fila("1", "x")}</body></html>"))
    }

    @Test
    fun `uid valido - ni vacio ni el 0 enmascarado`() {
        assertTrue(Contactos.uidValido("545278"))
        assertFalse(Contactos.uidValido("0"))
        assertFalse(Contactos.uidValido("000"))
        assertFalse(Contactos.uidValido(""))
        assertFalse(Contactos.uidValido(null))
        assertFalse(Contactos.uidValido("12a"))
    }

    @Test
    fun `es contacto por uid`() {
        val l = listOf(Contacto("111", "Fulanito"))
        assertTrue(Contactos.esContacto("111", l))
        assertFalse(Contactos.esContacto("222", l))
        assertFalse(Contactos.esContacto("0", listOf(Contacto("0", "x"))))
    }

    @Test
    fun `etiqueta ofrece lo contrario de lo que ya pasa`() {
        assertEquals("Añadir a contactos", Contactos.etiqueta(false))
        assertEquals("Quitar de contactos", Contactos.etiqueta(true))
    }

    @Test
    fun `anadir sale bien solo si la lista nueva lo trae`() {
        val con = listOf(Contacto("111", "Fulanito"))
        assertEquals(true to "Añadido a tus contactos", Contactos.resultado("add", "111", true, con, ""))
        assertEquals(false to "No se pudo añadir a tus contactos",
            Contactos.resultado("add", "111", true, emptyList(), ""))
    }

    @Test
    fun `quitar sale bien solo si la lista nueva ya no lo trae`() {
        assertEquals(true to "Quitado de tus contactos",
            Contactos.resultado("remove", "111", true, emptyList(), ""))
        assertEquals(false to "No se pudo quitar de tus contactos",
            Contactos.resultado("remove", "111", true, listOf(Contacto("111", "F")), ""))
    }

    @Test
    fun `si no salio, se ensena el texto de FC`() {
        val motivo = "No puedes añadirte a ti mismo a tu lista de contactos."
        assertEquals(false to motivo, Contactos.resultado("add", "111", true, emptyList(), motivo))
        assertEquals(false to motivo, Contactos.resultado("add", "111", false, emptyList(), motivo))
    }

    @Test
    fun `si salio, el texto de FC no se ensena`() {
        assertEquals(true to "Añadido a tus contactos",
            Contactos.resultado("add", "111", true, listOf(Contacto("111", "F")), "Algo que pinto FC aqui."))
    }

    @Test
    fun `fallos sin texto de FC - avisos propios`() {
        assertEquals(false to "Inicia sesión para gestionar tus contactos",
            Contactos.resultado("add", "1", false, emptyList(), "login"))
        assertEquals(false to "No se pudo añadir a tus contactos",
            Contactos.resultado("add", "1", false, emptyList(), "no-form"))
        assertEquals(false to "No se pudo quitar de tus contactos",
            Contactos.resultado("remove", "1", false, emptyList(), "TypeError: Failed to fetch"))
    }

    @Test
    fun `destinatarios de un MP separados por punto y coma, sin repetir`() {
        assertEquals("Fulanito", Contactos.anadirDestinatario("", "Fulanito"))
        assertEquals("Zutano; Fulanito", Contactos.anadirDestinatario("Zutano", "Fulanito"))
        assertEquals("Zutano; Fulanito", Contactos.anadirDestinatario("Zutano;", " Fulanito "))
        assertEquals("fulanito", Contactos.anadirDestinatario("fulanito", "Fulanito"))
        assertEquals("Zutano", Contactos.anadirDestinatario("Zutano", ""))
    }
}
