package com.fcplus.forocoches

import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner

/**
 * El panel del avatar salió de MainActivity (regla 10). Lo que se fija aquí es el contrato con
 * quien lo crea: abrir lo enseña y pide el perfil si aún no hay línea, cerrar sin animar lo quita
 * al momento, y las filas las pone quien lo crea (MainActivity sabe qué hay en la barra).
 */
@RunWith(RobolectricTestRunner::class)
class CuentaPanelControllerTest {

    private fun actividad(): AppCompatActivity {
        val a = Robolectric.buildActivity(AppCompatActivity::class.java).get()
        a.setTheme(R.style.Theme_FCForocoches)
        a.setContentView(LinearLayout(a))
        return a
    }

    @Test
    fun `abrir lo enseña con sus filas y cerrar sin animar lo quita`() {
        val a = actividad()
        var pedido = 0
        val panel = CuentaPanelController(
            activity = a,
            nombre = { "tu_usuario" },
            lineaPerfil = { "" },
            pedirPerfil = { pedido++ },
            pintarAvatar = {},
            alVerFicha = {},
            alSalir = {},
            rellenar = {
                fila(R.drawable.ic_opciones, "Opciones") {}
                separador()
                fila(R.drawable.ic_cuentas, "Cambiar de cuenta", insignia = 3) {}
            }
        )
        panel.configurar()
        assertFalse(panel.visible)

        panel.abrir()
        assertTrue(panel.visible)
        assertEquals(1, pedido)
        val raiz = a.findViewById<View>(R.id.panel_cuenta)
        assertEquals(View.VISIBLE, raiz.visibility)
        assertEquals("tu_usuario", a.findViewById<TextView>(R.id.cuenta_nombre).text.toString())
        assertEquals(3, a.findViewById<LinearLayout>(R.id.cuenta_filas).childCount)

        panel.cerrar(animar = false)
        assertFalse(panel.visible)
        assertEquals(View.GONE, raiz.visibility)
    }
}
