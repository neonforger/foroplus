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
                    "oírla y guardarla. Se publican sus ${Musica.cuantas(enlaces.size)}. Puedes retirarla cuando quieras."
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
            .show()
        hilo.execute {
            val r = try {
                PublicarPlaylist.publicar(
                    enganches.api(), p.nombre, enlaces,
                    alCrear = { id -> almacen.marcarPublicada(p.id, id) },
                    alTanda = { hechas, total ->
                        act.runOnUiThread { dlg.setMessage("Enviando canciones: tanda ${minOf(hechas + 1, total)} de $total") }
                    }
                )
            } catch (_: Exception) {
                PublicarPlaylist.Resultado.Fallo(ApiMusica.R.Error("red", 0, null), dudosa = true)
            }
            val error = (r as? PublicarPlaylist.Resultado.Fallo)?.error ?: (r as? PublicarPlaylist.Resultado.Parcial)?.error
            if (error != null && error.tokenInservible) enganches.tokens.borrar(uid)
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
        val id = p.publicadaId ?: return
        if (ocupado) return
        AlertDialog.Builder(act)
            .setTitle("¿Retirar «${p.nombre}»?")
            .setMessage(
                "Deja de verse en «Playlists de los shurs». Quien ya la guardó conserva su copia. " +
                    "Tu playlist de este móvil no se toca."
            )
            .setPositiveButton("Retirar") { _, _ -> retirando(p, id) }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun retirando(p: Playlist, id: String) {
        ocupado = true
        val uid = enganches.uidActivo()
        hilo.execute {
            val r = try { enganches.api().retirar(id) } catch (_: Exception) { ApiMusica.R.Error("red", 0, null) }
            // 404 = ya no está en el servidor (se retiró antes, o se borró la cuenta): la marca sobra.
            val hecho = r is ApiMusica.R.Ok || (r is ApiMusica.R.Error && r.status == 404)
            if (hecho) almacen.marcarPublicada(p.id, null)
            if (r is ApiMusica.R.Error && r.tokenInservible) enganches.tokens.borrar(uid)
            act.runOnUiThread {
                ocupado = false
                if (hecho) aviso("«${p.nombre}» retirada de «Playlists de los shurs»", largo = true)
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
            val hecho = try {
                BorrarCuentaMusica.borrar(enganches.api(), uid) { enganches.tokens.borrar(it) }
            } catch (_: Exception) { false }
            if (hecho) try { BorrarCuentaMusica.quitarMarcas(almacen) } catch (_: Exception) { }
            act.runOnUiThread {
                ocupado = false
                aviso(
                    if (hecho) "Tu cuenta de Música se ha borrado" else "No se ha podido borrar la cuenta ahora. Prueba en un rato",
                    largo = true
                )
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
