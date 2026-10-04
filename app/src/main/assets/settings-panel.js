(function () {
  if (typeof Android === 'undefined') return;
  if (document.getElementById('fc-settings-overlay')) return;

  var ignoredUsers = JSON.parse(Android.getIgnoredUsersJson());
  var lastUpdated = Android.getLastUpdatedMs();
  var kwEnabled = Android.getKeywordFilterEnabled();
  var keywords = JSON.parse(Android.getKeywordsJson());
  // Estado actual del diseño. Lo más fiable: el menú de FC muestra "Versión nueva"
  // (setNewDesign) cuando estás en el antiguo, y "Versión antigua" (setOldDesign) cuando
  // estás en el nuevo. Fallback: el global styleid (antiguo = 5/7, nuevo = 8/9).
  var isOldDesign = false;
  try {
    if (document.querySelector('[onclick*="setNewDesign"]')) isOldDesign = true;
    else if (document.querySelector('[onclick*="setOldDesign"]')) isOldDesign = false;
    else if (typeof styleid !== 'undefined') isOldDesign = (styleid == 5 || styleid == 7);
  } catch (e) {}

  function timeAgo(ms) {
    if (!ms) return 'nunca';
    var diff = Date.now() - ms;
    var minutes = Math.floor(diff / 60000);
    var hours = Math.floor(diff / 3600000);
    if (minutes < 1) return 'hace <1 min';
    if (hours < 1) return 'hace ' + minutes + ' min';
    if (hours === 1) return 'hace 1 h';
    return 'hace ' + hours + ' h';
  }

  function escAttr(s) {
    return s.replace(/&/g,'&amp;').replace(/"/g,'&quot;').replace(/</g,'&lt;');
  }

  function escJs(s) {
    return s.replace(/\\/g,'\\\\').replace(/'/g,"\\'");
  }

  function buildUserRows(users) {
    if (users.length === 0)
      return '<div style="color:#555;font-size:12px;padding:6px 0;">Sin usuarios ignorados</div>';
    return users.map(function(u) {
      return '<div style="display:flex;justify-content:space-between;align-items:center;' +
             'padding:5px 0;border-bottom:1px solid #222;" data-user="' + escAttr(u) + '">' +
             '<span style="color:#bbb;font-size:13px;">@' + escAttr(u) + '</span>' +
             '<button onclick="fcRemoveUser(\'' + escJs(u) + '\')" style="' +
             'background:none;border:none;color:#ff5555;cursor:pointer;font-size:13px;padding:2px 6px;">✕</button>' +
             '</div>';
    }).join('');
  }

  function buildKeywordRows(kws) {
    if (kws.length === 0)
      return '<div style="color:#555;font-size:12px;padding:6px 0;">Sin palabras clave</div>';
    return kws.map(function(k) {
      return '<div style="display:flex;justify-content:space-between;align-items:center;' +
             'padding:4px 0;border-bottom:1px solid #222;">' +
             '<span style="color:#bbb;font-size:12px;">' + escAttr(k) + '</span>' +
             '<button onclick="fcRemoveKeyword(\'' + escJs(k) + '\')" style="' +
             'background:none;border:none;color:#ff5555;cursor:pointer;font-size:13px;padding:2px 6px;">✕</button>' +
             '</div>';
    }).join('');
  }

  function toggle(id) {
    var el = document.getElementById(id);
    if (!el) return;
    el.style.display = el.style.display === 'none' ? 'block' : 'none';
  }

  function rowStyle() {
    return 'display:flex;justify-content:space-between;align-items:center;padding:9px 0;border-bottom:1px solid #222;';
  }

  function manageBtn(sectionId) {
    return '<button onclick="fcToggleSection(\'' + sectionId + '\')" style="' +
           'background:none;border:1px solid #444;color:#888;border-radius:4px;' +
           'padding:2px 8px;font-size:11px;cursor:pointer;">Gestionar</button>';
  }

  function toggleSwitch(inputId, trackId, thumbId, checked) {
    var bg = checked ? '#00e5cc' : '#555';
    var left = checked ? '21px' : '3px';
    return '<label style="position:relative;width:40px;height:22px;flex-shrink:0;cursor:pointer;">' +
           '<input type="checkbox" id="' + inputId + '" ' + (checked ? 'checked' : '') +
           ' style="opacity:0;width:0;height:0;position:absolute;">' +
           '<span id="' + trackId + '" style="position:absolute;inset:0;background:' + bg +
           ';border-radius:22px;transition:background 0.2s;">' +
           '<span id="' + thumbId + '" style="position:absolute;width:16px;height:16px;left:' + left +
           ';bottom:3px;background:#fff;border-radius:50%;transition:left 0.2s;"></span>' +
           '</span></label>';
  }

  // Overlay (transparente, solo para cerrar al tocar fuera)
  var overlay = document.createElement('div');
  overlay.id = 'fc-settings-overlay';
  overlay.style.cssText = 'display:none;position:fixed;inset:0;z-index:9998;';
  overlay.addEventListener('click', function(e) { if (e.target === overlay) closePanel(); });

  // Contenedor popover (panel + flecha)
  var container = document.createElement('div');
  container.id = 'fc-settings-container';
  container.style.cssText = 'display:none;position:fixed;bottom:82px;right:8px;z-index:9999;';

  // Panel
  var panel = document.createElement('div');
  panel.id = 'fc-settings-panel';
  panel.style.cssText = 'width:290px;background:#1a1a1a;border:1px solid #2a2a2a;' +
    'border-radius:12px;padding:4px 14px 8px;' +
    'font-family:sans-serif;font-size:14px;color:#e0e0e0;' +
    'max-height:70vh;overflow-y:auto;' +
    'box-shadow:0 4px 24px rgba(0,0,0,0.7);';

  // Flecha apuntando al FAB
  var arrow = document.createElement('div');
  arrow.style.cssText = 'width:0;height:0;' +
    'border-left:9px solid transparent;border-right:9px solid transparent;' +
    'border-top:9px solid #2a2a2a;margin-left:auto;margin-right:18px;';

  // — Fila: ignorados
  var ignoredRow =
    '<div style="' + rowStyle() + '">' +
    '<span style="color:#888;font-size:13px;">Ignorados' +
    '<span id="fc-ignored-count" style="color:#555;margin-left:6px;">' + ignoredUsers.length + '</span></span>' +
    '<div style="display:flex;gap:6px;align-items:center;">' +
    '<button id="fc-refresh-btn" onclick="fcRefresh()" style="background:none;border:none;color:#555;cursor:pointer;font-size:14px;padding:2px;">↻</button>' +
    manageBtn('fc-ignored-section') +
    '</div></div>' +
    '<div id="fc-ignored-section" style="display:none;padding-bottom:8px;">' +
    '<div id="fc-ignored-list">' + buildUserRows(ignoredUsers) + '</div>' +
    '<div style="color:#444;font-size:10px;margin-top:6px;">Lista sync: ' + timeAgo(lastUpdated) + '</div>' +
    '</div>';

  // — Fila: filtro político
  var kwRow =
    '<div style="' + rowStyle() + '">' +
    '<span style="color:#888;font-size:13px;">Filtro político' +
    '<span id="fc-kw-count" style="color:#555;margin-left:6px;">' + keywords.length + ' palabras</span></span>' +
    '<div style="display:flex;gap:8px;align-items:center;">' +
    toggleSwitch('fc-kw-toggle', 'fc-kw-track', 'fc-kw-thumb', kwEnabled) +
    manageBtn('fc-kw-section') +
    '</div></div>' +
    '<div id="fc-kw-section" style="display:none;padding-bottom:8px;">' +
    '<div id="fc-kw-list" style="max-height:140px;overflow-y:auto;">' + buildKeywordRows(keywords) + '</div>' +
    '<div style="display:flex;gap:4px;margin-top:6px;">' +
    '<input id="fc-kw-input" type="text" placeholder="Añadir palabra..." style="' +
    'flex:1;background:#222;border:1px solid #333;color:#e0e0e0;border-radius:4px;padding:4px 8px;font-size:12px;">' +
    '<button onclick="fcAddKeyword()" style="background:none;border:1px solid #00e5cc;color:#00e5cc;' +
    'border-radius:4px;padding:2px 10px;font-size:13px;cursor:pointer;">+</button>' +
    '</div>' +
    '<button onclick="fcResetKeywords()" style="width:100%;margin-top:5px;background:none;border:1px solid #2a2a2a;' +
    'color:#555;border-radius:4px;padding:3px;font-size:11px;cursor:pointer;">↺ Restablecer predeterminados</button>' +
    '</div>';

  // — Fila: diseño antiguo / nuevo (llama a las funciones de la propia FC)
  var designRow =
    '<div style="' + rowStyle() + '">' +
    '<span style="color:#888;font-size:13px;">Diseño antiguo</span>' +
    '<div style="display:flex;gap:8px;align-items:center;">' +
    toggleSwitch('fc-design-toggle', 'fc-design-track', 'fc-design-thumb', isOldDesign) +
    '</div></div>';

  panel.innerHTML = ignoredRow + kwRow + designRow;

  (function() {
    var d = panel.querySelector('#fc-design-toggle');
    if (!d) return;
    d.addEventListener('change', function() {
      var on = this.checked;
      // Feedback inmediato: mover la bola y colorear (igual que los otros toggles).
      var track = panel.querySelector('#fc-design-track');
      var thumb = panel.querySelector('#fc-design-thumb');
      if (track) track.style.background = on ? '#00e5cc' : '#555';
      if (thumb) thumb.style.left = on ? '21px' : '3px';
      // Evitar rebote/doble click durante la recarga.
      this.disabled = true;
      // OPCIÓN A: hacemos el POST DENTRO del WebView (fetch con credentials:'include'),
      // así la sesión (incluida la rotación de bbsessionhash) la gestiona el propio
      // WebView y no se desincroniza. Al terminar, recargamos nosotros (reload fiable).
      //
      // El newstyleset correcto NO es fijo (desde el viejo, "nuevo" = 8; desde el nuevo,
      // "viejo" = 7). FC define en cada página la función de "cambiar al otro diseño"
      // (setNewDesign si estás en viejo, setOldDesign si estás en nuevo) con el valor
      // correcto. Lo leemos de ahí en vez de hardcodear.
      var styleset = null;
      try {
        var fn = (typeof setNewDesign === 'function') ? setNewDesign
               : (typeof setOldDesign === 'function') ? setOldDesign : null;
        if (fn) { var fm = fn.toString().match(/newstyleset:\s*["'](\d+)["']/); if (fm) styleset = fm[1]; }
      } catch (e) {}
      if (!styleset) styleset = on ? '7' : '9'; // fallback
      var tok = '';
      try { if (typeof SECURITYTOKEN !== 'undefined' && SECURITYTOKEN) tok = SECURITYTOKEN; } catch (e) {}
      if (!tok) { var ti = document.querySelector('input[name="securitytoken"]'); if (ti) tok = ti.value; }
      var done = function() { if (Android.reloadPage) Android.reloadPage(); };
      fetch('/foro/profile.php?do=updatestyleid', {
        method: 'POST',
        headers: { 'Content-Type': 'application/x-www-form-urlencoded', 'X-Requested-With': 'XMLHttpRequest' },
        body: 'newstyleset=' + styleset + '&securitytoken=' + encodeURIComponent(tok) + '&s=&do=updatestyleid',
        credentials: 'include'
      }).then(done).catch(done);
    });
  })();

  container.appendChild(panel);
  container.appendChild(arrow);
  document.body.appendChild(overlay);
  document.body.appendChild(container);

  function openPanel() { overlay.style.display = 'block'; container.style.display = 'block'; }
  function closePanel() { overlay.style.display = 'none'; container.style.display = 'none'; }

  window.fcToggleSettings = function() {
    if (container.style.display === 'none') openPanel(); else closePanel();
  };

  window.fcToggleSection = function(id) { toggle(id); };

  window.fcRemoveUser = function(username) {
    Android.removeIgnoredUser(username);
    var el = panel.querySelector('[data-user="' + escAttr(username) + '"]');
    if (el) el.remove();
    var remaining = panel.querySelectorAll('[data-user]').length;
    if (remaining === 0)
      document.getElementById('fc-ignored-list').innerHTML =
        '<div style="color:#555;font-size:12px;padding:6px 0;">Sin usuarios ignorados</div>';
    var cnt = document.getElementById('fc-ignored-count');
    if (cnt) cnt.textContent = remaining;
  };

  window.fcRefresh = function() {
    var btn = document.getElementById('fc-refresh-btn');
    if (btn) { btn.textContent = '⏳'; btn.disabled = true; }
    window.fcOnRefreshDone = function() {
      window.fcOnRefreshDone = null;
      var users = JSON.parse(Android.getIgnoredUsersJson());
      var list = document.getElementById('fc-ignored-list');
      if (list) list.innerHTML = buildUserRows(users);
      var cnt = document.getElementById('fc-ignored-count');
      if (cnt) cnt.textContent = users.length;
      if (btn) { btn.textContent = '↻'; btn.disabled = false; }
    };
    Android.triggerRefresh();
  };

  panel.querySelector('#fc-kw-toggle').addEventListener('change', function() {
    Android.setKeywordFilterEnabled(this.checked);
    panel.querySelector('#fc-kw-track').style.background = this.checked ? '#00e5cc' : '#555';
    panel.querySelector('#fc-kw-thumb').style.left = this.checked ? '21px' : '3px';
  });

  window.fcRemoveKeyword = function(keyword) {
    Android.removeKeyword(keyword);
    var kws = JSON.parse(Android.getKeywordsJson());
    var list = document.getElementById('fc-kw-list');
    if (list) list.innerHTML = buildKeywordRows(kws);
    var cnt = document.getElementById('fc-kw-count');
    if (cnt) cnt.textContent = kws.length + ' palabras';
  };

  window.fcAddKeyword = function() {
    var input = document.getElementById('fc-kw-input');
    if (!input || !input.value.trim()) return;
    Android.addKeyword(input.value.trim());
    var kws = JSON.parse(Android.getKeywordsJson());
    var list = document.getElementById('fc-kw-list');
    if (list) list.innerHTML = buildKeywordRows(kws);
    var cnt = document.getElementById('fc-kw-count');
    if (cnt) cnt.textContent = kws.length + ' palabras';
    input.value = '';
  };

  window.fcResetKeywords = function() {
    Android.resetKeywordsToDefaults();
    var kws = JSON.parse(Android.getKeywordsJson());
    var list = document.getElementById('fc-kw-list');
    if (list) list.innerHTML = buildKeywordRows(kws);
    var cnt = document.getElementById('fc-kw-count');
    if (cnt) cnt.textContent = kws.length + ' palabras';
  };

  // FAB
  var fab = document.createElement('button');
  fab.id = 'fc-gear-btn';
  fab.onclick = window.fcToggleSettings;
  fab.style.cssText = 'position:fixed;bottom:24px;right:16px;z-index:9997;' +
    'width:48px;height:48px;border-radius:50%;background:#00e5cc;border:none;' +
    'color:#111;font-size:22px;cursor:pointer;box-shadow:0 2px 8px rgba(0,0,0,0.5);' +
    'display:flex;align-items:center;justify-content:center;';
  fab.textContent = '⚙';
  document.body.appendChild(fab);
})();
