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
    // Y el hash de sesión "s" (32 hex), que vBulletin mete en las URLs y en SESSIONURL cuando
    // el navegador aún no tiene la cookie.
    private const val VALOR = """\d{10}-[0-9a-f]{40}|[0-9a-f]{32,}"""
    private val nombres = setOf("securitytoken", "logouthash", "bbsessionhash", "bbpassword")

    // nombre = valor / nombre: valor, en texto, JS o JSON.
    private val campos = Regex(
        """(securitytoken|logouthash|bbsessionhash|bbpassword)["']?\s*[=:]\s*["']?($VALOR)""",
        RegexOption.IGNORE_CASE,
    )
    // <input>: name y value en cualquier orden y con lo que sea en medio.
    private val input = Regex("""<input\b[^>]*>""", RegexOption.IGNORE_CASE)
    private val atributo = Regex("""([\w-]+)\s*=\s*["']([^"']*)["']""")
    private val esValor = Regex("""^(?:$VALOR)$""", RegexOption.IGNORE_CASE)
    private val sesion = Regex(
        """(?:SESSIONURL\s*=\s*["']|[?&;])s=([0-9a-f]{32})(?![0-9a-f])""",
        RegexOption.IGNORE_CASE,
    )
    private val tokenGithub = Regex("""gh[pousr]_[A-Za-z0-9]{20,}""")

    private fun anonimizado(v: String) = v.all { it == '0' || it == '-' }

    /** Deshace %XX, las entidades de comillas/& y las comillas escapadas de JSON. */
    private fun normalizar(t: String): String =
        Regex("%([0-9A-Fa-f]{2})").replace(t) { it.groupValues[1].toInt(16).toChar().toString() }
            .replace("&quot;", "\"").replace("&#34;", "\"").replace("&#39;", "'").replace("&#039;", "'")
            .replace("&amp;", "&").replace("\\\"", "\"")

    fun buscar(texto: String): List<String> {
        val t = normalizar(texto)
        val hallazgos = mutableListOf<Pair<Int, String>>()
        campos.findAll(t).filterNot { anonimizado(it.groupValues[2]) }
            .forEach { hallazgos += it.range.first to it.groupValues[1] }
        input.findAll(t).forEach { etiqueta ->
            val attrs = atributo.findAll(etiqueta.value).associate { it.groupValues[1].lowercase() to it.groupValues[2] }
            val nombre = attrs["name"] ?: return@forEach
            val valor = attrs["value"] ?: return@forEach
            val vigilado = nombre.lowercase() in nombres || nombre == "s"
            if (vigilado && esValor.matches(valor) && !anonimizado(valor)) hallazgos += etiqueta.range.first to nombre
        }
        sesion.findAll(t).filterNot { anonimizado(it.groupValues[1]) }.forEach { hallazgos += it.range.first to "s" }
        tokenGithub.findAll(t).forEach { hallazgos += it.range.first to "token de GitHub" }
        return hallazgos.sortedBy { it.first }.map { it.second }
    }
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

    // Formas reales que la primera versión dejaba pasar (revisión del 2026-10-06).
    private val tok = "1234567890-" + "0123456789abcdef".repeat(2) + "01234567"  // inventado
    private val sesion = "0123456789abcdef".repeat(2)  // 32 hex, inventado

    @Test
    fun `el input con el value antes del name o con atributos en medio`() {
        assertEquals(listOf("securitytoken"), FixturesSinSecretos.buscar("""<input value="$tok" name="securitytoken">"""))
        assertEquals(listOf("securitytoken"),
            FixturesSinSecretos.buscar("""<input name="securitytoken" type="hidden" id="st" value="$tok" />"""))
        assertEquals(listOf("securitytoken"), FixturesSinSecretos.buscar("""<input name = "securitytoken" value = "$tok">"""))
    }

    @Test
    fun `codificado en la URL, con entidades HTML o en JSON escapado`() {
        assertEquals(listOf("securitytoken"), FixturesSinSecretos.buscar("a.php?securitytoken%3D" + tok.replace("-", "%2D")))
        assertEquals(listOf("securitytoken"), FixturesSinSecretos.buscar("{&quot;securitytoken&quot;:&quot;$tok&quot;}"))
        assertEquals(listOf("securitytoken"), FixturesSinSecretos.buscar("""{\"securitytoken\":\"$tok\"}"""))
    }

    @Test
    fun `el hash de sesion s de vBulletin cuando no hay cookie`() {
        assertEquals(listOf("s"), FixturesSinSecretos.buscar("""var SESSIONURL = "s=$sesion&";"""))
        assertEquals(listOf("s"), FixturesSinSecretos.buscar("""<input type="hidden" name="s" value="$sesion" />"""))
        assertEquals(listOf("s"), FixturesSinSecretos.buscar("""<a href="showthread.php?s=$sesion&amp;t=1">"""))
    }

    @Test
    fun `una s vacia o una palabra que acaba en s no son sesion`() {
        assertEquals(emptyList<String>(), FixturesSinSecretos.buscar(
            """var SESSIONURL = ""; <input type="hidden" name="s" value="" /> <a href="x.php?posts=$sesion">"""))
    }

    @Test
    fun `ningun fixture del repo lleva secretos`() {
        // El test corre con app/ como directorio de trabajo.
        val dirs = listOf(File("src/test/resources"), File("../tools/oracle/fixtures"))
        // Si uno se mueve, el test tiene que enterarse: si no, dejaría de vigilarlo en silencio.
        dirs.forEach { assertTrue("no existe ${it.path}: ¿se ha movido?", it.isDirectory) }
        val ficheros = dirs.flatMap { d -> d.walk().filter { it.isFile }.toList() }
        val sucios = ficheros.associate { it.path to FixturesSinSecretos.buscar(it.readText()) }
            .filterValues { it.isNotEmpty() }
        assertEquals("fixtures con secretos (anonimízalos a ceros)", emptyMap<String, List<String>>(), sucios)
    }
}
