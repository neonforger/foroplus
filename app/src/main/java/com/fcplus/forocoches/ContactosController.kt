package com.fcplus.forocoches

import android.view.View
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

/**
 * Tus contactos de ForoCoches desde la app: añadir/quitar a alguien (ficha nativa y menú ⋮ de un
 * mensaje) y elegir destinatarios de un MP nuevo entre ellos. Lo pidió un tester (DjAlexey,
 * 2026-11-14): "no puedes añadir usuarios a la lista de contactos desde el perfil o el menú".
 *
 * La red va por el motor (`fcLoadContactos`, `fcContactoAdd`, `fcContactoRemove` en extractor.js,
 * con el formulario REAL de confirmación de FC); lo que se decide, en [Contactos] (puro, con
 * tests). MainActivity solo crea esto, le pasa la respuesta del puente ([alLlegar]) y le pide que
 * pinte el botón de la ficha.
 *
 * La lista se recuerda en memoria y **atada a la cuenta** con la que se pidió: tras un cambio de
 * cuenta se da por desconocida y se vuelve a pedir, no se le enseñan a una cuenta los contactos
 * de otra.
 *
 * @param cambiandoDeCuenta añadir/quitar ESCRIBE en la cuenta con las cookies de ahora mismo: a
 *   medio cambiar, tocaría la lista de la cuenta equivocada. Es una vía que muta algo en FC, así
 *   que lleva el guard, y va AQUÍ, donde se publica, no en cada botón (MEMORY, "las vías que
 *   mutan algo en FC").
 * @param alCambiar la lista ha cambiado (llegó o se tocó): repintar lo que dependa de ella.
 */
class ContactosController(
    private val activity: AppCompatActivity,
    private val ejecutarJs: (String) -> Unit,
    private val logueado: () -> Boolean,
    private val cuentaActiva: () -> String,
    private val cambiandoDeCuenta: () -> Boolean,
    private val toast: (String) -> Unit,
    private val alCambiar: () -> Unit
) {
    private var lista: List<Contacto>? = null
    private var listaDe: String = ""
    private var pidiendo = false
    private var pidiendoPara: String = ""
    /** Cuenta cuya lista no se pudo leer: no se reintenta sola (repintar la pediría en bucle). */
    private var fallidaDe: String = ""
    /** Una acción en vuelo: un segundo toque no lanza otra encima. */
    private var accionEnCurso = false
    /** El campo "Para" que espera la lista para abrir el selector. */
    private var selectorPendiente: EditText? = null

    /** La lista de la cuenta activa, o null si no se sabe todavía. */
    fun conocida(): List<Contacto>? = lista.takeIf { listaDe == cuentaActiva() && listaDe.isNotEmpty() }

    /** null = no se sabe (aún no se ha leído la lista de esta cuenta). */
    fun esContacto(uid: String): Boolean? = conocida()?.let { Contactos.esContacto(uid, it) }

    fun pedirLista() {
        if (!logueado()) return
        val cuenta = cuentaActiva()
        if (pidiendo && pidiendoPara == cuenta) return
        pidiendo = true
        pidiendoPara = cuenta
        ejecutarJs("window.fcLoadContactos&&fcLoadContactos()")
    }

    /** Pide la lista solo si no se sabe todavía (para la ficha y el menú). */
    fun asegurarLista() { if (conocida() == null && fallidaDe != cuentaActiva()) pedirLista() }

    fun anadir(uid: String, nombre: String) = escribir("add", uid, nombre)
    fun quitar(uid: String, nombre: String) = escribir("remove", uid, nombre)

    /** Hace lo contrario de lo que ya pasa. Sin lista, añade (si ya lo era, FC lo deja igual). */
    fun alternar(uid: String, nombre: String) =
        if (esContacto(uid) == true) quitar(uid, nombre) else anadir(uid, nombre)

    private fun escribir(accion: String, uid: String, nombre: String) {
        if (!logueado()) { toast("Inicia sesión para gestionar tus contactos"); return }
        if (cambiandoDeCuenta()) { toast("Espera a que termine el cambio de cuenta"); return }
        if (!Contactos.uidValido(uid)) { toast("No se puede saber quién es este usuario"); return }
        if (accionEnCurso) return
        accionEnCurso = true
        val quien = if (nombre.isNotEmpty()) "@$nombre" else "el usuario"
        toast(if (accion == "add") "Añadiendo a $quien…" else "Quitando a $quien…")
        val fn = if (accion == "add") "fcContactoAdd" else "fcContactoRemove"
        pidiendoPara = cuentaActiva()
        // uid ya validado como solo dígitos: no hace falta escaparlo.
        ejecutarJs("window.$fn&&$fn('$uid')")
    }

    /** Respuesta del motor (ya en el hilo de la interfaz). */
    fun alLlegar(json: String) {
        val o = try { org.json.JSONObject(json) } catch (_: Exception) { return }
        val accion = o.optString("action", "")
        val cuenta = pidiendoPara
        if (accion.isEmpty()) pidiendo = false else accionEnCurso = false

        val okPeticion = o.optBoolean("ok", false)
        val sesion = o.optBoolean("session", false)
        val contactos = if (okPeticion && sesion) {
            try { ContactosParser.parse(o.optString("html", "")) } catch (_: Exception) { null }
        } else null
        // Solo se guarda si sigue siendo la cuenta con la que se pidió.
        if (contactos != null && cuenta == cuentaActiva()) { lista = contactos; listaDe = cuenta; fallidaDe = "" }
        else if (contactos == null && accion.isEmpty()) fallidaDe = cuenta

        if (accion.isNotEmpty()) {
            val (_, aviso) = Contactos.resultado(
                accion, o.optString("uid"), okPeticion && sesion, contactos ?: emptyList(),
                o.optString("error", "").ifEmpty { if (okPeticion && !sesion) "login" else "" }
            )
            toast(aviso)
        }
        alCambiar()
        selectorPendiente?.let { campo ->
            selectorPendiente = null
            if (accion.isEmpty()) {
                if (contactos == null) toast("No se pudieron cargar tus contactos")
                else elegirDestinatario(campo)
            }
        }
    }

    /**
     * El botón de la ficha: la acción contraria a lo que ya pasa, solo con sesión y con un uid
     * de verdad. Mientras no se sabe, se enseña quieto ("Contactos…") y se pide la lista.
     */
    fun pintarBoton(btn: TextView, uid: String, nombre: String) {
        val hay = logueado() && nombre.isNotEmpty() && Contactos.uidValido(uid)
        btn.visibility = if (hay) View.VISIBLE else View.GONE
        if (!hay) return
        val es = esContacto(uid)
        if (es == null && fallidaDe == cuentaActiva()) {
            // No se pudo leer la lista: se ofrece añadir (si ya lo era, FC lo deja igual).
            btn.text = Contactos.etiqueta(false)
            btn.isEnabled = true
        } else if (es == null) {
            btn.text = "Contactos…"
            btn.isEnabled = false
            asegurarLista()
        } else {
            btn.text = Contactos.etiqueta(es)
            btn.isEnabled = true
        }
    }

    /** "Contactos" junto al "Para" de un MP: lista para elegir; cada toque añade uno. */
    fun elegirDestinatario(campo: EditText) {
        if (!logueado()) { toast("Inicia sesión para ver tus contactos"); return }
        fallidaDe = ""   // pedirlo a mano sí reintenta
        val l = conocida()
        if (l == null) {
            selectorPendiente = campo
            toast("Cargando tus contactos…")
            pedirLista()
            return
        }
        if (l.isEmpty()) {
            toast("No tienes contactos en ForoCoches. Añádelos desde su perfil o el menú ⋮ de un mensaje.")
            return
        }
        val conNombre = l.filter { it.nombre.isNotEmpty() }.sortedBy { it.nombre.lowercase() }
        androidx.appcompat.app.AlertDialog.Builder(activity)
            .setTitle("Tus contactos")
            .setItems(conNombre.map { it.nombre }.toTypedArray()) { _, i ->
                val nuevo = Contactos.anadirDestinatario(campo.text.toString(), conNombre[i].nombre)
                campo.setText(nuevo)
                campo.setSelection(nuevo.length)
            }
            .setNegativeButton("Cerrar", null)
            .show()
    }
}
