package com.fcplus.forocoches

/**
 * Las URLs de un hilo, y la regla que nos costó el bug de las "páginas congeladas".
 *
 * **GOTCHA (medido por CDP el 2026-09-15, hilo 10804016): FC IGNORA `page=` cuando la URL
 * lleva `p=`.** No devuelve error ni redirige: sirve tranquilamente la página que contiene
 * ese post, sea cual sea el `page` que le pidas.
 *
 *   `?p=519079808`         → 519079675 … 519079808
 *   `?p=519079808&page=2`  → 519079675 … 519079808   ← idéntico
 *   `?p=519079808&page=3`  → 519079675 … 519079808   ← idéntico
 *   `?t=10804016&page=2`   → 519079810 … 519079981   ← correcto
 *
 * Es la misma familia que el gotcha 5 (`goto=lastpost` ignorado): FC se traga parámetros que
 * no piensa respetar, así que un 200 no significa que te haya hecho caso.
 *
 * Por eso toda URL de hilo se **canonicaliza a `?t=`** en cuanto se conoce el tid, y la
 * paginación se construye SIEMPRE sobre la canónica.
 */
object UrlHilo {

    private const val BASE = "https://forocoches.com/foro/showthread.php"

    /** El `t=` del hilo, si la URL lo lleva como parámetro de verdad. */
    private val PARAM_TID = Regex("""[?&]t=\d+""")

    /**
     * ¿Esta URL identifica el hilo por su tid?
     *
     * Se mira el parámetro ENTERO, no un `contains("t=")`: "showthread" lleva una `t` y un
     * `highlight=t=2` también colaría, y con eso la canonicalización se daría por hecha sobre
     * una URL que en realidad es por post.
     */
    fun tieneTid(url: String): Boolean = PARAM_TID.containsMatchIn(url)

    /** URL canónica del hilo [tid]. */
    fun canonica(tid: String): String = "$BASE?t=$tid"

    /**
     * Pasa [url] a su forma canónica en cuanto se conoce el [tid]. Sin tid, o si ya es
     * canónica, se devuelve tal cual.
     */
    fun canonicalizar(url: String, tid: String): String =
        if (tid.isEmpty() || tieneTid(url)) url else canonica(tid)

    /**
     * URL de la página [page] del hilo.
     *
     * Si la URL es por post se reconstruye desde [tid], que es lo único que FC respeta. Sin
     * tid **no se añade `page=`**: sería pedir algo que FC va a ignorar y hacer creer que el
     * salto funcionó — exactamente el fallo que reportaron los usuarios.
     */
    fun pagina(url: String, page: Int, tid: String): String {
        val base = canonicalizar(url, tid)
        return if (page > 1 && tieneTid(base)) "$base&page=$page" else base
    }
}
