package com.fcplus.forocoches

import android.content.Context
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebView
import java.util.Collections
import java.util.concurrent.Executors

class SettingsBridge(
    private val repo: IgnoreListRepository,
    private val keywordRepo: KeywordRepository,
    private val webView: WebView,
    private val context: Context = webView.context
) {

    // --- Creador de hilo (para el listado del skin viejo móvil, que no lo muestra) ---
    private val creatorCache by lazy { ThreadCreatorCache(context) }
    private val creatorFetcher = ThreadCreatorFetcher()
    private val creatorPool = Executors.newFixedThreadPool(4) // throttle: máx 4 a la vez
    private val creatorInFlight = Collections.synchronizedSet(HashSet<Long>())

    /** Creador cacheado (instantáneo) o "" si no se conoce aún. */
    @JavascriptInterface
    fun getCachedCreator(threadId: String): String =
        threadId.toLongOrNull()?.let { creatorCache.get(it) } ?: ""

    /** Pide el creador de un hilo desconocido (descarga con corte temprano, en 2º plano). */
    @JavascriptInterface
    fun requestThreadCreator(threadId: String) {
        val tid = threadId.toLongOrNull() ?: return
        creatorCache.get(tid)?.let { notifyCreator(tid, it); return }
        if (!creatorInFlight.add(tid)) return // ya en curso
        creatorPool.execute {
            try {
                val cookie = CookieManager.getInstance().getCookie("https://forocoches.com")
                if (!cookie.isNullOrBlank()) {
                    val creator = creatorFetcher.fetchCreator(tid, cookie)
                    if (!creator.isNullOrBlank()) {
                        creatorCache.put(tid, creator)
                        notifyCreator(tid, creator)
                    }
                }
            } catch (_: Exception) {
            } finally {
                creatorInFlight.remove(tid)
            }
        }
    }

    private fun notifyCreator(tid: Long, creator: String) {
        val safe = creator.replace("\\", "\\\\").replace("'", "\\'")
        webView.post {
            webView.evaluateJavascript("window.fcOnCreator&&fcOnCreator('$tid','$safe')", null)
        }
    }

    @JavascriptInterface
    fun getIgnoredUsersJson(): String {
        val users = repo.getIgnoredUsers()
        if (users.isEmpty()) return "[]"
        return "[" + users.joinToString(",") {
            "\"${it.replace("\\", "\\\\").replace("\"", "\\\"")}\""
        } + "]"
    }

    @JavascriptInterface
    fun removeIgnoredUser(username: String) {
        val users = repo.getIgnoredUsers().toMutableList()
        users.remove(username)
        repo.setIgnoredUsers(users)
    }

    @JavascriptInterface
    fun getLastUpdatedMs(): Long = repo.getLastUpdated()

    /**
     * Panel de ajustes de la v1 (settings-panel.js). En la v2 el WebView es invisible, así
     * que esto no lo alcanza ningún usuario; se mantiene solo para no dejar el JS llamando a
     * un método que no existe. La petición va por el motor, como el resto: el HTTP nativo a
     * FC se retiró el 2026-08-21 (ver [IgnoreListParser]). Quien guarda el resultado es
     * MainActivity, en su callback de AndroidShell.
     */
    @JavascriptInterface
    fun triggerRefresh() {
        webView.post {
            webView.evaluateJavascript("window.fcLoadIgnoreList&&fcLoadIgnoreList()", null)
            notifyRefreshDone()
        }
    }

    /**
     * Recarga completa del WebView. El cambio de diseño hace el POST `updatestyleid`
     * DENTRO del WebView (fetch en settings-panel.js, sesión consistente) y al terminar
     * llama aquí para una recarga limpia y fiable.
     */
    @JavascriptInterface
    fun reloadPage() {
        webView.post { webView.reload() }
    }

    /**
     * Canario: content.js avisa cuando un selector deja de encontrar datos que SÍ están en
     * la página (probable cambio de HTML de FC). Lo dejamos en logcat (tag FC_CANARY) y
     * guardamos el último incidente por si luego montamos un panel de diagnóstico.
     */
    @JavascriptInterface
    fun reportCanary(area: String, detail: String) {
        android.util.Log.w("FC_CANARY", "[$area] $detail")
        context.getSharedPreferences("fc_health", Context.MODE_PRIVATE).edit()
            .putString("last_issue", "[$area] $detail")
            .putLong("last_issue_ts", System.currentTimeMillis())
            .apply()
    }

    @JavascriptInterface
    fun getKeywordFilterEnabled(): Boolean = keywordRepo.isEnabled()

    @JavascriptInterface
    fun setKeywordFilterEnabled(enabled: Boolean) {
        keywordRepo.setEnabled(enabled)
    }

    @JavascriptInterface
    fun getKeywordsJson(): String {
        val keywords = keywordRepo.getKeywords()
        if (keywords.isEmpty()) return "[]"
        return "[" + keywords.joinToString(",") {
            "\"${escapeJson(it)}\""
        } + "]"
    }

    @JavascriptInterface
    fun addKeyword(keyword: String) {
        if (keyword.isNotBlank()) keywordRepo.addKeyword(keyword.trim())
    }

    @JavascriptInterface
    fun removeKeyword(keyword: String) {
        keywordRepo.removeKeyword(keyword)
    }

    @JavascriptInterface
    fun resetKeywordsToDefaults() {
        keywordRepo.resetToDefaults()
    }

    private fun notifyRefreshDone() {
        webView.post {
            webView.evaluateJavascript("if(window.fcOnRefreshDone)window.fcOnRefreshDone()", null)
        }
    }

    private fun escapeJson(s: String) = s.replace("\\", "\\\\").replace("\"", "\\\"")
}
