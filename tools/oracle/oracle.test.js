// Oraculo / golden de regresion para el filtrado de content.js.
//
// Carga cada fixture congelado en jsdom, inyecta el puente Android (inputs fijos de test),
// EJECUTA EL content.js REAL del repo y comprueba que oculta exactamente lo esperado.
// Si alguien rompe el filtrado, este test falla en el mismo PR.
//
// No comprueba "drift" de FC (eso es el canario en el movil); aqui el HTML esta congelado.

const test = require('node:test');
const assert = require('node:assert');
const fs = require('fs');
const path = require('path');
const L = require('./lib.js');

const GOLDEN = JSON.parse(fs.readFileSync(path.join(__dirname, 'golden.json'), 'utf8'));
const android = L.makeAndroid({ ...GOLDEN.inputs, creators: GOLDEN.creators });

function run(file, url) {
  const win = L.loadDom(L.readFixture(file), url);
  L.execContentJs(win, android);
  return win;
}

// ====================== TESTS DE HILO ======================
for (const [file, kind] of Object.entries(GOLDEN.fixtures)) {
  if (kind !== 'thread') continue;
  test(`HILO ${file}: filtra posts de ignorados y respeta los demas`, () => {
    const doc = run(file, GOLDEN.thread.url).document;
    const posts = L.postsOf(doc);
    assert.ok(posts.length > 0, 'no se detecto ningun post (¿selector roto?)');

    const ign = new Set(GOLDEN.inputs.ignored.map((u) => u.toLowerCase()));

    // 1) Ningun post VISIBLE puede ser de un usuario ignorado.
    for (const p of posts) {
      if (!L.isHidden(p.wrap) && p.author && ign.has(p.author.toLowerCase())) {
        assert.fail(`post de ignorado "${p.author}" sigue visible`);
      }
    }
    // 2) El autor ignorado de prueba tiene posts y TODOS estan ocultos.
    const target = GOLDEN.thread.ignoredAuthorWithPosts.toLowerCase();
    const targetPosts = posts.filter((p) => p.author && p.author.toLowerCase() === target);
    assert.ok(targetPosts.length > 0, `no hay posts de ${GOLDEN.thread.ignoredAuthorWithPosts} en el fixture`);
    for (const p of targetPosts) assert.ok(L.isHidden(p.wrap), `un post de ${GOLDEN.thread.ignoredAuthorWithPosts} sigue visible`);

    // 3) FAIL-SAFE: no se ha blanqueado el hilo (queda algun post visible).
    assert.ok(posts.some((p) => !L.isHidden(p.wrap)), 'se ocultaron TODOS los posts (blanqueo)');

    // 4) Negativo: un autor normal sigue visible.
    const keep = GOLDEN.thread.mustStayVisibleAuthor.toLowerCase();
    const keepPosts = posts.filter((p) => p.author && p.author.toLowerCase() === keep);
    assert.ok(keepPosts.length > 0 && keepPosts.some((p) => !L.isHidden(p.wrap)),
      `el autor normal ${GOLDEN.thread.mustStayVisibleAuthor} deberia seguir visible`);

    // 5) Solo skin nuevo: el post que CITA a un ignorado se oculta.
    if (!L.isOldSkin(doc)) {
      const cited = GOLDEN.thread.quotedIgnoredUserNewSkinOnly.toLowerCase();
      let found = false;
      for (const q of doc.querySelectorAll('div.quote')) {
        const b = q.querySelector('b');
        if (b && b.textContent.trim().toLowerCase() === cited) {
          found = true;
          const wrap = q.closest('div.postbit_wrapper') || q.closest('li.postbit');
          assert.ok(L.isHidden(wrap), `el post que cita a ${GOLDEN.thread.quotedIgnoredUserNewSkinOnly} sigue visible`);
        }
      }
      assert.ok(found, `no se encontro cita a ${GOLDEN.thread.quotedIgnoredUserNewSkinOnly} (¿cambio el fixture?)`);
    }
  });
}

// ====================== TESTS DE LISTADO ======================
for (const [file, kind] of Object.entries(GOLDEN.fixtures)) {
  if (kind !== 'listing') continue;
  test(`LISTADO ${file}: oculta hilos de ignorados + keyword, respeta el resto`, () => {
    const doc = run(file, GOLDEN.listing.url).document;

    // 1) FAIL-SAFE: el listado no se ha blanqueado (hay filas visibles).
    let anyVisible = false;
    for (const tid of GOLDEN.listing.mustStayVisibleThreadIds) {
      const row = L.rowForThreadId(doc, tid);
      assert.ok(row, `no se encontro la fila del hilo ${tid} (¿selector roto?)`);
      if (!L.isHidden(row)) anyVisible = true;
    }
    assert.ok(anyVisible, 'se ocultaron filas que deberian seguir visibles (blanqueo)');

    // 2) Los hilos esperados (creador ignorado + keyword) estan ocultos.
    for (const tid of GOLDEN.listing.expectHiddenThreadIds) {
      const row = L.rowForThreadId(doc, tid);
      assert.ok(row, `no se encontro la fila del hilo ${tid}`);
      assert.ok(L.isHidden(row), `el hilo ${tid} deberia estar oculto y sigue visible`);
    }

    // 3) Negativos: los hilos normales siguen visibles.
    for (const tid of GOLDEN.listing.mustStayVisibleThreadIds) {
      const row = L.rowForThreadId(doc, tid);
      assert.ok(!L.isHidden(row), `el hilo normal ${tid} no deberia estar oculto`);
    }
  });
}
