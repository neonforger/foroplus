(function () {
  if (typeof Android === 'undefined') { return; }

  const ignoredSet = new Set(JSON.parse(Android.getIgnoredUsersJson()).map(u => u.toLowerCase()));
  const keywordsEnabled = Android.getKeywordFilterEnabled();
  const keywords = JSON.parse(Android.getKeywordsJson()).map(k => k.toLowerCase());
  const creatorRows = {}; // hilo(tid) -> fila, para ocultar al resolver el creador (viejo móvil)

  // Selectores: por defecto los actuales, sobrescribibles por config remota (window.FC_CONFIG)
  // para poder arreglar roturas si FC cambia su HTML sin publicar versión nueva.
  const FC_DEFAULTS = {
    newSkinMarker: '.menu-item, .user-notifications-count-wrapper, .header-container, .forocoches-logo, .user-profile-menu-container',
    threadLink: 'a[href*="showthread.php?t="]',
    oldThreadTitle: 'a[id^="thread_title_"]',
    forumAuthorOld: '[onclick*="member.php"]',
    ignoredPlaceholder: 'oculto porque',
    postAuthorOld: 'span.xsaid a[href*="member.php"]',
    postAuthorNew: '[id^="postmenu_"] a[href*="member.php"]',
    postContainerNew: 'div.postbit_wrapper'
  };
  const CFG = Object.assign({}, FC_DEFAULTS, (window.FC_CONFIG && typeof window.FC_CONFIG === 'object') ? window.FC_CONFIG : {});

  function isThreadPage() { return window.location.href.includes('showthread.php'); }

  // Skin antiguo (tabla clásica) vs nuevo. El nuevo trae el menú de cabecera con
  // .menu-item (y los wrappers de notificaciones) en TODAS las páginas; el viejo no.
  // (No vale mirar thread_title_ porque eso solo está en el listado, no en los hilos.)
  function isOldSkin() {
    return !document.querySelector(CFG.newSkinMarker);
  }


  // ── Thread list filtering ────────────────────────────────────────────────

  function threadIdOf(a) {
    const m = (a.getAttribute('href') || '').match(/[?&]t=(\d+)/);
    return m ? m[1] : null;
  }

  // IDs de hilo distintos dentro de un elemento.
  function threadIdsIn(el) {
    const ids = new Set();
    for (const a of el.querySelectorAll(CFG.threadLink)) {
      const id = threadIdOf(a);
      if (id) ids.add(id);
    }
    return ids;
  }

  // Contenedor más ajustado que envuelve UN solo hilo (sube mientras el padre no meta
  // otro hilo distinto). Evita aterrizar en un contenedor compartido y ocultar de más.
  function rowForAnchor(a, tid) {
    let el = a;
    while (el.parentElement && el.parentElement.tagName !== 'BODY') {
      const ids = threadIdsIn(el.parentElement);
      if (ids.size > 1 || (ids.size === 1 && !ids.has(tid))) break;
      el = el.parentElement;
    }
    return el;
  }

  function threadIdFromRow(row) {
    const a = row.querySelector(CFG.threadLink);
    return a ? threadIdOf(a) : null;
  }

  function getThreadRows() {
    // Viejo de escritorio (styleid 5): cada hilo es un <tr> con <a id="thread_title_*">.
    const titleAnchors = document.querySelectorAll(CFG.oldThreadTitle);
    if (titleAnchors.length) {
      const rows = [];
      for (const a of titleAnchors) { const tr = a.closest('tr'); if (tr) rows.push(tr); }
      return rows;
    }
    // Nuevo y viejo móvil (styleid 7): contenedor más ajustado por hilo.
    const seen = new Set();
    const rows = [];
    for (const a of document.querySelectorAll(CFG.threadLink)) {
      const tid = threadIdOf(a);
      if (!tid || seen.has(tid)) continue;
      seen.add(tid);
      rows.push(rowForAnchor(a, tid));
    }
    return rows;
  }

  function getAuthorFromRow(row) {
    // Skin antiguo: autor del hilo en span con onclick a member.php.
    if (isOldSkin()) {
      const o = row.querySelector(CFG.forumAuthorOld);
      return o ? o.textContent.trim() : null;
    }
    const spans = Array.from(row.querySelectorAll('span'));
    for (let i = 0; i < spans.length - 1; i++) {
      if (spans[i].textContent.trim() === '@') return spans[i + 1].textContent.trim();
    }
    for (const el of row.querySelectorAll('span, a')) {
      const text = el.textContent.trim();
      if (!text.startsWith('@')) continue;
      const withoutAt = text.slice(1);
      const dashIdx = withoutAt.indexOf(' - ');
      return dashIdx !== -1 ? withoutAt.slice(0, dashIdx).trim() : withoutAt.trim();
    }
    return null;
  }

  function getTitleFromRow(row) {
    const a = row.querySelector(CFG.threadLink);
    return a ? a.textContent.trim().toLowerCase() : '';
  }

  function filterThreads(rows) {
    for (const row of rows) {
      // FAIL-SAFE: nunca ocultar una "fila" que envuelva más de un hilo (si el selector de
      // filas se rompe y devuelve un contenedor compartido, NO borramos el listado entero).
      if (threadIdsIn(row).size > 1) continue;
      const author = getAuthorFromRow(row);
      if (author && ignoredSet.has(author.toLowerCase())) { row.style.display = 'none'; continue; }
      if (keywordsEnabled && keywords.length > 0) {
        const title = getTitleFromRow(row);
        if (keywords.find(k => title.includes(k))) { row.style.display = 'none'; continue; }
      }
      // Viejo móvil: el listado NO trae el creador (solo el último que postea). Lo pedimos
      // a la app (caché + descarga con corte temprano) y ocultamos cuando se resuelve.
      if (!author && ignoredSet.size > 0 && typeof Android.getCachedCreator === 'function') {
        const tid = threadIdFromRow(row);
        if (tid) {
          const cached = Android.getCachedCreator(tid);
          if (cached) {
            if (ignoredSet.has(cached.toLowerCase())) row.style.display = 'none';
          } else {
            creatorRows[tid] = row;
            Android.requestThreadCreator(tid);
          }
        }
      }
    }
  }

  // La app nos avisa del creador de un hilo (resuelto en 2º plano) -> ocultar si está ignorado.
  window.fcOnCreator = function(tid, creator) {
    if (creator && ignoredSet.has(creator.toLowerCase())) {
      const row = creatorRows[tid];
      if (row) row.style.display = 'none';
    }
  };

  // ── Post filtering (dentro de hilos) ────────────────────────────────────

  function hidePost(post) {
    var el = post.parentElement || post;
    el.style.display = 'none';
  }

  function filterPosts(root) {
    const scope = root || document;
    const old = isOldSkin();

    for (const bit of scope.querySelectorAll('li.postbit')) {
      // Contenedor del post completo (cabecera + mensaje).
      const container = old ? (bit.closest('ul') || bit)
                            : (bit.closest(CFG.postContainerNew) || bit);
      // FC colapsa los posts de ignorados con "...está oculto porque ... lista de
      // ignorados". Usamos "oculto porque" (específico del aviso; NO el del menú).
      if ((container.textContent || '').indexOf(CFG.ignoredPlaceholder) !== -1) {
        container.style.display = 'none';
        continue;
      }
      // Respaldo por autor (por si no estuviera colapsado).
      const a = old ? bit.querySelector(CFG.postAuthorOld)
                    : bit.querySelector(CFG.postAuthorNew);
      if (a && ignoredSet.has(a.textContent.trim().toLowerCase())) {
        container.style.display = 'none';
      }
    }

    // Citas a usuarios ignorados (skin nuevo): ocultar el post que las contiene.
    for (const quote of scope.querySelectorAll('div.quote')) {
      const b = quote.querySelector('b');
      if (!b || !ignoredSet.has(b.textContent.trim().toLowerCase())) continue;
      const wrapper = quote.closest(CFG.postContainerNew) || quote.closest('li.postbit');
      if (wrapper) wrapper.style.display = 'none';
    }
  }

  // ── Ocultar anuncios de FC (cajones OptiDigital + banner de afiliado tipo Amazon) ─
  // OptiDigital y FC pintan sus ads con reglas `!important` que se cargan async, así que
  // un CSS inyectado (misma especificidad, antes) o un display='' inline los pierde.
  // Solución que gana SIEMPRE: display:none inline con prioridad `important` vía JS,
  // reaplicado en cada mutación. FAIL-SAFE: nunca ocultamos algo que contenga hilos.
  function hideEl(el) {
    if (el && !el.querySelector('a[href*="showthread.php"]')) {
      el.style.setProperty('display', 'none', 'important');
    }
  }

  // Cajones de anuncio: el contenedor externo lleva data-optidigital-slot; el slot su id;
  // más la barra fija inferior. NO tocamos '.optidigital-autoinread' (la lista de hilos).
  function hideAdSlots() {
    for (const el of document.querySelectorAll(
      '[data-optidigital-slot],[id^="optidigital-adslot-"],.fixed_adslot,[id*="fixed_adslot"]'
    )) hideEl(el);
  }

  // Barra de avisos superior: es un hueco de PROMOS por diseño (Amazon, phonegest, lo que
  // toque cada semana), no estructura del foro. Enfoque estructural: se oculta SIEMPRE,
  // sin mirar el dominio — así no perseguimos anunciantes uno a uno.
  function hideAdNotices() {
    for (const box of document.querySelectorAll('.navbar_notice, #notices-wrapper')) {
      // Sin fail-safe: aunque la promo enlace a un hilo de FC, sigue siendo promo.
      (box.closest('.navbar_notice') || box).style.setProperty('display', 'none', 'important');
    }
    // Promos de afiliado de FC (banner Amazon, skin móvil y escritorio). FC las INYECTA
    // DENTRO del cuerpo de los posts (post_message_), así que se ocultan incondicionalmente:
    // 'fc-custom-promo-wrap' es un contenedor de publi por diseño, nunca texto del usuario.
    for (const promo of document.querySelectorAll('[class*="fc-custom-promo-wrap"]')) {
      promo.style.setProperty('display', 'none', 'important');
    }
  }

  function hideAds() { hideAdSlots(); hideAdNotices(); }

  // ── Canario: avisa si los selectores dejan de encontrar datos ───────────────
  // (datos presentes en la página pero parser devuelve 0 => probable cambio de HTML de FC)
  function runCanary() {
    try {
      if (typeof Android.reportCanary !== 'function') return;
      if (isThreadPage()) {
        const hasPosts = document.querySelector('li.postbit, li[id^="td_post_"]');
        const anyAuthor = document.querySelector(
          'li.postbit span.xsaid a[href*="member.php"], [id^="postmenu_"] a[href*="member.php"]'
        );
        if (hasPosts && !anyAuthor) {
          Android.reportCanary('hilo', 'hay posts pero no se detecta ningún autor (¿cambió el HTML del hilo?)');
        }
      } else {
        const links = document.querySelectorAll(CFG.threadLink).length;
        if (links >= 5 && getThreadRows().length === 0) {
          Android.reportCanary('subforo', 'hay enlaces a hilos pero 0 filas detectadas (¿cambió el HTML del listado?)');
        }
      }
    } catch (e) {}
  }

  // ── Inicialización ───────────────────────────────────────────────────────

  if (isThreadPage()) {
    filterPosts();
  } else {
    filterThreads(getThreadRows());
  }
  hideAds();
  setTimeout(hideAds, 1200); // los ads de FC cargan async; reintento tras asentar
  // Algunos banners (promos Amazon 'fc-custom-promo') se inyectan LAZY al hacer scroll,
  // después de que el observer/temporizador ya pasaron: barremos también al desplazar.
  var lastAdSweep = 0;
  window.addEventListener('scroll', function () {
    var now = Date.now();
    if (now - lastAdSweep > 400) { lastAdSweep = now; hideAds(); }
  }, { passive: true });
  setTimeout(runCanary, 1500); // tras asentar el render

  // Detectar navegación SPA (pushState) para re-filtrar al entrar en un hilo
  var lastUrl = location.href;
  var origPushState = history.pushState.bind(history);
  history.pushState = function() {
    origPushState.apply(history, arguments);
    var newUrl = location.href;
    if (newUrl !== lastUrl) {
      lastUrl = newUrl;
      if (isThreadPage()) setTimeout(filterPosts, 400);
    }
  };
  window.addEventListener('popstate', function() {
    if (isThreadPage()) setTimeout(filterPosts, 400);
  });

  const observer = new MutationObserver(function (mutations) {
    hideAds();
    if (isThreadPage()) {
      for (const mutation of mutations) {
        for (const node of mutation.addedNodes) {
          if (node.nodeType !== 1) continue;
          if (node.querySelector?.('div[id^="postmenu_"]')) filterPosts(node);
        }
      }
    } else {
      // ¿Se añadieron enlaces a hilos? Si es así, re-filtra el listado completo con la
      // lógica robusta (rowForAnchor), en vez de subir 4 padres a ciegas.
      let hasNewThreadLinks = false;
      for (const mutation of mutations) {
        for (const node of mutation.addedNodes) {
          if (node.nodeType !== 1) continue;
          if (node.matches?.(CFG.threadLink) ||
              node.querySelector?.(CFG.threadLink)) {
            hasNewThreadLinks = true;
          }
        }
      }
      if (hasNewThreadLinks) filterThreads(getThreadRows());
    }
  });

  observer.observe(document.body, { childList: true, subtree: true });
})();
