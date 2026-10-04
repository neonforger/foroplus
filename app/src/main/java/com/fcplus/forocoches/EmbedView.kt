package com.fcplus.forocoches

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.widget.FrameLayout
import android.widget.TextView
import org.json.JSONArray

/** Un embed dentro de un post: red social o multimedia con su reproductor oficial. */
data class EmbedSpec(
    val kind: String,   // twitter | instagram | tiktok | youtube | video | iframe
    val url: String,
    val id: String,
    /**
     * Alto en CSS px que declara el propio HTML de FC, 0 si no dice nada. Los reproductores
     * que no son 16:9 lo necesitan: el de Vocaroo es `height="60"` y con el alto genérico de
     * 200 px salía como un ladrillo verde con un reproductor diminuto dentro.
     */
    val alto: Int = 0
)

fun parseEmbeds(arr: JSONArray?): List<EmbedSpec> {
    if (arr == null) return emptyList()
    val out = ArrayList<EmbedSpec>()
    for (i in 0 until arr.length()) {
        val o = arr.optJSONObject(i) ?: continue
        val kind = o.optString("kind").trim()
        if (kind.isEmpty()) continue
        out.add(
            EmbedSpec(
                kind, o.optString("url").trim(), o.optString("id").trim(),
                o.optInt("alto", 0).coerceAtLeast(0)
            )
        )
    }
    return out
}

/**
 * Embed interactivo. Al enlazarse el post monta DIRECTAMENTE (sin toque) un WebView que carga
 * el reproductor OFICIAL de la plataforma; el vídeo se reproduce inline al darle al play del
 * propio reproductor, sin salir de la app. La carga se difiere ~300 ms: así los posts que
 * pasan volando en un fling NO llegan a crear un WebView (se cancela al reciclar), y los hilos
 * largos siguen fluidos. No es el foro: la regla de oro no aplica a contenido de X/IG/TikTok/
 * YouTube dentro de nuestra tarjeta.
 */
@SuppressLint("SetJavaScriptEnabled")
class EmbedView(context: Context) : FrameLayout(context) {

    private var web: WebView? = null
    private var spec: EmbedSpec? = null
    private var pendingLoad: Runnable? = null

    /**
     * ¿El usuario ha llegado a TOCAR este embed?
     *
     * Es la señal de "aquí hay algo en marcha". No se puede preguntar al reproductor si está
     * reproduciendo —es un iframe de otro dominio y no nos habla—, pero un embed que nadie ha
     * tocado seguro que no está sonando, y uno que sí, casi seguro que sí. Con eso basta para
     * decidir a cuál de los dos hay que salvarle la vida al reciclar el mensaje.
     */
    var enUso = false
        private set

    /** Qué embed es, para poder reengancharlo al mensaje correcto cuando vuelva a pantalla. */
    var clave: String = ""

    /**
     * ¿Está ahora mismo en la ventanita flotante (ver [MiniReproductor])?
     *
     * Cambia dos cosas: el reproductor pasa a ocupar todo el hueco que le den, y **deja de
     * hacerle caso al puente de altura**. Ese puente existe para que el embed mida lo que mide
     * su contenido dentro del mensaje; flotando, el que manda es el marco, y si no se apagara
     * se pelearían: el iframe reporta su alto, el WebView se estira, y la ventanita se
     * descuadra sola.
     */
    var flotando = false
        set(valor) {
            field = valor
            val w = web ?: return
            if (valor) {
                altoAntesDeFlotar = w.layoutParams.height
                w.layoutParams = w.layoutParams.apply { height = LayoutParams.MATCH_PARENT }
            } else if (altoAntesDeFlotar > 0) {
                w.layoutParams = w.layoutParams.apply { height = altoAntesDeFlotar }
            }
            w.requestLayout()
        }

    /** Alto que tenía el reproductor dentro del mensaje, para devolvérselo al volver. */
    private var altoAntesDeFlotar = 0

    /**
     * ¿Está el vídeo reproduciéndose? Lo dice la propia página por el puente.
     *
     * **No vale preguntárselo al sistema** (`AudioManager.isMusicActive`), que era lo primero
     * que se probó: cuando salta `onViewRecycled` el WebView ya está desenganchado y Chromium
     * ya ha pausado el medio, así que siempre sale "no". Medido en el dispositivo.
     */
    var reproduciendo = false
        private set


    /** Callback para pedir modo pantalla completa de vídeo al Activity anfitrión. */
    var onFullscreen: ((View?, WebChromeClient.CustomViewCallback?) -> Unit)? = null

    /** Alto de partida del reproductor: el que declare el foro, o el genérico de 220dp. */
    private fun altoInicial(spec: EmbedSpec) = if (spec.alto > 0) dp(spec.alto) else dp(220)

    private fun dp(v: Int) = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), resources.displayMetrics
    ).toInt()

    fun bind(spec: EmbedSpec) {
        this.spec = spec
        enUso = false
        release()
        removeAllViews()
        addView(buildLoading())
        // Auto-carga diferida: si el post se recicla antes de los 300 ms (fling), el WebView
        // no llega a crearse (release() cancela el Runnable).
        pendingLoad = Runnable { load(spec) }.also { postDelayed(it, 300) }
    }

    /** Libera el WebView y cancela una carga pendiente (al reciclar el post o salir del hilo). */
    fun release() {
        pendingLoad?.let { removeCallbacks(it) }
        pendingLoad = null
        web?.let {
            it.stopLoading()
            it.loadUrl("about:blank")
            (it.parent as? ViewGroup)?.removeView(it)
            it.destroy()
        }
        web = null
    }

    /**
     * Deja de sonar **sin destruir nada**: al salir del hilo el panel solo se oculta, y ocultar
     * una vista NO la desengancha de la ventana — que es lo único que pausa a Chromium por su
     * cuenta (ver `onViewDetachedFromWindow` en PostAdapter). Por eso un tweet o un vídeo que
     * seguía **visible** en pantalla continuaba sonándote encima del listado.
     *
     * Se usa `onPause()` del WebView y no JavaScript a propósito: el medio vive dentro de un
     * iframe de otro dominio (Twitter, YouTube), así que desde nuestra página no se le puede
     * llamar a `pause()`. `onPause()` sí lo alcanza, venga de donde venga.
     */
    fun pausar() {
        web?.onPause()
    }

    /** Lo contrario de [pausar]. NO vuelve a dar al play: solo deja el reproductor operativo. */
    fun despertar() {
        web?.onResume()
    }

    /** Marcador ligero mientras carga el reproductor (no interactivo, evita saltos bruscos). */
    private fun buildLoading(): View {
        return TextView(context).apply {
            text = "Cargando contenido…"
            setTextColor(col(R.color.fc_texto_3))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(16), dp(14), dp(16))
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
                .apply { topMargin = dp(6); bottomMargin = dp(2) }
            background = roundedBg(col(R.color.fc_chip))
        }
    }

    private fun load(spec: EmbedSpec) {
        removeAllViews()
        val w = WebView(context.applicationContext).apply {
            // Con alto declarado se arranca ya con el bueno: el reproductor no da el salto de
            // 220dp a 60dp delante del usuario.
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, altoInicial(spec))
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.loadWithOverviewMode = true
            settings.useWideViewPort = true
            // El embed se muestra montado pero NO autoreproduce: el vídeo arranca al pulsar el
            // play del propio reproductor (un gesto del usuario), inline. Sin audio al scrollear.
            settings.mediaPlaybackRequiresUserGesture = true
            settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            // Los embeds sociales (tweet, IG, TikTok, botón "Ver en YouTube") abren el enlace con
            // target="_blank"/window.open() DESDE SU PROPIO IFRAME: no es una navegación de
            // main-frame, así que shouldOverrideUrlLoading NO la ve. Sin esto el WebView traga el
            // popup en silencio y el tap "no hace nada". Con multi-ventana + onCreateWindow abajo
            // capturamos la URL destino y la mandamos a la app/navegador externo.
            settings.setSupportMultipleWindows(true)
            // Nunca dejamos que Chromium "oscurezca" el contenido por su cuenta: lo que hay
            // dentro son widgets de terceros y el resultado dependía del embed (un tuit salía
            // bien y otro se quedaba en blanco). El tema se pide EXPLÍCITAMENTE abajo.
            if (androidx.webkit.WebViewFeature.isFeatureSupported(
                    androidx.webkit.WebViewFeature.ALGORITHMIC_DARKENING)) {
                androidx.webkit.WebSettingsCompat.setAlgorithmicDarkeningAllowed(settings, false)
            }
            setBackgroundColor(fondoEmbed(spec.kind))
            // Tocar el embed = darle al play (o interactuar con el tweet). A partir de ahí este
            // reproductor no se destruye al salir de pantalla: ver [enUso] y PostAdapter.
            //
            // SOLO cuenta el ACTION_UP, y esto no es un detalle: al deslizar el hilo, el dedo
            // pasa por encima del embed y el WebView recibe DOWN y MOVE igual. Marcando con
            // cualquier evento, **todos** los embeds por los que scrolleas quedaban marcados
            // como "en uso" (medido en el dispositivo), y entonces el que se salva es uno
            // cualquiera en vez del que estás viendo. Cuando el RecyclerView se queda el
            // gesto, al hijo le llega ACTION_CANCEL, no ACTION_UP: esa es la diferencia entre
            // "he tocado esto" y "he pasado el dedo por encima".
            //
            // Devuelve false SIEMPRE: el WebView tiene que seguir recibiendo el gesto.
            setOnTouchListener { _, e ->
                if (e.actionMasked == android.view.MotionEvent.ACTION_UP) enUso = true
                false
            }
            addJavascriptInterface(HeightBridge(), "AndroidEmbed")
            webViewClient = object : android.webkit.WebViewClient() {
                // Solo intercepta la navegación del MARCO PRINCIPAL (clic en un enlace del
                // embed → navegador externo). Los subrecursos (widgets.js, iframe de vídeo,
                // etc.) NO son main-frame y se cargan con normalidad: el reproductor funciona.
                override fun shouldOverrideUrlLoading(
                    view: WebView, req: android.webkit.WebResourceRequest
                ): Boolean {
                    if (!req.isForMainFrame) return false
                    return openExternal(req.url.toString())
                }
            }
            webChromeClient = object : WebChromeClient() {
                override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
                    onFullscreen?.invoke(view, callback)
                }
                override fun onHideCustomView() { onFullscreen?.invoke(null, null) }

                // El widget abre el enlace en una ventana nueva (target="_blank"/window.open).
                // No creamos ventana real: montamos un WebView efímero solo para que su
                // shouldOverrideUrlLoading nos entregue la URL destino, la lanzamos fuera y lo
                // destruimos. Así el tap en el tweet/vídeo salta a la app o al navegador.
                //
                // **NO se usa `hitTestResult`**, y esto era un atajo que estaba MINTIENDO.
                // Reportado el 2026-08-26 ("pinches donde pinches te lleva a la foto de perfil
                // de Twitter") y medido: al tocar el avatar de un tuit, `hitTestResult.type`
                // vale 8 (`SRC_IMAGE_ANCHOR_TYPE`, una imagen dentro de un enlace) y para ese
                // tipo `getExtra()` devuelve **la URL de la IMAGEN, no la del enlace** — o sea
                // que abríamos el fichero `pbs.twimg.com/profile_images/….jpg`. Literalmente
                // la foto de perfil. La URL buena es la que pide el propio widget, y esa la da
                // el WebView efímero de abajo.
                override fun onCreateWindow(
                    view: WebView?, isDialog: Boolean, isUserGesture: Boolean,
                    resultMsg: android.os.Message?
                ): Boolean {
                    val tmp = WebView(context.applicationContext)
                    var usado = false
                    tmp.webViewClient = object : android.webkit.WebViewClient() {
                        override fun shouldOverrideUrlLoading(
                            v: WebView, r: android.webkit.WebResourceRequest
                        ): Boolean {
                            usado = true
                            openExternal(r.url.toString())
                            v.destroy()
                            return true
                        }
                    }
                    val transport = resultMsg?.obj as? WebView.WebViewTransport
                    transport?.webView = tmp
                    resultMsg?.sendToTarget()
                    // Red de seguridad: si la ventana nueva no llega a navegar a ninguna parte
                    // (el widget la abre y no la usa), ese WebView se quedaría vivo para
                    // siempre. Sin esto, cada tap fallido deja uno colgado.
                    tmp.postDelayed({ if (!usado) tmp.destroy() }, 5_000)
                    return true
                }
            }
        }
        web = w
        addView(w)
        w.loadDataWithBaseURL(baseUrlFor(spec.kind), pageHtml(spec), "text/html", "UTF-8", null)
    }

    /**
     * Le dice al reproductor que siga.
     *
     * Hace falta al mudar el WebView de contenedor (a la ventanita flotante y de vuelta):
     * cambiar de padre lo **desengancha** un instante de la ventana, y ahí Chromium pausa el
     * medio. Volver a engancharlo no lo reanuda solo — medido en el dispositivo: la ventanita
     * salía y el audio se quedaba a cero igual.
     *
     * El vídeo de YouTube vive en un iframe de OTRO dominio, así que no se le puede llamar
     * directamente: se le manda la orden por `postMessage` con la API del reproductor (de ahí
     * el `enablejsapi=1` de la URL). Un `<video>` normal sí es nuestro y basta con `play()`.
     *
     * Que esto no lo bloquee la política de autoplay depende de que el usuario ya haya tocado
     * el reproductor — y para llegar aquí lo ha tocado, que es justo la condición para flotar.
     */
    fun reanudar() {
        val w = web ?: return
        w.evaluateJavascript(
            """
            (function () {
              try {
                var f = document.querySelector('iframe');
                if (f && f.contentWindow) {
                  f.contentWindow.postMessage(
                    '{"event":"command","func":"playVideo","args":[]}', '*');
                }
              } catch (e) {}
              try { var v = document.querySelector('video'); if (v) v.play(); } catch (e) {}
            })()
            """.trimIndent(), null
        )
    }

    /** Lanza [url] fuera de la app (app nativa de la plataforma o navegador). true si era http(s). */
    private fun openExternal(url: String): Boolean {
        if (!url.startsWith("http")) return false
        try {
            context.startActivity(
                android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url))
                    .addCategory(android.content.Intent.CATEGORY_BROWSABLE)
            )
        } catch (_: Exception) {}
        return true
    }

    private inner class HeightBridge {
        /** La página avisa de si el vídeo está sonando. Ver [reproduciendo]. */
        @JavascriptInterface
        fun onEstado(activo: Boolean) {
            post { reproduciendo = activo }
        }

        @JavascriptInterface
        fun onHeight(cssPx: Int) {
            if (cssPx <= 0) return
            // Flotando manda el marco de la ventanita, no el contenido: ver [flotando].
            if (flotando) return
            post {
                val w = web ?: return@post
                // El suelo de 120dp evita que un embed que aún está montándose se quede
                // aplastado, pero NO puede pisar un alto que el propio foro declara: si FC
                // dice 60, 60 es la verdad.
                val declarado = spec?.alto ?: 0
                val suelo = if (declarado > 0) dp(declarado) else dp(120)
                val px = (cssPx * resources.displayMetrics.density).toInt()
                    .coerceIn(suelo, dp(2200))
                if (w.layoutParams.height != px) {
                    w.layoutParams = w.layoutParams.apply { height = px }
                    w.requestLayout()
                }
            }
        }
    }

    /** ¿La app se está pintando en oscuro ahora mismo? */
    private fun esOscuro(): Boolean = TemaApp.esOscuro(resources.configuration.uiMode)

    /**
     * Fondo de la tarjeta del embed.
     *
     * X y YouTube saben pintarse en oscuro, así que en modo oscuro se integran con la app.
     * **Instagram y TikTok no**: sus widgets son claros y punto, y ponerles detrás el fondo
     * oscuro solo consigue un marco negro alrededor de una tarjeta blanca. Para esos se deja
     * el blanco, que es más honesto que fingir un modo oscuro que la plataforma no tiene.
     */
    private fun fondoEmbed(kind: String): Int =
        if (esOscuro() && kind in setOf("twitter", "youtube", "video", "iframe"))
            col(R.color.fc_superficie) else Color.WHITE

    private fun hex(color: Int) = String.format("#%06X", 0xFFFFFF and color)

    private fun baseUrlFor(kind: String) = when (kind) {
        "twitter" -> "https://twitter.com"
        "instagram" -> "https://www.instagram.com"
        "tiktok" -> "https://www.tiktok.com"
        // El embed de YouTube es un iframe DIRECTO a youtube.com/embed y su handshake postMessage
        // exige ser cross-origin respecto a la página anfitriona. Si la base fuera youtube.com el
        // iframe quedaría mismo-origen y el reproductor falla (error 150/152). Origen de tercero:
        "youtube" -> "https://forocoches.com"
        else -> "https://www.youtube.com"
    }

    /** HTML mínimo que monta el embed OFICIAL de cada plataforma + reporte de altura. */
    private fun pageHtml(spec: EmbedSpec): String {
        val body = when (spec.kind) {
            "twitter" ->
                // data-theme se lo decimos NOSOTROS. Sin este atributo el widget elige según
                // lo que crea que es el sistema, y ahí es donde salían resultados distintos.
                """<blockquote class="twitter-tweet" data-dnt="true" data-conversation="none"
                   data-theme="${if (esOscuro()) "dark" else "light"}">
                   <a href="${esc(spec.url)}"></a></blockquote>
                   <script async src="https://platform.twitter.com/widgets.js"></script>"""
            "instagram" ->
                """<blockquote class="instagram-media" data-instgrm-permalink="${esc(spec.url)}"
                   data-instgrm-version="14" style="margin:0;width:100%"></blockquote>
                   <script async src="https://www.instagram.com/embed.js"></script>"""
            "tiktok" ->
                """<blockquote class="tiktok-embed" cite="${esc(spec.url)}"
                   data-video-id="${esc(spec.id)}" style="margin:0"><section></section></blockquote>
                   <script async src="https://www.tiktok.com/embed.js"></script>"""
            "youtube" ->
                // enablejsapi=1: es lo que permite decirle "sigue" por postMessage cuando el
                // reproductor se muda a la ventanita flotante (ver [reanudar]).
                """<div class="yt"><iframe src="https://www.youtube.com/embed/${esc(spec.id)}?playsinline=1&rel=0&enablejsapi=1&origin=https%3A%2F%2Fforocoches.com"
                   frameborder="0" allow="encrypted-media; picture-in-picture" allowfullscreen></iframe></div>"""
            "video" ->
                """<video src="${esc(spec.url)}" controls playsinline preload="metadata" style="width:100%;height:auto"></video>"""
            else -> {
                // Sin alto declarado se deja el mínimo genérico (un iframe sin altura colapsa
                // a nada); con él manda el del foro.
                val alto = if (spec.alto > 0) "height:${spec.alto}px" else "min-height:200px"
                """<iframe src="${esc(spec.url)}" frameborder="0" allow="autoplay; encrypted-media; fullscreen"
                   allowfullscreen style="width:100%;border:0;$alto"></iframe>"""
            }
        }
        return """<!doctype html><html><head><meta charset="utf-8">
            <meta name="viewport" content="width=device-width, initial-scale=1, maximum-scale=1">
            <style>
              html,body{margin:0;padding:0;background:${hex(fondoEmbed(spec.kind))};overflow-x:hidden}
              .yt{position:relative;width:100%;padding-bottom:56.25%;height:0}
              .yt iframe{position:absolute;top:0;left:0;width:100%;height:100%}
              iframe,video{max-width:100%}
            </style></head><body>$body
            <script>
              function rep(){try{AndroidEmbed.onHeight(document.body.scrollHeight);}catch(e){}}
              window.addEventListener('load',rep);
              var n=0,t=setInterval(function(){rep();if(++n>20)clearInterval(t);},400);
              try{new ResizeObserver(rep).observe(document.body);}catch(e){}

              // Si el vídeo está sonando o no. Lo necesita el reproductor flotante para saber
              // si hay que decirle "sigue" tras la mudanza — y para NO despertar un vídeo que
              // el usuario había pausado.
              function estado(v){try{AndroidEmbed.onEstado(!!v);}catch(e){}}
              // <video> nuestro: sus eventos no burbujean, se escuchan en fase de captura.
              document.addEventListener('play',function(e){if(e.target.tagName==='VIDEO')estado(true);},true);
              document.addEventListener('pause',function(e){if(e.target.tagName==='VIDEO')estado(false);},true);
              document.addEventListener('ended',function(e){if(e.target.tagName==='VIDEO')estado(false);},true);
              // YouTube va en un iframe de otro dominio: habla por postMessage. Con
              // enablejsapi=1 se le pide que nos suscriba a sus eventos y manda el estado
              // (1 = reproduciendo) por onStateChange y por infoDelivery.
              window.addEventListener('message',function(e){
                try{
                  var d = typeof e.data === 'string' ? JSON.parse(e.data) : e.data;
                  if(!d) return;
                  if(d.event === 'onStateChange') estado(d.info === 1);
                  else if(d.event === 'infoDelivery' && d.info &&
                          typeof d.info.playerState === 'number') estado(d.info.playerState === 1);
                }catch(err){}
              });
              function suscribir(){
                var f=document.querySelector('iframe');
                if(f&&f.contentWindow){
                  f.contentWindow.postMessage('{"event":"listening","id":1,"channel":"widget"}','*');
                }
              }
              var s=0,st=setInterval(function(){suscribir();if(++s>10)clearInterval(st);},600);
            </script></body></html>"""
    }

    /** Color de la paleta (respeta el modo oscuro; un 0xFF… a pelo NO). */
    private fun col(id: Int) = androidx.core.content.ContextCompat.getColor(context, id)

    private fun roundedBg(color: Int) = android.graphics.drawable.GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(10).toFloat()
    }

    private fun esc(s: String) = s.replace("\\", "\\\\").replace("\"", "&quot;")
        .replace("<", "&lt;").replace(">", "&gt;")
}
