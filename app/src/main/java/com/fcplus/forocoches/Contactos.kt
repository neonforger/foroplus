package com.fcplus.forocoches

import org.jsoup.Jsoup

/** Un contacto de la cuenta de ForoCoches (la "buddy list" de vBulletin). */
data class Contacto(val uid: String, val nombre: String)

/**
 * Lo puro de los contactos: leer la lista de `profile.php?do=buddylist` y decidir qué se le dice
 * al usuario tras añadir/quitar. Lo que toca la red vive en `extractor.js`
 * (`fcLoadContactos`, `fcContactoAdd`, `fcContactoRemove`); lo que pinta, en [ContactosController].
 *
 * Medido por CDP el 2026-11-17 (solo GET): la página de contactos es el mismo molde que la de
 * ignorados — `#buddylist_change_form` con las filas dentro de `#buddylist`. La cuenta con la que
 * se midió no tenía ningún contacto, así que la fila se da por igual que la de ignorados (mismo
 * `userlist_bit` de vBulletin: `<div id="userNNN">` con `listbits[buddy][NNN]` y un enlace a
 * `member.php?u=NNN`). Por eso se leen las DOS señales, el id y el nombre del checkbox: con que
 * una de las dos siga ahí, la lista sale.
 */
object ContactosParser {

    private val ID_FILA = Regex("^user(\\d+)$")
    private val CHECK = Regex("^listbits\\[buddy]\\[(\\d+)]$")
    private val UID_ENLACE = Regex("[?&]u=(\\d+)")

    fun parse(html: String): List<Contacto> {
        val doc = Jsoup.parse(html)
        // Solo dentro de la lista de contactos: en la misma página está la de peticiones de
        // amistad (#incomingreqs), cuyas filas usan el mismo molde y NO son contactos.
        val caja = doc.selectFirst("#buddylist") ?: doc.selectFirst("#buddylist_change_form")
            ?: return emptyList()
        val vistos = LinkedHashMap<String, Contacto>()
        for (fila in caja.select("[id~=^user\\d+$]")) {
            val uid = ID_FILA.find(fila.id())?.groupValues?.get(1) ?: continue
            val nombre = fila.selectFirst("a[href*=member.php]")?.text()?.trim().orEmpty()
            vistos.getOrPut(uid) { Contacto(uid, nombre) }
        }
        // Los corchetes del nombre no caben en un selector de Jsoup: se filtra con el regex.
        for (check in caja.select("input[name]")) {
            val uid = CHECK.find(check.attr("name"))?.groupValues?.get(1) ?: continue
            if (uid in vistos) continue
            // El enlace del mismo usuario, por su uid; si no hay, sin nombre.
            val nombre = caja.select("a[href*=member.php]").firstOrNull {
                UID_ENLACE.find(it.attr("href"))?.groupValues?.get(1) == uid
            }?.text()?.trim().orEmpty()
            vistos[uid] = Contacto(uid, nombre)
        }
        return vistos.values.toList()
    }
}

object Contactos {

    /** Un uid con el que se puede pedir algo a FC: numérico y no el `0` del enmascarado (gotcha 1). */
    fun uidValido(uid: String?): Boolean =
        !uid.isNullOrBlank() && uid.all { it.isDigit() } && uid.trimStart('0').isNotEmpty()

    fun esContacto(uid: String, contactos: List<Contacto>): Boolean =
        uidValido(uid) && contactos.any { it.uid == uid }

    /** Texto de la acción que se ofrece (la CONTRARIA a lo que ya pasa, como Ignorar). */
    fun etiqueta(esContacto: Boolean): String =
        if (esContacto) "Quitar de contactos" else "Añadir a contactos"

    /**
     * Qué decirle al usuario tras una acción, con la lista RECIÉN traída como evidencia.
     *
     * @param motivoFc el error que dio FC (en la confirmación o al enviar). Solo se enseña si la
     *   lista dice que NO salió bien: FC a veces añade y aun así pinta algo en esa caja.
     * @return el texto del aviso y si salió bien.
     */
    fun resultado(
        accion: String,
        uid: String,
        okPeticion: Boolean,
        contactosDespues: List<Contacto>,
        motivoFc: String
    ): Pair<Boolean, String> {
        val anadir = accion == "add"
        if (!okPeticion) {
            return false to when {
                motivoFc == "login" -> "Inicia sesión para gestionar tus contactos"
                motivoFc == "cloudflare" -> "ForoCoches está pidiendo verificación; inténtalo en un momento"
                // Un texto con espacios es un mensaje de FC; lo demás (no-form, uid, errores de
                // red en inglés de fetch) no le dice nada a nadie.
                motivoFc.contains(' ') && !motivoFc.startsWith("TypeError") -> motivoFc
                anadir -> "No se pudo añadir a tus contactos"
                else -> "No se pudo quitar de tus contactos"
            }
        }
        val esta = contactosDespues.any { it.uid == uid }
        val ok = if (anadir) esta else !esta
        if (ok) return true to (if (anadir) "Añadido a tus contactos" else "Quitado de tus contactos")
        return false to when {
            motivoFc.contains(' ') -> motivoFc
            anadir -> "No se pudo añadir a tus contactos"
            else -> "No se pudo quitar de tus contactos"
        }
    }

    /**
     * Añade un destinatario al campo "Para" de un MP. FC separa los destinatarios con `;`
     * (vBulletin). Si ya está (sin mirar mayúsculas), no se repite.
     */
    fun anadirDestinatario(actual: String, nombre: String): String {
        val n = nombre.trim()
        if (n.isEmpty()) return actual
        val partes = actual.split(';').map { it.trim() }.filter { it.isNotEmpty() }
        if (partes.any { it.equals(n, ignoreCase = true) }) return partes.joinToString("; ")
        return (partes + n).joinToString("; ")
    }
}
