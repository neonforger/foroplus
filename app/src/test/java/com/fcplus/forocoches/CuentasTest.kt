package com.fcplus.forocoches

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Las cuentas guardadas en la app. Puro: el almacenamiento y la UI van aparte.
 *
 * Robolectric solo porque [Cuentas] usa org.json: en JVM puro esas clases están mockeadas y
 * lanzan RuntimeException.
 */
@RunWith(RobolectricTestRunner::class)
class CuentasTest {

    private val a = Cuenta("911128", "usuarioA", "https://x/a.png")
    private val b = Cuenta("222222", "usuarioB", "")

    @Test
    fun `una lista vacia se lee sin romper`() {
        assertEquals(emptyList<Cuenta>(), Cuentas.leer(""))
        assertEquals(emptyList<Cuenta>(), Cuentas.leer("no es json"))
    }

    @Test
    fun `lo guardado se vuelve a leer igual`() {
        assertEquals(listOf(a, b), Cuentas.leer(Cuentas.guardar(listOf(a, b))))
    }

    @Test
    fun `anadir pone la cuenta nueva al final`() {
        assertEquals(listOf(a, b), Cuentas.anadir(listOf(a), b))
    }

    /** Volver a entrar con una cuenta ya guardada refresca su nombre y avatar, no la duplica. */
    @Test
    fun `anadir una cuenta que ya esta la actualiza en su sitio`() {
        val renombrada = Cuenta("911128", "usuarioA2", "https://x/nuevo.png")
        val r = Cuentas.anadir(listOf(a, b), renombrada)
        assertEquals(2, r.size)
        assertEquals(renombrada, r[0])
    }

    @Test
    fun `no se pasa del tope de cinco`() {
        val cinco = (1..5).map { Cuenta("u$it", "n$it", "") }
        assertFalse(Cuentas.hayHueco(cinco))
        assertEquals(cinco, Cuentas.anadir(cinco, Cuenta("u6", "n6", "")))
    }

    /** Pero actualizar una existente SÍ se puede aunque esté lleno. */
    @Test
    fun `con el tope lleno todavia se puede actualizar una existente`() {
        val cinco = (1..5).map { Cuenta("u$it", "n$it", "") }
        val r = Cuentas.anadir(cinco, Cuenta("u3", "nuevo", ""))
        assertEquals(5, r.size)
        assertEquals("nuevo", r.first { it.uid == "u3" }.nombre)
    }

    @Test
    fun `quitar saca solo esa cuenta`() {
        assertEquals(listOf(b), Cuentas.quitar(listOf(a, b), "911128"))
    }

    @Test
    fun `quitar una que no esta no cambia nada`() {
        assertEquals(listOf(a, b), Cuentas.quitar(listOf(a, b), "999"))
    }

    /** Al quitar la activa hay que pasar a alguna: la primera que quede. */
    @Test
    fun `siguienteTras devuelve la primera que queda`() {
        assertEquals("222222", Cuentas.siguienteTras(listOf(a, b), "911128"))
    }

    @Test
    fun `siguienteTras devuelve vacio si no queda ninguna`() {
        assertEquals("", Cuentas.siguienteTras(listOf(a), "911128"))
    }

    /** Un uid vacío no es una cuenta: no debe entrar nunca en la lista. */
    @Test
    fun `una cuenta sin uid no se anade`() {
        assertEquals(listOf(a), Cuentas.anadir(listOf(a), Cuenta("", "x", "")))
    }

    // ── Cabecera del composer: con qué cuenta publicas ────────────────────────
    // La regla de todo este bloque es CALLAR ANTES QUE MENTIR: encima de una caja de texto,
    // una cuenta equivocada es peor que ninguna porque invita a confiar.

    @Test
    fun `paraComposer devuelve la cuenta activa`() {
        assertEquals(b, Cuentas.paraComposer(listOf(a, b), "222222", cambiando = false))
    }

    /** A diferencia del avatar de la barra rápida, aquí no hay puerta de "dos o más". */
    @Test
    fun `paraComposer se enseña tambien con una sola cuenta`() {
        assertEquals(a, Cuentas.paraComposer(listOf(a), "911128", cambiando = false))
    }

    /** Mientras FC no confirme quién eres, no se dice nada. */
    @Test
    fun `paraComposer calla durante un cambio de cuenta`() {
        assertNull(Cuentas.paraComposer(listOf(a, b), "222222", cambiando = true))
    }

    @Test
    fun `paraComposer calla sin sesion`() {
        assertNull(Cuentas.paraComposer(listOf(a, b), "", cambiando = false))
    }

    @Test
    fun `paraComposer calla si el uid activo no esta en la lista`() {
        assertNull(Cuentas.paraComposer(listOf(a, b), "999", cambiando = false))
    }

    /** "como @" no informa de nada, así que una cuenta sin nombre tampoco se enseña. */
    @Test
    fun `paraComposer calla si la cuenta no tiene nombre`() {
        val sinNombre = Cuenta("333333", "   ", "")
        assertNull(Cuentas.paraComposer(listOf(sinNombre), "333333", cambiando = false))
    }

    @Test
    fun `etiquetaComposer lleva la arroba delante`() {
        assertEquals("como @usuarioA", Cuentas.etiquetaComposer(a))
    }
}
