// extractor.js — motor de datos de la UI nativa (rama v2-shell).
//
// REGLA DE ORO anti-Cloudflare: TODA petición a FC sale de este contexto de navegador
// (fetch same-origin con las cookies de la sesión del WebView). Nada de HTTP nativo.
// Este script solo EXTRAE y entrega JSON crudo por el bridge AndroidShell; el filtrado
// (ignorados/keywords) y el render viven en Kotlin.
(function () {
  if (window.__fcExtractorLoaded) { return; }
  window.__fcExtractorLoaded = true;
  if (typeof AndroidShell === 'undefined') { return; }

  var THREAD_SEL = 'a[href*="showthread.php?t="]';

  function tidOf(a) {
    var m = (a.getAttribute('href') || '').match(/[?&]t=(\d+)/);
    return m ? m[1] : null;
  }

  function tidsIn(el) {
    var s = new Set();
    el.querySelectorAll(THREAD_SEL).forEach(function (x) {
      var t = tidOf(x); if (t) s.add(t);
    });
    return s;
  }

  // Mismo algoritmo que content.js: sube desde el anchor hasta el contenedor más
  // ajustado que envuelve UN solo hilo.
  function rowFor(a, tid, doc) {
    var el = a;
    while (el.parentElement && el.parentElement !== doc.body) {
      var ids = tidsIn(el.parentElement);
      if (ids.size > 1 || (ids.size === 1 && !ids.has(tid))) break;
      el = el.parentElement;
    }
    return el;
  }

  function inMenu(a) {
    // Enlaces showthread que NO son hilos: "chrome" de FC presente en cada página. Los hilos
    // reales viven en .threads-list (forumdisplay) o el threadbit clásico (subscription.php);
    // estos contenedores nunca los envuelven. Verificado por CDP (2026-07-25):
    //   · menú de perfil/ajustes → .user-profile-menu-container / #user-profile-menu / #full-window
    //   · banners "notice" (p.ej. "Fold8 y Flip8… para shurs") → #notices-wrapper / .navbar_notice
    //   · pie de página (p.ej. "Contacto") → footer / #footer-wrapper
    return !!(a.closest && a.closest(
      '.user-profile-menu-container, .header-container, #user-profile-menu, #full-window, ' +
      '#notices-wrapper, .navbar_notice, footer, #footer-wrapper'
    ));
  }

  // ── Helpers de envío de formularios (crear/editar/borrar) ───────────────────
  // Copia TODOS los campos de un form real de FC a URLSearchParams (menos ficheros
  // y submits sin marcar), aplica overrides y descarta los de 'drop'. Mismo principio
  // que fcSubmitReply: no reimplementamos nada, reutilizamos el form con su token.
  function copyFormFields(form, overrides, drop) {
    var params = new URLSearchParams();
    form.querySelectorAll('input, textarea, select').forEach(function (el) {
      var nm = el.getAttribute('name'); if (!nm) return;
      var type = (el.getAttribute('type') || '').toLowerCase();
      if (type === 'file') return;
      if (drop && drop.indexOf(nm) !== -1) return;
      if ((type === 'checkbox' || type === 'radio') && !el.checked) return;
      if (type === 'submit') return; // el submit se pasa por override (sbutton)
      params.set(nm, el.value || '');
    });
    if (overrides) Object.keys(overrides).forEach(function (k) { params.set(k, overrides[k]); });
    return params;
  }

  // Texto del mensaje de error de FC (validación, anti-flood, permisos).
  // Entidades HTML (&iacute;, &nbsp;…) → texto. Los errores de FC vienen con acentos así.
  function decodeEnt(s) {
    var t = document.createElement('textarea');
    t.innerHTML = s;
    return t.value;
  }

  function extractErr(html) {
    // FC marca los errores de publicación con comentarios y SIN una sola `class`:
    //   <!--POSTERROR do not remove this comment-->
    //   <ol><li style="color: var(--alert-red-color)">Debes esperar al menos 30 segundos…</li></ol>
    //   <!--/POSTERROR do not remove this comment-->
    // Por eso el patrón de clases de abajo NO los veía nunca y el usuario acababa leyendo
    // "http 200" en vez del motivo. Verificado contra el HTML real (2026-08-15) provocando
    // el límite de mensajes con dos respuestas seguidas.
    var blk = html.match(/POSTERROR[^>]*-->([\s\S]{0,2000}?)<!--\s*\/POSTERROR/i);
    if (blk) {
      var txt = decodeEnt(blk[1].replace(/<[^>]*>/g, ' ')).replace(/\s+/g, ' ').trim();
      if (txt.length >= 4) return txt;
    }
    var em = html.match(/<(?:div|li|p)[^>]*class="[^"]*(?:standard_error|error|blockrow)[^"]*"[^>]*>\s*([^<]{4,200})/i);
    return em ? em[1].replace(/\s+/g, ' ').trim() : '';
  }

  // El primer form del doc que contiene el selector dado (p. ej. el textarea message).
  function formWith(doc, selector) {
    var fs = [].slice.call(doc.querySelectorAll('form'));
    for (var i = 0; i < fs.length; i++) if (fs[i].querySelector(selector)) return fs[i];
    return null;
  }

  // ── Embeds: convierte el markup de FC (blockquotes de Twitter/IG/TikTok, iframes,
  // enlaces de YouTube) en algo que la tarjeta nativa SÍ sabe pintar. YouTube → una
  // miniatura real tocable; el resto → una "tarjeta-enlace" con icono de plataforma.
  function ytIdFrom(url) {
    var m = url.match(/(?:youtu\.be\/|v=|\/embed\/|\/shorts\/)([A-Za-z0-9_-]{6,})/);
    return m ? m[1] : '';
  }

  // Nodo tarjeta-enlace: [icono] Texto → se abre al tocar (URLSpan en el render nativo).
  function embedCard(doc, href, label) {
    var a = doc.createElement('a');
    a.setAttribute('href', href);
    var b = doc.createElement('b');
    b.textContent = label;
    a.appendChild(b);
    return a;
  }

  // Miniatura de YouTube tocable: <a href=watch><img thumb></a> + pie "▶ YouTube".
  function youtubeThumb(doc, videoUrl, id) {
    var wrap = doc.createElement('div');
    var a = doc.createElement('a');
    a.setAttribute('href', videoUrl);
    var img = doc.createElement('img');
    img.setAttribute('src', 'https://i.ytimg.com/vi/' + id + '/hqdefault.jpg');
    a.appendChild(img);
    wrap.appendChild(a);
    wrap.appendChild(doc.createElement('br'));
    wrap.appendChild(embedCard(doc, videoUrl, '▶ Ver en YouTube'));
    return wrap;
  }

  function processEmbeds(m, doc, pageUrl) {
    // Twitter / X
    m.querySelectorAll('blockquote.twitter-tweet').forEach(function (bq) {
      var a = bq.querySelector('a[href*="/status/"]') || bq.querySelector('a[href]');
      var href = a ? a.getAttribute('href') : '';
      var who = (href.match(/(?:twitter|x)\.com\/([^\/]+)\/status/) || [])[1] || '';
      bq.replaceWith(embedCard(doc, href || pageUrl, who ? ('🐦  Ver tweet de @' + who) : '🐦  Ver tweet'));
    });
    // Instagram
    m.querySelectorAll('blockquote.instagram-media, [data-instgrm-permalink]').forEach(function (bq) {
      var href = bq.getAttribute('data-instgrm-permalink') ||
        (bq.querySelector('a[href]') ? bq.querySelector('a[href]').getAttribute('href') : '');
      href = (href || '').split('?')[0];
      bq.replaceWith(embedCard(doc, href || pageUrl, '📷  Ver publicación de Instagram'));
    });
    // TikTok (el blockquote llega vacío → sin esto no se ve NADA)
    m.querySelectorAll('blockquote.tiktok-embed').forEach(function (bq) {
      var vid = bq.getAttribute('data-video-id') || '';
      var href = vid ? ('https://www.tiktok.com/@x/video/' + vid) :
        (bq.querySelector('a[href]') ? bq.querySelector('a[href]').getAttribute('href') : pageUrl);
      bq.replaceWith(embedCard(doc, href, '🎵  Ver vídeo de TikTok'));
    });
    // YouTube en iframe
    m.querySelectorAll('iframe[src*="youtube"], iframe[src*="youtu.be"], iframe[src*="ytimg"]').forEach(function (f) {
      var id = ytIdFrom(f.getAttribute('src') || '');
      if (id) f.replaceWith(youtubeThumb(doc, 'https://www.youtube.com/watch?v=' + id, id));
    });
    // YouTube como enlace pelado (lo más común en FC)
    m.querySelectorAll('a[href*="youtube.com/watch"], a[href*="youtu.be/"], a[href*="youtube.com/shorts"]').forEach(function (a) {
      var id = ytIdFrom(a.getAttribute('href') || '');
      if (id) a.replaceWith(youtubeThumb(doc, a.getAttribute('href'), id));
    });
  }

  // Recoge los embeds de un mensaje como SPECS y los QUITA del HTML: el render nativo los
  // pinta como reproductores oficiales interactivos (EmbedView, WebView por embed). Sustituye
  // a processEmbeds en la vista de hilo — ya no degradamos a tarjeta-enlace.
  /**
   * Saca un embed del mensaje y se lleva por delante el envoltorio que deja VACIO.
   *
   * FC monta el reproductor dentro de su propio <div> (verificado con Vocaroo:
   * `<div style="margin-top:8px"><iframe width=300 height=60 ...></iframe></div>`). Al quitar
   * solo el iframe, ese div se queda sin nada y HtmlCompat lo pinta igual como un parrafo:
   * un hueco en blanco en mitad del mensaje. Se sube quitando ancestros mientras no quede
   * dentro ni texto ni ninguna otra imagen o reproductor.
   */
  function quitarEmbed(el, m) {
    var p = el.parentElement;
    el.remove();
    while (p && p !== m) {
      var abuelo = p.parentElement;
      if ((p.textContent || '').trim() !== '') break;
      if (p.querySelector('img,iframe,video,embed,object')) break;
      p.remove();
      p = abuelo;
    }
  }

  /** Alto declarado por el propio HTML, en CSS px. Solo si es un numero (no "100%"). */
  function altoDeclarado(el) {
    var h = (el.getAttribute('height') || '').trim();
    return /^\d+$/.test(h) ? parseInt(h, 10) : 0;
  }

  /**
   * ¿Este embed vive DENTRO de una cita?
   *
   * Se mira contra `blockquote` porque para cuando corre collectEmbeds las citas de FC
   * (`div.quote`) ya se han convertido en `<blockquote>`. Se arranca desde el PADRE a
   * propósito: el markup de un tweet es él mismo un `blockquote.twitter-tweet`, y
   * preguntarle a él daría siempre que sí.
   */
  function dentroDeCita(el) {
    return !!(el.parentElement && el.parentElement.closest('blockquote'));
  }

  /**
   * Deja un enlace EN EL SITIO del embed, en vez de sacarlo del mensaje.
   *
   * Los reproductores se pintan todos al final del mensaje (son vistas, no texto), y eso
   * dentro de una cita se ve mal de verdad: citas a alguien que puso un tweet y el tweet
   * aparece flotando **debajo de tu respuesta**, fuera del recuadro (reportado por Green Floyd
   * y reproducido por el dueño el 2026-09-12 en `t=8076555`). Dentro de una cita no hace falta
   * un reproductor — lo que quieres es ver QUÉ citaron y poder abrirlo.
   */
  function enlaceEnCita(el, doc, url, etiqueta) {
    var a = doc.createElement('a');
    a.setAttribute('href', url || '');
    a.textContent = '↗ ' + etiqueta;
    if (el.parentNode) el.parentNode.replaceChild(a, el);
    return true;
  }

  function collectEmbeds(m, doc) {
    var embeds = [];
    m.querySelectorAll('blockquote.twitter-tweet').forEach(function (bq) {
      var a = bq.querySelector('a[href*="/status/"]') || bq.querySelector('a[href]');
      var href = a ? a.getAttribute('href') : '';
      if (dentroDeCita(bq)) { enlaceEnCita(bq, doc, href, 'Ver tweet'); return; }
      if (href) embeds.push({ kind: 'twitter', url: href.split('?')[0], id: '' });
      quitarEmbed(bq, m);
    });
    m.querySelectorAll('blockquote.instagram-media, [data-instgrm-permalink]').forEach(function (bq) {
      var href = bq.getAttribute('data-instgrm-permalink') ||
        (bq.querySelector('a[href]') ? bq.querySelector('a[href]').getAttribute('href') : '');
      href = (href || '').split('?')[0];
      if (dentroDeCita(bq)) { enlaceEnCita(bq, doc, href, 'Ver publicación de Instagram'); return; }
      if (href) embeds.push({ kind: 'instagram', url: href, id: '' });
      quitarEmbed(bq, m);
    });
    m.querySelectorAll('blockquote.tiktok-embed').forEach(function (bq) {
      var vid = bq.getAttribute('data-video-id') || '';
      var cite = bq.getAttribute('cite') ||
        (bq.querySelector('a[href]') ? bq.querySelector('a[href]').getAttribute('href') : '');
      if (dentroDeCita(bq)) { enlaceEnCita(bq, doc, (cite || '').split('?')[0], 'Ver TikTok'); return; }
      embeds.push({ kind: 'tiktok', url: (cite || '').split('?')[0], id: vid });
      quitarEmbed(bq, m);
    });
    // YouTube "oficial" de FC: NO es un iframe ni un enlace, sino un <div id=NNN></div> vacío
    // seguido de <script>verVideo('IDVIDEO','NNN')</script> que monta el iframe en runtime.
    // Como parseamos estático (sin ejecutar ese JS), el ID solo vive en el texto del script:
    // lo extraemos aquí o el vídeo se pierde (verificado por CDP 2026-07-24).
    m.querySelectorAll('script').forEach(function (s) {
      var mm = (s.textContent || '').match(/verVideo(?:HD)?\(\s*['"]([A-Za-z0-9_\-]{4,})['"]\s*,\s*['"]?([A-Za-z0-9_\-]+)['"]?\s*\)/);
      if (!mm) return;
      var holder = mm[2] ? m.querySelector('[id="' + mm[2] + '"]') : null;
      var urlYt = 'https://www.youtube.com/watch?v=' + mm[1];
      if (holder && dentroDeCita(holder)) {
        enlaceEnCita(holder, doc, urlYt, 'Ver vídeo de YouTube');
        s.remove();
        return;
      }
      embeds.push({ kind: 'youtube', url: '', id: mm[1] });
      if (holder) quitarEmbed(holder, m);
      else if (s.previousElementSibling) quitarEmbed(s.previousElementSibling, m);
      s.remove();
    });
    m.querySelectorAll('iframe[src*="youtube"], iframe[src*="youtu.be"], iframe[src*="ytimg"]').forEach(function (f) {
      var id = ytIdFrom(f.getAttribute('src') || '');
      if (dentroDeCita(f)) {
        enlaceEnCita(f, doc, 'https://www.youtube.com/watch?v=' + id, 'Ver vídeo de YouTube');
        return;
      }
      if (id) embeds.push({ kind: 'youtube', url: '', id: id });
      quitarEmbed(f, m);
    });
    m.querySelectorAll('a[href*="youtube.com/watch"], a[href*="youtu.be/"], a[href*="youtube.com/shorts"]').forEach(function (a) {
      var id = ytIdFrom(a.getAttribute('href') || '');
      // Dentro de una cita ya ES un enlace: se queda tal cual y se ahorra el reproductor.
      if (dentroDeCita(a)) return;
      if (id) { embeds.push({ kind: 'youtube', url: '', id: id }); quitarEmbed(a, m); }
    });
    m.querySelectorAll('video').forEach(function (v) {
      var src = v.getAttribute('src') ||
        (v.querySelector('source') ? v.querySelector('source').getAttribute('src') : '');
      if (src) { try { src = new URL(src, 'https://forocoches.com/foro/').href; } catch (e) {} }
      if (dentroDeCita(v)) { enlaceEnCita(v, doc, src, 'Ver vídeo'); return; }
      if (src) embeds.push({ kind: 'video', url: src, id: '', alto: altoDeclarado(v) });
      quitarEmbed(v, m);
    });
    // VOCAROO. FC lo monta en TRES piezas (verificado por CDP sobre un post real):
    //   <div><iframe width="300" height="60" src="vocaroo.com/embed/ID"></iframe></div>
    //   <div><img logo><a href="voca.ro/ID">Vocaroo</a></div><br>
    // El reproductor ya lo pinta EmbedView, asi que el pie con el logo y el enlace es
    // fontaneria repetida: dejarla ahi es lo que abria un hueco en blanco en mitad del
    // mensaje (medido en el movil: el texto llegaba como Vocaroo + tres saltos de linea).
    m.querySelectorAll('iframe[src*="vocaroo.com/embed"], iframe[src*="voca.ro/embed"]')
      .forEach(function (f) {
        var src = f.getAttribute('src') || '';
        try { src = new URL(src, 'https://forocoches.com/foro/').href; } catch (e) {}
        embeds.push({ kind: 'iframe', url: src, id: '', alto: altoDeclarado(f) || 60 });
        var pie = f.parentElement ? f.parentElement.nextElementSibling : null;
        if (pie && /voca\.ro|vocaroo/i.test(pie.innerHTML || '')) {
          var salto = pie.nextElementSibling;
          if (salto && salto.tagName === 'BR') salto.remove();
          pie.remove();
        }
        quitarEmbed(f, m);
      });
    m.querySelectorAll('iframe,embed,object').forEach(function (f) {
      var src = f.src || f.getAttribute('src') || f.getAttribute('data') || '';
      // El alto declarado importa: el reproductor de Vocaroo es `height="60"` y sin ese dato
      // se le daba el alto generico (200 px), de ahi el ladrillo verde que reportaron.
      if (src) { try { src = new URL(src, 'https://forocoches.com/foro/').href; } catch (e) {} }
      if (dentroDeCita(f)) { enlaceEnCita(f, doc, src, 'Ver contenido'); return; }
      if (src) embeds.push({ kind: 'iframe', url: src, id: '', alto: altoDeclarado(f) });
      quitarEmbed(f, m);
    });
    return embeds;
  }

  // Fecha del último mensaje ("Actualizado …"). VERIFICADO en el HTML móvil real: FC la pinta
  // en un <a href="showthread.php?p=NNN#postNNN"> (enlace al último post), que NO casa con
  // THREAD_SEL (?t=) → el heurístico viejo ("texto del último anchor de la fila") no la veía
  // NUNCA y la meta salía sin fecha. Se busca por PATRÓN, no por posición, para ser inmune a
  // las dos variantes de markup: invitado ("Hoy 10:46" en spans sueltos) y logueado
  // ("Actualizado Hoy a las 16:12" + botones de no leído/último mensaje).
  // Formatos VERIFICADOS por CDP sobre FC (2026-08-01): "Hoy 10:57", "Ayer 13:32" y, en hilos
  // viejos (p.ej. Modelismo), "01-jul-2026 14:24" — mes ABREVIADO con guiones, NO dd/mm/aa
  // (se tolera igualmente por si cambia el formato de fecha del foro).
  var TIME_RE = /(Hoy|Ayer)\s*(?:a\s+las\s*)?(\d{1,2}:\d{2})/i;
  var DATE_RE = /(\d{1,2}[-\/][A-Za-zÀ-ÿ]{3,10}[-\/]\d{2,4}|\d{1,2}\/\d{1,2}\/\d{2,4})\s*(?:a\s+las\s*)?(\d{1,2}:\d{2})/;

  function lastPostTime(row) {
    var cands = [];
    // Los anchors al último post son los candidatos buenos; el texto de la fila es el
    // último recurso (el título podría contener algo con pinta de hora, por eso va detrás).
    row.querySelectorAll('a[href*="showthread.php?p="], a[href*="#post"]')
      .forEach(function (a) { cands.push(a.textContent); });
    cands.push(row.textContent);
    for (var i = 0; i < cands.length; i++) {
      var t = (cands[i] || '').replace(/\s+/g, ' ');
      var m = t.match(TIME_RE);
      if (m) return m[1].charAt(0).toUpperCase() + m[1].slice(1).toLowerCase() + ' a las ' + m[2];
      m = t.match(DATE_RE);
      if (m) return m[1] + ' ' + m[2];
    }
    return '';
  }

  /**
   * Enlace al ÚLTIMO mensaje del hilo. FC lo pinta en la propia fila (es el anchor del que
   * `lastPostTime` saca la hora), así que no cuesta ni una petición: hasta ahora se tiraba.
   * Se prefiere el que lleva `p=` porque `?t=` abre el hilo por el principio.
   */
  function lastPostHref(row) {
    var a = row.querySelector('a[href*="showthread.php?p="]') ||
            row.querySelector('a[href*="#post"]');
    if (!a) return '';
    try { return new URL(a.getAttribute('href'), 'https://forocoches.com/foro/').href; }
    catch (e) { return ''; }
  }

  // Estado de vBulletin de la fila: el sufijo del viejo statusicon (`_dot_hot_lock_new`), que
  // FC ya no pinta como imagen (gotcha 16) pero deja en un COMENTARIO antes del icono:
  // `<!-- _hot_new-->`. Sondeado el 2026-09-27; `_dot` = has escrito en el hilo (visto al
  // publicar en Pruebas). Con `_dot` hay OTRO comentario al lado —el icono de "participado"
  // que FC deja apagado—, por eso se busca el que SOLO lleva sufijos. Lo interpreta
  // EstadoHilo.kt; aquí solo se extrae.
  function estadoDeFila(row) {
    var doc = row.ownerDocument;
    var w = doc.createTreeWalker(row, 128 /* NodeFilter.SHOW_COMMENT */);
    var c;
    while ((c = w.nextNode())) {
      var t = (c.nodeValue || '').trim();
      if (t && /^(_(dot|hot|lock|new))+$/.test(t)) return t;
    }
    return '';
  }

  function parseRow(row, tid) {
    var anchors = [];
    row.querySelectorAll(THREAD_SEL).forEach(function (x) { anchors.push(x); });
    if (!anchors.length) return null;
    // Título: el anchor con el texto más largo (los otros son contadores/hora). Se descartan
    // los enlaces de salto ("Primer mensaje no leído" / "Último mensaje", con goto= en el
    // href): son ?t= como el título y su texto puede ser MÁS LARGO que un título corto.
    var titled = anchors.filter(function (x) {
      return (x.getAttribute('href') || '').indexOf('goto=') === -1;
    });
    if (titled.length) anchors = titled;
    var titleA = anchors[0];
    anchors.forEach(function (x) {
      if (x.textContent.trim().length > titleA.textContent.trim().length) titleA = x;
    });
    var title = titleA.textContent.replace(/\s+/g, ' ').trim();
    if (!title) return null;

    // Autor: span inmediatamente después del span "@". Respuestas: span numérico contiguo,
    // buscando ATRÁS y luego ADELANTE — de invitado el contador va antes del autor, pero el
    // markup logueado lo pinta a la derecha de la fila; con solo la búsqueda hacia atrás se
    // quedaba vacío.
    var author = '', replies = '';
    var spans = row.querySelectorAll('span');
    function counterAt(idx) {
      var t = spans[idx].textContent.trim();
      // Tolerar separador de miles ("1.680"): sin él, los hilos con ≥1.000
      // respuestas salían sin contador (mismo bug que en parseSearchDoc).
      if (/^[\d.,]+$/.test(t)) return t.replace(/[.,]/g, '');
      return t.length > 6 ? null : ''; // null = corta la búsqueda (ya no es el contador)
    }
    for (var i = 0; i < spans.length; i++) {
      if (spans[i].textContent.trim() === '@') {
        if (i + 1 < spans.length) author = spans[i + 1].textContent.trim();
        for (var j = i - 1; j >= 0 && !replies; j--) {
          var back = counterAt(j);
          if (back === null) break;
          replies = back;
        }
        for (var k = i + 2; k < spans.length && !replies; k++) {
          var fwd = counterAt(k);
          if (fwd === null) break;
          // La hora ("10:46") NO es contador; counterAt ya la descarta por el ':'.
          replies = fwd;
        }
        break;
      }
    }

    var time = lastPostTime(row);

    // No leído: FC marca la fila con statusicon/thread_new.gif o thread_hot_new.gif (el
    // sufijo _new es la señal; los leídos van sin él). Solo lo marca con sesión iniciada.
    // VERIFICADO por CDP (2026-08-04) contra HTML real de forumdisplay.php y subscription.php:
    // ese <img> de statusicon YA NO EXISTE en la plantilla "mobile" que sirve FC actualmente
    // (0 apariciones en ambas páginas en vivo) — solo vive en el fixture antiguo
    // (forumdisplay_old.html, que SÍ trae el <img> en todas sus filas y por tanto toma
    // siempre la rama `icon` de abajo, nunca la del bucle). En la plantilla nueva, en su
    // lugar, el título va envuelto en un ancestro con "font-weight" en negrita (visto
    // "bold" y, en otras reglas de la misma hoja de estilos, valores numéricos como "700"
    // — de ahí que la regex acepte ambos) cuando hay algo sin leer, SIN id fijo (ni
    // #thread_title_<tid>, que tampoco existe ya). Contrastado en dos subforos reales:
    // 40/40 hilos en negrita en uno activo sin leer vs. 0/44 en uno leído de cabo a rabo
    // → la señal es real, no ruido de plantilla. Fijado con fixture
    // (forumdisplay_mobile_unread.html, mismo subforo con mezcla real 24/16).
    var unread = false;
    var icon = row.querySelector('img[src*="statusicon/thread"]');
    if (icon) {
      unread = (icon.getAttribute('src') || '').indexOf('_new') !== -1;
    } else {
      // bold/bolder o numérico ≥600 (p.ej. "700", el peso que usa FC en parte de su CSS
      // actual para textos destacados) cuentan como negrita.
      var boldRe = /font-weight\s*:\s*(bold(er)?\b|([6-9]\d{2}|1000)\b)/i;
      var boldEl = titleA;
      while (boldEl && boldEl !== row.parentElement) {
        var st = (boldEl.getAttribute && boldEl.getAttribute('style')) || '';
        if (boldRe.test(st)) { unread = true; break; }
        boldEl = boldEl.parentElement;
      }
    }

    return {
      tid: tid,
      title: title,
      author: author,
      replies: replies,
      time: time,
      unread: unread,
      estado: estadoDeFila(row),
      url: 'https://forocoches.com/foro/showthread.php?t=' + tid,
      lastUrl: lastPostHref(row)
    };
  }

  function parseListDoc(doc) {
    var seen = new Set();
    var threads = [];
    doc.querySelectorAll(THREAD_SEL).forEach(function (a) {
      var tid = tidOf(a);
      if (!tid || seen.has(tid) || inMenu(a)) return;
      seen.add(tid);
      var item = parseRow(rowFor(a, tid, doc), tid);
      if (item) threads.push(item);
    });
    return threads;
  }

  // Resultados de búsqueda (Mis hilos / Participados). FC NO usa el markup threadbit
  // aquí: cada resultado es un bloque con el título en <a href="showthread.php?t=N&highlight=">
  // y la meta ("NN @ usuario", fecha) en <a href="showthread.php?p=...&highlight=">. El
  // discriminador fiable frente a menús/anuncios/notices es el sufijo &highlight= del href
  // (los enlaces de menú/notice/settings NO lo llevan).
  function parseSearchDoc(doc) {
    var seen = new Set();
    var threads = [];
    doc.querySelectorAll('a[href*="showthread.php?t="][href*="highlight"]').forEach(function (a) {
      var tid = tidOf(a);
      if (!tid || seen.has(tid) || inMenu(a)) return;
      var title = a.textContent.replace(/\s+/g, ' ').trim();
      if (!title) return;
      seen.add(tid);
      // Sube al bloque del resultado (el que agrupa título + meta + fecha, ≥3 enlaces highlight).
      var item = a, el = a;
      for (var i = 0; i < 6 && el; i++) {
        if (el.querySelectorAll('a[href*="highlight"]').length >= 2) item = el;
        if (el.querySelectorAll('a[href*="highlight"]').length >= 3) break;
        el = el.parentElement;
      }
      var author = '', replies = '', time = '';
      item.querySelectorAll('a[href*="highlight"]').forEach(function (x) {
        var tx = x.textContent.replace(/\s+/g, ' ').trim();
        // "90 @ usuario" — OJO: con ≥1.000 respuestas FC pone separador de miles
        // ("1.680 @ usuario"); sin tolerarlo, esos hilos salían SIN autor (bug real).
        var rm = tx.match(/^([\d.,]+)\s*@\s*(.+)$/);
        if (rm) { replies = rm[1].replace(/[.,]/g, ''); author = rm[2].trim(); }
        else if (tx !== title && /\d{1,2}:\d{2}|ayer|hoy|-\w{3}-/i.test(tx)) time = tx;
      });
      threads.push({
        tid: tid, title: title, author: author, replies: replies, time: time,
        url: 'https://forocoches.com/foro/showthread.php?t=' + tid,
        // Tocar la hora abre el hilo por el ÚLTIMO mensaje. Faltaba aquí, así que en las
        // listas de búsqueda —Mis hilos, Participados, el buscador, la actividad de un
        // usuario— la hora no hacía nada y ni siquiera salía el `›` que avisa de que se
        // puede tocar (reportado por Green Floyd el 2026-09-07). El enlace SÍ está en el
        // markup: las anclas de la meta ("61 @ usuario") y de la hora apuntan las dos al
        // mismo `showthread.php?p=…`, que es justo el último mensaje.
        lastUrl: lastPostHref(item)
      });
    });
    return threads;
  }

  // Identidad del usuario logueado a partir de una página traída por fetch (el menú de
  // usuario del HTML recién descargado SÍ trae el u= real; el DOM VIVO puede estar en la
  // home de invitado y devolver u=0, que es justo lo que rompía Mis hilos).
  function ownIdentity(doc) {
    var id = { uid: '', user: '' };
    var hdr = doc.querySelector('.user-profile-menu-header[href*="u="]');
    if (hdr) {
      var m = (hdr.getAttribute('href') || '').match(/u=(\d+)/);
      if (m && m[1] !== '0') id.uid = m[1];
      var un = hdr.querySelector('.username');
      if (un) id.user = un.textContent.replace(/\s+/g, ' ').trim();
    }
    if (!id.uid) {
      var links = doc.querySelectorAll('a[href*="member.php?u="]');
      for (var i = 0; i < links.length; i++) {
        var mm = (links[i].getAttribute('href') || '').match(/u=(\d+)/);
        if (mm && mm[1] !== '0') { id.uid = mm[1]; break; }
      }
    }
    return id;
  }

  // Contadores del menú (MP / citas / menciones) del doc RECIÉN traído — misma lógica
  // que NotificationFetcher.parseCounts en Kotlin: mapear por href, nunca por posición.
  function menuCounts(doc) {
    var c = { pm: 0, quotes: 0, mentions: 0 };
    doc.querySelectorAll('a.menu-item').forEach(function (a) {
      var w = a.querySelector('.user-notifications-count-wrapper');
      if (!w) return;
      var m = (w.textContent || '').match(/\d+/);
      var n = m ? parseInt(m[0], 10) : 0;
      var h = a.getAttribute('href') || '';
      if (h.indexOf('private.php') !== -1) c.pm = n;
      else if (h.indexOf('tab=quotes') !== -1) c.quotes = n;
      else if (h.indexOf('tab=mentions') !== -1) c.mentions = n;
    });
    return c;
  }

  // Enlaces del menú del doc RECIÉN TRAÍDO (llevan el u= del usuario logueado; no se adivinan).
  //
  // OJO, `doc` NO es opcional y NO puede caer a `document`: el DOM vivo es la página que el
  // motor cargó AL ARRANCAR y no se recarga nunca, así que su menú lleva para siempre el u= de
  // la cuenta que estuviera activa entonces. Con varias cuentas eso mandaba `fcLoadProfile` y
  // las pestañas de Citas/Menciones a `member.php?u=<cuenta vieja>` hasta reiniciar la app
  // (medido por CDP el 2026-09-17 con dos cuentas reales: sesión 913877 y el menú vivo diciendo
  // 911128). Misma lección que `ownIdentity(doc)` y `menuCounts(doc)`, que ya reciben el doc.
  // Todas las páginas de FC traen el contenedor entero con el u= correcto (medido en
  // forumdisplay, subscription y private), así que el doc traído siempre basta.
  function menuLinks(doc) {
    var m = {};
    if (!doc) return m;
    doc.querySelectorAll('.user-profile-menu-container a.menu-item').forEach(function (a) {
      var h = a.getAttribute('href') || '';
      if (!h) return;
      if (h.indexOf('private.php') !== -1 && !m.pm) m.pm = a.href;
      else if (h.indexOf('tab=mentions') !== -1 && !m.mentions) m.mentions = a.href;
      else if (h.indexOf('tab=quotes') !== -1 && !m.quotes) m.quotes = a.href;
      else if (h.indexOf('subscription.php') !== -1 && !m.favs) m.favs = a.href;
      else if (h.indexOf('member.php') !== -1 && !m.profile) m.profile = a.href;
    });
    return m;
  }

  // ── Diseño del foro (skin) ──────────────────────────────────────────────────────────
  // FC sirve dos juegos de plantillas y elige por un ajuste de la CUENTA (`styleid`), NO por
  // la petición: sondeado el 2026-08-10, ni `?styleid=N` en la URL ni las cookies de estilo
  // cambian nada. Los selectores de este fichero son los del juego MODERNO, así que con el
  // antiguo no se parsea ni un post. Aquí solo se LEE y se EJECUTA; la política (qué styleid
  // es aceptable y cuándo cambiarlo) vive en Kotlin, en `ForumSkin`.

  // OJO: `styleid` NO es una variable global — FC la declara DENTRO de una función anónima,
  // así que `typeof styleid` es 'undefined' (verificado por CDP; la v1 lo leía así y era
  // código muerto). Se saca del HTML de la propia página, que sí lo contiene, y por eso no
  // cuesta ni una petición.
  function readStyleid() {
    var html = (document.documentElement && document.documentElement.innerHTML) || '';
    var m = html.match(/var\s+styleid\s*=\s*(\d+)/i);
    return m ? parseInt(m[1], 10) : null;
  }

  // SECURITYTOKEN sí es global (lo usa la propia FC en su setOldDesign). De invitado vale
  // literalmente 'guest' y no sirve para escribir.
  function securityToken() {
    if (typeof SECURITYTOKEN === 'undefined' || !SECURITYTOKEN) return '';
    var t = String(SECURITYTOKEN);
    return t === 'guest' ? '' : t;
  }

  /**
   * El `SECURITYTOKEN` de un HTML **recién traído**.
   *
   * Hace falta porque el global de la página cargada SE QUEDA RANCIO: el motor carga
   * forocoches.com una vez al arrancar y ya no la recarga, así que si el usuario inicia sesión
   * dentro de la app ese global sigue valiendo `guest` para siempre. Medido en el dispositivo
   * el 2026-08-26: con sesión iniciada, el token vivo decía `guest` y el de una página traída
   * en ese mismo instante traía el bueno. Fiarse del vivo hacía que el arreglo del diseño en
   * caliente se rindiera con un "inicia sesión" teniendo la sesión puesta.
   */
  function tokenDeHtml(html) {
    var m = html.match(/SECURITYTOKEN\s*=\s*["']([^"']+)["']/);
    var t = m ? m[1] : '';
    return t === 'guest' ? '' : t;
  }

  function reportSkin(extra) {
    var out = { styleid: readStyleid(), session: !!securityToken() };
    for (var k in extra) if (extra.hasOwnProperty(k)) out[k] = extra[k];
    AndroidShell.onSkin(JSON.stringify(out));
  }

  /** Qué diseño nos está sirviendo FC. Sin peticiones: se lee de la página ya cargada. */
  window.fcReadSkin = function () { reportSkin({}); };

  /**
   * Igual que `fcReadSkin`, pero preguntándoselo a una página **recién traída** en vez de a
   * la que el motor lleve cargada desde el arranque.
   *
   * Hace falta porque el diseño es un ajuste de la CUENTA y se puede cambiar desde fuera: si
   * el usuario lo pone en antiguo desde el navegador con la app abierta, el HTML de la página
   * del motor sigue siendo el de hace horas y `fcReadSkin` seguiría diciendo "moderno" para
   * siempre, mientras cada `fetch` nuevo llega en un markup que no sabemos leer. Eso es lo que
   * reportó un tester el 2026-08-21: la app dejaba de ir hasta cerrarla y volver a abrirla.
   *
   * Cache-buster `_fp` por el VARNISH (gotcha 10): sin él FC puede servir una copia rancia y
   * el diagnóstico sería el de antes del cambio.
   *
   * La SESIÓN también sale de esa página, no del token vivo: ver [tokenDeHtml].
   */
  window.fcCheckSkin = function () {
    fetch('https://forocoches.com/foro/?_fp=' + Date.now(), { credentials: 'same-origin' })
      .then(function (r) { return r.text(); })
      .then(function (html) {
        var m = html.match(/var\s+styleid\s*=\s*(\d+)/i);
        AndroidShell.onSkin(JSON.stringify({
          styleid: m ? parseInt(m[1], 10) : null,
          session: !!(tokenDeHtml(html) || securityToken()),
          fresh: true
        }));
      })
      .catch(function (e) { reportSkin({ fresh: true, error: String(e) }); });
  };

  /**
   * Cambia el juego de plantillas de la CUENTA con el MISMO POST que usa FC en su
   * `setOldDesign()`. Devuelve por `onSkin` el styleid REAL tras el cambio, no un "ok"
   * optimista: el éxito se comprueba releyendo, como el resto de escrituras de la app.
   */
  window.fcSwitchSkin = function (target) {
    // El token se pide FRESCO, no se coge del global de la página cargada: ese se queda
    // rancio en cuanto el usuario inicia sesión dentro de la app (ver [tokenDeHtml]), y este
    // POST es justo el que hay que poder hacer para recuperarse de un cambio de diseño.
    fetch('https://forocoches.com/foro/?_fp=' + Date.now(), { credentials: 'same-origin' })
      .then(function (r) { return r.text(); })
      .then(function (html) {
        var tok = tokenDeHtml(html) || securityToken();
        if (!tok) { reportSkin({ switched: false, error: 'sin sesión' }); return null; }
        return fetch('https://forocoches.com/foro/profile.php?do=updatestyleid', {
          method: 'POST',
          credentials: 'same-origin',
          headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
          body: 'newstyleset=' + encodeURIComponent(target) +
                '&securitytoken=' + encodeURIComponent(tok) + '&s=&do=updatestyleid'
        });
      })
      // Parámetro único por el VARNISH (gotcha 10): sin él FC puede devolver una copia
      // cacheada renderizada todavía con el estilo viejo y daríamos el cambio por fallido.
      .then(function (r) {
        if (!r) return null;   // sin sesión: ya se ha avisado arriba
        return fetch('https://forocoches.com/foro/?_fp=' + Date.now(), { credentials: 'same-origin' });
      })
      .then(function (r) { return r ? r.text() : null; })
      .then(function (html) {
        if (html === null) return;
        var m = html.match(/var\s+styleid\s*=\s*(\d+)/i);
        AndroidShell.onSkin(JSON.stringify({
          styleid: m ? parseInt(m[1], 10) : null, session: true, switched: true
        }));
      })
      .catch(function (e) { reportSkin({ switched: false, error: String(e) }); });
  };

  // Lista de subforos del índice (dinámica: si FC añade/quita subforos, la app se adapta).
  window.fcLoadForumList = function (url) {
    fetch(url, { credentials: 'same-origin' })
      .then(function (r) {
        if (!r.ok) throw new Error('http ' + r.status);
        return r.text();
      })
      .then(function (html) {
        var doc = new DOMParser().parseFromString(html, 'text/html');
        var seen = new Set();
        var forums = [];
        doc.querySelectorAll('a[href*="forumdisplay.php?f="]').forEach(function (a) {
          var m = (a.getAttribute('href') || '').match(/[?&]f=(\d+)/);
          if (!m) return;
          // Las "zonas" contenedoras (Zona ForoCoches, Zona Técnica...) son títulos
          // h1.forum-zone-title sin hilos propios: fuera de las pestañas.
          if (a.closest && a.closest('h1, h2, h3, .forum-zone-title')) return;
          var name = a.textContent.replace(/\s+/g, ' ').trim();
          if (!name || name.length > 40 || seen.has(m[1])) return;
          seen.add(m[1]);
          forums.push({ fid: m[1], name: name });
        });
        if (forums.length) {
          AndroidShell.onForumList(JSON.stringify({ forums: forums }));
        }
      })
      .catch(function (e) { /* sin lista de foros la app sigue con General */ });
  };

  // ── Encuesta del hilo ──────────────────────────────────────────────────────
  // Markup REAL de FC (verificado sobre 4 hilos con encuesta), NO es vBulletin de fábrica:
  //
  //   <table class="tborder">
  //     <tr><td class="tcat"><div>Ver Resultados<span class="normal">: PREGUNTA</span></div></td></tr>
  //     <tr><div class="poll-result-bit">   TEXTO OPCIÓN
  //           <div …barra…><span>14</span></div><span>26,92%</span>
  //         </div></tr>
  //   </table>
  //   <span class="smallfont">Votantes: <strong>52</strong>. Tú no puedes votar en esta encuesta</span>
  //
  // TRES TRAMPAS, todas comprobadas contra el HTML real:
  //  1. `<div>` dentro de `<tr>` es HTML INVÁLIDO: el parser aplica *foster parenting* y saca
  //     los .poll-result-bit FUERA de la <table>. Por eso aquí NO se usa closest('table') ni
  //     ninguna relación de árbol: todo se ancla con selectores de documento.
  //  2. El nº de votos NO siempre lleva class="poll-result-white" (solo cuando cabe DENTRO de
  //     la barra; si la barra es estrecha va fuera, en negro y sin clase). Se busca por
  //     CONTENIDO: span de solo dígitos = votos, span acabado en % = porcentaje.
  //  3. El porcentaje viene con COMA decimal ("26,92%").
  // El estado (votantes/permiso) se lee SOLO del span.smallfont de la encuesta, no de toda la
  // página: "cerrada" o "votar" aparecen en los mensajes y darían falsos positivos.
  function pollStateEl(doc) {
    var sfs = doc.querySelectorAll('span.smallfont');
    for (var i = 0; i < sfs.length; i++) {
      if (/Votantes\s*:/i.test(sfs[i].textContent || '')) return sfs[i];
    }
    return null;
  }

  // Nº de opción tal cual lo espera FC: los radios lo llevan en value (optionnumber=N) y
  // las casillas de las multirrespuesta en el nombre (options[N]).
  function pollOptionNum(input) {
    var nm = input.getAttribute('name') || '';
    var m = nm.match(/\[(\d+)\]/);
    if (m) return m[1];
    return (input.value || '').trim();
  }

  // Etiqueta de una opción del formulario de voto. FC envuelve cada control en su <label>
  // (`<label for="rb_optionnumber_1"><input …> Financiado</label>`), así que ese es el ancla
  // buena; el resto son redes de seguridad por si cambian el markup.
  function pollInputLabel(input) {
    var hops = [input.closest('label'), input.closest('td'), input.closest('tr'),
      input.parentNode, input.parentNode ? input.parentNode.parentNode : null];
    for (var i = 0; i < hops.length; i++) {
      var el = hops[i];
      if (!el || !el.textContent) continue;
      var t = el.textContent.replace(/\s+/g, ' ').trim();
      if (t) return t;
    }
    return '';
  }

  function parsePoll(doc) {
    try {
      var bits = doc.querySelectorAll('.poll-result-bit');
      // El form de VOTO es el que apunta a poll.php. NO vale "el primero que lleve pollid": al
      // AUTOR del hilo FC le pone ANTES el de administración (`postings.php`, "Borrar tema"),
      // que también lleva `<input name="pollid">` y ninguna opción → la encuesta salía null y
      // desaparecía justo en tus propios hilos (Nacho, 2026-09-22; medido por CDP el 09-24
      // sobre una encuesta creada en Pruebas). El pollid suelto solo sirve si el form trae
      // opciones de verdad.
      var form = null;
      var forms = doc.querySelectorAll('form');
      for (var i = 0; i < forms.length; i++) {
        if ((forms[i].getAttribute('action') || '').indexOf('poll.php') !== -1) { form = forms[i]; break; }
      }
      if (!form) {
        for (var j = 0; j < forms.length; j++) {
          if (forms[j].querySelector('input[name="pollid"]') &&
              forms[j].querySelector('input[type="radio"], input[type="checkbox"]')) {
            form = forms[j];
            break;
          }
        }
      }
      if (!bits.length && !form) return null;

      // La pregunta vive en DOS sitios distintos según el estado (verificado por CDP):
      //  · resultados → <td class="tcat">Ver Resultados<span class="normal">: PREGUNTA</span>
      //  · votable    → <strong> dentro del propio formulario (ahí NO hay tcat ninguno)
      var qEl = doc.querySelector('td.tcat span.normal');
      var question = qEl ? (qEl.textContent || '').replace(/\s+/g, ' ').replace(/^[:\s]+/, '').trim() : '';
      if (!question && form) {
        var qs = form.querySelector('strong');
        if (qs) question = (qs.textContent || '').replace(/\s+/g, ' ').trim();
      }

      var stEl = pollStateEl(doc);
      var state = stEl ? (stEl.textContent || '').replace(/\s+/g, ' ') : '';
      var tm = state.match(/Votantes\s*:\s*([\d.,]+)/i);
      var total = tm ? parseInt(tm[1].replace(/[.,]/g, ''), 10) || 0 : 0;

      // pollid: el hidden del formulario manda (es el que va a viajar en el POST). Si no hay
      // form, en las encuestas PÚBLICAS el contador enlaza a la lista de votantes
      // (poll.php?do=showresults&pollid=N).
      var pollid = '';
      if (form) {
        var pi = form.querySelector('input[name="pollid"]');
        if (pi && pi.value) pollid = (pi.value || '').trim();
      }
      if (!pollid) {
        var pa = doc.querySelector('a[href*="pollid="]');
        if (pa) {
          var pm = (pa.getAttribute('href') || '').match(/pollid=(\d+)/);
          if (pm) pollid = pm[1];
        }
      }

      // (a) Resultados.
      var results = [];
      for (var b = 0; b < bits.length; b++) {
        var bit = bits[b];
        // Texto de la opción = todo menos la barra. La barra es el <div> hijo; el resto de
        // envoltorios SÍ cuentan porque FC marca TU opción votada con <em>Financiado</em>
        // (y si solo se leyeran los nodos de texto directos, esa opción saldría vacía y se
        // perdería: 4 opciones se quedaban en 3 justo después de votar).
        var text = '', mine = false;
        for (var c = 0; c < bit.childNodes.length; c++) {
          var n = bit.childNodes[c];
          if (n.nodeType === 3) { text += n.textContent; continue; }
          if (n.nodeName === 'DIV') continue; // la barra de resultados
          if (n.nodeName === 'EM') mine = true;
          text += n.textContent || '';
        }
        text = text.replace(/\s+/g, ' ').trim();
        if (!text) continue;
        var votes = 0, pct = -1;
        var spans = bit.querySelectorAll('span');
        for (var s = 0; s < spans.length; s++) {
          var st = (spans[s].textContent || '').replace(/\s+/g, '');
          if (!st) continue;
          if (st.charAt(st.length - 1) === '%') {
            var pv = parseFloat(st.slice(0, -1).replace(',', '.'));
            if (!isNaN(pv)) pct = pv;
          } else if (/^[\d.]+$/.test(st)) {
            votes = parseInt(st.replace(/\./g, ''), 10) || 0;
          }
        }
        var opt = { num: '', text: text, votes: votes, mine: mine };
        if (pct >= 0) opt.pct = pct;
        results.push(opt);
      }

      // (b) Formulario de voto. Todos los radios/casillas del form de la encuesta SON
      // opciones; no se filtra por nombre para no atarse al nombre exacto de FC.
      var voteOpts = [], multiple = false;
      if (form) {
        var inputs = form.querySelectorAll('input[type="radio"], input[type="checkbox"]');
        for (var k = 0; k < inputs.length; k++) {
          var inp = inputs[k];
          var num = pollOptionNum(inp);
          if (!num) continue;
          if ((inp.getAttribute('type') || '').toLowerCase() === 'checkbox') multiple = true;
          voteOpts.push({ num: num, text: pollInputLabel(inp), votes: 0 });
        }
      }

      // Con resultados Y formulario a la vez (encuestas de resultados públicos) se fusionan
      // por posición: los números salen del form y los recuentos de los resultados.
      var options;
      if (voteOpts.length && results.length === voteOpts.length) {
        options = results.map(function (r, i) {
          return {
            num: voteOpts[i].num, text: r.text || voteOpts[i].text,
            votes: r.votes, pct: r.pct, mine: r.mine
          };
        });
      } else if (voteOpts.length) {
        options = voteOpts;
      } else {
        options = results;
      }
      if (!options.length) return null;

      var canVote = !!(form && voteOpts.length && pollid) && !/no puedes votar/i.test(state);
      return {
        id: pollid,
        question: question,
        multiple: multiple,
        closed: /cerrad/i.test(state),
        voted: /ya has votado|has votado/i.test(state),
        canVote: canVote,
        hasResults: results.length > 0,
        totalVotes: total,
        options: options
      };
    } catch (e) {
      return null; // una encuesta rara NUNCA puede tumbar la vista de hilo
    }
  }

  // ── Vista de hilo nativa (Fase 2) ──────────────────────────────────────────
  // Parsea los posts de un showthread traído por fetch same-origin. El HTML del
  // mensaje se SIMPLIFICA para el render nativo: citas → blockquote, embeds →
  // enlace tocable (degradación elegante), URLs relativas → absolutas.
  /**
   * @param canal a dónde va el resultado. Vacío = la pantalla del hilo, como siempre.
   *   `'descarga'` = el camino de "hilos descargados".
   *
   * El parseo es EXACTAMENTE el mismo por los dos caminos —es lo más delicado del motor y no
   * se duplica ni se refactoriza para esto—; lo único que cambia es por qué puerta sale. Así,
   * descargar un hilo de 60 páginas no repinta el que estás leyendo.
   *
   * Se llama SIEMPRE sobre AndroidShell (`AndroidShell.onX(...)`) y nunca guardando el método
   * en una variable: el puente es un objeto inyectado desde Java y sus métodos no sobreviven a
   * que los desates de su objeto (comprobado el 2026-09-21: tampoco se dejan reescribir).
   */
  window.fcLoadThread = function (url, canal) {
    var aDescarga = (canal === 'descarga');
    function emitir(json) {
      if (aDescarga) AndroidShell.onThreadDescarga(json); else AndroidShell.onThread(json);
    }
    function fallo(motivo) {
      if (aDescarga) AndroidShell.onThreadDescargaError(motivo); else AndroidShell.onThreadError(motivo);
    }
    fetch(url, { credentials: 'same-origin' })
      .then(function (r) {
        if (!r.ok) throw new Error('http ' + r.status);
        return r.text().then(function (html) { return { html: html, finalUrl: r.url || '' }; });
      })
      .then(function (res) {
        var html = res.html;
        var doc = new DOMParser().parseFromString(html, 'text/html');
        if (/just a moment|attention required|un momento/i.test(doc.title || '')) {
          fallo('cloudflare');
          return;
        }
        // Hilo restringido (+HD de invitado o sin antigüedad): FC NO enseña login,
        // REDIRIGE a una página informativa misc.php?do=page&template=Info. La señal
        // fiable es la URL final tras redirecciones. Extraemos la MISMA info que
        // enseña el foro (mensaje + metadatos + link de invitación) para reproducirla
        // con los estilos de la app.
        if (res.finalUrl.indexOf('misc.php') !== -1 || res.finalUrl.indexOf('showthread.php') === -1) {
          var info = { msg: '', meta: '', invite: '' };
          var strongs = doc.querySelectorAll('strong');
          for (var si = 0; si < strongs.length; si++) {
            var st = (strongs[si].textContent || '').replace(/\s+/g, ' ').trim();
            // El aviso de FC es el <strong> largo TODO en mayúsculas.
            if (st.length > 25 && st === st.toUpperCase() && /[A-ZÁÉÍÓÚÑ]/.test(st)) { info.msg = st; break; }
          }
          var meta = doc.querySelector('.smallfont-gray');
          if (meta) info.meta = (meta.textContent || '').replace(/\s+/g, ' ').trim();
          var inv = doc.querySelector('a[href*="/invitacion"]');
          if (inv) {
            try { info.invite = new URL(inv.getAttribute('href'), 'https://forocoches.com/').href; } catch (e) {}
          }
          fallo('restricted:' + JSON.stringify(info));
          return;
        }
        var tid = (url.match(/[?&]t=(\d+)/) || [])[1] || '';
        // URLs por post (showthread.php?p=N: citas, menciones y el "último mensaje" del
        // listado): el t= no va en la URL y hay que sacarlo de la propia página.
        //
        // OJO, esto NO es cosmético. FC **ignora `page=` cuando la URL lleva `p=`** (medido
        // por CDP el 2026-09-15 contra el hilo 10804016), así que sin tid la app no puede
        // canonicalizar la URL y TODAS las páginas del hilo devuelven la misma. Es el bug de
        // las "páginas congeladas" que reportó Green Floyd.
        //
        // Se prueban tres fuentes porque la primera falla justo donde apareció el fallo: en
        // un hilo CERRADO no hay permiso para responder, así que vBulletin no pinta la
        // respuesta rápida y no existe el input[name="t"].
        if (!tid) {
          var fuentesDeTid = [
            // 1. Respuesta rápida. Solo en hilos abiertos.
            function () {
              var i = doc.querySelector('input[name="t"]');
              return i ? (i.value || '') : '';
            },
            // 2. Formulario "Buscar en este hilo". Está en los dos casos (verificado en el
            //    10804016 cerrado y en el 10806006 abierto), así que es el fiable.
            function () {
              var f = doc.querySelector('form[action*="searchthreadid="]');
              var a = f ? (f.getAttribute('action') || '') : '';
              return (a.match(/searchthreadid=(\d+)/) || [])[1] || '';
            },
            // 3. Paginación. Solo si el hilo tiene más de una página, pero es justo el caso
            //    en el que la paginación importa.
            function () {
              var a = doc.querySelector('a[href*="showthread.php?t="][href*="page="]');
              var h = a ? (a.getAttribute('href') || '') : '';
              return (h.match(/[?&]t=(\d+)/) || [])[1] || '';
            }
          ];
          for (var fi = 0; fi < fuentesDeTid.length && !tid; fi++) {
            var cand = '';
            try { cand = fuentesDeTid[fi]() || ''; } catch (e) { cand = ''; }
            if (/^\d+$/.test(cand)) tid = cand;
          }
        }
        // Usuario logueado (para marcar los posts propios → menú editar/borrar).
        // El quick-reply del hilo trae un hidden loggedinuser con el nombre.
        var luEl = doc.querySelector('input[name="loggedinuser"]');
        var loggedUser = luEl ? (luEl.value || '').trim() : '';
        var posts = [];
        doc.querySelectorAll('li.postbit').forEach(function (bit) {
          var wrap = bit.closest('div.postbit_wrapper') || bit;
          var pmEl = wrap.querySelector('[id^="postmenu_"]');
          var pid = pmEl ? pmEl.id.replace('postmenu_', '') : '';
          var auEl = pmEl ? pmEl.querySelector('a[href*="member.php"]') : null;
          var author = auEl ? auEl.textContent.trim() : '';
          // UID real del autor. FC enmascara a u=0 en el DOM VIVO por JS, pero el HTML
          // crudo que trae fetch conserva el uid real en el href → perfil ajeno sin
          // peticiones extra. Ver gotcha #1 (la excepción del enlace de mención).
          var uid = '';
          if (auEl) {
            var um = (auEl.getAttribute('href') || '').match(/[?&]u=(\d+)/);
            if (um && um[1] !== '0') uid = um[1];
          }
          var msg = wrap.querySelector('[id^="post_message_"]');
          if (!msg || !pid) return;

          // Cabecera (todo menos el mensaje): fecha y avatar sin contaminarse de citas.
          var header = wrap.cloneNode(true);
          var hm = header.querySelector('[id^="post_message_"]');
          if (hm) hm.remove();
          var dm = (header.textContent || '').replace(/\s+/g, ' ')
            .match(/(Hoy|Ayer|\d{1,2}-[a-z]{3,4}-\d{2,4})[, ]*\d{1,2}:\d{2}/i);
          var date = dm ? dm[0] : '';
          var avatar = '';
          var av = header.querySelector('img[src*="avatar"], img[src*="image.php"]');
          if (av) {
            try { avatar = new URL(av.getAttribute('src'), 'https://forocoches.com/foro/').href; }
            catch (e) {}
          }

          // Mensaje simplificado para render nativo.
          var m = msg.cloneNode(true);
          m.querySelectorAll('style').forEach(function (x) { x.remove(); });
          deshacerCorreosCF(m);
          m.querySelectorAll('div.quote').forEach(function (q) {
            var bq = doc.createElement('blockquote');
            var qb = q.querySelector('b');
            var who = qb ? qb.textContent.trim() : '';
            // Enlace al mensaje CITADO. FC sí lo pone, pero como un <a> que envuelve un
            // <span> cuyo icono es un background-image de CSS: al renderizar en nativo no
            // tiene ni texto ni imagen, así que mide CERO y es imposible de tocar (por eso
            // parecía que las citas no llevaban a ninguna parte). Se rescata el pid y se
            // cuelga de la cabecera, que sí se puede tocar.
            var origen = q.querySelector('a[href*="showthread.php?p="]');
            var pid = origen
              ? ((origen.getAttribute('href') || '').match(/[?&]p=(\d+)/) || [])[1] || ''
              : '';
            // Fuera la cabecera propia de FC ("Cita de Fulano" + ese enlace invisible): la
            // nuestra dice lo mismo, y hasta ahora salían LAS DOS.
            if (origen) {
              var cab = origen.parentElement;
              while (cab && cab.parentElement !== q) cab = cab.parentElement;
              if (cab && cab !== q) cab.remove(); else origen.remove();
            }
            // OJO: "<b>X dijo:</b>" NO es decoración. El filtro de ignorados de Kotlin busca
            // esa cadena EXACTA para ocultar los posts que citan a alguien ignorado, así que
            // el enlace se pone por fuera y la etiqueta se queda igual.
            var etiqueta = who ? '<b>' + who + ' dijo:</b>' : '';
            if (etiqueta && pid) {
              etiqueta = '<a href="https://forocoches.com/foro/showthread.php?p=' + pid + '">' +
                etiqueta + '</a>';
            }
            bq.innerHTML = (etiqueta ? etiqueta + '<br>' : '') + q.innerHTML;
            q.replaceWith(bq);
          });
          // SPOILERS. FC no los pliega: los tapa SOLO con CSS —`.spoiler` pinta el texto
          // del mismo color que el fondo— y los revela con un `onclick` en el propio div.
          // Como el render nativo tira el CSS de clases, en la app salían DESTAPADOS (lo
          // reportó un tester el 2026-08-25). Aquí solo se MARCA el contenido con dos
          // caracteres del Área de Uso Privado; taparlo es cosa de Kotlin (ver Spoilers.kt).
          //   · Se conserva el <div>: es lo que le da al spoiler su propia línea.
          //   · Se marca con nodos de texto y NO reescribiendo innerHTML, porque con spoilers
          //     anidados reescribir el de fuera dejaría el de dentro huérfano y sin marcar.
          m.querySelectorAll('.spoiler').forEach(function (sp) {
            // FC pone SU PROPIA etiqueta justo antes del div (sondeado el 2026-08-26):
            //   <span class="smallfont" style="font-weight:bold">Spoiler:
            //     <span style="color:#afafaf"> [ pulsa para ver ]</span></span>
            // Fuera: nuestro aviso dice lo mismo, y el suyo además MIENTE en la app, porque
            // ese texto no se puede pulsar (lo tocable es el nuestro). Mismo caso que la
            // cabecera "Cita de Fulano", que salía duplicada con la nuestra.
            var prev = sp.previousSibling;
            while (prev && prev.nodeType === 3 && !(prev.textContent || '').trim()) {
              prev = prev.previousSibling;
            }
            if (prev && prev.nodeType === 1 &&
                /^spoiler\s*:/i.test((prev.textContent || '').trim())) {
              prev.parentNode.removeChild(prev);
            }
            sp.removeAttribute('onclick');
            sp.removeAttribute('class');
            sp.insertBefore(doc.createTextNode('\uE000'), sp.firstChild);
            sp.appendChild(doc.createTextNode('\uE001'));
          });
          // Embeds (X/IG/TikTok/YouTube/vídeo/iframe) → specs; se quitan del HTML y se
          // renderizan como reproductores oficiales interactivos (EmbedView).
          var embeds = collectEmbeds(m, doc);
          // Scripts fuera DESPUÉS de collectEmbeds: el YouTube "oficial" de FC vive en un
          // <script>verVideo('ID','cont')</script> que collectEmbeds necesita leer antes.
          m.querySelectorAll('script').forEach(function (x) { x.remove(); });
          m.querySelectorAll('a[href]').forEach(function (a) {
            try { a.setAttribute('href', new URL(a.getAttribute('href'), 'https://forocoches.com/foro/').href); } catch (e) {}
          });
          m.querySelectorAll('img[src]').forEach(function (i) {
            try { i.setAttribute('src', new URL(i.getAttribute('src'), 'https://forocoches.com/foro/').href); } catch (e) {}
          });

          // Post propio: autor == usuario logueado, o hay enlace de edición en el
          // postbit (FC solo lo pinta en los posts que puedes editar).
          var own = (loggedUser && author && author === loggedUser) ||
            !!wrap.querySelector('a[href*="editpost.php"]');

          posts.push({
            pid: pid, author: author, uid: uid, avatar: avatar, date: date,
            html: m.innerHTML, own: !!own, embeds: embeds
          });
        });
        if (!posts.length) {
          // Sin posts + form de login en la página = hilo que requiere cuenta (+HD
          // de invitado, p. ej.). La app enseña NUESTRO login nativo, nunca el foro.
          if (doc.querySelector('input[name="vb_login_username"]')) {
            fallo('login');
            return;
          }
          fallo('empty');
          return;
        }
        // Nº de páginas: el mayor page= de los enlaces del hilo (si no hay, 1).
        var pageCount = 1;
        doc.querySelectorAll('a[href*="showthread.php"][href*="page="]').forEach(function (a) {
          var h = a.getAttribute('href') || '';
          if (tid && h.indexOf('t=' + tid) === -1) return;
          var pm2 = h.match(/[?&]page=(\d+)/);
          if (pm2) pageCount = Math.max(pageCount, parseInt(pm2[1], 10));
        });
        var page = (url.match(/[?&]page=(\d+)/) || [])[1];
        page = page ? parseInt(page, 10) : 1;
        // URLs por post (p=): la página real no va en la URL y hay que deducirla.
        if (!/[?&]page=/.test(url)) {
          // VERIFICADO 2026-08-14: en las respuestas a `?p=NNN`, FC **NO emite**
          // `<link rel="next/prev">` (los dos salen null), así que el heurístico de abajo se
          // quedaba SIEMPRE en la página 1 — el hilo se abría bien, en la página que contiene
          // el post, pero el indicador y la barra de páginas mentían.
          // Lo que sí trae es el PERMALINK del propio post pedido, con su `&page=N`:
          //   <a href="showthread.php?t=10775768&page=61#post517566490">Hoy 15:20</a>
          var pedido = (url.match(/[?&]p=(\d+)/) || [])[1];
          var perma = pedido ? doc.querySelector('a[href*="#post' + pedido + '"]') : null;
          var nPerma = perma
            ? ((perma.getAttribute('href') || '').match(/[?&]page=(\d+)/) || [])[1]
            : null;
          if (nPerma) {
            page = parseInt(nPerma, 10);
          } else {
            // Respaldo: los <link rel> de vBulletin, por si vuelven algún día.
            var lnN = doc.querySelector('link[rel="next"]');
            var lnP = doc.querySelector('link[rel="prev"]');
            var nN = lnN ? ((lnN.getAttribute('href') || '').match(/[?&]page=(\d+)/) || [])[1] : null;
            var nP = lnP ? ((lnP.getAttribute('href') || '').match(/[?&]page=(\d+)/) || [])[1] : null;
            if (nN) page = Math.max(page, parseInt(nN, 10) - 1);
            else if (nP) page = Math.max(page, parseInt(nP, 10) + 1);
          }
        }
        pageCount = Math.max(pageCount, page);
        var title = (doc.title || '').replace(/\s*-\s*Forocoches.*$/i, '').trim();
        // Subforo del hilo, para la miga de la cabecera. La cabecera de FC lo enlaza
        // (sondeado el 2026-09-27 en f=2, f=17 y f=43): el ÚNICO forumdisplay dentro de
        // #header-showthread. Fuera de ella hay otros (el menú lleva "Ayuda", el pie un
        // "Volver a General") y el primero de la página no siempre es el bueno.
        var forum = null;
        var fa = doc.querySelector('#header-showthread a[href*="forumdisplay.php?f="]');
        if (fa) {
          var fm = (fa.getAttribute('href') || '').match(/[?&]f=(\d+)/);
          var fn = (fa.textContent || '').replace(/\s+/g, ' ').trim();
          if (fm && fn) forum = { fid: parseInt(fm[1], 10), name: fn };
        }
        emitir(JSON.stringify({
          url: url, tid: tid, title: title, page: page, pageCount: pageCount,
          counts: menuCounts(doc), posts: posts, poll: parsePoll(doc), forum: forum
        }));
      })
      .catch(function (e) { fallo(String(e)); });
  };

  // ── Votar en la encuesta del hilo ──────────────────────────────────────────
  // Mismo principio que fcSubmitReply: NO se reimplementa el protocolo. Se trae el form REAL
  // de FC, se copian TODOS sus campos ocultos y se marcan las opciones usando el nombre y el
  // valor de los propios inputs de FC. Devuelve por onPollResult la encuesta ya refrescada.
  window.fcPollVote = function (tid, pollid, nums) {
    var base = 'https://forocoches.com/foro/';
    var threadUrl = base + 'showthread.php?t=' + encodeURIComponent(tid);
    var done = function (payload) { AndroidShell.onPollResult(JSON.stringify(payload)); };
    // VARNISH (gotcha 10): sin parámetro único FC sirve una copia cacheada ~1 min y el form
    // vendría rancio (o, tras votar, los resultados sin actualizar).
    fetch(threadUrl + '&_fp=' + Date.now(), { credentials: 'same-origin' })
      .then(function (r) { return r.text(); })
      .then(function (html) {
        var doc = new DOMParser().parseFromString(html, 'text/html');
        var f = formWith(doc, 'input[name="pollid"]');
        if (!f) {
          var fs = doc.querySelectorAll('form');
          for (var i = 0; i < fs.length; i++) {
            if ((fs[i].getAttribute('action') || '').indexOf('poll.php') !== -1) { f = fs[i]; break; }
          }
        }
        if (!f) { done({ ok: false, error: 'La encuesta ya no admite tu voto' }); return null; }

        var params = copyFormFields(f, { do: 'pollvote', pollid: pollid });
        // copyFormFields DESCARTA radios y casillas sin marcar (es lo correcto para el resto
        // de formularios), así que la elección se añade aquí con los name/value REALES.
        var inputs = f.querySelectorAll('input[type="radio"], input[type="checkbox"]');
        var marcadas = 0;
        for (var k = 0; k < inputs.length; k++) {
          var inp = inputs[k];
          if (nums.indexOf(pollOptionNum(inp)) === -1) continue;
          params.set(inp.getAttribute('name') || 'optionnumber', inp.value || pollOptionNum(inp));
          marcadas++;
        }
        if (!marcadas) { done({ ok: false, error: 'No se pudo marcar la opción elegida' }); return null; }

        var action = f.getAttribute('action') || 'poll.php?do=pollvote';
        var post;
        try { post = new URL(action, base).href; } catch (e) { post = base + 'poll.php?do=pollvote'; }
        return fetch(post, {
          method: 'POST', credentials: 'same-origin',
          headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
          body: params.toString()
        }).then(function (r) {
          return r.text().then(function (h2) { return { url: r.url || '', html: h2 }; });
        });
      })
      .then(function (res) {
        if (!res) return;
        // Éxito por la URL FINAL de la redirección, nunca por el HTML (gotcha 9).
        if (res.url.indexOf('showthread.php') === -1) {
          done({ ok: false, error: extractErr(res.html) || 'FC rechazó el voto' });
          return;
        }
        // Relectura con parámetro único para esquivar Varnish y devolver el recuento real.
        fetch(threadUrl + '&_fp=' + Date.now(), { credentials: 'same-origin' })
          .then(function (r) { return r.text(); })
          .then(function (h) {
            var d = new DOMParser().parseFromString(h, 'text/html');
            done({ ok: true, poll: parsePoll(d) });
          })
          .catch(function () { done({ ok: true }); });
      })
      .catch(function (e) { done({ ok: false, error: String(e) }); });
  };

  // ── Responder desde UI nativa (Fase 3) ─────────────────────────────────────
  // NO reimplementamos el protocolo: traemos el form REAL de FC por fetch same-origin,
  // rellenamos solo 'message' y reenviamos con TODOS sus campos ocultos (securitytoken,
  // posthash, poststarttime, signature...) tal cual. FC lo acepta como su propio envío.
  window.fcSubmitReply = function (tid, message) {
    var base = 'https://forocoches.com/foro/newreply.php';
    fetch(base + '?do=newreply&t=' + tid, { credentials: 'same-origin' })
      .then(function (r) { return r.text(); })
      .then(function (html) {
        var doc = new DOMParser().parseFromString(html, 'text/html');
        var forms = [].slice.call(doc.querySelectorAll('form'));
        var f = forms.filter(function (x) { return x.querySelector('textarea[name="message"]'); })[0];
        if (!f) { AndroidShell.onReplyResult(JSON.stringify({ ok: false, error: 'no-form' })); return null; }
        var params = new URLSearchParams();
        f.querySelectorAll('input, textarea, select').forEach(function (el) {
          var nm = el.getAttribute('name'); if (!nm) return;
          var type = (el.getAttribute('type') || '').toLowerCase();
          if (type === 'file') return;
          if (type === 'submit') { if (nm === 'sbutton') params.set(nm, el.value || 'Responder'); return; }
          if (nm === 'preview') return;
          if (nm === 'message') { params.set(nm, message); return; }
          params.set(nm, el.value || '');
        });
        params.set('do', 'postreply');
        if (!params.has('message')) params.set('message', message);
        // El form de FC viene con wysiwyg=1 (editor visual HTML): en ese modo los \n
        // se colapsan como espacio en blanco. Nuestro composer manda texto plano +
        // BBCode → wysiwyg=0 para que vBulletin convierta los \n en saltos reales.
        params.set('wysiwyg', '0');
        return fetch(base + '?do=postreply', {
          method: 'POST', credentials: 'same-origin',
          headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
          body: params.toString()
        });
      })
      .then(function (r) {
        if (!r) return;
        // ÉXITO = vBulletin redirige a showthread.php (r.url es la URL FINAL tras la
        // redirección). OJO: no vale mirar si hay textarea "message" en el HTML — la
        // página del hilo lleva quick-reply con ese mismo name y daba falsos errores.
        var finalUrl = r.url || '';
        return r.text().then(function (html) {
          var ok = finalUrl.indexOf('showthread.php') !== -1;
          if (!ok && /showthread\.php\?p=\d+/.test(html) && html.indexOf('newreply.php?do=postreply') === -1) {
            ok = true; // interstitial "gracias por su mensaje" con refresh al hilo
          }
          // El motivo lo pone FC (límite de 30 s entre mensajes, mensaje duplicado, hilo
          // cerrado…). "http N" es el último recurso: Kotlin lo traduce a un aviso legible.
          var errM = ok ? '' : (extractErr(html) || ('http ' + r.status));
          // finalUrl: Kotlin le saca el pid del mensaje recién publicado para saltar a él
          // (replyPidFromUrl). vBulletin redirige a showthread.php?p=NNN#postNNN; si no lo
          // trae, Kotlin cae al último post cargado.
          AndroidShell.onReplyResult(JSON.stringify({ ok: ok, error: errM, finalUrl: finalUrl }));
        });
      })
      .catch(function (e) { AndroidShell.onReplyResult(JSON.stringify({ ok: false, error: String(e) })); });
  };

  // ── Crear hilo / editar / borrar (mismo patrón: form real + su token) ───────

  // Crea un hilo en el subforo f: trae el form de newthread, copia sus campos,
  // pone asunto+mensaje y reenvía con do=postthread. Éxito = redirige al hilo.
  // `encuesta`: JSON de EncuestaNueva (Kotlin) o vacío. Con encuesta son DOS envíos (medido por
  // CDP el 2026-09-24): el hilo con postpoll=yes&polloptions=N, que NO redirige al hilo sino a
  // poll.php?t=T&polloptions=N, y ahí el formulario de la encuesta. Si el segundo falla, el hilo
  // YA existe: se contesta ok con pollError, para que Kotlin lo diga claro en vez de "creado".
  window.fcCreateThread = function (fid, subject, message, encuesta) {
    var base = 'https://forocoches.com/foro/newthread.php';
    var poll = null;
    try { poll = encuesta ? JSON.parse(encuesta) : null; } catch (e) { poll = null; }
    function responder(o) { o.action = 'create'; AndroidShell.onThreadAction(JSON.stringify(o)); }
    fetch(base + '?do=newthread&f=' + fid, { credentials: 'same-origin' })
      .then(function (r) { return r.text(); })
      .then(function (html) {
        var doc = new DOMParser().parseFromString(html, 'text/html');
        var f = formWith(doc, 'textarea[name="message"]');
        if (!f) { responder({ ok: false, error: 'no-form' }); return null; }
        var params = copyFormFields(f, {
          subject: subject, message: message, do: 'postthread', wysiwyg: '0'
        }, ['preview']);
        if (!params.has('sbutton')) params.set('sbutton', 'Enviar el nuevo tema');
        if (poll) {
          params.set('postpoll', 'yes');
          params.set('polloptions', String(poll.opciones.length));
        }
        return fetch(base + '?do=postthread', {
          method: 'POST', credentials: 'same-origin',
          headers: { 'Content-Type': 'application/x-www-form-urlencoded' }, body: params.toString()
        });
      })
      .then(function (r) {
        if (!r) return;
        var fu = r.url || '';
        return r.text().then(function (h) {
          var tid = (fu.match(/showthread\.php\?t=(\d+)/) || [])[1] ||
            (h.match(/showthread\.php\?t=(\d+)/) || [])[1] || '';
          var pollTid = (fu.match(/poll\.php\?t=(\d+)/) || [])[1] ||
            (h.match(/poll\.php\?t=(\d+)/) || [])[1] || '';
          if (poll && pollTid) return crearEncuesta(pollTid, poll);
          var ok = fu.indexOf('showthread.php') !== -1 || !!tid || !!pollTid;
          responder({
            ok: ok, error: ok ? '' : extractErr(h), tid: tid || pollTid,
            // Pedía encuesta y FC no pasó por poll.php: el hilo está, la encuesta no.
            pollError: (ok && poll) ? 'FC no pidió la encuesta' : ''
          });
        });
      })
      .catch(function (e) { responder({ ok: false, error: String(e) }); });

    function crearEncuesta(t, p) {
      var url = 'https://forocoches.com/foro/poll.php?t=' + t + '&polloptions=' + p.opciones.length;
      return fetch(url, { credentials: 'same-origin' })
        .then(function (r) { return r.text(); })
        .then(function (html) {
          var doc = new DOMParser().parseFromString(html, 'text/html');
          var f = formWith(doc, 'input[name="question"]');
          if (!f) { responder({ ok: true, tid: t, pollError: extractErr(html) || 'no-form' }); return null; }
          var params = copyFormFields(f, { question: p.pregunta, do: 'postpoll', t: t },
            ['preview', 'updatenumber']);
          for (var i = 0; i < p.opciones.length; i++) params.set('options[' + (i + 1) + ']', p.opciones[i]);
          params.set('polloptions', String(p.opciones.length));
          params.set('timeout', p.dias > 0 ? String(p.dias) : '');
          // Las casillas: se manda el value REAL del form si está marcada, y nada si no.
          ['multiple', 'public'].forEach(function (nm) {
            var quiere = nm === 'multiple' ? p.multiple : p.publica;
            var el = f.querySelector('input[name="' + nm + '"]');
            if (quiere) params.set(nm, (el && el.value) || '1'); else params.delete(nm);
          });
          params.set('sbutton', 'Enviar Encuesta');
          var accion = f.getAttribute('action') || ('poll.php?do=postpoll&t=' + t);
          return fetch(new URL(accion, 'https://forocoches.com/foro/').href, {
            method: 'POST', credentials: 'same-origin',
            headers: { 'Content-Type': 'application/x-www-form-urlencoded' }, body: params.toString()
          }).then(function (r) {
            var fu = r.url || '';
            return r.text().then(function (h) {
              var ok = fu.indexOf('showthread.php') !== -1;
              responder({ ok: true, tid: t, pollError: ok ? '' : (extractErr(h) || 'error') });
            });
          });
        })
        .catch(function (e) { responder({ ok: true, tid: t, pollError: String(e) }); });
    }
  };

  // Carga el BBCode actual de un post para precargar el editor al editar.
  // OJO wysiwyg=0 EN LA URL: sin él FC sirve el form en modo editor visual (wysiwyg=1) y
  // el textarea trae HTML (<b>, <br>, <img> de smilies), no BBCode. Como al guardar
  // mandamos wysiwyg=0 ("esto es BBCode"), vBulletin escapaba ese HTML y el post salía
  // con todas las etiquetas literales (bug real que hubo). FC respeta el parámetro.
  window.fcLoadPostForEdit = function (pid) {
    fetch('https://forocoches.com/foro/editpost.php?do=editpost&wysiwyg=0&p=' + pid, { credentials: 'same-origin' })
      .then(function (r) { return r.text(); })
      .then(function (html) {
        var doc = new DOMParser().parseFromString(html, 'text/html');
        var ta = doc.querySelector('textarea[name="message"]');
        // Título del primer post: 'title' (skin nuevo) o 'subject'. Solo los primeros
        // posts lo traen con valor; en respuestas normales no existe o va vacío.
        var subj = doc.querySelector('input[name="title"]') || doc.querySelector('input[name="subject"]');
        var hasSubject = !!(subj && (subj.value || '').trim());
        AndroidShell.onEditLoad(JSON.stringify({
          pid: pid, ok: !!ta,
          message: ta ? ta.value : '',
          subject: subj ? (subj.value || '') : '',
          hasSubject: hasSubject
        }));
      })
      .catch(function (e) { AndroidShell.onEditLoad(JSON.stringify({ pid: pid, ok: false, error: String(e) })); });
  };

  // Guarda la edición de un post (do=updatepost). Éxito = vuelve al hilo.
  window.fcEditPost = function (pid, message, subject) {
    var base = 'https://forocoches.com/foro/editpost.php';
    // wysiwyg=0 también aquí: el form del que copiamos los campos ocultos debe estar en el
    // MISMO modo (BBCode) con el que enviamos.
    fetch(base + '?do=editpost&wysiwyg=0&p=' + pid, { credentials: 'same-origin' })
      .then(function (r) { return r.text(); })
      .then(function (html) {
        var doc = new DOMParser().parseFromString(html, 'text/html');
        var f = formWith(doc, 'textarea[name="message"]');
        if (!f) { AndroidShell.onThreadAction(JSON.stringify({ action: 'edit', ok: false, error: 'no-form' })); return null; }
        var ov = { message: message, do: 'updatepost', wysiwyg: '0' };
        // El primer post del hilo lleva el título en 'title' (skin nuevo) o 'subject'.
        if (subject) {
          if (f.querySelector('input[name="title"]')) ov.title = subject;
          else if (f.querySelector('input[name="subject"]')) ov.subject = subject;
        }
        var params = copyFormFields(f, ov, ['preview']);
        if (!params.has('sbutton')) params.set('sbutton', 'Guardar cambios');
        return fetch(base + '?do=updatepost', {
          method: 'POST', credentials: 'same-origin',
          headers: { 'Content-Type': 'application/x-www-form-urlencoded' }, body: params.toString()
        });
      })
      .then(function (r) {
        if (!r) return;
        var fu = r.url || '';
        return r.text().then(function (h) {
          var ok = fu.indexOf('showthread.php') !== -1;
          AndroidShell.onThreadAction(JSON.stringify({ action: 'edit', ok: ok, error: ok ? '' : extractErr(h), pid: pid }));
        });
      })
      .catch(function (e) { AndroidShell.onThreadAction(JSON.stringify({ action: 'edit', ok: false, error: String(e) })); });
  };

  // Borra un post propio. En el skin nuevo la página de confirmación viene VACÍA
  // (el borrado se dispara por JS), así que tomamos el token del form de EDICIÓN
  // (mismo editpost, sí lo trae) y montamos el POST de borrado a mano. Verificamos
  // el resultado re-consultando si el post sigue siendo editable (si no, se borró).
  window.fcDeletePost = function (pid) {
    var base = 'https://forocoches.com/foro/editpost.php';
    fetch(base + '?do=editpost&p=' + pid, { credentials: 'same-origin' })
      .then(function (r) { return r.text(); })
      .then(function (html) {
        var doc = new DOMParser().parseFromString(html, 'text/html');
        var f = formWith(doc, 'textarea[name="message"]');
        if (!f) { AndroidShell.onThreadAction(JSON.stringify({ action: 'delete', ok: false, error: 'no-form' })); return null; }
        var st = f.querySelector('[name="securitytoken"]');
        var sEl = f.querySelector('[name="s"]');
        var params = new URLSearchParams();
        params.set('do', 'deletepost');
        params.set('postid', pid);
        params.set('p', pid);
        params.set('securitytoken', st ? (st.value || '') : '');
        if (sEl) params.set('s', sEl.value || '');
        params.set('deletepost', 'delete'); // radio "borrar" (vs restaurar)
        params.set('deletetype', '1');      // borrado normal (soft)
        params.set('reason', '');
        return fetch(base + '?do=deletepost', {
          method: 'POST', credentials: 'same-origin',
          headers: { 'Content-Type': 'application/x-www-form-urlencoded' }, body: params.toString()
        });
      })
      .then(function (r) {
        if (!r) return;
        return r.text().then(function () {
          // Verificación fiable: ¿sigue editable el post? Si ya no, quedó borrado.
          return fetch(base + '?do=editpost&p=' + pid, { credentials: 'same-origin' })
            .then(function (r2) { return r2.text(); })
            .then(function (h2) {
              var doc2 = new DOMParser().parseFromString(h2, 'text/html');
              var gone = !doc2.querySelector('textarea[name="message"]');
              AndroidShell.onThreadAction(JSON.stringify({
                action: 'delete', ok: gone, error: gone ? '' : 'No se pudo borrar el mensaje', pid: pid
              }));
            });
        });
      })
      .catch(function (e) { AndroidShell.onThreadAction(JSON.stringify({ action: 'delete', ok: false, error: String(e) })); });
  };

  // Favoritos (suscripción a hilo). Verificado por CDP (2026-07-22):
  // - El botón de showthread es ESTÁTICO (siempre "addsubscription", esté suscrito o no):
  //   el estado real SOLO se ve en las filas de subscription.php.
  // - Alta: form real de do=addsubscription → POST a do=doaddsubscription (la respuesta
  //   es 200 sin redirección útil: verificar re-consultando la lista).
  // - Baja: la página do=removesubscription del skin nuevo es un STUB vacío (como la de
  //   deletepost) pero el GET YA EJECUTA el borrado server-side; añadimos securitytoken
  //   igualmente y verificamos re-consultando la lista.
  // - GOTCHA VARNISH: FC sirve HTML tras Varnish y páginas PRIVADAS como subscription.php
  //   llegan de caché (x-cache: HIT, age de hasta ~1 min). Leer el estado justo después
  //   de cambiarlo devuelve el estado ANTERIOR y cache:'no-store' NO ayuda (la caché es
  //   del servidor). Antídoto: query param único (_fp=timestamp) → tratada como URL
  //   nueva → contenido fresco (verificado: x-cache pasa de HIT a SHARED con age 0).
  window.fcToggleFavorite = function (tid) {
    var base = 'https://forocoches.com/foro/subscription.php';
    function fail(e) {
      AndroidShell.onThreadAction(JSON.stringify({ action: 'fav', ok: false, error: String(e), tid: tid }));
    }
    function fetchDoc(url) {
      return fetch(url, { credentials: 'same-origin' })
        .then(function (r) { return r.text(); })
        .then(function (h) { return new DOMParser().parseFromString(h, 'text/html'); });
    }
    function freshList() { return fetchDoc(base + '?_fp=' + Date.now()); }
    // Suscrito = el tid aparece como FILA de la lista (mismo criterio que la pestaña
    // Favoritos: parseListDoc), no como link de menú/banner.
    function subscribed(doc) {
      return parseListDoc(doc).some(function (t) { return t.tid === tid; });
    }
    freshList()
      .then(function (doc) {
        if (!subscribed(doc)) {
          // ALTA: copiar el form real (securitytoken, s, threadid, url, folderid...).
          return fetchDoc(base + '?do=addsubscription&t=' + tid + '&_fp=' + Date.now()).then(function (d2) {
            var f = formWith(d2, 'input[value="doaddsubscription"]');
            if (!f) { fail('login'); return null; }
            var params = copyFormFields(f, { emailupdate: '0' }, []);
            return fetch(base + '?do=doaddsubscription&threadid=' + tid, {
              method: 'POST', credentials: 'same-origin',
              headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
              body: params.toString()
            }).then(function () { return true; });
          });
        }
        // BAJA: GET con token (la página devuelta es un stub; el efecto es server-side).
        var tokEl = doc.querySelector('input[name="securitytoken"]');
        var tok = tokEl ? tokEl.value : '';
        return fetch(base + '?do=removesubscription&t=' + tid +
          (tok ? '&securitytoken=' + encodeURIComponent(tok) : ''),
          { credentials: 'same-origin' }).then(function () { return false; });
      })
      .then(function (wanted) {
        if (wanted === null || wanted === undefined) return;
        // Verificación con evidencia: ¿quedó la lista como debía? (fresca, sin Varnish)
        return freshList().then(function (doc) {
          var now = subscribed(doc);
          AndroidShell.onThreadAction(JSON.stringify({
            action: 'fav', ok: now === wanted, fav: now, tid: tid,
            error: now === wanted ? '' : 'La suscripción no cambió'
          }));
        });
      })
      .catch(fail);
  };

  // ── Mensajes privados nativos ──────────────────────────────────────────────
  // La regla de oro exige que los MP NO caigan a la capa web. Aquí se extrae la
  // bandeja y el detalle de private.php (fetch same-origin) y se emiten como JSON;
  // el envío copia el form REAL (do=newpm → do=insertpm, wysiwyg=0). Cache-buster _fp
  // por el gotcha Varnish: tras enviar/leer, la lista podría llegar rancia.

  // Simplifica el cuerpo de un MP igual que un post (citas → blockquote, embeds →
  // tarjeta, URLs absolutas) para el mismo render nativo.
  // ── Correos ofuscados por Cloudflare ───────────────────────────────────────
  // FC está detrás de Cloudflare y su "email obfuscation" reescribe CUALQUIER correo del HTML:
  //   <a href="/cdn-cgi/l/email-protection#HEX"><span class="__cf_email__"
  //      data-cfemail="HEX">[email&nbsp;protected]</span></a>
  // En un navegador lo deshace un script de Cloudflare; aquí ese script ni corre ni se inyecta
  // (el motor quita los <script>), así que al usuario le llegaba el literal "[email protected]"
  // y un enlace a cdn-cgi — reportado el 2026-09-17 como "los MP con un correo los bloquea
  // Cloudflare". No los bloquea: los tapa, y la app no los destapaba.
  // El dato viaja en el propio HEX: el primer byte es la clave y el resto va XOR con ella.
  function descifrarCorreoCF(hex) {
    if (!hex || hex.length < 4 || (hex.length % 2)) return '';
    var k = parseInt(hex.substr(0, 2), 16);
    if (isNaN(k)) return '';
    var out = '';
    for (var i = 2; i < hex.length; i += 2) {
      var c = parseInt(hex.substr(i, 2), 16);
      if (isNaN(c)) return '';
      out += String.fromCharCode(c ^ k);
    }
    // Si no parece un correo, mejor dejar lo que hubiera que inventarse un texto.
    return /^[^@\s]+@[^@\s]+\.[^@\s]{2,}$/.test(out) ? out : '';
  }

  // Deja el correo como TEXTO, no como enlace `mailto:`: el enlace añadiría otra vía de salida
  // a una app externa que habría que probar aparte, y lo que hace falta es poder leerlo y
  // copiarlo (pulsación larga → Copiar mensaje).
  function deshacerCorreosCF(el) {
    if (!el || !el.querySelectorAll) return;
    el.querySelectorAll('.__cf_email__').forEach(function (sp) {
      var txt = descifrarCorreoCF(sp.getAttribute('data-cfemail') || '');
      if (!txt) return;
      // El <a> de cdn-cgi que suele envolverlo se va con él: apunta a Cloudflare, no al correo.
      var a = sp.closest ? sp.closest('a[href*="/cdn-cgi/l/email-protection"]') : null;
      var destino = a || sp;
      // `ownerDocument` y no `document`: estos cuerpos vienen de un DOMParser, así que el nodo
      // de texto tiene que nacer en SU documento, no en el de la página del motor.
      if (destino.parentNode) {
        destino.parentNode.replaceChild(destino.ownerDocument.createTextNode(txt), destino);
      }
    });
    // Forma sin <span>: el HEX va en el fragmento del propio enlace.
    el.querySelectorAll('a[href*="/cdn-cgi/l/email-protection"]').forEach(function (a) {
      var h = a.getAttribute('href') || '';
      var txt = descifrarCorreoCF((h.split('#')[1] || ''));
      if (!txt) return;
      if (a.parentNode) a.parentNode.replaceChild(a.ownerDocument.createTextNode(txt), a);
    });
  }

  function simplifyPmBody(msg, doc) {
    var m = msg.cloneNode(true);
    m.querySelectorAll('script,style').forEach(function (x) { x.remove(); });
    deshacerCorreosCF(m);
    // Un MP no guarda la conversación aparte: cada respuesta trae los mensajes anteriores
    // DENTRO del cuerpo, como citas anidadas (`div.squote > div.quote > [cabecera "Cita de
    // <b>X</b>"] + [contenido]`). Se convierten de DENTRO hacia FUERA (orden inverso del
    // documento) y MOVIENDO nodos, no rehaciendo el HTML desde texto: antes se hacía al revés y
    // con innerHTML, y las citas de dentro quedaban huérfanas sin convertir — la conversación
    // salía aplanada, "Cita de A / Cita de B / Cita de A / …", sin saber quién dijo qué
    // (2026-09-24). La cabecera de FC se tira: el "X dijo:" ya dice lo mismo.
    var citas = m.querySelectorAll('div.quote');
    for (var ci = citas.length - 1; ci >= 0; ci--) {
      var q = citas[ci];
      var bq = doc.createElement('blockquote');
      var cab = null;
      for (var hi = 0; hi < q.children.length; hi++) {
        if (/^\s*Cita de\b/.test(q.children[hi].textContent || '')) { cab = q.children[hi]; break; }
      }
      var qb = (cab || q).querySelector('b');
      var who = qb ? qb.textContent.trim() : '';
      if (cab) cab.remove();
      if (who) {
        var tb = doc.createElement('b');
        tb.textContent = who + ' dijo:';
        bq.appendChild(tb);
        bq.appendChild(doc.createElement('br'));
      }
      while (q.firstChild) bq.appendChild(q.firstChild);
      // El envoltorio `div.squote` no aporta nada y, como div, mete un salto de más.
      var envoltorio = q.parentNode;
      if (envoltorio && envoltorio.classList && envoltorio.classList.contains('squote') &&
          envoltorio.children.length === 1) {
        envoltorio.replaceWith(bq);
      } else {
        q.replaceWith(bq);
      }
    }
    processEmbeds(m, doc, 'https://forocoches.com/foro/private.php');
    m.querySelectorAll('iframe,video,embed,object').forEach(function (f) {
      var src = f.src || f.getAttribute('src') || '';
      f.replaceWith(embedCard(doc, src || '#', '▶  Ver contenido'));
    });
    m.querySelectorAll('a[href]').forEach(function (a) {
      try { a.setAttribute('href', new URL(a.getAttribute('href'), 'https://forocoches.com/foro/').href); } catch (e) {}
    });
    m.querySelectorAll('img[src]').forEach(function (i) {
      try { i.setAttribute('src', new URL(i.getAttribute('src'), 'https://forocoches.com/foro/').href); } catch (e) {}
    });
    return m.innerHTML;
  }

  // `carpeta`: '0' (o nada) = Entrada; '-1' = Elementos enviados. Es la MISMA plantilla de
  // vBulletin en las dos: en enviados el enlace a member.php de la fila es el DESTINATARIO.
  window.fcLoadPmInbox = function (carpeta) {
    carpeta = carpeta === '-1' ? '-1' : '0';
    var qs = carpeta === '-1' ? 'folderid=-1&' : '';
    fetch('https://forocoches.com/foro/private.php?' + qs + '_fp=' + Date.now(), { credentials: 'same-origin' })
      .then(function (r) { return r.text(); })
      .then(function (html) {
        var doc = new DOMParser().parseFromString(html, 'text/html');
        if (/just a moment|attention required|un momento/i.test(doc.title || '')) {
          AndroidShell.onPmData(JSON.stringify({ view: 'inbox', carpeta: carpeta, error: 'cloudflare' })); return;
        }
        var pms = [], seen = {};
        doc.querySelectorAll('a[href*="do=showpm"]').forEach(function (a) {
          var mm = (a.getAttribute('href') || '').match(/pmid=(\d+)/);
          if (!mm || seen[mm[1]]) return;
          var pmid = mm[1];
          var subject = (a.querySelector('strong') || a).textContent.replace(/\s+/g, ' ').trim();
          if (!subject) return;
          seen[pmid] = 1;
          // Bloque de la fila: subir hasta tener remitente y fecha en el mismo contenedor.
          var row = a;
          for (var i = 0; i < 6 && row.parentElement; i++) {
            row = row.parentElement;
            if (row.querySelector('[onclick*="member.php"], a[href*="member.php?u="]')) break;
          }
          var senderEl = row.querySelector('[onclick*="member.php"], a[href*="member.php?u="]');
          var sender = senderEl ? senderEl.textContent.replace(/\s+/g, ' ').trim() : '';
          var sid = '';
          if (senderEl) {
            var sm = (senderEl.getAttribute('onclick') || senderEl.getAttribute('href') || '').match(/u=(\d+)/);
            if (sm) sid = sm[1];
          }
          var dm = row.textContent.match(/(Hoy|Ayer|\d{1,2}-[a-z]{3,4}-\d{2,4})[, ]*\d{1,2}:\d{2}/i);
          var unread = row.innerHTML.indexOf('message-unread-icon') !== -1;
          pms.push({
            pmid: pmid, subject: subject, sender: sender, senderId: sid,
            date: dm ? dm[0] : '', unread: unread
          });
        });
        AndroidShell.onPmData(JSON.stringify({
          view: 'inbox', carpeta: carpeta, pms: pms, menu: menuLinks(doc), counts: menuCounts(doc)
        }));
      })
      .catch(function (e) { AndroidShell.onPmData(JSON.stringify({ view: 'inbox', carpeta: carpeta, error: String(e) })); });
  };

  window.fcLoadPm = function (pmid) {
    fetch('https://forocoches.com/foro/private.php?do=showpm&pmid=' + pmid + '&_fp=' + Date.now(),
      { credentials: 'same-origin' })
      .then(function (r) { return r.text(); })
      .then(function (html) {
        var doc = new DOMParser().parseFromString(html, 'text/html');
        if (/just a moment|attention required|un momento/i.test(doc.title || '')) {
          AndroidShell.onPmData(JSON.stringify({ view: 'detail', pmid: pmid, error: 'cloudflare' })); return;
        }
        var msg = doc.querySelector('[id^="post_message_"]');
        var body = msg ? simplifyPmBody(msg, doc) : '';
        // Remitente. **NO vale el primer `member.php` de la página**: los primeros son los del
        // MENÚ, o sea los del usuario que MIRA — por eso el detalle de un MP enseñaba tu propio
        // nombre como remitente de todos los mensajes (reportado el 2026-08-27). Es la misma
        // trampa del gotcha 1, y aquí se distingue por el uid: los del menú comparten el uid de
        // quien mira, y el remitente es el primero con uno DISTINTO.
        var deMiembro = [].slice.call(
          doc.querySelectorAll('a[href*="member.php?u="], [onclick*="member.php"]')
        );
        function uidDe(el) {
          var src = el.getAttribute('href') || el.getAttribute('onclick') || '';
          return (src.match(/u=(\d+)/) || [])[1] || '';
        }
        var uidPropio = deMiembro.length ? uidDe(deMiembro[0]) : '';
        var senderEl = null;
        for (var si = 0; si < deMiembro.length; si++) {
          var t = (deMiembro[si].textContent || '').replace(/\s+/g, ' ').trim();
          if (t && uidDe(deMiembro[si]) !== uidPropio) { senderEl = deMiembro[si]; break; }
        }
        // Si todos comparten uid (un MP tuyo a ti mismo, o una página rara) se deja el primero.
        if (!senderEl) senderEl = deMiembro[0] || null;
        var sender = senderEl ? senderEl.textContent.replace(/\s+/g, ' ').trim() : '';
        // El del menú viene con "N Posts M Hilos" pegado; ese recorte SOLO se aplica ahí, que
        // hay usuarios con dígitos en el nombre y les cortaría la mitad ("ShurTifosi-46").
        if (/\d+\s+(Posts|Hilos)/i.test(sender)) {
          sender = (sender.match(/^[^\d]+/) || [sender])[0].trim() || sender;
        }
        var subj = (doc.title || '').replace(/^Forocoches\s*-\s*/, '').trim();
        var reply = doc.querySelector('a[href*="do=newpm&pmid="], a[href*="do=newpm&amp;pmid="]');
        AndroidShell.onPmData(JSON.stringify({
          view: 'detail', pmid: pmid, subject: subj, sender: sender,
          body: body, canReply: !!reply
        }));
      })
      .catch(function (e) { AndroidShell.onPmData(JSON.stringify({ view: 'detail', pmid: pmid, error: String(e) })); });
  };

  // Enviar MP: nuevo (recipients+title) o respuesta (pmid presente). Copia el form
  // REAL de FC con wysiwyg=0 (mismo principio que fcSubmitReply/fcCreateThread) y solo
  // sustituye destinatario/asunto/mensaje. Éxito = FC redirige fuera de newpm/insertpm.
  /**
   * La cita del MP al que se va a responder.
   *
   * **No la construimos**: FC ya rellena la textarea de `private.php?do=newpm&pmid=N` con un
   * `[QUOTE=...]…[/QUOTE]` hecho, además del destinatario y el asunto en "Re: …" (sondeado el
   * 2026-08-27 sobre un MP real). Hasta ahora se tiraba, porque al enviar se sustituía
   * `message` entero por lo que hubiera escrito el usuario, y por eso al responder no se veía
   * a quién estabas contestando.
   *
   * Es la MISMA petición que ya hace el envío: no es tráfico nuevo, es adelantarla.
   */
  /**
   * La cita de un mensaje **tal y como la construye ForoCoches**.
   *
   * No se reconstruye desde el HTML a propósito, que era la opción barata y está mal: el
   * smiley `goofy.gif` se escribe `:roto2:`, así que adivinar el código por el nombre del
   * fichero produce un smiley distinto. Pidiéndosela a FC llegan además las fotos como
   * `[IMG]…[/IMG]` y el formato (negritas, enlaces) intacto.
   *
   * `wysiwyg=0` es obligatorio (gotcha 4): sin él la textarea viene en HTML.
   */
  window.fcLoadQuote = function (pid) {
    fetch('https://forocoches.com/foro/newreply.php?do=newreply&p=' + pid + '&wysiwyg=0', {
      credentials: 'same-origin'
    })
      .then(function (r) { return r.text(); })
      .then(function (html) {
        var doc = new DOMParser().parseFromString(html, 'text/html');
        var ta = doc.querySelector('textarea[name="message"]');
        AndroidShell.onQuoteBody(JSON.stringify({
          pid: pid, bbcode: ta ? (ta.value || ta.textContent || '') : ''
        }));
      })
      .catch(function () {
        // Sin cita de FC se sigue con la de texto plano de siempre: peor, pero nunca nada.
        AndroidShell.onQuoteBody(JSON.stringify({ pid: pid, bbcode: '' }));
      });
  };

  window.fcLoadPmQuote = function (pmid) {
    fetch('https://forocoches.com/foro/private.php?do=newpm&pmid=' + pmid + '&wysiwyg=0', {
      credentials: 'same-origin'
    })
      .then(function (r) { return r.text(); })
      .then(function (html) {
        var doc = new DOMParser().parseFromString(html, 'text/html');
        var ta = doc.querySelector('textarea[name="message"]');
        var rec = doc.querySelector('input[name="recipients"]');
        AndroidShell.onPmData(JSON.stringify({
          view: 'quote',
          pmid: pmid,
          cita: ta ? (ta.value || ta.textContent || '') : '',
          para: rec ? (rec.value || '') : ''
        }));
      })
      .catch(function () {
        // Sin cita se responde igual: es una ayuda, no un requisito.
        AndroidShell.onPmData(JSON.stringify({ view: 'quote', pmid: pmid, cita: '', para: '' }));
      });
  };

  window.fcSendPm = function (recipients, title, message, pmid) {
    var formUrl = pmid
      ? 'https://forocoches.com/foro/private.php?do=newpm&pmid=' + pmid + '&wysiwyg=0'
      : 'https://forocoches.com/foro/private.php?do=newpm&wysiwyg=0';
    fetch(formUrl, { credentials: 'same-origin' })
      .then(function (r) { return r.text(); })
      .then(function (html) {
        var doc = new DOMParser().parseFromString(html, 'text/html');
        var f = formWith(doc, 'textarea[name="message"]');
        if (!f) { AndroidShell.onPmData(JSON.stringify({ view: 'send', ok: false, error: 'no-form' })); return null; }
        var ov = { message: message, wysiwyg: '0', do: 'insertpm' };
        if (recipients) ov.recipients = recipients;
        if (title) ov.title = title;
        var params = copyFormFields(f, ov, ['preview']);
        if (!params.has('sbutton')) params.set('sbutton', 'Enviar Mensaje');
        var action = pmid ? ('private.php?do=insertpm&pmid=' + pmid) : 'private.php?do=insertpm';
        return fetch('https://forocoches.com/foro/' + action, {
          method: 'POST', credentials: 'same-origin',
          headers: { 'Content-Type': 'application/x-www-form-urlencoded' }, body: params.toString()
        });
      })
      .then(function (r) {
        if (!r) return;
        var fu = r.url || '';
        return r.text().then(function (h) {
          var err = extractErr(h);
          var ok = !err && fu.indexOf('insertpm') === -1 && fu.indexOf('do=newpm') === -1;
          AndroidShell.onPmData(JSON.stringify({
            view: 'send', ok: ok, error: ok ? '' : (err || 'No se pudo enviar el mensaje')
          }));
        });
      })
      .catch(function (e) { AndroidShell.onPmData(JSON.stringify({ view: 'send', ok: false, error: String(e) })); });
  };

  // Cabecera y "Sobre mí" de una ficha member.php (propia o ajena). HAY DOS MARKUPS, y el que
  // importa es el que NO se ve desde un PC: FC elige plantilla por el User-Agent, y la app
  // (WebView de Android) recibe la MÓVIL, styleid 8. Medido el 2026-10-02 con curl en las dos:
  //   · móvil (8, la de la app): <h3>NOMBRE</h3> + <p>título</p>; la firma entre los
  //     comentarios <!-- signature row --> y <!-- / signature row -->; "Registro: <span>fecha";
  //     "Sobre X" con <span class="user_info_icon" style="background-image: var(--coches)">
  //     y el valor como texto suelto al lado.
  //   · escritorio (9): <h2>NOMBRE </h2> + <h4>título</h4> + <p id="signature"> +
  //     <span>Desde&nbsp</span><span>fecha</span>; "Sobre mí" en #collapseobj_aboutme con
  //     span.background_icon y el valor en un <span> hermano.
  // La primera versión de esta función (2026-09-27) se escribió solo con la de escritorio,
  // porque la sonda fue un curl SIN User-Agent: en el móvil se habría quedado sin título, sin
  // firma y sin "Sobre mí". Todo se ANCLA al encabezado con SU nombre, porque la cabecera de
  // la página trae los datos del usuario LOGUEADO (gotcha 1 y 33).
  function leerFicha(doc, username) {
    var out = { hilos: '', mensajes: '', registro: '', rango: '', firma: '', firmaTexto: '', sobreMi: [] };
    // \s ya incluye el espacio duro (el `Desde&nbsp` de FC).
    var norm = function (t) { return (t || '').replace(/\s+/g, ' ').trim(); };
    var nombre = norm(username).toLowerCase();
    var cab = null;
    [].slice.call(doc.querySelectorAll('h2, h3')).some(function (h) {
      if (nombre && norm(h.textContent).toLowerCase() === nombre) { cab = h; return true; }
      return false;
    });
    // La firma: se queda en TEXTO + ENLACES (decisión del dueño, 2026-09-27). Sin imágenes:
    // suelen ser banners, y en Html.fromHtml dejarían un U+FFFC (gotcha 21).
    function ponFirma(nodo) {
      if (!nodo || out.firmaTexto) return;
      out.firmaTexto = norm(nodo.textContent);
      out.firma = firmaSimple(nodo);
    }
    if (cab && cab.tagName === 'H3') {
      // Móvil: el título va en el <p> justo debajo del nombre (sonda del 2026-09-24).
      var p3 = cab.nextElementSibling;
      if (p3 && p3.tagName === 'P') out.rango = norm(p3.textContent).replace(/^ForoCoches:\s*/i, '');
    } else if (cab) {
      var caja = cab.parentElement && cab.parentElement.parentElement;
      var h4 = cab.parentElement.querySelector('h4');
      if (h4) out.rango = norm(h4.textContent).replace(/^ForoCoches:\s*/i, '');
      if (caja) {
        [].slice.call(caja.querySelectorAll('span')).some(function (s) {
          if (norm(s.textContent).toLowerCase() !== 'desde') return false;
          var sig = s.nextElementSibling;
          if (sig) out.registro = norm(sig.textContent);
          return true;
        });
        ponFirma(caja.querySelector('p#signature'));
      }
    }
    // Firma de la plantilla móvil: no tiene id ni clase, solo los dos comentarios que la
    // rodean. Quien no tiene firma deja los comentarios sin nada dentro (o sin el de cierre).
    if (!out.firmaTexto) {
      var it = doc.createNodeIterator(doc.body, 128 /* NodeFilter.SHOW_COMMENT */);
      var c;
      while ((c = it.nextNode())) {
        if (norm(c.nodeValue) !== 'signature row') continue;
        var cont = doc.createElement('div');
        for (var n = c.nextSibling; n; n = n.nextSibling) {
          if (n.nodeType === 8 && norm(n.nodeValue) === '/ signature row') break;
          if (n.nodeType === 8) continue;
          if (n.nodeType === 1 && /^\s*Registro:/i.test(n.textContent || '')) break;
          cont.appendChild(n.cloneNode(true));
        }
        if (norm(cont.textContent)) ponFirma(cont);
        break;
      }
    }
    // Fecha de alta. Móvil: "Registro: <span>03-oct-2010</span>". Se busca el <span> de la
    // etiqueta, y solo DESPUÉS del nombre del perfil: lo que haya antes es la cabecera, que es
    // de quien mira.
    // Sin encabezado con su nombre no hay fecha: mejor ninguna que la de quien mira.
    if (!out.registro && cab) {
      [].slice.call(doc.querySelectorAll('span')).some(function (s) {
        if (!(cab.compareDocumentPosition(s) & 4 /* DOCUMENT_POSITION_FOLLOWING */)) return false;
        if (!/^Registro:/i.test(norm(s.textContent))) return false;
        var m = norm(s.textContent).match(/^Registro:\s*([\w-]+)/i);
        if (m) out.registro = m[1];
        return !!m;
      });
    }
    // "Sobre mí": cada fila es un icono (var(--coches), var(--ubicacion-icon)…) y su valor.
    // Qué significa cada icono y qué se descarta (N/A) lo decide Kotlin (FichaMiembro).
    doc.querySelectorAll('span.user_info_icon, #collapseobj_aboutme span.background_icon').forEach(function (ic) {
      var m = (ic.getAttribute('style') || '').match(/var\(--([\w-]+)\)/);
      var fila = ic.parentElement;
      if (!m || !fila) return;
      out.sobreMi.push({ icono: m[1], valor: norm(fila.textContent) });
    });
    // Antigüedad y actividad del usuario. Lo pidió un tester el 2026-08-22 para poder
    // reconocer bots y cagahilos sin salirse de la app ("registrado en agosto y ya lleva
    // 70 hilos y 400 mensajes").
    //
    // Se leen de los ENLACES de las estadísticas, no buscando la etiqueta por el texto, y
    // hay dos motivos:
    //   1. El número va DELANTE de su etiqueta ("38 Hilos", "7.253 Mensajes"), así que un
    //      /Hilos\s+([\d.]+)/ devuelve los MENSAJES. Casi cae en la trampa.
    //   2. El menú de la cabecera trae los contadores del usuario LOGUEADO
    //      ("neonforger 261 Posts 16 Hilos"), así que buscar por etiqueta a secas te da los
    //      tuyos y no los suyos — la misma familia de trampa que el gotcha 1.
    // El href lleva `searchuser=<nombre>`, que es la comprobación de a quién pertenecen.
    //
    // Se busca entre TODOS los enlaces y no con querySelector: con sesión, el PRIMER enlace
    // de la página es el del menú de cabecera (el tuyo), la comprobación lo descartaba con
    // razón y en fichas ajenas no salía NINGÚN número (medido el 2026-09-27 en la de Dastan:
    // vacío con sesión, 155 hilos / 34.086 mensajes sin ella).
    function numeroDe(sel) {
      var clave = 'searchuser=' + encodeURIComponent(username).toLowerCase();
      var suyo = null;
      [].slice.call(doc.querySelectorAll(sel)).some(function (a) {
        // Tiene que ser del usuario que estamos mirando, no del que mira.
        var href = (a.getAttribute('href') || '').toLowerCase();
        if (username && href.indexOf(clave) === -1) return false;
        var m = (a.textContent || '').replace(/\s+/g, ' ').trim().match(/^([\d.,]+)/);
        if (!m) return false;
        suyo = m[1];
        return true;
      });
      return suyo || '';
    }
    out.hilos = numeroDe('a[href*="starteronly=1"]');
    out.mensajes = numeroDe('a[href*="showposts=1"]');
    return out;
  }

  // Una firma reducida a texto, <a href> absolutos y <br>. Todo lo demás (imágenes, estilos,
  // colores) se tira: la app la pinta con Html.fromHtml y los enlaces van por el mismo router
  // que los de los posts.
  function firmaSimple(nodo) {
    var esc = function (t) {
      return t.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
    };
    var html = '';
    (function recorrer(n) {
      n.childNodes.forEach(function (c) {
        if (c.nodeType === 3) { html += esc(c.textContent); return; }
        if (c.nodeType !== 1) return;
        var tag = c.tagName.toLowerCase();
        if (tag === 'br') { html += '<br>'; return; }
        if (tag === 'img' || tag === 'script' || tag === 'style') return;
        if (tag === 'a' && c.getAttribute('href')) {
          var href = '';
          try { href = new URL(c.getAttribute('href'), 'https://forocoches.com/foro/').href; } catch (e) {}
          var dentro = esc((c.textContent || '').trim());
          if (href && /^https?:/i.test(href) && dentro) {
            html += '<a href="' + href.replace(/"/g, '%22') + '">' + dentro + '</a>';
          }
          return;
        }
        recorrer(c);
      });
    })(nodo);
    return html.trim();
  }

  // Perfil de OTRO usuario (mención tocada). El enlace de mención SÍ trae el uid real
  // (member.php?u=NNN), a diferencia de los autores de post (enmascarados a u=0). Se
  // extrae nombre + avatar para pintar un perfil nativo (la regla de oro prohíbe la web).
  window.fcLoadMember = function (uid) {
    fetch('https://forocoches.com/foro/member.php?u=' + uid + '&_fp=' + Date.now(), { credentials: 'same-origin' })
      .then(function (r) { return r.text(); })
      .then(function (html) {
        var doc = new DOMParser().parseFromString(html, 'text/html');
        if (/just a moment|attention required|un momento/i.test(doc.title || '')) {
          AndroidShell.onMemberData(JSON.stringify({ uid: uid, error: 'cloudflare' })); return;
        }
        var username = (doc.title || '').replace(/^Forocoches\s*-\s*Ver Perfil:\s*/i, '').trim();
        // TODAS las candidatas, en orden: la elección es de Kotlin (`MemberAvatar`), que la ancla
        // al uid. Quedarse con la PRIMERA era el bug que reportó un tester: en el skin moderno la
        // primera imagen de avatar de la página es la de la cabecera, o sea la del usuario
        // LOGUEADO, así que el perfil ajeno salía con tu propia foto.
        var avatars = [];
        doc.querySelectorAll('img[src*="customavatars"], img[src*="image.php?u"]').forEach(function (i) {
          try { avatars.push(new URL(i.getAttribute('src'), 'https://forocoches.com/foro/').href); }
          catch (e) {}
        });
        var pm = doc.querySelector('a[href*="private.php?do=newpm"]');
        var newpm = pm ? pm.getAttribute('href') : ('private.php?do=newpm&u=' + uid);

        var ficha = leerFicha(doc, username);

        // ¿Admite MPs? FC solo pinta "Enviar mensaje privado" a quien los acepta, y con sesión
        // la ficha trae los enlaces de acción sobre ESE uid (gotcha 26). Si están las acciones
        // (agregar a contactos/ignorados) pero no la del MP, es que no los admite. Sin
        // acciones (invitado, o FC cambia el markup) no se sabe, y entonces el botón se queda.
        function accionSobreEl(patron) {
          if (!/^\d+$/.test(String(uid))) return false;
          return [].slice.call(doc.querySelectorAll('a[href*="' + patron + '"]')).some(function (a) {
            return new RegExp('[?&](amp;)?u(serid)?=' + uid + '(\\D|$)').test(a.getAttribute('href') || '');
          });
        }
        var mpPosible = accionSobreEl('do=newpm') ? 'si'
                      : accionSobreEl('do=addlist') ? 'no' : '';

        AndroidShell.onMemberData(JSON.stringify({
          uid: uid, username: username, avatars: avatars, newpmUrl: newpm, mpPosible: mpPosible,
          hilos: ficha.hilos, mensajes: ficha.mensajes, registro: ficha.registro, rango: ficha.rango,
          firma: ficha.firma, firmaTexto: ficha.firmaTexto, sobreMi: ficha.sobreMi
        }));
      })
      .catch(function (e) { AndroidShell.onMemberData(JSON.stringify({ uid: uid, error: String(e) })); });
  };

  // ── Lista de ignorados de la cuenta ────────────────────────────────────────
  // Hasta 2026-08-21 esto lo pedía IgnoreListFetcher con HTTP NATIVO (HttpURLConnection
  // + las cookies del CookieManager + un User-Agent copiado a mano del WebView). Funcionaba,
  // pero por disfraz: si Cloudflare aprieta, ese camino se come el challenge y la lista
  // vuelve vacía SIN que nadie se entere. Aquí sale del motor como todo lo demás, así que
  // es inmune por diseño y no por parecerse al navegador.
  //
  // Se entrega el HTML CRUDO a propósito, en contra de la costumbre de este fichero de
  // mandar JSON ya parseado: el parser de esta página vive en Kotlin (IgnoreListParser),
  // está cubierto por tests contra markup real y lo que había que mudar era la PETICIÓN,
  // no la extracción. Reescribirlo aquí habría tirado esa cobertura a cambio de nada.
  //
  // `session` es imprescindible: de invitado, FC no da un 403 — devuelve una página
  // perfectamente válida SIN ningún usuario, indistinguible de "no ignoras a nadie". Sin
  // esta señal, un despiste de sesión se leería como una lista vacía legítima.
  window.fcLoadIgnoreList = function () {
    // _fp: Varnish cachea páginas privadas ~1 min (gotcha 10). Sin esto, justo después de
    // tocar la lista en el foro se leería el estado anterior.
    fetch('https://forocoches.com/foro/profile.php?do=ignorelist&_fp=' + Date.now(),
      { credentials: 'same-origin' })
      .then(function (r) { return r.text(); })
      .then(function (html) {
        var doc = new DOMParser().parseFromString(html, 'text/html');
        var tok = doc.querySelector('input[name="securitytoken"]');
        // De invitado el token es literalmente 'guest' (gotcha 6): es la señal fiable,
        // porque el esqueleto del HTML es idéntico con sesión y sin ella.
        var session = !!tok && tok.value !== 'guest';
        AndroidShell.onIgnoreList(JSON.stringify({
          ok: true, session: session, html: html
        }));
      })
      .catch(function (e) {
        AndroidShell.onIgnoreList(JSON.stringify({ ok: false, error: String(e) }));
      });
  };

  // ── Cabecera de un hilo (para la tarjeta de compartir) ─────────────────────
  // Trae SOLO el primer mensaje de la página 1. Existe porque compartir un hilo desde la
  // página 30 no tenía forma de saber cómo empieza, y una tarjeta que cambia de contenido
  // según por dónde vayas leyendo es peor que no tenerla.
  //
  // NO reutiliza el bucle de fcLoadThread a propósito: aquel resuelve citas, embeds, uid
  // real, posts propios… nada de eso pinta en una tarjeta, y tocarlo para hacerlo reutilizable
  // sería meter mano en el corazón de la app para una función decorativa. Aquí basta con
  // autor, fecha, avatar y el HTML del mensaje (que Kotlin pasa a texto con el MISMO
  // CompartirFC.cuerpoTarjeta que usa la tarjeta de un mensaje suelto).
  // Quién escribió un mensaje concreto, para el "último que escribe" de la lista. FC no lo da en
  // ninguna página barata (gotcha 18, revisado el 2026-10-02), pero respeta `pp=1`: con
  // `showthread.php?p=N&pp=1` devuelve la página con ESE mensaje solo (~33 KB por el cable en la
  // plantilla móvil, medido). El autor está donde siempre: el enlace a member.php dentro de su
  // `postmenu_N`. Sin `_fp`: el autor de un mensaje no cambia, y si Varnish (gotcha 10) sirve una
  // copia, mejor. Vacío = no se pudo saber (hilo +HD de invitado, gotcha 7; alguien a quien
  // ignoras, cuyo post llega como muñón SIN postmenu_, gotcha 23; o un fallo de red). Qué se
  // pide y cuándo lo decide Kotlin (UltimosPosteadores).
  function autorDelMensaje(doc, pid) {
    var pm = doc.getElementById('postmenu_' + pid);
    var a = pm ? pm.querySelector('a[href*="member.php"]') : null;
    return a ? (a.textContent || '').replace(/\s+/g, ' ').trim() : '';
  }

  window.fcLoadLastPoster = function (pid) {
    if (!/^\d+$/.test(String(pid))) return;
    fetch('https://forocoches.com/foro/showthread.php?p=' + pid + '&pp=1', { credentials: 'same-origin' })
      .then(function (r) { return r.text(); })
      .then(function (html) {
        var doc = new DOMParser().parseFromString(html, 'text/html');
        AndroidShell.onLastPoster(JSON.stringify({ pid: String(pid), author: autorDelMensaje(doc, pid) }));
      })
      .catch(function () {
        AndroidShell.onLastPoster(JSON.stringify({ pid: String(pid), author: '' }));
      });
  };

  window.fcLoadThreadHead = function (tid) {
    fetch('https://forocoches.com/foro/showthread.php?t=' + tid + '&_fp=' + Date.now(),
      { credentials: 'same-origin' })
      .then(function (r) { return r.text(); })
      .then(function (html) {
        var doc = new DOMParser().parseFromString(html, 'text/html');
        var bit = null;
        var bits = doc.querySelectorAll('li.postbit');
        for (var i = 0; i < bits.length && !bit; i++) {
          // Un ignorado llega como muñón SIN postmenu_ (gotcha 23): si el hilo lo abre
          // alguien a quien ignoras, no hay primer mensaje que enseñar y se salta.
          if (bits[i].querySelector('[id^="postmenu_"]')) bit = bits[i];
        }
        if (!bit) { AndroidShell.onThreadHead(JSON.stringify({ ok: false, tid: tid })); return; }
        var wrap = bit.closest('div.postbit_wrapper') || bit;
        var pmEl = wrap.querySelector('[id^="postmenu_"]');
        var auEl = pmEl ? pmEl.querySelector('a[href*="member.php"]') : null;
        var msg = wrap.querySelector('[id^="post_message_"]');
        var header = wrap.cloneNode(true);
        var hm = header.querySelector('[id^="post_message_"]');
        if (hm) hm.remove();
        var dm = (header.textContent || '').replace(/\s+/g, ' ')
          .match(/(Hoy|Ayer|\d{1,2}-[a-z]{3,4}-\d{2,4})[, ]*\d{1,2}:\d{2}/i);
        var avatar = '';
        var av = header.querySelector('img[src*="avatar"], img[src*="image.php"]');
        if (av) {
          try { avatar = new URL(av.getAttribute('src'), 'https://forocoches.com/foro/').href; }
          catch (e) {}
        }
        // El mensaje CRUDO no vale para una tarjeta: FC mete los vídeos como un <div> vacío
        // + un <script> con verVideo(...) (gotcha 13), y al pasar eso a texto el <script> se
        // pinta ENTERO — visto en el PNG real, con "verVideo('r6gd0wcx28o','50647')" y todo.
        // Se limpia lo que no es texto; los smilies quedan como U+FFFC y los quita Kotlin
        // (gotcha 21).
        var limpio = '';
        if (msg) {
          var m = msg.cloneNode(true);
          m.querySelectorAll('script, style, noscript, iframe').forEach(function (x) { x.remove(); });
          deshacerCorreosCF(m);
          limpio = m.innerHTML;
        }
        AndroidShell.onThreadHead(JSON.stringify({
          ok: !!msg, tid: tid,
          title: (doc.title || '').trim(),
          author: auEl ? auEl.textContent.trim() : '',
          date: dm ? dm[0] : '',
          avatar: avatar,
          html: limpio
        }));
      })
      .catch(function (e) {
        AndroidShell.onThreadHead(JSON.stringify({ ok: false, tid: tid, error: String(e) }));
      });
  };

  // ── Escribir en la lista de ignorados de FC ────────────────────────────────
  // Hasta 2026-08-21 la app guardaba los ignorados SOLO en local, y como la sincronización
  // REEMPLAZA la lista con la de FC, todo lo añadido a mano desaparecía en la siguiente
  // importación (y lo borrado volvía). Ahora FC es la única verdad: se escribe allí y se
  // relee. Sondeado contra el servidor antes de escribir esto — los dos formularios viven
  // en la misma URL y se distinguen por su id.
  //
  // ALTA  (#ignorelist_add_form): copiar campos + `username`.
  // BAJA  (#ignorelist_change_form): FC NO tiene petición de baja. El form manda la lista
  //   ENTERA y quitar a alguien es enviarla SIN su `listbits[ignore][UID]` (el hidden
  //   `listbits[ignore_original][UID]` sí va). Por eso se monta sobre la lista RECIÉN
  //   traída: hacerlo sobre una copia rancia borraría ignorados que el usuario no tocó.
  //
  // El éxito NO se lee de la respuesta: si va bien FC redirige a la home y si va mal se
  // queda en la URL del POST (verificado). Se comprueba releyendo la lista, que además es
  // lo que se le devuelve a Kotlin para que guarde la verdad y no lo que suponemos.
  function ignoreListDoc() {
    return fetch('https://forocoches.com/foro/profile.php?do=ignorelist&_fp=' + Date.now(),
      { credentials: 'same-origin' }).then(function (r) { return r.text(); });
  }

  function ignoreRows(doc) {
    return [].slice.call(doc.querySelectorAll('[id]'))
      .filter(function (e) { return /^user\d+$/.test(e.id); })
      .map(function (e) {
        var a = e.querySelector('a[href*="member.php"]');
        return { uid: e.id.slice(4), nombre: a ? a.textContent.trim() : '' };
      });
  }

  function ignoreWrite(accion, usuario) {
    var objetivo = String(usuario || '').trim();
    function entrega(ok, html, error) {
      var doc = new DOMParser().parseFromString(html || '', 'text/html');
      var tok = doc.querySelector('input[name="securitytoken"]');
      AndroidShell.onIgnoreList(JSON.stringify({
        ok: ok, session: !!tok && tok.value !== 'guest', html: html || '',
        action: accion, target: objetivo, error: error || ''
      }));
    }
    ignoreListDoc()
      .then(function (h0) {
        var d0 = new DOMParser().parseFromString(h0, 'text/html');
        var params, f;
        if (accion === 'add') {
          f = d0.querySelector('#ignorelist_add_form');
          if (!f) return { err: 'no-form', html: h0 };
          params = copyFormFields(f, { username: objetivo }, []);
        } else {
          f = d0.querySelector('#ignorelist_change_form');
          if (!f) return { err: 'no-form', html: h0 };
          var fila = ignoreRows(d0).filter(function (u) {
            return u.nombre.toLowerCase() === objetivo.toLowerCase();
          })[0];
          // Ya no está en FC: no es un fallo, es que no hay nada que quitar.
          if (!fila) return { err: '', html: h0, saltar: true };
          params = copyFormFields(f, null, ['listbits[ignore][' + fila.uid + ']']);
        }
        return fetch('https://forocoches.com/foro/profile.php?do=updatelist&userlist=ignore', {
          method: 'POST', credentials: 'same-origin',
          headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
          body: params.toString()
        }).then(function (r) { return r.text(); }).then(function (post) { return { post: post }; });
      })
      .then(function (res) {
        if (res.err === 'no-form') { entrega(false, res.html, 'login'); return; }
        if (res.saltar) { entrega(true, res.html, ''); return; }
        // Verificación con evidencia: ¿quedó la lista como debía?
        return ignoreListDoc().then(function (h1) {
          var esta = ignoreRows(new DOMParser().parseFromString(h1, 'text/html')).some(function (u) {
            return u.nombre.toLowerCase() === objetivo.toLowerCase();
          });
          var ok = (accion === 'add') ? esta : !esta;
          var motivo = '';
          if (!ok) {
            // FC explica sus errores en #st-error-main-error-message ("El usuario 'x' no fue
            // encontrado en la base de datos. por favor regresa y…"). Nos quedamos con la
            // primera frase: el resto le habla a un navegador, no a una app.
            var pd = new DOMParser().parseFromString(res.post, 'text/html');
            var el = pd.querySelector('#st-error-main-error-message');
            var txt = el ? el.textContent.replace(/\s+/g, ' ').trim() : '';
            var punto = txt.indexOf('. ');
            motivo = punto > 20 ? txt.slice(0, punto + 1) : txt;
          }
          entrega(ok, h1, motivo);
        });
      })
      .catch(function (e) { entrega(false, '', String(e)); });
  }

  window.fcIgnoreAdd = function (usuario) { ignoreWrite('add', usuario); };
  window.fcIgnoreRemove = function (usuario) { ignoreWrite('remove', usuario); };

  // ── Login desde UI nativa (Fase 3) ─────────────────────────────────────────
  // Mismo principio que fcSubmitReply: traemos el form REAL de login de FC, copiamos
  // sus campos (securitytoken incluido) y solo rellenamos usuario y contraseña. El
  // POST sale same-origin del WebView, así que las cookies de sesión quedan puestas.
  // La credencial la teclea el usuario en la app y NUNCA sale hacia otro dominio.
  window.fcLogin = function (user, pass) {
    fetch('https://forocoches.com/foro/login.php', { credentials: 'same-origin' })
      .then(function (r) { return r.text(); })
      .then(function (html) {
        var doc = new DOMParser().parseFromString(html, 'text/html');
        if (/just a moment|attention required|un momento/i.test(doc.title || '')) {
          AndroidShell.onLoginResult(JSON.stringify({ posted: false, error: 'cloudflare' }));
          return null;
        }
        var f = null;
        doc.querySelectorAll('form').forEach(function (x) {
          if (!f && x.querySelector('input[name="vb_login_username"]')) f = x;
        });
        var params = new URLSearchParams();
        if (f) {
          f.querySelectorAll('input').forEach(function (el) {
            var nm = el.getAttribute('name'); if (!nm) return;
            var type = (el.getAttribute('type') || '').toLowerCase();
            if (type === 'submit' || type === 'file') return;
            params.set(nm, el.value || '');
          });
        }
        params.set('vb_login_username', user);
        params.set('vb_login_password', pass);
        // md5 vacío = vBulletin valida la contraseña en servidor (comportamiento estándar
        // cuando el JS del form no corre).
        params.set('vb_login_md5password', '');
        params.set('vb_login_md5password_utf', '');
        params.set('cookieuser', '1');
        params.set('do', 'login');
        // GOTCHA (medido por CDP el 2026-09-17, con sesión viva): `login.php` REDIRIGE a
        // `index.php` y NO trae formulario de login. Sin formulario no hay `securitytoken`, así
        // que se mandaba el literal 'guest' **junto a las cookies de la sesión actual** y
        // vBulletin tiraba el POST por token inválido — en silencio, sin página de error
        // reconocible. Ése era el "no me deja añadir otra cuenta": entrar desde fuera funciona
        // (no hay sesión → el formulario está ahí), añadir una segunda no.
        // La página que FC devuelve sí trae el token BUENO de esta sesión: se usa ése.
        if (!params.get('securitytoken')) {
          var tk = doc.querySelector('input[name="securitytoken"]');
          if (tk && tk.value) params.set('securitytoken', tk.value);
        }
        // 'guest' solo como último recurso: es el token que FC espera de un invitado de verdad.
        if (!params.has('securitytoken') || !params.get('securitytoken')) params.set('securitytoken', 'guest');
        if (!params.has('s')) params.set('s', '');
        return fetch('https://forocoches.com/foro/login.php?do=login', {
          method: 'POST', credentials: 'same-origin',
          headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
          body: params.toString()
        });
      })
      .then(function (r) {
        if (!r) return;
        return r.text().then(function (loginHtml) {
          // El HTML del menú es IDÉNTICO para invitados y logueados (esqueleto), así
          // que el veredicto de éxito lo da Kotlin mirando la cookie de sesión
          // (bbuserid, HttpOnly — visible para CookieManager, no para JS). Aquí solo
          // extraemos el posible mensaje de error del formulario.
          var err = '';
          var em = loginHtml.match(/<(?:div|li|p)[^>]*class="[^"]*(?:standard_error|error|blockrow)[^"]*"[^>]*>\s*([^<]{4,200})/i);
          if (em) err = em[1].replace(/\s+/g, ' ').trim();
          AndroidShell.onLoginResult(JSON.stringify({ posted: true, error: err }));
        });
      })
      .catch(function (e) { AndroidShell.onLoginResult(JSON.stringify({ posted: false, error: String(e) })); });
  };

  // Fecha REAL de un mensaje, para las citas/menciones: su lista solo trae la hora ("21:38")
  // aunque sea de hace tres días (medido el 2026-09-24). No hay forma ligera de pedirla:
  // showpost.php redirige al hilo entero. Se lee igual que en la vista de hilo — la cabecera
  // del post sin el cuerpo, para que una cita con fecha no la contamine. Kotlin la guarda para
  // siempre por pid, así que cada mensaje se pide una sola vez.
  window.fcNoticeDate = function (pid) {
    function fin(fecha) { AndroidShell.onNoticeDate(JSON.stringify({ pid: pid, fecha: fecha })); }
    fetch('https://forocoches.com/foro/showthread.php?p=' + pid, { credentials: 'same-origin' })
      .then(function (r) { return r.text(); })
      .then(function (html) {
        var doc = new DOMParser().parseFromString(html, 'text/html');
        var wrap = doc.getElementById('edit' + pid);
        if (!wrap) { fin(''); return; }
        var cab = wrap.cloneNode(true);
        var cuerpo = cab.querySelector('[id^="post_message_"]');
        if (cuerpo) cuerpo.remove();
        var dm = (cab.textContent || '').replace(/\s+/g, ' ')
          .match(/(Hoy|Ayer|\d{1,2}-[a-z]{3,4}-\d{2,4})[, ]*\d{1,2}:\d{2}/i);
        fin(dm ? dm[0] : '');
      })
      .catch(function () { fin(''); });
  };

  // ── Citas / Menciones aisladas (Bloque B) ──────────────────────────────────
  // Viven en member.php?u=N&tab=quotes|mentions dentro de div.quotes-mentions-wrapper
  // .quotes / .mentions como tabla tborder ("Sin resultados" si no hay).
  window.fcLoadNotices = function (url, kind) {
    fetch(url, { credentials: 'same-origin' })
      .then(function (r) {
        if (!r.ok) throw new Error('http ' + r.status);
        return r.text();
      })
      .then(function (html) {
        var doc = new DOMParser().parseFromString(html, 'text/html');
        if (/just a moment|attention required|un momento/i.test(doc.title || '')) {
          AndroidShell.onNotices(JSON.stringify({ kind: kind, error: 'cloudflare' }));
          return;
        }
        var cls = kind === 'quotes' ? 'quotes' : 'mentions';
        var wrap = doc.querySelector('.quotes-mentions-wrapper.' + cls);
        var items = [];
        if (wrap) {
          wrap.querySelectorAll('tr').forEach(function (tr) {
            var a = tr.querySelector('a[href*="showthread.php"]');
            if (!a) return; // fila "Sin resultados", o la del <separator> entre filas
            var href = '';
            try { href = new URL(a.getAttribute('href'), 'https://forocoches.com/foro/').href; } catch (e) { return; }
            var title = a.textContent.replace(/\s+/g, ' ').trim();
            var who = '';
            var uid = '';
            var m = tr.querySelector('a[href*="member.php"]');
            if (m) {
              who = m.textContent.replace(/\s+/g, ' ').trim();
              var mu = (m.getAttribute('href') || '').match(/[?&]u=(\d+)/);
              // Aquí el uid viene SIN enmascarar, al revés que en los hilos (gotcha 1).
              if (mu && mu[1] !== '0') uid = mu[1];
            }
            // El sello va al principio del primer <strong>: "14:22 - neonforger".
            var st = tr.querySelector('strong');
            var sello = st ? (st.textContent.split(' - ')[0] || '').replace(/\s+/g, ' ').trim() : '';
            var plano = tr.textContent.replace(/\s+/g, ' ');
            // El VERBO es lo ÚNICO que distingue una cita de una mención: la estructura de la
            // fila es idéntica (medido por CDP el 2026-09-21 sobre las dos pestañas).
            var tipo = /te cit\u00f3 en el tema/i.test(plano) ? 'cita'
                     : /te mencion\u00f3 en el tema/i.test(plano) ? 'mencion'
                     : (kind === 'quotes' ? 'cita' : 'mencion');
            // El extracto del mensaje. FC YA lo trunca con puntos suspensivos: no se recorta.
            var alt2 = tr.querySelector('.alt2');
            var extracto = alt2 ? alt2.textContent.replace(/\s+/g, ' ').trim() : '';
            items.push({
              url: href, title: title, who: who, uid: uid,
              sello: sello, tipo: tipo, extracto: extracto,
              // `text` se mantiene por compatibilidad con el camino viejo de la fila; el panel
              // nuevo no lo usa cuando hay `tipo`.
              text: plano.trim().slice(0, 200)
            });
          });
        }
        AndroidShell.onNotices(JSON.stringify({ kind: kind, items: items }));
      })
      .catch(function (e) { AndroidShell.onNotices(JSON.stringify({ kind: kind, error: String(e) })); });
  };

  // Quién es el usuario logueado: uid, nombre y avatar.
  //
  // El uid hace falta para separar los datos de cada cuenta, y `fcLoadProfile` no lo trae. Se
  // pide usercp.php porque ahí FC enlaza a member.php?u=<uid> SIN enmascarar (medido el
  // 2026-09-16; el gotcha 1 —todos los uid a u=0— vale para los perfiles de OTROS, no el tuyo).
  window.fcQuienSoy = function () {
    // Cache-buster obligatorio (gotcha VARNISH): esto se pregunta JUSTO después de entrar o de
    // cambiar de cuenta, que es cuando FC sirve la página privada de la sesión ANTERIOR desde
    // caché. Sin `_fp` se puede guardar el uid de la cuenta equivocada — y con él se guardan
    // también sus cookies en el cofre.
    fetch('https://forocoches.com/foro/usercp.php?_fp=' + Date.now(), { credentials: 'same-origin' })
      .then(function (r) { return r.text(); })
      .then(function (html) {
        var doc = new DOMParser().parseFromString(html, 'text/html');
        var uid = '';
        var nombre = '';
        var enlaces = doc.querySelectorAll('a[href*="member.php?u="]');
        for (var i = 0; i < enlaces.length && !uid; i++) {
          var m = (enlaces[i].getAttribute('href') || '').match(/[?&]u=(\d+)/);
          // u=0 es el enmascarado de FC: no identifica a nadie.
          if (m && m[1] !== '0') {
            uid = m[1];
            // El textContent del enlace arrastra los contadores del perfil ("300 Posts 17
            // Hilos"), asi que normalizar todos los espacios pega el nombre con ellos. Medido
            // por CDP el 2026-09-16: el nombre viene en su PROPIA linea, asi que se coge la
            // primera linea con texto. (firstChild no vale: es un nodo de texto vacio.)
            nombre = ((enlaces[i].textContent || '').split('\n')
              .map(function (s) { return s.trim(); })
              .filter(function (s) { return s; })[0] || '');
          }
        }
        var avatar = '';
        var av = doc.querySelector('img[src*="avatar"], img[src*="image.php"]');
        if (av) {
          try { avatar = new URL(av.getAttribute('src'), 'https://forocoches.com/foro/').href; }
          catch (e) {}
        }
        AndroidShell.onQuienSoy(JSON.stringify({ uid: uid, nombre: nombre, avatar: avatar }));
      })
      .catch(function (e) {
        AndroidShell.onQuienSoy(JSON.stringify({ uid: '', nombre: '', avatar: '', error: String(e) }));
      });
  };

  // ── Perfil + logout (Bloque B) ──────────────────────────────────────────────
  window.fcLoadProfile = function (url) {
    fetch(url, { credentials: 'same-origin' })
      .then(function (r) { return r.text(); })
      .then(function (html) {
        var doc = new DOMParser().parseFromString(html, 'text/html');
        var name = ((doc.title || '').match(/Ver Perfil:\s*(.+)$/i) || [])[1] || '';
        var avatar = '';
        var av = doc.querySelector('img[src*="avatar"], img[src*="image.php"]');
        if (av) {
          try { avatar = new URL(av.getAttribute('src'), 'https://forocoches.com/foro/').href; } catch (e) {}
        }
        // El link de logout (con su logouthash) viene en la propia página.
        var lo = '';
        var loA = doc.querySelector('a[href*="do=logout"]');
        if (loA) {
          try { lo = new URL(loA.getAttribute('href'), 'https://forocoches.com/foro/').href; } catch (e) {}
        }
        // La ficha propia es la misma member.php que la ajena: mismos datos, misma lectura.
        var ficha = leerFicha(doc, name);
        AndroidShell.onProfile(JSON.stringify({
          name: name.trim(), avatar: avatar, logout: lo,
          hilos: ficha.hilos, mensajes: ficha.mensajes, registro: ficha.registro, rango: ficha.rango,
          firma: ficha.firma, firmaTexto: ficha.firmaTexto, sobreMi: ficha.sobreMi
        }));
      })
      .catch(function (e) { AndroidShell.onProfile(JSON.stringify({ error: String(e) })); });
  };

  /** Cierra la sesión llamando al link real de logout (con logouthash). */
  window.fcLogout = function (logoutUrl) {
    fetch(logoutUrl, { credentials: 'same-origin' })
      .then(function (r) { return r.text(); })
      .then(function () { AndroidShell.onLogoutDone('ok'); })
      .catch(function (e) { AndroidShell.onLogoutDone(String(e)); });
  };

  // ── Smilies de FC (Bloque A: editor) ───────────────────────────────────────
  // La página getsmilies trae <img id="smilie_N" alt=":codigo:" title="nombre">.
  window.fcLoadSmilies = function () {
    fetch('https://forocoches.com/foro/misc.php?do=getsmilies&editorid=vB_Editor_001', { credentials: 'same-origin' })
      .then(function (r) { return r.text(); })
      .then(function (html) {
        var doc = new DOMParser().parseFromString(html, 'text/html');
        var list = [];
        doc.querySelectorAll('img[id^="smilie_"]').forEach(function (im) {
          var code = im.getAttribute('alt') || '';
          if (!code) return;
          var src = '';
          try { src = new URL(im.getAttribute('src'), 'https://forocoches.com/foro/').href; } catch (e) {}
          if (src) list.push({ code: code, src: src });
        });
        if (list.length) AndroidShell.onSmilies(JSON.stringify({ smilies: list }));
      })
      .catch(function (e) { /* sin smilies el editor sigue funcionando */ });
  };

  // API pública para la app: carga un listado por fetch same-origin y lo entrega parseado.
  // Sirve para forumdisplay, subscription y search (Mis hilos): la finalUrl tras
  // redirecciones permite paginar búsquedas (search.php?searchid=N&page=M).
  /**
   * El POPURRÍ: los hilos de varios subforos de una sentada.
   *
   * Se traen las páginas y se entregan **sin mezclar**, una lista por subforo. Mezclar y
   * ordenar es política, y la política vive en Kotlin (`Popurri`), como el resto del filtrado
   * de esta app. Aquí solo se extrae.
   *
   * Va en UNA sola llamada al puente aunque sean cinco peticiones: entregarlas de una en una
   * obligaría a repintar la lista cinco veces y el orden bailaría a la vista del usuario.
   *
   * Si un subforo falla, se entrega vacío y los demás siguen: perder Videojuegos no puede
   * dejarte sin Electrónica.
   */
  window.fcLoadPopurri = function (fidsCsv) {
    var fids = String(fidsCsv || '').split(',')
      .map(function (x) { return parseInt(x, 10); })
      .filter(function (x) { return x > 0; });
    if (!fids.length) { AndroidShell.onPopurri(JSON.stringify({ listas: [] })); return; }

    Promise.all(fids.map(function (fid) {
      var url = 'https://forocoches.com/foro/forumdisplay.php?f=' + fid + '&_fp=' + Date.now();
      return fetch(url, { credentials: 'same-origin' })
        .then(function (r) { return r.text(); })
        .then(function (html) {
          var doc = new DOMParser().parseFromString(html, 'text/html');
          if (/just a moment|attention required|un momento/i.test(doc.title || '')) {
            return { fid: fid, cloudflare: true, threads: [] };
          }
          return { fid: fid, threads: parseListDoc(doc), doc: doc };
        })
        .catch(function () { return { fid: fid, threads: [] }; });
    })).then(function (listas) {
      var cf = listas.some(function (l) { return l.cloudflare; });
      if (cf) { AndroidShell.onListError('cloudflare'); return; }
      // Aquí no hay UN doc: son varios subforos en paralelo. El menú es idéntico en todos
      // (es el chrome de FC), así que vale el primero que haya llegado entero — pero tiene
      // que salir de un doc TRAÍDO, no del DOM vivo (ver el comentario de menuLinks).
      var conDoc = listas.filter(function (l) { return l.doc; })[0];
      var menu = conDoc ? menuLinks(conDoc.doc) : {};
      // El doc de cada lista es de uso interno: pesa y no se serializa al puente.
      listas.forEach(function (l) { delete l.doc; });
      AndroidShell.onPopurri(JSON.stringify({
        listas: listas, menu: menu
      }));
    });
  };

  window.fcLoadThreadList = function (url) {
    fetch(url, { credentials: 'same-origin' })
      .then(function (r) {
        if (!r.ok) throw new Error('http ' + r.status);
        return r.text().then(function (html) { return { html: html, finalUrl: r.url || url }; });
      })
      .then(function (res) {
        var html = res.html;
        var doc = new DOMParser().parseFromString(html, 'text/html');
        // Challenge de Cloudflare: avisar a la app para que enseñe el WebView y lo resuelva
        // el usuario como en el navegador.
        if (/just a moment|attention required|un momento/i.test(doc.title || '')) {
          AndroidShell.onListError('cloudflare');
          return;
        }
        var payload = { url: url, finalUrl: res.finalUrl, menu: menuLinks(doc), counts: menuCounts(doc), threads: parseListDoc(doc) };
        // 0 hilos en un listado normal (forumdisplay) = probable cambio de HTML → canario.
        // Pero en subscription.php (Favoritos) una lista vacía es un estado LEGÍTIMO: el
        // usuario no tiene ningún hilo suscrito. Ahí entregamos el payload vacío (no error)
        // para que la app pinte "No tienes hilos en favoritos" en vez de "no se pudo cargar".
        var isFavs = (res.finalUrl || url).indexOf('subscription.php') !== -1;
        if (!payload.threads.length && !isFavs) {
          AndroidShell.onListError('empty');
          return;
        }
        AndroidShell.onThreadList(JSON.stringify(payload));
      })
      .catch(function (e) { AndroidShell.onListError(String(e)); });
  };

  // Buscador. Reusa la MISMA búsqueda de vBulletin que Mis hilos/Participados: POST a
  // search.php?do=process. OJO: FC exige sesión para buscar — de invitado el token es
  // 'guest', no existe el formulario y devuelve la home.
  //
  // DOS MODOS, y la diferencia es `showposts`:
  //   titleOnly  -> titleonly=1, showposts=0  → HILOS    (parseSearchDoc → onThreadList)
  //   !titleOnly -> titleonly=0, showposts=1  → MENSAJES (parseUserPosts → onNotices)
  //
  // Antes `showposts` estaba clavado a '0', así que "buscar en los mensajes" sí miraba dentro
  // del texto pero devolvía los HILOS que lo contienen. Lo reportó Green Floyd el 2026-09-18:
  // en la web salen los mensajes, del más reciente al más antiguo. Sondeado por CDP el
  // 2026-09-21 con `showposts=1`: FC añade `highlight=<consulta>` a cada enlace, así que el
  // selector de parseUserPosts vale tal cual, y llegan 100 anclas para 25 mensajes (gotcha 38,
  // de ahí que deduplicar por `p=` no sea opcional).
  window.fcSearch = function (query, titleOnly, pageUrl, usuario) {
    var porMensajes = !titleOnly;
    // Los dos modos pintan en PANTALLAS distintas, así que sus errores tienen que viajar por
    // el canal de su pantalla: por el de la lista o por el del panel de avisos.
    function fallo(motivo) {
      if (porMensajes) {
        AndroidShell.onNotices(JSON.stringify({ kind: 'searchposts', error: motivo }));
      } else {
        AndroidShell.onListError(motivo);
      }
    }
    function emit(doc, finalUrl) {
      if (/just a moment|attention required|un momento/i.test(doc.title || '')) {
        fallo('cloudflare'); return;
      }
      if (porMensajes) {
        AndroidShell.onNotices(JSON.stringify({
          kind: 'searchposts', url: finalUrl, items: parseUserPosts(doc, true)
        }));
        return;
      }
      var threads = parseSearchDoc(doc);
      if (!threads.length) { AndroidShell.onListError('empty'); return; }
      AndroidShell.onThreadList(JSON.stringify({
        url: finalUrl, finalUrl: finalUrl, menu: menuLinks(doc),
        counts: menuCounts(doc), threads: threads
      }));
    }
    if (pageUrl) {
      fetch(pageUrl, { credentials: 'same-origin' })
        .then(function (r) { return r.text().then(function (h) { return { h: h, u: r.url || pageUrl }; }); })
        .then(function (res) { emit(new DOMParser().parseFromString(res.h, 'text/html'), res.u); })
        .catch(function (e) { fallo(String(e)); });
      return;
    }
    fetch('https://forocoches.com/foro/search.php', { credentials: 'same-origin' })
      .then(function (r) { return r.text(); })
      .then(function (html) {
        var sdoc = new DOMParser().parseFromString(html, 'text/html');
        var tokEl = sdoc.querySelector('input[name="securitytoken"]');
        var token = tokEl ? tokEl.value : '';
        if (!token || token === 'guest') { fallo('login'); return null; }
        var body = new URLSearchParams();
        body.set('do', 'process'); body.set('securitytoken', token);
        body.set('query', query); body.set('titleonly', titleOnly ? '1' : '0');
        body.set('showposts', porMensajes ? '1' : '0'); body.set('dosearch', 'Buscar');
        // Por usuario, como el campo "Buscar por nombre de usuario" de FC (medido 2026-10-07).
        // exactname no viene en el formulario pero FC lo acepta, y es lo que usan los enlaces
        // de la ficha. Por títulos, starteronly=1 = los hilos que ABRIÓ; por mensajes, los suyos.
        if (usuario) {
          body.set('searchuser', usuario); body.set('exactname', '1');
          body.set('starteronly', titleOnly ? '1' : '0');
        }
        return fetch('https://forocoches.com/foro/search.php?do=process', {
          method: 'POST', credentials: 'same-origin',
          headers: { 'Content-Type': 'application/x-www-form-urlencoded' }, body: body.toString()
        }).then(function (r) { return r.text().then(function (h) { return { h: h, u: r.url }; }); });
      })
      .then(function (res) {
        if (!res) return;
        emit(new DOMParser().parseFromString(res.h, 'text/html'), res.u);
      })
      .catch(function (e) { fallo(String(e)); });
  };

  // Sugerencias de nombres para el campo de usuario del buscador: el MISMO autocompletado de la
  // web (vbulletin_ajax_namesugg.js → POST ajax.php?do=usersearch con `fragment`; contesta a
  // partir de 3 letras, con hasta 15 <user userid="N">nombre</user>). Pide el securitytoken de la
  // sesión: se saca de search.php y se reusa unos minutos, NUNCA del DOM vivo, que es la página
  // del arranque y puede ser de otra cuenta (gotcha 31). Si FC no contesta con nombres (token
  // caducado), se pide token nuevo y se reintenta una vez. Medido por CDP el 2026-10-07.
  var tokenSugerencias = { valor: '', cuando: 0 };
  function tokenParaSugerir(nuevo) {
    if (!nuevo && tokenSugerencias.valor && Date.now() - tokenSugerencias.cuando < 5 * 60 * 1000) {
      return Promise.resolve(tokenSugerencias.valor);
    }
    return fetch('https://forocoches.com/foro/search.php', { credentials: 'same-origin' })
      .then(function (r) { return r.text(); })
      .then(function (html) {
        var el = new DOMParser().parseFromString(html, 'text/html').querySelector('input[name="securitytoken"]');
        var t = el ? el.value : '';
        tokenSugerencias = { valor: (t && t !== 'guest') ? t : '', cuando: Date.now() };
        return tokenSugerencias.valor;
      });
  }

  window.fcSugerirUsuarios = function (fragmento) {
    var frag = String(fragmento || '').trim();
    function entregar(nombres) {
      AndroidShell.onUserSuggestions(JSON.stringify({ fragment: frag, nombres: nombres }));
    }
    function pedir(token) {
      if (!token) return Promise.resolve(null);
      var body = new URLSearchParams();
      body.set('securitytoken', token); body.set('do', 'usersearch'); body.set('fragment', frag);
      return fetch('https://forocoches.com/foro/ajax.php?do=usersearch', {
        method: 'POST', credentials: 'same-origin',
        headers: { 'Content-Type': 'application/x-www-form-urlencoded' }, body: body.toString()
      }).then(function (r) { return r.text(); })
        .then(function (xml) {
          if (xml.indexOf('<users') === -1) return null;
          var doc = new DOMParser().parseFromString(xml, 'text/xml');
          return Array.prototype.map.call(doc.getElementsByTagName('user'), function (u) {
            return (u.textContent || '').trim();
          }).filter(function (n) { return n; });
        });
    }
    tokenParaSugerir(false).then(pedir)
      .then(function (nombres) { return nombres || tokenParaSugerir(true).then(pedir); })
      .then(function (nombres) { entregar(nombres || []); })
      .catch(function () { entregar([]); });
  };

  // Mis hilos ('started') y Participados ('participated'). El UID real NO está en el DOM
  // vivo (FC lo enmascara a u=0), así que se resuelve del HTML recién traído. 'started'
  // usa la búsqueda finduser (hilos iniciados); 'participated' usa la búsqueda por nombre
  // de usuario con showposts=0 para que agrupe en HILOS y no en posts sueltos. Ambas
  // comparten el markup de resultados → parseSearchDoc. La finalUrl (searchid) permite paginar.
  /** Quién escribió el mensaje de esta fila de resultados. Ver parseUserPosts. */
  function autorDe(fila) {
    var anclas = fila.querySelectorAll('a[href*="showthread.php?p="]');
    for (var i = 0; i < anclas.length; i++) {
      var a = anclas[i];
      if (a.querySelector('span[style*="underline"]')) continue;   // es el título
      var conDiv = false;
      for (var j = 0; j < a.children.length; j++) {
        if (a.children[j].tagName === 'DIV') { conDiv = true; break; }
      }
      if (conDiv) continue;                                        // es el extracto
      var t = (a.textContent || '').replace(/\s+/g, ' ').trim();
      if (!t) continue;
      if (/^(Hoy|Ayer|\d{1,2}-[a-z]{3,4}-\d{2,4})[, ]*\d{1,2}:\d{2}$/i.test(t)) continue; // fecha
      return t;
    }
    return '';
  }

  /**
   * Los resultados de una búsqueda con `showposts=1`: los MENSAJES sueltos de alguien.
   *
   * Sondeado en producción el 2026-08-28. Dos cosas que hay que saber antes de tocar esto:
   *
   * 1. **Cada resultado trae CUATRO enlaces al mismo mensaje** (título, extracto, autor y
   *    fecha). Contar anclas da 100 por página cuando en realidad son **25**: sin quedarse
   *    con el primero de cada `p=`, la lista sale cuadruplicada.
   * 2. **No hay ni una `class` en todo el bloque**: es todo `style=` en línea con variables
   *    CSS. No hay de dónde agarrarse salvo la estructura y los `href`. Misma familia que el
   *    gotcha 20.
   *
   * La fila se acota como en los MPs (`rowOf`): el mayor ancestro que sigue conteniendo un
   * solo mensaje. Subir uno más se lleva los 25 de la página por delante.
   */
  /**
   * @param conAutor si viene, cada fila trae también QUIÉN lo escribió. En "sus mensajes" el
   *   autor es siempre el mismo y repetirlo es ruido, pero en una búsqueda por palabra cada
   *   fila es de alguien distinto y sin el nombre no se entiende nada.
   *
   *   El autor es una de las CUATRO anclas de la fila. Se identifica por **descarte
   *   estructural**, nunca por posición: no es la del título (lleva el span subrayado), ni la
   *   del extracto (lleva un <div> colgando), ni la de la fecha (casa el patrón de fecha).
   *   Fiarse del orden es el error que ya se pagó con los contadores del menú, donde el
   *   primero se daba por MP y una cita salía como "1 MP".
   */
  function parseUserPosts(doc, conAutor) {
    function pidDe(el) {
      var m = (el.getAttribute('href') || '').match(/[?&]p=(\d+)/);
      return m ? m[1] : '';
    }
    function filaDe(a, pid) {
      var fila = a;
      while (fila.parentElement) {
        var p = fila.parentElement;
        var ajena = false;
        var anclas = p.querySelectorAll('a[href*="showthread.php?p="]');
        for (var i = 0; i < anclas.length; i++) {
          if (pidDe(anclas[i]) !== pid) { ajena = true; break; }
        }
        if (ajena) break;
        fila = p;
      }
      return fila;
    }
    var vistos = {};
    var items = [];
    doc.querySelectorAll('a[href*="showthread.php?p="][href*="highlight"]').forEach(function (a) {
      var pid = pidDe(a);
      if (!pid || vistos[pid]) return;
      vistos[pid] = 1;
      var fila = filaDe(a, pid);
      // El título va en el ÚNICO span subrayado por estilo (el subforo usa <u>, no estilo).
      var t = fila.querySelector('span[style*="underline"]');
      var f = fila.querySelector('a[href*="forumdisplay.php?f="]');
      // El extracto es el div que cuelga directo de un enlace al mensaje.
      var e = fila.querySelector('a[href*="showthread.php?p="] > div');
      // Fecha: el MISMO patrón que el resto del motor (gotcha 14). Lo reciente no viene como
      // "21-ago-2026" sino como "Hoy 12:19" / "Ayer 20:56", así que un regex de solo fecha
      // larga deja sin hora justo a los mensajes de los últimos dos días — que son los que
      // más se miran. Se coge la ÚLTIMA coincidencia: la fecha va al final de la fila, y el
      // extracto podría llevar una hora escrita por el usuario.
      var titulo = t ? t.textContent.replace(/\s+/g, ' ').trim() : '';
      var foro = f ? f.textContent.replace(/[\s> ]+/g, ' ').trim() : '';
      // OJO: se normaliza el texto ANTES de buscar. FC parte la fecha en dos <span>
      // ("Hoy" en uno, "02:11" en otro), así que en el textContent CRUDO entre las dos
      // partes hay saltos de línea y tabuladores — y este patrón lleva `[, ]*`, que solo
      // acepta comas y espacios literales. Sin normalizar no casa NUNCA, y en silencio.
      var texto = fila.textContent.replace(/\s+/g, ' ');
      var fechas = texto.match(
        /(Hoy|Ayer|\d{1,2}-[a-z]{3,4}-\d{2,4})[, ]*\d{1,2}:\d{2}/gi);
      var fecha = fechas ? fechas[fechas.length - 1].trim() : '';
      var extracto = e ? e.textContent.replace(/\s+/g, ' ').trim() : '';
      var autor = conAutor ? autorDe(fila) : '';
      var url = '';
      try {
        url = new URL(a.getAttribute('href'), 'https://forocoches.com/foro/').href;
      } catch (ex) { return; }
      items.push({
        url: url,
        title: titulo,
        who: autor,                    // vacío salvo en la búsqueda: ver parseUserPosts
        text: [foro, fecha, extracto].filter(Boolean).join(' · ')
      });
    });
    return items;
  }

  /**
   * La actividad de CUALQUIER usuario, con las tres consultas que el propio FC enlaza desde
   * su ficha (sondeadas el 2026-08-28):
   *
   *   started  -> &starteronly=1   hilos que ha abierto
   *   threads  -> &showposts=0     hilos en los que ha participado
   *   posts    -> &showposts=1     sus mensajes, uno a uno
   *
   * **El GET funciona sin `securitytoken`**, así que no hace falta traerse `search.php` antes
   * solo para sacarlo (que es lo que hace `fcLoadOwnThreads`). Exige sesión: de invitado FC
   * devuelve la home sin dar error, y entonces no sale ningún resultado.
   *
   * Cada búsqueda crea una entrada en la base de datos de FC (de ahí el `searchid`), así que
   * esto se lanza SOLO cuando el usuario lo pide, nunca al abrir una ficha.
   */
  /**
   * @param tid si viene, los resultados se limitan a ESE hilo. Es lo que hace posible el
   *   "ver solo sus mensajes en este hilo" de los hilos tipo "respondo preguntas": FC acepta
   *   `searchthreadid` junto a `searchuser` y devuelve solo eso, paginado (verificado el
   *   2026-09-12: en `t=8076555` con `searchuser=Pak` devuelve **un** mensaje, el suyo).
   */
  window.fcLoadUserActivity = function (modo, usuario, pageUrl, tid) {
    var extra = modo === 'started' ? '&starteronly=1'
              : modo === 'posts' ? '&showposts=1' : '&showposts=0';
    if (tid) extra += '&searchthreadid=' + encodeURIComponent(tid);
    var url = pageUrl || ('https://forocoches.com/foro/search.php?do=process&exactname=1' +
                          '&searchuser=' + encodeURIComponent(usuario) + extra);
    fetch(url, { credentials: 'same-origin' })
      .then(function (r) {
        return r.text().then(function (h) { return { h: h, u: r.url || url }; });
      })
      .then(function (res) {
        var doc = new DOMParser().parseFromString(res.h, 'text/html');
        var cf = /just a moment|attention required|un momento/i.test(doc.title || '');
        if (modo === 'posts') {
          if (cf) {
            AndroidShell.onNotices(JSON.stringify({ kind: 'userposts', error: 'cloudflare' }));
            return;
          }
          AndroidShell.onNotices(JSON.stringify({
            kind: 'userposts', url: res.u, items: parseUserPosts(doc)
          }));
          return;
        }
        if (cf) { AndroidShell.onListError('cloudflare'); return; }
        var threads = parseSearchDoc(doc);
        if (!threads.length) { AndroidShell.onListError('empty'); return; }
        AndroidShell.onThreadList(JSON.stringify({
          url: res.u, finalUrl: res.u, menu: menuLinks(doc),
          counts: menuCounts(doc), threads: threads
        }));
      })
      .catch(function (e) {
        if (modo === 'posts') {
          AndroidShell.onNotices(JSON.stringify({ kind: 'userposts', error: String(e) }));
        } else {
          AndroidShell.onListError(String(e));
        }
      });
  };

  window.fcLoadOwnThreads = function (mode, pageUrl) {
    function emit(doc, finalUrl) {
      if (/just a moment|attention required|un momento/i.test(doc.title || '')) {
        AndroidShell.onListError('cloudflare'); return;
      }
      var threads = parseSearchDoc(doc);
      var payload = { url: finalUrl, finalUrl: finalUrl, menu: menuLinks(doc), counts: menuCounts(doc), threads: threads };
      if (!threads.length) { AndroidShell.onListError('empty'); return; }
      AndroidShell.onThreadList(JSON.stringify(payload));
    }
    // Paginación: la URL con searchid ya identifica la búsqueda; solo hace falta traerla.
    if (pageUrl) {
      fetch(pageUrl, { credentials: 'same-origin' })
        .then(function (r) { return r.text().then(function (h) { return { h: h, u: r.url || pageUrl }; }); })
        .then(function (res) { emit(new DOMParser().parseFromString(res.h, 'text/html'), res.u); })
        .catch(function (e) { AndroidShell.onListError(String(e)); });
      return;
    }
    // Página 1: resolver identidad (uid + usuario) y el token de búsqueda del HTML traído.
    fetch('https://forocoches.com/foro/search.php', { credentials: 'same-origin' })
      .then(function (r) { return r.text(); })
      .then(function (html) {
        var sdoc = new DOMParser().parseFromString(html, 'text/html');
        var id = ownIdentity(sdoc);
        var tokEl = sdoc.querySelector('input[name="securitytoken"]');
        var token = tokEl ? tokEl.value : '';
        if (mode === 'started' && id.uid) {
          // Hilos iniciados: finduser por UID (GET), formato de hilos garantizado.
          var gurl = 'https://forocoches.com/foro/search.php?do=finduser&u=' + id.uid + '&starteronly=1';
          return fetch(gurl, { credentials: 'same-origin' })
            .then(function (r) { return r.text().then(function (h) { return { h: h, u: r.url || gurl }; }); });
        }
        // Participados (o sin uid): búsqueda por nombre de usuario, agrupada en hilos.
        if (!id.user || !token) { AndroidShell.onListError('empty'); return null; }
        var body = new URLSearchParams();
        body.set('do', 'process'); body.set('securitytoken', token);
        body.set('query', ''); body.set('titleonly', '0');
        body.set('searchuser', id.user); body.set('exactname', '1');
        body.set('starteronly', mode === 'started' ? '1' : '0');
        body.set('showposts', '0'); body.set('dosearch', 'Buscar');
        return fetch('https://forocoches.com/foro/search.php?do=process', {
          method: 'POST', credentials: 'same-origin',
          headers: { 'Content-Type': 'application/x-www-form-urlencoded' }, body: body.toString()
        }).then(function (r) { return r.text().then(function (h) { return { h: h, u: r.url }; }); });
      })
      .then(function (res) {
        if (!res) return;
        emit(new DOMParser().parseFromString(res.h, 'text/html'), res.u);
      })
      .catch(function (e) { AndroidShell.onListError(String(e)); });
  };
})();
