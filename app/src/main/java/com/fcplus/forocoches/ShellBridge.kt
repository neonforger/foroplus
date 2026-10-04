package com.fcplus.forocoches

import android.webkit.JavascriptInterface

/**
 * Bridge del shell nativo (rama v2-shell): extractor.js entrega aquí el listado
 * parseado (JSON) o un error. Los callbacks llegan en el pool de JS del WebView;
 * quien los reciba debe saltar al hilo de UI.
 */
class ShellBridge(
    private val onList: (String) -> Unit,
    private val onError: (String) -> Unit,
    private val onForums: (String) -> Unit,
    private val onPopurriResult: (String) -> Unit,
    private val onThreadData: (String) -> Unit,
    private val onThreadDataError: (String) -> Unit,
    private val onReply: (String) -> Unit,
    private val onLogin: (String) -> Unit,
    private val onSmiliesData: (String) -> Unit,
    private val onNoticesData: (String) -> Unit,
    private val onProfileData: (String) -> Unit,
    private val onLogout: (String) -> Unit,
    private val onThreadActionData: (String) -> Unit,
    private val onEditLoadData: (String) -> Unit,
    private val onPmDataResult: (String) -> Unit,
    private val onMemberDataResult: (String) -> Unit,
    private val onPollVoteResult: (String) -> Unit,
    /** Diseño (styleid) con el que FC está sirviendo el HTML. Ver [ForumSkin]. */
    private val onSkinResult: (String) -> Unit,
    /** Lista de ignorados de la cuenta, pedida por el motor. Ver [IgnoreListParser]. */
    private val onIgnoreListResult: (String) -> Unit,
    /** Primer mensaje de un hilo, solo para la tarjeta de compartir. */
    private val onThreadHeadResult: (String) -> Unit,
    /** La cita de un mensaje, en BBCode y construida por el propio FC. */
    private val onQuoteBodyResult: (String) -> Unit,
    /** Quién es el usuario logueado (uid, nombre, avatar), para separar cuentas. */
    private val onQuienSoyData: (String) -> Unit,
    /**
     * Una página de un hilo que se está DESCARGANDO. Es el mismo JSON que [onThreadData] y sale
     * del mismo parseo: lo único que cambia es la puerta, para que guardar un hilo de 60
     * páginas no repinte el que el usuario está leyendo. Ver `fcLoadThread(url, 'descarga')`.
     */
    private val onThreadDescargaData: (String) -> Unit = {},
    private val onThreadDescargaErrorData: (String) -> Unit = {},
    /** Fecha real de una cita/mención (su lista solo trae la hora). Ver [SelloNoticia]. */
    private val onNoticeDateResult: (String) -> Unit = {},
    /** Quién escribió un mensaje: el "último que escribe" de la lista. Ver [UltimosPosteadores]. */
    private val onLastPosterResult: (String) -> Unit = {}
) {
    @JavascriptInterface
    fun onNoticeDate(json: String) = onNoticeDateResult(json)

    @JavascriptInterface
    fun onLastPoster(json: String) = onLastPosterResult(json)

    @JavascriptInterface
    fun onQuoteBody(json: String) = onQuoteBodyResult(json)

    @JavascriptInterface
    fun onThreadList(json: String) = onList(json)

    @JavascriptInterface
    fun onListError(reason: String) = onError(reason)

    @JavascriptInterface
    fun onForumList(json: String) = onForums(json)

    /** Las listas de los subforos del Popurrí, sin mezclar (ver [Popurri]). */
    @JavascriptInterface
    fun onPopurri(json: String) = onPopurriResult(json)

    @JavascriptInterface
    fun onThread(json: String) = onThreadData(json)

    @JavascriptInterface
    fun onThreadError(reason: String) = onThreadDataError(reason)

    @JavascriptInterface
    fun onThreadDescarga(json: String) = onThreadDescargaData(json)

    @JavascriptInterface
    fun onThreadDescargaError(reason: String) = onThreadDescargaErrorData(reason)

    @JavascriptInterface
    fun onReplyResult(json: String) = onReply(json)

    @JavascriptInterface
    fun onLoginResult(json: String) = onLogin(json)

    @JavascriptInterface
    fun onSmilies(json: String) = onSmiliesData(json)

    @JavascriptInterface
    fun onNotices(json: String) = onNoticesData(json)

    @JavascriptInterface
    fun onProfile(json: String) = onProfileData(json)

    @JavascriptInterface
    fun onLogoutDone(result: String) = onLogout(result)

    @JavascriptInterface
    fun onThreadAction(json: String) = onThreadActionData(json)

    @JavascriptInterface
    fun onEditLoad(json: String) = onEditLoadData(json)

    @JavascriptInterface
    fun onPmData(json: String) = onPmDataResult(json)

    @JavascriptInterface
    fun onMemberData(json: String) = onMemberDataResult(json)

    @JavascriptInterface
    fun onPollResult(json: String) = onPollVoteResult(json)

    @JavascriptInterface
    fun onSkin(json: String) = onSkinResult(json)

    @JavascriptInterface
    fun onIgnoreList(json: String) = onIgnoreListResult(json)

    @JavascriptInterface
    fun onThreadHead(json: String) = onThreadHeadResult(json)

    @JavascriptInterface
    fun onQuienSoy(json: String) { onQuienSoyData(json) }
}
