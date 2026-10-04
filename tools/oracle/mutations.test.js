// Harness de MUTACIONES (substrato del futuro self-healing).
//
// Coge un fixture limpio, le aplica una mutacion realista que rompe un selector de
// content.js (como haria FC al cambiar su HTML) y comprueba dos cosas:
//   (a) la mutacion ROMPE de verdad el selector objetivo (es significativa), y
//   (b) el FAIL-SAFE aguanta: content.js degrada a casi no-op, nunca oculta MAS ni
//       blanquea la pagina.
//
// Cuando exista el healer, este mismo HTML mutado sera su entrada: "repara y que el
// oraculo vuelva a verde". De momento solo verificamos robustez.

const test = require('node:test');
const assert = require('node:assert');
const fs = require('fs');
const path = require('path');
const L = require('./lib.js');
const Heal = require('./healing.js');

const GOLDEN = JSON.parse(fs.readFileSync(path.join(__dirname, 'golden.json'), 'utf8'));
const android = L.makeAndroid({ ...GOLDEN.inputs, creators: GOLDEN.creators });

// REDUNDANCIA del marcador de skin: newSkinMarker tiene varias señales OR. Romper las dos
// originales (.menu-item + .user-notifications-count-wrapper) NO debe romper la deteccion de
// skin (las demas señales lo cazan) -> el filtrado por autor del skin nuevo se conserva.
test('REDUNDANCIA newSkinMarker: romper las 2 señales originales no rompe el skin nuevo', () => {
  const clean = L.readFixture('thread_new.html');
  const win = L.loadDom(clean, 'about:blank');
  for (const el of win.document.querySelectorAll('.menu-item')) el.classList.replace('menu-item', 'menu-x');
  for (const el of win.document.querySelectorAll('.user-notifications-count-wrapper')) el.classList.replace('user-notifications-count-wrapper', 'unc-x');
  const broken = win.document.documentElement.outerHTML;

  const a = Heal.behavior(clean, GOLDEN.thread.url, android, 'div.postbit_wrapper', null);
  const b = Heal.behavior(broken, GOLDEN.thread.url, android, 'div.postbit_wrapper', null);
  assert.ok(a.size > 0, 'precondicion: en limpio se oculta algo');
  assert.ok(Heal.setEq(a, b), `romper las 2 señales originales cambio el comportamiento: limpio ${[...a]} vs roto ${[...b]}`);
});

// Cambia el tag de un elemento conservando atributos e hijos (FC podria cambiar <li> por <div>).
function renameTag(el, tag) {
  const doc = el.ownerDocument;
  const n = doc.createElement(tag);
  for (const a of el.attributes) n.setAttribute(a.name, a.value);
  while (el.firstChild) n.appendChild(el.firstChild);
  el.replaceWith(n);
  return n;
}

// Mutaciones realistas. countAnchor = selector estable (que la mutacion NO destruye) para
// medir "unidades de contenido" visibles. surface = thread|listing.
const MUTATIONS = [
  {
    name: 'renombrar clase .xsaid (autor, hilo viejo)',
    fixture: 'thread_old.html', url: GOLDEN.thread.url, surface: 'thread',
    target: 'span.xsaid', countAnchor: 'li.postbit',
    apply(doc) { for (const el of doc.querySelectorAll('span.xsaid')) el.className = el.className.replace('xsaid', 'user-said'); },
  },
  {
    name: 'cambiar prefijo id postmenu_ -> post-menu- (autor, hilo nuevo)',
    fixture: 'thread_new.html', url: GOLDEN.thread.url, surface: 'thread',
    target: '[id^="postmenu_"]', countAnchor: '[id^="post_message_"]',
    apply(doc) { for (const el of doc.querySelectorAll('[id^="postmenu_"]')) el.id = el.id.replace('postmenu_', 'post-menu-'); },
  },
  {
    name: 'cambiar tag li.postbit -> div (contenedor de post, hilo nuevo)',
    fixture: 'thread_new.html', url: GOLDEN.thread.url, surface: 'thread',
    target: 'li.postbit', countAnchor: '[id^="post_message_"]',
    apply(doc) { for (const li of [...doc.querySelectorAll('li.postbit')]) renameTag(li, 'div'); },
  },
  {
    name: 'cambiar esquema de URL de hilo (showthread.php?t= -> threads/, listado nuevo)',
    fixture: 'listing_new.html', url: GOLDEN.listing.url, surface: 'listing',
    target: L.THREAD_LINK, countAnchor: null, expectNoop: true,
    apply(doc) {
      for (const a of doc.querySelectorAll(L.THREAD_LINK)) {
        const h = a.getAttribute('href') || '';
        a.setAttribute('href', h.replace(/showthread\.php\?t=(\d+)/, 'threads/$1'));
      }
    },
  },
];

// Cuenta unidades de contenido VISIBLES (ninguno de sus ancestros oculto).
function visibleCount(doc, anchorSel) {
  let n = 0;
  for (const a of doc.querySelectorAll(anchorSel)) {
    let p = a, vis = true;
    while (p) { if (L.isHidden(p)) { vis = false; break; } p = p.parentElement; }
    if (vis) n++;
  }
  return n;
}

for (const m of MUTATIONS) {
  test(`MUT ${m.name}: rompe el selector y el fail-safe aguanta`, () => {
    // --- Run en LIMPIO (referencia de cuanto oculta content.js normalmente) ---
    const cleanWin = L.loadDom(L.readFixture(m.fixture), m.url);
    const cleanDoc = cleanWin.document;
    const beforeMatch = cleanDoc.querySelectorAll(m.target).length;
    assert.ok(beforeMatch > 0, `precondicion: ${m.target} deberia matchear en limpio`);
    const preClean = L.inlineHiddenSet(cleanDoc);
    L.execContentJs(cleanWin, android);
    const hiddenClean = L.newlyHidden(preClean, L.inlineHiddenSet(cleanDoc));

    // --- Run con MUTACION ---
    const mutWin = L.loadDom(L.readFixture(m.fixture), m.url);
    const mutDoc = mutWin.document;
    m.apply(mutDoc);
    const afterMatch = mutDoc.querySelectorAll(m.target).length;
    assert.ok(afterMatch < beforeMatch,
      `la mutacion deberia romper ${m.target} (antes ${beforeMatch}, despues ${afterMatch})`);
    const preMut = L.inlineHiddenSet(mutDoc);
    L.execContentJs(mutWin, android);
    const hiddenMut = L.newlyHidden(preMut, L.inlineHiddenSet(mutDoc));

    // FAIL-SAFE 1: tras romperse, NO oculta mas que en limpio (degrada, no amplifica).
    assert.ok(hiddenMut.length <= hiddenClean.length,
      `oculto MAS tras romperse (${hiddenMut.length} > ${hiddenClean.length}) = posible blanqueo`);

    // FAIL-SAFE 2: ningun elemento oculto es un contenedor "gordo" (varios posts/hilos).
    // Se cuenta con UN solo ancla por unidad (countAnchor en hilo, ids de hilo en listado);
    // combinar [id^=post_message_]+li.postbit contaria 2 por post (ambos viven en el mismo).
    for (const el of hiddenMut) {
      if (m.surface === 'thread') {
        const units = el.querySelectorAll(m.countAnchor || 'li.postbit').length;
        assert.ok(units <= 1, 'se oculto un contenedor con >1 post (blanqueo)');
      } else {
        assert.ok(L.threadIdsIn(el).size <= 1, 'se oculto un contenedor con >1 hilo (blanqueo)');
      }
    }

    // FAIL-SAFE 3: queda contenido visible (no blanqueo total).
    if (m.expectNoop) {
      assert.strictEqual(hiddenMut.length, 0, 'con el selector de hilos roto no deberia ocultar nada');
    } else if (m.countAnchor) {
      assert.ok(visibleCount(mutDoc, m.countAnchor) > 0, 'no queda contenido visible (blanqueo total)');
    }
  });
}
