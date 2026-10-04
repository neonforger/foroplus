package com.fcplus.forocoches

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
import android.text.Html
import android.text.SpannableStringBuilder
import android.text.Spannable
import android.text.Spanned
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.text.style.ImageSpan
import android.text.style.URLSpan
import android.util.LruCache
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.text.HtmlCompat
import androidx.recyclerview.widget.RecyclerView
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Collections
import java.util.concurrent.Executors

data class PostItem(
    val pid: String,
    val author: String,
    // UID real del autor (para abrir su perfil al tocar nombre/avatar). "" si es
    // anónimo/baneado o FC no lo expuso → sin perfil clicable.
    val uid: String,
    val avatar: String,
    val date: String,
    val html: String,
    val page: Int = 1,
    val own: Boolean = false,
    val embeds: List<EmbedSpec> = emptyList()
)

data class ThreadPayload(
    val url: String,
    val tid: String,
    val title: String,
    val page: Int,
    val pageCount: Int,
    val posts: List<PostItem>,
    val pmCount: Int,
    val quotesCount: Int,
    val mentionsCount: Int,
    /** Encuesta del hilo, o null si no tiene (lo normal). */
    val poll: Poll? = null,
    /** Subforo del hilo, para la miga de la cabecera. 0 / "" = no se sabe. */
    val forumFid: Int = 0,
    val forumName: String = ""
)

fun parseThreadPayload(json: String): ThreadPayload? {
    return try {
        val root = JSONObject(json)
        val pageNum = root.optInt("page", 1)
        val arr = root.getJSONArray("posts")
        val posts = ArrayList<PostItem>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val pid = o.optString("pid").trim()
            if (pid.isEmpty()) continue
            posts.add(
                PostItem(
                    pid = pid,
                    author = o.optString("author").trim(),
                    uid = o.optString("uid").trim(),
                    avatar = o.optString("avatar").trim(),
                    date = o.optString("date").trim(),
                    html = o.optString("html"),
                    page = pageNum,
                    own = o.optBoolean("own", false),
                    embeds = parseEmbeds(o.optJSONArray("embeds"))
                )
            )
        }
        val counts = root.optJSONObject("counts")
        ThreadPayload(
            url = root.optString("url"),
            tid = root.optString("tid"),
            title = root.optString("title"),
            page = root.optInt("page", 1),
            pageCount = root.optInt("pageCount", 1),
            posts = posts,
            pmCount = counts?.optInt("pm") ?: 0,
            quotesCount = counts?.optInt("quotes") ?: 0,
            mentionsCount = counts?.optInt("mentions") ?: 0,
            poll = parsePoll(root.optJSONObject("poll")),
            forumFid = root.optJSONObject("forum")?.optInt("fid", 0) ?: 0,
            forumName = root.optJSONObject("forum")?.optString("name")?.trim().orEmpty()
        )
    } catch (_: Exception) {
        null
    }
}

/**
 * Cargador de imágenes de posts (avatares, imágenes, smilies) con caché LRU.
 * NOTA de alcance: esto son GETs de imágenes (no scraping de HTML); a los hosts de FC
 * se les adjunta cookie de sesión + UA.
 */
object PostImages {
    private const val UA =
        "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"

    /**
     * Lado máximo al decodificar: **exactamente el ancho al que se van a dibujar**.
     *
     * Era un 1600 fijo "que sobra para nitidez", y no sobraba: no reducía nada (las fotos del
     * foro rondan los 1263 px), así que se guardaban 3,17 MB por foto para pintar 842 px.
     * Medido el 2026-09-11 en `t=10736632`: la caché llena a tope, 1.668 expulsiones en un
     * minuto y 2.447 descargas para 37 imágenes distintas — el parpadeo que reportaron.
     *
     * No se pierde nitidez: `ImagenEnTexto.medida` escala por densidad y **recorta a este
     * mismo ancho**, así que todo lo que se guardaba por encima se tiraba al dibujar. El visor
     * a pantalla completa no depende de esto (`loadFull` se baja la suya aparte).
     */
    private val MAX_DIM =
        ImagenEnTexto.anchoMaximo(android.content.res.Resources.getSystem().displayMetrics.widthPixels)
            .coerceAtLeast(640)

    /**
     * Tamaño ORIGINAL (el declarado por el fichero), por URL. Es lo que manda para medir:
     * `medida()` trata un píxel de imagen como un CSS px, así que si se midiera con el bitmap
     * ya reducido, los smileys volverían a salir diminutos y las fotos a media columna.
     * Ver [ImagenEnTexto] y el gotcha 24.
     */
    private val medidas = Collections.synchronizedMap(HashMap<String, Pair<Int, Int>>())

    // Caché dimensionada al heap real (¼ de la memoria máxima), NO un fijo de 24 MB: un post
    // con varias fotos suma decenas de MB; con la caché pequeña se expulsaban unas para meter
    // otras y el hueco disparaba un re-render que las recargaba → parpadeo (aparecer/desaparecer).
    private val cache = object : LruCache<String, Bitmap>(
        (Runtime.getRuntime().maxMemory() / 4).toInt()
    ) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }
    /**
     * Bytes crudos de los GIF, aparte de la caché de bitmaps.
     *
     * Un GIF necesita el fichero ENTERO para animarse, no un bitmap; y el bitmap del primer
     * fotograma se sigue guardando igual porque de él salen el ancho y el alto con los que se
     * mide la imagen y las decisiones de "esto es un icono" / "esto se puede ampliar".
     *
     * **El tamaño sale del heap real, y no es un capricho.** Empezó siendo un fijo de 12 MB
     * "que dan de sobra, los GIF del foro no llegan a 2 MB", y el PRIMER gif con el que se
     * probó en el dispositivo pesaba **16,8 MB**: `LruCache` lo metía y lo expulsaba en el
     * mismo `put` (una entrada más grande que la caché no cabe, y se va sin avisar), así que
     * al ir a pintarlo no había bytes y el gif se quedaba quieto — el fallo que se estaba
     * arreglando, intacto y sin que fallara nada. Mismo error, y misma cura, que la caché de
     * bitmaps en su día.
     *
     * **Límite conocido**: un gif MÁS GRANDE que esta caché se sigue quedando en estático. No
     * se puede hacer mejor sin bajar los ficheros a disco, y por encima de estos tamaños
     * tampoco interesa: son decenas de MB por una imagen de un mensaje.
     */
    private val gifs = object : LruCache<String, ByteArray>(
        (Runtime.getRuntime().maxMemory() / 8).toInt().coerceAtLeast(24 * 1024 * 1024)
    ) {
        override fun sizeOf(key: String, value: ByteArray) = value.size
    }

    /**
     * Imágenes que fallaron **y cuándo**, para poder reintentarlas.
     *
     * Era un `Set` a secas: una imagen que fallara una vez quedaba descartada para siempre
     * (mientras viviera el proceso) y no se volvía a pedir **nunca**. Con FC eso es un fallo
     * real, no teórico: las fotos de dominios que FC no conoce se sirven por **su propio
     * proxy** (`images.forocoches.com?url=…`), que va a buscarlas al origen la primera vez que
     * alguien las mira. Si esa primera petición tarda o se cae —justo al publicar— el mensaje se
     * quedaba **en blanco hasta reiniciar la app**. Descubierto el 2026-09-11 al estrenar la
     * subida de fotos: la misma foto salía vacía recién publicada y perfecta al volver a entrar.
     */
    private val failed = Collections.synchronizedMap(HashMap<String, Long>())

    /** Cuánto se espera antes de volver a intentar una imagen que falló. */
    private const val ESPERA_REINTENTO = 20_000L

    /** Cuántas veces ha fallado cada imagen: la segunda ya no se reintenta sola. */
    private val intentos = Collections.synchronizedMap(HashMap<String, Int>())

    /**
     * Quién está esperando cada descarga en vuelo.
     *
     * Antes era un simple `HashSet` de URLs y el segundo que pedía la misma imagen se iba con
     * un `return`: **su callback se perdía**. Como cada re-render vuelve a pedir las imágenes
     * del mensaje, bastaba con que una vista se reciclase mientras bajaba la foto para que
     * nadie repintara ese post al llegar — se quedaba en blanco hasta que volvías a pasar por
     * encima. Medido el 2026-09-11 en `t=10736632`: **14.000 callbacks tirados**.
     */
    private val esperando = HashMap<String, MutableList<() -> Unit>>()

    /** Tope de avisos por imagen: pasado eso se tira el MÁS VIEJO, que es el más rancio. */
    private const val MAX_ESPERANDO = 64
    private val pool = Executors.newFixedThreadPool(3)
    private val main = Handler(Looper.getMainLooper())

    fun get(url: String): Bitmap? = cache.get(url)

    /** Bytes del GIF de [url], o null si no es un GIF o aún no ha llegado. */
    fun gif(url: String): ByteArray? = gifs.get(url)

    /**
     * De dónde sacar los bytes de una imagen ANTES de ir a la red.
     *
     * Lo pone [MainActivity] al abrir una copia guardada y lo quita al salir: mientras lees un
     * hilo descargado, sus imágenes salen del disco y se ven sin internet — que es el sentido
     * de haberlas bajado. Como esto solo se consulta cuando la caché ya ha fallado, no toca el
     * camino rápido de la lectura normal.
     */
    @Volatile
    var origenLocal: ((String) -> ByteArray?)? = null

    /**
     * Descarga cruda, con la cookie de FC cuando el host es suyo.
     *
     * Público a propósito: es el ÚNICO sitio de la app que sabe pedirle una imagen a FC con la
     * cabecera y la cookie correctas, y el descargador de hilos lo reutiliza en vez de hacerse
     * una copia que se desincronice.
     */
    fun descargar(url: String, maxBytes: Long = Long.MAX_VALUE): ByteArray {
        origenLocal?.invoke(url)?.let { return it }
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.setRequestProperty("User-Agent", UA)
        // La cookie de sesión solo va al host de FC, no a cualquier URL que CONTENGA el texto
        // "forocoches.com" (p. ej. `forocoches.com.evil.example`), que es lo que permitía un
        // `url.contains(...)`: bastaba una imagen en un post para filtrar `bbpassword`.
        if (TrustedOrigins.isTrustedForocochesUrl(url)) {
            CookieManager.getInstance().getCookie("https://forocoches.com")
                ?.let { conn.setRequestProperty("Cookie", it) }
        }
        conn.connectTimeout = 10_000
        conn.readTimeout = 15_000
        // Con tope, se corta en cuanto se sabe que no cabe: por la cabecera si la manda, y si
        // no, leyendo (hay GIF en el foro de 53 MB, ver MainActivity.traerImagen).
        if (maxBytes != Long.MAX_VALUE && conn.contentLengthLong > maxBytes) {
            conn.disconnect()
            throw java.io.IOException("imagen demasiado grande")
        }
        val bytes = conn.inputStream.use { entrada ->
            if (maxBytes == Long.MAX_VALUE) entrada.readBytes()
            else {
                val out = java.io.ByteArrayOutputStream()
                val buf = ByteArray(64 * 1024)
                var total = 0L
                while (true) {
                    val n = entrada.read(buf)
                    if (n < 0) break
                    total += n
                    if (total > maxBytes) throw java.io.IOException("imagen demasiado grande")
                    out.write(buf, 0, n)
                }
                out.toByteArray()
            }
        }
        conn.disconnect()
        return bytes
    }

    /** Decodifica reduciendo al lado máximo pedido, al píxel exacto (ver [escaladoPara]). */
    private fun decodificar(url: String, bytes: ByteArray, ladoMax: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        // El tamaño con el que se MIDE es siempre el original, aunque el bitmap se reduzca.
        if (bounds.outWidth > 0 && bounds.outHeight > 0) {
            medidas[url] = bounds.outWidth to bounds.outHeight
        }
        val esc = escaladoPara(bounds.outWidth, bounds.outHeight, ladoMax)
        val opts = BitmapFactory.Options().apply {
            inSampleSize = esc.muestreo
            if (esc.ajusteFino) {
                inScaled = true
                inDensity = esc.desde
                inTargetDensity = esc.hasta
            }
        }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
    }

    /** Tamaño original de [url] (el del fichero), o null si aún no se ha decodificado. */
    fun tamano(url: String): Pair<Int, Int>? = medidas[url]

    /**
     * Carga para el VISOR a pantalla completa, a más resolución que la lista.
     *
     * NO pasa por la caché LRU **a propósito**: un bitmap de 2560 px son ~20 MB y metería
     * presión suficiente para expulsar todas las miniaturas del hilo — que es exactamente el
     * thrashing que se arregló en su día (parpadeo de las fotos al hacer scroll). El visor se
     * queda con el suyo y lo suelta al cerrarse.
     */
    fun loadFull(url: String, ladoMax: Int, maxBytes: Long = Long.MAX_VALUE, onDone: (Bitmap?) -> Unit) {
        pool.execute {
            val bmp = try { decodificar(url, descargar(url, maxBytes), ladoMax) } catch (_: Throwable) { null }
            main.post { onDone(bmp) }
        }
    }

    fun load(url: String, onDone: () -> Unit) {
        if (cache.get(url) != null) return
        val fallo = failed[url]
        if (fallo != null && System.currentTimeMillis() - fallo < ESPERA_REINTENTO) return
        synchronized(esperando) {
            val cola = esperando[url]
            if (cola != null) {
                cola.add(onDone)
                if (cola.size > MAX_ESPERANDO) cola.removeAt(0)
                return
            }
            esperando[url] = arrayListOf(onDone)
        }
        pool.execute {
            try {
                val bytes = descargar(url)
                // Downsampling: una foto de móvil a resolución completa (varios MP) decodificada
                // entera revienta memoria o supera la LruCache (24 MB) y se expulsa al instante
                // → no se pintaba. Se leen primero las dimensiones y se reduce al decodificar.
                val bmp = decodificar(url, bytes, MAX_DIM)
                if (bmp != null) {
                    // Los GIF se guardan además enteros: sin el fichero no hay animación
                    // posible, y con BitmapFactory solo se saca el primer fotograma (que es
                    // por lo que los testers los veían quietos).
                    // Un gif que no quepa en la caché ni se intenta: `put` lo expulsaría en
                    // el acto y solo serviría para tirar los que sí caben.
                    if (GifAnimado.soportado() && GifAnimado.esGif(bytes) &&
                        bytes.size <= gifs.maxSize()) gifs.put(url, bytes)
                    cache.put(url, bmp)
                    avisar(url)
                } else {
                    anotarFallo(url)
                }
            } catch (e: Throwable) {   // incluye OutOfMemoryError (no es Exception)
                anotarFallo(url)
            }
        }
    }

    /** Llegada buena: se avisa a TODOS los que la estaban esperando. */
    private fun avisar(url: String) {
        val cola = synchronized(esperando) { esperando.remove(url) } ?: return
        main.post { for (aviso in cola) aviso() }
    }

    /**
     * Se fue en fallo: no se avisa a nadie (repintar por una imagen que no está no arregla nada
     * y volvería a pedirla), pero hay que soltar la cola o crecería sin límite.
     */
    /**
     * Una imagen que se fue en fallo tiene **una segunda oportunidad**, y solo una.
     *
     * Marcar el fallo no basta: el repintado de un mensaje solo lo dispara una imagen que
     * LLEGA, así que sin esto la foto se quedaba esperando a que el usuario volviera a pasar
     * por encima. Se avisa a los que esperaban cuando ya ha pasado el tiempo de reintento: al
     * repintar la piden otra vez, y entonces `load` sí las deja pasar.
     *
     * **Solo una vez**: reintentar sin límite una imagen rota sería repintar ese mensaje cada
     * veinte segundos para siempre. A partir de ahí se reintenta cuando la vista se vuelva a
     * enlazar (bajar y subir), que es gratis.
     */
    private fun anotarFallo(url: String) {
        failed[url] = System.currentTimeMillis()
        val veces = (intentos[url] ?: 0) + 1
        intentos[url] = veces
        if (veces == 1) main.postDelayed({ avisar(url) }, ESPERA_REINTENTO + 500)
        else synchronized(esperando) { esperando.remove(url) }
    }
}

class PostAdapter(
    private val onLinkClick: (String) -> Unit,
    private val onQuote: (PostItem) -> Unit = {},
    private val onMultiquoteToggle: (PostItem) -> Unit = {},
    // Única fuente de verdad de la selección: la mantiene MainActivity (replyQuotes).
    private val isSelected: (String) -> Boolean = { false },
    // Menú ⋮ de un post propio (editar / borrar). El View es el ancla del popup.
    private val onMenu: (PostItem, View) -> Unit = { _, _ -> },
    // Tocar el nombre/avatar del autor → su perfil nativo. Solo se invoca con uid válido.
    private val onAuthorClick: (PostItem) -> Unit = {},
    // Pantalla completa de vídeo de un embed: lo gestiona el Activity anfitrión.
    private val onEmbedFullscreen: (View?, android.webkit.WebChromeClient.CustomViewCallback?) -> Unit = { _, _ -> },
    // Tocar una imagen del post → visor a pantalla completa. Recibe la URL original.
    private val onImageClick: (String) -> Unit = {}
) : RecyclerView.Adapter<PostAdapter.Holder>() {

    private val items = ArrayList<PostItem>()
    private val seenPids = HashSet<String>()

    /** Tamaño de letra del cuerpo del post (Opciones → Fuente). Se aplica al renderizar. */
    var postTextSp = 15f
        set(value) { field = value; notifyDataSetChanged() }

    /**
     * Autor del hilo: sus mensajes se pintan recuadrados. "" = desconocido → no se recuadra
     * NADA (ver [ThreadStarter]). Vive en el adaptador y no en los ítems porque el dato solo
     * llega con la página 1 y tiene que valer para las páginas que se carguen después.
     */
    var starterAuthor: String = ""
        set(value) { if (field != value) { field = value; notifyDataSetChanged() } }

    /** Color de la paleta (respeta el modo oscuro; un 0xFF… a pelo NO). */
    private fun col(v: View, id: Int) =
        androidx.core.content.ContextCompat.getColor(v.context, id)

    /** Texto plano de un post para citar: sin HTML y sin citas anidadas previas. */
    fun quoteBodyOf(item: PostItem): String = textoDeHtml(item.html)

    companion object {
        /**
         * HTML de un mensaje → texto plano, sin las citas. Lo usan la cita del composer y las
         * dos tarjetas de compartir. Está aquí, y no copiado en cada sitio, porque olvidar la
         * conversión no rompe nada que se note al compilar: la tarjeta sale con el HTML CRUDO
         * dentro, `<img src="...">` incluido. Visto en el PNG real el 2026-08-21.
         */
        /** Cuánto se espera a juntar imágenes de la misma tanda antes de repintar. */
        const val ESPERA_TANDA = 120L

        fun textoDeHtml(html: String): String {
            val sinCitas = html.replace(Regex("(?is)<blockquote.*?</blockquote>"), " ")
            val texto = HtmlCompat.fromHtml(sinCitas, HtmlCompat.FROM_HTML_MODE_LEGACY).toString()
            // Las imágenes dejan un U+FFFC que el sistema dibuja como una cajita "OBJ".
            // Aquí NO es cosmético: este texto es el que se PUBLICA al citar. Ver [TextoPlano].
            return TextoPlano.sinHuecosDeImagen(texto)
                            .replace(Regex("\\n{3,}"), "\n\n").trim()
        }
    }

    /** Citas que el usuario ha desplegado a mano, por "pid#nºdecita". */
    private val citasDesplegadas = HashSet<String>()

    /** Spoilers que el usuario ha destapado a mano, por "pid#nºdespoiler". */
    private val spoilersDestapados = HashSet<String>()

    fun submit(list: List<PostItem>) {
        items.clear()
        seenPids.clear()
        // Cada página SUSTITUYE a la anterior: lo desplegado era de mensajes que ya no están.
        citasDesplegadas.clear()
        spoilersDestapados.clear()
        // La página cambia entera: el reproductor que se hubiera salvado es de un mensaje que
        // ya no está en la lista y nadie lo va a reclamar.
        soltarEmbedEnUso()
        for (p in list) if (seenPids.add(p.pid)) items.add(p)
        notifyDataSetChanged()
    }

    fun append(list: List<PostItem>) {
        var added = 0
        for (p in list) if (seenPids.add(p.pid)) { items.add(p); added++ }
        if (added > 0) notifyItemRangeInserted(items.size - added, added)
    }

    /**
     * Inserta una página ANTERIOR al principio (scroll hacia arriba tras un salto).
     * Devuelve cuántos entró: quien llama lo necesita para reanclar el scroll y que la
     * pantalla no pegue un salto al meter contenido por encima.
     */
    fun prepend(list: List<PostItem>): Int {
        val nuevos = list.filter { seenPids.add(it.pid) }
        if (nuevos.isEmpty()) return 0
        items.addAll(0, nuevos)
        notifyItemRangeInserted(0, nuevos.size)
        return nuevos.size
    }

    fun clear() = submit(emptyList())

    /** Página del post en la posición dada (para el indicador de página al hacer scroll). */
    fun pageAt(pos: Int): Int = items.getOrNull(pos)?.page ?: 1

    /** Pid del post en la posición dada, o "" (para recordar por dónde ibas leyendo). */
    fun pidAt(pos: Int): String = items.getOrNull(pos)?.pid ?: ""

    /** Posición de un post por su pid, o -1 (para saltar a una cita/mención concreta). */
    fun indexOfPid(pid: String): Int = items.indexOfFirst { it.pid == pid }

    /** Pid del último post cargado, o "" si no hay ninguno. */
    fun lastPid(): String = items.lastOrNull()?.pid ?: ""

    /**
     * Post resaltado temporalmente (el que acabas de publicar). "" = ninguno.
     * Lo apaga quien lo encendió, con clearHighlight().
     */
    var highlightPid: String = ""
        set(value) {
            val old = field
            field = value
            if (old.isNotEmpty()) indexOfPid(old).takeIf { it >= 0 }?.let { notifyItemChanged(it) }
            if (value.isNotEmpty()) indexOfPid(value).takeIf { it >= 0 }?.let { notifyItemChanged(it) }
        }

    fun clearHighlight() { highlightPid = "" }

    override fun getItemCount() = items.size

    class Holder(v: View) : RecyclerView.ViewHolder(v) {
        val avatar: ImageView = v.findViewById(R.id.post_avatar)
        val author: TextView = v.findViewById(R.id.post_author)
        val date: TextView = v.findViewById(R.id.post_date)
        val content: TextView = v.findViewById(R.id.post_content)
        val embeds: android.widget.LinearLayout = v.findViewById(R.id.post_embeds)
        val quote: TextView = v.findViewById(R.id.post_quote)
        val multiquote: TextView = v.findViewById(R.id.post_multiquote)
        val menu: TextView = v.findViewById(R.id.post_menu)
        val divider: View = v.findViewById(R.id.post_divider)
        var boundPid: String = ""

        /** Repintado pendiente por imágenes recién llegadas. Ver [PostAdapter.pedirRepintado]. */
        var repintado: Runnable? = null

        /**
         * Las imágenes que este mensaje ya pintó, aunque la caché las haya echado después.
         *
         * Sin esto, un mensaje con más fotos de las que caben en la caché entraba en bucle: en
         * cada repintado la que faltaba llegaba, al guardarse echaba a la MÁS VIEJA —la primera
         * del mensaje— y ese hueco pedía otro repintado. Medido el 2026-09-24 en `t=10486650`
         * (40 fotos en el primer post): **476 repintados en 15 s**, y el smiley del principio
         * apareciendo y desapareciendo tres veces por segundo (el "intermitente" de Juan).
         * Echarlas de la caché no liberaba nada: el texto en pantalla las sigue teniendo.
         */
        val retenidas = HashMap<String, android.graphics.Bitmap>()
        var retenidasPid = ""

        /** Dónde apoyó el dedo: distingue una pulsación de un arrastre. */
        var dedoX = 0f
        var dedoY = 0f

    }

    /**
     * Dónde va a parar el reproductor que el usuario está usando cuando su mensaje se recicla.
     * Lo pone MainActivity; es la ventanita flotante (ver [MiniReproductor]).
     *
     * Si no hay ninguno, los reproductores se destruyen como siempre: el adaptador no guarda
     * WebViews por su cuenta, para que haya UN solo dueño de lo que sigue vivo.
     */
    var aparcadero: Aparcadero? = null

    /**
     * Libera los WebView de embeds cuando el post sale de pantalla (memoria), **menos el que
     * el usuario está usando**.
     *
     * Aquí estaba el fallo que reportó un tester el 2026-08-22 ("pones un vídeo de YouTube y
     * en cuanto scrolleas y se sale de pantalla, se detiene"): al reciclar el mensaje se
     * llamaba a `release()`, que hace `destroy()` del WebView. No es que se pausara el vídeo:
     * es que se destruía el reproductor entero, así que al volver empezaba de cero.
     */
    override fun onViewRecycled(h: Holder) {
        apartarOLiberar(h)
        h.embeds.removeAllViews()
        // Un GIF fuera de pantalla no tiene que seguir gastando batería ni repintando.
        GifAnimado.parar(h.content)
        h.retenidas.clear()
        h.retenidasPid = ""
    }

    /**
     * El mensaje sale de la ventana: si llevaba el reproductor que el usuario está viendo, se
     * muda YA a la ventanita flotante.
     *
     * **Este es el momento bueno, y no `onViewRecycled`.** Medido en el dispositivo: saltando
     * de golpe con la barra de desplazamiento, el mensaje se desengancha y se queda en la
     * CACHÉ del RecyclerView sin llegar a reciclarse — `onViewRecycled` no salta nunca —, pero
     * Chromium ya ha pausado el vídeo, porque lo que lo pausa es el desenganche de la ventana.
     * Enganchado aquí, da igual por dónde acabe pasando el mensaje.
     *
     * Aquí NO se libera nada más: los otros embeds del mensaje pueden volver tal cual desde la
     * caché, y destruirlos los dejaría en blanco al reaparecer.
     */
    override fun onViewDetachedFromWindow(h: Holder) {
        val donde = aparcadero ?: return
        for (i in 0 until h.embeds.childCount) {
            val ev = h.embeds.getChildAt(i) as? EmbedView ?: continue
            if (ev.enUso) { donde.aparcar(ev); return }
        }
    }

    /**
     * El mensaje vuelve a la ventana: si el que flota es suyo, se le devuelve a su sitio.
     *
     * Se hace en el frame SIGUIENTE a propósito. Esta llamada ocurre **durante el layout** del
     * RecyclerView, y meter o quitar vistas ahí es el fallo que dejó la barra de páginas medida
     * a 0x0 en su día (ver los gotchas de la guía): la petición de layout se pospone y se
     * pierde.
     */
    override fun onViewAttachedToWindow(h: Holder) {
        // Si este mensaje estaba pausado por haber salido del hilo, se le devuelve la vida al
        // volver a la ventana: `onPause()` de un WebView NO se deshace solo al reengancharse,
        // y sin esto el reproductor se quedaba mudo y quieto para siempre.
        for (i in 0 until h.embeds.childCount) (h.embeds.getChildAt(i) as? EmbedView)?.despertar()
        val donde = aparcadero ?: return
        val item = items.getOrNull(h.bindingAdapterPosition) ?: return
        if (!donde.tieneDe(item.pid)) return
        h.itemView.post { if (h.boundPid == item.pid) bindEmbeds(h, item) }
    }

    /**
     * De los embeds de [h]: el que el usuario haya tocado se guarda vivo (desenganchado de la
     * lista), el resto se destruyen como siempre.
     *
     * Solo se salva UNO. Guardar todos los que se hayan tocado en un hilo largo sería ir
     * acumulando WebViews sin techo, que es justo lo que este `release()` venía a evitar.
     */
    private fun apartarOLiberar(h: Holder) {
        // La lista de hijos se copia ANTES de tocar nada: aparcar uno lo saca del contenedor,
        // y recorrer por índice mientras encoge se salta el siguiente — que se quedaría sin
        // liberar, con su WebView vivo y sin dueño.
        val hijos = (0 until h.embeds.childCount).mapNotNull { h.embeds.getChildAt(it) as? EmbedView }
        val donde = aparcadero
        for (ev in hijos) {
            if (ev.enUso && donde != null) donde.aparcar(ev) else ev.release()
        }
    }

    /** Cierra el reproductor flotante, si lo hay. Al cambiar de página o salir del hilo. */
    fun soltarEmbedEnUso() {
        aparcadero?.soltar()
    }

    /**
     * Calla (o despierta) los reproductores de los mensajes que están **en pantalla**.
     *
     * `soltarEmbedEnUso()` solo apaga el que se hubiera quedado aparcado al reciclar su
     * mensaje; el de un mensaje que sigue visible ni se entera de que has salido del hilo,
     * porque el panel solo se OCULTA y ocultar no desengancha nada. Así seguía sonándote un
     * tweet encima del listado (comprobado por el dueño el 2026-09-11, con la 39 en pruebas).
     *
     * Los que están fuera de la ventana no hace falta tocarlos: a esos Chromium ya los pausa
     * solo al desengancharlos (ver [onViewDetachedFromWindow]).
     */
    fun pausarEmbeds(lista: RecyclerView, pausar: Boolean) {
        for (i in 0 until lista.childCount) {
            val h = lista.getChildViewHolder(lista.getChildAt(i)) as? Holder ?: continue
            for (j in 0 until h.embeds.childCount) {
                val ev = h.embeds.getChildAt(j) as? EmbedView ?: continue
                if (pausar) ev.pausar() else ev.despertar()
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_post, parent, false)
        return Holder(v)
    }

    override fun onBindViewHolder(h: Holder, pos: Int) {
        val item = items[pos]
        // Lo que hubiera pendiente de repintar es del mensaje ANTERIOR de esta vista.
        h.repintado?.let { h.content.removeCallbacks(it) }
        h.repintado = null
        if (h.retenidasPid != item.pid) { h.retenidas.clear(); h.retenidasPid = item.pid }
        h.boundPid = item.pid
        paintFrame(h, item)
        h.author.text = if (item.author.isNotEmpty()) "@${item.author}" else "(anónimo)"
        h.date.text = item.date
        // Nombre y avatar abren el perfil ajeno, pero solo si conocemos el uid real
        // (anónimos/baneados quedan sin clic, sin gestos fantasma).
        val hasProfile = item.uid.isNotEmpty()
        val authorClick = if (hasProfile) View.OnClickListener { onAuthorClick(item) } else null
        h.author.setOnClickListener(authorClick)
        h.avatar.setOnClickListener(authorClick)
        h.author.isClickable = hasProfile
        h.avatar.isClickable = hasProfile
        bindAvatar(h, item)
        renderContent(h, item)
        bindEmbeds(h, item)
        h.quote.setOnClickListener { onQuote(item) }
        paintMultiquote(h, isSelected(item.pid))
        h.multiquote.setOnClickListener { onMultiquoteToggle(item) }
        // El ⋮ ahora aparece SIEMPRE: en posts propios da Editar/Borrar; en ajenos, Reportar.
        h.menu.visibility = View.VISIBLE
        h.menu.setOnClickListener { onMenu(item, h.menu) }
        // Pulsación larga = acciones de ESTE mensaje, como en cualquier app de mensajería.
        // Es el mismo menú del ⋮ (ahí vive "Copiar mensaje"), así que no hay pantalla nueva que
        // mantener. Seleccionar texto DENTRO de la lista se probó y se descartó: ver el gotcha.
        h.content.setOnLongClickListener { onMenu(item, h.content); true }
        // Sin esto, el `RecyclerView` y el mensaje compiten por el mismo dedo y **gana la
        // lista**: en cuanto derivas unos píxeles sujetando, te roba el gesto y la pulsación
        // larga nunca llega a dispararse. Es la pieza estándar de Android para que un hijo
        // reclame un gesto, y es lo que faltaba desde el principio.
        h.content.setOnTouchListener { v, ev ->
            when (ev.actionMasked) {
                android.view.MotionEvent.ACTION_DOWN -> {
                    h.dedoX = ev.rawX; h.dedoY = ev.rawY
                    v.parent?.requestDisallowInterceptTouchEvent(true)
                }
                android.view.MotionEvent.ACTION_MOVE -> {
                    // En cuanto arrastra de verdad, el gesto vuelve a ser de la lista: el
                    // scroll manda. El umbral es el MISMO del sistema, así que el hilo no se
                    // siente más pegajoso; lo único que cambia es quién decide.
                    val d = Math.hypot(
                        (ev.rawX - h.dedoX).toDouble(), (ev.rawY - h.dedoY).toDouble()
                    )
                    if (d > android.view.ViewConfiguration.get(v.context).scaledTouchSlop) {
                        v.parent?.requestDisallowInterceptTouchEvent(false)
                    }
                }
                android.view.MotionEvent.ACTION_UP,
                android.view.MotionEvent.ACTION_CANCEL -> {
                    v.parent?.requestDisallowInterceptTouchEvent(false)
                }
            }
            false
        }
    }

    /**
     * Fondo del mensaje: recuadro rojo si es del AUTOR DEL HILO y/o resaltado breve si es el
     * que acabas de publicar. Los cuatro casos se pintan SIEMPRE, sin `if` sueltos: las vistas
     * se reciclan y una rama a medias dejaría el recuadro colgado en el mensaje de otro.
     */
    private fun paintFrame(h: Holder, item: PostItem) {
        val isOp = ThreadStarter.isStarter(item.author, starterAuthor)
        val highlighted = item.pid.isNotEmpty() && item.pid == highlightPid
        if (isOp) {
            // Un solo fondo por vista: con el resaltado encima el recuadro desaparecería, así
            // que el caso combinado tiene su propio drawable (trazo + relleno).
            h.itemView.setBackgroundResource(
                if (highlighted) R.drawable.bg_post_op_highlight else R.drawable.bg_post_op
            )
        } else {
            h.itemView.setBackgroundColor(
                if (highlighted) col(h.itemView, R.color.fc_resaltado) else 0x00000000)
        }
        // El recuadro se despega de los bordes de la pantalla; el resto de mensajes siguen a
        // ancho completo, como siempre.
        (h.itemView.layoutParams as? ViewGroup.MarginLayoutParams)?.let { lp ->
            val side = if (isOp) dp(h.itemView, 8f) else 0
            val vert = if (isOp) dp(h.itemView, 4f) else 0
            if (lp.leftMargin != side || lp.topMargin != vert) {
                lp.leftMargin = side
                lp.rightMargin = side
                lp.topMargin = vert
                lp.bottomMargin = vert
                h.itemView.layoutParams = lp
            }
        }
        h.divider.visibility = if (isOp) View.GONE else View.VISIBLE
    }

    private fun dp(v: View, value: Float): Int =
        (value * v.resources.displayMetrics.density).toInt()

    /** Monta un EmbedView por cada embed del post (tarjeta → toca → reproductor inline). */
    private fun bindEmbeds(h: Holder, item: PostItem) {
        apartarOLiberar(h)
        h.embeds.removeAllViews()
        if (item.embeds.isEmpty()) { h.embeds.visibility = View.GONE; return }
        h.embeds.visibility = View.VISIBLE
        for ((i, spec) in item.embeds.withIndex()) {
            val clave = "${item.pid}#$i"
            // Si este es el reproductor que el usuario dejó en marcha, se devuelve el MISMO
            // (con su vídeo por donde iba) en vez de montar uno nuevo desde cero: vuelve de la
            // ventanita flotante a su sitio en el mensaje.
            val guardado = aparcadero?.recuperar(clave)
            val ev = if (guardado != null) {
                (guardado.parent as? ViewGroup)?.removeView(guardado)
                guardado
            } else {
                EmbedView(h.embeds.context).apply {
                    this.clave = clave
                    onFullscreen = onEmbedFullscreen
                    bind(spec)
                }
            }
            ev.layoutParams = android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
            )
            h.embeds.addView(ev)
        }
    }

    /** Repinta los marcadores de "+"/"✓" tras cambiar la selección desde fuera. */
    fun refreshSelection() = notifyDataSetChanged()

    private fun paintMultiquote(h: Holder, active: Boolean) {
        h.multiquote.text = if (active) "✓" else "＋"
        h.multiquote.setTextColor(col(h.multiquote, if (active) R.color.fc_rojo else R.color.fc_texto_3))
    }

    private fun bindAvatar(h: Holder, item: PostItem) {
        // Quien no tiene avatar lleva el relleno del PROPIO foro (`ic_avatar_fc`), el mismo
        // que ve cualquiera entrando por el navegador. FC lo sirve como `avatar.svg`, que
        // BitmapFactory no sabe decodificar, así que aquí se pinta con el vector nativo.
        val url = item.avatar
        if (url.isEmpty() || url.endsWith(".svg")) {
            h.avatar.setImageResource(R.drawable.ic_avatar_fc)
            return
        }
        // Redondo, como el relleno: dejarlo cuadrado hacía convivir dos formas distintas
        // para lo mismo dentro del mismo hilo.
        val redondo = AvatarRedondo.de(url)
        if (redondo != null) {
            h.avatar.setImageBitmap(redondo)
        } else {
            // También mientras baja la foto: con vistas recicladas, dejar la anterior enseña
            // la cara del mensaje que ocupaba antes este hueco.
            h.avatar.setImageResource(R.drawable.ic_avatar_fc)
            val pid = item.pid
            PostImages.load(url) {
                if (h.boundPid == pid) AvatarRedondo.de(url)?.let { h.avatar.setImageBitmap(it) }
            }
        }
    }

    /**
     * Pliega las citas que se pasan de largas y deja un "Ver cita completa" que las despliega.
     *
     * Por qué plegar y no hacer scroll, y de dónde sale el tope, en [RecorteCita].
     *
     * El tramo de cada cita se saca de los [CitaSpan] que deja `vestir` sobre los `QuoteSpan`
     * que monta `fromHtml` a partir del `<blockquote>`. Se fusionan los contiguos porque una
     * cita de varios párrafos puede traer un span por párrafo, y entonces se plegaría solo el
     * primero.
     */
    private fun plegarCitasLargas(
        sb: SpannableStringBuilder,
        item: PostItem,
        h: Holder,
        tv: TextView
    ) {
        val citas = tramosDeCita(sb)
        // Lo que ocupa cada imagen, en caracteres. Sin esto una cita de cuatro fotos son
        // cuatro caracteres (el U+FFFC del gotcha 21) y no se plegaba NUNCA.
        val alturaLinea = tv.lineHeight.coerceAtLeast(1)
        val costes = HashMap<Int, Int>()
        for (img in sb.getSpans(0, sb.length, ImageSpan::class.java)) {
            val tam = PostImages.tamano(img.source.orEmpty())
            // Un smiley va DENTRO del renglón: no añade altura y cuenta como un carácter.
            if (tam != null && ImagenEnTexto.esIcono(tam.first, tam.second)) continue
            val alto = img.drawable?.bounds?.height() ?: continue
            costes[sb.getSpanStart(img)] = RecorteCita.costeDeImagen(alto, alturaLinea)
        }
        val costeDe = { i: Int -> costes[i] ?: 1 }
        // De atrás hacia delante: recortar una cita mueve los índices de las siguientes.
        for (i in citas.indices.reversed()) {
            val clave = "${item.pid}#$i"
            if (clave in citasDesplegadas) continue
            val ini = citas[i].first
            val fin = RecorteCita.sinSaltosFinales(sb, ini, citas[i].second)
            val corte = RecorteCita.corte(sb, ini, fin, costeDe = costeDe)
            if (corte >= fin) continue
            // replace (y no borrar + insertar): así el adorno de la cita sigue cubriendo el
            // "Ver cita completa", que si no se quedaría fuera del recuadro.
            sb.replace(corte, fin, RecorteCita.MAS)
            val desde = corte + RecorteCita.INICIO_ENLACE
            sb.setSpan(object : ClickableSpan() {
                override fun onClick(widget: View) {
                    citasDesplegadas.add(clave)
                    val pos = h.bindingAdapterPosition
                    if (pos != RecyclerView.NO_POSITION) notifyItemChanged(pos)
                }

                override fun updateDrawState(ds: android.text.TextPaint) {
                    ds.color = ContextCompat.getColor(tv.context, R.color.fc_rojo)
                    ds.isUnderlineText = false
                    ds.isFakeBoldText = true
                }
            }, desde, corte + RecorteCita.MAS.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
    }

    /**
     * Tapa los spoilers: el contenido se sustituye por un aviso tocable que lo destapa.
     *
     * Por qué se tapa así y no como lo hace FC (texto del color del fondo), en [Spoilers].
     * Va DESPUÉS de plegar las citas porque plegar edita el texto, y los tramos que se leen
     * aquí tienen que ser los definitivos.
     */
    private fun taparSpoilers(
        sb: SpannableStringBuilder,
        item: PostItem,
        h: Holder,
        tv: TextView
    ) {
        val marcas = sb.getSpans(0, sb.length, SpoilerSpan::class.java)
            .sortedBy { sb.getSpanStart(it) }
        // De atrás hacia delante: sustituir uno mueve los índices de los siguientes.
        for (i in marcas.indices.reversed()) {
            val ini = sb.getSpanStart(marcas[i])
            val fin = sb.getSpanEnd(marcas[i])
            // Un spoiler que se haya quedado sin contenido (por ejemplo, dentro de la parte
            // recortada de una cita) no tiene nada que tapar.
            if (ini < 0 || fin > sb.length || ini >= fin) continue
            val clave = "${item.pid}#${marcas[i].orden}"
            if (clave in spoilersDestapados) {
                // Destapado: se deja el contenido, pero con fondo, para que se vea dónde
                // empieza y dónde acaba lo que estaba tapado.
                sb.setSpan(
                    android.text.style.BackgroundColorSpan(col(tv, R.color.fc_chip)),
                    ini, fin, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                continue
            }
            sb.replace(ini, fin, Spoilers.AVISO)
            sb.setSpan(object : ClickableSpan() {
                override fun onClick(widget: View) {
                    spoilersDestapados.add(clave)
                    val pos = h.bindingAdapterPosition
                    if (pos != RecyclerView.NO_POSITION) notifyItemChanged(pos)
                }

                override fun updateDrawState(ds: android.text.TextPaint) {
                    ds.color = ContextCompat.getColor(tv.context, R.color.fc_rojo)
                    ds.isUnderlineText = false
                    ds.isFakeBoldText = true
                }
            }, ini, ini + Spoilers.AVISO.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            sb.setSpan(
                android.text.style.BackgroundColorSpan(col(tv, R.color.fc_chip)),
                ini, ini + Spoilers.AVISO.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }
    }

    /** Tramos `[inicio, fin)` de cada cita, fusionando los [CitaSpan] contiguos. */
    private fun tramosDeCita(sb: SpannableStringBuilder): List<Pair<Int, Int>> =
        RecorteCita.tramos(
            sb.getSpans(0, sb.length, CitaSpan::class.java)
                .map { sb.getSpanStart(it) to sb.getSpanEnd(it) }
        )

    /**
     * Quita los [CitaSpan] que se hayan quedado sin texto.
     *
     * Red de seguridad, no adorno: **un span vacío sigue siendo un `LeadingMarginSpan`**, así
     * que sigue reservando su margen y empuja el texto hacia la derecha hasta sacarlo de la
     * pantalla, sin pintar nada que delate por qué. Fue el bug de los mensajes cortados
     * (2026-09-16); la causa de que colapsaran ya está arreglada en [RecorteCita.tramos], pero
     * cualquier `replace` futuro sobre el texto podría volver a dejar uno así.
     */
    private fun limpiarCitasVacias(sb: SpannableStringBuilder) {
        for (c in sb.getSpans(0, sb.length, CitaSpan::class.java)) {
            if (sb.getSpanStart(c) >= sb.getSpanEnd(c)) sb.removeSpan(c)
        }
    }

    /**
     * Un solo repintado por tanda de imágenes, en vez de uno por foto.
     *
     * `renderContent` no es barato: reparsea el HTML del mensaje y reconstruye TODOS los spans
     * (citas, spoilers, enlaces, iconos). Un mensaje con ocho fotos lo hacía ocho veces, y cada
     * pasada volvía a pedir las ocho imágenes. Medido el 2026-09-11 en `t=10736632`: **1.316
     * repintados** para una página de 30 mensajes. Agrupando por frame, las que llegan juntas
     * —que son casi todas, van en ráfagas de red— se pintan de una vez.
     */
    private fun pedirRepintado(h: Holder, item: PostItem) {
        val pid = item.pid
        if (h.boundPid != pid) return
        // OJO: `View.postDelayed` sobre una vista **suelta de la ventana** no se ejecuta hasta
        // que vuelve a engancharse. Y un holder puede seguir enlazado a su mensaje y estar
        // suelto: es la caché del RecyclerView. Pasó de verdad al reintentar una foto fallida
        // — el aviso llegaba a un holder aparcado y el repintado no ocurría nunca. Cuando la
        // vista no está en pantalla se le pide el repintado a la LISTA, que ya sabe a quién
        // toca, y si el mensaje no está visible se repintará al volver a él.
        if (!h.content.isAttachedToWindow) {
            val pos = indexOfPid(pid)
            if (pos >= 0) notifyItemChanged(pos)
            return
        }
        h.repintado?.let { h.content.removeCallbacks(it) }
        val tarea = Runnable {
            h.repintado = null
            if (h.boundPid == pid) renderContent(h, item)
        }
        h.repintado = tarea
        // Agrupadas por tanda y no por frame: las fotos llegan en ráfagas de red repartidas en
        // segundos, y a frame suelto salían catorce repintados donde bastaba uno.
        h.content.postDelayed(tarea, ESPERA_TANDA)
    }

    /** Renderiza el HTML simplificado del extractor. Las imágenes cargan async y
     *  re-renderizan el post cuando llegan (la caché evita bucles de descarga). */
    private fun renderContent(h: Holder, item: PostItem) {
        val tv = h.content
        // Lo que hubiera animándose en esta vista es de OTRO mensaje (o de una pasada anterior
        // de este): si no se para, sigue encolando repintados para siempre.
        GifAnimado.parar(tv)
        tv.textSize = postTextSp
        val maxW = (tv.context.resources.displayMetrics.widthPixels * 0.78f).toInt()
        val pid = item.pid
        val densidad = tv.context.resources.displayMetrics.density
        // La caché primero; si ya la echó, la que este mensaje tenía pintada (ver Holder.retenidas).
        fun bmpDe(src: String): android.graphics.Bitmap? =
            PostImages.get(src)?.also { h.retenidas[src] = it } ?: h.retenidas[src]
        val getter = Html.ImageGetter { src ->
            val bmp = bmpDe(src)
            if (bmp != null) {
                // El bitmap viene ya reducido al ancho de dibujo, así que NO sirve para medir:
                // manda el tamaño del FICHERO (un píxel de imagen es un CSS px, ver abajo).
                val (anchoReal, altoReal) = PostImages.tamano(src) ?: (bmp.width to bmp.height)
                // Se escala por la densidad: un pixel declarado en HTML es un CSS px, que
                // es un dp. Sin esto un smiley de 20x15 px se pintaba a 20 px FISICOS
                // (6,7dp en un movil de densidad 3) y por eso se veian diminutos.
                val (w, hh) = ImagenEnTexto.medida(anchoReal, altoReal, densidad, maxW)
                // Si la imagen es un GIF y este Android sabe animarlo, se pinta el fichero
                // entero en vez del primer fotograma (ver [GifAnimado]).
                val animado = PostImages.gif(src)?.let { GifAnimado.decodificar(it) }
                if (animado != null) {
                    animado.setBounds(0, 0, w, hh)
                    GifAnimado.animar(animado, tv)
                    animado
                } else {
                    BitmapDrawable(tv.context.resources, bmp).apply { setBounds(0, 0, w, hh) }
                }
            } else {
                PostImages.load(src) { pedirRepintado(h, item) }
                ColorDrawable(Color.TRANSPARENT).apply { setBounds(0, 0, 2, 2) }
            }
        }
        // Los embeds ya NO van en el HTML: se extraen como specs y se pintan como
        // reproductores interactivos (bindEmbeds). El HTML aquí es solo texto/imágenes.
        // Se copia a un SpannableStringBuilder porque las citas largas se RECORTAN, y para eso
        // hay que poder editar el texto (los índices de los demás spans se ajustan solos).
        val spanned = SpannableStringBuilder(
            HtmlCompat.fromHtml(item.html, HtmlCompat.FROM_HTML_MODE_LEGACY, getter, null)
        )
        // Lo PRIMERO: las marcas de los spoilers. Se cambian por spans, que se ajustan solos
        // cuando las pasadas siguientes editan el texto (unas posiciones sueltas, no).
        Spoilers.marcar(spanned)
        // Después, el adorno de las citas. `fromHtml` pone un QuoteSpan de fábrica, que
        // es una raya AZUL FIJA del framework y en modo oscuro no se ve (ver [CitaSpan]). Va el
        // primero porque el plegado busca los tramos por CitaSpan.
        CitaSpan.vestir(
            spanned, col(tv, R.color.fc_cita_barra), col(tv, R.color.fc_cita_fondo), densidad
        )
        // Después el plegado: el resto de pasadas (enlaces, fotos) trabajan sobre los índices
        // que queden tras recortar.
        plegarCitasLargas(spanned, item, h, tv)
        limpiarCitasVacias(spanned)
        taparSpoilers(spanned, item, h, tv)
        if (spanned is Spannable) {
            for (span in spanned.getSpans(0, spanned.length, URLSpan::class.java)) {
                val start = spanned.getSpanStart(span)
                val end = spanned.getSpanEnd(span)
                val flags = spanned.getSpanFlags(span)
                val url = span.url
                spanned.removeSpan(span)
                spanned.setSpan(object : ClickableSpan() {
                    override fun onClick(widget: View) = onLinkClick(url)
                }, start, end, flags)
            }
        }
        // Los iconos pequenos (smilies, el logo de Vocaroo...) se centran en el renglon.
        // Html.fromHtml los pega a la BASE del texto, asi que cuelgan por debajo de la linea
        // y se ven torcidos. Solo los pequenos: una foto ocupa su propio renglon y ahi la
        // alineacion no cambia nada.
        if (spanned is Spannable) {
            for (img in spanned.getSpans(0, spanned.length, ImageSpan::class.java)) {
                val d = img.drawable ?: continue
                // Se mide el bitmap ORIGINAL, no el dibujo: los limites del drawable ya vienen
                // multiplicados por la densidad y el umbral esta en pixeles del fichero.
                val fuente = img.source.orEmpty()
                val bmp = bmpDe(fuente) ?: continue
                val (anchoReal, altoReal) = PostImages.tamano(fuente) ?: (bmp.width to bmp.height)
                if (!ImagenEnTexto.esIcono(anchoReal, altoReal)) continue
                val ini = spanned.getSpanStart(img)
                val fin = spanned.getSpanEnd(img)
                val flags = spanned.getSpanFlags(img)
                spanned.removeSpan(img)
                spanned.setSpan(IconoCentradoSpan(d, img.source.orEmpty()), ini, fin, flags)
            }
        }
        // Tocar una imagen la abre en el visor. Va DESPUÉS de los enlaces a propósito: cuando la
        // foto viene envuelta en un <a> (imgur y compañía lo hacen siempre), el toque debe abrir
        // la foto y no el enlace — el destino suele ser esa misma imagen, y sacarte de la app
        // para verla es peor que enseñártela aquí.
        if (spanned is Spannable) {
            for (img in spanned.getSpans(0, spanned.length, ImageSpan::class.java)) {
                val src = img.source.orEmpty()
                if (src.isEmpty() || src.contains("smilies")) continue
                // Un smiley o un icono no se amplían: sin bitmap aún, se deja para el re-render
                // que dispara la carga (si no, el primer toque abriría un visor vacío).
                val bmp = bmpDe(src) ?: continue
                val (anchoReal, altoReal) = PostImages.tamano(src) ?: (bmp.width to bmp.height)
                if (anchoReal < 160 && altoReal < 160) continue

                val ini = spanned.getSpanStart(img)
                val fin = spanned.getSpanEnd(img)
                // Se quita el enlace que envuelva EXACTAMENTE la imagen, no uno que abarque
                // también texto alrededor (ahí el enlace sigue mandando, como debe).
                for (c in spanned.getSpans(ini, fin, ClickableSpan::class.java)) {
                    if (spanned.getSpanStart(c) == ini && spanned.getSpanEnd(c) == fin) {
                        spanned.removeSpan(c)
                    }
                }
                spanned.setSpan(object : ClickableSpan() {
                    override fun onClick(widget: View) = onImageClick(src)
                    // Sin subrayado ni tinte azul: se pintaría sobre la propia foto.
                    override fun updateDrawState(ds: android.text.TextPaint) {}
                }, ini, fin, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        }
        tv.text = spanned
        tv.movementMethod = LinkMovementMethod.getInstance()
    }

}
