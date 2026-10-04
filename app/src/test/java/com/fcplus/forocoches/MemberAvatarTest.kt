package com.fcplus.forocoches

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Formatos reales de FC, sacados de HTML en vivo (2026-08-12):
 *   https://cdn2.forocoches.com/customavatars/thumbs/avatar568909_3.gif
 *   /image/new_icons/avatar.svg   (relleno de quien no tiene avatar)
 */
class MemberAvatarTest {

    private val miAvatar = "https://cdn2.forocoches.com/customavatars/thumbs/avatar111111_2.gif"
    private val suAvatar = "https://cdn2.forocoches.com/customavatars/thumbs/avatar568909_3.gif"

    @Test
    fun `coge el avatar del usuario aunque no sea el primero`() {
        // ESTE es el bug: el primero de la página es el de la cabecera, o sea el TUYO.
        assertEquals(suAvatar, MemberAvatar.pick(listOf(miAvatar, suAvatar), "568909"))
    }

    @Test
    fun `si el unico avatar de la pagina no es suyo, no se ensena nada`() {
        // Mejor la inicial de color que la cara de otra persona.
        assertEquals("", MemberAvatar.pick(listOf(miAvatar), "568909"))
    }

    @Test
    fun `no confunde un uid con otro que lo contiene`() {
        val otro = "https://cdn2.forocoches.com/customavatars/thumbs/avatar5689090_1.gif"
        assertEquals("", MemberAvatar.pick(listOf(otro), "568909"))
        assertFalse(MemberAvatar.belongsTo("customavatars/avatar12345_1.gif", "1234"))
    }

    @Test
    fun `reconoce tambien el formato image punto php`() {
        assertTrue(MemberAvatar.belongsTo("https://forocoches.com/foro/image.php?u=568909", "568909"))
        assertTrue(MemberAvatar.belongsTo("image.php?u=568909&dateline=123", "568909"))
        assertFalse(MemberAvatar.belongsTo("image.php?u=5689099", "568909"))
    }

    @Test
    fun `el relleno svg no es de nadie`() {
        assertFalse(MemberAvatar.belongsTo("/image/new_icons/avatar.svg", "568909"))
        assertEquals("", MemberAvatar.pick(listOf("/image/new_icons/avatar.svg"), "568909"))
    }

    @Test
    fun `sin candidatas devuelve vacio`() {
        assertEquals("", MemberAvatar.pick(emptyList(), "568909"))
    }

    @Test
    fun `uid vacio o no numerico no casa con nada`() {
        assertEquals("", MemberAvatar.pick(listOf(suAvatar), ""))
        assertEquals("", MemberAvatar.pick(listOf(suAvatar), "abc"))
        // Un uid vacío no puede convertir cualquier URL en válida por accidente.
        assertFalse(MemberAvatar.belongsTo(suAvatar, ""))
    }

    @Test
    fun `entre varias suyas se queda con la primera`() {
        val otra = "https://cdn2.forocoches.com/customavatars/avatar568909_9.gif"
        assertEquals(suAvatar, MemberAvatar.pick(listOf(miAvatar, suAvatar, otra), "568909"))
    }
}
