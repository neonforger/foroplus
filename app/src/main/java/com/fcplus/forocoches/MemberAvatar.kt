package com.fcplus.forocoches

/**
 * Elige el avatar que pertenece a UN usuario concreto entre las imágenes de su página de perfil.
 *
 * El bug que arregla (reportado por un tester el 2026-08-11): `fcLoadMember` cogía la PRIMERA
 * imagen de avatar del documento, y en el skin moderno la primera es la de la cabecera, o sea
 * **la tuya**. Resultado: abrías el perfil de otro y veías su nombre con tu foto.
 *
 * La regla correcta no es posicional sino de pertenencia: FC mete el uid DENTRO de la URL
 * (`customavatars/thumbs/avatar<uid>_<rev>.gif`, verificado contra HTML real, y `image.php?u=<uid>`).
 * Si ninguna candidata es de ese usuario se devuelve "" — mejor sin avatar, que la app ya pinta la
 * inicial de color, que con la cara de otra persona.
 */
object MemberAvatar {

    /** Primera candidata que pertenezca a [uid], o "" si ninguna. */
    fun pick(candidates: List<String>, uid: String): String {
        if (!uid.all { it.isDigit() } || uid.isEmpty()) return ""
        return candidates.firstOrNull { belongsTo(it, uid) }.orEmpty()
    }

    /** ¿Esta URL de avatar es de [uid]? Solo por el uid incrustado, nunca por posición. */
    fun belongsTo(url: String, uid: String): Boolean {
        if (url.isBlank() || uid.isEmpty()) return false
        // El relleno de FC cuando alguien no tiene avatar; no es de nadie.
        if (url.endsWith(".svg", ignoreCase = true)) return false
        // El separador final es imprescindible: sin él, el uid 1234 casaría con avatar12345_1.
        if (Regex("avatar$uid[_.]").containsMatchIn(url)) return true
        if (Regex("[?&]u=$uid(?:&|$)").containsMatchIn(url)) return true
        return false
    }
}
