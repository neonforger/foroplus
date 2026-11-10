package com.fcplus.forocoches

import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.fcplus.forocoches.VerificacionMusica.Fin
import com.fcplus.forocoches.VerificacionMusica.Motivo
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog

/**
 * La hoja "Verifícate con tu cuenta de ForoCoches" (C2). Las decisiones están en
 * [VerificacionMusica]; esto pinta, lanza la verificación en el hilo del coche
 * ([MotorCocheFC.hilo]) y guarda el token de la cuenta con la que EMPEZÓ.
 *
 * Cerrar la hoja no corta nada: la verificación sigue hasta devolver el coche (su `finally`), y el
 * resultado sale en un aviso si la hoja ya no está.
 *
 * @param alVerificar en el hilo de la interfaz, tras guardar un token (la portada se repinta).
 */
class HojaVerificarMusica(
    private val act: AppCompatActivity,
    private val enganches: EnganchesMusica,
    private val alVerificar: () -> Unit
) {
    private var dialog: BottomSheetDialog? = null
    private lateinit var progreso: View
    private lateinit var progresoTexto: TextView
    private lateinit var resultado: TextView
    private lateinit var boton: TextView

    /** Lo que hace el botón ahora mismo. */
    private var accion: () -> Unit = {}

    fun mostrar() {
        val vista = LayoutInflater.from(act).inflate(R.layout.hoja_verificar_musica, null)
        val sheet = BottomSheetDialog(act)
        sheet.setContentView(vista)
        // Sin esto el botón queda bajo la barra de navegación (gotcha BottomSheet).
        val base = vista.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(vista) { v, insets ->
            val barras = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(v.paddingLeft, v.paddingTop, v.paddingRight, base + barras.bottom)
            insets
        }
        sheet.behavior.skipCollapsed = true
        sheet.behavior.state = BottomSheetBehavior.STATE_EXPANDED

        progreso = vista.findViewById(R.id.verificar_progreso)
        progresoTexto = vista.findViewById(R.id.verificar_progreso_texto)
        resultado = vista.findViewById(R.id.verificar_resultado)
        boton = vista.findViewById(R.id.verificar_boton)
        boton.setOnClickListener { accion() }

        dialog = sheet
        sheet.setOnDismissListener { dialog = null }

        if (enganches.uidActivo().isEmpty() && !enganches.cambiandoDeCuenta()) {
            pedirEntrar("Para verificarte, entra antes con tu cuenta de ForoCoches.")
        } else ponerBoton("Verificarme") { empezar() }
        sheet.show()
    }

    private fun empezar() {
        // Antes que lo de la sesión: a mitad de un cambio de cuenta uidActivo vale "", y no es
        // que no haya sesión. Esto solo ahorra el viaje: el guard que cuenta está en
        // VerificacionMusica, donde se escribe.
        if (enganches.cambiandoDeCuenta()) {
            pintarResultado(VerificacionMusica.aviso(Motivo.CAMBIANDO_DE_CUENTA, null).texto, error = true)
            return
        }
        val uid = enganches.uidActivo()
        if (uid.isEmpty()) {
            pedirEntrar("Para verificarte, entra antes con tu cuenta de ForoCoches.")
            return
        }
        resultado.visibility = View.GONE
        progresoTexto.text = "Comprobando con ForoCoches… Tu coche volverá solo en unos segundos."
        progreso.visibility = View.VISIBLE
        boton.isEnabled = false
        boton.alpha = 0.5f

        val motor = enganches.motorCoche.para(uid)
        val api = enganches.api()
        MotorCocheFC.hilo.execute {
            // Si había uno, VerificacionMusica lo devuelve antes de nada (y si no puede, no verifica).
            val habia = enganches.pendientes.de(uid) != null
            val fin = try {
                VerificacionMusica(api, motor, enganches.pendientes.para(uid), System::currentTimeMillis) { Thread.sleep(it) }
                    // Si la cuenta activa deja de ser la del principio, también es "cambiando".
                    .ejecutar(uid) { enganches.cambiandoDeCuenta() || enganches.uidActivo() != uid }
            } catch (e: Exception) {
                Fin.Error(Motivo.RED)
            }
            when (fin) {
                is Fin.Ok -> enganches.tokens.guardar(uid, fin.token)
                // `baneada` antes que cualquier "token inservible": el que hubiera ya no sirve, y lo
                // que se dice es que la cuenta está bloqueada.
                is Fin.Error -> if (fin.motivo == Motivo.BANEADA) { enganches.tokens.borrar(uid); enganches.tokens.marcarBaneada(uid) }
            }
            val quedaPuesto = enganches.pendientes.de(uid) != null
            // Nunca `$fin` a secas: el toString de Ok lleva el token.
            val resumen = if (fin is Fin.Ok) "Ok(puedeVotar=${fin.puedeVotar})" else fin.toString()
            android.util.Log.i("Musica", "verificación: $resumen, había un original guardado de antes: $habia, " +
                "error del motor ${motor.ultimoError}, coche de prueba aún puesto: $quedaPuesto")
            // Un segundo intento de devolverlo, por si fue un tropiezo (va detrás, en el mismo hilo).
            if (quedaPuesto) enganches.restaurarCoche(uid)
            act.runOnUiThread { terminar(fin, motor.ultimoError, quedaPuesto) }
        }
    }

    private fun terminar(fin: Fin, errorMotor: String?, quedaPuesto: Boolean) {
        val nota = if (quedaPuesto) "\n\nTu coche de prueba sigue en el perfil: la app lo devolverá sola en cuanto ForoCoches la deje." else ""
        val (texto, error, entrar) = when (fin) {
            is Fin.Ok -> {
                val quien = if (fin.nombre.isNotEmpty()) " como ${fin.nombre}" else ""
                val voto = if (fin.puedeVotar) "" else
                    " Para votar y proponer, tu cuenta de ForoCoches necesita al menos 90 días."
                Triple("Listo: ya estás verificado$quien.$voto", false, false)
            }
            is Fin.Error -> VerificacionMusica.aviso(fin.motivo, errorMotor).let { Triple(it.texto, true, it.entrar) }
        }
        if (fin is Fin.Ok) alVerificar()
        if (dialog == null) {
            Toast.makeText(act, texto + nota, Toast.LENGTH_LONG).show()
            return
        }
        progreso.visibility = View.GONE
        boton.isEnabled = true
        boton.alpha = 1f
        pintarResultado(texto + nota, error)
        when {
            fin is Fin.Ok -> ponerBoton("Hecho") { dialog?.dismiss() }
            entrar -> ponerBoton("Entrar") { dialog?.dismiss(); enganches.entrar() }
            else -> ponerBoton("Volver a probar") { empezar() }
        }
    }

    private fun pedirEntrar(texto: String) {
        pintarResultado(texto, error = false)
        ponerBoton("Entrar") { dialog?.dismiss(); enganches.entrar() }
    }

    private fun pintarResultado(texto: String, error: Boolean) {
        resultado.text = texto
        resultado.setTextColor(act.getColor(if (error) R.color.fc_rojo else R.color.fc_texto))
        resultado.visibility = View.VISIBLE
    }

    private fun ponerBoton(texto: String, alPulsar: () -> Unit) {
        boton.text = texto
        accion = alPulsar
    }
}
