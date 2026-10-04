package com.fcplus.forocoches

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.WebView
import android.webkit.WebViewClient

/**
 * Los hilos **trending** de un subforo: los que FC saca en su propia página `trending.php`.
 *
 * ## Por qué esto existe aparte del motor
 *
 * `trending.php` **solo existe en el juego de plantillas de ESCRITORIO**. Pedida desde el motor
 * de la app —que usa User-Agent de móvil— devuelve **200 con el cuerpo vacío**, y da igual el
 * `referrer` o mandarla como AJAX: las tres variantes vuelven vacías (medido el 2026-08-27).
 * El foro móvil ni siquiera la enlaza: cero apariciones de `trending.php` en el HTML del índice
 * y del listado. No hay forma de sacarla con el UA de móvil.
 *
 * Así que se pide desde un WebView **aparte, con UA de escritorio**, que se crea al vuelo, hace
 * su carga y se destruye. Las cookies son del proceso (`CookieManager`), así que la sesión va
 * sola.
 *
 * ## El riesgo, dicho claro
 *
 * Esto es un **disfraz de UA**, el mismo patrón que este proyecto retiró en agosto con la lista
 * de ignorados ("funcionaba por disfraz, no por diseño: Cloudflare ata la `cf_clearance` al
 * UA"). La diferencia, y por la que aquí se acepta, es el **modo de fallo**: aquella lista era
 * una función central que al romperse dejaba al usuario creyendo que funcionaba. Esta, si
 * vuelve vacía o Cloudflare la corta, devuelve una lista vacía y la app **esconde el botón**.
 * Nada más depende de ella. Es prescindible por diseño, y por eso puede permitirse el disfraz.
 *
 * ## El orden es el de FC, y no se toca
 *
 * El ranking de FC **no es por número de respuestas** — medido: un hilo de 255 respuestas va
 * por delante de otro de 431 —, así que respetando el orden en el que vienen las filas nos
 * llevamos su criterio, sea cual sea, sin tener que adivinarlo. Ordenar por respuestas fue el
 * primer intento y devolvía los megahilos de siempre (las peñas, los "Vol. 4353"), que no es
 * lo que la gente entiende por trending.
 */
object Trending {

    /**
     * UA de escritorio. Es lo único que hace que FC sirva la página; con el de móvil llega
     * vacía. Se mantiene genérico y actual a propósito: uno raro llama más la atención.
     */
    private const val UA_ESCRITORIO =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/124.0.0.0 Safari/537.36"

    /** Si en este tiempo no hay respuesta, se abandona y se avisa. */
    private const val ESPERA_MS = 15_000L

    // MEDIDO en el dispositivo (2026-08-27): cada carga tarda **~430 ms**, y un subforo sin
    // trending devuelve la lista vacía en el mismo tiempo. Lo importante para quien venga
    // detrás: **"tener trending" depende de la HORA, no del subforo** — Electrónica dio
    // resultados por la tarde y devolvió vacío seis veces seguidas a las 3:40 de la mañana.
    // Por eso NO se recuerda qué subforos "no tienen": apuntarlo de madrugada escondería el
    // botón todo el día siguiente. Decisión del dueño: el botón se queda siempre visible y,
    // si no hay nada, se avisa y se vuelve al listado.

    fun url(forumId: Int): String =
        "https://forocoches.com/foro/trending.php?forumid=$forumId&all&display"

    /**
     * Extrae las filas del DOM de la página ya cargada.
     *
     * Las reglas salen de sondear el markup real (2026-08-27) y se comprobaron sobre las cinco
     * primeras filas antes de escribir nada:
     * - El título es un `<strong>` DENTRO del enlace al hilo. Ese `> strong` es además lo que
     *   deja fuera el banner de invitaciones, que también es un `showthread.php?t=`.
     * - La fila es el padre del `<p>` que envuelve ese enlace.
     * - Dentro de la fila: el enlace cuyo texto son solo dígitos es el **contador de
     *   respuestas**, y su href es un `?p=` — o sea, el enlace al último mensaje, gratis.
     * - El autor es el enlace cuyo texto empieza por `@`.
     * - **No hay hora**: la página de trending no la trae, así que `time` va vacío.
     */
    val EXTRACTOR = """
        (function () {
          var filas = [];
          document.querySelectorAll('a[href*="showthread.php?t="] > strong').forEach(function (st) {
            var a = st.parentElement;
            var p = a.closest('p');
            var fila = p ? p.parentElement : null;
            if (!fila) return;
            var tid = ((a.getAttribute('href') || '').match(/[?&]t=(\d+)/) || [])[1] || '';
            if (!tid) return;
            var enlaces = [].slice.call(fila.querySelectorAll('a'));
            var resp = null, autor = null;
            enlaces.forEach(function (x) {
              var t = (x.textContent || '').trim();
              if (!resp && /^[\d.,]+${'$'}/.test(t)) resp = x;
              if (!autor && t.charAt(0) === '@') autor = x;
            });
            filas.push({
              tid: tid,
              title: (st.textContent || '').replace(/\s+/g, ' ').trim(),
              author: autor ? (autor.textContent || '').trim().replace(/^@/, '') : '',
              replies: resp ? (resp.textContent || '').trim() : '',
              time: '',
              url: 'https://forocoches.com/foro/showthread.php?t=' + tid,
              lastUrl: resp ? (resp.getAttribute('href') || '') : ''
            });
          });
          return JSON.stringify({ threads: filas });
        })()
    """.trimIndent()

    /**
     * Trae el trending de [forumId]. Devuelve el JSON con las filas, o **cadena vacía** si no
     * se ha podido — que es la señal de "esconde el botón", nunca un error a la cara.
     */
    @SuppressLint("SetJavaScriptEnabled")
    fun cargar(context: Context, forumId: Int, alTerminar: (String) -> Unit) {
        val principal = Handler(Looper.getMainLooper())
        var contestado = false
        val web = WebView(context.applicationContext)

        fun terminar(json: String, w: WebView) {
            if (contestado) return
            contestado = true
            w.stopLoading()
            w.destroy()
            alTerminar(json)
        }

        web.settings.javaScriptEnabled = true
        web.settings.userAgentString = UA_ESCRITORIO
        web.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String) {
                view.evaluateJavascript(EXTRACTOR) { crudo ->
                    // evaluateJavascript devuelve el valor como literal JSON: una cadena
                    // entrecomillada y escapada. Hay que desenvolverla antes de parsearla.
                    val json = try {
                        org.json.JSONTokener(crudo).nextValue() as? String ?: ""
                    } catch (_: Exception) { "" }
                    terminar(json, view)
                }
            }

            override fun onReceivedError(
                view: WebView, req: android.webkit.WebResourceRequest?,
                err: android.webkit.WebResourceError?
            ) {
                if (req?.isForMainFrame == true) terminar("", view)
            }
        }
        principal.postDelayed({ terminar("", web) }, ESPERA_MS)
        web.loadUrl(url(forumId))
    }
}
