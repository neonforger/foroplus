package com.fcplus.forocoches

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Guardián del modo oscuro: **ningún color escrito a mano** fuera de la paleta.
 *
 * Existe porque este fallo no se parece a un fallo. Un `#FFFFFF` en un layout nuevo compila,
 * pasa todos los tests y se ve perfecto en el móvil de quien lo escribió — y le deja un panel
 * blanco cegador, o texto invisible, a quien tenga la app en oscuro. No hay pantalla roja que
 * lo avise: hay que impedir que entre.
 *
 * Si este test falla, la solución NO es añadir el fichero a la lista de permitidos, es usar un
 * token de `res/values/colors.xml` (y comprobar que existe también en `values-night`).
 */
class ColoresFijosTest {

    /** Excepciones con motivo. Cada una está razonada en values/colors.xml. */
    private val permitidos = setOf(
        // Las tarjetas que se comparten son IMÁGENES que salen de la app: claras siempre.
        "card_share_post.xml", "card_share_thread.xml",
        // Transparencias y capas oscuras que valen igual en los dos modos.
        "bg_login_button.xml", "bg_poll_vote.xml", "bg_login_ghost.xml",
        // Color del icono de notificación: lo pinta el sistema, fuera de la app.
        "bg_avatar.xml",
        // La silueta de la barra de estado: Android la TIÑE él, así que tiene que ser blanca
        // siempre. Un color de la paleta aquí sería incorrecto, no una dejadez.
        "ic_stat_foroplus.xml",
        // El icono de la app: es la identidad y no cambia con el tema del móvil.
        "ic_launcher_background.xml",
        // La paleta, que es justo donde deben vivir los colores.
        "colors.xml"
    )

    private val hex = Regex("""#[0-9A-Fa-f]{6,8}\b""")

    @Test
    fun `no quedan colores escritos a mano en layouts ni drawables`() {
        val raiz = File("src/main/res")
        if (!raiz.isDirectory) return   // defensa por si cambia el directorio de trabajo
        val culpables = raiz.walkTopDown()
            .filter { it.isFile && it.extension == "xml" && it.name !in permitidos }
            .mapNotNull { f ->
                // Los colores dentro de comentarios XML no pintan nada.
                val sinComentarios = f.readText().replace(Regex("(?s)<!--.*?-->"), "")
                hex.find(sinComentarios)?.let { "${f.name}: ${it.value}" }
            }
            .toList()
        assertTrue(
            "Colores a mano (usa un token de values/colors.xml): $culpables",
            culpables.isEmpty()
        )
    }

    @Test
    fun `la paleta oscura define exactamente los mismos nombres que la clara`() {
        val nombres = { p: String ->
            File(p).takeIf { it.isFile }?.readText()
                ?.let { Regex("""<color name="([^"]+)"""").findAll(it).map { m -> m.groupValues[1] }.toSet() }
                ?: emptySet()
        }
        val claros = nombres("src/main/res/values/colors.xml")
        val oscuros = nombres("src/main/res/values-night/colors.xml")
        if (claros.isEmpty()) return
        // Un token que falte en values-night NO falla al compilar: Android usa el claro, y ese
        // elemento se queda blanco en medio de una pantalla negra.
        assertTrue("Faltan en values-night: ${claros - oscuros}", (claros - oscuros).isEmpty())
        assertTrue("Sobran en values-night: ${oscuros - claros}", (oscuros - claros).isEmpty())
    }

    /**
     * Lo mismo para Kotlin, y es MÁS importante que el de los recursos: un `0xFF1A1A1A` en
     * código se aplica en tiempo de ejecución y **se salta el values-night** entero, así que
     * no hay forma de que el sistema lo corrija. Este test se escribió después de que la lista
     * de hilos pintara los NO LEÍDOS casi negros sobre fondo negro — el único fichero que se
     * quedó sin migrar, y no lo vio ni el compilador ni ningún otro test.
     */
    @Test
    fun `no quedan colores escritos a mano en Kotlin`() {
        val raiz = File("src/main/java")
        if (!raiz.isDirectory) return
        val exentos = setOf(
            // Paleta de las iniciales de avatar: identidad de cada persona. Solo queda en
            // las tarjetas de compartir; el listado y los mensajes usan ya el avatar de
            // relleno del propio foro (ic_avatar_fc) y se quedaron sin colores a mano.
            "TarjetaCompartir.kt",
            // Capas oscuras del visor y de la vista previa: oscuras en los dos modos.
            "MainActivity.kt",
            // Color del icono de notificación (lo pinta el sistema) y el del BBCode [color=]
            // que escribe el propio usuario en su mensaje.
            "NotificationHelper.kt", "BbcodeEditor.kt"
        )
        val patron = Regex("""0x[0-9A-Fa-f]{8}\.toInt\(\)|Color\.parseColor\("#""")
        val culpables = raiz.walkTopDown()
            .filter { it.isFile && it.extension == "kt" && it.name !in exentos }
            .mapNotNull { f -> patron.find(f.readText())?.let { "${f.name}: ${it.value}" } }
            .toList()
        assertTrue(
            "Colores a mano en Kotlin (usa ContextCompat.getColor con un token): $culpables",
            culpables.isEmpty()
        )
    }
}
