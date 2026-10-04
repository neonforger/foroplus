package com.fcplus.forocoches

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient

class ForocochesWebViewClient(
    private val context: Context,
    private val repo: IgnoreListRepository,
    private val keywordRepo: KeywordRepository,
    private val onPageLoad: ((String) -> Unit)? = null
) : WebViewClient() {

    private val contentJs: String by lazy {
        context.assets.open("content.js").bufferedReader().readText()
    }

    private val extractorJs: String by lazy {
        context.assets.open("extractor.js").bufferedReader().readText()
    }

    private val adblockCss: String by lazy {
        context.assets.open("adblock.css").bufferedReader().readText()
    }

    private val settingsPanelJs: String by lazy {
        context.assets.open("settings-panel.js").bufferedReader().readText()
    }

    override fun shouldInterceptRequest(
        view: WebView,
        request: WebResourceRequest
    ): WebResourceResponse? {
        if (AdBlocker.shouldBlock(request.url.toString())) {
            return WebResourceResponse("text/plain", "utf-8", "".byteInputStream())
        }
        // No servimos copias del HTML: el filtrado (hilos y posts, ambos skins) lo hace
        // content.js ocultando por DOM, que no rompe el render JS de FC.
        return null
    }

    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
        val targetUrl = request.url?.toString() ?: return false
        // Solo gobernamos la navegacion del frame principal. Los iframes (embeds de
        // Instagram, YouTube, Twitter...) se cargan con normalidad: corren en su propio
        // origen, sin la sesion ni los scripts de FC, asi que no son un riesgo.
        if (!request.isForMainFrame) return false
        if (TrustedOrigins.isTrustedForocochesUrl(targetUrl)) return false
        if (TrustedOrigins.isHttpOrHttps(targetUrl)) {
            openExternal(targetUrl)
        }
        return true
    }

    override fun onPageFinished(view: WebView, url: String) {
        if (!TrustedOrigins.isTrustedForocochesUrl(url)) {
            onPageLoad?.invoke(url)
            return
        }
        injectCss(view, adblockCss)
        // Config de selectores (remota o bundleada) ANTES de content.js, que la lee.
        view.evaluateJavascript("window.FC_CONFIG=${RemoteConfig.cachedJson(context)};", null)
        view.evaluateJavascript(contentJs, null)
        view.evaluateJavascript(settingsPanelJs, null)
        view.evaluateJavascript(extractorJs, null) // motor de datos del shell nativo (v2)
        onPageLoad?.invoke(url)
    }

    private fun injectCss(view: WebView, css: String) {
        val escaped = css
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "")
        view.evaluateJavascript(
            """(function(){var s=document.createElement('style');s.textContent="$escaped";document.head&&document.head.appendChild(s);})();""",
            null
        )
    }

    private fun openExternal(url: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                addCategory(Intent.CATEGORY_BROWSABLE)
            }
            context.startActivity(intent)
        } catch (_: Exception) {
        }
    }

}
