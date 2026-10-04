// Helpers compartidos por el oraculo (oracle.test.js) y el harness de mutaciones
// (mutations.test.js). Cargan fixtures en jsdom y ejecutan el content.js REAL del repo.
const fs = require('fs');
const path = require('path');
const { JSDOM } = require('jsdom');

const ROOT = path.resolve(__dirname, '..', '..');
const CONTENT_JS = fs.readFileSync(path.join(ROOT, 'app', 'src', 'main', 'assets', 'content.js'), 'utf8');
const FX = path.join(__dirname, 'fixtures');

function readFixture(file) { return fs.readFileSync(path.join(FX, file), 'utf8'); }

// Carga un DOM SIN ejecutar content.js (runScripts 'outside-only' => los <script> de FC
// no corren; solo lo que evaluemos nosotros).
function loadDom(html, url) { return new JSDOM(html, { url, runScripts: 'outside-only' }).window; }

// Ejecuta el content.js real en un window ya cargado.
function execContentJs(win, android) { win.Android = android; win.eval(CONTENT_JS); }

// Puente Android mockeado con inputs de test.
function makeAndroid({ ignored, keywords, keywordFilterEnabled, creators = {} }) {
  return {
    getIgnoredUsersJson: () => JSON.stringify(ignored),
    getKeywordFilterEnabled: () => !!keywordFilterEnabled,
    getKeywordsJson: () => JSON.stringify(keywords),
    getCachedCreator: (tid) => creators[tid] || '',
    requestThreadCreator: () => {},
    reportCanary: () => {},
  };
}

const isHidden = (el) => !!(el && el.style && el.style.display === 'none');
const isOldSkin = (doc) => !doc.querySelector('.menu-item, .user-notifications-count-wrapper');

const THREAD_LINK = 'a[href*="showthread.php?t="]';
function threadIdOf(a) { const m = (a.getAttribute('href') || '').match(/[?&]t=(\d+)/); return m ? m[1] : null; }
function threadIdsIn(el) {
  const ids = new Set();
  for (const a of el.querySelectorAll(THREAD_LINK)) { const id = threadIdOf(a); if (id) ids.add(id); }
  return ids;
}
function rowForAnchor(a, tid) {
  let el = a;
  while (el.parentElement && el.parentElement.tagName !== 'BODY') {
    const ids = threadIdsIn(el.parentElement);
    if (ids.size > 1 || (ids.size === 1 && !ids.has(tid))) break;
    el = el.parentElement;
  }
  return el;
}
function rowForThreadId(doc, tid) {
  for (const a of doc.querySelectorAll(THREAD_LINK)) {
    if (threadIdOf(a) === tid) return rowForAnchor(a, tid);
  }
  return null;
}

function postsOf(doc) {
  const old = isOldSkin(doc);
  const out = [];
  for (const bit of doc.querySelectorAll('li.postbit')) {
    const wrap = old ? (bit.closest('ul') || bit) : (bit.closest('div.postbit_wrapper') || bit);
    const a = old ? bit.querySelector('span.xsaid a[href*="member.php"]')
                  : bit.querySelector('[id^="postmenu_"] a[href*="member.php"]');
    out.push({ bit, wrap, author: a ? a.textContent.trim() : null });
  }
  return out;
}

// Conjunto de elementos con display:none INLINE (para medir lo que oculta content.js).
function inlineHiddenSet(doc) {
  const s = new Set();
  for (const el of doc.querySelectorAll('*')) {
    if (el.style && el.style.display === 'none') s.add(el);
  }
  return s;
}
// Elementos ocultos NUEVOS = los que no estaban ocultos antes de correr content.js.
function newlyHidden(beforeSet, afterSet) {
  return [...afterSet].filter((el) => !beforeSet.has(el));
}

module.exports = {
  ROOT, FX, CONTENT_JS, readFixture, loadDom, execContentJs, makeAndroid,
  isHidden, isOldSkin, THREAD_LINK, threadIdOf, threadIdsIn, rowForAnchor, rowForThreadId,
  postsOf, inlineHiddenSet, newlyHidden,
};
