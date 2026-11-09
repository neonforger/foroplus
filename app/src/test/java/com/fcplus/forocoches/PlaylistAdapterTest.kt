package com.fcplus.forocoches

import android.os.Looper
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/**
 * El arrastre de la pantalla de una playlist va atado a la fila que se agarra. El caso que se
 * fija: se suelta A y, mientras A aún vuelve a su sitio (su clearView llega ~200 ms después), se
 * coge B. Antes, la posición de salida de B pisaba la de A: se guardaba "B_salida → A_llegada"
 * (otra canción movida en disco), y el movimiento de B no se guardaba nunca.
 */
@RunWith(RobolectricTestRunner::class)
class PlaylistAdapterTest {

    private fun cancion(n: Int) = Cancion(
        RefCancion(FuenteMusica.YOUTUBE, "id%09d".format(n)), "Tema $n", null, null, null, null, 0L
    )

    @Test
    fun `coger otra cancion mientras la anterior vuelve a su sitio guarda los dos movimientos bien`() {
        val ctrl = Robolectric.buildActivity(AppCompatActivity::class.java)
        val a = ctrl.get()
        a.setTheme(R.style.Theme_FCForocoches)
        ctrl.setup()
        val movidas = ArrayList<Pair<Int, Int>>()
        val adaptador = PlaylistAdapter(
            alTocar = {}, alMenu = { _, _ -> }, alMover = { d, h -> movidas.add(d to h) },
            alVolver = {}, alReproducir = {}, alMenuPlaylist = {}, alCompartir = {}
        )
        val rv = RecyclerView(a)
        rv.layoutManager = LinearLayoutManager(a)
        rv.adapter = adaptador
        a.setContentView(rv)
        val cb = adaptador.callbackDeArrastre()
        adaptador.touchHelper = ItemTouchHelper(cb)
        adaptador.pintar("Prueba", (0 until 4).map { cancion(it) })
        rv.measure(
            View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(4000, View.MeasureSpec.EXACTLY)
        )
        rv.layout(0, 0, 1080, 4000)
        // Posiciones del adaptador: 0 es la cabecera; las canciones 0..3 van en 1..4.
        fun fila(posicion: Int) = rv.findViewHolderForAdapterPosition(posicion)!!

        // A (canción 0) se arrastra una posición abajo y se suelta.
        val filaA = fila(1)
        cb.onSelectedChanged(filaA, ItemTouchHelper.ACTION_STATE_DRAG)
        cb.onMove(rv, filaA, fila(2))
        // Antes de que A termine de volver, se coge B (la canción 3, ahora en la posición 4).
        val filaB = fila(4)
        cb.onSelectedChanged(filaB, ItemTouchHelper.ACTION_STATE_DRAG)
        // Llega el clearView de A, tarde: no es del arrastre de B.
        cb.clearView(rv, filaA)
        // B sube a lo más alto y se suelta.
        cb.onMove(rv, filaB, fila(3))
        cb.onMove(rv, filaB, fila(2))
        cb.onMove(rv, filaB, fila(1))
        cb.clearView(rv, filaB)
        shadowOf(Looper.getMainLooper()).idle()

        // A: de 0 a 1. B: de 3 a 0. Y en ese orden, que es como se aplicaron en la lista.
        assertEquals(listOf(0 to 1, 3 to 0), movidas)
        assertEquals(
            listOf("Tema 3", "Tema 1", "Tema 0", "Tema 2"),
            adaptador.canciones().map { it.titulo }
        )
    }
}
