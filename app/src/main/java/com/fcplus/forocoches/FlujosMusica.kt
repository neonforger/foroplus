package com.fcplus.forocoches

import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import java.util.concurrent.Executors

/**
 * Las tres acciones de Música que hablan con el servidor en nombre de la cuenta y piden
 * confirmación: publicar una playlist tuya, retirarla y borrar la cuenta. Las decisiones (tandas,
 * reintentos, qué es "hecho") están en [PublicarPlaylist] y [BorrarCuentaMusica]; esto pregunta,
 * enseña el avance y cuenta cómo ha ido.
 *
 * @param alCambiar tras cualquier cambio (marcas de "publicada", cuenta borrada): la portada se
 *   repinta. Siempre en el hilo de la interfaz.
 */
class FlujosMusica(
    private val act: AppCompatActivity,
    private val enganches: EnganchesMusica,
    private val almacen: AlmacenMusica,
    private val abrirVerificacion: () -> Unit,
    private val alCambiar: () -> Unit
) {
    private var ocupado = false

    // ── Publicar ─────────────────────────────────────────────────────────────

    /** Pregunta y publica [p] (sus canciones están en [canciones], en orden). */
    fun publicar(p: Playlist, canciones: List<Cancion>) {
        if (ocupado) return
        val enlaces = canciones.map { it.ref.url() }
        if (enlaces.isEmpty()) { aviso("Esa playlist no tiene canciones que publicar"); return }
        val uid = enganches.uidActivo()
        ocupado = true
        hilo.execute {
            val tiene = uid.isNotEmpty() && try { enganches.tokens.de(uid) != null } catch (_: Exception) { false }
            act.runOnUiThread {
                ocupado = false
                if (!tiene) {
                    aviso("Para publicar tienes que verificar tu cuenta de ForoCoches", largo = true)
                    abrirVerificacion()
                } else confirmarPublicar(p, enlaces, uid)
            }
        }
    }

    private fun confirmarPublicar(p: Playlist, enlaces: List<String>, uid: String) {
        AlertDialog.Builder(act)
            .setTitle("¿Publicar «${p.nombre}»?")
            .setMessage(
                "Cualquiera que use ForoPlus podrá verla en «Playlists de los shurs», con tu nombre de ForoCoches, " +
                    "oírla y guardarla. Se publican sus ${Musica.cuantas(enlaces.size)}, una a una, así que si es larga tarda un rato. " +
                    "Puedes retirarla cuando quieras."
            )
            .setPositiveButton("Publicar") { _, _ -> publicando(p, enlaces, uid) }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun publicando(p: Playlist, enlaces: List<String>, uid: String) {
        ocupado = true
        val dlg = AlertDialog.Builder(act)
            .setTitle("Publicando «${p.nombre}»")
            .setMessage("Preparando…")
            .setCancelable(false)
            .setNegativeButton("Cancelar", null)
            .show()
        // Sin cerrar el diálogo al pulsar: se para entre tandas y el resultado lo cuenta después.
        val cancelar = java.util.concurrent.atomic.AtomicBoolean(false)
        dlg.getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener {
            cancelar.set(true)
            it.isEnabled = false
            dlg.setMessage("Cancelando al acabar la canción en curso…")
        }
        hilo.execute {
            val r = try {
                PublicarPlaylist.publicar(
                    enganches.api(), p.nombre, enlaces,
                    alCrear = { id -> almacen.marcarPublicada(p.id, id, uid) },
                    alTanda = { hechas, total ->
                        act.runOnUiThread { dlg.setMessage("Enviando canciones: tanda ${minOf(hechas + 1, total)} de $total") }
                    },
                    cancelado = { cancelar.get() }
                )
            } catch (_: Exception) {
                PublicarPlaylist.Resultado.Fallo(ApiMusica.R.Error("red", 0, null), dudosa = true)
            }
            val error = (r as? PublicarPlaylist.Resultado.Fallo)?.error ?: (r as? PublicarPlaylist.Resultado.Parcial)?.error
            if (error != null) enganches.tokens.trasError(uid, error)
            act.runOnUiThread {
                ocupado = false
                dlg.dismiss()
                contar(p, r)
                alCambiar()
            }
        }
    }

    private fun contar(p: Playlist, r: PublicarPlaylist.Resultado) {
        when (r) {
            is PublicarPlaylist.Resultado.Hecho -> {
                val sin = if (r.fallidas.isEmpty()) "" else
                    "\n\n${r.fallidas.size} no se han podido añadir (el servidor no ha podido leerlas)."
                dialogo("Publicada", "«${p.nombre}» ya está en «Playlists de los shurs» con ${Musica.cuantas(r.canciones)}.$sin")
            }
            is PublicarPlaylist.Resultado.Parcial -> dialogo(
                "Publicada a medias",
                "Solo se han publicado ${Musica.cuantas(r.canciones)} de «${p.nombre}»: ${TextosShurs.error(r.error)}.\n\n" +
                    "Puedes retirarla desde su menú ⋮ y volver a publicarla."
            )
            is PublicarPlaylist.Resultado.Fallo ->
                if (r.dudosa) dialogo(
                    "No sabemos si se ha publicado",
                    "No ha llegado la respuesta de Música. Mira en «Playlists de los shurs» si «${p.nombre}» ya está " +
                        "antes de volver a intentarlo, o saldría dos veces."
                ) else aviso(TextosShurs.error(r.error), largo = true)
        }
    }

    // ── Retirar ──────────────────────────────────────────────────────────────

    fun retirar(p: Playlist) {
        val uid = enganches.uidActivo()
        // Solo la marca de la cuenta activa: la de otra cuenta de este móvil no se retira desde aquí.
        if (!p.deLaCuenta(uid)) return
        val id = p.publicadaId ?: return
        if (ocupado) return
        AlertDialog.Builder(act)
            .setTitle("¿Retirar «${p.nombre}»?")
            .setMessage(
                "Deja de verse en «Playlists de los shurs». Quien ya la guardó conserva su copia. " +
                    "Tu playlist de este móvil no se toca."
            )
            .setPositiveButton("Retirar") { _, _ -> retirando(p, id, uid) }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun retirando(p: Playlist, id: String, uid: String) {
        ocupado = true
        hilo.execute {
            val r = try { enganches.api().retirar(id) } catch (_: Exception) { ApiMusica.R.Error("red", 0, null) }
            // 404 = ya no está con ESTA cuenta; solo sobra la marca si la publicó ella (ver RetirarPlaylist).
            val veredicto = RetirarPlaylist.veredicto(r, p, uid)
            val hecho = veredicto == RetirarPlaylist.Veredicto.RETIRADA
            if (hecho) almacen.marcarPublicada(p.id, null)
            if (r is ApiMusica.R.Error) enganches.tokens.trasError(uid, r)
            act.runOnUiThread {
                ocupado = false
                if (hecho) aviso("«${p.nombre}» retirada de «Playlists de los shurs»", largo = true)
                else if (veredicto == RetirarPlaylist.Veredicto.NO_ES_DE_ESTA_CUENTA) AlertDialog.Builder(act)
                    .setTitle("No está con esta cuenta")
                    .setMessage(
                        "Con la cuenta que tienes ahora no se encuentra «${p.nombre}» en «Playlists de los shurs». " +
                            "Si sigue publicada, hay que retirarla desde la cuenta que la publicó. " +
                            "Quitar la marca solo la borra de este móvil (no toca el servidor): la playlist deja de figurar como publicada aquí."
                    )
                    .setPositiveButton("Quitar la marca") { _, _ -> if (almacen.marcarPublicada(p.id, null)) alCambiar() }
                    .setNegativeButton("Cancelar", null)
                    .show()
                else aviso(TextosShurs.error(r as ApiMusica.R.Error), largo = true)
                alCambiar()
            }
        }
    }

    // ── Borrar la cuenta ─────────────────────────────────────────────────────

    fun borrarCuenta() {
        if (ocupado) return
        AlertDialog.Builder(act)
            .setTitle("Borrar mi cuenta de Música")
            .setMessage(
                "Se borran del servidor tus playlists publicadas, lo que hayas guardado, tus votos y tus propuestas " +
                    "de esta semana. Tus playlists de este móvil no se tocan. Podrás verificarte otra vez cuando quieras."
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
                            "Tu verificación ha caducado, así que no se ha podido borrar nada: tu cuenta de Música y tus playlists " +
                                "públicas siguen en el servidor. Verifícate de nuevo y, después, vuelve a pedir el borrado."
                        )
                        .setPositiveButton("Verificar") { _, _ -> abrirVerificacion() }
                        .setNegativeButton("Ahora no", null)
                        .show()
                }
                alCambiar()
            }
        }
    }

    private fun dialogo(titulo: String, texto: String) {
        AlertDialog.Builder(act).setTitle(titulo).setMessage(texto).setPositiveButton("Vale", null).show()
    }

    private fun aviso(texto: String, largo: Boolean = false) {
        Toast.makeText(act, texto, if (largo) Toast.LENGTH_LONG else Toast.LENGTH_SHORT).show()
    }

    private companion object {
        /** Un solo hilo: publicar, retirar y borrar no se pisan. */
        val hilo = Executors.newSingleThreadExecutor()
    }
}
