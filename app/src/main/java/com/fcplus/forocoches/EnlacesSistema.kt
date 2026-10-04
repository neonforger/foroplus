package com.fcplus.forocoches

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings

/**
 * Lo poco que hay que preguntarle a Android sobre los enlaces de ForoCoches.
 *
 * La política (cuándo ofrecerlo) vive en [EnlacesApp], que es pura y testeable. Aquí solo está
 * lo que obliga a hablar con el sistema, y se mantiene mínimo a propósito.
 */
object EnlacesSistema {

    /**
     * ¿Existe siquiera el problema en este móvil?
     *
     * Por debajo de Android 12 el sistema ofrece la app en el diálogo de "abrir con" sin pedir
     * autorización, así que no hay nada que activar ni de qué avisar.
     */
    fun soportado(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    /**
     * ¿Están ya los enlaces asignados a la app?
     *
     * Se le pregunta al SISTEMA y no a una preferencia nuestra: el usuario puede activarlo o
     * quitarlo desde Ajustes sin pasar por la app, y fiarse de lo que creíamos recordar acabaría
     * enseñando el aviso a quien ya lo tiene puesto.
     */
    fun activados(ctx: Context): Boolean {
        if (!soportado()) return false
        return try {
            val gestor = ctx.getSystemService(
                android.content.pm.verify.domain.DomainVerificationManager::class.java
            ) ?: return false
            val estado = gestor.getDomainVerificationUserState(ctx.packageName) ?: return false
            estado.hostToStateMap.values.any {
                it == android.content.pm.verify.domain.DomainVerificationUserState.DOMAIN_STATE_SELECTED ||
                    it == android.content.pm.verify.domain.DomainVerificationUserState.DOMAIN_STATE_VERIFIED
            }
        } catch (_: Throwable) {
            // Fabricantes que no implementan bien esta API: mejor no ofrecer nada que insistirle
            // a alguien que ya lo tiene.
            true
        }
    }

    /**
     * Lleva al ajuste del sistema donde se autorizan los enlaces.
     *
     * No hay forma de activarlo desde la app: Android exige que lo haga el usuario en su
     * pantalla. Si el móvil no tiene esa pantalla concreta, se cae a la ficha de la aplicación,
     * que siempre existe.
     */
    fun abrirAjuste(ctx: Context): Boolean {
        val paquete = Uri.parse("package:" + ctx.packageName)
        val intentos = listOfNotNull(
            if (soportado()) Intent(Settings.ACTION_APP_OPEN_BY_DEFAULT_SETTINGS, paquete) else null,
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, paquete)
        )
        for (i in intentos) {
            try { ctx.startActivity(i); return true } catch (_: Throwable) { }
        }
        return false
    }
}
