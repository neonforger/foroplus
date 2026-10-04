// SELF-HEALING: matriz mutacion x selector + caso "no curar".
//
// Por cada (selector, mutacion realista que lo rompe):
//   1. precondicion: el selector por defecto funciona en limpio (scope-aware).
//   2. la mutacion ROMPE de verdad el comportamiento (mutante != limpio).
//   3. el healer cura y la cura es BEHAVIORALMENTE EQUIVALENTE al limpio (oculta lo mismo).
// Y un caso en el que NO se debe curar (autor eliminado) -> el healer se rinde, no inventa.

const test = require('node:test');
const assert = require('node:assert');
const fs = require('fs');
const path = require('path');
const L = require('./lib.js');
const Heal = require('./healing.js');

const GOLDEN = JSON.parse(fs.readFileSync(path.join(__dirname, 'golden.json'), 'utf8'));
const android = L.makeAndroid({ ...GOLDEN.inputs, creators: GOLDEN.creators });

// --- Selectores objetivo (key = clave en CFG; scope/anchor = como los usa content.js) ---
const T_AUTHOR_NEW = {
  key: 'postAuthorNew', fixture: 'thread_new.html', url: GOLDEN.thread.url,
  scope: 'li.postbit', default: '[id^="postmenu_"] a[href*="member.php"]', anchor: 'div.postbit_wrapper',
};
const T_AUTHOR_OLD = {
  key: 'postAuthorOld', fixture: 'thread_old.html', url: GOLDEN.thread.url,
  scope: 'li.postbit', default: 'span.xsaid a[href*="member.php"]', anchor: 'li.postbit',
};
// Forma "ancestro": se usa como bit.closest(postContainerNew). Romperlo pierde el ocultado
// de las CITAS (la cita vive fuera del li.postbit, fallback insuficiente).
const T_CONTAINER_NEW = {
  key: 'postContainerNew', fixture: 'thread_new.html', url: GOLDEN.thread.url,
  scope: 'li.postbit', shape: 'ancestor', default: 'div.postbit_wrapper', anchor: 'li.postbit',
};

// --- Mutaciones (transforman el DOM como haria FC) ---
const M = {
  idPrefix: { name: 'cambiar prefijo de id postmenu_ -> post-menu-',
    apply: (d) => { for (const el of d.querySelectorAll('[id^="postmenu_"]')) el.id = el.id.replace('postmenu_', 'post-menu-'); } },
  noId: { name: 'eliminar el id postmenu_ entero',
    apply: (d) => { for (const el of d.querySelectorAll('[id^="postmenu_"]')) el.removeAttribute('id'); } },
  hrefScheme: { name: 'cambiar esquema href member.php?u= -> members/',
    apply: (d) => { for (const a of d.querySelectorAll('a[href*="member.php"]')) a.setAttribute('href', (a.getAttribute('href') || '').replace('member.php?u=', 'members/').replace('member.php', 'members')); } },
  classRename: { name: 'renombrar clase .xsaid -> .user-said',
    apply: (d) => { for (const el of d.querySelectorAll('span.xsaid')) el.className = el.className.replace('xsaid', 'user-said'); } },
  wrapperRename: { name: 'renombrar clase .postbit_wrapper -> .pb-wrap-x',
    apply: (d) => { for (const el of d.querySelectorAll('div.postbit_wrapper')) el.className = el.className.replace('postbit_wrapper', 'pb-wrap-x'); } },
  removeAuthor: { name: 'eliminar el bloque de autor (postmenu_) entero',
    apply: (d) => { for (const el of d.querySelectorAll('[id^="postmenu_"]')) el.remove(); } },
};

function mutate(fixture, mutation) {
  const win = L.loadDom(L.readFixture(fixture), 'about:blank');
  mutation.apply(win.document);
  return win.document.documentElement.outerHTML;
}

// La mutacion debe romper el TARGET concreto (no otro). Comprobamos rompiendo solo ese
// selector por defecto y viendo que el comportamiento cambia.
function brokenToken(mutKey) {
  return { idPrefix: 'postmenu_', noId: 'postmenu_', classRename: 'xsaid' }[mutKey] || null;
}

const MATRIX = [
  { target: T_AUTHOR_NEW, mut: 'idPrefix' },
  { target: T_AUTHOR_NEW, mut: 'noId' },
  { target: T_AUTHOR_NEW, mut: 'hrefScheme' },
  { target: T_AUTHOR_OLD, mut: 'classRename' },
  { target: T_CONTAINER_NEW, mut: 'wrapperRename' },
];

for (const { target, mut } of MATRIX) {
  const mutation = M[mut];
  test(`HEAL ${target.key} <- ${mutation.name}`, () => {
    const cleanHtml = L.readFixture(target.fixture);
    const cleanDoc = L.loadDom(cleanHtml, target.url).document;
    const mutHtml = mutate(target.fixture, mutation);

    // 2) la mutacion rompe de verdad el comportamiento
    const cleanBeh = Heal.behavior(cleanHtml, target.url, android, target.anchor, null);
    const brokenBeh = Heal.behavior(mutHtml, target.url, android, target.anchor, null);
    assert.ok(cleanBeh.size > 0, 'precondicion: en limpio se oculta algo');
    assert.ok(!Heal.setEq(cleanBeh, brokenBeh), 'la mutacion no cambia el comportamiento: no rompe nada');

    // 3) el healer cura y la cura reproduce EXACTAMENTE el comportamiento limpio
    const res = Heal.heal(target, { cleanDoc, cleanHtml, mutHtml, android });
    assert.ok(res.selector, `no curo: ${res.reason}. candidatos=${JSON.stringify(res.candidates)}`);

    const healedBeh = Heal.behavior(mutHtml, target.url, android, target.anchor, { [target.key]: res.selector });
    assert.ok(Heal.setEq(healedBeh, cleanBeh),
      `la cura "${res.selector}" no reproduce el comportamiento limpio (limpio ${[...cleanBeh]} vs curado ${[...healedBeh]})`);

    // el selector curado no debe seguir usando el token roto
    const tok = brokenToken(mut);
    if (tok) assert.ok(!res.selector.includes(tok), `la cura sigue usando el token roto "${tok}": ${res.selector}`);

    console.log(`   ${target.key} <- ${mut}: curado a "${res.selector}"`);
  });
}

// --- Caso "NO curar": si FC elimina el autor, el healer debe rendirse, no inventar ---
test('NO cura cuando el autor desaparece (se rinde en vez de inventar)', () => {
  const target = T_AUTHOR_NEW;
  const cleanHtml = L.readFixture(target.fixture);
  const cleanDoc = L.loadDom(cleanHtml, target.url).document;
  const mutHtml = mutate(target.fixture, M.removeAuthor);

  const res = Heal.heal(target, { cleanDoc, cleanHtml, mutHtml, android });
  assert.strictEqual(res.selector, null,
    `deberia rendirse pero curo a "${res.selector}" (${res.reason})`);
  console.log(`   no-cura OK: ${res.reason}`);
});
