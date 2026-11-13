package com.fcplus.forocoches

/**
 * Notas privadas sobre usuarios: un texto tuyo pegado a un usuario, que solo se guarda en ESTE
 * móvil y NUNCA viaja a ForoCoches. Idea sacada de `forocoches-premium`, pero mejorada: aquel
 * userscript ataba la nota al NICK (un navegador solo tiene el nombre a mano); nosotros tenemos el
 * UID real del autor (el HTML crudo del hilo lo conserva aunque FC lo enmascare a u=0), así que la
 * nota se ata a la PERSONA. Importa porque en FC la gente se renombra y los nicks se reutilizan: una
 * nota por nombre acabaría pegada a quien herede ese nick.
 *
 * Regla de la clave: por UID cuando FC lo expone; por NICK en minúsculas como respaldo cuando no hay
 * uid (anónimo/baneado/alguna ficha de invitado). Sin uid ni nick no se puede identificar: no hay nota.
 *
 * La lógica es pura y se prueba sin Android: opera sobre [Almacen] (un par leer/escribir), que en la
 * app envuelve a SharedPreferences y en los tests es un mapa.
 */
object ClaveNota {
    /** La clave de almacenamiento de la nota sobre este autor, o null si no hay forma de identificarlo. */
    fun de(uid: String, nick: String): String? {
        val u = uid.trim()
        if (u.isNotEmpty() && u != "0") return "u:$u"
        val n = nick.trim().lowercase()
        return if (n.isEmpty()) null else "n:$n"
    }
}

class NotasUsuario(private val almacen: Almacen) {
    /** Persistencia mínima. `escribir` con valor null BORRA la clave. */
    interface Almacen {
        fun leer(clave: String): String?
        fun escribir(clave: String, valor: String?)
    }

    /** La nota sobre este autor, ya recortada, o "" si no hay ninguna. */
    fun get(uid: String, nick: String): String =
        ClaveNota.de(uid, nick)?.let { almacen.leer(it) }?.trim() ?: ""

    /** Si hay una nota (no vacía) sobre este autor. */
    fun hay(uid: String, nick: String): Boolean = get(uid, nick).isNotEmpty()

    /**
     * Guarda la nota sobre este autor. Un texto que queda vacío tras recortar BORRA la nota (vaciar
     * el bocadillo = quitarla). Sin forma de identificar al autor no hace nada.
     */
    fun poner(uid: String, nick: String, texto: String) {
        val clave = ClaveNota.de(uid, nick) ?: return
        val t = texto.trim()
        almacen.escribir(clave, t.ifEmpty { null })
    }
}
