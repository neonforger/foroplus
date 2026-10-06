package com.fcplus.forocoches

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * El repo es PÚBLICO: un fixture capturado con sesión lleva el securitytoken, el logouthash o
 * las cookies de quien lo capturó. Esto falla antes de que llegue a un commit.
 */
object FixturesSinSecretos {
    // Se busca la FORMA de un valor real, no una lista de marcadores permitidos: los fixtures
    // se han anonimizado con ceros, "fixture-security-token", "guest"… y vendrán otros.
    // securitytoken/logouthash de vBulletin = 10 dígitos + "-" + 40 hex; las cookies
    // bbsessionhash/bbpassword = 32 hex. Todo ceros es la forma anonimizada.
    private val campos = Regex(
        """(securitytoken|logouthash|bbsessionhash|bbpassword)["']?\s*(?:=|:|value=)\s*["']?(\d{10}-[0-9a-f]{40}|[0-9a-f]{32,})""",
        RegexOption.IGNORE_CASE,
    )
    private val tokenGithub = Regex("""gh[pousr]_[A-Za-z0-9]{20,}""")

    private fun anonimizado(v: String) = v.all { it == '0' || it == '-' }

    fun buscar(texto: String): List<String> =
        campos.findAll(texto).filterNot { anonimizado(it.groupValues[2]) }.map { it.groupValues[1] }.toList() +
            tokenGithub.findAll(texto).map { "token de GitHub" }.toList()
}

class FixturesSinSecretosTest {

    @Test
    fun `un token a ceros no es un secreto`() {
        assertEquals(emptyList<String>(), FixturesSinSecretos.buscar(
            """var SECURITYTOKEN = "0000000000-0000000000000000000000000000000000000000";"""))
    }

    @Test
    fun `un securitytoken real si lo es`() {
        assertEquals(listOf("SECURITYTOKEN"), FixturesSinSecretos.buscar(
            """var SECURITYTOKEN = "1234567890-0123456789abcdef0123456789abcdef01234567";"""))  // gitleaks:allow (inventado)
    }

    @Test
    fun `el del formulario y el del logout tambien`() {
        val token = "1234567890-0123456789abcdef0123456789abcdef01234567"  // gitleaks:allow (inventado)
        val html = """<input type="hidden" name="securitytoken" value="$token" />
            <a href="login.php?do=logout&logouthash=$token">"""
        assertEquals(listOf("securitytoken", "logouthash"), FixturesSinSecretos.buscar(html))
    }

    @Test
    fun `los marcadores con que se anonimizan los fixtures no son secretos`() {
        val html = """SECURITYTOKEN = "fixture-security-token"; securitytoken: SECURITYTOKEN,
            <input name="securitytoken" value="guest" />"""
        assertEquals(emptyList<String>(), FixturesSinSecretos.buscar(html))
    }

    @Test
    fun `una cookie de sesion o un token de GitHub tambien`() {
        assertEquals(listOf("bbsessionhash"), FixturesSinSecretos.buscar("bbsessionhash=9f8e7d6c5b4a3f2e1d0c9b8a7f6e5d4c"))
        assertEquals(listOf("token de GitHub"), FixturesSinSecretos.buscar("ghp_" + "a1B2".repeat(9)))
    }

    @Test
    fun `ningun fixture del repo lleva secretos`() {
        // El test corre con app/ como directorio de trabajo.
        val dirs = listOf(File("src/test/resources"), File("../tools/oracle/fixtures"))
        val ficheros = dirs.filter { it.isDirectory }.flatMap { d -> d.walk().filter { it.isFile }.toList() }
        assertTrue("no se encontró ningún fixture: ¿ha cambiado el directorio de trabajo?", ficheros.isNotEmpty())
        val sucios = ficheros.associate { it.path to FixturesSinSecretos.buscar(it.readText()) }
            .filterValues { it.isNotEmpty() }
        assertEquals("fixtures con secretos (anonimízalos a ceros)", emptyMap<String, List<String>>(), sucios)
    }
}
