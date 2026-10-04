package com.fcplus.forocoches

import android.app.Activity
import android.content.Context
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.install.InstallStateUpdatedListener
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.InstallStatus
import com.google.android.play.core.install.model.UpdateAvailability

/** Versión nueva disponible. */
data class Actualizacion(val codigo: Int)

/**
 * Aviso de actualización, detrás de una interfaz A PROPÓSITO.
 *
 * Hoy solo hay una implementación, la de Google Play. Pero el día que la app se distribuya
 * fuera de Play (web + Telegram), lo que cambia es SOLO la implementación: comprobar contra un
 * fichero propio y descargar el APK. El resto de la app llama siempre a lo mismo, así que esa
 * migración no toca ni una línea de MainActivity.
 *
 * OJO con lo que NO se puede hacer: instalar un APK desde la app requiere el permiso
 * `REQUEST_INSTALL_PACKAGES`, que estando en Play es buscarse un problema en las revisiones.
 * Por eso la implementación de descarga directa iría en un flavor aparte, nunca en el de Play.
 */
interface Actualizador {

    /** ¿Hay versión nueva? Devuelve null si estás al día o si no se puede saber. */
    fun comprobar(cb: (Actualizacion?) -> Unit)

    /**
     * Arranca lo que corresponda (el flujo de Play, o descargar el APK el día de mañana).
     *
     * @param bloqueante flujo del que no se puede salir sin actualizar.
     * @param onFallo se invoca si NO se pudo lanzar. Es imprescindible cuando el aviso no se
     *   puede cerrar: si el usuario solo tiene un botón y ese botón no hace nada, la app queda
     *   inservible y su única salida es desinstalar.
     */
    fun lanzar(
        activity: Activity,
        actualizacion: Actualizacion,
        bloqueante: Boolean = false,
        onFallo: () -> Unit = {}
    )

    /**
     * Al volver a la app. Es imprescindible: si el usuario se fue mientras se descargaba, la
     * actualización se queda bajada y sin instalar, y sin esto no se entera nadie nunca.
     */
    fun alReanudar(activity: Activity)

    /** Se llama cuando la descarga ha terminado y solo falta reiniciar para instalarla. */
    var onDescargaLista: (() -> Unit)?

    /** Instala lo ya descargado (reinicia la app). */
    fun completar()
}

/**
 * Implementación con la API de Google Play. Solo dice que hay algo si la app se instaló DESDE
 * Play y la versión publicada en el canal de ese usuario es mayor que la suya: en una build de
 * debug (que no viene de Play) siempre contestará que no hay nada, y eso es lo normal, no un fallo.
 */
class ActualizadorPlay(context: Context) : Actualizador {

    private val manager = AppUpdateManagerFactory.create(context.applicationContext)

    override var onDescargaLista: (() -> Unit)? = null

    /**
     * Se usa el flujo FLEXIBLE, no el inmediato: la descarga va en segundo plano y el usuario
     * sigue leyendo el foro mientras tanto. El inmediato bloquea la app con una pantalla
     * completa y solo se justifica para arreglar algo grave.
     */
    private val escucha = InstallStateUpdatedListener { estado ->
        if (estado.installStatus() == InstallStatus.DOWNLOADED) onDescargaLista?.invoke()
    }

    init {
        manager.registerListener(escucha)
    }

    override fun comprobar(cb: (Actualizacion?) -> Unit) {
        manager.appUpdateInfo
            .addOnSuccessListener { info ->
                val hay = info.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE &&
                    info.isUpdateTypeAllowed(AppUpdateType.FLEXIBLE)
                cb(if (hay) Actualizacion(info.availableVersionCode()) else null)
            }
            // Sin Play Services, sin red o instalado por sideload: no se sabe, y no pasa nada.
            .addOnFailureListener { cb(null) }
    }

    override fun lanzar(
        activity: Activity,
        actualizacion: Actualizacion,
        bloqueante: Boolean,
        onFallo: () -> Unit
    ) {
        val tipo = if (bloqueante) AppUpdateType.IMMEDIATE else AppUpdateType.FLEXIBLE
        manager.appUpdateInfo
            .addOnSuccessListener { info ->
                val puede = info.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE &&
                    info.isUpdateTypeAllowed(tipo)
                if (!puede) { onFallo(); return@addOnSuccessListener }
                val lanzado = runCatching {
                    manager.startUpdateFlowForResult(info, tipo, activity, COD_PETICION)
                }.isSuccess
                if (!lanzado) onFallo()
            }
            .addOnFailureListener { onFallo() }
    }

    override fun alReanudar(activity: Activity) {
        // Descarga que quedó terminada mientras la app estaba en segundo plano: sin esto se
        // queda ahí para siempre y el usuario no vuelve a ver el aviso.
        manager.appUpdateInfo.addOnSuccessListener { info ->
            if (info.installStatus() == InstallStatus.DOWNLOADED) onDescargaLista?.invoke()
        }
    }

    /** Instala lo descargado. Reinicia la app, así que solo se llama si el usuario lo pide. */
    override fun completar() {
        manager.completeUpdate()
    }

    companion object {
        const val COD_PETICION = 4711
    }
}
