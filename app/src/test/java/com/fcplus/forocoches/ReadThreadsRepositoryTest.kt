package com.fcplus.forocoches

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * `ReadThreadsRepository` resuelve su fichero de SharedPreferences EN CADA ACCESO leyendo
 * `uid_activo` de `shell_prefs`, y mantiene una caché en memoria (`cache` + `cacheUid`) que se
 * invalida sola cuando ese uid cambia. Estos tests ejercitan justo esa invalidación: que la
 * caché de una cuenta no se cuele en otra, y sobre todo que un `markRead` tras cambiar de
 * cuenta no acabe ESCRIBIENDO el mapa heredado en el fichero de la cuenta nueva (el bug que
 * mezclaría para siempre los leídos de dos cuentas).
 *
 * No se puede reproducir esto con `adb run-as` cambiando `uid_activo` desde fuera del proceso:
 * Android cachea la instancia de SharedPreferences por nombre durante toda la vida del proceso
 * y no se entera del cambio. Con Robolectric el test corre EN el mismo proceso, así que escribir
 * `uid_activo` desde aquí reproduce exactamente lo que pasa en el móvil al cambiar de cuenta.
 */
@RunWith(RobolectricTestRunner::class)
class ReadThreadsRepositoryTest {

    // Espejo del KEY privado de ReadThreadsRepository: no es accesible desde el test, pero hace
    // falta el mismo nombre de clave para poder leer el fichero en disco tal cual lo deja la
    // clase, sin pasar por el propio repositorio (si no, no se estaría comprobando nada nuevo).
    private val claveLeidos = "read_threads"

    private lateinit var ctx: Context
    private lateinit var repo: ReadThreadsRepository
    private lateinit var shellPrefs: android.content.SharedPreferences

    @Before
    fun setUp() {
        ctx = ApplicationProvider.getApplicationContext()
        repo = ReadThreadsRepository(ctx)
        shellPrefs = ctx.getSharedPreferences("shell_prefs", Context.MODE_PRIVATE)
        // Estado limpio: sin esto, una fuga entre tests haría creer que la invalidación
        // funciona cuando en realidad lo que se ve es el fichero vacío de otro test.
        shellPrefs.edit().clear().apply()
        for (nombre in listOf("fc_leidos", "fc_leidos_111", "fc_leidos_222")) {
            ctx.getSharedPreferences(nombre, Context.MODE_PRIVATE).edit().clear().apply()
        }
    }

    private fun cambiarCuenta(uid: String?) {
        val editor = shellPrefs.edit()
        if (uid == null) editor.remove("uid_activo") else editor.putString("uid_activo", uid)
        editor.apply()
    }

    private fun leidosEnDisco(nombreFichero: String): String? =
        ctx.getSharedPreferences(nombreFichero, Context.MODE_PRIVATE).getString(claveLeidos, null)

    @Test
    fun `cambiar de cuenta invalida la cache, lo marcado con 111 no aparece bajo 222`() {
        cambiarCuenta("111")
        repo.markRead("100", "42")
        assertEquals(42, repo.readReplies("100"))

        // El cambio de cuenta lo escribe la propia app en shell_prefs (MainActivity); aquí lo
        // simulamos desde dentro del mismo proceso, que es lo que Robolectric permite y
        // `adb run-as` no.
        cambiarCuenta("222")

        assertNull(
            "la caché de la cuenta 111 no debería sobrevivir al cambio de uid_activo",
            repo.readReplies("100")
        )
    }

    @Test
    fun `un markRead tras cambiar de cuenta no contamina el fichero de la cuenta anterior`() {
        cambiarCuenta("111")
        repo.markRead("100", "42")

        cambiarCuenta("222")
        repo.markRead("200", "7")

        // Lo crítico: el mapa heredado de 111 (con "100:42") no debe haberse persistido en el
        // fichero de 222, y el fichero de 111 debe seguir exactamente como se dejó.
        assertEquals("100:42", leidosEnDisco("fc_leidos_111"))
        assertEquals("200:7", leidosEnDisco("fc_leidos_222"))
    }

    @Test
    fun `sin uid_activo se usa el fichero de siempre, sin sufijo`() {
        // Ausente del todo (nunca se ha iniciado sesión).
        cambiarCuenta(null)
        repo.markRead("300", "5")
        assertEquals(5, repo.readReplies("300"))
        assertEquals("300:5", leidosEnDisco("fc_leidos"))

        // Presente pero vacío (mismo caso que ausente, según ClavesPorCuenta.fichero).
        cambiarCuenta("")
        assertEquals(5, repo.readReplies("300"))
    }

    @Test
    fun `volver a la cuenta anterior recupera sus marcas intactas`() {
        cambiarCuenta("111")
        repo.markRead("100", "42")

        cambiarCuenta("222")
        repo.markRead("200", "7")
        assertNull(repo.readReplies("100")) // aquí no existe lo de 111

        cambiarCuenta("111")
        assertEquals(
            "las marcas de 111 deben seguir ahí, sin mezcla con lo escrito para 222",
            42,
            repo.readReplies("100")
        )
    }
}
