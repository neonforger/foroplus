package com.fcplus.forocoches

import android.content.Context
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.util.TypedValue
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.content.ContextCompat

/**
 * Leer un atributo `fc*` del tema actual (ver attrs.xml y EstiloApp). Es lo que usa el código
 * que pinta fondos y márgenes a mano (adaptadores, barra de páginas…) para seguir el estilo sin
 * preguntar cuál es: el tema ya trae la respuesta.
 */
object AtributosTema {

    private fun resolver(ctx: Context, attr: Int): TypedValue? {
        val tv = TypedValue()
        return if (ctx.theme.resolveAttribute(attr, tv, true)) tv else null
    }

    /** Un drawable NUEVO (cada vista necesita el suyo), o null si el atributo es @null. */
    fun drawable(ctx: Context, attr: Int): Drawable? {
        val tv = resolver(ctx, attr) ?: return null
        if (tv.resourceId != 0) return AppCompatResources.getDrawable(ctx, tv.resourceId)
        if (tv.type in TypedValue.TYPE_FIRST_COLOR_INT..TypedValue.TYPE_LAST_COLOR_INT) return ColorDrawable(tv.data)
        return null
    }

    /** Píxeles de un atributo de dimensión (0 si no está). */
    fun dimen(ctx: Context, attr: Int): Int {
        val tv = resolver(ctx, attr) ?: return 0
        return TypedValue.complexToDimensionPixelSize(tv.data, ctx.resources.displayMetrics)
    }

    fun color(ctx: Context, attr: Int): Int {
        val tv = resolver(ctx, attr) ?: return 0
        return if (tv.resourceId != 0) ContextCompat.getColor(ctx, tv.resourceId) else tv.data
    }

    /**
     * Aplica al tema de [ctx] el tono del modo oscuro guardado (ver [TonoOscuro]). Lo usa
     * MainActivity antes de inflar, y los diálogos con tema propio (FC.PantallaCompleta): ese
     * tema hereda del base y, al aplicarse, devuelve las superficies al gris de fábrica.
     */
    fun aplicarTono(ctx: Context) {
        val prefs = ctx.getSharedPreferences("shell_prefs", Context.MODE_PRIVATE)
        TonoOscuro.overlay(TonoOscuro.guardado(prefs))?.let { ctx.theme.applyStyle(it, true) }
    }

    fun bool(ctx: Context, attr: Int): Boolean {
        val tv = resolver(ctx, attr) ?: return false
        return tv.type == TypedValue.TYPE_INT_BOOLEAN && tv.data != 0
    }
}
