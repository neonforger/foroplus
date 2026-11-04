package com.fcplus.forocoches

import android.content.Context
import android.print.PrintAttributes
import android.print.PrintManager
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity
import java.io.ByteArrayInputStream

/**
 * Exporta una copia descargada a PDF con el diálogo de imprimir de Android ("Guardar como PDF",
 * o una impresora). Así no hace falta pedir permiso de almacenamiento: el sistema pregunta dónde
 * guardarlo. El HTML lo arma [HiloPdf].
 *
 * Un WebView que **no se ve ni se monta en pantalla** pinta ese HTML y su `PrintDocumentAdapter`
 * lo pasa a páginas. No es el foro web (regla de oro): es un documento nuestro. Y va **sin red**:
 * las fotos salen de la copia y todo lo demás se corta, salvo los emoticonos de FC, que no se
 * guardan en las copias (si no hay conexión, simplemente no salen).
 */
class ExportarPdf(
    private val activity: AppCompatActivity,
    private val almacen: AlmacenDescargas,
    private val toast: (String) -> Unit
) {
    /** El WebView del último PDF: tiene que vivir mientras el sistema lo imprime. */
    private var web: WebView? = null

    fun exportar(tid: String) {
        val json = almacen.leer(tid) ?: run { toast("No se encuentra la copia de este hilo"); return }
        val doc = HiloPdf.documento(json, { almacen.tieneImagen(tid, it) })
            ?: run { toast("Esta copia no tiene mensajes que exportar"); return }
        val titulo = try { org.json.JSONObject(json).optString("titulo") } catch (_: Exception) { "" }
        toast("Preparando el PDF…")

        web?.destroy()
        var impreso = false
        val wv = WebView(activity)
        wv.settings.javaScriptEnabled = false
        wv.settings.allowFileAccess = false
        wv.settings.allowContentAccess = false
        wv.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(view: WebView, req: WebResourceRequest): WebResourceResponse? {
                val url = req.url.toString()
                if (url.startsWith(HiloPdf.URL_IMAGEN)) {
                    val original = url.removePrefix(HiloPdf.URL_IMAGEN).toIntOrNull()?.let { doc.imagenes.getOrNull(it) }
                    val bytes = original?.let { almacen.imagen(tid, it) }
                    if (bytes != null) return WebResourceResponse("image/jpeg", null, ByteArrayInputStream(bytes))
                }
                // Emoticonos de FC: lo único que sale a la red (ver la cabecera de la clase).
                if (url.startsWith("https://forocoches.com/") && Descargas.esSmiley(url)) return null
                return WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream(ByteArray(0)))
            }

            // Un documento para imprimir no navega a ningún sitio.
            override fun shouldOverrideUrlLoading(view: WebView, req: WebResourceRequest) = true

            override fun onPageFinished(view: WebView, url: String?) {
                if (impreso || web !== view) return
                impreso = true
                imprimir(view, titulo)
            }
        }
        web = wv
        wv.loadDataWithBaseURL("https://copia.foroplus.invalid/", doc.html, "text/html", "utf-8", null)
    }

    private fun imprimir(wv: WebView, titulo: String) {
        if (activity.isFinishing) return
        val pm = activity.getSystemService(Context.PRINT_SERVICE) as? PrintManager
            ?: run { toast("Este móvil no permite imprimir ni guardar PDF"); return }
        val nombre = HiloPdf.nombreArchivo(titulo)
        val atributos = PrintAttributes.Builder()
            .setMediaSize(PrintAttributes.MediaSize.ISO_A4)
            .build()
        pm.print(nombre, wv.createPrintDocumentAdapter(nombre), atributos)
    }
}
