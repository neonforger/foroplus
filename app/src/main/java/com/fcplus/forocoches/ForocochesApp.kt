package com.fcplus.forocoches

import android.app.Application
import androidx.work.WorkManager

class ForocochesApp : Application() {
    override fun onCreate() {
        super.onCreate()

        // El tema, lo PRIMERO: si se aplica después de inflar la primera pantalla, Android
        // tiene que recrear la actividad y el usuario ve el cambiazo al arrancar.
        TemaApp.aplicarGuardado(getSharedPreferences("shell_prefs", MODE_PRIVATE))

        RemoteConfig.refresh(this) // actualiza selectores para la próxima carga

        try {
            // DOS trabajos periódicos que ya no existen y hay que DESENCOLAR, no solo dejar de
            // encolar: en los móviles que ya tienen la app siguen apuntados y sobreviven a la
            // actualización, así que sin esto seguirían despertándose para siempre llamando a
            // workers borrados.
            //
            // · "ignore-list-refresh": la lista de ignorados se lee solo al pintar listas y
            //   hilos —con la app delante—, así que un worker cada 30 min gastaba batería para
            //   tener al día un dato que nadie miraba. La pide el motor al abrir la app y al
            //   iniciar sesión (fcLoadIgnoreList).
            // · "notification-poll": las notificaciones push se quitaron enteras el 2026-09-21
            //   (ver MEMORY.md). Este es el que sondeaba el foro cada 15 minutos.
            WorkManager.getInstance(this).cancelUniqueWork("ignore-list-refresh")
            WorkManager.getInstance(this).cancelUniqueWork("notification-poll")
        } catch (_: IllegalStateException) {
            // WorkManager not initialized (e.g., in tests)
        }

        // Los DOS canales se borran a mano: si no, se quedan para siempre en los ajustes del
        // sistema de quien ya tenía la app, anunciando algo que ya no existe. Los ids están
        // copiados de los ficheros que se borraron: "fc_notifications" era el de los avisos
        // (NotificationHelper) y "fc_service2" el del servicio en primer plano.
        try {
            val nm = getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
            // Los canales son de Android 8. En un Android 7 la llamada no existe y lanza
            // NoSuchMethodError, que es un Error y el catch de abajo NO lo para: la app se
            // cerraba al abrirla (ArranqueAndroid7Test). Allí no hay canales que borrar.
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                nm.deleteNotificationChannel("fc_notifications")
                nm.deleteNotificationChannel("fc_service2")
            }
            // Y el aviso que pudiera quedar colgando en la bandeja de una versión anterior:
            // sin esto se queda ahí hasta que el usuario lo aparte a mano.
            nm.cancelAll()
        } catch (_: Exception) {
        }
    }
}
