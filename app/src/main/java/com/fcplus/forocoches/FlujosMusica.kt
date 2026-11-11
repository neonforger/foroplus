package com.fcplus.forocoches

import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import java.util.concurrent.Executors

/**
 * La acción de Música que habla con el servidor en nombre de la cuenta y pide confirmación: borrar
 * la cuenta. La decisión (qué es "hecho") está en [BorrarCuentaMusica]; esto pregunta y cuenta
 * cómo ha ido.
 *
 * @param alCambiar tras borrar la cuenta: la portada se repinta. Siempre en el hilo de la interfaz.
 */
class FlujosMusica(
    private val act: AppCompatActivity,
    private val enganches: EnganchesMusica,
    private val almacen: AlmacenMusica,
    private val abrirVerificacion: () -> Unit,
    private val alCambiar: () -> Unit
) {
    private var ocupado = false

    // ── Borrar la cuenta ─────────────────────────────────────────────────────

    fun borrarCuenta() {
        if (ocupado) return
        AlertDialog.Builder(act)
            .setTitle("Borrar mi cuenta de Música")
            .setMessage(
                "Se borran del servidor tu cuenta de Música, tus publicaciones y tus corazones. " +
                    "Tus playlists de este móvil no se tocan. Podrás verificarte otra vez cuando quieras."
            )
            .setPositiveButton("Borrar") { _, _ -> borrando() }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun borrando() {
        ocupado = true
        val uid = enganches.uidActivo()
        hilo.execute {
            val res = try {
                BorrarCuentaMusica.borrar(enganches.api(), uid) { enganches.tokens.borrar(it) }
            } catch (_: Exception) { BorrarCuentaMusica.Resultado.FALLO }
            if (res == BorrarCuentaMusica.Resultado.HECHO) try { BorrarCuentaMusica.quitarMarcas(almacen, uid) } catch (_: Exception) { }
            act.runOnUiThread {
                ocupado = false
                when (res) {
                    BorrarCuentaMusica.Resultado.HECHO -> aviso("Tu cuenta de Música se ha borrado", largo = true)
                    BorrarCuentaMusica.Resultado.FALLO -> aviso("No se ha podido borrar la cuenta ahora. Prueba en un rato", largo = true)
                    // Un 401 también es un token caducado: la cuenta puede seguir en el servidor, así que NO se dice
                    // que se ha borrado ni se quitan las marcas de publicada. Hay que verificarse antes.
                    BorrarCuentaMusica.Resultado.VERIFICAR -> AlertDialog.Builder(act)
                        .setTitle("Verifícate otra vez para borrarla")
                        .setMessage(
                            "Tu verificación ha caducado, así que no se ha podido borrar nada: tu cuenta de Música y tus " +
                                "publicaciones siguen en el servidor. Verifícate de nuevo y, después, vuelve a pedir el borrado."
                        )
                        .setPositiveButton("Verificar") { _, _ -> abrirVerificacion() }
                        .setNegativeButton("Ahora no", null)
                        .show()
                }
                alCambiar()
            }
        }
    }

    private fun aviso(texto: String, largo: Boolean = false) {
        Toast.makeText(act, texto, if (largo) Toast.LENGTH_LONG else Toast.LENGTH_SHORT).show()
    }

    private companion object {
        /** Un solo hilo: dos borrados no se pisan. */
        val hilo = Executors.newSingleThreadExecutor()
    }
}
