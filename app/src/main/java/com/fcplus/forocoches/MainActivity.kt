package com.fcplus.forocoches

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.webkit.CookieManager
import android.webkit.WebView
import android.widget.EditText
import android.widget.LinearLayout
import androidx.appcompat.widget.SwitchCompat
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.material.tabs.TabLayout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var swipeRefresh: SwipeRefreshLayout
    private lateinit var repo: IgnoreListRepository
    private lateinit var keywordRepo: KeywordRepository

    /**
     * Creador de cada hilo (el mismo almacén que usa SettingsBridge para el filtro de
     * ignorados). Aquí sirve para recuadrar sus mensajes aunque entres directo a una página
     * interior, donde el HTML ya no dice quién abrió el hilo. Ver [ThreadStarter].
     */
    private val creatorCache by lazy { ThreadCreatorCache(this) }

    // ── Shell nativo (v2) ──
    private lateinit var nativePanel: View
    private lateinit var listRefresh: SwipeRefreshLayout
    private lateinit var threadRefresh: SwipeRefreshLayout
    private lateinit var threadList: RecyclerView
    private lateinit var listLoading: ProgressBar
    private lateinit var listEmpty: TextView
    private lateinit var mas18Cabecera: View
    private lateinit var mas18Estado: TextView
    private lateinit var mas18Chips: LinearLayout
    // Barra inferior PROPIA: BarraAbajo.HUECOS botones abajo y el resto en el panel del avatar.
    private lateinit var bottomNav: View
    private var selectedNavId = R.id.nav_home
    private val navIcons = HashMap<Int, android.widget.ImageView>()
    private val navLabels = HashMap<Int, TextView>()
    private val navBadges = HashMap<Int, TextView>()
    private val navIds = intArrayOf(
        R.id.nav_home, R.id.nav_avisos, R.id.nav_pm, R.id.nav_favs, R.id.nav_mythreads,
        R.id.nav_participated, R.id.nav_mismensajes, R.id.nav_descargas
    )
    /** La vista entera de cada botón, para poder reordenarlos y quitarlos de la barra. */
    private val navItems = HashMap<Int, View>()

    /**
     * Clave estable de cada botón de la barra de abajo, **en el orden por defecto** (el que se
     * fijó el 2026-08-13 para que los tres avisos cayeran donde se ven).
     *
     * La clave es una cadena nuestra y NO el `R.id`: los identificadores de recursos los
     * regenera el compilador en cada build, así que guardarlos en las preferencias haría que
     * la barra de todo el mundo se descolocara sola al actualizar la app.
     */
    private val navClaves = listOf(
        "home" to R.id.nav_home,
        // Citas + Menciones desde el 2026-10-02 (antes "quotes" y "notif"; BarraAbajo migra
        // lo guardado). "profile" ya no existe: Perfil es el avatar de la cabecera.
        "avisos" to R.id.nav_avisos,
        // MPs directos: antes había que pasar por Perfil (lo pidió Zorro).
        "pm" to R.id.nav_pm,
        "favs" to R.id.nav_favs,
        "mythreads" to R.id.nav_mythreads,
        "participated" to R.id.nav_participated,
        "mismensajes" to R.id.nav_mismensajes,
        "descargas" to R.id.nav_descargas
    )

    /** Nombre e icono de cada destino: los usan la barra, el panel y la pantalla de organizar. */
    private val navNombres = mapOf(
        "home" to "Inicio", "avisos" to "Avisos", "pm" to "Privados", "favs" to "Suscripciones",
        "mythreads" to "Mis hilos", "participated" to "Participados",
        "mismensajes" to "Mis mensajes", "descargas" to "Descargados"
    )
    private val navIconos = mapOf(
        "home" to R.drawable.ic_nav_home, "avisos" to R.drawable.ic_nav_bell,
        "pm" to R.drawable.ic_nav_pm, "favs" to R.drawable.ic_nav_star,
        "mythreads" to R.drawable.ic_nav_threads, "participated" to R.drawable.ic_nav_participated,
        "mismensajes" to R.drawable.ic_mis_mensajes, "descargas" to R.drawable.ic_nav_descargas
    )

    /**
     * Inicio no se puede quitar de la barra: es la raíz de toda la navegación de la app y sin
     * él no se vuelve al listado desde ninguna pantalla.
     */
    private val navFijos = BarraAbajo.FIJOS
    private lateinit var adapter: ThreadListAdapter
    private val readThreads by lazy { ReadThreadsRepository(this) }

    /** Preferencias de la app. Antes se pedían con getSharedPreferences en cada uso. */
    private val shellPrefs by lazy { getSharedPreferences(PREFS, MODE_PRIVATE) }

    private lateinit var forumTabs: TabLayout
    private lateinit var navTop: TextView
    /** Subforos reales del índice (fid → nombre), para la elección del Popurrí en Opciones. */
    private var subforosConocidos: List<Pair<Int, String>> = emptyList()
    /** Los mismos, por fid: los usa la miga de las filas del Popurrí. */
    private var nombresDeForo: Map<Int, String> = emptyMap()

    // ── Panel de hilo nativo (Fase 2) ──
    private lateinit var threadPanel: View
    private lateinit var postList: RecyclerView
    private lateinit var threadLoading: ProgressBar
    private lateinit var threadTitle: TextView
    /** ¿El título de la cabecera está desplegado a todas sus líneas? Se reinicia por hilo. */
    private var tituloDesplegado = false
    private lateinit var threadPageInfo: TextView
    private lateinit var threadFav: ImageButton
    /** La miga de la cabecera: el subforo del hilo abierto ("General ›"). */
    private lateinit var threadForum: TextView
    /** Subforo del hilo abierto, si ya se sabe (0 = todavía no). Lo trae cada página. */
    private var threadForumFid = 0
    private var threadForumName = ""
    private lateinit var postAdapter: PostAdapter

    // ── Aviso del autor de la app (fc_config.json) ──
    private lateinit var noticeBar: android.widget.LinearLayout
    private lateinit var noticeText: android.widget.TextView
    private lateinit var noticeClose: android.widget.TextView
    private lateinit var noticeDivider: View

    // ── Barra de páginas (modo paginado, opcional) ──
    private lateinit var pageBar: View
    private lateinit var pageBarRow: android.widget.LinearLayout
    private lateinit var pageBarDivider: View

    // ── Encuesta del hilo ──
    private lateinit var pollBar: View
    private lateinit var pollBarText: TextView
    private lateinit var pollBarDivider: View
    private var currentPoll: Poll? = null
    private var pollSheet: PollSheet? = null

    // ── Pantalla "hilo restringido" (+HD) ──
    private lateinit var restrictedView: View
    private lateinit var restrictedMsg: TextView
    private lateinit var restrictedMeta: TextView
    private lateinit var restrictedLogin: TextView
    private lateinit var restrictedInvite: TextView
    private var restrictedInviteUrl = ""

    // ── Respuesta rápida (barra del fondo del hilo) ──
    private lateinit var quickReplyBar: View
    private lateinit var quickInput: EditText
    // Avatar de la cuenta con la que se va a publicar, pintado por pintarAvatarComposer().
    private lateinit var quickAvatar: android.widget.ImageView
    // Marca que el envío en curso viene de la barra rápida: replyInput es solo un relé de
    // submitReplyPost() en ese caso, así que si falla hay que vaciarlo (no es donde el
    // usuario está mirando) para no dejar el mismo texto duplicado en las dos cajas. Se
    // consume (se apaga) en cuanto llega el resultado, en onReplyResult().
    private var quickSendPending = false

    // ── Panel de respuesta nativo (Fase 3) ──
    private lateinit var replyPanel: View
    private lateinit var replyInput: android.widget.EditText
    private lateinit var replySend: TextView
    private lateinit var replyCancel: TextView
    private lateinit var replyQuotesContainer: android.widget.LinearLayout
    private lateinit var replyHeaderTitle: TextView
    // Cabecera del composer: con qué cuenta se publica. Los pinta pintarCuentaComposer().
    private lateinit var replyHeaderAccount: TextView
    private lateinit var replyAvatar: android.widget.ImageView
    private lateinit var replySubject: android.widget.EditText
    private var isReplyVisible = false
    private var sendingReply = false
    // Modo del panel de escritura: reply | edit | newthread. Editar/crear reusan el panel.
    private var replyMode = "reply"
    private var editingPid = ""
    // Citas de la respuesta en curso (única fuente de verdad, ordenadas). El "+" de cada
    // post y las tarjetas del panel leen de aquí.
    private val replyQuotes = LinkedHashMap<String, PostItem>()

    /**
     * Texto RECORTADO de una cita (pid → texto), cuando el usuario la edita. El cuerpo normal
     * se deriva del post al enviar (`quoteBodyOf`), así que sin guardarlo aparte la edición no
     * sobreviviría ni a cerrar el panel de respuesta.
     */
    private val replyQuoteEdits = HashMap<String, String>()

    // ── Editor BBCode + smilies (Bloque A) ──
    private lateinit var bbcode: BbcodeEditor
    private var smileyCache: List<Smiley>? = null
    // Encuesta del hilo nuevo (ver EncuestaNueva y fcCreateThread).
    private lateinit var pollBox: View
    private lateinit var pollToggle: TextView
    private lateinit var pollForm: View
    private lateinit var pollQuestion: EditText
    private lateinit var pollOptions: android.widget.LinearLayout
    private lateinit var pollAdd: TextView
    private lateinit var pollMultiple: android.widget.CheckBox
    private lateinit var pollPublic: android.widget.CheckBox
    private lateinit var pollDays: EditText
    /** `:roto2:` → el emoticono mientras escribes, en el composer y en la barra rápida. */
    private val smileysEnCajas = ArrayList<SmileysEnCaja>()
    private var smiliesPedidos = false
    private var smileyDialogPending = false

    // ── Secciones nativas (Bloque B) ──
    private lateinit var noticesPanel: View
    private lateinit var noticesHeader: TextView
    private lateinit var noticesMarkAll: TextView
    private lateinit var noticesList: RecyclerView
    private lateinit var noticesLoading: ProgressBar
    private lateinit var noticesEmpty: TextView
    private lateinit var noticeAdapter: NoticeAdapter
    private var isNoticesVisible = false
    private var currentNoticesKind = ""

    private lateinit var profilePanel: View
    private lateinit var profileAvatar: android.widget.ImageView
    private lateinit var profileName: TextView
    private lateinit var profileSub: TextView
    private lateinit var profileFirma: TextView
    private lateinit var profileSobreMi: TextView
    // Fila para llegar a la hoja de cuentas desde Perfil: es el único camino de verdad para
    // añadir una segunda cuenta (la pulsación larga y el avatar del composer están escondidos
    // hasta que ya tienes dos, así que sin esta fila nadie llega nunca a tener la segunda).
    private lateinit var profileCuentas: TextView
    private var isProfileVisible = false
    private var profileLogoutUrl = ""
    /** Uid de la cuenta activa, o "" sin sesión. Es la identidad para separar preferencias. */
    private var uidActivo = ""
    /** Cambio de cuenta en curso: mientras dure, no se puede publicar. */
    private var cambiandoDeCuenta = false
    /**
     * Generación del cambio de cuenta en curso: el puente JS→Kotlin NO correlaciona peticiones
     * (`onQuienSoy` es un callback global, no una respuesta atada a QUIÉN preguntó), así que la
     * correlación la ponemos nosotros. Una respuesta que llega cuando ya se ha lanzado —o
     * revertido— otro cambio se descarta comparando esta generación.
     */
    private var generacionCambio = 0
    /** Uid que se espera que confirme FC ahora mismo, o "" si no hay cambio de cuenta en curso. */
    private var uidEsperado = ""
    /**
     * Tras un login (onLoginResult), el uid que conteste FC es por definición el bueno: aún no
     * lo conocemos, así que onQuienSoy no puede exigir que coincida con la cuenta activa (eso
     * es justo lo que mantiene vivo "Añadir cuenta" con una cuenta distinta a la actual).
     */
    private var aceptarUidNuevo = false
    /**
     * El login se abrió para entrar con OTRA cuenta ("Añadir cuenta", o una sesión caducada).
     * Ahí la cookie de sesión NO sirve de veredicto: ya está puesta antes de empezar
     * (ver [VeredictoLogin]).
     */
    private var loginParaOtraCuenta = false
    /**
     * Las cookies de la cuenta que había antes de intentar entrar con otra.
     *
     * Entrar con otra cuenta exige **borrar la sesión primero** (gotcha 6: son cuatro cookies y
     * el `bbsessionhash` viejo manda sobre las credenciales nuevas). Si el login no sale, hay
     * que reponer éstas o el usuario se queda fuera de la cuenta que ya tenía.
     */
    private var cookiesAntesDeAnadir: Map<String, String> = emptyMap()

    // ── Mensajes privados nativos ──
    private lateinit var pmPanel: View
    private lateinit var pmList: RecyclerView
    private lateinit var pmLoading: ProgressBar
    private lateinit var pmEmpty: TextView
    private lateinit var pmAdapter: PmInboxAdapter
    private lateinit var pmDetailPanel: View
    private lateinit var pmDetailSubject: TextView
    private lateinit var pmDetailSender: TextView
    private lateinit var pmDetailBody: TextView
    private lateinit var pmDetailReply: TextView
    /** Carpeta de MPs a la vista: "0" = Recibidos, "-1" = Enviados (el folderid de FC). */
    private var pmCarpeta = "0"
    private lateinit var pmCarpetaRecibidos: TextView
    private lateinit var pmCarpetaEnviados: TextView
    private lateinit var pmComposePanel: View
    private lateinit var pmComposeTitle: TextView
    private lateinit var pmComposeTo: android.widget.EditText
    private lateinit var pmComposeQuotes: android.widget.LinearLayout
    /** Cita del MP al que se responde, en BBCode tal y como la escribe FC. "" = sin cita. */
    private var pmQuote = ""
    private lateinit var pmComposeSubject: android.widget.EditText
    private lateinit var pmComposeMessage: android.widget.EditText
    private lateinit var pmComposeSend: TextView
    private var isPmVisible = false
    private var isPmDetailVisible = false
    private var isPmComposeVisible = false
    private var currentPmId = ""           // MP abierto en el detalle
    private var currentPmSubject = ""      // asunto del MP abierto (para "Re:" al responder)
    private var pmComposeMode = "new"      // new | reply
    private var sendingPm = false

    // ── Perfil de otro usuario (mención tocada) ──
    private lateinit var memberPanel: View
    private lateinit var memberStats: TextView
    private lateinit var memberFirma: TextView
    private lateinit var memberSobreMi: TextView
    private lateinit var memberAvatar: android.widget.ImageView
    private lateinit var memberName: TextView
    private lateinit var memberConectado: View
    private lateinit var memberConectadoPunto: View
    private lateinit var memberPmBtn: TextView
    private var isMemberVisible = false
    private var currentMemberUid = ""
    /** Avatar conocido de antemano (el del post desde el que se abrió el perfil). */
    private var pendingMemberAvatar = ""

    /**
     * Pila de navegación de la app. Las pantallas que se DESCARTAN (reporte, vídeo a pantalla
     * completa, capa web, composer, login, Opciones) no entran aquí: para esas, atrás sigue
     * siendo "cierra esto". Ver [NavStack].
     */
    private val nav = NavStack()

    /**
     * Aviso de versión nueva. Detrás de la interfaz para que el día que haya que distribuir
     * fuera de Play solo cambie la implementación. Ver [Actualizador].
     */
    private val actualizador: Actualizador by lazy { ActualizadorPlay(this) }

    /**
     * Citas que entraron por el botón **"Citar"** y aún no se han enviado. Se caen al cerrar el
     * composer sin publicar; las del **＋** no. Ver [CitasPendientes].
     */
    private val citasProvisionales = HashSet<String>()

    /**
     * La cita en BBCode que ha escrito FC para cada mensaje citado (pid → `[QUOTE=…]…[/QUOTE]`).
     * Es lo que se ENVÍA: trae las fotos como `[IMG]`, los smileys con su código real y el
     * formato intacto. Ver [CitaBbcode].
     */
    private val citasBbcode = HashMap<String, String>()

    /** Hilo al que pertenecen las citas y el borrador en curso ("" = no hay nada pendiente). */
    private var quotesThreadTid = ""
    private var currentMemberUsername = ""

    private lateinit var nativeHeader: TextView
    private lateinit var fabNewThread: View
    // home | top | popurri | mas18 | favs | mine (qué alimenta la lista)
    private var listSource = "home"
        set(valor) {
            field = valor
            // La cabecera de la sección +18 (estado y chips) solo existe sobre SU lista: atada
            // al setter, ninguna de las rutas que cambian de fuente la deja colgada.
            if (::mas18Cabecera.isInitialized)
                mas18Cabecera.visibility = if (valor == "mas18") View.VISIBLE else View.GONE
        }
    /** Sección +18: lo ya pintado, sin filtrar por chips (para refiltrar sin volver a bajar). */
    private var mas18Hilos: List<HiloMas18> = emptyList()
    private var mas18Total = 1
    /** Generación de la descarga: una respuesta de otra generación (lista ya abandonada) se tira. */
    private var mas18Gen = 0
    /** Páginas pedidas solas para llenar una lista corta por los chips; se pone a 0 con cualquier acción del usuario. */
    private var mas18Auto = 0
    /** Motivo del último fallo de la página 1, para que un chip no lo pise con "No hay hilos". */
    private var mas18Error: String? = null
    private val descargaMas18 by lazy {
        DescargaMas18(getSharedPreferences(DescargaMas18.PREFS, MODE_PRIVATE))
    }
    private fun configMas18(): ConfigMas18? =
        if (OptionsController.mas18Activa(shellPrefs)) ConfigMas18Parser.de(RemoteConfig.cached(this)) else null
    private fun mas18Apagadas(): Set<String> =
        FiltroMas18.apagadas(shellPrefs.getString(FiltroMas18.PREF, "") ?: "")
    private var myThreadsBase = ""         // search.php?searchid=N para paginar Mis hilos
    /** Usuario y modo de la pantalla de actividad ajena que se está viendo. */
    private var actividadUsuario = ""
    private var actividadModo = ""

    // ── Opciones (fuente, avatar, filtros) ──
    private lateinit var optionsPanel: View
    private lateinit var options: OptionsController
    private var isOptionsVisible = false

    // ── Panel de login nativo (Fase 3) ──
    private lateinit var loginPanel: View
    private lateinit var loginUser: android.widget.EditText
    private lateinit var loginPass: android.widget.EditText
    private lateinit var loginError: TextView
    private lateinit var loginSubmit: TextView
    private var isLoginVisible = false
    private var sendingLogin = false
    private var pendingThreadUrl = ""      // hilo que pidió login (+HD invitado): se reabre al entrar
    private var pendingThreadTitle = ""

    private var currentThreadUrl = ""      // URL base del hilo abierto (sin &page=)

    // ── Descarga de un hilo (ver Descargas / AlmacenDescargas) ──────────────
    private val almacenDescargas by lazy { AlmacenDescargas(this) }
    private lateinit var descargaBar: View
    private lateinit var descargaTexto: TextView
    private lateinit var descargaCancelar: TextView
    private lateinit var descargaProgreso: android.widget.ProgressBar
    private var descargaTid = ""
    private var descargaTitulo = ""
    private var descargaPaginasTotales = 1
    private var descargaSiguiente = 1
    private val descargaRecogidas = ArrayList<String>()
    private var descargando = false
    /**
     * Para QUÉ se está recorriendo el hilo: `"descarga"` (guardarlo entero) o `"usuario"`
     * (quedarse solo con los mensajes de alguien). El recorrido es el mismo —pedir página a
     * página por el canal aparte del motor, sin repintar lo que el usuario está leyendo—, así
     * que hay un solo recorredor y dos consumidores.
     */
    private var modoRecorrido = "descarga"
    private var usuarioBuscado = ""
    private val mensajesDelUsuario = ArrayList<org.json.JSONObject>()
    // Copia que se está leyendo ahora mismo, o "" si estamos en el foro de verdad.
    private var copiaAbierta = ""
    private var copiaPaginas: List<String> = emptyList()
    /**
     * Título de una vista DERIVADA del hilo (hoy: "sus mensajes en este hilo"). No es el hilo
     * vivo ni una copia guardada, así que ni pinta insignias ni pagina contra FC.
     */
    private var vistaDerivada = ""
    private lateinit var contextoBar: TextView
    private lateinit var descargadosPanel: View
    private lateinit var descargadosTotal: TextView
    private lateinit var descargadosEmpty: TextView
    private lateinit var descargadosList: RecyclerView
    private lateinit var descargadosAdapter: DescargadosAdapter
    private var isDescargadosVisible = false
    private val poolDescargas = java.util.concurrent.Executors.newFixedThreadPool(3)
    private var currentThreadTid = ""      // t= del hilo abierto (para responder/citar)
    private var threadPage = 1
    private var threadPageCount = 1
    // (página resaltada, nº total de páginas) con la que se pintaron los chips ahora
    // mismo; null si la barra está oculta. Evita reconstruir en cada frame de scroll.
    private var pageBarState: Pair<Int, Int>? = null
    private var replaceOnLoad = false      // salto de página: la respuesta REEMPLAZA la lista
    private var pendingScrollPid = ""      // post al que saltar tras cargar (citas, deep links)
    // Solo lo pone el ATRAS: desplazamiento fino del ancla. Distinto de null = restauracion,
    // asi que ni se resalta el post ni se aterriza en su borde superior.
    private var pendingScrollOffset: Int? = null
    // Tras publicar sin pid en el redirect: saltar al último post de la página que llegue.
    private var pendingScrollToLast = false
    /**
     * Acabas de publicar y se está buscando tu mensaje. Hace falta porque la página que se pide
     * al publicar es la última que se CONOCÍA antes de hacerlo: si tu mensaje abrió una página
     * nueva (era el primero de la 7), la que llega es la 6 y no está. FC sí dice en esa misma
     * respuesta que ya hay 7 páginas, así que se reintenta UNA vez con la nueva última.
     * Lo reportaron Juan (2026-09-21) y Green Floyd (2026-09-23).
     */
    private var buscandoMiMensaje = false

    /**
     * Dónde estabas en Citas/Menciones (o en "sus mensajes"/la búsqueda, que usan el mismo
     * panel) cuando tocaste una fila: (tipo de lista, primera fila visible, su desplazamiento).
     *
     * Volver a esa pantalla la RECARGA —es lo que la mantiene al día— y la recarga la dejaba
     * arriba del todo: si habías bajado diez filas, a bajarlas otra vez (Miguel, 2026-09-21).
     */
    private var posicionNoticias: Triple<String, Int, Int>? = null
    /** Lo pone el atrás: la próxima lista de noticias que llegue vuelve a [posicionNoticias]. */
    private var restaurarNoticias = false

    /**
     * Fecha real de cada cita/mención (pid → ms), guardada para siempre. La lista de FC solo
     * trae la hora; ver [SelloNoticia]. Se pide una a una y solo las que falten.
     */
    private val fechasNoticias: MutableMap<String, Long> by lazy {
        HashMap(SelloNoticia.leerCache(shellPrefs.getString(PREF_FECHAS_NOTICIAS, "") ?: ""))
    }
    private val fechasPorPedir = ArrayDeque<String>()
    private var pidiendoFecha = ""

    /**
     * Aterrizar en el ÚLTIMO mensaje de la página recién cargada.
     *
     * Distinto de [pendingScrollToLast], que es "resalta MI mensaje recién publicado": aquí no
     * se resalta nada ni se compara ningún pid, solo se baja del todo.
     */
    private var pendingIrAlFinal = false
    private var pendingDeepLink = ""       // deep link recibido antes de tener motor listo
    // Hilo abierto por notificación/cita/deep link (no desde la lista, que ya marca leído
    // ella misma con el nº de respuestas real). Se resuelve en onThreadJson, en cuanto se
    // conoce el tid real (el link puede venir en p=, sin t=). Ver markReadUnknownReplies.
    private var pendingMarkReadUnknown = false
    private var searchQuery = ""           // término del buscador (listSource = "search")
    private var searchTitleOnly = true
    private var searchUser = ""            // de quién son los resultados ("" = de todo el foro)
    /** El buscador abierto espera sugerencias de nombres; null si no hay buscador abierto. */
    private var alLlegarSugerencias: ((String) -> Unit)? = null
    private var loadingThreadPage = false
    private var isThreadVisible = false
    private var cameFromThread = false     // para que atrás desde la web vuelva al hilo

    private var menuLinks: MenuLinks? = null
    private var isWebVisible = false
    private var engineReady = false      // el WebView ya cargó una página de FC con extractor
    private var listLoaded = false
    private var currentPage = 1
    private var loadingPage = false      // evita ráfagas de peticiones con scroll rápido
    /** URL de la última lista de subforo pedida; "" si la lista actual es de otro tipo. Ver [PeticionLista]. */
    private var listaEsperada = ""
    /**
     * La lista ya no da más de sí: se pidió otra página y no entró **ni un hilo nuevo**.
     *
     * Hace falta porque **FC no dice cuándo se acaban los resultados de una búsqueda**: pasado
     * el último, en vez de devolver una página vacía **repite la última una y otra vez**
     * (medido el 2026-08-28: de la página 8 a la 12, siempre los mismos 18 hilos y la lista
     * clavada en 168). Sin esta bandera, el scroll infinito se queda pidiéndole páginas a la
     * búsqueda del foro mientras el usuario siga deslizando — que es exactamente lo que la
     * "regla anti-crawler" de ese listener dice que no debe pasar.
     */
    private var listaAgotada = false
    private var currentForumId = 2       // General; se persiste el último elegido
    private var forumsRequested = false
    private var populatingTabs = false   // el alta programática de tabs no debe disparar cargas

    private var touchDownX = 0f
    private var touchDownY = 0f
    private var swipeStartedInList = false   // el swipe de subforo solo cuenta si empieza en la lista

    // Vídeo de embed a pantalla completa (WebChromeClient.onShowCustomView).
    private var fullscreenView: View? = null
    private var fullscreenCallback: android.webkit.WebChromeClient.CustomViewCallback? = null

    companion object {
        /** Lo que se espera a la tarjeta de compartir un hilo antes de mandar solo el enlace. */
        private const val PLAZO_TARJETA = 12_000L
        /** Tope de la foto de una tarjeta: un GIF del foro llegó a pesar 53 MB. */
        private const val MAX_BYTES_FOTO_TARJETA = 8L * 1024 * 1024
        /**
         * Tope al guardar o compartir una foto. Aquí SÍ se quiere el fichero entero (el GIF de
         * 53 MB del foro cabe), solo se evita meter en memoria algo desmesurado.
         */
        private const val MAX_BYTES_GUARDAR_IMAGEN = 64L * 1024 * 1024
        private const val PREFS = "shell_prefs"
        /**
         * Por encima, "sus mensajes" usa el buscador de FC en vez de recorrer el hilo. Estaba en
         * 40 y los hilos "respondo preguntas", que son justo para lo que existe esto, pasan de
         * ahí: el buscador devuelve como mucho 25 extractos y NO pagina (medido 2026-09-27 en
         * `t=7742347`: 66 páginas, 554 mensajes del OP, se veían 25). Recorrer esas 66 páginas
         * costó 14 s y 34 MB con wifi; 150 son unos 80 MB en el peor caso.
         */
        private const val MAX_PAGINAS_SUS_MENSAJES = 150
        /** Fechas reales de citas/menciones (pid → ms). No va por cuenta: un pid es de todos. */
        private const val PREF_FECHAS_NOTICIAS = "fechas_noticias"
        /** Ids de avisos que este usuario ya cerró (ver Avisos.kt). */
        private const val PREF_AVISOS = "avisos_descartados"
        private const val PREF_LAST_FID = "last_fid"
        /** Cuántas citas/menciones decía FC la última vez que se miró el panel (ver Insignias). */
        private const val PREF_VISTAS_QUOTES = "vistas_quotes"
        private const val PREF_VISTAS_MENTIONS = "vistas_mentions"
        /** Ids de las citas/menciones que ya se han mirado (ver NoticiasVistas). */
        /** Últimas búsquedas, para no reescribirlas (ver BusquedasRecientes). */
        private const val PREF_BUSQUEDAS = "busquedas_recientes"
        private const val PREF_SUSCRITOS = "suscripciones_conocidas"

        /** Hilos que el usuario ha mandado callar (ver HilosIgnorados). */
        private const val PREF_HILOS_IGNORADOS = "hilos_ignorados"

        /** Cuántos mensajes ha publicado desde la app, y si ya se le enseñó la valoración. */
        private const val PREF_PUBLICADOS = "mensajes_publicados"
        private const val PREF_VALORACION_INTENTOS = "valoracion_intentos"
        private const val PREF_CITAS_VISTAS = "citas_vistas"
        private const val PREF_MENCIONES_VISTAS = "menciones_vistas"

        /** Separador entre bloques de cita. Aparte para no pelearse con los escapes. */
        private const val SALTO = "\n"

        // Personalización de las dos barras. Se guarda el ORDEN y lo OCULTO, nunca la lista
        // de lo permitido: ver OrdenBarra.
        private const val PREF_TABS_ORDEN = "tabs_orden"
        private const val PREF_TABS_OCULTOS = "tabs_ocultos"
        private const val PREF_NAV_ORDEN = "nav_orden"
        private const val PREF_NAV_OCULTOS = "nav_ocultos"
        private const val PREF_WELCOME_SHOWN = "welcome_shown"

        // Enlaces de FC que abren la app: cuántas veces se ha abierto (para no ofrecerlo el
        // primer día) y si el usuario cerró el aviso. Ver [EnlacesApp].
        private const val PREF_SESIONES = "sesiones"
        private const val PREF_ENLACES_DESCARTADO = "enlaces_aviso_cerrado"

        /** Índice de cuentas. OJO: `sesiones` ya existe y es el contador de aperturas. */
        private const val PREF_CUENTAS = "cuentas"
        /**
         * Uid de la cuenta activa, persistido: los procesos en segundo plano no ven la variable
         * en memoria. `NotificationChecker` y `NotificationService` corren sin `MainActivity`
         * viva y necesitan leer el uid para abrir el fichero de notificaciones de la cuenta
         * correcta; sin esto, las notificaciones acabarían leyendo las de otra cuenta.
         */
        private const val PREF_UID_ACTIVO = "uid_activo"
        /** Marca persistente: ya se copiaron a la cuenta activa los datos "sin dueño" de antes de la 40. */
        private const val PREF_MIGRADAS = "cuentas_migradas"

        /**
         * Cuánto se espera entre dos comprobaciones del diseño del foro. Cada una cuesta una
         * petición, y los sitios desde donde se dispara (contenido vacío, volver a primer
         * plano) pueden repetirse muy seguidos.
         */
        private const val ESPERA_CHEQUEO_SKIN = 30_000L

        /** Marca de la pestaña del Popurrí; ningún subforo real tiene este fid. */
        private const val TAG_POPURRI = -1
        private const val TAG_MAS18 = -2
        private val LISTAS_DE_SUBFORO = setOf("home", "top", "popurri")
    }

    private fun buildListUrl(page: Int): String {
        // Cache-buster _fp: FC sirve subscription.php tras Varnish con copias de hasta
        // ~1 min (x-cache: HIT); sin él, tras marcar/desmarcar un favorito la lista
        // llegaría rancia. El param único fuerza contenido fresco (gotcha 2026-07-22).
        val base = when (listSource) {
            "favs" -> "https://forocoches.com/foro/subscription.php?_fp=${System.currentTimeMillis()}"
            else -> "https://forocoches.com/foro/forumdisplay.php?f=$currentForumId"
        }
        if (page <= 1) return base
        return base + (if (base.contains('?')) "&" else "?") + "page=$page"
    }

    /**
     * Sesión = cookie bbuserid de vBulletin (la pone el login "recuérdame"; los invitados
     * nunca la tienen). OJO: el HTML del menú de FC NO sirve de señal — el esqueleto con
     * private.php/member.php se sirve IDÉNTICO a invitados y logueados (verificado por CDP).
     * CookieManager sí ve las cookies HttpOnly, a diferencia del document.cookie de JS.
     */
    private fun isLoggedIn(): Boolean {
        val c = CookieManager.getInstance().getCookie("https://forocoches.com") ?: return false
        return c.contains("bbuserid=")
    }

    /** La cuenta de la cabecera: tu cara con sesión, el muñeco ("Entrar") sin ella. */
    private fun updateAccountNavItem() {
        pintarAvatarCuenta()
    }

    /**
     * El avatar de arriba a la derecha (y el del panel) enseña **tu cara** en vez del muñeco
     * genérico. Sin sesión se queda el muñeco y tocarlo abre el login.
     */
    private fun pintarAvatarCuenta() {
        if (!::nativeAvatar.isInitialized) return
        val conSesion = isLoggedIn()
        val url = if (conSesion) cuentasGuardadas().firstOrNull { it.uid == uidActivo }?.avatar.orEmpty() else ""
        val redondo = if (url.isEmpty()) null else AvatarRedondo.de(url)
        for (iv in listOfNotNull(nativeAvatar, if (::cuentaPanel.isInitialized) cuentaPanel.avatar else null)) {
            if (redondo != null) {
                iv.setImageBitmap(redondo)
                iv.clearColorFilter()
            } else if (conSesion) {
                // Con sesión y sin foto: el relleno del propio foro, el mismo que llevan tus
                // mensajes (PostAdapter) y la ficha de quien no tiene avatar.
                iv.setImageResource(R.drawable.ic_avatar_fc)
                iv.clearColorFilter()
            } else {
                iv.setImageResource(R.drawable.ic_nav_person)
                iv.setColorFilter(color(R.color.fc_texto_2))
            }
        }
        if (redondo == null && url.isNotEmpty()) PostImages.load(url) { runOnUiThread { pintarAvatarCuenta() } }
        findViewById<View>(R.id.native_cuenta)?.contentDescription =
            if (conSesion) "Tu cuenta y más opciones" else "Entrar"
    }

    /**
     * Con qué cuenta vas a publicar, en la barra de respuesta rápida: dentro de un hilo no se
     * ve ni la cabecera ni la barra de abajo, así que es el ÚNICO sitio donde queda dicho.
     * **No se pinta mientras hay un cambio en curso** (`cambiandoDeCuenta`): antes de que FC
     * confirme quién eres, prefiere no decir nada a decir algo falso. Y solo aparece con más
     * de una cuenta guardada: con una sola no hay nada que confundir.
     */
    private fun pintarAvatarComposer() {
        if (!::quickAvatar.isInitialized) return
        val cuentas = cuentasGuardadas()
        if (cuentas.size < 2 || cambiandoDeCuenta || uidActivo.isEmpty()) {
            quickAvatar.visibility = View.GONE
            return
        }
        val url = cuentas.firstOrNull { it.uid == uidActivo }?.avatar.orEmpty()
        val redondo = if (url.isEmpty()) null else AvatarRedondo.de(url)
        if (redondo != null) {
            quickAvatar.setImageBitmap(redondo)
        } else {
            // Medido en el móvil de pruebas: la cuenta de pruebas no tiene avatar en FC, así
            // que este es el camino que de verdad se ve. ic_nav_person, NO ic_nav_profile
            // (no existe ese drawable).
            quickAvatar.setImageResource(R.drawable.ic_nav_person)
            if (url.isNotEmpty()) PostImages.load(url) { runOnUiThread { pintarAvatarComposer() } }
        }
        quickAvatar.visibility = View.VISIBLE
    }

    /**
     * Con qué cuenta vas a publicar, en la cabecera de la pantalla de escritura.
     *
     * Lo pidió un tester tras fotografiar el panel "Responder" entero sin una sola señal de la
     * cuenta: el avatar existía, pero **solo en la barra de respuesta rápida**, y con una cita
     * la barra rápida ni se usa (las citas van obligatoriamente al composer completo), así que
     * el caso donde más importa era justo el que no lo decía.
     *
     * Dos reglas:
     * - **Avatar y nombre se encienden y se apagan JUNTOS.** Un avatar suelto no dice un
     *   nombre, y las multis suelen ser cuentas sin foto: dos de ellas se verían idénticas.
     * - **Callar antes que mentir**: quién se enseña lo decide [Cuentas.paraComposer], que
     *   devuelve `null` durante un cambio de cuenta, sin sesión, o si no hay nombre.
     *
     * Sirve para los tres modos (responder, editar, hilo nuevo) porque los tres pasan por
     * [showComposer].
     */
    private fun pintarCuentaComposer() {
        if (!::replyAvatar.isInitialized) return
        val cuenta = Cuentas.paraComposer(cuentasGuardadas(), uidActivo, cambiandoDeCuenta)
        if (cuenta == null) {
            replyAvatar.visibility = View.GONE
            replyHeaderAccount.visibility = View.GONE
            return
        }
        val redondo = if (cuenta.avatar.isEmpty()) null else AvatarRedondo.de(cuenta.avatar)
        if (redondo != null) {
            replyAvatar.setImageBitmap(redondo)
        } else {
            // Mismo camino que la barra rápida: hay cuentas sin avatar en FC (gotcha 46), así
            // que el relleno no es una rareza. ic_nav_person, NO ic_nav_profile (no existe).
            replyAvatar.setImageResource(R.drawable.ic_nav_person)
            if (cuenta.avatar.isNotEmpty()) {
                PostImages.load(cuenta.avatar) { runOnUiThread { pintarCuentaComposer() } }
            }
        }
        replyHeaderAccount.text = Cuentas.etiquetaComposer(cuenta)
        replyAvatar.visibility = View.VISIBLE
        replyHeaderAccount.visibility = View.VISIBLE
    }

    // ── Barra inferior propia ────────────────────────────────────────────────

    private fun configureBottomBar() {
        val claveDe = navClaves.associate { (clave, id) -> id to clave }
        for (id in navIds) {
            val item = findViewById<View>(id)
            navItems[id] = item
            navIcons[id] = item.findViewById(R.id.nav_icon)
            navLabels[id] = item.findViewById(R.id.nav_label)
            navBadges[id] = item.findViewById(R.id.nav_badge)
            val clave = claveDe.getValue(id)
            navIcons[id]?.setImageResource(navIconos.getValue(clave))
            navLabels[id]?.text = navNombres.getValue(clave)
            item.setOnClickListener { onNavClicked(id) }
        }
        pintarBarraInferior()
        setSelectedNav(R.id.nav_home)
    }

    /**
     * Pestañas de la barra inferior: cada una se apila **sobre Inicio** y la pila se rehace en
     * cada salto, así que el atrás lleva a Inicio y **no** va paseando hacia atrás por todas las
     * pestañas que hayas tocado. Ver [pestana] y NavStack.
     *
     * La raíz se fija SOLO en el camino que de verdad cambia de pantalla: si falta sesión se
     * enseña el login y la pila se queda como estaba.
     */
    private fun onNavClicked(id: Int) {
        val reselect = id == selectedNavId
        when (id) {
            R.id.nav_home -> {
                showHomeList(); nav.root(Screen.ThreadList("home", currentForumId))
                if (reselect) requestThreadList(1)
            }
            R.id.nav_descargas -> { showDescargados(); setSelectedNav(R.id.nav_descargas) }
            R.id.nav_favs -> if (isLoggedIn()) { showFavsList(); pestana(Screen.ThreadList("favs")) } else showLogin()
            R.id.nav_mythreads -> if (isLoggedIn()) { showMyThreadsList(); pestana(Screen.ThreadList("mine")) } else showLogin()
            R.id.nav_participated -> if (isLoggedIn()) { showParticipatedList(); pestana(Screen.ThreadList("participated")) } else showLogin()
            // showNotices puede rendirse si el motor aún no tiene los enlaces del menú, así
            // que la pila solo se toca si la pantalla llegó a aparecer.
            R.id.nav_avisos -> if (isLoggedIn()) {
                val kind = avisoQueAbrir()
                showNotices(kind); if (isNoticesVisible) pestana(Screen.Notices(kind))
            } else showLogin()
            R.id.nav_mismensajes -> if (isLoggedIn()) abrirMisMensajes() else showLogin()
            R.id.nav_pm -> if (isLoggedIn()) { showPmInbox(remember = false); pestana(Screen.PmInbox) } else showLogin()
        }
    }

    private fun setSelectedNav(id: Int) {
        selectedNavId = id
        val red = color(R.color.fc_rojo)
        val gray = color(R.color.fc_texto_2)
        for (nid in navIds) {
            val c = if (nid == id) red else gray
            navIcons[nid]?.setColorFilter(c)
            navLabels[nid]?.setTextColor(c)
            // Tarjetas: pastilla detrás del icono activo (en la Compacta el atributo es @null).
            (navIcons[nid]?.parent as? View)?.background =
                if (nid == id) AtributosTema.drawable(this, R.attr.fcNavPastilla) else null
        }
    }

    /**
     * ¿La lista está en un modo que enseña las pestañas de subforo?
     *
     * Existe como función y no como una cadena de `||` sueltos porque ya son tres modos
     * (Inicio, lo más movido y el Popurrí) y **al añadir el tercero se me olvidó uno de los
     * sitios**: en el Popurrí no se podía deslizar para cambiar de subforo y había que tocar
     * las pestañas a mano. Un cuarto modo volvería a olvidarse. El cuarto fue la sección +18:
     * sin ella aquí, la pila no la anotaba y volver de un hilo te sacaba de +18.
     */
    private fun conPestanasDeSubforo(): Boolean =
        listSource == "home" || listSource == "top" || listSource == "popurri" || listSource == "mas18"

    /**
     * ¿Lo que se está mirando es UN subforo concreto?
     *
     * No es lo mismo que [conPestanasDeSubforo]: el Popurrí también tiene pestañas, pero mezcla
     * varios subforos, así que ahí no hay un "aquí" al que mandar un hilo nuevo ni un listado
     * que ordenar por lo más movido. Casi cuelo el botón de crear hilo en el Popurrí, donde
     * habría publicado en el último subforo que hubieras visitado.
     */
    private fun sobreUnSubforo(): Boolean = listSource == "home" || listSource == "top"

    /** Ítem de la barra que corresponde a la fuente actual de la lista nativa. */
    /**
     * Entrar en una pestaña que no es Inicio: se apila **sobre** Inicio.
     *
     * Antes cada pestaña era una raíz (`nav.root`), así que el atrás desde Menciones, Citas o
     * Perfil **se salía de la app** — molestaba al dueño y con razón. El motivo original de
     * hacerlas raíces era evitar que el atrás recorriera hacia atrás todas las pestañas que
     * hubieras tocado, y sigue siendo bueno: por eso la pila se **rehace** cada vez (Inicio +
     * esta), en vez de ir acumulando. Resultado: el atrás siempre lleva a Inicio, y solo se
     * sale de la app desde Inicio. Es lo que hace Android por defecto con una barra inferior.
     */
    private fun pestana(destino: Screen) {
        nav.root(Screen.ThreadList("home", currentForumId))
        nav.push(destino)
    }

    private fun navIdForList(): Int = when (listSource) {
        "favs" -> R.id.nav_favs
        "mine" -> R.id.nav_mythreads
        "participated" -> R.id.nav_participated
        else -> R.id.nav_home   // "search" y "top" incluidos: cuelgan de Inicio
    }

    // ── Secciones nativas (Bloque B) ─────────────────────────────────────────

    /** Inicio: la lista nativa vuelve al modo foro (pestañas de subforos visibles). */
    private fun showHomeList() {
        val wasOther = listSource != "home"
        val veniaDeSeccion = listSource == "mas18" || listSource == "popurri"
        listSource = "home"
        // Que la pestaña marcada sea la del subforo y no la +18/Popurrí de la que se sale.
        if (veniaDeSeccion) pintarPestanas()
        forumTabs.visibility = View.VISIBLE
        nativeHeader.text = "ForoPlus"
        pintarBotonTop()
        showNative()
        fabNewThread.visibility = if (isLoggedIn()) View.VISIBLE else View.GONE
        setSelectedNav(R.id.nav_home)
        if (wasOther) {
            listLoaded = false
            adapter.submit(emptyList())
        }
        if (!listLoaded) requestThreadList(1)
    }

    /**
     * Lo más movido del subforo en el que estés: la MISMA lista, ordenada por FC.
     *
     * Lo pidió un tester el 2026-08-26 porque en el escritorio del foro salen los hilos
     * "trending" en una columna a la derecha y en móvil no están. Se hace reusando la pantalla
     * del listado —con su scroll, su filtro de ignorados y sus palabras clave— en vez de la
     * ventanita de cinco que él proponía: cuesta lo mismo y no deja al usuario en un callejón.
     *
     * Es un interruptor: se sale volviendo a tocarlo, o tocando Inicio.
     */
    private fun alternarTop() = ponerModoTop(listSource != "top")

    private fun ponerModoTop(activo: Boolean) {
        listSource = if (activo) "top" else "home"
        forumTabs.visibility = View.VISIBLE
        nativeHeader.text = if (activo) "Hilos del momento" else "ForoPlus"
        pintarBotonTop()
        setSelectedNav(R.id.nav_home)
        listLoaded = false
        adapter.submit(emptyList())
        anotarListaEnPila()
        requestThreadList(1)
    }

    /**
     * Deja la entrada de la lista en la pila diciendo lo que de verdad se está viendo: qué
     * subforo y en qué modo (normal, TOP o Popurrí).
     *
     * La pila fijaba "home" + el subforo al tocar Inicio y ya no se enteraba de nada más, así
     * que el atrás desde un hilo rehacía Inicio: entrabas desde los hilos del momento y volvías
     * al General (javier hg, 2026-09-26). Solo toca la entrada si la lista ES la pantalla
     * actual: desde un hilo o un perfil no hay nada que anotar.
     */
    private fun anotarListaEnPila() {
        if (nav.current !is Screen.ThreadList || !conPestanasDeSubforo()) return
        nav.replaceTop(Screen.ThreadList(listSource, currentForumId))
    }

    /**
     * Pide el trending del subforo actual al WebView de escritorio (ver [Trending]).
     *
     * Si vuelve vacío —Cloudflare, un cambio de FC, o simplemente que ese subforo no tiene
     * trending— **no se enseña un error**: se apaga el botón TOP y se vuelve al listado
     * normal. Es una función prescindible y tiene que comportarse como tal.
     */
    private fun pedirTrending() {
        val fid = currentForumId
        Trending.cargar(this, fid) { json ->
            if (listSource != "top" || currentForumId != fid) return@cargar
            loadingPage = false
            listLoading.visibility = View.GONE
            listRefresh.isRefreshing = false
            val hilos = if (json.isEmpty()) emptyList() else try {
                parseThreadItems(org.json.JSONObject(json).optJSONArray("threads"))
            } catch (_: Exception) { emptyList() }
            if (hilos.isEmpty()) {
                toast("Ahora mismo no hay hilos del momento en este subforo")
                alternarTop()          // de vuelta al listado normal
                return@cargar
            }
            listLoaded = true
            currentPage = 1
            adapter.submit(filtrarLista(hilos))
            listEmpty.visibility = if (adapter.itemCount == 0) View.VISIBLE else View.GONE
        }
    }

    /** Subforos elegidos para el Popurrí. */
    private fun popurriFids(): List<Int> =
        Popurri.leer(getSharedPreferences(PREFS, MODE_PRIVATE).getString(Popurri.PREF, "") ?: "")

    /**
     * El **Popurrí**: los hilos de los subforos que hayas elegido, en una sola lista ordenada
     * por lo último que se ha escrito. Lo pidió Márquez; el nombre es suyo.
     */
    private fun showPopurri() {
        listSource = "popurri"
        forumTabs.visibility = View.VISIBLE
        nativeHeader.text = "Popurrí"
        pintarBotonTop()
        showNative()
        setSelectedNav(R.id.nav_home)
        listLoaded = false
        adapter.submit(emptyList())
        anotarListaEnPila()
        if (popurriFids().isEmpty()) {
            // Sin subforos elegidos no hay nada que pedir; se dice qué hacer en vez de dejar
            // una lista vacía sin explicación.
            listLoading.visibility = View.GONE
            listEmpty.visibility = View.VISIBLE
            listEmpty.text = "Elige tus subforos en Opciones → Popurrí"
            return
        }
        requestThreadList(1)
    }

    /** Llegan las listas de los subforos del Popurrí, sin mezclar. */
    private fun onPopurriJson(json: String) {
        loadingPage = false
        listLoading.visibility = View.GONE
        listRefresh.isRefreshing = false
        if (listSource != "popurri") return          // el usuario ya se ha ido a otro sitio
        val listas = ArrayList<List<ThreadItem>>()
        try {
            val arr = org.json.JSONObject(json).optJSONArray("listas")
            for (i in 0 until (arr?.length() ?: 0)) {
                val lista = arr!!.optJSONObject(i)
                // Cada lista sabe de qué subforo es; al mezclarlas se perdería, así que se apunta
                // en cada hilo (la miga de la fila).
                listas.add(Popurri.conForo(lista?.optInt("fid") ?: 0, parseThreadItems(lista?.optJSONArray("threads"))))
            }
        } catch (_: Exception) { return }
        listLoaded = true
        currentPage = 1
        // Mezclar y ordenar es política, y por eso vive en Kotlin y con tests (ver Popurri).
        val mezcla = Popurri.mezclar(listas, System.currentTimeMillis())
        adapter.submit(filtrarLista(mezcla))
        listEmpty.visibility = if (adapter.itemCount == 0) View.VISIBLE else View.GONE
        listEmpty.text = skinWarning() ?: "No hay hilos que mostrar"
    }

    /** La sección +18: el archivo que publica el lector, paginado como un subforo. */
    private fun showMas18() {
        listSource = "mas18"
        forumTabs.visibility = View.VISIBLE
        nativeHeader.text = "+18"
        pintarBotonTop()
        showNative()
        setSelectedNav(R.id.nav_home)
        listLoaded = false
        mas18Hilos = emptyList()
        mas18Error = null
        mas18Estado.text = ""
        mas18Gen++
        mas18Auto = 0
        adapter.submit(emptyList())
        pintarChipsMas18()
        anotarListaEnPila()
        requestThreadList(1)
    }

    /** Descarga fuera del hilo de la UI: DescargaMas18 es bloqueante. */
    private fun pedirMas18(page: Int) {
        val cfg = configMas18() ?: run {
            // Interruptor remoto apagado (o config sin bloque): requestThreadList ya había
            // enseñado el spinner, así que se apaga y se vuelve a Inicio en vez de dejar la
            // pantalla muerta con una pestaña que ya no existe.
            loadingPage = false
            listLoading.visibility = View.GONE
            listRefresh.isRefreshing = false
            pintarPestanas()
            showHomeList()
            return
        }
        // La página 1 abre una lista nueva (otra generación); las siguientes llevan la actual.
        if (page <= 1) { mas18Gen++; mas18Auto = 0 }
        val gen = mas18Gen
        Thread {
            val r = descargaMas18.pagina(cfg, page)
            runOnUiThread { onMas18(page, r, gen) }
        }.start()
    }

    private fun onMas18(page: Int, r: ResultadoMas18, gen: Int) {
        // Antes de tocar NINGUNA bandera: una descarga lenta que acaba cuando ya estás en otro
        // subforo no puede apagarle el spinner a ese subforo.
        if (gen != mas18Gen || listSource != "mas18") return
        loadingPage = false
        listLoading.visibility = View.GONE
        listRefresh.isRefreshing = false
        when (val l = r.lectura) {
            is LecturaMas18.Mal -> {
                if (page == 1) {
                    mas18Error = l.motivo
                    mas18Estado.text = ""
                    adapter.submit(emptyList())
                    listEmpty.visibility = View.VISIBLE
                    listEmpty.text = l.motivo
                }
                listaAgotada = true
            }
            is LecturaMas18.Bien -> {
                val p = l.pagina
                mas18Total = p.totalPaginas
                mas18Error = null
                mas18Hilos = if (page == 1) p.hilos else FiltroMas18.unir(mas18Hilos, p.hilos)
                // Quien escribió lo último ya viene en la lista: se apunta para no preguntarlo a FC.
                p.hilos.forEach {
                    if (it.ultimoPid > 0 && it.ultimoAutor.isNotEmpty())
                        ultimosPosteadores.llego(it.ultimoPid.toString(), it.ultimoAutor)
                }
                listLoaded = true
                currentPage = page
                listaAgotada = page >= p.totalPaginas
                pintarEstadoMas18(p, r.deCache)
                repintarMas18()
                completarMas18()
            }
        }
    }

    /**
     * Con chips apagados la lista puede quedarse corta y sin poder desplazarse, y entonces
     * nadie pide la página siguiente. Se piden solas, con tope de 10 por acción del usuario
     * (para no encadenar descargas sin fin) y hasta total_paginas.
     */
    private fun completarMas18() {
        if (listSource != "mas18" || adapter.itemCount >= 8 || listaAgotada || loadingPage) return
        if (mas18Auto >= 10) return
        mas18Auto++
        requestThreadList(currentPage + 1)
    }

    private fun repintarMas18() {
        val ahora = System.currentTimeMillis()
        val apagadas = mas18Apagadas()
        val items = mas18Hilos.filter { FiltroMas18.visible(it, apagadas) }
            .map { PaginaMas18Parser.aThreadItem(it, ahora) }
        adapter.submit(filtrarLista(items))
        listEmpty.visibility = if (adapter.itemCount == 0) View.VISIBLE else View.GONE
        listEmpty.text = mas18Error?.takeIf { adapter.itemCount == 0 } ?: FiltroMas18.textoVacio(apagadas)
    }

    private fun pintarEstadoMas18(p: PaginaMas18, deCache: Boolean) {
        // Sin "generado" (0) no se sabe cuando se hizo: ni "hace 490000 h" ni el aviso de rancia.
        val sabeCuando = p.generado > 0
        val min = if (sabeCuando) ((System.currentTimeMillis() - p.generado) / 60_000).coerceAtLeast(0) else 0
        val hace = when { min < 1 -> "ahora mismo"; min < 60 -> "hace $min min"; else -> "hace ${min / 60} h" }
        val partes = mutableListOf<String>()
        if (sabeCuando) partes += "Actualizado $hace"
        val hasta = PaginaMas18Parser.mesLegible(p.historicoHasta)
        if (hasta.isNotEmpty()) partes += "histórico hasta $hasta"
        var texto = partes.joinToString(" · ")
        if (min >= 60) texto += "\nPuede que la lista no esté al día"
        if (deCache) texto += "\nSin conexión: es la última lista guardada"
        mas18Estado.text = texto.trim()
    }

    private fun pintarChipsMas18() {
        mas18Cabecera.visibility = View.VISIBLE
        mas18Chips.removeAllViews()
        val apagadas = mas18Apagadas()
        EtiquetasHilo.TODAS.forEach { et ->
            val chip = layoutInflater.inflate(R.layout.item_chip_mas18, mas18Chips, false) as TextView
            chip.text = if (et == "peña") "Peñas" else et
            val encendida = et !in apagadas
            chip.isSelected = encendida
            chip.paintFlags = if (encendida) chip.paintFlags and android.graphics.Paint.STRIKE_THRU_TEXT_FLAG.inv()
                else chip.paintFlags or android.graphics.Paint.STRIKE_THRU_TEXT_FLAG
            chip.setOnClickListener {
                val ahora = mas18Apagadas().toMutableSet()
                if (!ahora.remove(et)) ahora.add(et)
                shellPrefs.edit().putString(FiltroMas18.PREF, FiltroMas18.guardar(ahora)).apply()
                pintarChipsMas18()
                repintarMas18()
                mas18Auto = 0
                completarMas18()
            }
            mas18Chips.addView(chip)
        }
    }

    /** El botón TOP encendido (rojo) o apagado. Se pinta SIEMPRE que cambia la fuente. */
    private fun pintarBotonTop() {
        val activo = listSource == "top"
        navTop.setTextColor(color(if (activo) R.color.fc_rojo else R.color.fc_texto_2))
        // Solo tiene sentido sobre un subforo: en Suscripciones o en el buscador no pinta nada.
        navTop.visibility =
            if (sobreUnSubforo()) View.VISIBLE else View.GONE
    }

    /** Favoritos: la MISMA lista nativa, alimentada por subscription.php. */
    private fun showFavsList() {
        listSource = "favs"
        pintarBotonTop()
        forumTabs.visibility = View.GONE
        nativeHeader.text = "Suscripciones"
        listLoaded = false
        adapter.submit(emptyList())
        showNative()
        fabNewThread.visibility = View.GONE
        setSelectedNav(R.id.nav_favs)
        requestThreadList(1)
    }

    /** Mis hilos: los iniciados por el usuario. El motor resuelve el UID real (finduser). */
    private fun showMyThreadsList() {
        listSource = "mine"
        myThreadsBase = ""
        forumTabs.visibility = View.GONE
        nativeHeader.text = "Mis hilos"
        listLoaded = false
        adapter.submit(emptyList())
        showNative()
        fabNewThread.visibility = View.GONE
        setSelectedNav(R.id.nav_mythreads)
        requestThreadList(1)
    }

    /** Buscador: pide el término y enseña los resultados en la MISMA lista nativa. */
    private fun showSearchSheet() {
        if (!isLoggedIn()) { toast("La búsqueda de ForoCoches requiere iniciar sesión"); showLogin(); return }
        val view = layoutInflater.inflate(R.layout.sheet_search, null)
        val sheet = com.google.android.material.bottomsheet.BottomSheetDialog(this)
        sheet.setContentView(view)
        val basePad = view.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(view) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            v.setPadding(v.paddingLeft, v.paddingTop, v.paddingRight, basePad + maxOf(bars.bottom, ime.bottom))
            insets
        }
        val input = view.findViewById<EditText>(R.id.search_input)
        val usuario = view.findViewById<EditText>(R.id.search_user)
        val quitarUsuario = view.findViewById<View>(R.id.search_user_clear)
        val sugerencias = view.findViewById<LinearLayout>(R.id.search_sugerencias)
        val recientes = view.findViewById<LinearLayout>(R.id.search_recientes)
        val ayuda = view.findViewById<TextView>(R.id.search_ayuda)
        val ayudaSinUsuario = ayuda.text
        val scope = view.findViewById<android.widget.RadioGroup>(R.id.search_scope)
        input.setText(searchQuery)
        usuario.setText(searchUser)
        scope.check(if (searchTitleOnly) R.id.search_scope_titles else R.id.search_scope_posts)
        fun lanzar(q: String) {
            val porTitulos = scope.checkedRadioButtonId != R.id.search_scope_posts
            when (val p = BusquedaPorUsuario.decidir(q, usuario.text.toString(), porTitulos)) {
                is PeticionBusqueda.NoVale -> toast(p.motivo)
                // Sin palabras es todo lo suyo: la misma pantalla que su ficha, sin repetirla.
                is PeticionBusqueda.TodoDe -> {
                    sheet.dismiss()
                    showUserActivity(p.usuario, if (p.porTitulos) "started" else "posts")
                }
                is PeticionBusqueda.Palabras -> {
                    searchQuery = p.palabras
                    searchUser = p.usuario
                    searchTitleOnly = p.porTitulos
                    recordarBusqueda(p.palabras)
                    sheet.dismiss()
                    runSearch()
                }
            }
        }
        pintarBusquedasRecientes(recientes) { lanzar(it) }
        val hayRecientes = recientes.visibility == View.VISIBLE

        // Sugerencias de nombres: se piden con una pausa (no en cada tecla) y solo se pinta la
        // respuesta de lo que sigue escrito (ver BusquedaPorUsuario.sugerencias).
        val pausa = android.os.Handler(android.os.Looper.getMainLooper())
        var elegido = ""          // nombre recién tocado: no se vuelve a pedir sugerencias por él
        fun pintarSugerencias(nombres: List<String>) {
            sugerencias.removeAllViews()
            for (nombre in nombres.take(5)) {
                sugerencias.addView(TextView(this).apply {
                    text = "@$nombre"
                    textSize = 15f
                    setTextColor(color(R.color.fc_texto))
                    minHeight = dp(44)
                    gravity = android.view.Gravity.CENTER_VERTICAL
                    setPadding(dp(4), 0, dp(4), 0)
                    setBackgroundResource(android.R.drawable.list_selector_background)
                    setOnClickListener {
                        elegido = nombre
                        usuario.setText(nombre)
                        usuario.setSelection(nombre.length)
                        pintarSugerencias(emptyList())
                    }
                })
            }
            val hay = sugerencias.childCount > 0
            sugerencias.visibility = if (hay) View.VISIBLE else View.GONE
            // Las recientes son un atajo para las PALABRAS: mientras se elige usuario, sobran y
            // dejan sitio (el panel no se desplaza y el teclado está abierto).
            recientes.visibility = if (!hay && hayRecientes) View.VISIBLE else View.GONE
        }
        fun pintarAyuda(texto: String) {
            val u = BusquedaPorUsuario.limpiarUsuario(texto)
            quitarUsuario.visibility = if (u.isEmpty()) View.GONE else View.VISIBLE
            ayuda.text = if (u.isEmpty()) ayudaSinUsuario else
                "Con usuario: por títulos salen los hilos que abrió @$u; por mensajes, sus mensajes. Sin palabras, todo lo suyo."
        }
        alLlegarSugerencias = { json ->
            BusquedaPorUsuario.sugerencias(json, usuario.text.toString())?.let { pintarSugerencias(it) }
        }
        usuario.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                val texto = s?.toString().orEmpty()
                pintarAyuda(texto)
                pausa.removeCallbacksAndMessages(null)
                val limpio = BusquedaPorUsuario.limpiarUsuario(texto)
                if (!BusquedaPorUsuario.pedirSugerencias(texto) || limpio == elegido) {
                    pintarSugerencias(emptyList()); return
                }
                pausa.postDelayed({
                    webView.evaluateJavascript(
                        "window.fcSugerirUsuarios&&fcSugerirUsuarios('${jsEscape(limpio)}')", null
                    )
                }, 350)
            }
        })
        pintarAyuda(searchUser)
        elegido = BusquedaPorUsuario.limpiarUsuario(searchUser)
        quitarUsuario.setOnClickListener { elegido = ""; usuario.setText("") }
        val alBuscarDesdeTeclado = TextView.OnEditorActionListener { _, accion, _ ->
            if (accion == android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH) {
                lanzar(input.text.toString().trim()); true
            } else false
        }
        input.setOnEditorActionListener(alBuscarDesdeTeclado)
        usuario.setOnEditorActionListener(alBuscarDesdeTeclado)
        view.findViewById<View>(R.id.search_go).setOnClickListener {
            lanzar(input.text.toString().trim())
        }
        sheet.setOnDismissListener {
            pausa.removeCallbacksAndMessages(null)
            alLlegarSugerencias = null
        }
        // Abierto entero: con el campo de usuario ya no cabe en la altura a medias del panel.
        sheet.behavior.skipCollapsed = true
        sheet.behavior.state = com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED
        sheet.show()
        input.requestFocus()
    }

    // Por cuenta (ver ClavesPorCuenta): dos personas compartiendo dispositivo no quieren ver
    // las búsquedas de la otra cuenta al abrir el buscador.
    private fun busquedasGuardadas(): List<String> =
        BusquedasRecientes.leer(
            shellPrefs.getString(ClavesPorCuenta.clave(PREF_BUSQUEDAS, uidActivo), "") ?: ""
        )

    private fun recordarBusqueda(q: String) {
        val lista = BusquedasRecientes.recordar(busquedasGuardadas(), q)
        shellPrefs.edit()
            .putString(ClavesPorCuenta.clave(PREF_BUSQUEDAS, uidActivo), BusquedasRecientes.guardar(lista))
            .apply()
    }

    // ── Estrella de suscripción (ver SuscripcionesConocidas) ─────────────────────────────
    // También por cuenta: cada una tiene sus propias suscripciones en FC.

    private fun suscritosGuardados(): List<String> =
        SuscripcionesConocidas.leer(
            shellPrefs.getString(ClavesPorCuenta.clave(PREF_SUSCRITOS, uidActivo), "") ?: ""
        )

    private fun guardarSuscritos(lista: List<String>) {
        shellPrefs.edit()
            .putString(ClavesPorCuenta.clave(PREF_SUSCRITOS, uidActivo), SuscripcionesConocidas.guardar(lista))
            .apply()
    }

    /** Aprende de una página de Suscripciones ya descargada: no cuesta ni una petición. */
    private fun aprenderSuscripciones(tids: List<String>) {
        val antes = suscritosGuardados()
        val despues = SuscripcionesConocidas.anotar(antes, tids)
        if (despues !== antes) guardarSuscritos(despues)
        if (isThreadVisible) pintarEstrella()
    }

    /** Alta o baja de uno solo, con el resultado que el motor ya ha verificado contra FC. */
    private fun anotarSuscripcion(tid: String, suscrito: Boolean) {
        guardarSuscritos(SuscripcionesConocidas.marcar(suscritosGuardados(), tid, suscrito))
    }

    /**
     * Pinta la estrella del hilo abierto con lo que se sabe.
     *
     * Desconocido → hueca, que es lo que se hacía siempre. Lo que se arregla es el caso en que
     * SÍ se sabe: antes también salía hueca y el primer toque te daba de baja creyendo que te
     * dabas de alta (el vídeo de Márquez del 2026-09-16).
     */
    private fun pintarEstrella() {
        pintarEstrella(SuscripcionesConocidas.esta(suscritosGuardados(), currentThreadTid))
    }

    /**
     * El icono Y su etiqueta, siempre juntos.
     *
     * La etiqueta estaba clavada a "Favorito" en el layout, así que un lector de pantalla oía
     * lo mismo estando suscrito y no estándolo — la misma mentira que contaba el icono, pero
     * para quien no puede verlo. Como efecto lateral útil, ahora el estado **se puede
     * verificar con `uiautomator dump`**, que de un icono no sabe decir nada.
     */
    private fun pintarEstrella(suscrito: Boolean) {
        threadFav.setImageResource(if (suscrito) R.drawable.ic_fav_on else R.drawable.ic_fav)
        threadFav.contentDescription =
            if (suscrito) "Quitar de suscripciones" else "Añadir a suscripciones"
    }

    /**
     * Pinta las últimas búsquedas como filas que se tocan para repetirlas.
     *
     * Cada una lleva su ✕: una búsqueda tonta o embarazosa tiene que poder borrarse sin vaciar
     * la lista entera — esto queda guardado en el móvil y lo ve quien mire por encima del hombro.
     */
    private fun pintarBusquedasRecientes(caja: LinearLayout, alTocar: (String) -> Unit) {
        caja.removeAllViews()
        val lista = busquedasGuardadas()
        caja.visibility = if (lista.isEmpty()) View.GONE else View.VISIBLE
        if (lista.isEmpty()) return
        val d = resources.displayMetrics.density
        for (q in lista) {
            val fila = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
            }
            val texto = TextView(this).apply {
                text = q
                textSize = 14f
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
                setTextColor(androidx.core.content.ContextCompat.getColor(context, R.color.fc_texto))
                setPadding(0, (9 * d).toInt(), 0, (9 * d).toInt())
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                setOnClickListener { alTocar(q) }
            }
            val quitar = TextView(this).apply {
                text = "✕"
                textSize = 14f
                setTextColor(androidx.core.content.ContextCompat.getColor(context, R.color.fc_texto_3))
                setPadding((14 * d).toInt(), (9 * d).toInt(), (4 * d).toInt(), (9 * d).toInt())
                setOnClickListener {
                    val quedan = BusquedasRecientes.olvidar(busquedasGuardadas(), q)
                    shellPrefs.edit()
                        .putString(ClavesPorCuenta.clave(PREF_BUSQUEDAS, uidActivo), BusquedasRecientes.guardar(quedan))
                        .apply()
                    pintarBusquedasRecientes(caja, alTocar)
                }
            }
            fila.addView(texto)
            fila.addView(quitar)
            caja.addView(fila)
        }
    }

    /**
     * Lanza la búsqueda y **la apila**: los resultados son una pantalla como cualquier otra,
     * y sin apilarlos el atrás desde un resultado te devolvía al subforo desde el que
     * buscaste, tirando la lista entera.
     */
    private fun runSearch(apilar: Boolean = true) {
        if (apilar) nav.push(
            Screen.ThreadList("search", query = searchQuery, porMensajes = !searchTitleOnly, usuario = searchUser)
        )
        // Buscar en los mensajes devuelve MENSAJES, y esos no caben en el listado de hilos:
        // se pintan en el panel de avisos, que es el que ya sabe dibujar un mensaje suelto.
        if (!searchTitleOnly) { mostrarBusquedaPorMensajes(); return }
        listSource = "search"
        myThreadsBase = ""
        forumTabs.visibility = View.GONE
        nativeHeader.text = BusquedaPorUsuario.cabecera(searchQuery, searchUser, porMensajes = false)
        listLoaded = false
        adapter.submit(emptyList())
        showNative()
        fabNewThread.visibility = View.GONE
        setSelectedNav(R.id.nav_home)
        requestThreadList(1)
    }

    /**
     * Resultados de "buscar en los mensajes": los mensajes, uno a uno y del más reciente al
     * más antiguo, como en la web (lo pidió Green Floyd el 2026-09-18).
     *
     * Reusa el panel de avisos en vez de inventar pantalla: ya pinta exactamente esta fila
     * (título, autor, foro · fecha · extracto) para "ver sus mensajes", y ya sabe que aquí no
     * se resalta nada como "nuevo" ([noticiasNuevas] devuelve vacío para todo lo que no sean
     * citas o menciones).
     *
     * **Una sola página.** FC no dice cuándo se acaba una búsqueda: repite la última para
     * siempre (gotcha 37), así que paginar esto a ciegas es una cinta sin fin. 25 resultados.
     */
    private fun mostrarBusquedaPorMensajes() {
        actividadEnHilo = ""      // No está acotada a ningún hilo: la fila va en su orden normal.
        reiniciarPaginacionMensajes()
        currentNoticesKind = "searchposts"
        pintarPastillas()
        isNoticesVisible = true
        dejarElHilo(); isWebVisible = false; isReplyVisible = false
        isLoginVisible = false; isProfileVisible = false; isOptionsVisible = false
        hidePmPanels()
        // Igual que en showUserActivity: el panel de avisos NO esconde la ficha ajena.
        memberPanel.visibility = View.GONE
        isMemberVisible = false
        noticesPanel.visibility = View.VISIBLE
        nativePanel.visibility = View.GONE
        threadPanel.visibility = View.GONE
        replyPanel.visibility = View.GONE
        loginPanel.visibility = View.GONE
        profilePanel.visibility = View.GONE
        swipeRefresh.visibility = View.INVISIBLE
        bottomNav.visibility = View.VISIBLE
        setSelectedNav(R.id.nav_home)
        noticesHeader.text = BusquedaPorUsuario.cabecera(searchQuery, searchUser, porMensajes = true)
        noticeAdapter.submit(emptyList())
        noticesEmpty.visibility = View.GONE
        noticesLoading.visibility = View.VISIBLE
        webView.evaluateJavascript(
            "window.fcSearch&&fcSearch('${jsEscape(searchQuery)}',false,'','${jsEscape(searchUser)}')", null
        )
    }

    /** Participados: hilos donde el usuario ha posteado (búsqueda por usuario, showposts=0). */
    /**
     * La actividad de OTRO usuario: los hilos que abrió, en los que participa, o sus mensajes.
     * Lo pidieron los testers, y son las tres consultas que el propio FC enlaza desde la ficha.
     *
     * **Exige sesión** (gotcha 3: de invitado la búsqueda devuelve la home sin dar error, así
     * que sin esta guarda saldría una lista vacía y nadie sabría por qué).
     */
    /**
     * @param enHilo tid al que limitar los resultados. Con él, "sus mensajes" pasa a ser
     *   **"sus mensajes EN ESTE HILO"**, que es lo que pedían Juan, Márquez y Miguel para los
     *   hilos tipo "respondo preguntas": leer solo al autor sin ir saltando 20 páginas. Lo
     *   resuelve FC con `searchthreadid`, así que sale completo y paginado, no solo lo que
     *   hubiera cargado en pantalla.
     */
    /** Hilo al que está acotada la lista de "sus mensajes", o "" si son todos. */
    private var actividadEnHilo = ""

    // Páginas siguientes de una lista de MENSAJES ("sus mensajes", "mis mensajes" y el buscador
    // por mensajes). Ver PaginacionMensajes: solo sobre un searchid, y se para cuando FC repite.
    private var mensajesBase: String? = null
    private var mensajesPagina = 1
    private var mensajesCargando = false
    private var mensajesAgotada = false

    private fun reiniciarPaginacionMensajes() {
        mensajesBase = null
        mensajesPagina = 1
        mensajesCargando = false
        mensajesAgotada = false
    }

    /** Pide la página siguiente si se puede. La llama el scroll al acercarse al final. */
    private fun pedirMasMensajes() {
        if (!isNoticesVisible || mensajesCargando || mensajesAgotada) return
        if (currentNoticesKind != "userposts" && currentNoticesKind != "searchposts") return
        val base = mensajesBase ?: return
        val url = PaginacionMensajes.pagina(base, mensajesPagina + 1)
        mensajesCargando = true
        webView.evaluateJavascript(
            if (currentNoticesKind == "userposts") {
                "window.fcLoadUserActivity&&fcLoadUserActivity('posts','${jsEscape(actividadUsuario)}'," +
                    "'${jsEscape(url)}','${jsEscape(actividadEnHilo)}')"
            } else {
                "window.fcSearch&&fcSearch('${jsEscape(searchQuery)}',false,'${jsEscape(url)}','${jsEscape(searchUser)}')"
            }, null
        )
    }

    private fun showUserActivity(
        usuario: String,
        modo: String,
        remember: Boolean = true,
        enHilo: String = ""
    ) {
        if (usuario.isEmpty()) { toast("Todavía no sé quién es este usuario"); return }
        if (!isLoggedIn()) { showLogin(); return }
        if (remember) nav.push(Screen.UserActivity(usuario, modo, enHilo))
        reiniciarPaginacionMensajes()
        actividadUsuario = usuario
        actividadModo = modo
        actividadEnHilo = enHilo
        // showNative() y el panel de avisos NO esconden la ficha ajena (solo lo hace
        // hideAllStandardPanels, que ninguno de los dos llama). Sin esto se queda encima.
        memberPanel.visibility = View.GONE
        isMemberVisible = false
        if (modo == "posts") {
            currentNoticesKind = "userposts"
            pintarPastillas()
            isNoticesVisible = true
            dejarElHilo(); isWebVisible = false; isReplyVisible = false
            isLoginVisible = false; isProfileVisible = false; isOptionsVisible = false
            hidePmPanels()
            noticesPanel.visibility = View.VISIBLE
            nativePanel.visibility = View.GONE
            threadPanel.visibility = View.GONE
            replyPanel.visibility = View.GONE
            loginPanel.visibility = View.GONE
            profilePanel.visibility = View.GONE
            swipeRefresh.visibility = View.INVISIBLE
            bottomNav.visibility = View.VISIBLE
            noticesHeader.text =
                if (enHilo.isEmpty()) "@$usuario · mensajes" else "@$usuario · en este hilo"
            noticeAdapter.submit(emptyList())
            noticesEmpty.visibility = View.GONE
            noticesLoading.visibility = View.VISIBLE
            webView.evaluateJavascript(
                "window.fcLoadUserActivity&&fcLoadUserActivity('posts','${jsEscape(usuario)}','','" +
                    jsEscape(enHilo) + "')", null
            )
            return
        }
        listSource = if (modo == "started") "user-started" else "user-threads"
        // Sin esto el botón TOP se queda con la visibilidad que traía: sale sobre la lista de
        // hilos de un usuario, donde no significa nada (reportado el 2026-08-28).
        pintarBotonTop()
        myThreadsBase = ""
        forumTabs.visibility = View.GONE
        nativeHeader.text = if (modo == "started") "@$usuario · hilos suyos" else "@$usuario · participa"
        listLoaded = false
        adapter.submit(emptyList())
        showNative()
        fabNewThread.visibility = View.GONE
        requestThreadList(1)
    }

    private fun showParticipatedList() {
        listSource = "participated"
        myThreadsBase = ""
        forumTabs.visibility = View.GONE
        nativeHeader.text = "Participados"
        listLoaded = false
        adapter.submit(emptyList())
        showNative()
        fabNewThread.visibility = View.GONE
        setSelectedNav(R.id.nav_participated)
        requestThreadList(1)
    }

    // ── Mensajes privados nativos ─────────────────────────────────────────────

    private fun configurePmPanels() {
        pmPanel = findViewById(R.id.pm_panel)
        pmList = findViewById(R.id.pm_list)
        pmLoading = findViewById(R.id.pm_loading)
        pmEmpty = findViewById(R.id.pm_empty)
        pmAdapter = PmInboxAdapter { pm -> showPmDetail(pm.pmid, pm.subject) }
        pmList.layoutManager = LinearLayoutManager(this)
        pmList.adapter = pmAdapter
        findViewById<View>(R.id.pm_back).setOnClickListener { goBack() }
        findViewById<View>(R.id.pm_new).setOnClickListener { showPmCompose("new", "", "") }
        pmCarpetaRecibidos = findViewById(R.id.pm_carpeta_recibidos)
        pmCarpetaEnviados = findViewById(R.id.pm_carpeta_enviados)
        pmCarpetaRecibidos.setOnClickListener { cambiarCarpetaMp("0") }
        pmCarpetaEnviados.setOnClickListener { cambiarCarpetaMp("-1") }

        pmDetailPanel = findViewById(R.id.pm_detail_panel)
        pmDetailSubject = findViewById(R.id.pm_detail_subject)
        pmDetailSender = findViewById(R.id.pm_detail_sender)
        pmDetailBody = findViewById(R.id.pm_detail_body)
        pmDetailBody.movementMethod = android.text.method.LinkMovementMethod.getInstance()
        pmDetailReply = findViewById(R.id.pm_detail_reply)
        findViewById<View>(R.id.pm_detail_back).setOnClickListener { goBack() }
        pmDetailReply.setOnClickListener { showPmCompose("reply", currentPmId, currentPmSubject) }

        pmComposePanel = findViewById(R.id.pm_compose_panel)
        pmComposeTitle = findViewById(R.id.pm_compose_title)
        pmComposeTo = findViewById(R.id.pm_compose_to)
        pmComposeQuotes = findViewById(R.id.pm_compose_quotes)
        pmComposeSubject = findViewById(R.id.pm_compose_subject)
        pmComposeMessage = findViewById(R.id.pm_compose_message)
        pmComposeSend = findViewById(R.id.pm_compose_send)
        findViewById<View>(R.id.pm_compose_cancel).setOnClickListener { cancelPmCompose() }
        pmComposeSend.setOnClickListener { sendPm() }
    }

    /** Oculta los paneles de MP y el de perfil (al navegar a cualquier destino estándar). */
    private fun hidePmPanels() {
        isPmVisible = false; isPmDetailVisible = false; isPmComposeVisible = false
        isMemberVisible = false
        pmPanel.visibility = View.GONE
        pmDetailPanel.visibility = View.GONE
        pmComposePanel.visibility = View.GONE
        memberPanel.visibility = View.GONE
    }

    /** Oculta todas las capas estándar (para que un panel de MP/perfil quede solo). */
    private fun hideAllStandardPanels() {
        nativePanel.visibility = View.GONE
        threadPanel.visibility = View.GONE
        replyPanel.visibility = View.GONE
        loginPanel.visibility = View.GONE
        noticesPanel.visibility = View.GONE
        descargadosPanel.visibility = View.GONE
        profilePanel.visibility = View.GONE
        optionsPanel.visibility = View.GONE
        memberPanel.visibility = View.GONE
        descargadosPanel.visibility = View.GONE
        isMemberVisible = false
    }

    /** Bandeja de MPs nativa (la regla de oro prohíbe la capa web). */
    private fun showPmInbox(remember: Boolean = true) {
        if (!isLoggedIn()) { toast("Inicia sesión para ver tus mensajes"); showLogin(); return }
        if (remember) nav.push(Screen.PmInbox)
        isPmVisible = true; isPmDetailVisible = false; isPmComposeVisible = false
        dejarElHilo(); isWebVisible = false; isReplyVisible = false
        isLoginVisible = false; isNoticesVisible = false; isProfileVisible = false; isOptionsVisible = false
        hideAllStandardPanels()
        pmPanel.visibility = View.VISIBLE
        pmDetailPanel.visibility = View.GONE
        pmComposePanel.visibility = View.GONE
        bottomNav.visibility = View.VISIBLE
        swipeRefresh.visibility = View.INVISIBLE
        // Si Privados vive en el panel, su botón no está en la barra y no se pinta nada.
        setSelectedNav(R.id.nav_pm)
        cargarCarpetaMp()
    }

    private fun cambiarCarpetaMp(carpeta: String) {
        if (carpeta == pmCarpeta) return
        pmCarpeta = carpeta
        cargarCarpetaMp()
    }

    private fun cargarCarpetaMp() {
        pintarPastilla(pmCarpetaRecibidos, pmCarpeta == "0")
        pintarPastilla(pmCarpetaEnviados, pmCarpeta == "-1")
        pmAdapter.enviados = pmCarpeta == "-1"
        pmAdapter.submit(emptyList())
        pmEmpty.visibility = View.GONE
        pmLoading.visibility = View.VISIBLE
        webView.evaluateJavascript("window.fcLoadPmInbox&&fcLoadPmInbox('$pmCarpeta')", null)
    }

    /** Pastilla de carpeta: rellena de rojo la elegida, gris la otra. */
    private fun pintarPastilla(tv: TextView, elegida: Boolean) {
        tv.background = android.graphics.drawable.GradientDrawable().apply {
            cornerRadius = 99f * resources.displayMetrics.density
            setColor(ContextCompat.getColor(this@MainActivity, if (elegida) R.color.fc_rojo else R.color.fc_chip))
        }
        tv.setTextColor(ContextCompat.getColor(this, if (elegida) R.color.fc_sobre_rojo else R.color.fc_texto_2))
        tv.setTypeface(null, if (elegida) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)
    }

    private fun showPmDetail(pmid: String, subject: String, remember: Boolean = true) {
        if (remember) nav.push(Screen.PmDetail(pmid, subject))
        currentPmId = pmid
        currentPmSubject = subject
        isPmDetailVisible = true; isPmVisible = false; isPmComposeVisible = false
        hideAllStandardPanels()
        pmPanel.visibility = View.GONE
        pmComposePanel.visibility = View.GONE
        pmDetailPanel.visibility = View.VISIBLE
        bottomNav.visibility = View.VISIBLE
        swipeRefresh.visibility = View.INVISIBLE
        pmDetailSubject.text = subject
        pmDetailSender.text = ""
        pmDetailBody.text = "Cargando…"
        pmDetailReply.visibility = View.GONE
        webView.evaluateJavascript("window.fcLoadPm&&fcLoadPm('${jsEscape(pmid)}')", null)
    }

    private fun showPmCompose(mode: String, pmid: String, subject: String) {
        pmComposeMode = mode
        isPmComposeVisible = true; isPmVisible = false; isPmDetailVisible = false
        hideAllStandardPanels()
        pmPanel.visibility = View.GONE
        pmDetailPanel.visibility = View.GONE
        pmComposePanel.visibility = View.VISIBLE
        bottomNav.visibility = View.GONE   // hay inputs de texto: teclado a pantalla limpia
        swipeRefresh.visibility = View.INVISIBLE
        sendingPm = false
        pmComposeSend.isEnabled = true
        pmComposeSend.text = "Enviar"
        pmComposeMessage.setText("")
        pmQuote = ""
        pintarCitaMp()
        if (mode == "reply") {
            pmComposeTitle.text = "Responder"
            pmComposeTo.visibility = View.GONE
            pmComposeSubject.visibility = View.GONE
            // La cita la trae FC hecha en el propio formulario de respuesta (ver CitaPrivada).
            if (pmid.isNotEmpty()) {
                webView.evaluateJavascript(
                    "window.fcLoadPmQuote&&fcLoadPmQuote('${jsEscape(pmid)}')", null
                )
            }
        } else {
            pmComposeTitle.text = "Nuevo mensaje"
            pmComposeTo.visibility = View.VISIBLE
            pmComposeSubject.visibility = View.VISIBLE
            pmComposeTo.setText("")
            pmComposeSubject.setText("")
        }
        pmComposeMessage.requestFocus()
        // Y se abre el teclado, como hace el composer de los hilos. Sin esto la pantalla salía
        // con el campo enfocado pero sin teclado, que es medio camino a ninguna parte.
        //
        // En el frame SIGUIENTE, y no aquí mismo: el panel acaba de pasar a VISIBLE y todavía
        // no está medido ni enganchado, así que `showSoftInput` se pide sobre una vista que el
        // gestor de teclado aún no sirve y no pasa nada (medido: `mInputShown=false`).
        pmComposeMessage.post {
            val imm = getSystemService(INPUT_METHOD_SERVICE)
                as android.view.inputmethod.InputMethodManager
            imm.showSoftInput(
                pmComposeMessage, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT
            )
        }
    }

    /**
     * La tarjeta con el mensaje al que respondes. Es la MISMA que al citar en un hilo, a
     * propósito: es el mismo gesto y no tiene por qué verse distinto.
     *
     * Sin cita no se pinta nada (ni un hueco): responder sin ella es perfectamente válido, y
     * es lo que pasa si la quitas con la ✕ o si FC no la da.
     */
    private fun pintarCitaMp() {
        pmComposeQuotes.removeAllViews()
        if (pmQuote.isEmpty()) return
        val card = layoutInflater.inflate(R.layout.item_reply_quote, pmComposeQuotes, false)
        val autor = CitaPrivada.autor(pmQuote)
        card.findViewById<TextView>(R.id.quote_author).text =
            if (autor.isNotEmpty()) "@$autor" else "(mensaje citado)"
        card.findViewById<TextView>(R.id.quote_preview).text =
            CitaPrivada.previsualizacion(pmQuote)
        card.findViewById<View>(R.id.quote_remove).setOnClickListener {
            pmQuote = ""
            pintarCitaMp()
        }
        pmComposeQuotes.addView(card)
    }

    private fun cancelPmCompose() {
        val imm = getSystemService(INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
        imm.hideSoftInputFromWindow(pmComposePanel.windowToken, 0)
        if (pmComposeMode == "reply" && currentPmId.isNotEmpty()) showPmDetail(currentPmId, currentPmSubject)
        else showPmInbox()
    }

    private fun sendPm() {
        // Con las cookies a medio cambiar, este MP podría salir firmado por la cuenta que se
        // está dejando (o llegar a medias con las de la nueva sin confirmar todavía).
        if (cambiandoDeCuenta) { toast("Espera a que termine el cambio de cuenta"); return }
        if (sendingPm) return
        val message = pmComposeMessage.text.toString().trim()
        if (message.isEmpty()) { toast("Escribe un mensaje"); return }
        val recipients: String
        val title: String
        val pmid: String
        if (pmComposeMode == "reply") {
            recipients = ""; title = ""; pmid = currentPmId
        } else {
            recipients = pmComposeTo.text.toString().trim()
            title = pmComposeSubject.text.toString().trim()
            pmid = ""
            if (recipients.isEmpty()) { toast("Indica el destinatario"); return }
            if (title.isEmpty()) { toast("Escribe un asunto"); return }
        }
        sendingPm = true
        pmComposeSend.isEnabled = false
        pmComposeSend.text = "…"
        // La cita va delante, como en el foro. Si la has quitado, va solo tu texto.
        val cuerpo = CitaPrivada.montar(pmQuote, message)
        webView.evaluateJavascript(
            "window.fcSendPm&&fcSendPm('${jsEscape(recipients)}','${jsEscape(title)}'," +
                "'${jsEscape(cuerpo)}','${jsEscape(pmid)}')", null
        )
    }

    /** Callback del motor para todos los estados de MP (bandeja/detalle/envío). */
    private fun onPmData(json: String) {
        val o = try { org.json.JSONObject(json) } catch (_: Exception) { return }
        when (o.optString("view")) {
            // La cita del MP al que se responde, tal y como la rellena FC (ver CitaPrivada).
            "quote" -> {
                // Puede llegar tarde: si ya no estás en el composer, o has abierto OTRO MP,
                // esta cita no es de lo que tienes delante.
                if (!isPmComposeVisible || pmComposeMode != "reply") return
                if (o.optString("pmid") != currentPmId) return
                pmQuote = o.optString("cita").trim()
                pintarCitaMp()
            }
            "inbox" -> {
                if (!isPmVisible) return
                // Llega tarde la de la carpeta que ya no se mira: no se pinta encima.
                if (o.optString("carpeta", "0") != pmCarpeta) return
                pmLoading.visibility = View.GONE
                val err = o.optString("error", "")
                if (err == "cloudflare") {
                    // La regla de oro admite el challenge de CF como única excepción.
                    showWeb(); webView.loadUrl("https://forocoches.com/foro/private.php"); return
                }
                val items = parsePmInbox(json)
                pmAdapter.submit(items)
                if (items.isEmpty()) {
                    pmEmpty.visibility = View.VISIBLE
                    pmEmpty.text = if (err.isNotEmpty()) "No se pudieron cargar los mensajes"
                        else if (pmCarpeta == "-1") "No tienes mensajes enviados"
                        else "No tienes mensajes privados"
                }
            }
            "detail" -> {
                if (!isPmDetailVisible) return
                if (o.optString("pmid") != currentPmId) return
                val err = o.optString("error", "")
                if (err.isNotEmpty()) { pmDetailBody.text = "No se pudo cargar el mensaje"; return }
                val sender = o.optString("sender").trim()
                pmDetailSender.text = if (sender.isNotEmpty()) "@$sender" else ""
                val subj = o.optString("subject").trim()
                if (subj.isNotEmpty()) { pmDetailSubject.text = subj; currentPmSubject = subj }
                // La conversación anterior viene como citas anidadas: se visten con la misma
                // barra y fondo que en los hilos. Con el QuoteSpan de fábrica (raya azul fija,
                // sin fondo, gotcha 29) no se distinguía qué era cita y qué la respuesta.
                val cuerpo = android.text.SpannableStringBuilder(
                    androidx.core.text.HtmlCompat.fromHtml(
                        o.optString("body"), androidx.core.text.HtmlCompat.FROM_HTML_MODE_LEGACY
                    )
                )
                CitaSpan.vestir(
                    cuerpo,
                    ContextCompat.getColor(this, R.color.fc_cita_barra),
                    ContextCompat.getColor(this, R.color.fc_cita_fondo),
                    resources.displayMetrics.density
                )
                pmDetailBody.text = cuerpo
                pmDetailReply.visibility = if (o.optBoolean("canReply", false)) View.VISIBLE else View.GONE
            }
            "send" -> {
                sendingPm = false
                pmComposeSend.isEnabled = true
                pmComposeSend.text = "Enviar"
                if (o.optBoolean("ok", false)) {
                    val imm = getSystemService(INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
                    imm.hideSoftInputFromWindow(pmComposePanel.windowToken, 0)
                    toast("Mensaje enviado")
                    showPmInbox()
                } else {
                    val err = o.optString("error", "")
                    toast(ErrorPublicar.corto(err, "No se pudo enviar el mensaje"))
                }
            }
        }
    }

    // ── Perfil de otro usuario (mención tocada) ───────────────────────────────

    private fun configureMemberPanel() {
        memberPanel = findViewById(R.id.member_panel)
        memberAvatar = findViewById(R.id.member_avatar)
        memberName = findViewById(R.id.member_name)
        memberConectado = findViewById(R.id.member_conectado)
        memberConectadoPunto = findViewById(R.id.member_conectado_punto)
        memberStats = findViewById(R.id.member_stats)
        memberFirma = findViewById(R.id.member_firma)
        memberSobreMi = findViewById(R.id.member_sobre_mi)
        memberPmBtn = findViewById(R.id.member_pm)
        findViewById<View>(R.id.member_back).setOnClickListener { goBack() }
        memberPmBtn.setOnClickListener {
            if (!isLoggedIn()) { toast("Inicia sesión para enviar mensajes"); showLogin(); return@setOnClickListener }
            if (currentMemberUsername.isEmpty()) { toast("Perfil aún cargando…"); return@setOnClickListener }
            showPmCompose("new", "", "")
            pmComposeTo.setText(currentMemberUsername)
            pmComposeSubject.requestFocus()
        }
        // Ya NO hay "Ver perfil completo (navegador)": la ficha nativa enseña todo lo que trae la
        // de FC (nombre, rango, avatar, hilos, mensajes, registro y actividad), y sacar al usuario
        // al foro va contra la filosofía de la app (decisión del dueño, 2026-09-24).
        // Su actividad. Cada una es una búsqueda de verdad en FC, así que van detrás de un
        // toque y nunca solas al abrir la ficha.
        for ((id, modo) in listOf(
            R.id.member_started to "started",
            R.id.member_threads to "threads",
            R.id.member_posts to "posts"
        )) {
            findViewById<View>(id).setOnClickListener {
                if (currentMemberUsername.isEmpty()) { toast("Perfil aún cargando…"); return@setOnClickListener }
                showUserActivity(currentMemberUsername, modo)
            }
        }
        findViewById<View>(R.id.member_ignore).setOnClickListener {
            if (!isLoggedIn()) { toast("Inicia sesión para gestionar tus ignorados"); showLogin(); return@setOnClickListener }
            if (currentMemberUsername.isEmpty()) { toast("Perfil aún cargando…"); return@setOnClickListener }
            if (yaIgnorado(currentMemberUsername)) escribirIgnorado("remove", currentMemberUsername)
            else confirmarIgnorar(currentMemberUsername)
        }
    }

    private fun yaIgnorado(usuario: String) =
        repo.getIgnoredUsers().any { it.equals(usuario, ignoreCase = true) }

    /**
     * El botón del perfil ofrece lo CONTRARIO de lo que ya pasa. Sin esto, abrir el perfil de
     * alguien a quien ya ignoras enseña "Ignorar", que es una acción sin efecto y hace dudar
     * de si la lista funciona. Se repinta también al confirmar FC (ver onIgnoreListJson).
     */
    private fun pintarBotonIgnorar() {
        val btn = findViewById<TextView>(R.id.member_ignore)
        val hay = currentMemberUsername.isNotEmpty() && isLoggedIn()
        btn.visibility = if (hay) View.VISIBLE else View.GONE
        btn.text = if (hay && yaIgnorado(currentMemberUsername)) "Dejar de ignorar a este usuario"
                   else "Ignorar a este usuario"
    }

    /**
     * Muestra el perfil NATIVO de un usuario (la regla de oro prohíbe la web de FC).
     *
     * @param knownAvatar avatar que YA conocemos de quien abre el perfil desde un post (el
     *   postbit sí trae el del autor). Se usa solo si la página de perfil no trae ninguno
     *   suyo — de invitado, por ejemplo, FC no lo pinta.
     */
    /**
     * @param conectado lo dijo FC en el mensaje desde el que se abre (el punto verde de su avatar).
     *   La ficha de FC no lo enseña, así que entrando por cualquier otro sitio no se sabe y no se
     *   pinta nada.
     */
    private fun showMemberProfile(
        uid: String,
        knownAvatar: String = "",
        remember: Boolean = true,
        conectado: Boolean = false
    ) {
        if (remember) nav.push(Screen.Member(uid))
        currentMemberUid = uid
        currentMemberUsername = ""
        pendingMemberAvatar = knownAvatar
        // Ocultar todo primero; el flag isMemberVisible se pone AL FINAL (los helpers lo resetean).
        hideAllStandardPanels()
        pmPanel.visibility = View.GONE
        pmDetailPanel.visibility = View.GONE
        pmComposePanel.visibility = View.GONE
        isPmVisible = false; isPmDetailVisible = false; isPmComposeVisible = false
        dejarElHilo(); isWebVisible = false; isReplyVisible = false
        isLoginVisible = false; isNoticesVisible = false; isProfileVisible = false; isOptionsVisible = false
        memberStats.text = "Usuario de ForoCoches"   // la del perfil anterior no vale aquí
        memberFirma.visibility = View.GONE
        memberSobreMi.visibility = View.GONE
        memberPmBtn.visibility = View.VISIBLE   // el "no admite MPs" del perfil anterior no vale aquí
        memberPanel.visibility = View.VISIBLE
        isMemberVisible = true
        bottomNav.visibility = View.VISIBLE
        swipeRefresh.visibility = View.INVISIBLE
        memberName.text = "…"
        // Quien no tiene foto lleva el relleno del propio foro, igual que en sus mensajes
        // (PostAdapter). Antes se dejaba en null y la ficha quedaba con un hueco en blanco
        // encima del nombre (visto el 2026-10-03 haciendo las capturas de Play; gotcha 46: el
        // camino "sin foto" no es una rareza). Si llega su avatar, lo sustituye.
        memberAvatar.setImageResource(R.drawable.ic_avatar_fc)
        memberConectado.visibility = if (conectado) View.VISIBLE else View.GONE
        memberConectadoPunto.visibility = if (conectado) View.VISIBLE else View.GONE
        webView.evaluateJavascript("window.fcLoadMember&&fcLoadMember('${jsEscape(uid)}')", null)
    }

    private fun onMemberData(json: String) {
        if (!isMemberVisible) return
        val o = try { org.json.JSONObject(json) } catch (_: Exception) { return }
        if (o.optString("uid") != currentMemberUid) return
        if (o.optString("error").isNotEmpty()) { memberName.text = "No se pudo cargar el perfil"; return }
        val username = o.optString("username").trim()
        currentMemberUsername = username
        memberName.text = if (username.isNotEmpty()) "@$username" else "Perfil"
        // Antigüedad y actividad (mensajes, hilos, desde cuándo y media al día). Si FC no da
        // ninguno de los tres, se deja el texto genérico del layout.
        EstadisticasMiembro.conRango(
            o.optString("rango").trim(),
            EstadisticasMiembro.linea(
                o.optString("mensajes").trim(), o.optString("hilos").trim(),
                o.optString("registro").trim(), System.currentTimeMillis()
            )
        ).takeIf { it.isNotEmpty() }?.let { memberStats.text = it }
        pintarFicha(o, memberFirma, memberSobreMi)
        // FC solo ofrece el MP a quien lo admite (Juan, Telegram 2211: "si tiene la opción
        // activa"). Ante la duda ("" = no se sabe) el botón se queda: escondérselo a quien sí
        // los acepta es peor que dejar que FC diga que no.
        memberPmBtn.visibility = if (o.optString("mpPosible") == "no") View.GONE else View.VISIBLE
        pintarBotonIgnorar()
        // El avatar se elige por PERTENENCIA al uid, nunca por orden: la primera imagen de la
        // página es la de la cabecera (la tuya). Si el perfil no trae ninguna suya, se usa la
        // que ya conocíamos de su post; y si tampoco, ninguna (queda la inicial de color).
        val candidatos = ArrayList<String>()
        o.optJSONArray("avatars")?.let { arr ->
            for (i in 0 until arr.length()) arr.optString(i).trim().takeIf { it.isNotEmpty() }
                ?.let { candidatos.add(it) }
        }
        val avatar = MemberAvatar.pick(candidatos, o.optString("uid"))
            .ifEmpty { pendingMemberAvatar }
        android.util.Log.d(
            "FC_SHELL",
            "member uid=${o.optString("uid")} candidatos=${candidatos.size} avatar=$avatar"
        )
        if (avatar.isNotEmpty() && !avatar.endsWith(".svg")) {
            PostImages.get(avatar)?.let { memberAvatar.setImageBitmap(it) }
                ?: PostImages.load(avatar) {
                    if (isMemberVisible && currentMemberUid == o.optString("uid"))
                        PostImages.get(avatar)?.let { memberAvatar.setImageBitmap(it) }
                }
        }
    }

    /**
     * Firma y "Sobre mí" de una ficha (la tuya o la de otro): lo que FC enseña en member.php y
     * la app no pintaba. Qué se descarta (N/A, el aviso de "sin firma") lo decide
     * [FichaMiembro]; aquí solo se pinta.
     *
     * La firma llega de `extractor.js` reducida a texto + enlaces (sin imágenes, decisión del
     * dueño del 2026-09-27) y sus enlaces van por el mismo router que los de los posts, así que
     * un hilo de FC se abre en nativo y lo demás fuera — nunca la capa web.
     */
    private fun pintarFicha(o: org.json.JSONObject, firma: TextView, sobreMi: TextView) {
        if (FichaMiembro.hayFirma(o.optString("firmaTexto"))) {
            val sp = android.text.SpannableStringBuilder(
                androidx.core.text.HtmlCompat.fromHtml(
                    o.optString("firma"), androidx.core.text.HtmlCompat.FROM_HTML_MODE_COMPACT
                )
            )
            for (span in sp.getSpans(0, sp.length, android.text.style.URLSpan::class.java)) {
                val url = span.url
                val ini = sp.getSpanStart(span); val fin = sp.getSpanEnd(span)
                sp.removeSpan(span)
                sp.setSpan(object : android.text.style.ClickableSpan() {
                    override fun onClick(widget: View) = onPostLinkClick(url)
                }, ini, fin, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            firma.text = sp.trim()
            firma.movementMethod = android.text.method.LinkMovementMethod.getInstance()
            firma.visibility = View.VISIBLE
        } else {
            firma.visibility = View.GONE
        }

        val crudos = ArrayList<Pair<String, String>>()
        o.optJSONArray("sobreMi")?.let { arr ->
            for (i in 0 until arr.length()) arr.optJSONObject(i)?.let {
                crudos.add(it.optString("icono") to it.optString("valor"))
            }
        }
        val campos = FichaMiembro.campos(crudos)
        if (campos.isEmpty()) { sobreMi.visibility = View.GONE; return }
        val gris = androidx.core.content.ContextCompat.getColor(this, R.color.fc_texto_3)
        val sb = android.text.SpannableStringBuilder()
        campos.forEachIndexed { i, c ->
            if (i > 0) sb.append('\n')
            val ini = sb.length
            sb.append(c.etiqueta)
            sb.setSpan(
                android.text.style.ForegroundColorSpan(gris), ini, sb.length,
                android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            sb.append("  ").append(c.valor)
        }
        sobreMi.text = sb
        sobreMi.visibility = View.VISIBLE
    }

    /** Citas o menciones AISLADAS en su propia página (no el batiburrillo de FC). */
    private fun showNotices(kind: String) {
        actividadEnHilo = ""   // Citas/Menciones nunca están acotadas a un hilo.
        reiniciarPaginacionMensajes()   // citas y menciones no paginan
        val url = if (kind == "quotes") menuLinks?.quotes else menuLinks?.mentions
        if (url == null) { toast("Conectando con el foro…"); return }
        currentNoticesKind = kind
        // Mirar el panel es "ya lo he visto": se apunta lo que FC dice EN ESTE MOMENTO, que es
        // lo único que hace que un contador fantasma deje de dar la lata (ver [Insignias]).
        shellPrefs.edit()
            .putInt(
                ClavesPorCuenta.clave(
                    if (kind == "quotes") PREF_VISTAS_QUOTES else PREF_VISTAS_MENTIONS, uidActivo
                ),
                if (kind == "quotes") fcQuotes else fcMentions
            )
            .apply()
        if (kind == "quotes") insigniaCitas = 0 else insigniaMenciones = 0
        pintarInsignias()
        isNoticesVisible = true
        dejarElHilo(); isWebVisible = false; isReplyVisible = false
        isLoginVisible = false; isProfileVisible = false; isOptionsVisible = false
        hidePmPanels()
        noticesPanel.visibility = View.VISIBLE
        nativePanel.visibility = View.GONE
        threadPanel.visibility = View.GONE
        replyPanel.visibility = View.GONE
        loginPanel.visibility = View.GONE
        profilePanel.visibility = View.GONE
        swipeRefresh.visibility = View.INVISIBLE
        bottomNav.visibility = View.VISIBLE
        setSelectedNav(R.id.nav_avisos)
        noticesHeader.text = "Avisos"
        pintarPastillas()
        noticeAdapter.submit(emptyList())
        noticesEmpty.visibility = View.GONE
        noticesLoading.visibility = View.VISIBLE
        webView.evaluateJavascript(
            "window.fcLoadNotices&&fcLoadNotices('${jsEscape(url)}','$kind')", null
        )
    }

    /**
     * Cuáles de estas citas/menciones se pintan como nuevas — y de paso **las da por vistas**.
     *
     * ForoCoches no marca cuáles has leído (sondeado por CDP: la celda del icono viene vacía),
     * así que la cuenta la lleva la app. Ver [NoticiasVistas].
     *
     * La actividad de OTRO usuario usa este mismo panel y ahí "nuevo" no significa nada: no se
     * resalta ni se recuerda.
     */
    /**
     * Dónde se recuerda lo leído de esta pantalla, o `null` si esta pantalla no lleva cuenta
     * de nada ("sus mensajes" y la búsqueda por mensajes usan este mismo panel).
     */
    private fun claveNoticiasVistas(): String? = when (currentNoticesKind) {
        "quotes" -> ClavesPorCuenta.clave(PREF_CITAS_VISTAS, uidActivo)
        "mentions" -> ClavesPorCuenta.clave(PREF_MENCIONES_VISTAS, uidActivo)
        else -> null
    }

    /**
     * Da por leída UNA cita/mención: la que se acaba de tocar.
     *
     * Antes esto lo hacía sola la pantalla al abrirse, con la tanda entera. Ver
     * [NoticiasVistas.marcarLeida] para el porqué del cambio.
     */
    private fun marcarNoticiaLeida(url: String) {
        val clave = claveNoticiasVistas() ?: return
        val vistas = NoticiasVistas.leer(shellPrefs.getString(clave, "") ?: "")
        shellPrefs.edit()
            .putString(clave, NoticiasVistas.guardar(NoticiasVistas.marcarLeida(vistas, url)))
            .apply()
    }

    /** El botón de la cabecera: vacía de golpe lo que hay pintado y lo repinta apagado. */
    private fun marcarTodasLasNoticiasLeidas() {
        val clave = claveNoticiasVistas() ?: return
        val urls = noticeAdapter.urls()
        if (urls.isEmpty()) return
        val vistas = NoticiasVistas.leer(shellPrefs.getString(clave, "") ?: "")
        shellPrefs.edit()
            .putString(clave, NoticiasVistas.guardar(NoticiasVistas.recordar(vistas, urls)))
            .apply()
        noticeAdapter.submit(noticeAdapter.items(), emptySet())
    }

    private fun noticiasNuevas(urls: List<String>): Set<String> {
        val clave = claveNoticiasVistas() ?: return emptySet()
        val guardado = shellPrefs.getString(clave, null)
        // null = nunca se ha abierto este panel. Distinto de "" (abierto y sin nada guardado):
        // la primera vez no se resalta NADA, o verías tu historial entero en negrita.
        // PRIMERA VISITA: no se resalta nada y se da por visto TODO lo que hay. Si no, la
        // primera vez que abres el panel te saldría tu historial entero como recién llegado.
        // Es el único caso en que mirar marca algo; a partir de aquí, marca TOCAR.
        if (guardado == null) {
            shellPrefs.edit()
                .putString(clave, NoticiasVistas.guardar(NoticiasVistas.recordar(emptySet(), urls)))
                .apply()
            return emptySet()
        }
        // Y aquí NO se escribe nada: abrir la pantalla ya no marca nada como leído.
        val vistas = NoticiasVistas.leer(guardado)
        return urls
            .filter { NoticiasVistas.esNueva(it, vistas, primeraVisita = false) }
            .map { NoticiasVistas.idDe(it) }
            .toSet()
    }

    /** Encola las fechas que no se saben todavía y arranca si no hay ninguna en vuelo. */
    private fun pedirFechasNoticias(pids: List<String>) {
        fechasPorPedir.clear()
        for (pid in pids) if (pid.isNotEmpty() && pid !in fechasNoticias) fechasPorPedir.addLast(pid)
        if (pidiendoFecha.isEmpty()) siguienteFechaNoticia()
    }

    /** Una a una, para no soltarle a FC diez hilos enteros de golpe. */
    private fun siguienteFechaNoticia() {
        // Si ya no estás en Citas/Menciones, lo que quede se pedirá la próxima vez.
        val pid = if (isNoticesVisible) fechasPorPedir.removeFirstOrNull() else null
        pidiendoFecha = pid ?: ""
        if (pid == null) { fechasPorPedir.clear(); return }
        webView.evaluateJavascript("window.fcNoticeDate&&fcNoticeDate('${jsEscape(pid)}')", null)
    }

    private fun onNoticeDateJson(json: String) {
        val o = try { org.json.JSONObject(json) } catch (_: Exception) { null }
        val pid = o?.optString("pid").orEmpty()
        // "Hoy"/"Ayer" son relativos a CUANDO se pidió: se fijan ya, no al pintar.
        val momento = o?.optString("fecha")?.let { Popurri.momento(it, System.currentTimeMillis()) }
        if (pid.isNotEmpty() && momento != null) {
            fechasNoticias[pid] = momento
            shellPrefs.edit()
                .putString(PREF_FECHAS_NOTICIAS, SelloNoticia.guardarCache(fechasNoticias))
                .apply()
            if (isNoticesVisible) {
                noticeAdapter.ahora = System.currentTimeMillis()
                noticeAdapter.fechas = HashMap(fechasNoticias)
                noticeAdapter.notifyDataSetChanged()
            }
        }
        // Si falla, la fila se queda con la hora de FC: feo pero cierto. Y se sigue.
        siguienteFechaNoticia()
    }

    private fun onNoticesJson(json: String) {
        if (!isNoticesVisible) return
        noticesLoading.visibility = View.GONE
        val p = parseNoticesPayload(json) ?: return
        if (p.kind != currentNoticesKind) return
        // Página 2 en adelante: se AÑADE debajo, y un fallo no borra lo que ya se ve.
        val esPaginaSiguiente = mensajesCargando && noticeAdapter.itemCount > 0
        if (esPaginaSiguiente) {
            mensajesCargando = false
            if (p.error.isNotEmpty()) return   // el próximo scroll lo vuelve a intentar
            val nuevos = PaginacionMensajes.nuevos(noticeAdapter.urls(), filasDeMensajes(p.items))
            if (nuevos.isEmpty()) { mensajesAgotada = true; return }   // gotcha 37
            mensajesPagina++
            noticeAdapter.append(nuevos)
            return
        }
        mensajesCargando = false
        if (currentNoticesKind == "userposts" || currentNoticesKind == "searchposts") {
            mensajesBase = PaginacionMensajes.base(p.url)
            mensajesPagina = 1
            mensajesAgotada = mensajesBase == null || p.items.isEmpty()
        }
        if (p.error.isNotEmpty()) {
            restaurarNoticias = false
            // Citas y menciones tienen una URL propia que enseñarle al usuario para que
            // resuelva el challenge. Los mensajes de otro usuario NO: son el resultado de una
            // búsqueda POST-redirigida, así que aquí la capa web no es un plan B, es una
            // pantalla del foro sin motivo (regla de oro). Se dice qué pasa y ya.
            if (p.error == "cloudflare" &&
                (currentNoticesKind == "quotes" || currentNoticesKind == "mentions")
            ) {
                showWeb()
                webView.loadUrl(
                    if (currentNoticesKind == "quotes") menuLinks?.quotes ?: ""
                    else menuLinks?.mentions ?: ""
                )
                return
            }
            noticeAdapter.submit(emptyList())
            noticesEmpty.visibility = View.VISIBLE
            noticesEmpty.text = when {
                currentNoticesKind != "searchposts" -> "No se pudo consultar la actividad de este usuario"
                p.error == "login" -> "La búsqueda de ForoCoches requiere iniciar sesión"
                else -> "No se pudo completar la búsqueda"
            }
            return
        }
        // Dentro de UN hilo, repetir su título en cada fila es ruido: lo que quieres leer es el
        // mensaje. Se le da la vuelta a la fila — arriba el extracto, abajo el foro y la fecha.
        val filas = filasDeMensajes(p.items)
        // El sello de FC es solo la hora ("14:22") AUNQUE SEA DE OTRO DÍA: la fecha buena sale
        // del propio mensaje (pedirFechasNoticias). Hasta que llega, la hora tal cual.
        noticeAdapter.ahora = System.currentTimeMillis()
        noticeAdapter.fechas = HashMap(fechasNoticias)
        val nuevas = noticiasNuevas(filas.map { it.url })
        noticeAdapter.submit(filas, nuevas)
        if (currentNoticesKind == "quotes" || currentNoticesKind == "mentions") {
            pedirFechasNoticias(filas.map { replyPidFromUrl(it.url) })
        }
        val donde = posicionNoticias
        if (restaurarNoticias && donde != null && donde.first == currentNoticesKind &&
            donde.second < filas.size
        ) {
            val lm = noticesList.layoutManager as LinearLayoutManager
            noticesList.post { lm.scrollToPositionWithOffset(donde.second, donde.third) }
        }
        restaurarNoticias = false
        // "Marcar todo leído" solo donde hay algo que marcar.
        noticesMarkAll.visibility =
            if (claveNoticiasVistas() != null && filas.isNotEmpty()) View.VISIBLE else View.GONE
        if (p.items.isEmpty()) {
            noticesEmpty.visibility = View.VISIBLE
            noticesEmpty.text = when (currentNoticesKind) {
                "quotes" -> "No tienes citas recientes"
                "userposts" -> "Este usuario no tiene mensajes visibles"
                "searchposts" -> "Ningún mensaje contiene \"$searchQuery\"" +
                    (if (searchUser.isEmpty()) "" else " de @$searchUser")
                else -> "No tienes menciones recientes"
            }
        }
    }

    /**
     * Dentro de UN hilo, repetir su título en cada fila es ruido: lo que quieres leer es el
     * mensaje. Se le da la vuelta a la fila — arriba el extracto, abajo el foro y la fecha.
     */
    private fun filasDeMensajes(items: List<NoticeItem>): List<NoticeItem> =
        if (currentNoticesKind == "userposts" && actividadEnHilo.isNotEmpty()) {
            items.map { n ->
                val trozos = n.text.split(" · ", limit = 3)
                if (trozos.size == 3) n.copy(title = trozos[2], text = trozos[0] + " · " + trozos[1])
                else n
            }
        } else items

    // ── Panel del avatar (rediseño del 2026-10-02, ver BarraAbajo) ─────────────

    private lateinit var nativeAvatar: android.widget.ImageView
    private lateinit var nativeAvatarPunto: View
    /** La línea de tu ficha ("Usuario · 17 mensajes · …"), de la última vez que se pidió. */
    private var lineaPerfil = ""

    /** El panel del avatar: vive en [CuentaPanelController]; aquí solo se dice qué filas lleva. */
    private lateinit var cuentaPanel: CuentaPanelController

    /** ¿Está abierto el panel del avatar? (Falso hasta que existe.) */
    private val isPanelCuentaVisible: Boolean
        get() = ::cuentaPanel.isInitialized && cuentaPanel.visible

    private fun configurarPanelCuenta() {
        cuentaPanel = CuentaPanelController(
            activity = this,
            nombre = {
                cuentasGuardadas().firstOrNull { it.uid == uidActivo }?.nombre
                    ?: profileName.text?.toString().orEmpty()
            },
            lineaPerfil = { lineaPerfil },
            pedirPerfil = { pedirPerfil() },
            pintarAvatar = { pintarAvatarCuenta() },
            alVerFicha = { if (isLoggedIn()) { showProfile(); pestana(Screen.Profile) } else showLogin() },
            alSalir = { doLogout() },
            rellenar = {
                val panel = repartoBarra().panel
                for (clave in panel) {
                    val id = navClaves.firstOrNull { it.first == clave }?.second ?: continue
                    fila(navIconos.getValue(clave), navNombres.getValue(clave), insigniaDe(clave)) { onNavClicked(id) }
                }
                if (panel.isNotEmpty()) separador()
                fila(R.drawable.ic_cuentas,
                    if (cuentasGuardadas().size > 1) "Cambiar de cuenta" else "Añadir otra cuenta") { mostrarHojaCuentas() }
                fila(R.drawable.ic_organizar, "Organizar barra") { showOptions(); showOrganizar(true) }
                fila(R.drawable.ic_opciones, "Opciones") { showOptions() }
                separador()
                fila(R.drawable.ic_opt_comunidad, "Comunidad en Telegram") { openExternal("https://t.me/foroplus") }
                fila(R.drawable.ic_opt_cafe, "Invítame a un café") { openExternal("https://paypal.me/neonforger") }
            }
        )
        cuentaPanel.configurar()
    }

    /** Qué abre el botón Avisos: Menciones solo si es lo único que hay nuevo; si no, Citas. */
    private fun avisoQueAbrir(): String =
        if (insigniaMenciones > 0 && insigniaCitas == 0) "mentions" else "quotes"

    private lateinit var pastillaCitas: TextView
    private lateinit var pastillaMenciones: TextView

    private fun configurarPastillas() {
        pastillaCitas = findViewById(R.id.notices_pill_citas)
        pastillaMenciones = findViewById(R.id.notices_pill_menciones)
        for ((v, kind) in listOf(pastillaCitas to "quotes", pastillaMenciones to "mentions")) {
            v.setOnClickListener {
                if (currentNoticesKind == kind) return@setOnClickListener
                showNotices(kind)
                if (isNoticesVisible) pestana(Screen.Notices(kind))
            }
        }
    }

    /** Citas | Menciones encima de la lista, solo en esas dos pantallas. */
    private fun pintarPastillas() {
        if (!::pastillaCitas.isInitialized) return
        val enAvisos = currentNoticesKind == "quotes" || currentNoticesKind == "mentions"
        findViewById<View>(R.id.notices_pastillas).visibility = if (enAvisos) View.VISIBLE else View.GONE
        if (!enAvisos) return
        for ((v, kind) in listOf(pastillaCitas to "quotes", pastillaMenciones to "mentions")) {
            val nombre = if (kind == "quotes") "Citas" else "Menciones"
            val n = if (kind == "quotes") insigniaCitas else insigniaMenciones
            val elegida = kind == currentNoticesKind
            v.text = if (n > 0) "$nombre · $n" else nombre
            v.backgroundTintList = android.content.res.ColorStateList.valueOf(
                color(if (elegida) R.color.fc_rojo else R.color.fc_chip))
            v.setTextColor(color(if (elegida) R.color.fc_sobre_rojo else R.color.fc_texto_2))
        }
    }

    /** "Mis mensajes" como destino de la barra o del panel: tus mensajes uno a uno. */
    private fun abrirMisMensajes() {
        val yo = cuentasGuardadas().firstOrNull { it.uid == uidActivo }?.nombre
            ?: profileName.text?.toString()?.trim().orEmpty()
        if (yo.isEmpty() || yo == "…") { toast("Perfil aún cargando…"); return }
        showUserActivity(yo, "posts", remember = false)
        pestana(Screen.UserActivity(yo, "posts", ""))
        setSelectedNav(R.id.nav_mismensajes)
    }

    /** Perfil nativo: nombre + avatar + cerrar sesión (el foro no se ve). */
    private fun showProfile() {
        isProfileVisible = true
        dejarElHilo(); isWebVisible = false; isReplyVisible = false
        isLoginVisible = false; isNoticesVisible = false; isOptionsVisible = false
        hidePmPanels()
        profilePanel.visibility = View.VISIBLE
        nativePanel.visibility = View.GONE
        threadPanel.visibility = View.GONE
        replyPanel.visibility = View.GONE
        loginPanel.visibility = View.GONE
        noticesPanel.visibility = View.GONE
        descargadosPanel.visibility = View.GONE
        optionsPanel.visibility = View.GONE
        swipeRefresh.visibility = View.INVISIBLE
        bottomNav.visibility = View.VISIBLE
        setSelectedNav(0)   // tu ficha se abre desde el panel del avatar, no desde la barra
        pintarFilaCuentas()
        if (profileName.text.isNullOrEmpty()) profileName.text = "…"
        pedirPerfil()
    }

    /**
     * Pide la ficha propia. Separado de [showProfile] porque hay un segundo disparador: tras un
     * cambio de cuenta, [olvidarIdentidadPintada] deja `menuLinks` a null a propósito (sus
     * enlaces son de la cuenta anterior), así que si el panel de Perfil ya está delante no hay
     * URL que pedir todavía. Es [onThreadListJson] quien, al traer el menú de la cuenta nueva,
     * vuelve a llamar aquí — mismo patrón que `pendingDeepLink`. Sin esto el panel se
     * quedaría clavado en "…".
     */
    private fun pedirPerfil() {
        val url = menuLinks?.profile ?: return
        webView.evaluateJavascript("window.fcLoadProfile&&fcLoadProfile('${jsEscape(url)}')", null)
    }

    /**
     * Borra TODO lo que hay pintado de "quién eres". Existe como función única porque esta
     * lista se necesita en dos sitios —cerrar sesión y cambiar de cuenta— y tenerla suelta en
     * uno solo fue justo el fallo: el cambio de cuenta dejaba el nombre, el avatar y los
     * enlaces del menú de la cuenta anterior. Misma regla que `updatePageBar()`: estas cuatro
     * cosas se tocan desde aquí y de ningún otro sitio.
     *
     * `menuLinks` a null y no "lo que hubiera": sus enlaces llevan el `u=` de la cuenta que se
     * va, así que conservarlos es pedir la ficha, las citas y las menciones de OTRO. Prefiere
     * quedarse sin enlaces un instante (hasta que llegue la lista de la cuenta nueva) a usar
     * los viejos.
     */
    private fun olvidarIdentidadPintada() {
        lineaPerfil = ""
        profileLogoutUrl = ""
        profileName.text = ""
        profileAvatar.setImageResource(R.drawable.ic_nav_person)
        profileSub.text = "Cuenta de ForoCoches"
        profileFirma.visibility = View.GONE
        profileSobreMi.visibility = View.GONE
        menuLinks = null
    }

    private fun onProfileJson(json: String) {
        try {
            val o = org.json.JSONObject(json)
            val name = o.optString("name")
            if (name.isNotEmpty()) profileName.text = name
            profileLogoutUrl = o.optString("logout", profileLogoutUrl)
            // Tu ficha es la misma member.php que ven los demás: misma línea y mismos datos.
            EstadisticasMiembro.conRango(
                o.optString("rango").trim(),
                EstadisticasMiembro.linea(
                    o.optString("mensajes").trim(), o.optString("hilos").trim(),
                    o.optString("registro").trim(), System.currentTimeMillis()
                )
            ).takeIf { it.isNotEmpty() }?.let { profileSub.text = it; lineaPerfil = it }
            pintarFicha(o, profileFirma, profileSobreMi)
            if (isPanelCuentaVisible) cuentaPanel.pintar()
            val av = o.optString("avatar")
            if (av.isNotEmpty()) {
                PostImages.get(av)?.let { profileAvatar.setImageBitmap(it) } ?: PostImages.load(av) {
                    PostImages.get(av)?.let { profileAvatar.setImageBitmap(it) }
                }
            }
        } catch (_: Exception) { }
        // fcLoadProfile es async: si al llegar la respuesta cambió el número de cuentas
        // (p.ej. se acaba de terminar un cambio de cuenta), que la fila no se quede rezagada.
        pintarFilaCuentas()
    }

    /**
     * Texto de la fila que lleva a la hoja de cuentas, en el panel de Perfil. Dinámico porque es
     * la única puerta de entrada real a "tener una segunda cuenta": con una sola, invita a
     * añadir; con dos o más, ya no tiene sentido decir "añadir", así que pasa a ser el atajo
     * genérico para cambiar. Sin este texto puesto a mano en cada pintado, un cambio de cuenta
     * dejaría la fila diciendo "añadir" aunque ya hubiera dos.
     */
    private fun pintarFilaCuentas() {
        if (!::profileCuentas.isInitialized) return
        profileCuentas.text =
            if (cuentasGuardadas().size > 1) "Cambiar de cuenta" else "Añadir otra cuenta"
    }

    /**
     * Quién es el usuario logueado. Llega tras entrar y al arrancar con sesión: es lo que da el
     * uid con el que se separan las preferencias de cada cuenta.
     */
    /**
     * No se ha podido entrar con la otra cuenta: se repone la que había y se explica por qué.
     *
     * Reponer **no es opcional**: para intentar el login se borró la sesión (ver `submitLogin`),
     * así que sin esto el usuario se queda fuera de la cuenta que ya tenía por intentar añadir
     * otra. El panel se deja abierto y con lo escrito, para poder corregir y reintentar.
     */
    private fun reponerCuentaAnterior(err: String) {
        val anteriores = cookiesAntesDeAnadir
        cookiesAntesDeAnadir = emptyMap()
        aceptarUidNuevo = false
        SesionFC.borrar(CookieManager.getInstance()) {
            SesionFC.poner(CookieManager.getInstance(), anteriores)
            CookieManager.getInstance().flush()
            sendingLogin = false
            loginSubmit.text = "Iniciar sesión"
            loginError.text = VeredictoLogin.mensajeAlAnadir(err)
            loginError.visibility = View.VISIBLE
        }
    }

    private fun onQuienSoy(json: String) {
        // Ninguna salida de aquí en adelante puede dejar aceptarUidNuevo colgado en true: es un
        // permiso de un solo uso (onLoginResult lo pone justo antes de la ÚNICA llamada a
        // fcQuienSoy() que hace tras un login) y no hay ningún otro disparador que lo cierre
        // después. Si la respuesta no trae ningún uid utilizable (JSON que no parsea, o
        // fcQuienSoy con uid: '' por un fallo de red o por una página intermedia de Cloudflare
        // en vez de usercp.php), no hay nada que comparar ni que aceptar: hay que cerrar el
        // permiso aquí, porque no va a volver a intentarse.
        val o = try { org.json.JSONObject(json) } catch (_: Exception) {
            aceptarUidNuevo = false
            return
        }
        val uid = o.optString("uid").trim()
        if (uid.isEmpty()) {
            aceptarUidNuevo = false
            return
        }
        // Regla de tres casos, en este orden, ANTES de escribir nada. Sin ella, en cuanto
        // uidEsperado se vacía (éxito, revert o pedirLoginDe) la función volvía a aceptar
        // CUALQUIER uid — y cambiarACuenta puede dejar hasta cuatro peticiones en vuelo (la
        // inmediata y las de los reintentos), así que una que resolviera tarde podía sobrescribir
        // uidActivo/PREF_UID_ACTIVO/PREF_CUENTAS y guardar en el cofre de una cuenta las
        // cookies de otra.
        //
        // 1) Hay un cambio de cuenta en curso: solo vale la respuesta de la cuenta que se está
        //    esperando. Cualquier otra es de otra cuenta o de una petición ya superada.
        if (uidEsperado.isNotEmpty() && uid != uidEsperado) return
        // 2) Ninguno en curso, pero acabamos de iniciar sesión (onLoginResult puso
        //    aceptarUidNuevo = true justo antes de pedir fcQuienSoy): es una cuenta que
        //    todavía no conocíamos, así que cualquier uid vale — sin este caso, entrar con una
        //    cuenta distinta a la activa ("Añadir cuenta") se descartaría en el caso 3.
        // 3) Ni lo uno ni lo otro: la única respuesta legítima es la de la cuenta activa. Una
        //    respuesta tardía de un intento ya cerrado se descarta.
        if (uidEsperado.isEmpty() && !aceptarUidNuevo &&
            uidActivo.isNotEmpty() && uid != uidActivo) return
        aceptarUidNuevo = false
        uidActivo = uid
        val cuenta = Cuenta(uid, o.optString("nombre").trim(), o.optString("avatar").trim())
        val lista = Cuentas.anadir(cuentasGuardadas(), cuenta)
        shellPrefs.edit()
            .putString(PREF_CUENTAS, Cuentas.guardar(lista))
            .putString(PREF_UID_ACTIVO, uid)
            .apply()
        migrarSiHaceFalta()
        guardarCookiesDe(uidActivo)
        pintarAvatarCuenta()
        pintarAvatarComposer()
    }

    /** Cuentas guardadas ahora mismo. */
    private fun cuentasGuardadas(): List<Cuenta> =
        Cuentas.leer(shellPrefs.getString(PREF_CUENTAS, "") ?: "")

    /**
     * Adopta para la cuenta activa los datos que venían de antes de las cuentas.
     *
     * Le pasa a TODO el que actualice a la 40: si esto falla, pierde las marcas de leído y el
     * foro entero le sale en negrita. Por eso **el origen no se borra**: se copia, se marca como
     * hecha, y lo viejo se queda donde estaba. Ocupa poco y es la red de seguridad si algo salió
     * torcido.
     */
    // Kotlin no admite @Suppress delante de una rama de un `when` (no es una declaración):
    // se anota la función entera en vez de cada `is Set<*> ->` por separado.
    @Suppress("UNCHECKED_CAST")
    private fun migrarSiHaceFalta() {
        val yaHecha = shellPrefs.getBoolean(PREF_MIGRADAS, false)
        // Se usa `size > 1` y NO `isNotEmpty()`: aquí arriba, unas líneas antes, `onQuienSoy`
        // YA ha añadido la cuenta activa a la lista (vía Cuentas.anadir). Con una sola cuenta
        // guardada seguimos en el caso "no había índice previo" (instalación que viene de
        // antes de la 40) y SÍ hay que migrar. Con `isNotEmpty()` esa primera cuenta bastaría
        // para creer que el índice ya existía de antes y la migración de los 144 no correría
        // nunca. No "arreglar" esto a isNotEmpty().
        if (!MigracionCuentas.hayQueMigrar(yaHecha, uidActivo, cuentasGuardadas().size > 1)) return
        try {
            // 1. Ficheros enteros: se copia clave a clave al fichero de la cuenta.
            for (base in ClavesPorCuenta.FICHEROS) {
                val viejo = getSharedPreferences(base, MODE_PRIVATE)
                val nuevo = getSharedPreferences(ClavesPorCuenta.fichero(base, uidActivo), MODE_PRIVATE)
                if (viejo.all.isEmpty() || nuevo.all.isNotEmpty()) continue
                val ed = nuevo.edit()
                for ((k, v) in viejo.all) {
                    when (v) {
                        is String -> ed.putString(k, v)
                        is Boolean -> ed.putBoolean(k, v)
                        is Int -> ed.putInt(k, v)
                        is Long -> ed.putLong(k, v)
                        is Float -> ed.putFloat(k, v)
                        is Set<*> -> ed.putStringSet(k, v as Set<String>)
                    }
                }
                ed.apply()
            }
            // 2. Claves sueltas de shell_prefs.
            val ed = shellPrefs.edit()
            for (base in ClavesPorCuenta.CLAVES) {
                val destino = ClavesPorCuenta.clave(base, uidActivo)
                if (shellPrefs.contains(destino)) continue
                when (val v = shellPrefs.all[base]) {
                    is String -> ed.putString(destino, v)
                    is Boolean -> ed.putBoolean(destino, v)
                    is Int -> ed.putInt(destino, v)
                    is Long -> ed.putLong(destino, v)
                    is Set<*> -> ed.putStringSet(destino, v as Set<String>)
                    else -> {}
                }
            }
            ed.apply()
            shellPrefs.edit().putBoolean(PREF_MIGRADAS, true).apply()
        } catch (e: Throwable) {
            // Sin marcar como hecha: se reintenta al siguiente arranque, con el origen intacto.
            android.util.Log.w("Cuentas", "migración incompleta, se reintentará", e)
        }
    }

    /**
     * Hoja de cambio rápido. Solo tiene sentido con más de una cuenta: con una sola, la app no
     * se complica para quien no usa esto.
     */
    private fun mostrarHojaCuentas() {
        // Con un cambio ya en marcha no se puede lanzar otro por encima (ver generacionCambio):
        // dos SesionFC.borrar/poner a la vez dejarían las cookies en cualquier estado.
        if (cambiandoDeCuenta) { toast("Cambiando de cuenta…"); return }
        val cuentas = cuentasGuardadas()
        val view = layoutInflater.inflate(R.layout.sheet_cuentas, null)
        val sheet = com.google.android.material.bottomsheet.BottomSheetDialog(this)
        sheet.setContentView(view)
        val lista = view.findViewById<LinearLayout>(R.id.cuentas_lista)
        for (c in cuentas) {
            val fila = layoutInflater.inflate(R.layout.item_cuenta, lista, false)
            fila.findViewById<TextView>(R.id.cuenta_nombre).text = c.nombre.ifEmpty { "Cuenta ${c.uid}" }
            fila.findViewById<TextView>(R.id.cuenta_activa).visibility =
                if (c.uid == uidActivo) View.VISIBLE else View.GONE
            val av = fila.findViewById<android.widget.ImageView>(R.id.cuenta_avatar)
            AvatarRedondo.de(c.avatar)?.let { av.setImageBitmap(it) }
                ?: av.setImageResource(R.drawable.ic_nav_person)
            fila.setOnClickListener {
                sheet.dismiss()
                if (c.uid != uidActivo) cambiarACuenta(c.uid)
            }
            fila.setOnLongClickListener {
                sheet.dismiss()
                confirmarQuitarCuenta(c)
                true
            }
            lista.addView(fila)
        }
        view.findViewById<TextView>(R.id.cuentas_anadir).setOnClickListener {
            sheet.dismiss()
            if (!Cuentas.hayHueco(cuentas)) {
                toast("Puedes tener ${Cuentas.TOPE} cuentas como máximo")
            } else {
                // Login de siempre: sin pantallas nuevas. Al entrar, onQuienSoy la guarda sola.
                loginParaOtraCuenta = true
                showLogin()
            }
        }
        sheet.show()
    }

    private fun confirmarQuitarCuenta(c: Cuenta) {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Quitar cuenta")
            .setMessage("¿Quitar ${c.nombre.ifEmpty { c.uid }} de la app? No se borra nada en ForoCoches.")
            .setPositiveButton("Quitar") { _, _ ->
                val antes = cuentasGuardadas()
                // Se calcula el relevo ANTES de guardar la lista nueva: después, la cuenta que
                // se va ya no está y no habría desde dónde decidir.
                val siguiente = Cuentas.siguienteTras(antes, c.uid)
                shellPrefs.edit()
                    .putString(PREF_CUENTAS, Cuentas.guardar(Cuentas.quitar(antes, c.uid))).apply()
                borrarCookiesGuardadas(c.uid)
                if (c.uid == uidActivo) {
                    if (siguiente.isEmpty()) cerrarSesionLocal() else {
                        // Sin esto, cambiarACuenta() volvería a escribir en el cofre: su primer
                        // paso es guardarCookiesDe(uidActivo) "por si se renovaron", y uidActivo
                        // en este instante SIGUE siendo la cuenta que se acaba de quitar — así
                        // que resucitaría las cookies que borrarCookiesGuardadas() acaba de
                        // borrar. Vaciarlo antes deja guardarCookiesDe() sin nada que guardar
                        // (sale sin hacer nada con uid vacío).
                        uidActivo = ""
                        cambiarACuenta(siguiente)
                    }
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    /**
     * Las cookies de sesión de cada cuenta. Son **credenciales**, así que van cifradas; si el
     * keystore del móvil falla (pasa en algunos), se cae a preferencias normales antes que
     * dejar la función rota — el CookieManager ya guarda las de la cuenta activa en claro de
     * todos modos.
     *
     * OJO versión: `security-crypto:1.0.0` (la que pide el plan) es la última estable y NO
     * trae la clase `MasterKey` nueva — esa API llegó en 1.1.0-alpha. Con 1.0.0 la forma de
     * conseguir la clave maestra es `MasterKeys.getOrCreate(...)`, que devuelve el alias como
     * `String` en vez de un objeto `MasterKey`. Mismo cifrado, mismo `catch` de red de
     * seguridad; solo cambia la llamada por la que existe de verdad en esta versión.
     */
    private val cofre: android.content.SharedPreferences by lazy {
        try {
            val aliasMaestra = androidx.security.crypto.MasterKeys.getOrCreate(
                androidx.security.crypto.MasterKeys.AES256_GCM_SPEC
            )
            androidx.security.crypto.EncryptedSharedPreferences.create(
                "fc_cuentas_cookies", aliasMaestra, this,
                androidx.security.crypto.EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                androidx.security.crypto.EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Throwable) {
            android.util.Log.w("Cuentas", "sin cifrado disponible", e)
            getSharedPreferences("fc_cuentas_cookies", MODE_PRIVATE)
        }
    }

    private fun guardarCookiesDe(uid: String) {
        if (uid.isEmpty()) return
        val cookies = SesionFC.leer(CookieManager.getInstance())
        if (cookies.isEmpty()) return
        val o = org.json.JSONObject()
        for ((k, v) in cookies) o.put(k, v)
        cofre.edit().putString(uid, o.toString()).apply()
    }

    private fun cookiesDe(uid: String): Map<String, String> {
        val s = cofre.getString(uid, "") ?: ""
        if (s.isEmpty()) return emptyMap()
        return try {
            val o = org.json.JSONObject(s)
            o.keys().asSequence().associateWith { o.optString(it) }
        } catch (_: Exception) { emptyMap() }
    }

    private fun borrarCookiesGuardadas(uid: String) { cofre.edit().remove(uid).apply() }

    /**
     * Cambia a [uid], avisando antes si hay un borrador sin enviar (en la barra rápida o en el
     * composer grande: son dos cajas distintas y cualquiera de las dos se perdería). El cambio
     * de verdad vive en [cambiarACuentaConfirmado]; esta función es solo la comprobación previa.
     */
    private fun cambiarACuenta(uid: String) {
        val hayBorrador = quickInput.text.isNotEmpty() || replyInput.text.isNotEmpty()
        if (hayBorrador) {
            androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Tienes un mensaje a medias")
                .setMessage("Si cambias de cuenta se queda sin enviar. ¿Cambiar igualmente?")
                .setPositiveButton("Cambiar") { _, _ -> cambiarACuentaConfirmado(uid) }
                .setNegativeButton("Seguir escribiendo", null)
                .show()
            return
        }
        cambiarACuentaConfirmado(uid)
    }

    /**
     * El cambio de cuenta de verdad. El riesgo de esta función es **publicar con la cuenta
     * equivocada**, así que: no se anuncia el cambio hasta que FC confirma quién eres, y si algo
     * falla se vuelve a la cuenta anterior en vez de quedarse a medias.
     */
    private fun cambiarACuentaConfirmado(uid: String) {
        val guardadas = cookiesDe(uid)
        if (guardadas.isEmpty()) { pedirLoginDe(uid); return }
        guardarCookiesDe(uidActivo)            // por si se renovaron
        val anteriores = SesionFC.leer(CookieManager.getInstance())
        val anterior = uidActivo
        cambiandoDeCuenta = true
        replySend.isEnabled = false
        // Token de correlación (ver el comentario de generacionCambio): esta llamada es LA
        // única en curso a partir de ahora. Cualquier callback (de onQuienSoy o de los propios
        // reintentos de más abajo) que llegue cuando generacionCambio ya no sea este número es
        // de un cambio superado y se ignora.
        val miGeneracion = ++generacionCambio
        uidEsperado = uid
        SesionFC.borrar(CookieManager.getInstance()) {
            SesionFC.poner(CookieManager.getInstance(), guardadas)
            uidActivo = ""
            // Mismo instante en que cambian las cookies, y en disco: si no, NotificationChecker/
            // NotificationService (que no ven esta variable en memoria, leen shell_prefs desde
            // otro proceso) seguirían resolviendo su fichero con el uid VIEJO mientras las
            // cookies ya son las nuevas, y le escribirían los contadores de la cuenta nueva a la
            // anterior. Vacío, no el uid destino: el cambio no está confirmado todavía (eso es
            // justo lo que dice el comentario de esta función — no se anuncia hasta que FC
            // confirma quién eres), así que los repositorios caen al fichero SIN sufijo, el
            // mismo que usan sin sesión. Diluye ese fichero compartido en vez de escribir en el
            // de una cuenta que no es.
            shellPrefs.edit().putString(PREF_UID_ACTIVO, "").apply()

            fun preguntar() {
                webView.evaluateJavascript("window.fcQuienSoy&&fcQuienSoy()") { }
            }

            // Reintentos en vez de una única espera fija: SesionFC documenta que FC puede
            // seguir sirviendo la sesión vieja por Varnish hasta ~1 minuto después de cambiar
            // las cookies, así que una sola comprobación a los 6 s puede leer una respuesta
            // cacheada y revertir un cambio que en realidad SÍ era válido. Se comprueba a los
            // 2, 5 y 9 s; en cuanto cuadra se da el cambio por bueno y se corta; solo si tras el
            // último intento sigue sin cuadrar se revierte.
            // Los tres `comprobar` se programan de golpe y NADA los cancela: en cuanto el
            // primero cuadra, los otros dos seguían entrando por la rama de éxito y repetían el
            // cambio entero. Medido por logcat el 2026-09-17 con dos cuentas reales: un solo
            // cambio disparaba TRES `requestThreadList` (2/5/9 s), con sus tres toasts
            // "Cuenta cambiada" y la lista vaciándose y repintándose tres veces. El token de
            // generación no valía para esto: los tres comparten generación justamente por ser
            // el mismo cambio.
            var resuelto = false

            fun comprobar(esUltimo: Boolean) {
                // Otro cambio (uno nuevo, o el revert de este mismo) ha tomado el relevo
                // mientras esperábamos: esta comprobación ya no pinta nada, ni para bien ni
                // para revertir.
                if (miGeneracion != generacionCambio) return
                // Este mismo cambio ya se dio por bueno en un intento anterior.
                if (resuelto) return
                if (uidActivo == uid) {
                    resuelto = true
                    uidEsperado = ""
                    cambiandoDeCuenta = false
                    replySend.isEnabled = true
                    toast("Cuenta cambiada")
                    listLoaded = false
                    // El adaptador de la LISTA (ThreadListAdapter), no el de los mensajes de un
                    // hilo: sin esto seguirían viéndose los hilos de la cuenta anterior, con sus
                    // marcas de leído, hasta que llegara la respuesta de la cuenta nueva.
                    adapter.submit(emptyList())
                    // Nombre, avatar, logout y menuLinks eran los de la cuenta que se va. Los
                    // enlaces del menú son lo importante: llevan `u=<uid viejo>`, así que sin
                    // esto la ficha y las pestañas de Citas/Menciones seguían siendo las de la
                    // otra cuenta hasta reiniciar la app (que es lo único que recarga la página
                    // del motor). requestThreadList, justo aquí abajo, trae los de la nueva.
                    olvidarIdentidadPintada()
                    requestThreadList(1)
                    pintarAvatarCuenta()
                    pintarAvatarComposer()
                } else if (!esUltimo) {
                    preguntar()     // reintento: puede que la respuesta anterior fuera Varnish
                } else {
                    // Último intento y sigue sin cuadrar: sesión caducada de verdad. Se vuelve
                    // a la anterior y se pide login.
                    SesionFC.borrar(CookieManager.getInstance()) revertir@{
                        // Mismo motivo que arriba: si mientras se borraba ya ha empezado OTRO
                        // cambio, este revert llegaría tarde y pisaría sus cookies.
                        if (miGeneracion != generacionCambio) return@revertir
                        SesionFC.poner(CookieManager.getInstance(), anteriores)
                        uidActivo = anterior
                        // Revert también en disco: se vació al empezar el cambio (ver arriba),
                        // y aquí las cookies vuelven a ser las de la cuenta anterior.
                        shellPrefs.edit().putString(PREF_UID_ACTIVO, anterior).apply()
                        uidEsperado = ""
                        cambiandoDeCuenta = false
                        replySend.isEnabled = true
                        pedirLoginDe(uid)
                    }
                }
            }

            preguntar()
            webView.postDelayed({ comprobar(esUltimo = false) }, 2_000L)
            webView.postDelayed({ comprobar(esUltimo = false) }, 5_000L)
            webView.postDelayed({ comprobar(esUltimo = true) }, 9_000L)
        }
    }

    /** Sesión caducada: login nativo con el nombre puesto. La cuenta NO se borra de la lista. */
    private fun pedirLoginDe(uid: String) {
        // Ya no hay ningún cambio de cuenta esperando confirmación: sin esto, si este aviso
        // viniera del camino "sin cookies guardadas" (el otro punto de entrada, que no pasa por
        // el token de generacionCambio) dejaría uidEsperado colgado de un cambio anterior.
        uidEsperado = ""
        val c = cuentasGuardadas().firstOrNull { it.uid == uid }
        toast("Tu sesión ha caducado, vuelve a entrar")
        // Aquí también puede seguir viva la sesión de OTRA cuenta, así que la cookie no vale
        // como veredicto: lo que hay que comprobar es que la sesión cambie de dueño.
        loginParaOtraCuenta = true
        showLogin()
        loginUser.setText(c?.nombre.orEmpty())
        loginPass.setText("")
    }

    // ── Opciones ─────────────────────────────────────────────────────────────

    private fun showOptions() {
        isOptionsVisible = true
        isNoticesVisible = false; isProfileVisible = false
        hidePmPanels()
        options.bind()
        optionsPanel.visibility = View.VISIBLE
        nativePanel.visibility = View.GONE
        threadPanel.visibility = View.GONE
        replyPanel.visibility = View.GONE
        loginPanel.visibility = View.GONE
        noticesPanel.visibility = View.GONE
        descargadosPanel.visibility = View.GONE
        profilePanel.visibility = View.GONE
        swipeRefresh.visibility = View.INVISIBLE
        bottomNav.visibility = View.GONE   // tiene inputs de texto: teclado a pantalla limpia
    }

    private fun hideOptions() {
        isOptionsVisible = false
        val imm = getSystemService(INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
        imm.hideSoftInputFromWindow(optionsPanel.windowToken, 0)
        optionsPanel.visibility = View.GONE
        bottomNav.visibility = View.VISIBLE
        // Vuelve a la lista (donde está el engranaje).
        showNative()
        setSelectedNav(navIdForList())
    }

    // ── Organizar las barras ──────────────────────────────────────────────────
    //
    // Las dos barras (pestañas de subforo y botones de abajo) se personalizan con la MISMA
    // pantalla y la misma lógica ([OrdenBarra]): son el mismo problema, y tenerlo dos veces
    // con reglas distintas era justo el lío que se quería evitar.

    private lateinit var organizarPanel: View
    private lateinit var orgAdapter: OrganizarAdapter
    private var isOrganizarVisible = false
    /** `true` = se está organizando la barra de abajo; `false` = las pestañas de subforo. */
    private var organizandoNav = false

    private fun configurarOrganizar() {
        organizarPanel = findViewById(R.id.organizar_panel)
        orgAdapter = OrganizarAdapter(
            alTocarCasilla = { clave -> alternarVisibleEnBarra(clave) },
            alSoltar = { orden -> guardarOrdenDeBarra(orden) }
        )
        val lista = findViewById<RecyclerView>(R.id.org_list)
        lista.layoutManager = LinearLayoutManager(this)
        lista.adapter = orgAdapter
        val helper = ItemTouchHelper(orgAdapter.callbackDeArrastre())
        helper.attachToRecyclerView(lista)
        orgAdapter.touchHelper = helper
        findViewById<View>(R.id.org_back).setOnClickListener { goBack() }
        findViewById<View>(R.id.org_reset).setOnClickListener { restablecerBarra() }
    }

    // ── Novedades (Opciones → Acerca de) ──────────────────────────────────────

    private lateinit var novedadesPanel: View
    private var isNovedadesVisible = false

    private fun configurarNovedades() {
        novedadesPanel = findViewById(R.id.novedades_panel)
        findViewById<View>(R.id.novedades_back).setOnClickListener { goBack() }
        optionsPanel.findViewById<View>(R.id.opt_novedades).setOnClickListener { showNovedades() }
        val nombre = try { packageManager.getPackageInfo(packageName, 0).versionName } catch (_: Exception) { null }
        options.ponerVersion(
            if (nombre.isNullOrEmpty()) "ForoPlus" else "ForoPlus $nombre (${versionCodeInstalado()})")
    }

    private fun showNovedades() {
        isNovedadesVisible = true
        pintarNovedades()
        findViewById<android.widget.ScrollView>(R.id.novedades_scroll).scrollTo(0, 0)
        optionsPanel.visibility = View.GONE
        novedadesPanel.visibility = View.VISIBLE
    }

    /** Se vuelve a Opciones, que es de donde se ha entrado. */
    private fun hideNovedades() {
        isNovedadesVisible = false
        novedadesPanel.visibility = View.GONE
        optionsPanel.visibility = View.VISIBLE
    }

    /**
     * Se pinta cada vez que se abre (son unas decenas de líneas) en vez de tenerlo en memoria:
     * es una pantalla de visita rara. Del APK, no de la red: ver [Novedades].
     */
    private fun pintarNovedades() {
        val lista = findViewById<LinearLayout>(R.id.novedades_lista)
        lista.removeAllViews()
        val json = try { assets.open("novedades.json").bufferedReader().use { it.readText() } }
                   catch (_: Exception) { "" }
        val instalada = versionCodeInstalado()
        val versiones = Novedades.hasta(Novedades.leer(json), instalada)
        val dp = resources.displayMetrics.density
        if (versiones.isEmpty()) {
            lista.addView(TextView(this).apply {
                text = "No se ha podido leer el historial."
                textSize = 14f
                setTextColor(ContextCompat.getColor(context, R.color.fc_texto_2))
                setPadding(0, (20 * dp).toInt(), 0, 0)
            })
            return
        }
        for (v in versiones) {
            lista.addView(TextView(this).apply {
                text = if (v.codigo == instalada) "${Novedades.cabecera(v)}  ·  la tuya" else Novedades.cabecera(v)
                textSize = 15f
                setTypeface(null, android.graphics.Typeface.BOLD)
                setTextColor(ContextCompat.getColor(context,
                    if (v.codigo == instalada) R.color.fc_rojo else R.color.fc_texto))
                setPadding(0, (22 * dp).toInt(), 0, (6 * dp).toInt())
            })
            for (nota in v.notas) {
                lista.addView(TextView(this).apply {
                    text = "•  $nota"
                    textSize = 14f
                    setTextColor(ContextCompat.getColor(context, R.color.fc_texto_2))
                    setLineSpacing(0f, 1.15f)
                    setPadding(0, (3 * dp).toInt(), 0, (3 * dp).toInt())
                })
            }
        }
    }

    /** Clave → nombre visible de cada elemento de la barra que se está organizando. */
    private fun catalogoDeBarra(nav: Boolean): List<Pair<String, String>> =
        if (nav) navClaves.map { (clave, _) -> clave to (navNombres[clave] ?: clave) }
        else subforosConocidos.map { (fid, nombre) -> fid.toString() to nombre }

    private fun prefOrdenBarra(nav: Boolean) = if (nav) PREF_NAV_ORDEN else PREF_TABS_ORDEN
    private fun prefOcultosBarra(nav: Boolean) = if (nav) PREF_NAV_OCULTOS else PREF_TABS_OCULTOS
    private fun ordenDeBarra(nav: Boolean) =
        OrdenBarra.leer(shellPrefs.getString(prefOrdenBarra(nav), "") ?: "")
    private fun ocultosDeBarra(nav: Boolean) =
        OrdenBarra.leer(shellPrefs.getString(prefOcultosBarra(nav), "") ?: "").toSet()
    private fun fijosDeBarra(nav: Boolean) = if (nav) navFijos else emptySet()

    private fun showOrganizar(nav: Boolean) {
        organizandoNav = nav
        isOrganizarVisible = true
        findViewById<TextView>(R.id.org_title).text =
            if (nav) "Barra de abajo" else "Subforos de arriba"
        findViewById<TextView>(R.id.org_hint).text =
            if (nav) "Toca una fila para pasarla de la barra al panel del avatar o al revés. " +
                "Arrastra ☰ para cambiar el orden."
            else "Toca una fila para verla o esconderla. Arrastra ☰ para cambiar el orden."
        pintarOrganizar()
        optionsPanel.visibility = View.GONE
        organizarPanel.visibility = View.VISIBLE
    }

    /** Se vuelve a Opciones, que es de donde se ha entrado. */
    private fun hideOrganizar() {
        isOrganizarVisible = false
        organizarPanel.visibility = View.GONE
        optionsPanel.visibility = View.VISIBLE
    }

    private fun pintarOrganizar() {
        if (organizandoNav) {
            // La barra de abajo se organiza en dos partes: lo que va abajo y lo que va al panel.
            val r = repartoBarra()
            orgAdapter.submit(
                r.abajo.mapIndexed { i, clave ->
                    FilaOrganizar(clave, navNombres[clave] ?: clave, true, clave in navFijos,
                        if (i == 0) "BARRA DE ABAJO · ${BarraAbajo.HUECOS} HUECOS" else null)
                } + r.panel.mapIndexed { i, clave ->
                    FilaOrganizar(clave, navNombres[clave] ?: clave, false, false,
                        if (i == 0) "EN EL PANEL DEL AVATAR" else null)
                }
            )
            return
        }
        val cat = catalogoDeBarra(organizandoNav)
        val nombres = cat.toMap()
        val ocultos = ocultosDeBarra(organizandoNav)
        val fijos = fijosDeBarra(organizandoNav)
        // Salen TODOS, ocultos incluidos: si lo escondido desapareciera también de aquí, la
        // única forma de recuperarlo sería restablecerlo todo.
        orgAdapter.submit(
            OrdenBarra.completo(cat.map { it.first }, ordenDeBarra(organizandoNav)).map { clave ->
                FilaOrganizar(clave, nombres[clave] ?: clave, clave !in ocultos, clave in fijos)
            }
        )
    }

    private fun alternarVisibleEnBarra(clave: String) {
        if (organizandoNav) { alternarEnBarraAbajo(clave); return }
        val nav = organizandoNav
        val fijos = fijosDeBarra(nav)
        val nuevos = OrdenBarra.alternarOculto(
            catalogoDeBarra(nav).map { it.first }, ocultosDeBarra(nav), clave, fijos
        )
        // Un toque que no hace nada y no dice por qué es peor que no poder tocar.
        if (nuevos == null) {
            toast(if (clave in fijos) "Inicio no se puede quitar" else "Tiene que quedar al menos uno")
            return
        }
        shellPrefs.edit()
            .putString(prefOcultosBarra(nav), OrdenBarra.guardar(nuevos.toList())).apply()
        pintarOrganizar()
        aplicarBarra(nav)
    }

    /**
     * Barra de abajo: tocar una fila la pasa de la barra al panel o al revés. Con la barra
     * llena, se pregunta cuál sale: un toque que mueve algo que no has elegido sorprende.
     */
    private fun alternarEnBarraAbajo(clave: String) {
        val r = repartoBarra()
        if (clave in r.abajo) {
            val n = BarraAbajo.alPanel(r, clave)
            if (n == null) { toast("Inicio va siempre en la barra"); return }
            guardarReparto(n)
            return
        }
        BarraAbajo.ponerAbajo(r, clave)?.let { guardarReparto(it); return }
        val opciones = BarraAbajo.sustituibles(r)
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("La barra tiene ${BarraAbajo.HUECOS} huecos. ¿Cuál pasa al panel?")
            .setItems(opciones.map { navNombres[it] ?: it }.toTypedArray()) { _, i ->
                BarraAbajo.ponerAbajo(r, clave, opciones[i])?.let { guardarReparto(it) }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun guardarReparto(r: BarraAbajo.Reparto) {
        shellPrefs.edit()
            .putString(PREF_NAV_ORDEN, OrdenBarra.guardar(BarraAbajo.orden(r)))
            .putString(PREF_NAV_OCULTOS, OrdenBarra.guardar(BarraAbajo.ocultos(r).toList()))
            .apply()
        pintarOrganizar()
        pintarBarraInferior()
    }

    private fun guardarOrdenDeBarra(orden: List<String>) {
        if (organizandoNav) { guardarReparto(BarraAbajo.trasArrastrar(repartoBarra(), orden)); return }
        shellPrefs.edit()
            .putString(prefOrdenBarra(organizandoNav), OrdenBarra.guardar(orden)).apply()
        aplicarBarra(organizandoNav)
    }

    /**
     * Devuelve la barra a como viene de fábrica. Es barato y es el único cable pelado que
     * salva a quien se deje la barra hecha un cristo: sin esto, la marcha atrás sería
     * reinstalar la app.
     */
    private fun restablecerBarra() {
        val nav = organizandoNav
        shellPrefs.edit()
            .remove(prefOrdenBarra(nav)).remove(prefOcultosBarra(nav)).apply()
        pintarOrganizar()
        aplicarBarra(nav)
        toast("Barra restablecida")
    }

    private fun aplicarBarra(nav: Boolean) {
        if (nav) pintarBarraInferior() else pintarPestanas()
    }

    /**
     * Rehace la barra de abajo con el orden del usuario y sin lo que haya escondido.
     *
     * Los botones escondidos se quedan **fuera de la jerarquía**, no en GONE, porque de todas
     * formas hay que volver a añadirlos en orden. Sus vistas siguen vivas en [navItems], así
     * que las referencias de iconos, textos y avisos no se rompen — y **esconder un botón no
     * apaga su pantalla**: una notificación de cita sigue abriendo Citas aunque su botón no
     * esté en la barra. La barra enseña el atajo, no da el permiso.
     */
    private fun pintarBarraInferior() {
        val fila = findViewById<LinearLayout>(R.id.bottom_nav_row) ?: return
        fila.removeAllViews()
        for (clave in repartoBarra().abajo) {
            val id = navClaves.firstOrNull { it.first == clave }?.second ?: continue
            navItems[id]?.let {
                (it.parent as? android.view.ViewGroup)?.removeView(it)
                // A partes iguales: sin scroll, cada botón se lleva un cuarto de la pantalla.
                it.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                fila.addView(it)
            }
        }
        pintarInsignias()
    }

    /** Qué va abajo y qué va al panel, según lo guardado (viejo o nuevo). Ver [BarraAbajo]. */
    private fun repartoBarra(): BarraAbajo.Reparto =
        BarraAbajo.reparto(ordenDeBarra(true), ocultosDeBarra(true))

    private fun doLogout() {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Cerrar sesión")
            .setMessage("¿Seguro que quieres salir de tu cuenta?")
            .setPositiveButton("Salir") { _, _ ->
                // Se avisa a FC si se puede (deja la sesión limpia en su lado), pero el cierre
                // NO depende de que conteste: con una cuenta baneada no da la URL de salir, y
                // así el usuario se quedaba atrapado sin poder cambiar de cuenta.
                if (profileLogoutUrl.isNotEmpty()) {
                    webView.evaluateJavascript(
                        "window.fcLogout&&fcLogout('${jsEscape(profileLogoutUrl)}')", null
                    )
                }
                cerrarSesionLocal()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    /**
     * Corta la sesión **en el móvil**, que es donde vive de verdad: son cookies (ver [SesionFC]).
     * No pregunta a FC, así que funciona también con una cuenta baneada.
     */
    private fun cerrarSesionLocal() {
        SesionFC.borrar(CookieManager.getInstance()) {
            uidActivo = ""
            // NotificationChecker y NotificationService corren sin MainActivity viva y leen
            // PREF_UID_ACTIVO de disco: si no se borra aquí, seguirían consultando y escribiendo
            // las notificaciones de la cuenta que acaba de cerrar sesión.
            shellPrefs.edit().remove(PREF_UID_ACTIVO).apply()
            // Nombre, avatar, logout y menuLinks: la lista vive en olvidarIdentidadPintada()
            // porque el cambio de cuenta necesita exactamente la misma limpieza.
            olvidarIdentidadPintada()
            updateAccountNavItem()
            updateBadges(0, 0, 0)
            listLoaded = false
            // showHomeList() solo vacía el adaptador cuando VENÍAS de otra lista (wasOther); si
            // ya estabas en Inicio se queda tal cual hasta que llega la respuesta nueva, y
            // durante ese instante se ven los hilos (con sus marcas de leído) de la cuenta que
            // acaba de cerrar sesión. Mismo caso que en cambiarACuenta(): se vacía aquí también,
            // sin esperar a que showHomeList() decida si le toca.
            adapter.submit(emptyList())
            toast("Sesión cerrada")
            // Vuelve a la lista: esta era la ÚNICA función del fichero que recargaba la lista
            // sin pasar antes por showNative()/showHomeList(), y doLogout() solo se puede
            // invocar desde Perfil -> el usuario se quedaba atrapado ahí, viendo su nombre en
            // blanco. showHomeList() deja listSource="home" (si se venía de "Mis hilos" no se
            // repite una petición que exige sesión) y ya pide la página 1 ella sola -> no hace
            // falta un requestThreadList(1) aparte, sería una petición duplicada.
            showHomeList()
        }
    }

    private fun onLogoutDone(result: String) {
        // El cierre real ya lo hizo cerrarSesionLocal(); esto es solo la respuesta de FC.
        CookieManager.getInstance().flush()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Estilo Tarjetas: un overlay de tema que redefine los atributos fc* (ver EstiloApp).
        // Tiene que ir ANTES de inflar nada; cambiarlo luego recrea la actividad.
        EstiloApp.overlay(EstiloApp.guardado(getSharedPreferences(PREFS, MODE_PRIVATE)))
            ?.let { theme.applyStyle(it, true) }
        setContentView(R.layout.activity_main)

        swipeRefresh = findViewById(R.id.swipe_refresh)
        webView = findViewById(R.id.webview)
        nativePanel = findViewById(R.id.native_panel)
        listRefresh = findViewById(R.id.list_refresh)
        threadRefresh = findViewById(R.id.thread_refresh)
        threadList = findViewById(R.id.thread_list)
        listLoading = findViewById(R.id.list_loading)
        listEmpty = findViewById(R.id.list_empty)
        mas18Cabecera = findViewById(R.id.mas18_cabecera)
        mas18Estado = findViewById(R.id.mas18_estado)
        mas18Chips = findViewById(R.id.mas18_chips)
        bottomNav = findViewById(R.id.bottom_nav)
        forumTabs = findViewById(R.id.forum_tabs)
        navTop = findViewById(R.id.native_top)
        navTop.setOnClickListener { alternarTop() }
        threadPanel = findViewById(R.id.thread_panel)
        postList = findViewById(R.id.post_list)
        threadLoading = findViewById(R.id.thread_loading)
        threadTitle = findViewById(R.id.thread_title)
        threadTitle.setOnClickListener {
            // Solo se despliega si de verdad esta cortado: un toque que no hace nada en los
            // titulos que ya se ven enteros deja al usuario dudando de si el gesto existe.
            if (tituloDesplegado || tituloCortado()) {
                tituloDesplegado = !tituloDesplegado
                pintarTituloHilo()
            }
        }
        threadPageInfo = findViewById(R.id.thread_page_info)
        threadPageInfo.setOnClickListener { if (threadPageCount > 1) showPageJumpSheet() }
        // Sesiones abiertas: sirve para no ofrecer lo de los enlaces el primer día, cuando el
        // usuario todavía no sabe qué es la app. Ver [EnlacesApp].
        shellPrefs.edit().putInt(PREF_SESIONES, shellPrefs.getInt(PREF_SESIONES, 0) + 1).apply()
        noticeBar = findViewById(R.id.notice_bar)
        noticeText = findViewById(R.id.notice_text)
        noticeClose = findViewById(R.id.notice_close)
        noticeDivider = findViewById(R.id.notice_divider)
        pageBar = findViewById(R.id.page_bar)
        pageBarRow = findViewById(R.id.page_bar_row)
        pageBarDivider = findViewById(R.id.page_bar_divider)
        threadFav = findViewById(R.id.thread_fav)
        threadFav.setOnClickListener { toggleFavorite() }
        threadForum = findViewById(R.id.thread_forum)
        threadForum.setOnClickListener { irAlSubforoDelHilo() }
        findViewById<View>(R.id.thread_back).setOnClickListener { goBack() }
        findViewById<View>(R.id.thread_more).setOnClickListener { menuDelHiloAbierto(it) }
        pollBar = findViewById(R.id.poll_bar)
        pollBarText = findViewById(R.id.poll_bar_text)
        pollBarDivider = findViewById(R.id.poll_bar_divider)
        pollBar.setOnClickListener { showPollSheet() }
        nativeHeader = findViewById(R.id.native_header)
        noticesPanel = findViewById(R.id.notices_panel)
        noticesHeader = findViewById(R.id.notices_header)
        noticesList = findViewById(R.id.notices_list)
        noticesLoading = findViewById(R.id.notices_loading)
        noticesEmpty = findViewById(R.id.notices_empty)
        profilePanel = findViewById(R.id.profile_panel)
        profileAvatar = findViewById(R.id.profile_avatar)
        profileName = findViewById(R.id.profile_name)
        profileSub = findViewById(R.id.profile_sub)
        profileFirma = findViewById(R.id.profile_firma)
        profileSobreMi = findViewById(R.id.profile_sobre_mi)
        profileCuentas = findViewById(R.id.profile_cuentas)
        currentForumId = getSharedPreferences(PREFS, MODE_PRIVATE).getInt(PREF_LAST_FID, 2)

        applyWindowInsets()
        configureWebView()
        configureSwipeRefresh()
        configureShell()
        // Atrás del sistema por el dispatcher (API 36 no llama a onBackPressed()).
        onBackPressedDispatcher.addCallback(this, onBackCallback)

        // El WebView arranca OCULTO como motor: al terminar de cargar, extractor.js queda
        // inyectado y pedimos el listado por fetch same-origin (nunca HTTP nativo).
        // Destino centralizado en IntentRouter. En frío el motor aún no está listo, así que
        // hilo/MP se guardan como pendientes y se abren en NATIVO al arrancar; mientras, se
        // muestra la lista nativa. REGLA DE ORO: jamás la capa web.
        val route = IntentRouter.route(urlDelIntent(intent), engineReady = false)
        when (route.target) {
            Target.THREAD, Target.PM_INBOX -> pendingDeepLink = route.url
            Target.HOME -> {}
        }
        showNative()
        nav.root(Screen.ThreadList("home", currentForumId))
        listLoading.visibility = View.VISIBLE
        webView.loadUrl(TrustedOrigins.DEFAULT_URL)

        prepararActualizador()
        maybeShowWelcome()
    }

    /**
     * Popup de bienvenida: se muestra UNA sola vez (primer arranque). Agradece e invita a la
     * comunidad de Telegram. El "café" NO va aquí a propósito (queda en Opciones).
     */
    private fun maybeShowWelcome() {
        val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        if (prefs.getBoolean(PREF_WELCOME_SHOWN, false)) return
        prefs.edit().putBoolean(PREF_WELCOME_SHOWN, true).apply()
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("¡Gracias por confiar en ForoPlus! 👋")
            .setMessage(
                "Soy un desarrollador independiente y hago esta app en mi tiempo libre, sin ánimo " +
                "de lucro. Que la uses ya es la mejor recompensa. Si quieres estar al día y proponer " +
                "mejoras, únete a nuestra comunidad de Telegram."
            )
            .setPositiveButton("Abrir el Telegram") { _, _ ->
                openExternal("https://t.me/foroplus")
            }
            .setNegativeButton("Cerrar", null)
            .show()
    }

    /**
     * Android 15 (targetSdk 35) fuerza dibujar edge-to-edge. Aplicamos los insets como
     * padding del contenedor raíz (la barra inferior queda sobre la de navegación).
     */
    private fun applyWindowInsets() {
        val root = findViewById<View>(R.id.root_container)
        root.setBackgroundColor(color(R.color.fc_fondo))
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            // El teclado (IME) empuja el contenido hacia arriba para que el composer no quede tapado.
            v.setPadding(bars.left, bars.top, bars.right, maxOf(bars.bottom, ime.bottom))
            insets
        }
        // "Light bars" quiere decir barra CLARA, o sea iconos OSCUROS. Estaba fijo a true, así
        // que en modo oscuro el fondo de la barra de estado se iba a oscuro pero el reloj y la
        // batería se seguían pintando en oscuro: **invisibles** (lo reportó un tester el
        // 2026-08-21). Tiene que seguir al modo en el que se está pintando la app.
        val claro = !TemaApp.esOscuro(resources.configuration.uiMode)
        WindowInsetsControllerCompat(window, root).apply {
            isAppearanceLightStatusBars = claro
            isAppearanceLightNavigationBars = claro
        }
    }

    private fun configureWebView() {
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            setSupportZoom(true)
            builtInZoomControls = true
            displayZoomControls = false
            // Respeta el tamaño de letra configurado en el sistema (accesibilidad), sin UI extra.
            textZoom = (this@MainActivity.resources.configuration.fontScale * 100).toInt()
            userAgentString = "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"
        }
        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(webView, false)
        }
        repo = IgnoreListRepository(this)
        keywordRepo = KeywordRepository(this)
        // Filtro de palabras: apagado en instalación nueva, encendido si vienes de una versión
        // anterior (ver KeywordRepository.fijarValorDeFabrica).
        keywordRepo.fijarValorDeFabrica(esInstalacionNueva())
        webView.webViewClient = ForocochesWebViewClient(
            this, repo, keywordRepo,
            onPageLoad = { url ->
                swipeRefresh.isRefreshing = false
                onEnginePageReady(url)
            }
        )
        webView.addJavascriptInterface(SettingsBridge(repo, keywordRepo, webView), "Android")
        webView.addJavascriptInterface(
            ShellBridge(
                onList = { json -> runOnUiThread { onThreadListJson(json) } },
                onError = { reason -> runOnUiThread { onThreadListError(reason) } },
                onForums = { json -> runOnUiThread { onForumListJson(json) } },
                onPopurriResult = { json -> runOnUiThread { onPopurriJson(json) } },
                onThreadData = { json -> runOnUiThread { onThreadJson(json) } },
                onThreadDataError = { reason -> runOnUiThread { onThreadError(reason) } },
                onReply = { json -> runOnUiThread { onReplyResult(json) } },
                onLogin = { json -> runOnUiThread { onLoginResult(json) } },
                onSmiliesData = { json -> runOnUiThread { onSmiliesJson(json) } },
                onNoticesData = { json -> runOnUiThread { onNoticesJson(json) } },
                onNoticeDateResult = { json -> runOnUiThread { onNoticeDateJson(json) } },
                onProfileData = { json -> runOnUiThread { onProfileJson(json) } },
                onLogout = { result -> runOnUiThread { onLogoutDone(result) } },
                onThreadActionData = { json -> runOnUiThread { onThreadActionResult(json) } },
                onEditLoadData = { json -> runOnUiThread { onEditLoad(json) } },
                onPmDataResult = { json -> runOnUiThread { onPmData(json) } },
                onMemberDataResult = { json -> runOnUiThread { onMemberData(json) } },
                onPollVoteResult = { json -> runOnUiThread { onPollResultJson(json) } },
                onSkinResult = { json -> runOnUiThread { onSkinJson(json) } },
                onIgnoreListResult = { json -> runOnUiThread { onIgnoreListJson(json) } },
                onThreadHeadResult = { json -> runOnUiThread { onThreadHeadJson(json) } },
                onQuoteBodyResult = { json -> runOnUiThread { onQuoteBodyJson(json) } },
                onQuienSoyData = { json -> runOnUiThread { onQuienSoy(json) } },
                onThreadDescargaData = { json -> runOnUiThread { onPaginaDescargada(json) } },
                onThreadDescargaErrorData = { r -> runOnUiThread { cancelarDescarga(motivoDescarga(r)) } },
                onLastPosterResult = { json -> runOnUiThread { onLastPosterJson(json) } },
                onUserSuggestionsResult = { json -> runOnUiThread { alLlegarSugerencias?.invoke(json) } }
            ),
            "AndroidShell"
        )
    }

    private fun configureSwipeRefresh() {
        swipeRefresh.setColorSchemeColors(color(R.color.fc_rojo))
        swipeRefresh.setOnChildScrollUpCallback { _, _ -> webView.canScrollVertically(-1) }
        swipeRefresh.setOnRefreshListener { webView.reload() }
    }

    /**
     * Marca leído un hilo abierto SIN pasar por la lista (notificación, cita/mención o
     * deep link): a diferencia del click en la lista, aquí no se conoce el nº de
     * respuestas real (no viene de ninguna fila de forumdisplay), así que se estima con
     * [estimateReadReplies] a partir de la página que ha llegado. La estimación NUNCA
     * sobreestima (ver el razonamiento en esa función): puede quedarse corta si el link
     * aterriza lejos del final del hilo, y ese hilo concreto podría volver a verse en
     * negrita antes de tiempo — coste asumido, es la dirección segura. Lo que NO puede
     * pasar es lo del centinela anterior: quedarse mudo para siempre por sobreestimar.
     */
    private fun markReadUnknownReplies(tid: String, page: Int, pageCount: Int, postsOnPage: Int) =
        readThreads.markRead(tid, estimateReadReplies(page, pageCount, postsOnPage).toString())

    // ── Shell nativo ─────────────────────────────────────────────────────────

    private fun configureShell() {
        adapter = ThreadListAdapter(
            onClick = { item ->
                // Al abrirlo queda leído con el nº de respuestas de ahora: si luego crece,
                // isThreadUnread lo devuelve a negrita.
                readThreads.markRead(item.tid, item.replies)
                openThreadNative(item.url, item.title)
            },
            // Tocar la hora abre el hilo directamente en el ÚLTIMO mensaje. La URL viene en la
            // propia fila (`showthread.php?p=NNN`, de donde sale la hora), así que FC sirve ya
            // la página que contiene ese post y `pendingScrollPid` salta a él: cero peticiones
            // de más y sin depender de goto=lastpost, que FC IGNORA (gotcha 5).
            onLastPost = { item ->
                readThreads.markRead(item.tid, item.replies)
                openThreadNative(item.lastPostUrl, item.title)
            },
            readThreads = readThreads,
            onLongClick = { item -> menuDeHilo(item) }
        )
        adapter.tituloSp = OptionsController.tituloSp(shellPrefs)
        adapter.mostrarUltimo = OptionsController.ultimoEnLista(shellPrefs)
        // El "último que escribe": el autor sale de la caché, y si es alguien a quien ignoras en
        // la app no se enseña su nombre ("" = se sabe, pero no se pinta ni se vuelve a pedir).
        adapter.autorUltimo = { pid ->
            ultimosPosteadores.autor(pid)?.let { if (yaIgnorado(it)) "" else it }
        }
        adapter.pedirUltimo = { pid -> lanzarUltimos(ultimosPosteadores.quiero(pid)) }
        adapter.nombreDeForo = { fid -> Popurri.etiqueta(fid, nombresDeForo) }
        configureThreadPanel()

        // Secciones nativas (Bloque B): citas/menciones y perfil.
        noticeAdapter = NoticeAdapter { n ->
            // Tocar una cita/mención la da por leída: es el gesto que lo dice, no abrir el
            // panel (decisión del dueño, 2026-09-21). En "sus mensajes" y en la búsqueda esto
            // no hace nada, porque ahí no se lleva cuenta de nada (ver claveNoticiasVistas).
            marcarNoticiaLeida(n.url)
            (noticesList.layoutManager as? LinearLayoutManager)?.let { lm ->
                val primera = lm.findFirstVisibleItemPosition()
                if (primera >= 0) {
                    val arriba = lm.findViewByPosition(primera)?.top ?: 0
                    posicionNoticias = Triple(currentNoticesKind, primera, arriba)
                }
            }
            openThreadNative(n.url, n.title, markReadUnknown = true)
        }
        noticesList.layoutManager = LinearLayoutManager(this)
        noticesList.adapter = noticeAdapter
        // Mensajes de un usuario / de una búsqueda: la página siguiente BAJO DEMANDA, como el
        // listado (regla anti-crawler). Desde onScrolled no se toca ninguna vista (gotcha de
        // Android): solo se lanza la petición; lo que llega se pinta en onNotices.
        noticesList.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(rv: RecyclerView, dx: Int, dy: Int) {
                if (dy <= 0 || mensajesCargando || mensajesAgotada || mensajesBase == null) return
                val lm = rv.layoutManager as? LinearLayoutManager ?: return
                if (noticeAdapter.itemCount > 0 && lm.findLastVisibleItemPosition() >= noticeAdapter.itemCount - 6) {
                    pedirMasMensajes()
                }
            }
        })
        contextoBar = findViewById(R.id.contexto_bar)
        descargadosPanel = findViewById(R.id.descargados_panel)
        descargadosTotal = findViewById(R.id.descargados_total)
        descargadosEmpty = findViewById(R.id.descargados_empty)
        descargadosList = findViewById(R.id.descargados_list)
        descargadosAdapter = DescargadosAdapter(
            onAbrir = { d -> abrirCopia(d.tid) },
            onBorrar = { d -> confirmarBorrarCopia(d) }
        )
        descargadosList.layoutManager = LinearLayoutManager(this)
        descargadosList.adapter = descargadosAdapter

        descargaBar = findViewById(R.id.descarga_bar)
        descargaTexto = findViewById(R.id.descarga_texto)
        descargaCancelar = findViewById(R.id.descarga_cancelar)
        descargaProgreso = findViewById(R.id.descarga_progreso)
        descargaCancelar.setOnClickListener { cancelarDescarga("Descarga cancelada") }

        noticesMarkAll = findViewById(R.id.notices_mark_all)
        noticesMarkAll.setOnClickListener { marcarTodasLasNoticiasLeidas() }

        fabNewThread = findViewById(R.id.fab_new_thread)
        fabNewThread.setOnClickListener {
            if (!isLoggedIn()) { showLogin(); return@setOnClickListener }
            openNewThread()
        }

        // Opciones nativas (fuente, avatar, filtros ignorados/keywords).
        optionsPanel = findViewById(R.id.options_panel)
        options = OptionsController(
            panel = optionsPanel,
            ignoreRepo = repo,
            keywordRepo = keywordRepo,
            prefs = getSharedPreferences(PREFS, MODE_PRIVATE),
            onFontChanged = {
                postAdapter.postTextSp = OptionsController.fontSp(shellPrefs)
                applyComposerFont()
            },
            onTitleFontChanged = { adapter.tituloSp = OptionsController.tituloSp(shellPrefs) },
            onUltimoChanged = { adapter.mostrarUltimo = OptionsController.ultimoEnLista(shellPrefs) },
            subforos = { subforosConocidos },
            onPopurriChanged = {
                // Cambia la selección: hay que rehacer las pestañas (el Popurrí aparece o
                // desaparece) y tirar la lista, que ya no es la que corresponde.
                listLoaded = false
                if (listSource == "popurri") adapter.submit(emptyList())
                pintarPestanas()
            },
            onListsChanged = {
                // Re-aplica filtros al vuelo: recarga lista y, si hay, el hilo abierto.
                listLoaded = false
                loadingPage = false
                requestThreadList(1)
                if (currentThreadUrl.isNotEmpty()) reloadCurrentThread()
            },
            onIgnoreWrite = { accion, usuario -> escribirIgnorado(accion, usuario) },
            onTemaChanged = { recreate() },
            onEstiloChanged = { recreate() },
            hilosIgnorados = { hilosIgnorados() },
            onDesignorarHilo = { tid -> designorarHilo(tid) },
            mas18Disponible = { ConfigMas18Parser.de(RemoteConfig.cached(this)) != null },
            onMas18Changed = {
                listLoaded = false
                pintarPestanas()
                recargarListaPorFiltros()
            }
        )
        // Comunidad y apoyo: enlaces EXTERNOS (Telegram / navegador), nunca la capa web.
        optionsPanel.findViewById<View>(R.id.opt_telegram).setOnClickListener {
            openExternal("https://t.me/foroplus")
        }
        optionsPanel.findViewById<View>(R.id.opt_coffee).setOnClickListener {
            openExternal("https://paypal.me/neonforger")
        }
        configurarOrganizar()
        optionsPanel.findViewById<View>(R.id.opt_org_tabs).setOnClickListener { showOrganizar(false) }
        optionsPanel.findViewById<View>(R.id.opt_org_nav).setOnClickListener { showOrganizar(true) }
        optionsPanel.findViewById<View>(R.id.opt_enlaces).setOnClickListener { explicarEnlaces() }
        configurarNovedades()
        postAdapter.postTextSp = OptionsController.fontSp(getSharedPreferences(PREFS, MODE_PRIVATE))
        nativeAvatar = findViewById(R.id.native_avatar)
        nativeAvatarPunto = findViewById(R.id.native_avatar_punto)
        findViewById<View>(R.id.native_cuenta).apply {
            setOnClickListener { if (isLoggedIn()) cuentaPanel.abrir() else showLogin() }
            // Atajo de siempre para cambiar de cuenta (antes estaba en el botón de Perfil).
            setOnLongClickListener { mostrarHojaCuentas(); true }
        }
        configurarPanelCuenta()
        configurarPastillas()
        pintarAvatarCuenta()
        findViewById<View>(R.id.native_search).setOnClickListener { showSearchSheet() }
        // Desde una subpantalla, ← vuelve a las categorías; desde las categorías, cierra.
        findViewById<View>(R.id.options_back).setOnClickListener { if (!options.volver()) hideOptions() }
        configurePmPanels()
        configureMemberPanel()
        findViewById<View>(R.id.profile_logout).setOnClickListener { doLogout() }
        // Único sitio alcanzable con UNA sola cuenta: abre la misma hoja que la pulsación
        // larga y el avatar del composer, que hasta tener dos cuentas están escondidos.
        profileCuentas.setOnClickListener { mostrarHojaCuentas() }
        findViewById<View>(R.id.profile_pms).setOnClickListener { showPmInbox() }
        findViewById<View>(R.id.profile_mis_mensajes).setOnClickListener {
            // El nombre sale de tu propia ficha (onProfile): mientras no ha llegado no se sabe
            // a quién buscar, y buscar "" en FC devuelve la home.
            val yo = profileName.text.toString().trim()
            if (yo.isEmpty()) { toast("Perfil aún cargando…"); return@setOnClickListener }
            showUserActivity(yo, "posts")
        }
        val layoutManager = LinearLayoutManager(this)
        threadList.layoutManager = layoutManager
        threadList.adapter = adapter
        listRefresh.setColorSchemeColors(color(R.color.fc_rojo))
        listRefresh.setOnRefreshListener { requestThreadList(1) }

        // Deslizar hacia abajo en un hilo lo recarga. Se recarga la pagina que se esta
        // LEYENDO (no la 1): el gesto se usa para ver si han contestado, y eso pasa en la
        // pagina en la que estas. Si la peticion no llega a salir (motor no listo, otra
        // pagina en vuelo) hay que apagar el aro a mano, o se queda girando para siempre.
        threadRefresh.setColorSchemeColors(color(R.color.fc_rojo))
        threadRefresh.setOnRefreshListener {
            val antes = loadingThreadPage
            requestThreadPage(threadPage)
            if (antes || !loadingThreadPage) threadRefresh.isRefreshing = false
        }

        // Scroll infinito BAJO DEMANDA (regla anti-crawler: solo se pide la página
        // siguiente cuando el usuario se acerca al final, nunca precarga en bucle).
        threadList.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(rv: RecyclerView, dx: Int, dy: Int) {
                if (dy <= 0 || loadingPage || !listLoaded || listaAgotada) return
                val last = layoutManager.findLastVisibleItemPosition()
                if (adapter.itemCount > 0 && last >= adapter.itemCount - 8) {
                    if (listSource == "mas18") mas18Auto = 0
                    requestThreadList(currentPage + 1)
                }
            }
        })

        configureBottomBar()
        updateAccountNavItem()

        forumTabs.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                if (populatingTabs) return
                val fid = tab.tag as? Int ?: return
                if (fid == TAG_MAS18) { showMas18(); return }
                if (fid == TAG_POPURRI) { showPopurri(); return }
                // Salir del Popurrí o de +18 a un subforo devuelve la lista a su modo normal
                // (al cambiar listSource, el setter apaga la cabecera de +18).
                if (listSource == "popurri" || listSource == "mas18") {
                    listSource = "home"
                    nativeHeader.text = "ForoPlus"
                    pintarBotonTop()
                }
                currentForumId = fid
                getSharedPreferences(PREFS, MODE_PRIVATE).edit().putInt(PREF_LAST_FID, fid).apply()
                listLoaded = false
                adapter.submit(emptyList())
                showNative()
                anotarListaEnPila()
                requestThreadList(1)
            }
            override fun onTabUnselected(tab: TabLayout.Tab) {}
            override fun onTabReselected(tab: TabLayout.Tab) {
                // Tocar Inicio estando en +18/Popurrí deja la pestaña marcada pero la lista es de
                // otra fuente: tocarla de nuevo tiene que ENTRAR en su sección, no recargar la lista
                // que haya (General). Se reparte según la etiqueta de la pestaña.
                when (tab.tag as? Int) {
                    TAG_MAS18 -> if (listSource != "mas18") showMas18() else requestThreadList(1)
                    TAG_POPURRI -> if (listSource != "popurri") showPopurri() else requestThreadList(1)
                    else -> requestThreadList(1)
                }
            }
        })
    }

    /** Pestaña que hay que dejar a la vista en cuanto el listado se pinte. -1 = ninguna. */
    private var encuadrarPestanaEn = -1

    /**
     * Deja la pestaña marcada dentro de la pantalla. No hace nada si la barra no se está
     * viendo: se reintenta desde [showNative], que es cuando vuelve a estar medida.
     */
    private fun encuadrarPestanas() {
        val idx = encuadrarPestanaEn
        if (idx < 0 || forumTabs.visibility != View.VISIBLE || !forumTabs.isShown) return
        encuadrarPestanaEn = -1
        forumTabs.post { forumTabs.setScrollPosition(idx, 0f, false) }
    }

    /** Rellena las pestañas con los subforos reales del índice y marca el actual. */
    private fun onForumListJson(json: String) {
        val forums = parseForumListPayload(json)
        if (forums.isEmpty()) return
        pintarPestanas(forums)
    }

    /** Rehace la barra de subforos. Sin argumento, con los que ya conocíamos. */
    private fun pintarPestanas(lista: List<ForumTab>? = null) {
        var vueltaAInicio = false
        val crudos = lista ?: subforosConocidos.map { ForumTab(it.first, it.second) }
        if (crudos.isEmpty()) return
        // El orden que da el índice de FC pone Ayuda la primera y General la segunda, que no
        // es el que sirve para leer. Ver OrdenSubforos: es un DEFECTO, no una imposición.
        val forums = OrdenSubforos.porDefecto(crudos)
        // El catálogo COMPLETO se recuerda entero, aunque se escondan pestañas: lo necesitan
        // la pantalla de organizar (para poder devolver lo escondido) y la del Popurrí.
        subforosConocidos = forums.map { it.fid to it.name }
        // Los nombres pueden llegar después que el Popurrí (arranque directo en él): sin esto
        // sus filas se quedarían sin miga hasta el siguiente refresco.
        nombresDeForo = subforosConocidos.toMap()
        if (::adapter.isInitialized) adapter.nombresDeForoCambiados()
        // Y encima del defecto va lo que haya elegido el usuario.
        val porFid = forums.associateBy { it.fid.toString() }
        val visibles = OrdenBarra
            .visibles(forums.map { it.fid.toString() }, ordenDeBarra(false), ocultosDeBarra(false))
            .mapNotNull { porFid[it] }
        // Si has escondido el subforo que estabas leyendo no te quedas colgado en una pestaña
        // que ya no existe: se salta al primero que sí se ve. Se decide ANTES de pintar, o el
        // marcado se calcularía con un subforo ausente y la barra acabaría sin nada marcado
        // enseñando los hilos de otro (medido en el dispositivo el 2026-08-28).
        // La sección +18 apagada (Opciones o config remota) estando dentro: no hay pestaña a la
        // que volver, así que la lista pasa a Inicio ANTES de pintar.
        if (listSource == "mas18" && configMas18() == null) {
            listSource = "home"
            nativeHeader.text = "ForoPlus"
            pintarBotonTop()
            listLoaded = false
            adapter.submit(emptyList())
            // Una descarga +18 en vuelo se tirará (otra generación, otra fuente) y no apagaría
            // loadingPage: sin esto la petición de Inicio se descartaría por "ya hay una".
            mas18Gen++
            loadingPage = false
            listLoading.visibility = View.GONE
            listRefresh.isRefreshing = false
            vueltaAInicio = true
        }
        val saltar = listSource == "home" && visibles.isNotEmpty() &&
            visibles.none { it.fid == currentForumId }
        if (saltar) {
            currentForumId = visibles.first().fid
            shellPrefs.edit().putInt(PREF_LAST_FID, currentForumId).apply()
        }
        populatingTabs = true
        forumTabs.removeAllTabs()
        // El Popurrí va el PRIMERO, que es donde lo pidió Márquez ("la ayuda, a la izquierda").
        // Solo aparece si has elegido subforos: una pestaña que al tocarla dice "no has
        // configurado nada" no es un atajo, es un obstáculo.
        var selectIdx = 0
        var desplazamiento = 0
        // +18 va la primera y el Popurrí detrás: el Popurrí queda en desplazamiento - 1.
        if (configMas18() != null) {
            val tab = forumTabs.newTab().setText("+18")
            tab.tag = TAG_MAS18
            forumTabs.addTab(tab, false)
            desplazamiento += 1
        }
        if (popurriFids().isNotEmpty()) {
            val tab = forumTabs.newTab().setText("Popurrí")
            tab.tag = TAG_POPURRI
            forumTabs.addTab(tab, false)
            desplazamiento += 1
        }
        visibles.forEachIndexed { idx, f ->
            val tab = forumTabs.newTab().setText(f.name)
            tab.tag = f.fid
            forumTabs.addTab(tab, false)
            if (f.fid == currentForumId && listSource != "popurri" && listSource != "mas18") selectIdx = idx + desplazamiento
        }
        if (listSource == "mas18") selectIdx = 0
        if (listSource == "popurri" && popurriFids().isNotEmpty()) selectIdx = desplazamiento - 1
        forumTabs.getTabAt(selectIdx)?.select()
        populatingTabs = false
        // La lista se vació arriba (sección +18 apagada estando dentro): se pide la de Inicio. Va
        // aquí y no antes porque requestThreadList no repinta pestañas, así que no hay recursión.
        if (vueltaAInicio) { showNative(); requestThreadList(1) }
        // Y que la pestaña marcada se VEA.
        //
        // `select()` le pide a TabLayout que se desplace hasta ella, pero aquí no sirve: la
        // barra se repinta con su panel ESCONDIDO (se llega desde Opciones → Organizar), y una
        // vista que no está en pantalla no se desplaza. El resultado, medido en el dispositivo
        // el 2026-08-28 al esconder el subforo que se estaba leyendo: la barra se queda parada
        // donde estuviera, sin nada marcado a la vista, enseñando los hilos de otro subforo.
        // Se apunta y se encuadra cuando el listado vuelve a verse; y con `setScrollPosition`,
        // que solo desplaza, y no con otro `select`, que a estas alturas ya navegaría.
        encuadrarPestanaEn = selectIdx
        encuadrarPestanas()

        // La lista se pide DESPUÉS de pintar, no dentro: `populatingTabs` está en alto
        // mientras se construyen las pestañas y pedirla ahí se cruzaría con el select().
        if (saltar) {
            listLoaded = false
            adapter.submit(emptyList())
            requestThreadList(1)
        }
    }

    // ── Hilo nativo (Fase 2) ─────────────────────────────────────────────────

    private fun configureThreadPanel() {
        replyPanel = findViewById(R.id.reply_panel)
        replyInput = findViewById(R.id.reply_input)
        replySend = findViewById(R.id.reply_send)
        replyCancel = findViewById(R.id.reply_cancel)
        replyQuotesContainer = findViewById(R.id.reply_quotes)
        replyHeaderTitle = findViewById(R.id.reply_header_title)
        replyHeaderAccount = findViewById(R.id.reply_header_account)
        replyAvatar = findViewById(R.id.reply_avatar)
        replySubject = findViewById(R.id.reply_subject)
        replySend.setOnClickListener { submitReply() }
        replyCancel.setOnClickListener { hideReply() }

        // Barra de formato BBCode (Bloque A): el editor propio es SIEMPRE el por defecto.
        bbcode = BbcodeEditor(this, replyInput)
        configurarEncuestaNueva()
        SmileysEnCaja(replyInput).also { smileysEnCajas.add(it); replyInput.addTextChangedListener(it) }
        findViewById<View>(R.id.fmt_bold).setOnClickListener { bbcode.wrap("[B]", "[/B]") }
        findViewById<View>(R.id.fmt_italic).setOnClickListener { bbcode.wrap("[I]", "[/I]") }
        findViewById<View>(R.id.fmt_under).setOnClickListener { bbcode.wrap("[U]", "[/U]") }
        findViewById<View>(R.id.fmt_color).setOnClickListener { bbcode.pickColor() }
        findViewById<View>(R.id.fmt_size).setOnClickListener { bbcode.pickSize() }
        findViewById<View>(R.id.fmt_align).setOnClickListener { bbcode.pickAlign() }
        findViewById<View>(R.id.fmt_list).setOnClickListener { bbcode.pickList() }
        findViewById<View>(R.id.fmt_subir).setOnClickListener { elegirFoto() }
        findViewById<View>(R.id.fmt_img).setOnClickListener { bbcode.askImg() }
        findViewById<View>(R.id.fmt_url).setOnClickListener { bbcode.askUrl() }
        findViewById<View>(R.id.fmt_embed).setOnClickListener { bbcode.pickEmbed() }
        findViewById<View>(R.id.fmt_quote).setOnClickListener { bbcode.wrap("[QUOTE]", "[/QUOTE]") }
        findViewById<View>(R.id.fmt_spoiler).setOnClickListener { bbcode.wrap("[SPOILER]", "[/SPOILER]") }
        findViewById<View>(R.id.fmt_smiley).setOnClickListener { openSmileyPicker() }

        postAdapter = PostAdapter(
            onLinkClick = { url -> onPostLinkClick(url) },
            // Citar: añade la cita y abre la pestaña de respuesta.
            onQuote = { post -> quotePost(post) },
            // "+": alterna la cita en la respuesta (sin abrir el panel).
            onMultiquoteToggle = { post -> toggleMultiquote(post) },
            isSelected = { pid -> replyQuotes.containsKey(pid) },
            onMenu = { post, anchor -> showPostMenu(post, anchor) },
            // Nombre/avatar del autor → su perfil nativo (uid real del HTML crudo).
            // El avatar del autor ya lo tenemos del postbit: se pasa como respaldo por si la
            // página de perfil no trae el suyo (así el perfil nunca sale con la foto de otro).
            onAuthorClick = { post ->
                if (post.uid.isNotEmpty()) showMemberProfile(post.uid, post.avatar, conectado = post.conectado)
            },
            onEmbedFullscreen = { view, cb -> onEmbedFullscreen(view, cb) },
            onImageClick = { url -> abrirVisorImagen(url) }
        )
        // Pulgar de scroll de tamaño fijo: ver FixedThumbLayoutManager.
        val lm = FixedThumbLayoutManager(this)
        postList.layoutManager = lm
        postList.adapter = postAdapter
        // Barra de desplazamiento arrastrable (la de Android era solo un indicador).
        findViewById<BarraDesplazamiento>(R.id.post_scrollbar).enlazar(postList)
        // Reproductor flotante: el vídeo que estás viendo se muda aquí cuando su mensaje se
        // recicla, y así sigue sonando (ver MiniReproductor).
        postAdapter.aparcadero = MiniReproductor(
            findViewById(R.id.mini_reproductor),
            findViewById(R.id.mini_hueco),
            findViewById(R.id.mini_cerrar)
        )
        // PAGINACIÓN CLÁSICA: nada se carga solo. Ni la página siguiente al llegar al final
        // (scroll infinito, retirado a petición de los testers: se hacía interminable y no se
        // sabía por dónde ibas) ni la anterior al subir. De página se cambia con la barra de
        // arriba. Aquí solo se mantiene el indicador al día.
        postList.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(rv: RecyclerView, dx: Int, dy: Int) {
                if (postAdapter.itemCount == 0) return
                // Solo lee y anota en la pila: no toca ninguna vista.
                rememberThreadPosition()
                // Y lo que SÍ toca vistas se aplaza al frame siguiente. Ver pintarIndicadores.
                postList.removeCallbacks(pintarIndicadores)
                postList.post(pintarIndicadores)
            }
        })
        quickReplyBar = findViewById(R.id.quick_reply_bar)
        quickInput = findViewById(R.id.quick_input)
        SmileysEnCaja(quickInput).also { smileysEnCajas.add(it); quickInput.addTextChangedListener(it) }
        quickAvatar = findViewById(R.id.quick_avatar)
        // Con una sola cuenta no hay nada que confundir: se manda directo a la hoja solo
        // tiene sentido si hay más de una entre las que elegir.
        quickAvatar.setOnClickListener { if (cuentasGuardadas().size > 1) mostrarHojaCuentas() }
        findViewById<View>(R.id.quick_send).setOnClickListener { submitQuickReply() }
        findViewById<View>(R.id.quick_expand).setOnClickListener { expandQuickReply() }
        restrictedView = findViewById(R.id.thread_restricted)
        restrictedMsg = findViewById(R.id.restricted_msg)
        restrictedMeta = findViewById(R.id.restricted_meta)
        restrictedLogin = findViewById(R.id.restricted_login)
        restrictedInvite = findViewById(R.id.restricted_invite)
        restrictedLogin.setOnClickListener { showLogin() }
        restrictedInvite.setOnClickListener {
            // Guía de invitaciones: fuera de la app (el foro no se ve dentro).
            if (restrictedInviteUrl.isNotEmpty()) {
                try {
                    startActivity(
                        android.content.Intent(
                            android.content.Intent.ACTION_VIEW,
                            android.net.Uri.parse(restrictedInviteUrl)
                        ).addCategory(android.content.Intent.CATEGORY_BROWSABLE)
                    )
                } catch (_: Exception) { }
            }
        }
        configureLoginPanel()
    }

    /** Hilo restringido: reproduce la info de FC con nuestros estilos + acceso al login. */
    private fun showRestricted(msg: String, meta: String, inviteUrl: String) {
        if (!isLoggedIn()) {
            // El login (botón o pestaña) reabrirá este hilo al entrar.
            pendingThreadUrl = currentThreadUrl
            pendingThreadTitle = threadTitle.text.toString()
        }
        restrictedMsg.text = msg.ifEmpty { "Este hilo no está disponible con tu cuenta actual" }
        restrictedMeta.text = meta
        restrictedMeta.visibility = if (meta.isEmpty()) View.GONE else View.VISIBLE
        restrictedLogin.visibility = if (isLoggedIn()) View.GONE else View.VISIBLE
        restrictedInviteUrl = inviteUrl
        restrictedInvite.visibility = if (inviteUrl.isEmpty()) View.GONE else View.VISIBLE
        restrictedView.visibility = View.VISIBLE
        updateQuickReplyVisibility()
    }

    /** Indicador "Página X de Y" de la cabecera; con varias páginas es el botón de salto. */
    private fun showThreadPageInfo(visiblePage: Int) {
        val multi = threadPageCount > 1
        // Pastilla "Página X de Y ▾" (el ▾ es su drawable); con una sola página no hay nada que
        // elegir y no se enseña.
        threadPageInfo.text = if (multi) "Página $visiblePage de $threadPageCount" else ""
        threadPageInfo.isClickable = multi
        threadPageInfo.contentDescription = if (multi) "Página $visiblePage de $threadPageCount. Elegir página" else null
        threadPageInfo.visibility = if (multi) View.VISIBLE else View.GONE
    }

    /**
     * Barra de páginas, estilo foro clásico: `« ‹ 1 2 [3] 4 5 › »`. **Siempre visible** en
     * hilos de más de una página y **al fondo**, donde acabas de leer y donde cae el pulgar.
     * Ya no hay scroll infinito: de página se cambia aquí y punto.
     */

    /**
     * Título de la cabecera del hilo: dos líneas con "…", o entero si el usuario lo despliega.
     *
     * Se queda corto a propósito. La cabecera se pinta en TODOS los hilos, así que dejarla
     * crecer de fijo le quita alto de lectura a los miles de hilos con título normal para
     * arreglar los pocos que lo tienen largo — el mismo motivo por el que no hay una barra
     * más arriba.
     */
    /**
     * La miga del subforo. Sin dato (cargando, o una copia guardada antes de que existiera)
     * se queda vacía y sin tocar, pero la fila sigue ahí: lleva la estrella y el ⋮.
     */
    private fun pintarMigaHilo() {
        val hay = threadForumFid > 0 && threadForumName.isNotEmpty()
        threadForum.text = if (hay) threadForumName else ""
        threadForum.setCompoundDrawablesRelativeWithIntrinsicBounds(
            0, 0, if (hay) R.drawable.ic_miga else 0, 0
        )
        threadForum.isClickable = hay
        threadForum.contentDescription = if (hay) "Ir al subforo $threadForumName" else null
        // Chip del subforo (fase 2): sin dato no hay chip; la fila sigue llevando atrás, ★ y ⋮.
        threadForum.background = if (hay) ContextCompat.getDrawable(this, R.drawable.bg_chip_subforo) else null
        threadForum.visibility = if (hay) View.VISIBLE else View.GONE
    }

    /**
     * Tocar la miga es "subir" al subforo, como el Up de Android: se deja la lista de ese
     * subforo como raíz, no se apila encima del hilo. Si no, el atrás desde el subforo te
     * devolvería al hilo del que acabas de subir.
     */
    private fun irAlSubforoDelHilo() {
        val fid = threadForumFid
        if (fid <= 0) return
        dejarElHilo()
        nav.root(Screen.ThreadList("home", fid))
        openForumNative(fid)
    }

    /** El ⋮ de la cabecera: lo que se usa de vez en cuando. */
    private fun menuDelHiloAbierto(ancla: View) {
        val menu = androidx.appcompat.widget.PopupMenu(this, ancla)
        menu.menu.add(0, 1, 1, "Descargar este hilo")
        menu.menu.add(0, 2, 2, "Compartir hilo")
        menu.setOnMenuItemClickListener { mi ->
            when (mi.itemId) {
                1 -> { descargarHiloActual(); true }
                2 -> { compartirHilo(); true }
                else -> false
            }
        }
        menu.show()
    }

    private fun pintarTituloHilo() {
        threadTitle.maxLines = if (tituloDesplegado) 8 else 2
        threadTitle.ellipsize =
            if (tituloDesplegado) null else android.text.TextUtils.TruncateAt.END
    }

    /** ¿El título no cabe y FC nos lo está cortando con "…"? */
    private fun tituloCortado(): Boolean {
        val l = threadTitle.layout ?: return false
        return (0 until l.lineCount).any { l.getEllipsisCount(it) > 0 }
    }

    private fun updatePageBar() {
        val on = threadPageCount > 1 && threadPanel.visibility == View.VISIBLE
        if (!on) {
            pageBar.visibility = View.GONE
            pageBarDivider.visibility = View.GONE
            pageBarState = null
            return
        }
        val lm = postList.layoutManager as LinearLayoutManager

        // Misma fuente que la cabecera (showThreadPageInfo): la página VISIBLE, no
        // threadPage (última página CARGADA). Sin esto los dos indicadores se
        // contradicen tras responder desde una página intermedia (la respuesta añade
        // contenido por debajo, en threadPageCount, sin mover lo que se está viendo).
        val visiblePage = postAdapter.pageAt(lm.findFirstVisibleItemPosition().coerceAtLeast(0))
        val state = visiblePage to threadPageCount
        // Evita reconstruir los chips en CADA frame de scroll durante un fling: si la
        // barra ya está pintada y nada de lo que determina los chips cambió, no se toca
        // (un tap que cae mientras la lista se asienta no se pierde bajo un chip
        // recién sustituido bajo el dedo).
        if (pageBar.visibility == View.VISIBLE && pageBarState == state) {
            return
        }
        pageBarState = state

        pageBar.visibility = View.VISIBLE
        pageBarDivider.visibility = View.VISIBLE
        pageBarRow.removeAllViews()

        /**
         * @param current es la página en la que estás (fondo rojo, no se pulsa)
         * @param enabled hay a dónde ir (las flechas de los extremos quedan apagadas, pero
         *   NO en rojo: apagado y "estás aquí" son cosas distintas)
         */
        fun chip(label: String, page: Int, current: Boolean, enabled: Boolean = true, alFinal: Boolean = false) {
            val tv = TextView(this)
            tv.text = label
            tv.textSize = 14f
            // Alto contenido: la barra vive pegada a la respuesta rápida y a la navegación, y
            // tres franjas gordas seguidas cargan mucho el fondo. Se recorta a lo alto, no a lo
            // ancho: el ancho es lo que hace que un número no se falle con el dedo.
            tv.setPadding(dp(14), dp(6), dp(14), dp(6))
            tv.setTextColor(
                when {
                    current -> color(R.color.fc_sobre_rojo)
                    !enabled -> color(R.color.fc_texto_off)
                    else -> color(R.color.fc_texto_2)
                }
            )
            // Compacta: rectángulo rojo; Tarjetas: pastilla roja (fcPaginaActual).
            tv.background = if (current) AtributosTema.drawable(this, R.attr.fcPaginaActual) else null
            if (current) tv.setTypeface(null, android.graphics.Typeface.BOLD)
            val pulsable = !current && enabled
            tv.isClickable = pulsable
            if (pulsable) tv.setOnClickListener { jumpToThreadPage(page, alFinal) }
            pageBarRow.addView(tv)
        }

        // Primera y última siempre a la vista (« »), que es lo que uno busca en un hilo largo;
        // los números solo alcanzan ±2. Se pintan apagadas en los extremos en vez de
        // desaparecer, para que la barra no baile de ancho al cambiar de página.
        val hayAnterior = visiblePage > 1
        val haySiguiente = visiblePage < threadPageCount
        chip("«", 1, current = false, enabled = hayAnterior)
        chip("‹", visiblePage - 1, current = false, enabled = hayAnterior)
        for (p in pageWindow(visiblePage, threadPageCount, pageSpan(threadPageCount)))
            chip("$p", p, current = p == visiblePage)
        chip("›", visiblePage + 1, current = false, enabled = haySiguiente)
        // El » aterriza en el ÚLTIMO MENSAJE, no arriba de la última página: quien pulsa "al
        // final" quiere lo nuevo, y caer arriba obliga a bajar por lo que ya ha leído (lo pidió
        // Alfa). Los números NO: ir a la página 14 es ir a su principio, o te saltarías mensajes.
        chip("»", threadPageCount, current = false, enabled = haySiguiente, alFinal = true)
    }

    // ── Panel de respuesta nativo (Fase 3) ───────────────────────────────────

    /**
     * El bloque que se envía por cada cita.
     *
     * Orden de preferencia, y el porqué:
     * 1. **Lo que el usuario recortó**, si tocó la tarjeta: manda su decisión.
     * 2. **La cita que escribió FC**, tal cual. Trae fotos, smileys y formato; construirla
     *    nosotros los perdía todos (ver [CitaBbcode]).
     * 3. El texto plano de siempre, si la petición a FC no llegó. Peor, pero nunca nada.
     */
    private fun quoteBlock(p: PostItem): String {
        replyQuoteEdits[p.pid]?.let { return envolver(p, it) }
        citasBbcode[p.pid]?.takeIf { it.isNotBlank() }?.let { return it.trimEnd() + SALTO }
        return envolver(p, postAdapter.quoteBodyOf(p))
    }

    private fun envolver(p: PostItem, cuerpo: String) =
        "[QUOTE=" + p.author + ";" + p.pid + "]" + cuerpo + "[/QUOTE]" + SALTO

    /** Texto de una cita para ENSEÑARLA (tarjeta del composer y diálogo de recortar). */
    private fun cuerpoDeCita(p: PostItem): String =
        replyQuoteEdits[p.pid]
            ?: citasBbcode[p.pid]?.takeIf { it.isNotBlank() }
                ?.let { CitaBbcode.previsualizacion(it) }
            ?: postAdapter.quoteBodyOf(p)

    /** Le pide a FC la cita de verdad. Llega por [onQuoteBodyJson]. */
    private fun pedirCitaReal(pid: String) {
        if (citasBbcode.containsKey(pid)) return
        webView.evaluateJavascript("window.fcLoadQuote&&fcLoadQuote('" + jsEscape(pid) + "')", null)
    }

    private fun onQuoteBodyJson(json: String) {
        val o = try { org.json.JSONObject(json) } catch (_: Exception) { return }
        val pid = o.optString("pid")
        if (pid.isEmpty()) return
        citasBbcode[pid] = o.optString("bbcode")
        // La tarjeta se pintó con el texto plano mientras llegaba: ahora se repinta con la
        // cita buena (y con el 🖼 donde haya fotos).
        if (isReplyVisible && replyQuotes.containsKey(pid)) renderReplyQuotes()
    }

    /**
     * Abre una cita pendiente para recortarla. El diálogo de Android ya oscurece el fondo y se
     * cierra al tocar fuera, así que el "popup semiopaco" no hay que construirlo.
     *
     * OJO con lo que se edita: es TEXTO PLANO. `quoteBodyOf` ya descarta hoy el formato y las
     * citas anidadas del mensaje original, así que aquí no se pierde nada nuevo — pero lo que
     * ves en el recuadro no es exactamente lo que se leía en el hilo.
     */
    private fun editarCita(p: PostItem) {
        val caja = EditText(this)
        // Lo que se enseña es la vista previa legible; si vuelve IGUAL, no se registra edición
        // ninguna y se envía la cita que escribió FC, con sus fotos. Ver [CitaBbcode].
        val original = cuerpoDeCita(p)
        caja.setText(original)
        caja.setTextColor(color(R.color.fc_texto))
        caja.setBackgroundColor(0x00000000)
        caja.textSize = 14f
        caja.setPadding(dp(20), dp(12), dp(20), dp(12))
        caja.setSelection(caja.text.length)
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(if (p.author.isNotEmpty()) "Cita de @${p.author}" else "Cita")
            .setView(caja)
            .setPositiveButton("Guardar") { _, _ ->
                val texto = caja.text.toString().trim()
                // Vaciar la cita entera equivale a quitarla: dejar un [QUOTE] vacío en el
                // mensaje no le sirve a nadie.
                if (texto.isEmpty()) {
                    replyQuotes.remove(p.pid)
                    replyQuoteEdits.remove(p.pid)
                    postAdapter.refreshSelection()
                } else if (texto == original.trim()) {
                    // No lo ha tocado: se queda la cita buena, no una copia en texto plano.
                    replyQuoteEdits.remove(p.pid)
                } else {
                    replyQuoteEdits[p.pid] = CitaBbcode.sinMarcas(texto)
                }
                renderReplyQuotes()
            }
            .setNegativeButton("Cancelar", null)
            // Restaurar solo tiene sentido si de verdad hay algo que deshacer.
            .apply {
                if (replyQuoteEdits.containsKey(p.pid)) {
                    setNeutralButton("Original") { _, _ ->
                        replyQuoteEdits.remove(p.pid)
                        renderReplyQuotes()
                    }
                }
            }
            .show()
    }

    /**
     * Tira el borrador y las citas pendientes. Se llama SOLO al cambiar de hilo de verdad:
     * una respuesta se publica en un hilo concreto, así que arrastrar citas de otro no tendría
     * sentido — pero volver al mismo hilo sí debe conservarlas.
     */
    private fun descartarBorrador() {
        replyInput.setText("")
        replyQuotes.clear()
        replyQuoteEdits.clear()
        citasProvisionales.clear()
        citasBbcode.clear()
        quotesThreadTid = ""
        postAdapter.refreshSelection()
    }

    /** "Citar" en un post: lo añade a la respuesta y abre la pestaña de respuesta. */
    /**
     * Selector del sistema para elegir una foto. **No pide permisos**: el contenido llega por
     * `content://` y solo se puede leer el fichero que la persona haya elegido.
     */
    private val pedirFoto = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.GetContent()
    ) { uri -> if (uri != null) subirFoto(uri) }

    private fun elegirFoto() {
        try {
            pedirFoto.launch("image/*")
        } catch (_: Exception) {
            toast("No hay ninguna aplicación para elegir fotos")
        }
    }

    /**
     * Sube la foto y pega su `[IMG]` donde estuviera el cursor.
     *
     * Se avisa al empezar porque **puede tardar segundos** (medido: 3,2 s una foto de móvil ya
     * reducida, por wifi), y quedarse sin señal ninguna mientras sube invita a darle otra vez.
     */
    private fun subirFoto(uri: android.net.Uri) {
        toast("Subiendo foto…")
        val host = SubidaImagen.host(RemoteConfig.cached(this))
        SubidorFotos.subir(this, uri, host) { enlace, error ->
            if (enlace.isNotEmpty()) {
                bbcode.insert(SubidaImagen.bbcode(enlace))
                toast("Foto añadida")
            } else {
                toast(error.ifEmpty { "No se pudo subir la foto" })
            }
        }
    }

    private fun quotePost(post: PostItem) {
        if (!isLoggedIn()) { showLogin(); return }
        // "Citar" es "responde a ESTE": si sales sin enviar, no se queda pegada (ver
        // [CitasPendientes]). Lo que ya estuviera marcado con ＋ no se toca.
        citasProvisionales.clear()
        citasProvisionales.addAll(
            CitasPendientes.alCitar(replyQuotes.keys, citasProvisionales, post.pid)
        )
        replyQuotes[post.pid] = post
        quotesThreadTid = currentThreadTid
        pedirCitaReal(post.pid)
        postAdapter.refreshSelection()
        openReply()
    }

    /** "+" en un post: alterna su cita en la respuesta en curso (sin abrir el panel). */
    private fun toggleMultiquote(post: PostItem) {
        val added = if (replyQuotes.containsKey(post.pid)) {
            replyQuotes.remove(post.pid); replyQuoteEdits.remove(post.pid); false
        } else {
            replyQuotes[post.pid] = post; quotesThreadTid = currentThreadTid
            pedirCitaReal(post.pid); true
        }
        postAdapter.refreshSelection()
        if (isReplyVisible) renderReplyQuotes()
        if (added) {
            val n = replyQuotes.size
            toast(if (n > 1) "Cita añadida ($n)" else "Cita añadida")
        }
    }

    /**
     * Aplica el tamaño de letra de Opciones a la caja de escribir. El layout la trae
     * clavada a 15sp y por eso el ajuste de fuente no se notaba al redactar.
     * Solo el cuerpo: el asunto del hilo nuevo mantiene su tamaño.
     */
    private fun applyComposerFont() {
        replyInput.textSize = OptionsController.fontSp(shellPrefs)
    }

    /** Abre la pantalla de escritura en el modo dado (reply | edit | newthread). */
    private fun showComposer(mode: String, title: String, showSubject: Boolean) {
        replyMode = mode
        isReplyVisible = true
        hidePmPanels()
        replyPanel.visibility = View.VISIBLE
        threadPanel.visibility = View.GONE
        nativePanel.visibility = View.GONE
        // Pantalla de escritura = sin barra de navegación: el teclado ocupa su hueco
        // y se escribe cómodo (la barra no "sube" con el teclado).
        bottomNav.visibility = View.GONE
        replyHeaderTitle.text = title
        pintarCuentaComposer()
        replySubject.visibility = if (showSubject) View.VISIBLE else View.GONE
        pollBox.visibility = if (mode == "newthread") View.VISIBLE else View.GONE
        applyComposerFont()
        renderReplyQuotes()
        val focus = if (showSubject) replySubject else replyInput
        focus.requestFocus()
        val imm = getSystemService(INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
        imm.showSoftInput(focus, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
    }

    private fun openReply() = showComposer("reply", "Responder", showSubject = false)

    // ── Respuesta rápida (barra del fondo del hilo) ───────────────────────────

    /**
     * Envía lo escrito en la barra rápida. Reutiliza submitReplyPost() sin tocarlo: se
     * vuelca el texto en replyInput, que es de donde lee. Así el POST es EXACTAMENTE el
     * mismo que el del composer completo (fcSubmitReply con el token del form real).
     */
    private fun submitQuickReply() {
        if (sendingReply) return
        if (!isLoggedIn()) { showLogin(); return }
        // Con citas pendientes (Citar/Multicitar), la barra rápida NO las enseña — publicar
        // directo mandaría bloques de cita que el usuario nunca vio, en su cuenta real. Se
        // manda al composer grande, donde sí aparecen las tarjetas, y decide él (ruling del
        // dueño: las citas siempre pasan por el composer completo).
        if (replyQuotes.isNotEmpty()) { expandQuickReply(); return }
        val body = quickInput.text.toString().trim()
        if (body.isEmpty()) { toast("Escribe algo antes de enviar"); return }
        replyMode = "reply"
        quickSendPending = true
        replyInput.setText(body)
        submitReplyPost()
    }

    /**
     * ⤢: abre el composer completo llevándose el texto y la posición del cursor.
     *
     * La concatenación con un borrador ya presente en replyInput SOLO se aplica con
     * replyQuotes.isNotEmpty() — es el único caso para el que se diseñó (citas pendientes:
     * hideReply() preserva el borrador en replyInput a propósito en vez de bajarlo a la
     * barra rápida, precisamente porque su guarda exige replyQuotes.isEmpty()). Sin citas
     * pendientes, replyInput solo puede tener texto sobrante por otro motivo: el mismo
     * hideReply() NO vacía replyInput fuera de replyMode == "reply", así que el cuerpo
     * completo de un mensaje que se estaba EDITANDO (modo "edit") se queda ahí tal cual al
     * salir sin guardar. Concatenar en ese caso prependería el mensaje ajeno editado a la
     * respuesta nueva — el mismo tipo de mezcla de texto que este fix intentaba evitar,
     * solo que con otro origen. Por eso fuera del caso de citas se mantiene el
     * comportamiento de siempre: pisar.
     */
    private fun expandQuickReply() {
        if (!isLoggedIn()) { showLogin(); return }
        val quick = quickInput.text.toString()
        if (replyQuotes.isNotEmpty()) {
            val existing = replyInput.text.toString()
            val combined = when {
                existing.isEmpty() -> quick
                quick.isEmpty() -> existing
                else -> "$existing\n$quick"
            }
            replyInput.setText(combined)
            replyInput.setSelection(combined.length)
        } else {
            val cursor = quickInput.selectionStart.coerceIn(0, quick.length)
            replyInput.setText(quick)
            replyInput.setSelection(cursor)
        }
        quickInput.setText("")
        openReply()
    }

    /**
     * La barra solo tiene sentido leyendo un hilo, con sesión y si el hilo no está
     * restringido (+HD). En cualquier otro panel se oculta.
     */
    private fun updateQuickReplyVisibility() {
        val show = threadPanel.visibility == View.VISIBLE &&
            isLoggedIn() &&
            currentThreadTid.isNotEmpty() &&
            restrictedView.visibility != View.VISIBLE
        quickReplyBar.visibility = if (show) View.VISIBLE else View.GONE
        if (show) {
            quickInput.textSize = OptionsController.fontSp(shellPrefs)
            // Se llama aquí (y no solo desde showThread()) porque esta es la función que de
            // verdad decide cuándo la barra —y por tanto el avatar— se hace visible: cubre
            // también volver de guardar una respuesta o de refrescar la lista sin pasar de
            // nuevo por showThread().
            pintarAvatarComposer()
        }
    }

    /** FAB: nuevo hilo en el subforo actual (asunto + editor). */
    private fun openNewThread() {
        replyQuotes.clear()
        replyQuoteEdits.clear()
        citasProvisionales.clear()
        citasBbcode.clear()
        postAdapter.refreshSelection()
        replyInput.setText("")
        replySubject.setText("")
        editingPid = ""
        showComposer("newthread", "Nuevo hilo", showSubject = true)
    }

    /** Menú ⋮ de un post: editar/borrar si es tuyo, reportar si es ajeno, y compartir siempre. */
    /**
     * Copia el mensaje entero al portapapeles.
     *
     * Seleccionar un trozo se hace **directamente sobre el mensaje** con una pulsación larga
     * (ver [SeleccionConEnlaces]); esto es solo el atajo para llevárselo todo sin ir
     * arrastrando tiradores. Se copia el texto PLANO: sin citas anidadas y sin las cajitas de
     * imagen (gotcha 21), que es lo que cualquiera espera pegar.
     */
    private fun copiarMensaje(post: PostItem) {
        val texto = PostAdapter.textoDeHtml(post.html)
        if (texto.isBlank()) { toast("Este mensaje no tiene texto que copiar"); return }
        val cb = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
        cb.setPrimaryClip(android.content.ClipData.newPlainText("mensaje", texto))
        toast("Mensaje copiado")
    }

    private fun showPostMenu(post: PostItem, anchor: View) {
        val menu = androidx.appcompat.widget.PopupMenu(this, anchor)
        if (post.own) {
            menu.menu.add(0, 1, 0, "Editar")
            menu.menu.add(0, 2, 1, "Borrar")
        } else {
            menu.menu.add(0, 3, 0, "Reportar")
            // Ignorar es la razón por la que mucha gente se instala esto, y hasta ahora
            // exigía irse a Opciones y teclear el nombre a mano. Solo con sesión: sin ella
            // no se puede escribir en la lista de la cuenta.
            if (post.author.isNotEmpty() && isLoggedIn()) {
                menu.menu.add(0, 5, 1, "Ignorar a ${post.author}")
            }
        }
        // Compartir vale para cualquier mensaje, propio o ajeno: va el último para no mover
        // de sitio las opciones que la gente ya tiene aprendidas.
        menu.menu.add(0, 7, 7, "Ver solo sus mensajes")
        menu.menu.add(0, 6, 8, "Copiar mensaje")
        menu.menu.add(0, 4, 9, "Compartir")
        menu.setOnMenuItemClickListener { mi ->
            when (mi.itemId) {
                1 -> { startEdit(post); true }
                2 -> { confirmDelete(post); true }
                3 -> { reporte.mostrarDialogo(post); true }
                4 -> { compartirMensaje(post); true }
                5 -> { confirmarIgnorar(post.author); true }
                6 -> { copiarMensaje(post); true }
                7 -> { mostrarSusMensajesEnHilo(post.author, currentThreadTid); true }
                else -> false
            }
        }
        menu.show()
    }

    /**
     * Pide confirmación antes de ignorar. No es un gesto destructivo, pero **escribe en la
     * cuenta de ForoCoches** (no solo en la app), así que se dice explícitamente: quien lo
     * pulse sin querer desde un menú se llevaría una sorpresa al abrir el foro en el navegador.
     */
    private fun confirmarIgnorar(autor: String) {
        if (autor.isEmpty()) return
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Ignorar a $autor")
            .setMessage("No verás sus mensajes ni sus hilos. Se añade a tu lista de ignorados " +
                "de ForoCoches, así que también le afecta en la web. Puedes deshacerlo desde " +
                "Opciones.")
            .setPositiveButton("Ignorar") { _, _ -> escribirIgnorado("add", autor) }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    /**
     * Reportar un mensaje (diálogo + envío tras el Cloudflare): vive en [ReporteController].
     * Perezoso: el motor ya existe cuando alguien lo usa.
     */
    private val reporte by lazy {
        ReporteController(
            activity = this,
            userAgent = { webView.settings.userAgentString },
            cambiandoDeCuenta = { cambiandoDeCuenta },
            toast = ::toast,
            escaparJs = ::jsEscape
        )
    }

    // Lo comparten la verificación del login y otras esperas en el hilo principal.
    private val reportHandler by lazy { android.os.Handler(mainLooper) }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun startEdit(post: PostItem) {
        editingPid = post.pid
        replyQuotes.clear()
        replyQuoteEdits.clear()
        citasProvisionales.clear()
        citasBbcode.clear()
        replyInput.setText("")   // se rellena al cargar el BBCode real
        replySubject.setText("")
        showComposer("edit", "Editar mensaje", showSubject = false)
        replySend.isEnabled = false
        replySend.text = "…"
        webView.evaluateJavascript("window.fcLoadPostForEdit&&fcLoadPostForEdit('${jsEscape(post.pid)}')", null)
    }

    private fun onEditLoad(json: String) {
        replySend.isEnabled = true
        replySend.text = "Guardar"
        try {
            val o = org.json.JSONObject(json)
            if (!o.optBoolean("ok", false)) {
                toast("No se pudo cargar el mensaje")
                hideReply()
                return
            }
            if (o.optString("pid") != editingPid) return
            replyInput.setText(o.optString("message"))
            replyInput.setSelection(replyInput.text.length)
            if (o.optBoolean("hasSubject", false)) {
                replySubject.setText(o.optString("subject"))
                replySubject.visibility = View.VISIBLE
            }
        } catch (_: Exception) { }
    }

    private fun confirmDelete(post: PostItem) {
        // Borrar es irreversible: mejor no ni ofrecer el diálogo mientras las cookies están a
        // medio cambiar que dejar que el usuario confirme y descubrir después con qué cuenta
        // salió el borrado.
        if (cambiandoDeCuenta) { toast("Espera a que termine el cambio de cuenta"); return }
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Borrar mensaje")
            .setMessage("¿Seguro que quieres borrar este mensaje? No se puede deshacer.")
            .setPositiveButton("Borrar") { _, _ ->
                toast("Borrando…")
                webView.evaluateJavascript("window.fcDeletePost&&fcDeletePost('${jsEscape(post.pid)}')", null)
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun hideReply() {
        isReplyVisible = false
        // Salir sin enviar tira las citas que abrió "Citar" y respeta las del ＋.
        if (citasProvisionales.isNotEmpty()) {
            val sobreviven = CitasPendientes.alCerrarSinEnviar(replyQuotes.keys, citasProvisionales)
            replyQuotes.keys.retainAll(sobreviven)
            replyQuoteEdits.keys.retainAll(sobreviven)
            citasProvisionales.clear()
            postAdapter.refreshSelection()
        }
        val imm = getSystemService(INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
        imm.hideSoftInputFromWindow(replyInput.windowToken, 0)
        replyInput.clearFocus()
        replySubject.clearFocus()
        replyPanel.visibility = View.GONE
        bottomNav.visibility = View.VISIBLE
        // Nuevo hilo se abrió desde la lista; responder/editar desde el hilo.
        if (replyMode == "newthread") {
            nativePanel.visibility = View.VISIBLE
        } else {
            threadPanel.visibility = View.VISIBLE
        }
        // Si vuelves del composer sin enviar, el texto regresa a la barra rápida en vez
        // de perderse (lo pediste explícitamente al diseñar el ⤢).
        if (replyMode == "reply" && replyInput.text.isNotEmpty() && replyQuotes.isEmpty()) {
            val body = replyInput.text.toString()
            val cursor = replyInput.selectionStart.coerceIn(0, body.length)
            quickInput.setText(body)
            quickInput.setSelection(cursor)
            replyInput.setText("")
        }
        replyMode = "reply"
        updateQuickReplyVisibility()
    }

    /** Reconstruye las tarjetas de cita del panel a partir de replyQuotes. */
    private fun renderReplyQuotes() {
        replyQuotesContainer.removeAllViews()
        val inflater = layoutInflater
        // Las citas como TEXTO, para recortarlas con formato o ponerlas donde quieras (ver
        // CitaBbcode.aTexto). Todas a la vez: una sola dejaría el orden al revés al publicar.
        if (replyQuotes.isNotEmpty()) {
            replyQuotesContainer.addView(TextView(this).apply {
                text = if (replyQuotes.size == 1) "✎ Editar la cita como texto" else "✎ Editar las citas como texto"
                textSize = 14f
                setTextColor(color(R.color.fc_rojo))
                setPadding(0, dp(2), dp(12), dp(8))
                setOnClickListener { citasATexto() }
            })
        }
        for (post in replyQuotes.values.toList()) {
            val card = inflater.inflate(R.layout.item_reply_quote, replyQuotesContainer, false)
            card.findViewById<TextView>(R.id.quote_author).text =
                if (post.author.isNotEmpty()) "@${post.author}" else "(anónimo)"
            val editada = replyQuoteEdits.containsKey(post.pid)
            val preview = cuerpoDeCita(post).replace(Regex("\\s+"), " ").trim()
            card.findViewById<TextView>(R.id.quote_preview).text =
                if (editada) "✎ $preview" else preview
            // Tocar la tarjeta abre la cita para recortarla: citar entero un mensaje largo
            // para responder a una sola línea es lo que hace todo el mundo en un foro.
            card.setOnClickListener { editarCita(post) }
            card.findViewById<View>(R.id.quote_remove).setOnClickListener {
                replyQuotes.remove(post.pid)
                replyQuoteEdits.remove(post.pid)
                postAdapter.refreshSelection()
                renderReplyQuotes()
            }
            replyQuotesContainer.addView(card)
        }
    }

    /**
     * Las tarjetas de cita pasan a la caja como BBCode (`[QUOTE=autor;pid]…[/QUOTE]`) y dejan de
     * ser tarjetas: a partir de ahí es texto normal, con la barra de formato y todo. Se usa la
     * cita que escribió FC si ya llegó (fotos, smileys y formato intactos), igual que al enviar.
     */
    private fun citasATexto() {
        if (replyQuotes.isEmpty()) return
        val bloques = replyQuotes.values.map { quoteBlock(it) }
        val (nuevo, cursor) = CitaBbcode.aTexto(bloques, replyInput.text.toString())
        replyQuotes.clear()
        replyQuoteEdits.clear()
        citasProvisionales.clear()
        postAdapter.refreshSelection()
        renderReplyQuotes()
        replyInput.setText(nuevo)
        replyInput.setSelection(cursor.coerceIn(0, replyInput.text.length))
        replyInput.requestFocus()
    }

    /** Smilies reales de FC: se piden una vez por sesión y se cachean en memoria. */
    private fun openSmileyPicker() {
        smileyCache?.let { bbcode.showSmilies(it); return }
        if (!engineReady) { toast("Conectando con el foro…"); return }
        smileyDialogPending = true
        toast("Cargando emoticonos…")
        webView.evaluateJavascript("window.fcLoadSmilies&&fcLoadSmilies()", null)
    }

    private fun onSmiliesJson(json: String) {
        val list = parseSmilies(json)
        if (list.isEmpty()) return
        smileyCache = list
        for (c in smileysEnCajas) c.conLista(list)
        if (smileyDialogPending) {
            smileyDialogPending = false
            if (isReplyVisible) bbcode.showSmilies(list)
        }
    }

    /** Escapa un String para incrustarlo entre comillas simples en evaluateJavascript. */
    /**
     * Color de la paleta. SIEMPRE por aquí y nunca un 0xFF… a pelo: un color escrito en Kotlin
     * se aplica en tiempo de ejecución y **se salta el values-night**, así que en modo oscuro
     * se queda con el valor claro y no falla nada — solo hay texto que no se lee.
     */
    private fun color(id: Int) = androidx.core.content.ContextCompat.getColor(this, id)

    private fun jsEscape(s: String): String =
        s.replace("\\", "\\\\").replace("'", "\\'")
            .replace("\r", "").replace("\n", "\\n")

    /**
     * Cierra el mensaje con la firma de la app si el usuario la tiene activada en Opciones
     * (por defecto sí). Se consulta en cada envío, no se cachea: así el interruptor surte
     * efecto en el siguiente mensaje sin reabrir nada.
     */
    private fun withSignature(body: String): String =
        PostSignature.append(body, OptionsController.signatureEnabled(shellPrefs))

    private fun submitReply() {
        // El guard de cambiandoDeCuenta NO va aquí: esto es "donde se pulsa" (el botón Enviar
        // del composer grande), y submitReplyPost() tiene una SEGUNDA vía de entrada
        // (submitQuickReply(), la barra rápida del hilo) que se saltaba este guard por completo.
        // La regla es "el guard va donde se publica": está en submitReplyPost(), submitNewThread()
        // y submitEdit(), que es donde de verdad se llama a fcSubmitReply/fcCreateThread/fcEditPost.
        if (sendingReply) return
        // Envío desde el composer grande (botón "Enviar"), nunca desde la barra rápida:
        // asegura que la marca de "viene de la barra" no quede colgada de un intento anterior.
        quickSendPending = false
        when (replyMode) {
            "newthread" -> submitNewThread()
            "edit" -> submitEdit()
            else -> submitReplyPost()
        }
    }

    /**
     * Lo escrito en el composer, listo para publicar.
     *
     * Único sitio del que salen los tres envíos (responder, hilo nuevo y editar), y por eso
     * existe: un `U+FFFC` pegado desde otro mensaje se publica como cajita "OBJ" y lo que se
     * ensucia es la cuenta del usuario (ver [TextoPlano]). Y de paso, los enlaces de Instagram,
     * X, TikTok y YouTube se envuelven en su BBCode para que el foro los pinte como tarjeta
     * (ver [EnlacesEmbebibles]).
     *
     * @param conTarjetas false al EDITAR: reescribir los enlaces de un mensaje viejo al tocarle
     *   una coma es cambiarle el aspecto a algo que ya estaba publicado, y eso no lo ha pedido
     *   quien edita.
     */
    /**
     * true solo en una instalación limpia: tras una actualización las dos fechas difieren, así
     * que no hay que adivinar nada ni dejar banderas por ahí.
     */
    private fun esInstalacionNueva(): Boolean = try {
        val info = packageManager.getPackageInfo(packageName, 0)
        info.firstInstallTime == info.lastUpdateTime
    } catch (_: Exception) {
        false   // ante la duda, lo de siempre: no quitarle el filtro a nadie
    }

    private fun textoDelComposer(conTarjetas: Boolean = true): String {
        val limpio = TextoPlano.sinCajitas(replyInput.text.toString())
        return (if (conTarjetas) EnlacesEmbebibles.conTarjetas(limpio) else limpio).trim()
    }

    private fun submitReplyPost() {
        // Aquí, y no en submitReply(): esta función tiene DOS vías de entrada (el composer
        // grande y submitQuickReply(), la barra rápida) y es donde de verdad se llama a
        // fcSubmitReply con las cookies que haya puestas en ese instante.
        if (cambiandoDeCuenta) { toast("Espera a que termine el cambio de cuenta"); return }
        val body = textoDelComposer()
        if (body.isEmpty() && replyQuotes.isEmpty()) { toast("Escribe algo antes de enviar"); return }
        if (currentThreadTid.isEmpty()) { toast("No se pudo identificar el hilo"); return }
        // Mensaje final = citas (BBCode) + texto del usuario + firma invisible de la app
        // (el usuario no la ve en el editor; se añade al enviar, con aire por encima).
        val quotes = replyQuotes.values.joinToString("") { quoteBlock(it) }
        var msg = (quotes + body).trim()
        if (msg.isEmpty()) { toast("Escribe algo antes de enviar"); return }
        msg = withSignature(msg)
        startSending("Enviando…")
        webView.evaluateJavascript(
            "window.fcSubmitReply&&fcSubmitReply('${jsEscape(currentThreadTid)}','${jsEscape(msg)}')",
            null
        )
    }

    private fun submitNewThread() {
        // Hoy solo se llega aquí desde submitReply(), pero el guard va donde se publica (ver
        // submitReplyPost()): si mañana le sale otra vía de entrada, como le pasó a esa,
        // queda cubierta igual sin tener que acordarse de repetirlo en el sitio nuevo.
        if (cambiandoDeCuenta) { toast("Espera a que termine el cambio de cuenta"); return }
        val subject = replySubject.text.toString().trim()
        val body = textoDelComposer()
        if (subject.isEmpty()) { toast("Ponle un título al hilo"); return }
        if (body.isEmpty()) { toast("Escribe el mensaje del hilo"); return }
        // La encuesta se valida ANTES de publicar: si FC la rechazara después, el hilo ya
        // estaría creado y sin encuesta.
        var encuesta = ""
        if (pollForm.visibility == View.VISIBLE) {
            when (val v = EncuestaNueva.validar(
                pollQuestion.text.toString(),
                (0 until pollOptions.childCount).map { (pollOptions.getChildAt(it) as EditText).text.toString() },
                pollMultiple.isChecked, pollPublic.isChecked, pollDays.text.toString()
            )) {
                is EncuestaNueva.Companion.Validacion.Error -> { toast(v.motivo); return }
                is EncuestaNueva.Companion.Validacion.Ok -> encuesta = v.encuesta.json()
            }
        }
        val msg = withSignature(body)
        startSending("Creando…")
        webView.evaluateJavascript(
            "window.fcCreateThread('$currentForumId','${jsEscape(subject)}','${jsEscape(msg)}','${jsEscape(encuesta)}')", null
        )
    }

    private fun configurarEncuestaNueva() {
        pollBox = findViewById(R.id.reply_poll_box)
        pollToggle = findViewById(R.id.reply_poll_toggle)
        pollForm = findViewById(R.id.reply_poll_form)
        pollQuestion = findViewById(R.id.reply_poll_question)
        pollOptions = findViewById(R.id.reply_poll_options)
        pollAdd = findViewById(R.id.reply_poll_add)
        pollMultiple = findViewById(R.id.reply_poll_multiple)
        pollPublic = findViewById(R.id.reply_poll_public)
        pollDays = findViewById(R.id.reply_poll_days)
        pollToggle.setOnClickListener {
            if (pollForm.visibility == View.VISIBLE) {
                pollForm.visibility = View.GONE
                pollToggle.text = "＋ Añadir encuesta"
            } else {
                if (pollOptions.childCount == 0) repeat(EncuestaNueva.MIN_OPCIONES) { anadirOpcionEncuesta() }
                pollForm.visibility = View.VISIBLE
                pollToggle.text = "− Quitar encuesta"
                pollQuestion.requestFocus()
            }
        }
        pollAdd.setOnClickListener {
            if (pollOptions.childCount >= EncuestaNueva.MAX_OPCIONES) {
                toast("Como mucho ${EncuestaNueva.MAX_OPCIONES} opciones"); return@setOnClickListener
            }
            anadirOpcionEncuesta().requestFocus()
        }
    }

    private fun anadirOpcionEncuesta(): EditText {
        val n = pollOptions.childCount + 1
        val caja = EditText(this).apply {
            hint = "Opción $n"
            setTextColor(color(R.color.fc_texto))
            setHintTextColor(color(R.color.fc_texto_3))
            textSize = 15f
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            // El autorrelleno es de Android 8: en un Android 7 esta llamada cerraba la app.
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO
            }
        }
        pollOptions.addView(caja)
        pollAdd.visibility = if (pollOptions.childCount >= EncuestaNueva.MAX_OPCIONES) View.GONE else View.VISIBLE
        return caja
    }

    /** Deja la encuesta como recién abierta: plegada y en blanco. */
    private fun vaciarEncuestaNueva() {
        pollForm.visibility = View.GONE
        pollToggle.text = "＋ Añadir encuesta"
        pollQuestion.setText("")
        pollOptions.removeAllViews()
        pollAdd.visibility = View.VISIBLE
        pollMultiple.isChecked = false
        pollPublic.isChecked = false
        pollDays.setText("")
    }

    private fun submitEdit() {
        // Mismo motivo que submitNewThread(): el guard vive donde se publica, no donde se
        // pulsa, para que una futura vía de entrada distinta a submitReply() no lo pierda.
        if (cambiandoDeCuenta) { toast("Espera a que termine el cambio de cuenta"); return }
        val body = textoDelComposer(conTarjetas = false)
        if (body.isEmpty()) { toast("El mensaje no puede quedar vacío"); return }
        if (editingPid.isEmpty()) { toast("No se pudo identificar el mensaje"); return }
        // Primer post del hilo: FC muestra el asunto, hay que reenviarlo.
        val subj = if (replySubject.visibility == View.VISIBLE) replySubject.text.toString().trim() else ""
        // Al editar NO se añade firma (el texto cargado ya la lleva si la tenía).
        startSending("Guardando…")
        webView.evaluateJavascript(
            "window.fcEditPost('${jsEscape(editingPid)}','${jsEscape(body)}','${jsEscape(subj)}')", null
        )
    }

    private fun startSending(label: String) {
        sendingReply = true
        replySend.isEnabled = false
        replySend.text = label
    }

    private val valoracion: Valoracion = ValoracionPlay()

    /**
     * Tras publicar con éxito, y solo entonces, se tantea la tarjeta de valoración.
     *
     * Es el mejor momento que hay: la persona acaba de hacer algo que le ha salido bien y no se
     * le interrumpe ninguna lectura. **Nunca** se le pregunta antes si le gusta la app para
     * enseñarla solo a los contentos — eso es filtrar reseñas y Play lo castiga con la retirada.
     *
     * Se retrasa un segundo y medio a propósito: el salto a la última página está en marcha y
     * la tarjeta no debe pelearse con él.
     */
    private fun tantearValoracion() {
        // A propósito SIN sufijo de cuenta (ver ClavesPorCuenta): pese al nombre, esto no cuenta
        // "mensajes tuyos", cuenta intentos para decidir cuándo enseñar la tarjeta de valoración
        // de Play. Esa tarjeta es de la APP, no de la cuenta con la que se publicó, así que
        // compartirla entre cuentas es lo correcto.
        val publicados = shellPrefs.getInt(PREF_PUBLICADOS, 0) + 1
        shellPrefs.edit().putInt(PREF_PUBLICADOS, publicados).apply()
        val intentos = shellPrefs.getInt(PREF_VALORACION_INTENTOS, 0)
        if (!CuandoPedirValoracion.toca(publicados, intentos)) return
        reportHandler.postDelayed({
            valoracion.pedir(this) {
                // Se cuenta AQUÍ, al completarse el flujo: si falló por no haber red, no se
                // gasta un intento. Ojo, completarse NO significa que se haya enseñado (medido).
                shellPrefs.edit().putInt(PREF_VALORACION_INTENTOS, intentos + 1).apply()
            }
        }, 1_500)
    }

    private fun onReplyResult(json: String) {
        sendingReply = false
        replySend.isEnabled = true
        replySend.text = "Enviar"
        // Se consume aquí, antes de cualquier retorno: si el POST venía de la barra rápida,
        // un fallo (incluida una respuesta que ni siquiera se pudo parsear) vacía SOLO el
        // relé (replyInput) para no dejar el mismo texto duplicado en las dos cajas — el
        // usuario lo sigue teniendo intacto en quickInput, listo para reintentar o expandir.
        val wasQuick = quickSendPending
        quickSendPending = false
        val ok: Boolean
        val err: String
        val finalUrl: String
        try {
            val o = org.json.JSONObject(json)
            ok = o.optBoolean("ok", false)
            err = o.optString("error", "")
            finalUrl = o.optString("finalUrl", "")
        } catch (_: Exception) {
            if (wasQuick) replyInput.setText("")
            toast("No se pudo enviar la respuesta")
            return
        }
        if (ok) {
            replyInput.setText("")
            replyQuotes.clear()
            replyQuoteEdits.clear()
        replyQuoteEdits.clear()
            postAdapter.refreshSelection()
            hideReply()
            quickInput.setText("")
            toast("Respuesta publicada")
            // Salta al mensaje recién publicado en vez de dejar la lista arriba del todo.
            // Si FC no da el pid en la URL del redirect, se resuelve al cargar la página
            // cogiendo el último post (pendingScrollToLast).
            val pid = replyPidFromUrl(finalUrl)
            pendingScrollOffset = null
            if (pid.isNotEmpty()) pendingScrollPid = pid else pendingScrollToLast = true
            buscandoMiMensaje = true
            // Sin scroll infinito, la última página SUSTITUYE lo que hubiera cargado: es a
            // donde acabas de escribir. Antes se añadía por debajo, que tenía sentido cuando
            // el hilo se leía de un tirón.
            // `apilar = false`: acabas de publicar, no has navegado a la última página. Si
            // apilara, el atrás te devolvería a donde escribías en vez de salir del hilo.
            jumpToThreadPage(threadPageCount, apilar = false)
            tantearValoracion()
        } else {
            if (wasQuick) replyInput.setText("")
            toast(ErrorPublicar.corto(err, "No se pudo enviar la respuesta"))
        }
    }

    /** Resultado de crear hilo / editar / borrar. */
    private fun onThreadActionResult(json: String) {
        sendingReply = false
        replySend.isEnabled = true
        val action: String
        val ok: Boolean
        val err: String
        val tid: String
        try {
            val o = org.json.JSONObject(json)
            action = o.optString("action")
            ok = o.optBoolean("ok", false)
            err = o.optString("error", "")
            tid = o.optString("tid", "")
        } catch (_: Exception) { return }
        when (action) {
            "create" -> {
                replySend.text = "Enviar"
                if (ok) {
                    replyInput.setText(""); replySubject.setText("")
                    vaciarEncuestaNueva()
                    hideReply()
                    val errEncuesta = try { org.json.JSONObject(json).optString("pollError") } catch (_: Exception) { "" }
                    // Son dos envíos: el hilo ya existe aunque la encuesta falle, y eso se dice.
                    if (errEncuesta.isEmpty()) toast("Hilo creado")
                    else {
                        val motivo = ErrorPublicar.corto(errEncuesta, "")
                        toast("Hilo creado, pero la encuesta no se pudo añadir" +
                            if (motivo.isEmpty()) "" else ": $motivo")
                    }
                    if (tid.isNotEmpty()) openThreadNative("https://forocoches.com/foro/showthread.php?t=$tid", "")
                    else showHomeList()
                } else toast(ErrorPublicar.corto(err, "No se pudo crear el hilo"))
            }
            "edit" -> {
                replySend.text = "Guardar"
                if (ok) {
                    // Se guarda ANTES de hideReply(): a partir de ahí el composer se desmonta
                    // y `editingPid` deja de ser de fiar.
                    val editado = editingPid
                    replyInput.setText("")
                    hideReply()
                    toast("Mensaje editado")
                    reloadCurrentThread(Motivo.EDITAR, editado)
                } else toast(ErrorPublicar.corto(err, "No se pudo editar el mensaje"))
            }
            "delete" -> {
                if (ok) {
                    toast("Mensaje borrado")
                    reloadCurrentThread(Motivo.BORRAR)
                } else toast(ErrorPublicar.corto(err, "No se pudo borrar el mensaje"))
            }
            "fav" -> {
                threadFav.isEnabled = true
                val fav = try { org.json.JSONObject(json).optBoolean("fav", false) } catch (_: Exception) { false }
                if (ok) {
                    // El motor relee la lista antes de contestar, así que este `fav` es la
                    // verdad de FC: es la única fuente que puede dar de BAJA en la memoria.
                    anotarSuscripcion(currentThreadTid, fav)
                    pintarEstrella(fav)
                    toast(if (fav) "Añadido a suscripciones" else "Quitado de suscripciones")
                } else toast(if (err == "login") "Inicia sesión para usar las suscripciones"
                             else if (err.isNotEmpty()) err else "No se pudo cambiar la suscripción")
            }
        }
    }

    /** Favoritos: alterna la suscripción del hilo abierto. El estado real vive en
     *  subscription.php (el botón de FC en el hilo es estático), así que el motor
     *  consulta, alterna y verifica; aquí solo se refleja el resultado. */
    private fun toggleFavorite() {
        // Alterna la suscripción DE LA CUENTA QUE HAYA EN LAS COOKIES ahora mismo: a medio
        // cambiar, se marcaría/desmarcaría en la cuenta equivocada.
        if (cambiandoDeCuenta) { toast("Espera a que termine el cambio de cuenta"); return }
        if (!isLoggedIn()) { toast("Inicia sesión para usar las suscripciones"); showLogin(); return }
        if (currentThreadTid.isEmpty()) { toast("No se pudo identificar el hilo"); return }
        threadFav.isEnabled = false
        webView.evaluateJavascript(
            "window.fcToggleFavorite&&fcToggleFavorite('${jsEscape(currentThreadTid)}')", null
        )
    }

    /**
     * Recarga el hilo abierto **por la página en la que estás**, no por la 1.
     *
     * Pedir la 1 era el bug que reportó Green Floyd el 2026-09-13: editabas un mensaje en la
     * página 12 y acababas arriba del todo del hilo, sin ver el cambio que acababas de hacer.
     * Con [Motivo.EDITAR] además se salta al mensaje editado y se resalta, que es justo lo que
     * él pedía ("debería mostrarte el mensaje que acabas de editar").
     *
     * La regla vive en [RecargaHilo], con tests.
     */
    private fun reloadCurrentThread(motivo: Motivo = Motivo.FILTROS, pid: String = "") {
        if (currentThreadUrl.isEmpty()) return
        val r = RecargaHilo.tras(motivo, threadPage, pid)
        threadPage = r.pagina
        postAdapter.clear()
        loadingThreadPage = false
        // `pendingScrollOffset` se queda a null a propósito: es lo que distingue "saltar y
        // RESALTAR" de "restaurar una posición en silencio" (ver onThreadJson).
        pendingScrollPid = r.pidDestacado
        pendingScrollOffset = null
        requestThreadPage(r.pagina)
    }

    // ── Panel de login nativo (Fase 3) ───────────────────────────────────────

    private fun configureLoginPanel() {
        loginPanel = findViewById(R.id.login_panel)
        loginUser = findViewById(R.id.login_user)
        loginPass = findViewById(R.id.login_pass)
        loginError = findViewById(R.id.login_error)
        loginSubmit = findViewById(R.id.login_submit)
        loginSubmit.setOnClickListener { submitLogin() }
        findViewById<View>(R.id.login_skip).setOnClickListener { hideLogin() }
        // Registro: fuera de la app (navegador del sistema); el foro nunca se ve dentro.
        findViewById<View>(R.id.login_register).setOnClickListener {
            try {
                startActivity(
                    android.content.Intent(
                        android.content.Intent.ACTION_VIEW,
                        android.net.Uri.parse("https://forocoches.com/foro/register.php")
                    ).addCategory(android.content.Intent.CATEGORY_BROWSABLE)
                )
            } catch (_: Exception) { }
        }
    }

    private fun showLogin() {
        isLoginVisible = true
        isNoticesVisible = false
        isProfileVisible = false
        loginPanel.visibility = View.VISIBLE
        nativePanel.visibility = View.GONE
        threadPanel.visibility = View.GONE
        replyPanel.visibility = View.GONE
        noticesPanel.visibility = View.GONE
        descargadosPanel.visibility = View.GONE
        profilePanel.visibility = View.GONE
        bottomNav.visibility = View.GONE   // pantalla de escritura: teclado a pantalla limpia
        loginError.visibility = View.GONE
        loginUser.requestFocus()
    }

    private fun hideLogin() {
        isLoginVisible = false
        // Salir del panel cancela el intento. Si se había borrado la sesión para entrar con
        // otra cuenta, hay que reponerla: si no, cerrar el panel te deja sin ninguna.
        if (loginParaOtraCuenta && cookiesAntesDeAnadir.isNotEmpty()) {
            val anteriores = cookiesAntesDeAnadir
            cookiesAntesDeAnadir = emptyMap()
            SesionFC.borrar(CookieManager.getInstance()) {
                SesionFC.poner(CookieManager.getInstance(), anteriores)
                CookieManager.getInstance().flush()
            }
        }
        loginParaOtraCuenta = false
        val imm = getSystemService(INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
        imm.hideSoftInputFromWindow(loginPanel.windowToken, 0)
        loginPanel.visibility = View.GONE
        bottomNav.visibility = View.VISIBLE
        pendingThreadUrl = ""
        pendingThreadTitle = ""
        // Vuelve a donde estaba: al hilo si venía de uno CON contenido (un +HD que no
        // cargó por falta de cuenta no cuenta), si no a la lista.
        if (isThreadVisible && currentThreadUrl.isNotEmpty() && postAdapter.itemCount > 0) {
            showThread()
        } else {
            showNative()
            setSelectedNav(navIdForList())
        }
    }

    /**
     * ¿Ya se ha ofrecido la verificación en este intento de login? Evita el bucle de overlays si
     * el desafío no se deja resolver. Se rearma en cuanto un login entra bien.
     */
    private var verificacionLoginIntentada = false

    private var loginCfOverlay: android.widget.FrameLayout? = null
    private var loginCfWeb: android.webkit.WebView? = null
    private var loginCfCover: View? = null
    private var loginCfPoll: Runnable? = null

    /**
     * Deja que el usuario resuelva el Cloudflare que le bloquea `login.php`.
     *
     * Mismo truco que al reportar (gotcha 12): un WebView a pantalla completa que SÍ renderiza
     * —para que el desafío avance— pero **tapado por una capa opaca**, que solo se baja cuando
     * hay un Cloudflare interactivo que exige un toque. El formulario de FC no llega a verse: en
     * cuanto aparece se vuelve a tapar y se reintenta el login NATIVO con lo ya escrito.
     *
     * Las cookies son del proceso (`CookieManager`), así que la `cf_clearance` que gane este
     * WebView la hereda el motor.
     */
    private fun empezarVerificacionLogin() {
        if (loginCfOverlay != null) return
        val root = findViewById<android.view.ViewGroup>(android.R.id.content)
        val overlay = android.widget.FrameLayout(this)
        val wv = object : android.webkit.WebView(this) {
            // Tapado, Android lo da por invisible y Chromium congela el render: el desafío no
            // avanzaría nunca. Se fuerza "visible".
            override fun onVisibilityAggregated(isVisible: Boolean) { super.onVisibilityAggregated(true) }
        }.apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.databaseEnabled = true
            settings.userAgentString = webView.settings.userAgentString
            webViewClient = object : android.webkit.WebViewClient() {
                override fun shouldOverrideUrlLoading(
                    view: android.webkit.WebView, req: android.webkit.WebResourceRequest
                ): Boolean = false
            }
        }
        overlay.addView(wv, android.widget.FrameLayout.LayoutParams(-1, -1))
        val cover = construirTapaVerificacion()
        cover.visibility = View.VISIBLE
        overlay.addView(cover, android.widget.FrameLayout.LayoutParams(-1, -1))
        root.addView(overlay, android.view.ViewGroup.LayoutParams(-1, -1))
        loginCfOverlay = overlay; loginCfWeb = wv; loginCfCover = cover

        wv.loadUrl("https://forocoches.com/foro/login.php")
        val empezo = System.currentTimeMillis()
        var reintentado = false
        var ultimoTramoLog = -1L
        val poll = object : Runnable {
            override fun run() {
                val w = loginCfWeb ?: return
                w.evaluateJavascript(
                    """(function(){
                        var b=document.body?document.body.innerText:'';
                        var cf=/Un momento|Just a moment|Verificaci.n de seguridad|Verifique que|Attention Required/i.test((document.title||'')+b);
                        var form=!!document.querySelector('input[name="vb_login_username"]');
                        return JSON.stringify({cf:cf,form:form,
                            url:location.href,
                            title:(document.title||''),
                            txt:b.replace(/\s+/g,' ').slice(0,220)});
                    })()"""
                ) { res ->
                    val r = try {
                        val inner = org.json.JSONTokener(res).nextValue() as? String
                        if (inner != null) org.json.JSONObject(inner) else null
                    } catch (e: Exception) { null }
                    val cf = r?.optBoolean("cf") == true
                    val form = r?.optBoolean("form") == true
                    val url = r?.optString("url").orEmpty()
                    val txt = r?.optString("txt").orEmpty()
                    val pasado = System.currentTimeMillis() - empezo
                    // ÉXITO = el desafío ya no está, estemos donde estemos.
                    //
                    // Esperar a ver el formulario de login era el fallo: medido el 2026-09-11
                    // desde una IP holandesa, Cloudflare pasa en 9 s pero FC NO devuelve a
                    // login.php, redirige a index.php. Con la condición vieja nos quedábamos
                    // 81 s más mirando la portada y soltábamos un error con el trabajo ya hecho.
                    // Se exige texto de verdad para que un about:blank del arranque no cuele
                    // como éxito.
                    val enElForo = url.contains("forocoches.com") && !url.contains("__cf_chl")
                    val verificado = form || (!cf && enElForo && txt.length > 50)
                    // Traza: con VPN puesta no se puede depurar en vivo (el adb inalámbrico se
                    // cae), así que la app apunta QUÉ está viendo y se lee luego por logcat.
                    if (pasado / 1500 != ultimoTramoLog) {
                        ultimoTramoLog = pasado / 1500
                        android.util.Log.i("FC_LOGINCF",
                            "t=${pasado}ms cf=$cf form=$form url=${r?.optString("url")} " +
                                "title=[${r?.optString("title")}] txt=[${r?.optString("txt")}]")
                    }
                    // La tapa solo se baja con Cloudflare interactivo y sin formulario todavía:
                    // es la única ventana en la que el foro puede verse, y es a propósito.
                    loginCfCover?.visibility = if (cf && !form) View.GONE else View.VISIBLE
                    when {
                        verificado && !reintentado -> {
                            reintentado = true
                            cerrarVerificacionLogin()
                            submitLogin()
                        }
                        pasado > 90_000 -> {
                            cerrarVerificacionLogin()
                            loginError.text = ErrorLogin.mensaje("cloudflare")
                            loginError.visibility = View.VISIBLE
                        }
                        else -> reportHandler.postDelayed(this, 150)
                    }
                }
            }
        }
        loginCfPoll = poll
        reportHandler.postDelayed(poll, 250)
    }

    /** Capa opaca: el usuario solo ve ESTO mientras no haya que tocar nada. */
    private fun construirTapaVerificacion(): View {
        val ll = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            gravity = android.view.Gravity.CENTER
            setBackgroundColor(color(R.color.fc_fondo))
            isClickable = true; isFocusable = true
            setPadding(dp(32), dp(32), dp(32), dp(32))
        }
        ll.addView(android.widget.ProgressBar(this))
        ll.addView(android.widget.TextView(this).apply {
            text = "Verificando con ForoCoches…"
            setTextColor(color(R.color.fc_texto))
            textSize = 16f
            gravity = android.view.Gravity.CENTER
            setPadding(0, dp(18), 0, dp(6))
        })
        ll.addView(android.widget.TextView(this).apply {
            text = "Si aparece una casilla, tócala para continuar"
            setTextColor(color(R.color.fc_texto_3))
            textSize = 13f
            gravity = android.view.Gravity.CENTER
        })
        ll.addView(android.widget.TextView(this).apply {
            text = "Cancelar"
            setTextColor(color(R.color.fc_rojo))
            textSize = 15f
            setPadding(dp(20), dp(24), dp(20), dp(10))
            setOnClickListener {
                cerrarVerificacionLogin()
                loginError.text = ErrorLogin.mensaje("cloudflare")
                loginError.visibility = View.VISIBLE
            }
        })
        return ll
    }

    /**
     * Cierra la verificación. **El orden importa y no es el intuitivo.**
     *
     * Android exige sacar el WebView de la jerarquía de vistas ANTES de destruirlo. Al revés
     * revienta por dentro (se ve en el log como `Scheduling restart of crashed service …
     * SandboxedProcessService0`, el renderer de Chromium muriéndose), la excepción se lleva por
     * delante el `removeView` que venía después, **y el overlay se queda en pantalla con el
     * foro a la vista y sin su capa opaca**: la regla de oro rota justo al terminar de entrar.
     * Medido el 2026-09-11.
     *
     * Además se aplaza al frame siguiente: esto se llama desde el callback del propio WebView, y
     * destruirlo desde ahí dentro es pedir guerra.
     */
    private fun cerrarVerificacionLogin() {
        loginCfPoll?.let { reportHandler.removeCallbacks(it) }; loginCfPoll = null
        val overlay = loginCfOverlay
        val wv = loginCfWeb
        loginCfOverlay = null; loginCfWeb = null; loginCfCover = null
        reportHandler.post {
            // 1) fuera de la pantalla   2) fuera del WebView de dentro   3) destruir
            (overlay?.parent as? android.view.ViewGroup)?.removeView(overlay)
            wv?.let {
                it.stopLoading()
                it.loadUrl("about:blank")
                (it.parent as? android.view.ViewGroup)?.removeView(it)
                it.destroy()
            }
        }
    }

    private fun submitLogin() {
        if (sendingLogin) return
        val user = loginUser.text.toString().trim()
        val pass = loginPass.text.toString()
        if (user.isEmpty() || pass.isEmpty()) {
            loginError.text = "Rellena usuario y contraseña"
            loginError.visibility = View.VISIBLE
            return
        }
        if (!engineReady) {
            loginError.text = "Conectando con el foro… prueba en unos segundos"
            loginError.visibility = View.VISIBLE
            return
        }
        sendingLogin = true
        loginError.visibility = View.GONE
        loginSubmit.text = "Entrando…"
        val entrar = {
            webView.evaluateJavascript(
                "window.fcLogin&&fcLogin('${jsEscape(user)}','${jsEscape(pass)}')", null
            )
        }
        if (loginParaOtraCuenta) {
            // Con la sesión vieja puesta, FC contesta "Bienvenido de nuevo X. Gracias por
            // iniciar sesión"… y NO cambia de sesión: el `bbsessionhash` anterior sigue
            // mandando (medido por CDP el 2026-09-17). Así que se borra la sesión primero,
            // igual que hace `cambiarACuenta`, y se guarda para reponerla si el login no sale.
            cookiesAntesDeAnadir = SesionFC.leer(CookieManager.getInstance())
            SesionFC.borrar(CookieManager.getInstance()) { entrar() }
        } else entrar()
    }

    private fun onLoginResult(json: String) {
        sendingLogin = false
        loginSubmit.text = "Iniciar sesión"
        var err = ""
        // `posted` lo manda fcLogin desde el primer día y no lo leía nadie: false = el
        // formulario NO llegó a enviarse (Cloudflare delante, fallo de red).
        var posted = true
        try {
            val o = org.json.JSONObject(json)
            err = o.optString("error", "")
            posted = o.optBoolean("posted", true)
        } catch (_: Exception) { }
        // Veredicto: la cookie `bbuserid` sola NO basta. Vale cuando entras desde fuera, pero
        // al añadir una cuenta esa cookie YA está puesta antes de intentarlo, así que un login
        // rechazado por FC se contaba como éxito y la cuenta no se añadía sin decir nada
        // (reportado el 2026-09-17). Las reglas y el porqué, en [VeredictoLogin].
        val veredicto = VeredictoLogin.de(posted, isLoggedIn())
        if (veredicto == VeredictoLogin.Veredicto.FALLA) {
            // Veníamos de borrar la sesión para entrar con otra cuenta y no ha salido: hay que
            // devolver al usuario a la cuenta que tenía, o lo dejamos fuera de las dos.
            if (loginParaOtraCuenta && cookiesAntesDeAnadir.isNotEmpty()) {
                reponerCuentaAnterior(err)
                return
            }
            // Cloudflare: el listado ya sabia salir de esto (ensena la web y el usuario resuelve
            // el desafio); el login se quedaba en un texto y sin salida. Ahora tambien se le
            // ofrece resolverlo, tras una capa opaca igual que al reportar. Ver [ErrorLogin].
            if (ErrorLogin.debeVerificar(err, verificacionLoginIntentada)) {
                verificacionLoginIntentada = true
                empezarVerificacionLogin()
                return
            }
            loginError.text = ErrorLogin.mensaje(err)
            loginError.visibility = View.VISIBLE
            return
        }
        entrarTrasLogin(pedirIdentidad = true)
    }

    /** Se cierra el panel y se refresca todo con la sesión nueva. */
    private fun entrarTrasLogin(pedirIdentidad: Boolean) {
        verificacionLoginIntentada = false
        loginParaOtraCuenta = false
        // Ha entrado: las cookies de la cuenta anterior ya están a salvo en su cofre
        // (guardarCookiesDe la guardó al activarla), así que esta copia temporal sobra.
        cookiesAntesDeAnadir = emptyMap()
        sendingLogin = false
        loginSubmit.text = "Iniciar sesión"
        // Dentro. Persistimos cookies ya y refrescamos todo con la sesión nueva.
        CookieManager.getInstance().flush()
        updateAccountNavItem()
        if (pedirIdentidad) {
            // Esta cuenta puede no ser la activa (login desde "Añadir cuenta"): sin esto,
            // onQuienSoy descartaría su propia respuesta por no coincidir con uidActivo. Se
            // consume ahí mismo en cuanto llega esa respuesta.
            aceptarUidNuevo = true
            webView.evaluateJavascript("window.fcQuienSoy&&fcQuienSoy()", null)
        }
        loginPass.setText("")
        isLoginVisible = false
        loginPanel.visibility = View.GONE
        bottomNav.visibility = View.VISIBLE
        toast("Sesión iniciada")
        listLoaded = false
        loadingPage = false
        val backToThread = pendingThreadUrl
        val backToTitle = pendingThreadTitle
        pendingThreadUrl = ""
        pendingThreadTitle = ""
        if (backToThread.isNotEmpty()) {
            // Venía de un hilo que exigía cuenta (+HD): lo reabrimos ya logueado.
            openThreadNative(backToThread, backToTitle)
        } else if (isThreadVisible && currentThreadUrl.isNotEmpty()) {
            showThread()
        } else {
            showNative()
            setSelectedNav(navIdForList())
        }
        requestThreadList(1)   // refresca lista + menuLinks + badges con la sesión
        // La lista de ignorados se pedía SOLO en onCreate, cuando todavía no había sesión:
        // quien se logueaba se quedaba sin filtro hasta reiniciar la app (reportado por dos
        // testers el 2026-08-20). Ahora se pide aquí, con la sesión recién puesta.
        requestIgnoreList()
    }

    private fun toast(m: String) =
        android.widget.Toast.makeText(this, m, android.widget.Toast.LENGTH_SHORT).show()

    private fun threadPageUrl(page: Int): String =
        UrlHilo.pagina(currentThreadUrl, page, currentThreadTid)

    // ── Descargar un hilo ───────────────────────────────────────────────────
    //
    // Se piden TODAS las páginas por el canal 'descarga' del motor: es el mismo parseo que el
    // de la pantalla, pero sale por otra puerta, así que guardar un hilo de 60 páginas no
    // repinta el que el usuario está leyendo. Con la app delante, que es la única forma: todo
    // lo que se le pide a FC sale por el WebView con la sesión.

    private fun descargarHiloActual() {
        if (descargando) { toast("Ya se está descargando un hilo"); return }
        if (!engineReady) { toast("Conectando con el foro…"); return }
        if (!Descargas.tidValido(currentThreadTid)) { toast("Este hilo no se puede descargar"); return }
        modoRecorrido = "descarga"
        descargaTid = currentThreadTid
        descargaTitulo = currentThreadTitle()
        descargaPaginasTotales = threadPageCount.coerceAtLeast(1)
        descargaSiguiente = 1
        descargaRecogidas.clear()
        descargando = true
        pintarDescarga()
        pedirPaginaDeDescarga()
    }

    /** El título del hilo, sin el "- Página N" que le cuelga FC (gotcha 22). */
    private fun currentThreadTitle(): String =
        threadTitle.text?.toString()?.substringBefore(" - Página ")?.trim().orEmpty()

    private fun pedirPaginaDeDescarga() {
        if (!descargando) return
        val url = UrlHilo.pagina(currentThreadUrl, descargaSiguiente, descargaTid)
        webView.evaluateJavascript(
            "window.fcLoadThread&&fcLoadThread('${jsEscape(url)}','descarga')", null
        )
    }

    private fun onPaginaDescargada(json: String) {
        if (!descargando) return
        if (modoRecorrido == "usuario") { onPaginaDeUsuario(json); return }
        descargaRecogidas.add(json)
        // FC solo dice cuántas páginas hay cuando te trae una: la primera respuesta puede
        // corregir al alza lo que sabíamos al empezar.
        val o = try { org.json.JSONObject(json) } catch (_: Exception) { null }
        val dice = o?.optInt("pageCount") ?: 0
        if (dice > descargaPaginasTotales) descargaPaginasTotales = dice
        if (descargaSiguiente >= descargaPaginasTotales) { terminarDescarga(); return }
        descargaSiguiente++
        pintarDescarga()
        pedirPaginaDeDescarga()
    }

    private fun terminarDescarga() {
        val paginas = ArrayList(descargaRecogidas)
        val tid = descargaTid
        val titulo = descargaTitulo
        descargando = false
        pintarDescarga()
        val nuevos = paginas.sumOf {
            try { org.json.JSONObject(it).optJSONArray("posts")?.length() ?: 0 } catch (_: Exception) { 0 }
        }
        val guardados = almacenDescargas.mensajesGuardados(tid)
        // Sustituir es lo normal y no pregunta. Solo se pregunta cuando la copia nueva trae
        // MENOS mensajes: ahí re-descargar borraría justo lo que fuiste a salvar.
        if (Descargas.hayQuePreguntar(guardados, nuevos)) {
            androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("La copia nueva tiene menos mensajes")
                .setMessage(
                    "La que tienes guardada tiene $guardados mensajes y la que acaba de bajar " +
                        "trae $nuevos. Puede que hayan borrado mensajes del hilo.\n\n" +
                        "Si la sustituyes, se pierden."
                )
                .setNegativeButton("Quedarme con la mía", null)
                .setPositiveButton("Sustituir") { _, _ -> guardarDescarga(tid, titulo, paginas) }
                .show()
            return
        }
        guardarDescarga(tid, titulo, paginas)
    }

    private fun guardarDescarga(tid: String, titulo: String, paginas: List<String>) {
        if (!almacenDescargas.guardar(tid, titulo, paginas)) { toast("No se pudo guardar el hilo"); return }
        descargarImagenes(tid, paginas)
    }

    /**
     * Las imágenes del hilo, como MINIATURAS.
     *
     * Van DESPUÉS del texto y en su propia tanda por un motivo: si algo falla aquí, la copia ya
     * está guardada y se lee. Una imagen rota —un 404, un host caído, un proxy que no contesta—
     * **no puede tumbar la descarga**: se cuenta y se sigue. Se reutiliza `PostImages.descargar`,
     * que es el único sitio de la app que sabe pedirle una imagen a FC con la cabecera y la
     * cookie correctas.
     */
    private fun descargarImagenes(tid: String, paginas: List<String>) {
        val htmls = ArrayList<String>()
        for (p in paginas) {
            val posts = try { org.json.JSONObject(p).optJSONArray("posts") } catch (_: Exception) { null }
                ?: continue
            for (i in 0 until posts.length()) htmls.add(posts.optJSONObject(i)?.optString("html").orEmpty())
        }
        val urls = Descargas.urlsDeImagen(htmls)
        if (urls.isEmpty()) { toast("Hilo descargado (${paginas.size} pág.)"); refrescarDescargados(); return }
        descargando = true
        descargaSiguiente = 0
        descargaPaginasTotales = urls.size
        pintarDescargaImagenes(0, urls.size)
        poolDescargas.execute {
            var ok = 0
            for ((i, u) in urls.withIndex()) {
                if (!descargando) break
                try {
                    if (almacenDescargas.guardarImagen(tid, u, PostImages.descargar(u))) ok++
                } catch (_: Throwable) {
                }
                runOnUiThread { pintarDescargaImagenes(i + 1, urls.size) }
            }
            runOnUiThread {
                descargando = false
                pintarDescarga()
                toast("Hilo descargado · ${paginas.size} pág. · $ok de ${urls.size} imágenes")
                refrescarDescargados()
            }
        }
    }

    // ── Sus mensajes en este hilo ───────────────────────────────────────────
    //
    // Antes esto lo resolvía el BUSCADOR de FC (`searchthreadid`), que devuelve extractos
    // truncados con "…": frases sueltas sin contexto, imposible saber a quién contesta. Lo
    // reportó Alfa el 2026-09-17 ("la implementación de ahora no me dice nada") y Márquez lo
    // secundó pidiendo "la conversación anidada de la respuesta y la del op".
    //
    // La solución no fue reconstruir nada: **cuando alguien cita, FC mete la cita DENTRO del
    // html del mensaje**, y la app ya la sabe dibujar. Así que basta con leer el HILO en vez
    // del buscador y quedarse con los mensajes de esa persona: la cita viene con ellos.

    private fun mostrarSusMensajesEnHilo(usuario: String, tid: String) {
        // Recorrer el hilo entero es lo que da las citas completas, pero en uno de 500 páginas
        // serían 500 peticiones: ahí se usa el buscador de FC, como antes (extractos, pero ya).
        // Si está DESCARGADO da igual lo largo que sea: sale del fichero sin pedir nada.
        if (threadPageCount > MAX_PAGINAS_SUS_MENSAJES && almacenDescargas.leer(tid) == null) {
            showUserActivity(usuario, "posts", enHilo = tid)
            return
        }
        if (descargando) { toast("Espera a que acabe lo que está bajando"); return }
        if (!engineReady) { toast("Conectando con el foro…"); return }
        // Dónde estabas: atrás tiene que devolverte AQUÍ, no a la lista (ver goBack).
        rememberThreadPosition()
        usuarioBuscado = usuario
        mensajesDelUsuario.clear()
        vistaDerivada = "@$usuario · en este hilo"
        currentThreadTid = tid
        postAdapter.submit(emptyList())
        showThread()
        contextoBar.text = "Sus mensajes en este hilo"
        contextoBar.visibility = View.VISIBLE
        threadTitle.text = vistaDerivada

        // Si el hilo está descargado, esto sale del fichero: cero peticiones y al instante.
        val copia = almacenDescargas.leer(tid)
        if (copia != null) {
            val arr = try { org.json.JSONObject(copia).optJSONArray("contenido") } catch (_: Exception) { null }
            if (arr != null) {
                for (i in 0 until arr.length()) filtrarPaginaDelUsuario(arr.optJSONObject(i))
                pintarMensajesDelUsuario(terminado = true)
                return
            }
        }

        modoRecorrido = "usuario"
        descargaTid = tid
        descargaPaginasTotales = threadPageCount.coerceAtLeast(1)
        descargaSiguiente = 1
        descargando = true
        pintarBusquedaUsuario()
        pedirPaginaDeDescarga()
    }

    private fun onPaginaDeUsuario(json: String) {
        val o = try { org.json.JSONObject(json) } catch (_: Exception) { null }
        if (o != null) {
            val dice = o.optInt("pageCount")
            if (dice > descargaPaginasTotales) descargaPaginasTotales = dice
            filtrarPaginaDelUsuario(o)
        }
        // Se repinta en CADA página: los mensajes van apareciendo según se encuentran, que es
        // lo que hace soportable un hilo de 40 páginas.
        pintarMensajesDelUsuario(terminado = false)
        if (descargaSiguiente >= descargaPaginasTotales) {
            descargando = false
            pintarDescarga()
            pintarMensajesDelUsuario(terminado = true)
            return
        }
        descargaSiguiente++
        pintarBusquedaUsuario()
        pedirPaginaDeDescarga()
    }

    /** Se compara el nombre sin mayúsculas: FC no es consistente con ellas. */
    private fun filtrarPaginaDelUsuario(pagina: org.json.JSONObject?) {
        val posts = pagina?.optJSONArray("posts") ?: return
        for (i in 0 until posts.length()) {
            val p = posts.optJSONObject(i) ?: continue
            if (p.optString("author").equals(usuarioBuscado, ignoreCase = true)) {
                mensajesDelUsuario.add(p)
            }
        }
    }

    /**
     * Pinta lo encontrado reusando el render del hilo: se le pasa un payload con SUS mensajes.
     * Así salen como mensajes de verdad —con sus citas, sus fotos y sus embeds— y no hay una
     * rama de dibujo nueva que mantener.
     */
    private fun pintarMensajesDelUsuario(terminado: Boolean) {
        val arr = org.json.JSONArray()
        for (p in mensajesDelUsuario) arr.put(p)
        val payload = org.json.JSONObject()
            .put("url", currentThreadUrl)
            .put("tid", descargaTid)
            .put("title", vistaDerivada)
            .put("page", 1)
            .put("pageCount", 1)
            .put("posts", arr)
        onThreadJson(payload.toString())
        if (terminado) {
            val n = mensajesDelUsuario.size
            contextoBar.text =
                if (n == 0) "No ha escrito nada en este hilo"
                else "$n ${if (n == 1) "mensaje suyo" else "mensajes suyos"} en este hilo"
        }
    }

    private fun salirDeSusMensajes() {
        cerrarCopia()
        // La lista tiene sus mensajes, no el hilo: vaciarla obliga a restoreThread a traer la
        // página donde estabas en vez de dar por bueno lo que hay pintado.
        postAdapter.submit(emptyList())
        contextoBar.visibility = View.GONE
        (nav.current as? Screen.Thread)?.let { restoreThread(it) }
    }

    private fun pintarBusquedaUsuario() {
        if (!::descargaBar.isInitialized || !descargando) return
        descargaBar.visibility = View.VISIBLE
        descargaTexto.text = "Buscando sus mensajes · página $descargaSiguiente de $descargaPaginasTotales"
        descargaProgreso.progress =
            (100 * (descargaSiguiente - 1) / descargaPaginasTotales.coerceAtLeast(1))
    }

    private fun pintarDescargaImagenes(hechas: Int, total: Int) {
        if (!::descargaBar.isInitialized || !descargando) return
        descargaBar.visibility = View.VISIBLE
        descargaTexto.text = "Guardando imágenes · $hechas de $total"
        descargaProgreso.progress = if (total == 0) 100 else 100 * hechas / total
    }

    // ── La pantalla de Descargados ──────────────────────────────────────────

    private fun showDescargados() {
        isDescargadosVisible = true
        dejarElHilo(); isWebVisible = false; isReplyVisible = false
        isLoginVisible = false; isProfileVisible = false; isOptionsVisible = false
        isNoticesVisible = false
        hidePmPanels()
        memberPanel.visibility = View.GONE
        isMemberVisible = false
        nativePanel.visibility = View.GONE
        threadPanel.visibility = View.GONE
        replyPanel.visibility = View.GONE
        loginPanel.visibility = View.GONE
        profilePanel.visibility = View.GONE
        noticesPanel.visibility = View.GONE
        optionsPanel.visibility = View.GONE
        swipeRefresh.visibility = View.INVISIBLE
        bottomNav.visibility = View.VISIBLE
        // El suyo, el ÚLTIMO: el barrido de arriba apaga todos los paneles, este incluido.
        descargadosPanel.visibility = View.VISIBLE
        refrescarDescargados()
    }

    private fun refrescarDescargados() {
        if (!::descargadosAdapter.isInitialized) return
        val lista = almacenDescargas.listar()
        descargadosAdapter.submit(lista)
        descargadosEmpty.visibility = if (lista.isEmpty()) View.VISIBLE else View.GONE
        val hilos = if (lista.size == 1) "1 hilo" else "${lista.size} hilos"
        descargadosTotal.text =
            "$hilos · ${Descargas.tamanoLegible(almacenDescargas.bytesTotales())} en este móvil"
    }

    private fun confirmarBorrarCopia(d: HiloDescargado) {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("¿Borrar la copia?")
            .setMessage(
                "Se borra de este móvil \"${d.titulo}\" (${Descargas.tamanoLegible(d.bytes)}). " +
                    "Si el hilo ya no está en el foro, no hay forma de recuperarlo."
            )
            .setNegativeButton("No", null)
            .setPositiveButton("Borrar") { _, _ ->
                if (copiaAbierta == d.tid) cerrarCopia()
                almacenDescargas.borrar(d.tid)
                refrescarDescargados()
            }
            .show()
    }

    // ── Leer una copia ──────────────────────────────────────────────────────

    /**
     * Abre una copia guardada. A partir de aquí **no se le pide nada a ForoCoches**: las páginas
     * salen del fichero (ver [requestThreadPage]) y las imágenes del disco (ver
     * `PostImages.origenLocal`).
     */
    private fun abrirCopia(tid: String) {
        val json = almacenDescargas.leer(tid) ?: run { toast("No se pudo abrir la copia"); return }
        val raiz = try { org.json.JSONObject(json) } catch (_: Exception) { null }
            ?: run { toast("La copia está dañada"); return }
        val arr = raiz.optJSONArray("contenido") ?: return
        copiaPaginas = (0 until arr.length()).map { arr.optJSONObject(it).toString() }
        if (copiaPaginas.isEmpty()) { toast("La copia está vacía"); return }
        copiaAbierta = tid
        PostImages.origenLocal = { url -> almacenDescargas.imagen(tid, url) }
        currentThreadTid = tid
        currentThreadUrl = "https://forocoches.com/foro/showthread.php?t=$tid"
        postAdapter.submit(emptyList())
        showThread()
        contextoBar.text =
            "Copia guardada · " + java.text.SimpleDateFormat("d 'de' MMMM 'a las' HH:mm",
                java.util.Locale.getDefault()).format(java.util.Date(raiz.optLong("guardadoEn")))
        contextoBar.visibility = View.VISIBLE
        pintarPaginaDeCopia(1)
    }

    /** Se vuelve al foro de verdad: se sueltan las imágenes del disco y la franja. */
    private fun cerrarCopia() {
        if (copiaAbierta.isEmpty() && vistaDerivada.isEmpty()) return
        copiaAbierta = ""
        copiaPaginas = emptyList()
        // Si se sale mientras se buscaban sus mensajes, se para: si no, las páginas que
        // siguieran llegando se pintarían encima del SIGUIENTE hilo que abrieras.
        if (vistaDerivada.isNotEmpty() && modoRecorrido == "usuario" && descargando) {
            descargando = false
            modoRecorrido = "descarga"
            pintarDescarga()
        }
        vistaDerivada = ""
        usuarioBuscado = ""
        mensajesDelUsuario.clear()
        PostImages.origenLocal = null
        if (::contextoBar.isInitialized) contextoBar.visibility = View.GONE
    }

    private fun pintarPaginaDeCopia(page: Int) {
        val p = copiaPaginas.getOrNull(page - 1) ?: return
        onThreadJson(p)
    }


    private fun cancelarDescarga(aviso: String) {
        if (!descargando) return
        descargando = false
        descargaRecogidas.clear()
        pintarDescarga()
        toast(aviso)
    }

    private fun motivoDescarga(reason: String): String = when {
        reason.startsWith("restricted") -> "Este hilo no se puede leer entero"
        reason == "cloudflare" -> "El foro pide verificación: ábrelo y vuelve a intentarlo"
        reason == "login" -> "Hace falta iniciar sesión"
        else -> "No se pudo descargar el hilo"
    }

    /**
     * La franja, su texto y su barra se mueven SIEMPRE desde aquí y desde ningún otro sitio
     * (misma regla que la barra de avisos y la de páginas).
     */
    private fun pintarDescarga() {
        if (!::descargaBar.isInitialized) return
        if (!descargando) { descargaBar.visibility = View.GONE; return }
        descargaBar.visibility = View.VISIBLE
        descargaTexto.text = "Descargando · página $descargaSiguiente de $descargaPaginasTotales"
        descargaProgreso.progress =
            (100 * (descargaSiguiente - 1) / descargaPaginasTotales.coerceAtLeast(1))
    }

    /**
     * Repinta el indicador de página y la barra de páginas, SIEMPRE fuera del paso de layout.
     *
     * `onScrolled` del RecyclerView corre DENTRO de su propio layout, y tocar vistas ahí hace
     * que su `requestLayout()` caiga "durante el layout": Android lo pospone a un segundo pase
     * y, encadenado, lo pierde. Lo canta el log del sistema — `requestLayout() improperly
     * called by app:id/thread_page_info during layout: running second layout pass` — y el
     * resultado era que los chips quedaban añadidos pero **medidos a 0x0** (visto en
     * `dumpsys activity top`): la barra salía como un contenedor vacío y NO se recuperaba sola.
     * Es el fallo que capturó el dueño el 2026-08-15.
     */
    private val pintarIndicadores = Runnable {
        val lm = postList.layoutManager as? LinearLayoutManager
        val first = lm?.findFirstVisibleItemPosition() ?: -1
        if (first >= 0) showThreadPageInfo(postAdapter.pageAt(first))
        updatePageBar()
    }

    /**
     * Anota en la pila por dónde vas leyendo (página y post visible), sin apilar nada. Sin
     * esto, volver a un hilo desde un perfil te dejaría al principio de la página 1 — peor
     * que el fallo que estamos arreglando.
     */
    private fun rememberThreadPosition() {
        if (!isThreadVisible) return
        // Lo que hay en la lista son SUS mensajes, no el hilo: guardar esta posición haría que
        // al volver aterrizaras en un post suelto de otra página.
        if (vistaDerivada.isNotEmpty()) return
        val actual = nav.current as? Screen.Thread ?: return
        val lm = postList.layoutManager as? LinearLayoutManager ?: return
        val first = lm.findFirstVisibleItemPosition()
        if (first < 0) return
        val pid = postAdapter.pidAt(first)
        // findFirstVisibleItemPosition da el post PARCIALMENTE visible: guardando también su
        // desplazamiento se vuelve al píxel exacto y no un post más arriba.
        val offset = lm.findViewByPosition(first)?.top ?: 0
        val nuevo = actual.copy(page = postAdapter.pageAt(first), anchorPid = pid, anchorOffset = offset)
        if (nuevo != actual) nav.replaceTop(nuevo)
    }

    /**
     * Restaura una pantalla al retroceder. Es el ÚNICO sitio que pinta desde la pila, y por eso
     * llama a los `show*` con `remember = false`: si volvieran a apilar, el atrás no avanzaría
     * nunca y te dejaría dando vueltas.
     */
    private fun goTo(screen: Screen) {
        // OJO: showNative() y showThread() ocultan todos los paneles MENOS el del perfil ajeno
        // (solo lo hace hideAllStandardPanels, que ellos no llaman). Sin esta línea, volver de
        // un perfil dejaría el perfil pegado encima del hilo.
        if (screen !is Screen.Member) {
            memberPanel.visibility = View.GONE
            isMemberVisible = false
        }
        when (screen) {
            is Screen.ThreadList -> restaurarLista(screen)
            is Screen.Notices -> { restaurarNoticias = true; showNotices(screen.kind) }
            is Screen.Thread -> restoreThread(screen)
            is Screen.Member -> showMemberProfile(screen.uid, remember = false)
            is Screen.PmInbox -> showPmInbox(remember = false)
            is Screen.PmDetail -> showPmDetail(screen.pmid, screen.subject, remember = false)
            is Screen.UserActivity -> {
                restaurarNoticias = true
                showUserActivity(screen.usuario, screen.modo, remember = false, enHilo = screen.enHilo)
            }
            is Screen.Profile -> showProfile()
        }
    }

    /**
     * Salta a un post que YA está cargado (el caso normal al tocar una cita: quien te cita
     * suele estar en la misma página). Cero peticiones y con el mismo resaltado de 1,2 s que
     * usa el salto al mensaje recién publicado.
     */
    private fun saltarAPostCargado(pos: Int, pid: String) {
        // Se apila el DESTINO: como la entrada actual guarda por dónde ibas leyendo, el atrás
        // te devuelve ahí en vez de dejarte tirado en mitad del hilo.
        (nav.current as? Screen.Thread)?.let { nav.push(it.copy(anchorPid = pid, anchorOffset = 0)) }
        val lm = postList.layoutManager as LinearLayoutManager
        postList.post { lm.scrollToPositionWithOffset(pos, 0) }
        postAdapter.highlightPid = pid
        postList.postDelayed({ postAdapter.clearHighlight() }, 1200L)
    }

    /**
     * Vuelta a un hilo. Si es el que ya está en memoria se enseña tal cual (ni una petición,
     * y conservas el scroll); si es otro —hay UN solo adaptador de posts— hay que recargarlo
     * por su página y saltar al post por el que ibas.
     */
    /**
     * Volver a una lista de la pila.
     *
     * Hay que mirar **de qué lista se trata**: antes se hacía `showNative()` a secas, que enseña
     * la que estuviera cargada. Daba igual mientras las pestañas eran raíces (nunca se volvía a
     * una lista distinta de la actual), pero desde que el atrás lleva a Inicio sí importa: si
     * vienes de Citas y lo cargado era "Mis hilos", verías "Mis hilos" creyendo estar en Inicio.
     * Las `show*List` no recargan si ya estaban: el coste es cero cuando no hay que cambiar.
     */
    private fun restaurarLista(s: Screen.ThreadList) {
        when (s.source) {
            "favs" -> showFavsList()
            "mine" -> showMyThreadsList()
            "participated" -> showParticipatedList()
            "home", "top", "popurri" -> restaurarListaDeSubforo(s)
            // La sección +18 no usa el motor ni el subforo: se rehace entera. Apagada, a Inicio.
            "mas18" -> when {
                configMas18() == null -> showHomeList()
                // Si la lista sigue cargada se enseña tal cual: recargar tiraba páginas y scroll.
                listSource == "mas18" && listLoaded -> { showNative(); setSelectedNav(R.id.nav_home) }
                else -> { showMas18(); pintarPestanas() }   // que +18 sea la pestaña marcada
            }
            // La búsqueda se REHACE si lo cargado ya no son esos resultados (p. ej. volviste
            // pasando por Inicio). Si siguen ahí no se pide nada: repetir la consulta por
            // gusto es lento y además la búsqueda de FC va justa.
            // El modo viaja en la entrada: sin restaurarlo, volver a una búsqueda por
            // mensajes la repetía en el modo que tuviera el interruptor en ese momento.
            "search" -> {
                searchTitleOnly = !s.porMensajes
                if (s.porMensajes) {
                    // El panel de avisos no cachea nada suyo: se rehace siempre, igual que
                    // "sus mensajes" (showUserActivity) cuando vuelves a él.
                    searchQuery = s.query
                    searchUser = s.usuario
                    restaurarNoticias = true      // volver a la fila en la que ibas
                    runSearch(apilar = false)
                } else if (Restauracion.hayQueRehacerBusqueda(
                        BusquedaPorUsuario.clave(s.query, s.usuario), listSource,
                        BusquedaPorUsuario.clave(searchQuery, searchUser), listLoaded)) {
                    // La clave lleva el usuario: "tesla de @x" no es lo mismo que "tesla".
                    searchQuery = s.query
                    searchUser = s.usuario
                    runSearch(apilar = false)
                } else { showNative(); setSelectedNav(navIdForList()) }
            }
            // La actividad de un usuario cuelga de lo que ya hubiera: se enseña tal cual, que es
            // el comportamiento de siempre.
            else -> { showNative(); setSelectedNav(navIdForList()) }
        }
    }

    /**
     * Volver a un subforo, a sus hilos del momento o al Popurrí. Si es lo que sigue pintado no
     * se pide nada y la lista se queda en el hilo por el que ibas; si no, se rehace ESA lista y
     * no Inicio (ver [Restauracion.hayQueRehacerLista]).
     */
    private fun restaurarListaDeSubforo(s: Screen.ThreadList) {
        if (!Restauracion.hayQueRehacerLista(s.source, s.forumId, listSource, currentForumId, listLoaded)) {
            showNative(); setSelectedNav(R.id.nav_home)
            return
        }
        if (s.forumId > 0 && s.source != "popurri") currentForumId = s.forumId
        listLoaded = false
        adapter.submit(emptyList())
        when (s.source) {
            "popurri" -> showPopurri()
            "top" -> { showNative(); ponerModoTop(true) }
            else -> showHomeList()
        }
        // Que la pestaña marcada sea la de la lista que se enseña.
        pintarPestanas()
    }

    private fun restoreThread(s: Screen.Thread) {
        val hiloEnPantalla = mismoHiloCargado(s.url)
        val anclaCargada = s.anchorPid.isNotEmpty() && postAdapter.indexOfPid(s.anchorPid) >= 0
        if (!Restauracion.hayQueRecargar(
                hiloEnPantalla, s.anchorPid.isNotEmpty(), anclaCargada, s.page, threadPage
            )
        ) {
            showThread()
            val pos = postAdapter.indexOfPid(s.anchorPid)
            if (pos >= 0) {
                val lm = postList.layoutManager as LinearLayoutManager
                postList.post { lm.scrollToPositionWithOffset(pos, s.anchorOffset) }
            }
            return
        }
        // Volver no es "saltar a un mensaje": se recupera el píxel exacto y NO se resalta nada
        // (el resaltado significa "este es tu mensaje" y aquí mentiría).
        pendingScrollOffset = s.anchorOffset
        pendingScrollPid = s.anchorPid
        if (hiloEnPantalla) {
            // Ya está el hilo en pantalla: basta con traer SU página, sin recargarlo entero.
            showThread()
            jumpToThreadPage(s.page, apilar = false)
        } else {
            openThreadNative(s.url, "", remember = false, startPage = s.page)
            // openThreadNative limpia pendingScrollPid/Offset con el pid de la URL: se
            // restauran después, o el atrás aterrizaría donde diga la URL y no donde ibas.
            if (s.anchorPid.isNotEmpty()) {
                pendingScrollPid = s.anchorPid
                pendingScrollOffset = s.anchorOffset
            }
        }
    }

    /** El hilo de [url] es el que ya está pintado (aunque sea por otra página). */
    private fun mismoHiloCargado(url: String): Boolean =
        currentThreadUrl.isNotEmpty() && url.startsWith(currentThreadUrl) && postAdapter.itemCount > 0

    /**
     * Cambio de página: carga esa página REEMPLAZANDO la lista, nunca trayendo las
     * intermedias (en un hilo de 1000 páginas eso sería suicida). Es el ÚNICO camino para
     * moverse por el hilo desde que se retiró el scroll infinito.
     */
    /**
     * @param apilar false cuando se llama DESDE el atrás: restaurar una página no es navegar a
     *   ella. Apilando aquí, volver dejaría otra entrada encima y el atrás se quedaría dando
     *   vueltas en la misma página.
     */
    private fun jumpToThreadPage(page: Int, alFinal: Boolean = false, apilar: Boolean = true) {
        val p = page.coerceIn(1, threadPageCount)
        // Cambiar de página APILA: el atrás devuelve a la página anterior en vez de sacarte del
        // hilo de un golpe (lo pidió el dueño). Solo se apilan las que hayas visitado — yendo
        // 1→5→12 el atrás hace 12→5→1→lista, no veinte pasos — y la pila tiene tope.
        // La entrada que se deja atrás ya lleva su página y su post (los anota
        // `rememberThreadPosition` al hacer scroll), así que se vuelve a donde ibas leyendo.
        if (apilar) (nav.current as? Screen.Thread)?.let { actual ->
            if (actual.page != p) nav.push(actual.copy(page = p, anchorPid = "", anchorOffset = 0))
        }
        loadingThreadPage = false          // un salto siempre manda sobre la carga en curso
        replaceOnLoad = true
        pendingIrAlFinal = alFinal
        requestThreadPage(p)
    }

    // OJO: NADA de goto=lastpost / goto=newpost. Verificado por CDP que FC los IGNORA:
    // devuelve 200 con la PÁGINA 1 (sin redirección y sin <link rel="next/prev">), así que
    // el salto parecía no hacer nada. La última página se pide por page=threadPageCount,
    // que sí funciona. "Primer mensaje sin leer" queda fuera hasta encontrar señal fiable.

    /** Hoja de salto: se abre tocando el "Página X de Y" de la cabecera. */
    private fun showPageJumpSheet() {
        if (currentThreadTid.isEmpty()) return
        val view = layoutInflater.inflate(R.layout.sheet_thread_pages, null)
        val sheet = com.google.android.material.bottomsheet.BottomSheetDialog(this)
        sheet.setContentView(view)
        // Sin esto la fila del "Ir a página N" queda DEBAJO de la barra de navegación
        // del sistema y es inalcanzable (el sheet se dibuja a pantalla completa).
        val basePad = view.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(view) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            v.setPadding(v.paddingLeft, v.paddingTop, v.paddingRight,
                basePad + maxOf(bars.bottom, ime.bottom))
            insets
        }
        view.findViewById<TextView>(R.id.jump_range).text =
            if (threadPageCount > 1) "Este hilo tiene $threadPageCount páginas"
            else "Este hilo tiene una sola página"
        val input = view.findViewById<EditText>(R.id.jump_input)
        fun go(block: () -> Unit) { sheet.dismiss(); block() }
        view.findViewById<View>(R.id.jump_last)
            .setOnClickListener { go { jumpToThreadPage(threadPageCount, alFinal = true) } }
        view.findViewById<View>(R.id.jump_first).setOnClickListener { go { jumpToThreadPage(1) } }
        view.findViewById<View>(R.id.jump_go).setOnClickListener {
            val n = input.text.toString().trim().toIntOrNull()
            if (n == null || n < 1 || n > threadPageCount) {
                toast("Introduce una página entre 1 y $threadPageCount")
            } else go { jumpToThreadPage(n) }
        }
        sheet.show()
    }

    // ── Encuesta del hilo ────────────────────────────────────────────────────

    /** Enseña la barra "Encuesta · N votos" solo en los hilos que traen encuesta. */
    private fun updatePollBar() {
        val p = currentPoll
        val vis = if (p == null) View.GONE else View.VISIBLE
        pollBar.visibility = vis
        pollBarDivider.visibility = vis
        if (p != null) pollBarText.text = pollSummary(p)
    }

    /** Hoja de la encuesta. Sin sesión ofrece el login NATIVO, nunca la capa web. */
    private fun showPollSheet() {
        val p = currentPoll ?: return
        val sheet = PollSheet(
            context = this,
            onVote = { nums -> sendPollVote(p, nums) },
            onLoginNeeded = { showLogin() }
        )
        pollSheet = sheet
        sheet.show(p, isLoggedIn())
    }

    /** Manda el voto por el motor; la encuesta actualizada vuelve por [onPollResultJson]. */
    private fun sendPollVote(poll: Poll, nums: List<String>) {
        // Votar es publicar algo público atado a la cuenta que haya en las cookies AHORA MISMO:
        // la hoja de la encuesta puede seguir abierta durante un cambio en curso.
        if (cambiandoDeCuenta) { pollSheet?.showError("Espera a que termine el cambio de cuenta"); return }
        if (currentThreadTid.isEmpty() || poll.id.isEmpty() || nums.isEmpty()) {
            pollSheet?.showError("No se pudo identificar la encuesta")
            return
        }
        val arr = nums.joinToString(",") { "'${jsEscape(it)}'" }
        webView.evaluateJavascript(
            "window.fcPollVote&&fcPollVote('${jsEscape(currentThreadTid)}'," +
                "'${jsEscape(poll.id)}',[$arr])",
            null
        )
    }

    /** Resultado del voto: repinta barra y hoja, o enseña el error REAL que dio FC. */
    private fun onPollResultJson(json: String) {
        val o = try { org.json.JSONObject(json) } catch (_: Exception) { null }
        if (o == null) { pollSheet?.showError(""); return }
        val poll = parsePoll(o.optJSONObject("poll"))
        if (poll != null) {
            currentPoll = poll
            updatePollBar()
        }
        if (!o.optBoolean("ok", false)) {
            pollSheet?.showError(o.optString("error"))
            return
        }
        toast("Voto enviado ✓")
        // Sin encuesta de vuelta no hay nada nuevo que pintar: se cierra en vez de dejar
        // la hoja con el estado viejo, que parecería que el voto no ha contado.
        if (poll != null) pollSheet?.update(poll) else pollSheet?.dismiss()
    }

    /** pid de una URL de post: showthread.php?p=NNN o .../showthread.php?t=1#post NNN. */
    private fun pidFromUrl(url: String): String =
        Regex("[?&]p=(\\d+)").find(url)?.groupValues?.get(1)
            ?: Regex("#post(\\d+)").find(url)?.groupValues?.get(1) ?: ""

    /**
     * Ejecuta la decisión del [IntentRouter] con la app VIVA (onNewIntent). REGLA DE ORO:
     * ningún destino es la capa web. Los diferidos (motor/menú aún no listos) se guardan y los
     * reintenta onEnginePageReady (hilo/MP) u onThreadListJson (citas).
     */
    /**
     * Entrar por una NOTIFICACIÓN cae a media película: no hay historia que deshacer. Se deja
     * la pila como "inicio → lo que abrió la notificación", así que el primer atrás te lleva a
     * la app y el segundo sale. Lo contrario —salir de golpe— deja al usuario fuera sin haber
     * visto la app, y una historia inventada sería mentir.
     */
    private fun rootFromNotification(destino: Screen) {
        nav.root(Screen.ThreadList(listSource, currentForumId))
        nav.push(destino)
    }

    private fun executeRoute(route: Route) {
        when (route.target) {
            Target.PM_INBOX -> if (route.deferred) pendingDeepLink = route.url
                else { showPmInbox(remember = false); rootFromNotification(Screen.PmInbox) }
            Target.THREAD -> if (route.deferred) pendingDeepLink = route.url
                else {
                    openThreadNative(route.url, "", markReadUnknown = true, remember = false)
                    rootFromNotification(Screen.Thread(route.url))
                }
            Target.HOME -> { showNative(); setSelectedNav(navIdForList()) }
        }
    }

    /**
     * Reintento de un deep link de hilo/MP guardado como pendiente (motor no listo al llegar).
     * Lo llama onEnginePageReady. REGLA DE ORO: solo destinos nativos, nunca la capa web.
     */
    private fun openDeepLink(url: String): Boolean {
        if (!TrustedOrigins.isTrustedForocochesUrl(url)) return false
        // Deep link pendiente del arranque en frío: mismo criterio que rootFromNotification.
        if (url.contains("private.php")) {
            showPmInbox(remember = false); rootFromNotification(Screen.PmInbox); return true
        }
        if (!url.contains("showthread.php")) return false
        openThreadNative(url, "", markReadUnknown = true, remember = false)
        rootFromNotification(Screen.Thread(url))
        return true
    }

    /**
     * @param markReadUnknown el hilo se abre SIN pasar por la lista (notificación, cita/
     *   mención o deep link): no se conoce el nº de respuestas real en este momento (a
     *   diferencia del click en la lista, que lo lee de la fila y marca leído él mismo antes
     *   de llamar aquí). Se resuelve más tarde, en onThreadJson, con
     *   [pendingMarkReadUnknown].
     */
    private fun openThreadNative(
        url: String,
        title: String,
        markReadUnknown: Boolean = false,
        remember: Boolean = true,
        startPage: Int = 1
    ) {
        if (remember) nav.push(Screen.Thread(url))
        currentThreadUrl = url.substringBefore("&page=")
        currentThreadTid = Regex("[?&]t=(\\d+)").find(url)?.groupValues?.get(1) ?: ""
        pendingMarkReadUnknown = markReadUnknown
        threadPage = 1
        threadPageCount = 1
        replaceOnLoad = false
        // Si la URL apunta a un post concreto (?p= o #postN), al cargar se salta a él en vez
        // de dejar al usuario buscándolo a mano por el scroll. FC sirve en ?p= la página que
        // contiene ese post, así que siempre está entre los que llegan.
        pendingScrollPid = pidFromUrl(url)
        pendingScrollOffset = null
        pendingScrollToLast = false
        buscandoMiMensaje = false
        postAdapter.clear()
        // Autor del hilo: lo que sepamos de antes (si no, se resuelve al llegar la página 1).
        // Fijarlo AQUÍ es también lo que borra el del hilo anterior, para que el recuadro no
        // se arrastre de un hilo a otro.
        postAdapter.starterAuthor =
            currentThreadTid.toLongOrNull()?.let { creatorCache.get(it) }.orEmpty()
        // La encuesta es del hilo anterior: fuera antes de que llegue la primera página.
        currentPoll = null
        pollSheet?.dismiss()
        pollSheet = null
        updatePollBar()
        // Las citas y el borrador YA NO se borran a ciegas: solo si de verdad cambias de
        // hilo. Antes, volver al MISMO hilo (típico al ir recogiendo citas desde la pantalla
        // de Citas) contaba como hilo nuevo y se perdía lo que llevabas. Como las citas se
        // abren con URLs `?p=NNN`, que NO llevan el número de hilo, la comparación no siempre
        // se puede hacer aquí: la decide onThreadJson en cuanto se sabe el tid.
        val tidDeLaUrl = Regex("""[?&]t=(\d+)""").find(url)?.groupValues?.get(1) ?: ""
        if (tidDeLaUrl.isNotEmpty() && quotesThreadTid.isNotEmpty() && tidDeLaUrl != quotesThreadTid) {
            descartarBorrador()
        }
        replySubject.setText("")
        editingPid = ""
        replyMode = "reply"
        if (isReplyVisible) { isReplyVisible = false; replyPanel.visibility = View.GONE }
        restrictedView.visibility = View.GONE
        threadTitle.text = CompartirFC.tituloLimpio(title)
        tituloDesplegado = false
        pintarTituloHilo()
        // La miga es del hilo anterior: fuera hasta que la primera página diga el suyo.
        threadForumFid = 0
        threadForumName = ""
        pintarMigaHilo()
        threadPageInfo.text = ""
        threadPageInfo.visibility = View.GONE
        // La barra de páginas es del hilo anterior: fuera hasta que updatePageBar() la
        // repinte con los datos del nuevo hilo (evita un parpadeo con números viejos).
        // Se apaga LLAMANDO a updatePageBar (con threadPageCount ya a 1, su rama "!on"), y no
        // a mano: la barra, su divisoria y pageBarState son tres cosas que deben moverse
        // juntas. Apagar solo la barra dejaba la raya de 1dp huérfana delimitando un hueco
        // vacío, y el estado rancio se saltaría el siguiente repintado sin que nada lo delate.
        updatePageBar()
        // El estado de favorito NO viene en la página del hilo (el botón de FC es estático),
        // así que se deja hueca y la decide `pintarEstrella()` en cuanto `onThreadJson` trae el
        // tid. Apagarla aquí no es opcional: sin esto se quedaría encendida la del hilo
        // anterior, que es peor mentira que la que se está arreglando.
        pintarEstrella(false)
        threadFav.isEnabled = true
        showThread()
        // Al volver a un hilo desde la pila se recupera la página en la que lo dejaste, no
        // la 1: si no, el atrás te castigaría con veinte páginas de scroll.
        if (startPage > 1) { replaceOnLoad = true; requestThreadPage(startPage) } else requestThreadPage(1)
    }

    private fun requestThreadPage(page: Int) {
        // Leyendo una copia, las páginas salen del fichero: es el sentido de haberla guardado.
        if (copiaAbierta.isNotEmpty()) { pintarPaginaDeCopia(page); return }
        // Y una vista derivada (sus mensajes) no tiene páginas que pedirle a FC: ya está todo.
        if (vistaDerivada.isNotEmpty()) return
        if (!engineReady || loadingThreadPage || currentThreadUrl.isEmpty()) return
        loadingThreadPage = true
        if (page <= 1 && !threadRefresh.isRefreshing) threadLoading.visibility = View.VISIBLE
        webView.evaluateJavascript(
            "window.fcLoadThread&&fcLoadThread('${threadPageUrl(page)}')", null
        )
    }

    private fun onThreadJson(json: String) {
        loadingThreadPage = false
        threadLoading.visibility = View.GONE
        threadRefresh.isRefreshing = false
        restrictedView.visibility = View.GONE
        // Una copia descargada trae la marca de "conectado" de cuando se bajó: no vale para ahora.
        val t = parseThreadPayload(json)
            ?.let { it.copy(posts = Presencia.paraMostrar(it.posts, esCopia = copiaAbierta.isNotEmpty())) }
            ?: return
        // Respuesta tardía de otro hilo (el user ya abrió otro): descartar.
        if (!t.url.startsWith(currentThreadUrl)) return
        // Hilos abiertos por enlace p= (citas/menciones, y el "último mensaje" del listado):
        // canonicaliza a t= para que la paginación y responder funcionen con normalidad.
        // Es OBLIGATORIO, no una comodidad: FC ignora `page=` si la URL lleva `p=`, así que
        // sin esto todas las páginas del hilo devuelven la misma (ver UrlHilo).
        currentThreadUrl = UrlHilo.canonicalizar(currentThreadUrl, t.tid)
        // Citas/borrador de OTRO hilo: ahora que se sabe el tid real, fuera. (Las citas se
        // abren por `?p=` y hasta aquí no se sabía a qué hilo pertenecían.)
        if (t.tid.isNotEmpty() && quotesThreadTid.isNotEmpty() && t.tid != quotesThreadTid) {
            descartarBorrador()
        }
        if (currentThreadTid.isEmpty() && t.tid.isNotEmpty()) {
            currentThreadTid = t.tid
            updateQuickReplyVisibility()
        }
        // La estrella se pinta AQUÍ y no al abrir: entrando por `?p=` el tid no se conoce hasta
        // que contesta el motor, y sin tid no hay nada que consultar.
        pintarEstrella()
        if (pendingMarkReadUnknown && currentThreadTid.isNotEmpty()) {
            pendingMarkReadUnknown = false
            markReadUnknownReplies(currentThreadTid, t.page, t.pageCount, t.posts.size)
        }
        // Los contadores de una COPIA son de cuando se guardó, y una vista derivada no los
        // trae siquiera: pintarlos movería las insignias con datos viejos o con ceros. Solo el
        // hilo vivo dice algo del estado de tu cuenta.
        if (copiaAbierta.isEmpty() && vistaDerivada.isEmpty()) {
            updateBadges(t.pmCount, t.quotesCount, t.mentionsCount)
        }
        // Sin el "- Página N" que pone vBulletin (gotcha 22): la página ya la dice la pastilla.
        if (t.title.isNotEmpty()) threadTitle.text = CompartirFC.tituloLimpio(t.title)
        if (t.forumFid > 0) {
            threadForumFid = t.forumFid
            threadForumName = t.forumName
            pintarMigaHilo()
        }
        // Encuesta: solo se pisa si la página traída trae una. Si no la trae NO se apaga, que
        // un hilo con encuesta la sigue teniendo aunque estemos leyendo la página 7.
        if (t.poll != null) {
            currentPoll = t.poll
            updatePollBar()
            if (pollSheet?.isShowing() == true) pollSheet?.update(t.poll)
        }
        threadPageCount = t.pageCount
        threadPage = t.page
        showThreadPageInfo(t.page)

        // Autor del hilo (para recuadrar sus mensajes). Se lee de t.posts SIN filtrar: si el
        // creador está en tu lista de ignorados sus posts no se pintan igual, pero el primero
        // de la página 1 sigue siendo la única señal de quién abrió el hilo.
        ThreadStarter.of(t.posts, t.page).takeIf { it.isNotEmpty() }?.let { starter ->
            postAdapter.starterAuthor = starter
            currentThreadTid.toLongOrNull()?.let { creatorCache.put(it, starter) }
        }

        // Filtro de ignorados, mismas reglas que content.js: autor ignorado, post
        // colapsado por FC ("oculto porque") o post que cita a un ignorado.
        val ignored = repo.getIgnoredUsers().map { it.lowercase() }
        val visible = t.posts.filter { p ->
            if (p.author.isNotEmpty() && ignored.contains(p.author.lowercase())) return@filter false
            val body = p.html.lowercase()
            if (body.contains("oculto porque")) return@filter false
            if (ignored.any { body.contains("<b>$it dijo:</b>") }) return@filter false
            true
        }
        replaceOnLoad = false
        // Se guarda ANTES de poblar la lista: es la referencia para el fallback de abajo
        // (pendingScrollToLast) — solo se fía de lastPid() si esta carga TRAJO algo nuevo al
        // final. Si threadPageCount estaba desfasado (tu respuesta abrió una página nueva que
        // esta carga no llega a ver), la página pedida no aporta nada al final y lastPid()
        // sale igual que antes → no se resalta un post ajeno como si fuera el tuyo.
        val previousLastPid = postAdapter.lastPid()
        // Paginación clásica: la página cargada SUSTITUYE a la anterior y se empieza por
        // arriba. Ya no hay append (scroll infinito) ni prepend (carga hacia atrás): en la
        // lista solo conviven posts de una misma página.
        postAdapter.submit(visible)
        postList.scrollToPosition(0)
        if (pendingIrAlFinal) {
            pendingIrAlFinal = false
            // `post` y no directo: la lista acaba de recibir los datos y aún no ha medido nada.
            if (postAdapter.itemCount > 0) {
                postList.post { postList.scrollToPosition(postAdapter.itemCount - 1) }
            }
        }

        // Tu mensaje abrió una página nueva: la que ha llegado era la última de ANTES de
        // publicar. Se pide la nueva última conservando lo que se buscaba (el pid o "el último").
        if (buscandoMiMensaje) {
            buscandoMiMensaje = false
            val esta = if (pendingScrollPid.isNotEmpty()) postAdapter.indexOfPid(pendingScrollPid) >= 0
                       else postAdapter.lastPid() != previousLastPid
            val otra = paginaDeMiMensaje(esta, t.page, t.pageCount)
            if (otra != null) {
                if (pendingScrollPid.isEmpty()) pendingScrollToLast = true
                postList.post { jumpToThreadPage(otra, apilar = false) }
                return
            }
        }
        // Salto al post citado, o al mensaje recién publicado. DESPUÉS de poblar la lista.
        if (pendingScrollToLast) {
            pendingScrollToLast = false
            // Solo se acepta el último post si esta carga REALMENTE trajo algo nuevo al
            // final (lastPid cambió). Si no cambió (p. ej. threadPageCount desfasado y la
            // página pedida ya no es la última tras responder), no hay pid fiable que
            // resaltar: se deja pendingScrollPid como estaba, casi siempre "".
            val newLastPid = postAdapter.lastPid()
            if (newLastPid.isNotEmpty() && newLastPid != previousLastPid) pendingScrollPid = newLastPid
        }
        if (pendingScrollPid.isNotEmpty()) {
            val pos = postAdapter.indexOfPid(pendingScrollPid)
            if (pos >= 0) {
                val pid = pendingScrollPid
                val restaurando = pendingScrollOffset != null
                val offset = pendingScrollOffset ?: 0
                pendingScrollPid = ""
                pendingScrollOffset = null
                val lm = postList.layoutManager as LinearLayoutManager
                postList.post { lm.scrollToPositionWithOffset(pos, offset) }
                if (!restaurando) {
                    // Resaltado de 1,2 s para que se vea cuál es tu mensaje.
                    postAdapter.highlightPid = pid
                    postList.postDelayed({ postAdapter.clearHighlight() }, 1200L)
                }
            }
        }
        postList.post { updatePageBar() }
    }

    private fun onThreadError(reason: String) {
        loadingThreadPage = false
        replaceOnLoad = false
        pendingScrollToLast = false
        buscandoMiMensaje = false
        pendingIrAlFinal = false
        pendingScrollOffset = null
        threadLoading.visibility = View.GONE
        threadRefresh.isRefreshing = false
        when {
            reason == "cloudflare" -> {
                // Único caso donde asoma el WebView: el challenge solo lo resuelve
                // un navegador visible. Al pasarlo, atrás vuelve al hilo nativo.
                cameFromThread = true
                showWeb()
                webView.loadUrl(threadPageUrl(threadPage))
            }
            reason == "login" -> {
                // Página que pide identificarse: NUESTRO login, nunca el foro.
                // Al entrar se reabre este hilo automáticamente.
                pendingThreadUrl = currentThreadUrl
                pendingThreadTitle = threadTitle.text.toString()
                showLogin()
            }
            reason.startsWith("restricted") -> {
                // FC redirigió a su página informativa (+HD). Reproducimos SU info
                // con nuestros estilos, dentro del panel de hilo nativo.
                var msg = ""; var meta = ""; var invite = ""
                try {
                    val o = org.json.JSONObject(reason.substringAfter("restricted:", "{}"))
                    msg = o.optString("msg"); meta = o.optString("meta"); invite = o.optString("invite")
                } catch (_: Exception) { }
                showRestricted(msg, meta, invite)
            }
            else -> {
                android.util.Log.w("FC_SHELL", "thread error: $reason (styleid=$skinStyleid)")
                // Un hilo que llega vacío es el síntoma de que FC nos sirve un diseño que no
                // sabemos leer. Puede haber cambiado HACE UN MOMENTO desde el navegador, así
                // que se vuelve a preguntar con una página nueva en vez de fiarse del styleid
                // que se leyó al arrancar.
                if (reason == "empty") recheckSkin()
                // El foro de debajo NO se enseña: error nativo y de vuelta a la lista.
                if (postAdapter.itemCount == 0) {
                    // CANARIO: un hilo con posts que llega vacío casi siempre significa que
                    // el HTML no es el que sabemos leer. Decirlo, en vez del genérico "no se
                    // pudo cargar", que es lo que dejó a un tester a ciegas.
                    toast(skinWarning() ?: "No se pudo cargar el hilo")
                    showNative()
                }
            }
        }
    }

    /**
     * Links dentro de posts. La regla de oro exige NO caer a la capa web: lo que la app
     * sabe pintar en nativo se abre en nativo; solo lo que no (perfiles de miembro, páginas
     * sueltas de FC) va al NAVEGADOR EXTERNO — nunca al WebView motor visible.
     */
    private fun onPostLinkClick(url: String) {
        // Tocar la cabecera de una cita lleva al mensaje citado. Si ya está en pantalla no se
        // recarga NADA: se salta y se resalta, y se apila la posición de lectura para que el
        // atrás te devuelva justo donde estabas (sin eso, saltar da miedo en un hilo largo).
        if (isThreadVisible && url.contains("showthread.php")) {
            val pid = Regex("""[?&]p=(\d+)""").find(url)?.groupValues?.get(1).orEmpty()
            val pos = if (pid.isEmpty()) -1 else postAdapter.indexOfPid(pid)
            if (pos >= 0) { saltarAPostCargado(pos, pid); return }
        }
        when {
            url.contains("showthread.php") && TrustedOrigins.isTrustedForocochesUrl(url) ->
                openThreadNative(url.substringBefore("&page="), "")
            // Subforo → lista nativa (antes abría la web).
            url.contains("forumdisplay.php") && TrustedOrigins.isTrustedForocochesUrl(url) -> {
                val fid = Regex("[?&]f=(\\d+)").find(url)?.groupValues?.get(1)?.toIntOrNull()
                if (fid != null) openForumNative(fid) else openExternal(url)
            }
            // MPs → bandeja nativa.
            url.contains("private.php") && TrustedOrigins.isTrustedForocochesUrl(url) -> showPmInbox()
            // Perfil de usuario (mención): el enlace trae el uid real → perfil NATIVO.
            url.contains("member.php") && TrustedOrigins.isTrustedForocochesUrl(url) -> {
                val uid = Regex("[?&]u=(\\d+)").find(url)?.groupValues?.get(1)
                if (uid != null && uid != "0") showMemberProfile(uid) else openExternal(url)
            }
            // Resto de FC (misc.php, etc.): navegador externo, NO la capa web.
            else -> openExternal(url)
        }
    }

    /** Abre un subforo concreto en la lista nativa de Inicio (selecciona su pestaña si existe). */
    private fun openForumNative(fid: Int) {
        currentForumId = fid
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putInt(PREF_LAST_FID, fid).apply()
        listSource = "home"
        forumTabs.visibility = View.VISIBLE
        // Si el subforo es una de las pestañas, seleccionarla; si no, cargarlo igualmente.
        var tabIdx = -1
        for (i in 0 until forumTabs.tabCount) if (forumTabs.getTabAt(i)?.tag == fid) { tabIdx = i; break }
        listLoaded = false
        adapter.submit(emptyList())
        showNative()
        setSelectedNav(R.id.nav_home)
        if (tabIdx >= 0) forumTabs.getTabAt(tabIdx)?.select() else requestThreadList(1)
    }

    /**
     * Vídeo de embed a pantalla completa: el reproductor pide mostrar su vista custom;
     * la ponemos sobre todo, ocultando las barras del sistema. NO es el foro (es el
     * reproductor de X/YouTube/TikTok), así que la regla de oro no aplica.
     */
    private fun onEmbedFullscreen(view: View?, callback: android.webkit.WebChromeClient.CustomViewCallback?) {
        val decor = window.decorView as android.view.ViewGroup
        val controller = WindowInsetsControllerCompat(window, decor)
        if (view != null) {
            fullscreenView = view
            fullscreenCallback = callback
            decor.addView(view, android.view.ViewGroup.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.MATCH_PARENT
            ))
            controller.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            fullscreenView?.let { decor.removeView(it) }
            fullscreenView = null
            try { fullscreenCallback?.onCustomViewHidden() } catch (_: Exception) {}
            fullscreenCallback = null
            controller.show(WindowInsetsCompat.Type.systemBars())
            // El reproductor a veces gira a horizontal y no revierte: volver a vertical.
            requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            window.decorView.post {
                requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            }
        }
    }

    /** Abre una URL en el navegador externo (nunca en el WebView motor visible). */
    /**
     * La URL con la que se ha abierto la app: el extra de las notificaciones, o el enlace que
     * el usuario ha tocado fuera (`ACTION_VIEW`, desde que la app reclama los de hilo).
     * `IntentRouter` la sanea después con [TrustedOrigins], así que de aquí no puede salir un
     * destino ajeno.
     */
    private fun urlDelIntent(i: android.content.Intent?): String? =
        i?.getStringExtra("url") ?: i?.data?.toString()

    /**
     * Abre algo FUERA de la app. Es la salida de emergencia de la regla de oro: lo que no
     * sabemos pintar en nativo va al navegador, nunca a la capa web.
     *
     * **Desde que la app reclama los enlaces de ForoCoches hay que apuntar al navegador
     * EXPLÍCITAMENTE**: un `ACTION_VIEW` a pelo puede resolver a nosotros mismos, y entonces la
     * salida de emergencia se convierte en un bucle que devuelve al usuario justo a la pantalla
     * de la que se le quería sacar.
     */
    private fun openExternal(url: String) {
        val i = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url))
            .addCategory(android.content.Intent.CATEGORY_BROWSABLE)
        navegadorPorDefecto()?.let { i.setPackage(it) }
        try {
            startActivity(i)
        } catch (_: Exception) {
            // Sin navegador que lo resuelva, el diálogo del sistema antes que no hacer nada.
            try {
                startActivity(android.content.Intent.createChooser(
                    android.content.Intent(android.content.Intent.ACTION_VIEW,
                        android.net.Uri.parse(url)), "Abrir con"))
            } catch (_: Exception) { }
        }
    }

    /**
     * Paquete del navegador por defecto. Se resuelve contra un dominio que NO reclamamos, para
     * que la respuesta no sea la propia app.
     */
    private fun navegadorPorDefecto(): String? = try {
        val sonda = android.content.Intent(
            android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://example.com")
        ).addCategory(android.content.Intent.CATEGORY_BROWSABLE)
        packageManager.resolveActivity(sonda, android.content.pm.PackageManager.MATCH_DEFAULT_ONLY)
            ?.activityInfo?.packageName?.takeIf { it != packageName && it != "android" }
    } catch (_: Exception) { null }

    // ── Capas ────────────────────────────────────────────────────────────────

    /**
     * Dejar de ver el hilo. **Único sitio que baja `isThreadVisible`**, y por eso existe.
     *
     * Apaga el reproductor que se hubiera quedado apartado al reciclar su mensaje: seguir
     * oyendo un vídeo mientras miras otra pantalla no lo ha pedido nadie. Estaba solo en
     * `showNative()`, así que salir por la barra de abajo —a Citas, Menciones, Perfil o los
     * MPs, que es la salida natural— dejaba el audio sonando: el fallo que reportó Alberto el
     * 01-09 y repitió Alfa el 07-09. Cinco sitios bajaban la bandera y solo uno apagaba.
     *
     * El `if` no es decorativo: es también lo que garantiza que `postAdapter` ya existe
     * — `showNative()` se llama durante `onCreate` ANTES de `configureThreadPanel()`.
     */
    private fun dejarElHilo() {
        if (isThreadVisible) {
            postAdapter.soltarEmbedEnUso()
            // Y además los que sigan EN PANTALLA: soltar solo apaga el aparcado, y un tweet
            // visible se quedaba sonando encima del listado.
            postAdapter.pausarEmbeds(postList, pausar = true)
        }
        isThreadVisible = false
        // Salir de un hilo cierra la copia si la había: si no, `requestThreadPage` seguiría
        // sirviendo páginas del fichero al SIGUIENTE hilo que abrieras, y sus imágenes
        // saldrían del disco de otra copia. Es el único sitio por el que se sale de un hilo.
        cerrarCopia()
    }

    private fun showNative() {
        dejarElHilo()
        isWebVisible = false
        isReplyVisible = false
        isLoginVisible = false
        isNoticesVisible = false
        isProfileVisible = false
        isOptionsVisible = false
        cameFromThread = false
        hidePmPanels()
        nativePanel.visibility = View.VISIBLE
        threadPanel.visibility = View.GONE
        replyPanel.visibility = View.GONE
        loginPanel.visibility = View.GONE
        noticesPanel.visibility = View.GONE
        descargadosPanel.visibility = View.GONE
        profilePanel.visibility = View.GONE
        optionsPanel.visibility = View.GONE
        if (::organizarPanel.isInitialized) organizarPanel.visibility = View.GONE
        if (::novedadesPanel.isInitialized) { novedadesPanel.visibility = View.GONE; isNovedadesVisible = false }
        bottomNav.visibility = View.VISIBLE
        // Si las pestañas se repintaron mientras el listado estaba escondido, ahora es cuando
        // se puede dejar la marcada a la vista (una vista oculta no se desplaza).
        encuadrarPestanas()
        // FAB de crear hilo: solo en un subforo real y con sesión.
        fabNewThread.visibility =
            if (sobreUnSubforo() && isLoggedIn()) View.VISIBLE else View.GONE
        // invisible (no gone): el WebView sigue vivo debajo como motor de datos.
        swipeRefresh.visibility = View.INVISIBLE
    }

    private fun showThread() {
        isWebVisible = false
        // Volver al hilo devuelve la vida a los reproductores que se callaron al salir (ver
        // dejarElHilo). NO les da al play: solo deja de tenerlos suspendidos.
        if (!isThreadVisible && ::postAdapter.isInitialized) {
            postAdapter.pausarEmbeds(postList, pausar = false)
        }
        isThreadVisible = true
        isReplyVisible = false
        isLoginVisible = false
        isNoticesVisible = false
        isDescargadosVisible = false
        isProfileVisible = false
        isOptionsVisible = false
        hidePmPanels()
        threadPanel.visibility = View.VISIBLE
        nativePanel.visibility = View.GONE
        replyPanel.visibility = View.GONE
        loginPanel.visibility = View.GONE
        noticesPanel.visibility = View.GONE
        descargadosPanel.visibility = View.GONE
        profilePanel.visibility = View.GONE
        optionsPanel.visibility = View.GONE
        bottomNav.visibility = View.VISIBLE
        swipeRefresh.visibility = View.INVISIBLE
        updateQuickReplyVisibility()
        // showThread() se llama también sobre un hilo YA cargado en memoria, sin pasar
        // por openThreadNative() ni por ningún onScrolled: salir del perfil de un autor
        // (exitMemberProfile), volver del login (cancelar o entrar) o volver de la web
        // tras Cloudflare (cameFromThread). Sin esto, la barra de páginas podía quedar
        // ausente en esos casos hasta el próximo scroll (que ni siquiera llega si la
        // página cabe entera en pantalla).
        updatePageBar()
    }

    private fun showWeb() {
        isWebVisible = true
        isReplyVisible = false
        isLoginVisible = false
        isNoticesVisible = false
        isProfileVisible = false
        isOptionsVisible = false
        hidePmPanels()
        swipeRefresh.visibility = View.VISIBLE
        nativePanel.visibility = View.GONE
        threadPanel.visibility = View.GONE
        replyPanel.visibility = View.GONE
        loginPanel.visibility = View.GONE
        noticesPanel.visibility = View.GONE
        descargadosPanel.visibility = View.GONE
        profilePanel.visibility = View.GONE
        optionsPanel.visibility = View.GONE
        bottomNav.visibility = View.VISIBLE
    }

    /** El WebView terminó una página. Si es de FC, el extractor ya está inyectado. */
    private fun onEnginePageReady(url: String) {
        if (!TrustedOrigins.isTrustedForocochesUrl(url)) return
        engineReady = true
        // La lista de smileys, una vez por sesión: sin ella `:roto2:` no se puede convertir en
        // el emoticono mientras escribes (y el selector abre al instante).
        if (!smiliesPedidos && smileyCache == null) {
            smiliesPedidos = true
            webView.evaluateJavascript("window.fcLoadSmilies&&fcLoadSmilies()", null)
        }
        // Si ya hay sesión, saber de quién es: la migración y el avatar dependen del uid.
        if (isLoggedIn() && uidActivo.isEmpty()) {
            webView.evaluateJavascript("window.fcQuienSoy&&fcQuienSoy()", null)
        }
        // NADA se pide hasta saber QUÉ diseño nos sirve FC. Leerlo no cuesta ni una petición
        // (sale del HTML ya cargado), y pedir en paralelo era una carrera real: con el diseño
        // antiguo el listado llega ilegible, y como su HTML es enorme aterrizaba DESPUÉS de
        // la recarga posterior al cambio y se quedaba pintado (verificado en dispositivo).
        skinGateOpen = false
        webView.evaluateJavascript("window.fcReadSkin&&fcReadSkin()", null)
        // Red de seguridad: si el motor no contesta (inyección fallida, FC raro), la app no
        // se queda muda para siempre.
        skinHandler.removeCallbacks(skinFallback)
        skinHandler.postDelayed(skinFallback, 3_000)
    }

    /** Arranca las peticiones iniciales del motor. Idempotente por carga de página. */
    private fun startEngineLoads() {
        if (skinGateOpen) return
        skinGateOpen = true
        skinHandler.removeCallbacks(skinFallback)
        if (!forumsRequested) {
            forumsRequested = true
            webView.evaluateJavascript(
                "window.fcLoadForumList&&fcLoadForumList('https://forocoches.com/foro/')", null
            )
        }
        if (!listLoaded) requestThreadList(1)
        // Si el motor se recargó con un hilo abierto (recuperación del diseño en caliente), la
        // pantalla que el usuario tiene delante es la del hilo: recargar solo el listado le
        // dejaría mirando el mismo error.
        if (isThreadVisible && currentThreadUrl.isNotEmpty()) requestThreadPage(threadPage)
        requestIgnoreList()
        if (pendingDeepLink.isNotEmpty()) {
            val dl = pendingDeepLink
            pendingDeepLink = ""
            openDeepLink(dl)
        }
    }

    // ── Diseño del foro ────────────────────────────────────────────────────────────────
    // Último styleid que nos sirvió FC y si había sesión. Alimenta la decisión de ForumSkin
    // y el aviso que se enseña cuando el contenido llega vacío.
    private var skinStyleid: Int? = null
    private var skinSession = false
    /**
     * El cambio de diseño se intenta UNA vez por ronda: si falla, no se entra en bucle. Se
     * vuelve a poner a false en cuanto una lectura dice que el diseño ya es el bueno, para que
     * un cambio POSTERIOR desde el navegador se pueda arreglar también (ver [recheckSkin]).
     */
    private var skinSwitchTried = false
    /** Cuándo se preguntó por última vez, para no pedir una página por cada error. */
    private var ultimoChequeoSkin = 0L
    /** ¿Ya se han lanzado las peticiones iniciales de esta carga del motor? */
    private var skinGateOpen = false
    private val skinHandler by lazy { android.os.Handler(mainLooper) }
    private val skinFallback = Runnable { startEngineLoads() }

    /**
     * Respuesta de `fcReadSkin`/`fcSwitchSkin`. Con el juego antiguo (5/7) el motor no sabe
     * leer NADA (el tester que lo reportó veía los hilos vacíos), y FC solo deja cambiarlo
     * en el ajuste de la cuenta: no hay parámetro de URL ni cookie que valga. Decisión del
     * dueño: cambiarlo sin preguntar, declarado en la ficha de Google Play.
     */
    private fun onSkinJson(json: String) {
        val o = try { org.json.JSONObject(json) } catch (_: Exception) { return }
        skinStyleid = if (o.isNull("styleid")) null else o.optInt("styleid")
        skinSession = o.optBoolean("session", false)
        val yaCambiado = o.optBoolean("switched", false)
        when (ForumSkin.decide(skinStyleid, skinSession)) {
            ForumSkin.Decision.Switch -> {
                // Si ya se intentó y seguimos en el diseño antiguo, no insistimos: se sigue
                // adelante y el canario explicará por qué no hay contenido.
                if (skinSwitchTried) { startEngineLoads(); return }
                skinSwitchTried = true
                // Sigue sin abrirse la puerta: lo que se pidiera ahora vendría en el markup
                // que no sabemos leer. Se amplía la red por si el POST tarda.
                skinHandler.removeCallbacks(skinFallback)
                skinHandler.postDelayed(skinFallback, 10_000)
                webView.evaluateJavascript("window.fcSwitchSkin&&fcSwitchSkin(${ForumSkin.TARGET})", null)
            }
            ForumSkin.Decision.Ok -> {
                // El diseño es el bueno AHORA. Se levanta el veto de un intento por ronda: si
                // el usuario vuelve a ponerse el antiguo desde el navegador, hay que poder
                // arreglarlo otra vez sin obligarle a reiniciar la app.
                skinSwitchTried = false
                // Recién cambiado: la página del motor sigue siendo la vieja. Se recarga, y
                // las peticiones las lanzará el onEnginePageReady de la carga nueva.
                if (yaCambiado) {
                    forumsRequested = false
                    listLoaded = false
                    // NO vale reload(): el VARNISH (gotcha 10) sirve ~1 min una copia
                    // cacheada renderizada TODAVÍA con el diseño viejo, y el motor se
                    // quedaría con el HTML que no sabe leer — y con él, el canario leyendo
                    // un styleid rancio. Con parámetro único el HTML llega recién hecho.
                    webView.loadUrl(TrustedOrigins.DEFAULT_URL + "?_fp=" + System.currentTimeMillis())
                } else {
                    startEngineLoads()
                }
            }
            // Sin sesión o estilo desconocido: no se toca la cuenta de nadie a ciegas. Se
            // carga igual (mejor intentarlo) y el aviso de ForumSkin explica lo que pase.
            else -> startEngineLoads()
        }
    }

    /**
     * Vuelve a preguntar qué diseño sirve FC, con una página **recién traída**.
     *
     * El diseño es un ajuste de la CUENTA, así que puede cambiar desde fuera de la app. Si el
     * usuario se pone el antiguo desde el navegador con la app viva, `fcReadSkin` seguiría
     * leyendo el HTML que el motor cargó al arrancar —moderno— mientras cada petición nueva
     * llega en el markup que no sabemos parsear: la app se queda sin leer nada hasta que la
     * cierras y la vuelves a abrir. Eso reportó un tester el 2026-08-21.
     *
     * Se llama donde se NOTA el síntoma (contenido vacío) y al volver a primer plano, no en
     * bucle: con el acelerador de [ESPERA_CHEQUEO_SKIN] como mucho cuesta una petición cada
     * medio minuto, y solo cuando algo ya ha ido mal.
     */
    private fun recheckSkin() {
        if (!engineReady) return
        val ahora = System.currentTimeMillis()
        if (ahora - ultimoChequeoSkin < ESPERA_CHEQUEO_SKIN) return
        ultimoChequeoSkin = ahora
        webView.evaluateJavascript("window.fcCheckSkin&&fcCheckSkin()", null)
    }

    /** Aviso del canario, o null si el diseño no es el problema. */
    private fun skinWarning(): String? = ForumSkin.warning(skinStyleid, skinSession)

    /** Quién escribió el último mensaje de cada hilo del listado. Ver [UltimosPosteadores]. */
    private val ultimosPosteadores = UltimosPosteadores()

    private fun lanzarUltimos(pids: List<String>) {
        for (pid in pids) {
            webView.evaluateJavascript("window.fcLoadLastPoster&&fcLoadLastPoster('${jsEscape(pid)}')", null)
        }
    }

    private fun onLastPosterJson(json: String) {
        val o = try { org.json.JSONObject(json) } catch (_: Exception) { return }
        val pid = o.optString("pid")
        if (pid.isEmpty()) return
        val siguientes = ultimosPosteadores.llego(pid, o.optString("author"))
        if (ultimosPosteadores.autor(pid) != null) adapter.ultimoLlego(pid)
        lanzarUltimos(siguientes)
    }

    private fun requestThreadList(page: Int) {
        // Traza permanente, mismo motivo que la de los ignorados: si la paginación de una
        // lista de búsqueda deja de funcionar, el síntoma es que la lista "se acaba" — no hay
        // error, no hay hueco, no lo canta nadie.
        // Traza permanente, mismo motivo que la de los ignorados: si la paginación de una
        // lista se rompe, el síntoma es que "se acaba" — sin error, sin hueco y sin que lo
        // cante nadie.
        android.util.Log.i("FC_LIST", "pedir $listSource pagina=$page" +
            (if (listaAgotada) " (AGOTADA)" else ""))
        // La sección +18 no pasa por el motor: baja su JSON de GitHub, así que no espera al WebView.
        if ((!engineReady && listSource != "mas18") || !PeticionLista.hayQuePedir(page, loadingPage) ||
            (page > 1 && listaAgotada)) return
        if (page <= 1) {
            listaAgotada = false
            ultimosPosteadores.reiniciar()   // lista nueva: lo que esperaba turno ya no se ve
        }
        loadingPage = true
        if (page <= 1 && !listRefresh.isRefreshing) listLoading.visibility = View.VISIBLE
        listEmpty.visibility = View.GONE
        // Solo las listas de subforo y Suscripciones pasan por fcLoadThreadList; el resto no
        // espera ninguna, y una de subforo que llegue tarde se tirará (ver PeticionLista).
        listaEsperada = ""
        val js = when (listSource) {
            // Mis hilos / Participados: el motor resuelve el UID/usuario real (el DOM vivo
            // trae u=0) y busca; para paginar reusa la URL con searchid (myThreadsBase).
            "search" -> "window.fcSearch&&fcSearch('${jsEscape(searchQuery)}',${searchTitleOnly}," +
                "'${jsEscape(if (page > 1 && myThreadsBase.isNotEmpty()) myThreadsBase + "&page=$page" else "")}'," +
                "'${jsEscape(searchUser)}')"
            "mine", "participated" -> {
                val mode = if (listSource == "mine") "started" else "participated"
                val pageUrl = if (page > 1 && myThreadsBase.isNotEmpty())
                    myThreadsBase + "&page=$page" else ""
                "window.fcLoadOwnThreads&&fcLoadOwnThreads('$mode','${jsEscape(pageUrl)}')"
            }
            // La actividad de otro usuario: la misma búsqueda, con SU nombre en vez del tuyo.
            "user-started", "user-threads" -> {
                val modo = if (listSource == "user-started") "started" else "threads"
                val pageUrl = if (page > 1 && myThreadsBase.isNotEmpty())
                    myThreadsBase + "&page=$page" else ""
                "window.fcLoadUserActivity&&fcLoadUserActivity('$modo'," +
                    "'${jsEscape(actividadUsuario)}','${jsEscape(pageUrl)}')"
            }
            // El Popurrí se trae VARIOS subforos de una vez y los mezcla en Kotlin.
            "popurri" -> "window.fcLoadPopurri&&fcLoadPopurri('${popurriFids().joinToString(",")}')"
            // El trending NO sale del motor: solo existe en el diseño de escritorio y con el
            // UA de móvil llega vacío. Ver [Trending].
            "top" -> { pedirTrending(); return }
            "mas18" -> { pedirMas18(page); return }
            else -> {
                val url = buildListUrl(page)
                listaEsperada = url
                "window.fcLoadThreadList&&fcLoadThreadList('$url')"
            }
        }
        webView.evaluateJavascript(js, null)
    }

    /**
     * Filtrado nativo del listado: ignorados y palabras clave (los mismos datos que usaba
     * `content.js` en la v1).
     *
     * Está extraído porque lo necesitan el listado de siempre y el Popurrí. Y no es un detalle:
     * ocultar los hilos de los ignorados es LA razón por la que la gente se instala esto, así
     * que una lista nueva que se lo saltara sería peor que no tener la lista.
     */
    fun hilosIgnorados(): List<HilosIgnorados.Hilo> =
        HilosIgnorados.leer(shellPrefs.getString(PREF_HILOS_IGNORADOS, "") ?: "")

    private fun guardarHilosIgnorados(lista: List<HilosIgnorados.Hilo>) {
        shellPrefs.edit()
            .putString(PREF_HILOS_IGNORADOS, HilosIgnorados.guardar(lista)).apply()
    }

    /** Quita un hilo de la lista de ignorados. Lo llama Opciones. */
    fun designorarHilo(tid: String) {
        guardarHilosIgnorados(HilosIgnorados.olvidar(hilosIgnorados(), tid))
        recargarListaPorFiltros()
    }

    /** Mismo camino que al tocar los filtros de Opciones: re-aplicarlos al vuelo. */
    private fun recargarListaPorFiltros() {
        listLoaded = false
        loadingPage = false
        requestThreadList(1)
    }

    /**
     * Acciones de un hilo desde la lista (pulsación larga).
     *
     * "Ignorar este hilo" lo pidió Nacho: hay hilos que no molestan por quién escribe ni por su
     * tema — los dos casos que ya cubrían los filtros — sino porque llevan tres semanas arriba.
     * Se puede deshacer desde Opciones, que si no un mal toque escondería un hilo para siempre.
     */
    private fun menuDeHilo(item: ThreadItem) {
        val opciones = ArrayList<String>()
        opciones.add("Ignorar este hilo")
        if (item.author.isNotEmpty()) opciones.add("Ignorar a ${item.author}")
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(item.title.take(60))
            .setItems(opciones.toTypedArray()) { _, i ->
                if (i == 0) {
                    guardarHilosIgnorados(HilosIgnorados.ignorar(hilosIgnorados(), item.tid, item.title))
                    toast("Hilo ignorado. Se puede deshacer en Opciones")
                    recargarListaPorFiltros()
                } else {
                    confirmarIgnorar(item.author)
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun filtrarLista(hilos: List<ThreadItem>): List<ThreadItem> {
        val ignored = repo.getIgnoredUsers().map { it.lowercase() }.toHashSet()
        val keywords = if (keywordRepo.isEnabled())
            keywordRepo.getKeywords().toList() else emptyList()
        val hilosCallados = hilosIgnorados()
        val ocultarMas18 = OptionsController.ocultarMas18(shellPrefs)
        return hilos.filter { t ->
            if (HilosIgnorados.estaIgnorado(hilosCallados, t.tid)) return@filter false
            if (t.author.isNotEmpty() && ignored.contains(t.author.lowercase())) return@filter false
            // La palabra tiene que ABRIR una palabra del título, no aparecer en medio de otra:
            // con `contains` a secas, el "PP" de fábrica escondía "apple", "app" y "WhatsApp"
            // (Márquez, 2026-09-16). La regla y su porqué, en [FiltroPalabras].
            if (FiltroPalabras.hayQueOcultar(t.title, keywords)) return@filter false
            // Los +18/+16 se esconden en las listas normales, pero no dentro de su propia sección.
            // Solo en listas de subforo: favoritos, mis hilos, búsquedas... son cosa del usuario.
            if (ocultarMas18 && listSource in LISTAS_DE_SUBFORO && EtiquetasHilo.esMas18o16(t.title)) return@filter false
            true
        }
    }

    private fun onThreadListJson(json: String) {
        val parsed = parseThreadListPayload(json) ?: run {
            loadingPage = false
            listLoading.visibility = View.GONE
            listRefresh.isRefreshing = false
            onThreadListError("parse")
            return
        }
        // Una respuesta atrasada (otro subforo, o una página de antes de recargar) se tira sin
        // tocar el "cargando": la que se espera sigue en vuelo.
        if (!PeticionLista.aceptar(parsed.url, listaEsperada)) {
            android.util.Log.i("FC_LIST", "DESCARTADA ${parsed.url} (se espera '$listaEsperada')")
            return
        }
        loadingPage = false
        listLoading.visibility = View.GONE
        listRefresh.isRefreshing = false
        val menuAntes = menuLinks?.profile
        if (parsed.menu.pm != null || parsed.menu.profile != null) menuLinks = parsed.menu
        updateAccountNavItem()
        // Perfil delante y los enlaces acaban de cambiar (o de aparecer): es el caso del cambio
        // de cuenta, donde olvidarIdentidadPintada() dejó el panel en blanco a propósito porque
        // los enlaces de antes eran de la otra cuenta: la petición se lanza cuando el menú
        // está listo, no antes.
        if (isProfileVisible && menuLinks?.profile != null && menuLinks?.profile != menuAntes) {
            if (profileName.text.isNullOrEmpty()) profileName.text = "…"
            pedirPerfil()
        }
        updateBadges(parsed.pmCount, parsed.quotesCount, parsed.mentionsCount)
        // Mis hilos: la búsqueda redirige a search.php?searchid=N — se guarda como base
        // de paginación (sin su posible page=).
        if ((listSource == "mine" || listSource == "participated" || listSource == "search" ||
             listSource == "user-started" || listSource == "user-threads") &&
            parsed.finalUrl.contains("searchid=")) {
            myThreadsBase = parsed.finalUrl.replace(Regex("[&?]page=\\d+"), "")
        }

        // Se aprende de los hilos SIN FILTRAR: uno tapado por el filtro de palabras sigue
        // estando suscrito en FC, y la estrella tiene que decir la verdad al abrirlo.
        if (listSource == "favs") aprenderSuscripciones(parsed.threads.map { it.tid })
        val visible = filtrarLista(parsed.threads)
        listLoaded = true
        currentPage = parsed.page
        val antes = adapter.itemCount
        if (parsed.page <= 1) adapter.submit(visible) else adapter.append(visible)
        // Una página que no aporta NI UN hilo nuevo es el final de los resultados, aunque FC
        // haya contestado 200 con contenido (ver [listaAgotada]).
        if (parsed.page > 1 && adapter.itemCount == antes) listaAgotada = true
        android.util.Log.i("FC_LIST", "llega $listSource pagina=${parsed.page} " +
            "nuevos=${adapter.itemCount - antes} enLista=${adapter.itemCount}" +
            (if (listaAgotada) " -> AGOTADA" else ""))
        listEmpty.visibility = if (adapter.itemCount == 0) View.VISIBLE else View.GONE
        // Mismo razonamiento que en el hilo: un listado vacío puede ser un diseño cambiado
        // desde fuera hace un momento. Solo cuando NO hay ni un hilo (con filtros de por medio
        // un listado puede quedarse vacío legítimamente, y por eso se mira parsed, no visible).
        if (parsed.threads.isEmpty()) recheckSkin()
        // Un listado vacío también puede ser el HTML que no sabemos leer, no que no haya
        // hilos: si el diseño del foro es el problema, se dice (canario, ver ForumSkin).
        listEmpty.text = skinWarning()
            ?: if (listSource == "favs") "No tienes hilos suscritos"
               else "No hay hilos que mostrar"
    }

    /** Badges de la barra inferior: MP en Perfil, citas en Citas, menciones en Menciones.
     *  Los contadores vienen gratis en el HTML de cada listado (cero peticiones extra). */
    /** Lo último que dijo FC, para poder apuntarlo al abrir el panel. */
    private var fcQuotes = 0
    private var fcMentions = 0

    private fun updateBadges(pm: Int, quotes: Int, mentions: Int) {
        fcQuotes = quotes
        fcMentions = mentions
        // FC cuenta cosas que la app no puede enseñar (filtra ignorados y palabras clave), y
        // abrir el panel no cambiaba nada: el "1 cita sin leer" fantasma se quedaba para
        // siempre. Manda la memoria local, igual que en las negritas. Ver [Insignias].
        val claveVistasQ = ClavesPorCuenta.clave(PREF_VISTAS_QUOTES, uidActivo)
        val claveVistasM = ClavesPorCuenta.clave(PREF_VISTAS_MENTIONS, uidActivo)
        val vistasQ = Insignias.vistoAjustado(quotes, shellPrefs.getInt(claveVistasQ, 0))
        val vistasM = Insignias.vistoAjustado(mentions, shellPrefs.getInt(claveVistasM, 0))
        shellPrefs.edit()
            .putInt(claveVistasQ, vistasQ)
            .putInt(claveVistasM, vistasM)
            .apply()
        insigniaCitas = Insignias.aMostrar(quotes, vistasQ)
        insigniaMenciones = Insignias.aMostrar(mentions, vistasM)
        insigniaPm = pm
        pintarInsignias()
    }

    /** Lo que se enseña como nuevo en cada sitio. Lo calcula [updateBadges]. */
    private var insigniaCitas = 0
    private var insigniaMenciones = 0
    private var insigniaPm = 0

    private fun insigniaDe(clave: String): Int = when (clave) {
        "avisos" -> insigniaCitas + insigniaMenciones
        "pm" -> insigniaPm
        else -> 0
    }

    /**
     * Pinta los contadores donde estén ahora sus botones: en la barra, o en la fila del panel
     * más un punto en el avatar. Si Privados vive en el panel, un MP nuevo se tiene que ver
     * igual — si no, se quedaría pegado a un botón que no está y nadie se enteraría.
     */
    private fun pintarInsignias() {
        for ((clave, id) in navClaves) {
            val n = insigniaDe(clave)
            navBadges[id]?.apply {
                if (n > 0) {
                    visibility = View.VISIBLE
                    text = if (n > 99) "99+" else n.toString()
                } else {
                    visibility = View.GONE
                }
            }
        }
        if (::nativeAvatarPunto.isInitialized) {
            nativeAvatarPunto.visibility =
                if (repartoBarra().panel.any { insigniaDe(it) > 0 }) View.VISIBLE else View.GONE
        }
        if (isPanelCuentaVisible) cuentaPanel.pintar()
        if (isNoticesVisible) pintarPastillas()
    }

    private fun onThreadListError(reason: String) {
        android.util.Log.i("FC_LIST", "ERROR $listSource: $reason")
        loadingPage = false
        listLoading.visibility = View.GONE
        listRefresh.isRefreshing = false
        when (reason) {
            "cloudflare" -> {
                // Challenge de CF: solo un navegador visible puede resolverlo. Enseñamos
                // la web; al pasarlo, el usuario vuelve a Inicio y la lista carga.
                showWeb()
                webView.loadUrl(buildListUrl(1))
            }
            else -> {
                if (adapter.itemCount == 0) {
                    listEmpty.visibility = View.VISIBLE
                    listEmpty.text = "No se pudo cargar el listado.\nDesliza para reintentar."
                }
                android.util.Log.w("FC_SHELL", "thread list error: $reason")
            }
        }
    }

    // ── Comportamiento heredado ──────────────────────────────────────────────

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        // Gesto atrás/adelante por swipe en la capa web; en la lista nativa de Inicio
        // (pestañas visibles), swipe horizontal = subforo anterior/siguiente (testers).
        val tabsSwipe = !isWebVisible && !isThreadVisible && !isReplyVisible &&
            !isLoginVisible && !isNoticesVisible && !isProfileVisible && !isOptionsVisible &&
            conPestanasDeSubforo() && forumTabs.visibility == View.VISIBLE
        if (!isWebVisible && !tabsSwipe) return super.dispatchTouchEvent(ev)
        when (ev.action) {
            MotionEvent.ACTION_DOWN -> {
                touchDownX = ev.x
                touchDownY = ev.y
                // El swipe de subforo solo vale si arranca SOBRE la lista de hilos: así,
                // deslizar la barra inferior (que scrollea en horizontal) o las pestañas
                // ya no cambia de subforo por accidente.
                swipeStartedInList = touchInside(threadList, ev)
            }
            MotionEvent.ACTION_UP -> {
                val diffX = ev.x - touchDownX
                val diffY = ev.y - touchDownY
                if (isWebVisible && abs(diffX) > abs(diffY) * 2f && abs(diffX) > 100f) {
                    if (diffX > 0 && webView.canGoBack()) { webView.goBack(); return true }
                    if (diffX < 0 && webView.canGoForward()) { webView.goForward(); return true }
                }
                // Umbral más exigente que en la web: la lista scrollea en vertical y un
                // arrastre diagonal no debe cambiar de subforo por accidente.
                if (tabsSwipe && swipeStartedInList && abs(diffX) > abs(diffY) * 2f && abs(diffX) > 150f) {
                    selectAdjacentTab(if (diffX < 0) 1 else -1)
                    return true
                }
            }
        }
        return super.dispatchTouchEvent(ev)
    }

    /** ¿El toque cae dentro de los límites en pantalla de [view]? (coords absolutas rawX/rawY). */
    private fun touchInside(view: View, ev: MotionEvent): Boolean {
        if (view.visibility != View.VISIBLE || view.width == 0 || view.height == 0) return false
        val loc = IntArray(2)
        view.getLocationOnScreen(loc)
        val x = ev.rawX.toInt()
        val y = ev.rawY.toInt()
        return x in loc[0]..(loc[0] + view.width) && y in loc[1]..(loc[1] + view.height)
    }

    /** Pestaña vecina (delta ±1); select() dispara onTabSelected → carga del subforo. */
    private fun selectAdjacentTab(delta: Int) {
        val idx = forumTabs.selectedTabPosition
        val next = idx + delta
        if (idx < 0 || next < 0 || next >= forumTabs.tabCount) return
        forumTabs.getTabAt(next)?.select()
    }

    /**
     * Pide al motor la lista de ignorados de la cuenta. Sin sesión no se pide nada: la página
     * de invitado no falla, devuelve una lista vacía perfectamente creíble.
     */
    private fun requestIgnoreList() {
        if (!isLoggedIn()) return
        webView.evaluateJavascript("window.fcLoadIgnoreList&&fcLoadIgnoreList()", null)
    }

    /**
     * Ignora o des-ignora a alguien EN FOROCOCHES (no solo en la app). Devuelve `false` si no
     * hay sesión, y entonces quien llama se queda con la edición local — que sin sesión no la
     * sincroniza nadie, así que sobrevive.
     */
    private fun escribirIgnorado(accion: String, usuario: String): Boolean {
        if (!isLoggedIn()) return false
        // Ignorar/des-ignorar escribe en profile.php?do=updatelist con las cookies de AHORA
        // MISMO: a medio cambiar, tocaría la lista de la cuenta equivocada. Mismo motivo que
        // el resto de vías que mutan algo en FC.
        if (cambiandoDeCuenta) { toast("Espera a que termine el cambio de cuenta"); return false }
        val fn = if (accion == "add") "fcIgnoreAdd" else "fcIgnoreRemove"
        val safe = usuario.replace("\\", "\\\\").replace("'", "\'")
        webView.evaluateJavascript("window.$fn&&$fn('$safe')", null)
        toast(if (accion == "add") "Ignorando a @$usuario…" else "Quitando a @$usuario…")
        return true
    }

    /**
     * Llega la lista de ignorados del motor.
     *
     * Solo se sobreescribe con una lista NO vacía y con sesión confirmada. Es la misma
     * prudencia que tenía el camino nativo y sigue haciendo falta: una respuesta rara de FC
     * borraría de golpe la lista de alguien que lleva años construyéndola, y eso no se
     * recupera desde la app.
     *
     * Si la lista CAMBIA hay que repintar lo que ya está en pantalla: el filtrado se aplica
     * al renderizar (`onThreadListJson`), así que una lista que aterriza después de la lista
     * de hilos no filtra nada hasta la siguiente recarga. Ese era justo el bug que reportaron
     * dos testers el 2026-08-20 — entraban, se logueaban, y los ignorados no aparecían hasta
     * reiniciar la app.
     */
    private fun onIgnoreListJson(json: String) {
        // Traza permanente, por el mismo motivo que la tiene RemoteConfig: si esto deja de
        // funcionar no hay ningún síntoma visible — la app simplemente no filtra a nadie y
        // parece que el usuario no ignora a nadie.
        fun traza(q: String) = android.util.Log.i("FC_IGNORE", q)
        val o = try { org.json.JSONObject(json) } catch (_: Exception) {
            traza("respuesta ilegible"); return
        }
        // Acción del usuario (ignorar/des-ignorar) vs. sincronización automática al abrir la
        // app. La diferencia importa en TODO lo que viene debajo: una acción se contesta con
        // un aviso y puede dejar la lista vacía; un sincronizado es silencioso y conservador.
        val accion = o.optString("action", "")
        val objetivo = o.optString("target", "")

        if (!o.optBoolean("ok", false)) {
            val motivo = o.optString("error", "")
            traza("fallo${if (accion.isEmpty()) "" else " al $accion"}: $motivo")
            if (accion.isNotEmpty()) toast(
                when {
                    motivo == "login" -> "Inicia sesión para cambiar tus ignorados"
                    motivo.isNotEmpty() && motivo.contains(' ') -> motivo
                    else -> "No se pudo cambiar la lista de ignorados"
                }
            )
            return
        }
        if (!o.optBoolean("session", false)) { traza("sin sesión, no se toca la lista"); return }

        val users = try { IgnoreListParser.parse(o.optString("html", "")) } catch (_: Exception) {
            traza("html ilegible"); return
        }
        traza("recibidos ${users.size} ignorados${if (accion.isEmpty()) "" else " (tras $accion)"}")
        // Una lista vacía en un SINCRONIZADO se descarta: una respuesta rara de FC borraría de
        // golpe la lista de alguien que lleva años construyéndola. Pero tras una acción SÍ vale
        // — quitar al único ignorado que tenías deja la lista vacía, y eso es la verdad.
        if (users.isEmpty() && accion.isEmpty()) return

        val antes = repo.getIgnoredUsers().map { it.lowercase() }.toHashSet()
        repo.setIgnoredUsers(users)
        val cambio = antes != users.map { it.lowercase() }.toHashSet()

        if (accion.isNotEmpty()) {
            toast(if (accion == "add") "Ignorado @$objetivo" else "Ya no ignoras a @$objetivo")
            options.refrescarIgnorados()
            if (isMemberVisible) pintarBotonIgnorar()
            // Aquí el hilo abierto SÍ se recarga: lo ha pedido el usuario, y el sentido de
            // ignorar a alguien es dejar de verlo AHORA, no en la próxima página.
            listLoaded = false
            loadingPage = false
            requestThreadList(1)
            if (currentThreadUrl.isNotEmpty()) reloadCurrentThread()
            return
        }

        if (!cambio) return
        traza("la lista CAMBIÓ (antes ${antes.size}) -> se repinta el listado")
        // Cambió de verdad: refrescar solo el listado. Un hilo abierto NO se toca, que
        // recargarle la página a quien está leyendo es peor que enseñarle un post de más.
        if (!isThreadVisible && listLoaded) requestThreadList(currentPage.coerceAtLeast(1))
    }

    /**
     * Comprueba si hay versión nueva y, si la hay, lo dice SIN interrumpir: una barra abajo
     * con un botón. El flujo es el "flexible" de Play — se descarga en segundo plano mientras
     * sigues leyendo el foro — así que hace falta avisar aparte de que ya se puede instalar.
     */
    /**
     * Enseña (o esconde) el aviso del autor de la app. Barra, raya y estado se mueven SIEMPRE
     * juntos desde aquí: apagar solo la barra dejaría la raya de 1dp huérfana, que es justo el
     * fallo que ya costó una sesión con la barra de páginas.
     *
     * Se llama con la config RECIÉN bajada, no con la del arranque anterior: un aviso que llega
     * un día tarde no sirve de nada cuando lo que se quiere contar es que la app está rota.
     */
    /**
     * Ofrece en la barra que los enlaces de ForoCoches abran la app. Devuelve `true` si la ha
     * pintado (y entonces la barra ya está ocupada).
     */
    private fun pintarOfertaDeEnlaces(): Boolean {
        val ofrecer = EnlacesApp.debeOfrecer(
            soportado = EnlacesSistema.soportado(),
            yaActivado = EnlacesSistema.activados(this),
            sesiones = shellPrefs.getInt(PREF_SESIONES, 0),
            descartado = shellPrefs.getBoolean(PREF_ENLACES_DESCARTADO, false),
            hayAvisoRemoto = false        // aquí solo se llega si no había aviso remoto
        )
        if (!ofrecer) return false
        noticeText.text = "Los enlaces de ForoCoches pueden abrirse aquí. Toca para activarlo."
        noticeBar.visibility = View.VISIBLE
        noticeDivider.visibility = View.VISIBLE
        noticeClose.visibility = View.VISIBLE
        noticeClose.setOnClickListener {
            shellPrefs.edit().putBoolean(PREF_ENLACES_DESCARTADO, true).apply()
            noticeBar.visibility = View.GONE
            noticeDivider.visibility = View.GONE
        }
        noticeBar.isClickable = true
        noticeBar.setOnClickListener { explicarEnlaces() }
        return true
    }

    /**
     * Explica por qué esto no puede activarlo la app y lleva al ajuste.
     *
     * El paso intermedio no es adorno: la pantalla del sistema tampoco es obvia (hay que marcar
     * los dominios uno a uno), y decir POR QUÉ hace falta evita que parezca que la app está
     * pidiendo algo turbio.
     */
    private fun explicarEnlaces() {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Abrir enlaces aquí")
            .setMessage(
                "Android pide que lo autorices tú, porque forocoches.com no es un dominio " +
                    "nuestro y no podemos verificarlo.\n\n" +
                    "Te llevamos al ajuste: activa \"Abrir enlaces compatibles\" y marca " +
                    "forocoches.com."
            )
            .setPositiveButton("Ir al ajuste") { _, _ ->
                if (!EnlacesSistema.abrirAjuste(this)) toast("No se pudo abrir el ajuste")
            }
            .setNegativeButton("Ahora no", null)
            .show()
    }

    private fun pintarAviso() {
        val descartados = Avisos.leerDescartados(shellPrefs.getString(PREF_AVISOS, "") ?: "")
        val aviso = Avisos.para(RemoteConfig.cached(this), versionCodeInstalado(), descartados)
        if (aviso == null) {
            // Sin aviso remoto, la barra queda libre para ofrecer que los enlaces de FC se
            // abran aquí. Al revés NO: el canal de avisos es para emergencias y competir con
            // él el día que haga falta de verdad lo devaluaría (ver [EnlacesApp]).
            if (pintarOfertaDeEnlaces()) return
            noticeBar.visibility = View.GONE
            noticeDivider.visibility = View.GONE
            return
        }
        noticeText.text = aviso.texto
        noticeBar.visibility = View.VISIBLE
        noticeDivider.visibility = View.VISIBLE

        // Un aviso que no se puede cerrar es una piedra en el zapato: solo para emergencias.
        noticeClose.visibility = if (aviso.fijo) View.GONE else View.VISIBLE
        noticeClose.setOnClickListener {
            shellPrefs.edit()
                .putString(PREF_AVISOS, Avisos.conDescartado(shellPrefs.getString(PREF_AVISOS, "") ?: "", aviso.id))
                .apply()
            noticeBar.visibility = View.GONE
            noticeDivider.visibility = View.GONE
        }

        // Con enlace, tocar el aviso lo abre FUERA (Telegram, un hilo…): nunca en la capa web.
        if (aviso.enlace.isNotEmpty()) {
            noticeBar.isClickable = true
            noticeBar.setOnClickListener { openExternal(aviso.enlace) }
        } else {
            noticeBar.isClickable = false
            noticeBar.setOnClickListener(null)
        }
    }

    /**
     * Versión realmente instalada. Se pregunta al sistema en vez de usar `BuildConfig` porque
     * esa clase no se genera en este proyecto, y de paso es el número que de verdad corre en el
     * móvil. Si fallara, se devuelve 0: `Avisos` trata el 0 como "no sé qué versión es" y
     * enseña el aviso igualmente, que es el lado seguro (mejor de más que dejar a alguien
     * roto y sin explicación).
     */
    private fun versionCodeInstalado(): Int = try {
        val info = packageManager.getPackageInfo(packageName, 0)
        if (android.os.Build.VERSION.SDK_INT >= 28) info.longVersionCode.toInt()
        else @Suppress("DEPRECATION") info.versionCode
    } catch (_: Exception) { 0 }

    private fun prepararActualizador() {
        actualizador.onDescargaLista = {
            runOnUiThread {
                com.google.android.material.snackbar.Snackbar.make(
                    findViewById(R.id.root_container),
                    "Actualización descargada",
                    com.google.android.material.snackbar.Snackbar.LENGTH_INDEFINITE
                ).setAction("Reiniciar") { actualizador.completar() }.show()
            }
        }
        // Se espera a que llegue la config RECIÉN bajada antes de preguntar por la versión: si
        // se usara la copia del arranque anterior, el primer arranque tras publicar enseñaría
        // el aviso sin novedades — y para cuando el fichero bueno estuviera cacheado, media
        // gente ya habría actualizado y no vería las notas nunca. Con tope, para que un remoto
        // caído no retrase nada.
        RemoteConfig.cuandoEsteFresca(3_000, skinHandler) {
            runOnUiThread { pintarAviso(); pintarPestanas() }   // la config fresca puede traer/quitar +18
            actualizador.comprobar { nueva ->
                if (nueva == null) return@comprobar
                runOnUiThread { mostrarDialogoActualizacion(nueva) }
            }
        }
    }

    /**
     * Diálogo centrado con lo que trae la versión nueva. Las notas NO las da Play (su API solo
     * expone el versionCode), así que salen de nuestro `fc_config.json`; ver [NotasVersion].
     *
     * Si la versión está marcada como bloqueante, no se puede cerrar… pero **solo mientras el
     * botón sirva de algo**: si Play no puede arrancar la actualización (sin Play Services,
     * instalada por sideload, sin conexión), se libera el diálogo. Un aviso que no se cierra y
     * cuyo único botón no funciona deja la app inservible, y la única salida del usuario sería
     * desinstalarla.
     */
    private fun mostrarDialogoActualizacion(nueva: Actualizacion) {
        val aviso = NotasVersion.para(nueva.codigo, RemoteConfig.cached(this))
        val dialogo = androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(NotasVersion.titulo(aviso))
            .setMessage(NotasVersion.mensaje(aviso))
            .setPositiveButton("Actualizar", null)   // se engancha abajo para no cerrarlo solo
            .setCancelable(!aviso.bloqueante)
            .apply { if (!aviso.bloqueante) setNegativeButton("Ahora no", null) }
            .create()
        dialogo.setCanceledOnTouchOutside(!aviso.bloqueante)
        dialogo.show()
        dialogo.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            actualizador.lanzar(this, nueva, bloqueante = aviso.bloqueante, onFallo = {
                runOnUiThread {
                    dialogo.setCancelable(true)
                    dialogo.setCanceledOnTouchOutside(true)
                    dialogo.dismiss()
                    toast("No se pudo iniciar la actualización. Ábrela desde Google Play.")
                }
            })
            if (!aviso.bloqueante) dialogo.dismiss()
        }
    }

    override fun onResume() {
        super.onResume()
        // Si la descarga terminó con la app en segundo plano, aquí se recupera el aviso: sin
        // esto la actualización se queda bajada y sin instalar, y el usuario no se entera.
        actualizador.alReanudar(this)
        // Volver a la app es justo cuando el usuario puede haber estado en el navegador
        // cambiándose el diseño del foro. El acelerador de recheckSkin evita que esto sea una
        // petición cada vez que se cambia de aplicación.
        recheckSkin()
    }

    override fun onPause() {
        super.onPause()
        // Persiste las cookies de sesión a disco para que el NotificationWorker en
        // background no haga el fetch deslogueado.
        CookieManager.getInstance().flush()
    }

    override fun onDestroy() {
        // El respaldo del chequeo de diseño es un callback diferido: si la Activity se va
        // antes de que dispare, se queda apuntando a una pantalla que ya no existe.
        skinHandler.removeCallbacks(skinFallback)
        super.onDestroy()
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        // App viva: destino centralizado en IntentRouter. REGLA DE ORO: nunca la capa web.
        executeRoute(
            IntentRouter.route(urlDelIntent(intent), engineReady)
        )
    }

    /**
     * Atrás del sistema. En API 33+ (y por defecto al apuntar a 35/36) el atrás se despacha
     * por OnBackInvokedCallback → hay que registrar la navegación en el OnBackPressedDispatcher.
     * Sobrescribir `onBackPressed()` ya NO se llama en API 36 (la app se salía al escritorio).
     * Registrado en onCreate con `onBackPressedDispatcher.addCallback`.
     */
    private val onBackCallback = object : androidx.activity.OnBackPressedCallback(true) {
        override fun handleOnBackPressed() = goBack()
    }

    /**
     * UN solo camino hacia atrás para el botón del móvil y para las flechas ← de los paneles:
     * si cada uno decidiera por su cuenta, el ← y el gesto acabarían llevando a sitios
     * distintos desde la misma pantalla.
     */
    // ── Compartir un mensaje como tarjeta ─────────────────────────────────────
    private var compartirOverlay: android.widget.FrameLayout? = null

    /**
     * Enseña el mensaje como tarjeta y deja mandarlo. La vista previa NO es un adorno: lo que
     * se comparte sale de la app del usuario con su nombre dentro, así que conviene que vea
     * exactamente qué va a mandar antes de mandarlo (es lo que hacen Reddit e Instagram).
     */
    private fun compartirMensaje(post: PostItem) {
        if (compartirOverlay != null) return
        val titulo = threadTitle.text?.toString().orEmpty()
        val cuerpo = CompartirFC.cuerpoTarjeta(postAdapter.quoteBodyOf(post))
        val tarjeta = try {
            TarjetaCompartir.crear(
                ctx = this,
                autor = post.author,
                fecha = post.date,
                avatar = PostImages.get(post.avatar),
                cuerpo = cuerpo,
                foto = CompartirFC.primeraImagen(post.html).takeIf { it.isNotEmpty() }
                    ?.let { PostImages.get(it) },
                tituloHilo = CompartirFC.tituloLimpio(titulo)
            )
        } catch (_: Throwable) {   // incluye OutOfMemoryError al crear el lienzo
            toast("No se pudo preparar la tarjeta")
            return
        }
        val texto = CompartirFC.textoMensaje(post.author, titulo, post.pid)
        mostrarVistaPreviaCompartir(tarjeta, texto, CompartirFC.enlaceMensaje(post.pid))
    }

    /** Capa con la tarjeta y los dos botones. Mismo patrón que el visor de imágenes. */
    private fun mostrarVistaPreviaCompartir(tarjeta: android.graphics.Bitmap, texto: String, enlace: String) {
        val dp = resources.displayMetrics.density
        val root = findViewById<android.view.ViewGroup>(android.R.id.content)
        val capa = android.widget.FrameLayout(this).apply {
            setBackgroundColor(0xE6000000.toInt())
            isClickable = true
            setOnClickListener { cerrarCompartir() }   // tocar fuera cierra
        }
        val columna = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            gravity = android.view.Gravity.CENTER_HORIZONTAL
            val m = (24 * dp).toInt()
            setPadding(m, m, m, m)
            isClickable = true                        // que tocar la tarjeta NO cierre
        }
        // Tope de alto: una tarjeta larga empujaría los botones fuera de la pantalla y la
        // vista previa se quedaría sin salida visible. Se ve encajada, no recortada.
        columna.addView(android.widget.ImageView(this).apply {
            setImageBitmap(tarjeta)
            adjustViewBounds = true
            maxHeight = (resources.displayMetrics.heightPixels * 0.68f).toInt()
            scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
            contentDescription = "Vista previa del mensaje"
        }, android.widget.LinearLayout.LayoutParams(-1, -2))

        val fila = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.HORIZONTAL
            val t = (16 * dp).toInt()
            setPadding(0, t, 0, 0)
        }
        fun boton(texto: String, alPulsar: () -> Unit) = android.widget.TextView(this).apply {
            this.text = texto
            textSize = 15f
            setTextColor(0xFFFFFFFF.toInt())
            gravity = android.view.Gravity.CENTER
            val h = (20 * dp).toInt(); val v = (12 * dp).toInt()
            setPadding(h, v, h, v)
            setBackgroundColor(0x33FFFFFF)
            setOnClickListener { alPulsar() }
        }
        fila.addView(boton("Compartir") { lanzarCompartir(tarjeta, texto) },
            android.widget.LinearLayout.LayoutParams(-2, -2).apply { rightMargin = (12 * dp).toInt() })
        fila.addView(boton("Copiar enlace") {
            val cb = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
            cb.setPrimaryClip(android.content.ClipData.newPlainText("ForoPlus", enlace))
            toast("Enlace copiado")
            cerrarCompartir()
        }, android.widget.LinearLayout.LayoutParams(-2, -2))
        columna.addView(fila)

        capa.addView(columna, android.widget.FrameLayout.LayoutParams(-1, -2).apply {
            gravity = android.view.Gravity.CENTER
        })
        root.addView(capa, android.view.ViewGroup.LayoutParams(-1, -1))
        compartirOverlay = capa
    }

    /**
     * Manda la tarjeta con el intent estándar. **Siempre va también el texto con el enlace**:
     * si solo fuera la imagen, quien la recibe tendría una foto de un mensaje y ninguna forma
     * de llegar al original. Si el fichero fallara, se comparte al menos el enlace.
     */
    private fun lanzarCompartir(tarjeta: android.graphics.Bitmap, texto: String) {
        val uri = TarjetaCompartir.guardar(this, tarjeta)
        val envio = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
            if (uri != null) {
                type = "image/png"
                putExtra(android.content.Intent.EXTRA_STREAM, uri)
                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } else {
                type = "text/plain"
            }
            putExtra(android.content.Intent.EXTRA_TEXT, texto)
        }
        cerrarCompartir()
        startActivity(android.content.Intent.createChooser(envio, "Compartir mensaje"))
    }

    /** Comparte el HILO (botón de la cabecera): solo enlace, sin tarjeta. */
    /**
     * Compartir el HILO. Antes mandaba solo el enlace, y eso confundía: Márquez pulsó este
     * botón buscando la tarjeta que había visto, le salió un enlace pelado y dio por hecho que
     * estaba roto. El desajuste era real — la acción visible daba el resultado pobre y la buena
     * estaba escondida en el menú ⋮ de un mensaje.
     *
     * Se pide el primer mensaje al motor SIEMPRE, aunque estés en la página 1 y ya lo tengamos:
     * si dependiera de por dónde vas leyendo, la misma acción daría tarjetas distintas y eso
     * es justo lo que confunde. Si el motor no contesta, se comparte el enlace de siempre —
     * nunca se deja al usuario sin nada.
     */
    private fun compartirHilo() {
        if (compartirOverlay != null) return
        val tid = currentThreadTid
        if (CompartirFC.enlaceHilo(tid).isEmpty()) { toast("No se pudo identificar el hilo"); return }
        pidiendoCabeceraTid = tid
        toast("Preparando tarjeta…")
        // Plazo: si la tarjeta no está en PLAZO_TARJETA, se comparte el enlace. Sin esto,
        // en un hilo cuya primera foto era un GIF de 53 MB (Green Floyd, 2026-09-24,
        // t=10814496) no pasaba NADA durante más de un minuto y la app parecía colgada.
        val intento = ++intentoCompartir
        tarjetaPendiente = true
        window.decorView.postDelayed({
            if (intento == intentoCompartir && tarjetaPendiente) {
                tarjetaPendiente = false
                pidiendoCabeceraTid = ""
                compartirSoloEnlace(CompartirFC.textoHilo(threadTitle.text?.toString().orEmpty(), tid))
            }
        }, PLAZO_TARJETA)
        webView.evaluateJavascript("window.fcLoadThreadHead&&fcLoadThreadHead('${jsEscape(tid)}')", null)
    }

    /** Hilo cuya cabecera se ha pedido para compartir ("" = no hay ninguna en curso). */
    private var pidiendoCabeceraTid = ""
    /** Se está montando la tarjeta del hilo; el primero que acabe (tarjeta o plazo) la apaga. */
    private var tarjetaPendiente = false
    private var intentoCompartir = 0

    private fun onThreadHeadJson(json: String) {
        val tid = pidiendoCabeceraTid
        if (tid.isEmpty()) return
        pidiendoCabeceraTid = ""
        if (!tarjetaPendiente) return        // ya se compartió el enlace por plazo
        val titulo = CompartirFC.tituloLimpio(threadTitle.text?.toString().orEmpty())
        val texto = CompartirFC.textoHilo(threadTitle.text?.toString().orEmpty(), tid)
        val enlace = CompartirFC.enlaceHilo(tid)
        val o = try { org.json.JSONObject(json) } catch (_: Exception) { null }
        if (o == null || !o.optBoolean("ok", false) || o.optString("tid") != tid) {
            tarjetaPendiente = false
            compartirSoloEnlace(texto)
            return
        }
        // La foto y el avatar del primer mensaje NO están en la caché si vienes de otra página
        // (solo se cachea lo que se ha pintado). Se descargan aquí, porque si no la tarjeta
        // saldría con foto o sin ella según por dónde ibas leyendo — el mismo defecto que este
        // botón venía a arreglar. Sin foto, la carga ni se intenta.
        val urlFoto = CompartirFC.primeraImagen(o.optString("html"))
        traerImagen(o.optString("avatar"), 240) { avatar ->
            traerImagen(urlFoto, 1200) { foto ->
                if (!tarjetaPendiente) return@traerImagen   // llegó tarde: ya se mandó el enlace
                tarjetaPendiente = false
                val tarjeta = try {
                    TarjetaCompartir.crearHilo(
                        ctx = this,
                        titulo = titulo,
                        autor = o.optString("author"),
                        fecha = o.optString("date"),
                        avatar = avatar,
                        cuerpo = CompartirFC.cuerpoTarjeta(PostAdapter.textoDeHtml(o.optString("html"))),
                        foto = foto
                    )
                } catch (_: Throwable) { null }   // incluye OutOfMemoryError al crear el lienzo
                if (tarjeta == null) compartirSoloEnlace(texto)
                else mostrarVistaPreviaCompartir(tarjeta, texto, enlace)
            }
        }
    }

    /**
     * Bitmap de una URL, venga de la caché o de la red. La caché solo tiene lo que se ha
     * PINTADO, así que compartiendo desde otra página el avatar y la foto del primer mensaje
     * no están: sin esto, la misma tarjeta saldría completa o descafeinada según por dónde
     * fueras leyendo. URL vacía o descarga fallida → null, y cada parte de la tarjeta ya sabe
     * apañarse sin su imagen.
     */
    private fun traerImagen(url: String, ladoMax: Int, onDone: (android.graphics.Bitmap?) -> Unit) {
        if (url.isEmpty()) { onDone(null); return }
        PostImages.get(url)?.let { onDone(it); return }
        // Tope de bytes: la foto de una tarjeta nunca justifica bajarse decenas de MB (sin
        // foto, la tarjeta sale igual).
        PostImages.loadFull(url, ladoMax, MAX_BYTES_FOTO_TARJETA) { onDone(it) }
    }

    private fun compartirSoloEnlace(texto: String) {
        val envio = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(android.content.Intent.EXTRA_TEXT, texto)
        }
        startActivity(android.content.Intent.createChooser(envio, "Compartir hilo"))
    }

    private fun cerrarCompartir() {
        compartirOverlay?.let { (it.parent as? android.view.ViewGroup)?.removeView(it) }
        compartirOverlay = null
    }

    // ── Visor de imágenes a pantalla completa ──────────────────────────────────
    private var visorOverlay: android.widget.FrameLayout? = null

    /**
     * Abre una foto del hilo en una capa a pantalla completa, con el hilo oscurecido detrás.
     *
     * Se monta sobre `android.R.id.content` (no dentro de `root_container`, que es un
     * LinearLayout vertical) para que tape también la barra inferior — mismo patrón que la capa
     * del reporte. NO entra en la pila de navegación: es un "cierra esto", no un "vuelve atrás".
     */
    private fun abrirVisorImagen(url: String) {
        if (visorOverlay != null || url.isEmpty()) return
        val root = findViewById<android.view.ViewGroup>(android.R.id.content)
        val capa = android.widget.FrameLayout(this).apply {
            // Semitransparente a propósito: el hilo se sigue intuyendo por detrás, así se ve
            // que no has salido de donde estabas leyendo.
            setBackgroundColor(0xE6000000.toInt())
            isClickable = true      // que ningún toque se cuele al hilo de debajo
        }
        val visor = VisorImagenView(this).apply { onCerrar = { cerrarVisorImagen() } }
        capa.addView(visor, android.widget.FrameLayout.LayoutParams(-1, -1))

        // Solo se enseña si no hay nada que pintar todavía: girando encima de la miniatura
        // parecería que está rota, cuando en realidad ya se ve la foto.
        val previa = PostImages.get(url)
        val cargando = android.widget.ProgressBar(this).apply {
            isIndeterminate = true
            visibility = if (previa == null) View.VISIBLE else View.GONE
        }
        capa.addView(cargando, android.widget.FrameLayout.LayoutParams(-2, -2).apply {
            gravity = android.view.Gravity.CENTER
        })

        // La ✕ es por descubrimiento: cerrar tocando la foto es lo estándar, pero no todo el
        // mundo lo prueba, y quedarse encerrado en una foto asusta.
        val dp = resources.displayMetrics.density
        val cerrar = android.widget.TextView(this).apply {
            text = "✕"
            textSize = 20f
            setTextColor(0xFFFFFFFF.toInt())
            // Fondo propio: sobre una foto clara, una ✕ blanca a pelo desaparece.
            setBackgroundColor(0x66000000)
            val h = (18 * dp).toInt(); val v = (12 * dp).toInt()
            setPadding(h, v, h, v)
            contentDescription = "Cerrar la imagen"
            setOnClickListener { cerrarVisorImagen() }
        }
        capa.addView(cerrar, android.widget.FrameLayout.LayoutParams(-2, -2).apply {
            gravity = android.view.Gravity.TOP or android.view.Gravity.END
        })

        // Guardar y compartir, a la vista y también con la pulsación larga: los botones son para
        // quien no la prueba, y la pulsación larga para quien la prueba (lo primero que hizo el
        // tester que pidió esto).
        visor.onPulsacionLarga = { elegirAccionImagen(url) }
        val acciones = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.HORIZONTAL
            addView(botonVisor("Guardar", R.drawable.ic_descargar) { guardarImagen(url) })
            addView(botonVisor("Compartir", R.drawable.ic_share) { compartirImagen(url) })
        }
        capa.addView(acciones, android.widget.FrameLayout.LayoutParams(-2, -2).apply {
            gravity = android.view.Gravity.BOTTOM or android.view.Gravity.CENTER_HORIZONTAL
        })

        // La capa va de borde a borde: sin esto los botones caen sobre la barra de gestos y la ✕
        // debajo de la de estado. Mismo cálculo que la raíz (applyWindowInsets).
        ViewCompat.setOnApplyWindowInsetsListener(capa) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            (cerrar.layoutParams as android.view.ViewGroup.MarginLayoutParams).topMargin = bars.top
            (acciones.layoutParams as android.view.ViewGroup.MarginLayoutParams).bottomMargin =
                bars.bottom + (20 * dp).toInt()
            cerrar.requestLayout()
            acciones.requestLayout()
            insets
        }

        root.addView(capa, android.view.ViewGroup.LayoutParams(-1, -1))
        ViewCompat.requestApplyInsets(capa)
        visorOverlay = capa

        // Se enseña YA la versión que hay en la lista para que no haya un fogonazo negro, y se
        // sustituye por la grande cuando termine de decodificarse. Ampliar la de la lista se ve
        // borroso (está reducida a 1600 px), por eso se vuelve a decodificar más grande.
        previa?.let { visor.setImageBitmap(it) }
        PostImages.loadFull(url, ZoomMath.ladoMaximoVisor(resources.displayMetrics.widthPixels)) { bmp ->
            if (visorOverlay !== capa) return@loadFull   // lo cerraron mientras cargaba
            cargando.visibility = View.GONE
            when {
                bmp != null -> visor.setImageBitmap(bmp)
                PostImages.get(url) == null -> {         // ni grande ni pequeña: no hay nada que ver
                    toast("No se pudo cargar la imagen")
                    cerrarVisorImagen()
                }
            }
        }
    }

    private fun cerrarVisorImagen() {
        visorOverlay?.let { (it.parent as? android.view.ViewGroup)?.removeView(it) }
        // Se suelta la referencia y ya: el bitmap grande (~20 MB) lo recoge el GC. NO se llama a
        // recycle() porque la versión pequeña la comparte la caché de la lista.
        visorOverlay = null
    }

    /** Botón redondeado y semitransparente del visor: se lee sobre una foto clara y oscura. */
    private fun botonVisor(texto: String, icono: Int, alPulsar: () -> Unit): View {
        val dp = resources.displayMetrics.density
        return android.widget.TextView(this).apply {
            text = texto
            textSize = 15f
            setTextColor(0xFFFFFFFF.toInt())
            gravity = android.view.Gravity.CENTER_VERTICAL
            setCompoundDrawablesRelativeWithIntrinsicBounds(icono, 0, 0, 0)
            compoundDrawablePadding = (8 * dp).toInt()
            compoundDrawableTintList = android.content.res.ColorStateList.valueOf(0xFFFFFFFF.toInt())
            background = android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = 24 * dp
                setColor(0x99000000.toInt())
            }
            setPadding((16 * dp).toInt(), (12 * dp).toInt(), (20 * dp).toInt(), (12 * dp).toInt())
            layoutParams = android.widget.LinearLayout.LayoutParams(-2, -2).apply {
                marginStart = (6 * dp).toInt(); marginEnd = (6 * dp).toInt()
            }
            setOnClickListener { alPulsar() }
        }
    }

    private fun elegirAccionImagen(url: String) {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setItems(arrayOf("Guardar en el móvil", "Compartir")) { _, i ->
                if (i == 0) guardarImagen(url) else compartirImagen(url)
            }
            .show()
    }

    /** Una sola descarga a la vez: un segundo toque mientras baja no lanza otra. */
    private var imagenEnCurso = false

    /** Bytes que esperan a que la persona elija dónde guardar (Android 7-9, "guardar como"). */
    private var imagenPorGuardar: ByteArray? = null

    private val guardarComo = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.CreateDocument("image/*")
    ) { destino ->
        val bytes = imagenPorGuardar ?: return@registerForActivityResult
        imagenPorGuardar = null
        if (destino == null) return@registerForActivityResult   // cancelado: no es un error
        toast(if (GuardarImagen.escribirEn(this, destino, bytes)) "Imagen guardada" else "No se pudo guardar la imagen")
    }

    /**
     * Los bytes ORIGINALES de la foto, no el bitmap del visor (ver [GuardarImagen]). Un GIF que
     * ya se está animando tiene sus bytes en memoria; lo demás se vuelve a pedir, por el único
     * sitio que sabe pedirle una imagen a FC con su cookie ([PostImages.descargar]) — y que,
     * leyendo un hilo descargado, la saca del disco sin internet.
     */
    private fun conImagen(url: String, alTener: (ByteArray, TipoImagen) -> Unit) {
        if (imagenEnCurso) return
        PostImages.gif(url)?.let { b -> TipoImagen.de(b)?.let { alTener(b, it); return } }
        imagenEnCurso = true
        lifecycleScope.launch {
            // Solo se avisa si tarda: una foto normal llega antes y el aviso sobraría.
            val aviso = launch { delay(700); toast("Bajando la imagen…") }
            val bytes = withContext(Dispatchers.IO) {
                try { PostImages.descargar(url, MAX_BYTES_GUARDAR_IMAGEN) } catch (_: Exception) { null }
            }
            aviso.cancel()
            imagenEnCurso = false
            val tipo = bytes?.let { TipoImagen.de(it) }
            if (bytes == null || tipo == null) toast("No se pudo bajar la imagen")
            else alTener(bytes, tipo)
        }
    }

    private fun guardarImagen(url: String) = conImagen(url) { bytes, tipo ->
        val nombre = tipo.nombre(System.currentTimeMillis())
        if (GuardarImagen.directoEnGaleria) {
            lifecycleScope.launch {
                val ok = withContext(Dispatchers.IO) {
                    GuardarImagen.enGaleria(this@MainActivity, bytes, tipo, nombre)
                }
                toast(if (ok) "Guardada en Imágenes › ${GuardarImagen.CARPETA}" else "No se pudo guardar la imagen")
            }
        } else {
            imagenPorGuardar = bytes
            try {
                guardarComo.launch(nombre)
            } catch (_: Exception) {
                imagenPorGuardar = null
                toast("No hay ninguna aplicación para guardar archivos")
            }
        }
    }

    private fun compartirImagen(url: String) = conImagen(url) { bytes, tipo ->
        lifecycleScope.launch {
            val uri = withContext(Dispatchers.IO) { GuardarImagen.paraCompartir(this@MainActivity, bytes, tipo) }
            if (uri == null) { toast("No se pudo preparar la imagen"); return@launch }
            val envio = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                type = tipo.mime
                putExtra(android.content.Intent.EXTRA_STREAM, uri)
                // Con ClipData el permiso de lectura llega también a la app que se elija en el
                // selector, no solo al selector.
                clipData = android.content.ClipData.newRawUri(null, uri)
                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(android.content.Intent.createChooser(envio, "Compartir imagen"))
        }
    }

    private fun goBack() {
        // La vista previa de compartir y el visor están por encima de todo: atrás las cierra.
        if (compartirOverlay != null) { cerrarCompartir(); return }
        // El visor de imágenes está por encima de todo: atrás lo cierra.
        if (visorOverlay != null) { cerrarVisorImagen(); return }
        // El overlay de reporte tiene prioridad: atrás lo cancela.
        if (loginCfOverlay != null) { cerrarVerificacionLogin(); return }
        if (reporte.abierto) { reporte.cerrar(); return }
        // Salir de la pantalla completa de vídeo antes que nada.
        if (fullscreenView != null) { onEmbedFullscreen(null, null); return }
        if (isWebVisible) {
            when {
                webView.canGoBack() -> webView.goBack()
                cameFromThread -> showThread()   // la web se abrió desde un hilo nativo
                else -> { showNative(); setSelectedNav(navIdForList()) } // web → lista nativa
            }
            return
        }
        // Lo que se DESCARTA va antes que la pila: son "cierra esto", no "vuelve atrás", y no
        // están en la pila precisamente para que retroceder no pueda resucitarlos (la capa web
        // reapareciendo sería violar la regla de oro).
        if (isPanelCuentaVisible) { cuentaPanel.cerrar(); return }
        if (isLoginVisible) { hideLogin(); return }
        if (isReplyVisible) { hideReply(); return }
        if (isOrganizarVisible) { hideOrganizar(); return }
        if (isNovedadesVisible) { hideNovedades(); return }
        if (isOptionsVisible) { if (!options.volver()) hideOptions(); return }
        if (isPmComposeVisible) { cancelPmCompose(); return }

        // "Sus mensajes" no es una pantalla de la pila: es el MISMO hilo mirado de otra forma.
        // Atrás te devuelve al hilo donde estabas, no a la lista.
        if (vistaDerivada.isNotEmpty() && isThreadVisible) { salirDeSusMensajes(); return }

        // Navegación de verdad: se deshace la pila, sea cual sea la pantalla.
        val destino = nav.pop()
        if (destino != null) { goTo(destino); return }

        // Raíz: nada que deshacer → comportamiento por defecto (salir). Se desactiva el
        // callback y se re-despacha para que el sistema haga el finish.
        onBackCallback.isEnabled = false
        onBackPressedDispatcher.onBackPressed()
        onBackCallback.isEnabled = true
    }
}
