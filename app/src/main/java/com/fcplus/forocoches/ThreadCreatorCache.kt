package com.fcplus.forocoches

import android.content.Context

/**
 * Caché persistente hilo→creador. El creador de un hilo no cambia nunca, así que se
 * guarda para siempre y solo se descarga cada hilo una vez en la vida.
 */
class ThreadCreatorCache(context: Context) {
    private val prefs = context.getSharedPreferences("fc_thread_creators", Context.MODE_PRIVATE)
    fun get(threadId: Long): String? = prefs.getString(threadId.toString(), null)
    fun put(threadId: Long, creator: String) {
        prefs.edit().putString(threadId.toString(), creator).apply()
    }
}
