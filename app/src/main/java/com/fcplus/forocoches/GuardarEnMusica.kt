package com.fcplus.forocoches

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.os.Bundle
import android.util.Log
import android.widget.Toast

/**
 * "Guardar en Música" en el menú de compartir de Android: una actividad SOLO de paso, sin pantalla
 * (`Theme.NoDisplay`), que se cierra en el mismo `onCreate`.
 *
 * Ruling 30: nunca una segunda MainActivity. Antes era un `activity-alias` de MainActivity y cada
 * compartir abría otra (el alias hereda el launchMode standard), con su propio WebView contra FC
 * (medido: encima de la que había, `onCreate` con `raíz=false`). Ahora el texto se le pasa a la
 * MainActivity que ya haya:
 * - `NEW_TASK`: a la tarea de ForoPlus, no a la de la app que comparte.
 * - `CLEAR_TOP`: si en esa tarea hay algo encima de MainActivity (solo pueden ser actividades de
 *   OTRAS apps abiertas desde ForoPlus: el menú de compartir, el selector de fotos, la hoja de
 *   WhatsApp…), se cierra. Sin él, SINGLE_TOP no encuentra a MainActivity arriba y Android apila
 *   otra encima de eso.
 * - `SINGLE_TOP`: la que hay recibe `onNewIntent`. Sin él, CLEAR_TOP la destruiría y la volvería a
 *   crear (standard), y con ella el borrador del composer.
 *
 * El intent es de launcher (MAIN + LAUNCHER) con el texto en [EntradaCompartida.EXTRA], nunca un
 * SEND: ni IntentRouter ni el camino del launcher lo pueden tomar por otra cosa, y como con
 * CLEAR_TOP pasa a ser el intent de la tarea, que sea "igual" al del icono (filterEquals no mira
 * los extras) hace que tocar el icono después siga trayendo esta tarea y no apile otra MainActivity.
 */
class GuardarEnMusica : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val crudo = intent.getCharSequenceExtra(Intent.EXTRA_TEXT)
        val asunto = intent.getCharSequenceExtra(Intent.EXTRA_SUBJECT)
        // Solo en el build depurable: lo que manda cada app tal cual (medición 5 del spec) y quién.
        if ((applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0) {
            Log.d("Musica", "SEND de ${referrer?.host} (flags=0x${Integer.toHexString(intent.flags)}) " +
                "asunto=[$asunto] texto=[$crudo]")
        }
        val texto = EntradaCompartida.texto(intent.action, intent.type, crudo, asunto)
        when {
            texto == null -> {}
            !EntradaCompartida.traeCanciones(texto) -> avisarSinCanciones(applicationContext)
            else -> startActivity(paraMainActivity(this, texto))
        }
        finish()
    }

    companion object {
        private fun paraMainActivity(ctx: Context, texto: String): Intent =
            Intent.makeMainActivity(ComponentName(ctx, MainActivity::class.java))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .putExtra(EntradaCompartida.EXTRA, texto)

        /** Con el contexto de la app: quien lo pide se cierra justo después. */
        fun avisarSinCanciones(ctx: Context) {
            Toast.makeText(ctx.applicationContext,
                "No he encontrado ninguna canción de YouTube o Spotify en lo que has compartido",
                Toast.LENGTH_LONG).show()
        }
    }
}
