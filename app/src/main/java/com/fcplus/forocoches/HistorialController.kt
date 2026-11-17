package com.fcplus.forocoches

import android.content.Context
import android.content.SharedPreferences

/**
 * Dónde vive el historial de hilos visitados ([HistorialHilos]): en `shell_prefs`, **por
 * cuenta** (clave con el sufijo del uid, ver [ClavesPorCuenta]) y solo en el móvil.
 *
 * El uid se pide EN CADA ACCESO, igual que en [ReadThreadsRepository]: cambia al cambiar de
 * cuenta y resolverlo una vez al construir es la clase de bug "se quedó apuntando a la cuenta
 * anterior". Sin caché en memoria a propósito: son 30 filas y se leen al abrir la pantalla.
 */
class AlmacenHistorial(
    private val prefs: SharedPreferences,
    private val uid: () -> String
) {
    private fun clave() = ClavesPorCuenta.clave(CLAVE, uid())

    fun leer(): List<HistorialHilos.Visita> = HistorialHilos.leer(prefs.getString(clave(), null))

    fun apuntar(v: HistorialHilos.Visita) {
        val antes = leer()
        val despues = HistorialHilos.apuntar(antes, v)
        if (despues == antes) return
        prefs.edit().putString(clave(), HistorialHilos.guardar(despues)).apply()
    }

    fun quitar(tid: String) {
        prefs.edit().putString(clave(), HistorialHilos.guardar(HistorialHilos.quitar(leer(), tid))).apply()
    }

    fun borrar() {
        prefs.edit().remove(clave()).apply()
    }

    companion object {
        const val CLAVE = "historial_hilos"
    }
}

/**
 * La pantalla "Historial": la MISMA lista de hilos que Inicio (mismas filas, compacta o
 * Tarjetas), alimentada desde el móvil en vez de desde el foro. MainActivity solo dice cuándo
 * se enseña y cuándo se apunta una visita; aquí está qué se pinta y el borrado.
 *
 * Cada fila: el título, el subforo (si se sabe) en la miga, quien abrió el hilo y "visto hace X"
 * donde va la hora. No lleva número de respuestas ni enlace al último mensaje: serían los de
 * cuando lo viste, y un dato viejo con pinta de actual engaña más que un hueco.
 */
class HistorialController(
    private val almacen: AlmacenHistorial,
    private val ahora: () -> Long = { System.currentTimeMillis() }
) {

    /** Apunta un hilo que acaba de cargar bien. */
    fun apuntar(tid: String, titulo: String, fid: Int, foro: String, autor: String) {
        almacen.apuntar(HistorialHilos.Visita(tid, titulo, fid, foro, autor, ahora()))
    }

    /** Las filas de la lista, la más reciente arriba. */
    fun filas(): List<ThreadItem> {
        val t = ahora()
        return almacen.leer().map { v ->
            ThreadItem(
                tid = v.tid,
                title = v.titulo,
                author = v.autor,
                replies = "",
                time = HistorialHilos.haceCuanto(v.cuando, t),
                url = HistorialHilos.url(v.tid),
                foroFid = v.fid
            )
        }
    }

    fun estaVacio(): Boolean = almacen.leer().isEmpty()

    /** Pide confirmación y, si se acepta, borra el historial de esta cuenta y avisa. */
    fun confirmarBorrar(ctx: Context, alBorrar: () -> Unit) {
        if (estaVacio()) { alBorrar(); return }
        androidx.appcompat.app.AlertDialog.Builder(ctx)
            .setTitle("¿Borrar el historial?")
            .setMessage("Se olvidan los hilos que has abierto con esta cuenta. Solo se borra de este móvil.")
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Borrar") { _, _ -> almacen.borrar(); alBorrar() }
            .show()
    }

    companion object {
        const val TEXTO_VACIO =
            "Aquí aparecerán los últimos ${HistorialHilos.TOPE} hilos que abras, el más reciente " +
                "arriba.\n\nSirve para volver a uno que se te ha perdido al actualizarse la lista. " +
                "Se guarda solo en este móvil."
    }
}
