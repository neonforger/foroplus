package com.fcplus.forocoches

import org.junit.Assert.assertEquals
import org.junit.Test

class IgnoreListParserTest {

    @Test
    fun `devuelve lista vacía si no hay usuarios ignorados`() {
        val html = "<html><body><ul></ul></body></html>"
        assertEquals(emptyList<String>(), IgnoreListParser.parse(html))
    }

    @Test
    fun `extrae usernames de la lista de ignorados`() {
        val html = """
            <html><body>
              <ul>
                <li id="user123">
                  <input type="checkbox" name="listbits[ignore][123]" value="123" checked>
                  <a href="member.php?u=123">UsuarioUno</a>
                </li>
                <li id="user456">
                  <input type="checkbox" name="listbits[ignore][456]" value="456" checked>
                  <a href="member.php?u=456">UsuarioDos</a>
                </li>
              </ul>
            </body></html>
        """.trimIndent()
        assertEquals(listOf("UsuarioUno", "UsuarioDos"), IgnoreListParser.parse(html))
    }

    @Test
    fun `devuelve lista vacía si la página es de login`() {
        val html = """<html><body><form action="login.php"><input name="username"></form></body></html>"""
        assertEquals(emptyList<String>(), IgnoreListParser.parse(html))
    }

    @Test
    fun `hace trim a los usernames`() {
        val html = """
            <html><body>
              <ul>
                <li id="user789">
                  <input type="checkbox" name="listbits[ignore][789]" value="789" checked>
                  <a href="member.php?u=789">
                      UsuarioTres
                  </a>
                </li>
              </ul>
            </body></html>
        """.trimIndent()
        assertEquals(listOf("UsuarioTres"), IgnoreListParser.parse(html))
    }
}
