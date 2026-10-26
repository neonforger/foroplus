// collectEmbeds (extractor.js): los reproductores en su sitio y el Vocaroo citado.
//
// Contexto (2026-10-26, los dos los reportó Juan por privado):
// - Los embeds se pintaban todos al FINAL del mensaje y dejaban un hueco donde iban. Ahora
//   collectEmbeds deja en su sitio una marca `<n>` y Kotlin parte el texto por ahí
//   (TramosEmbed.kt).
// - Un Vocaroo dentro de una cita salía fuera del recuadro, como si fuera del que cita: el
//   bloque de Vocaroo no miraba si estaba dentro de una cita (gotcha 43).

const test = require('node:test');
const assert = require('node:assert');
const fs = require('fs');
const path = require('path');
const vm = require('vm');
const { JSDOM } = require('jsdom');

const ROOT = path.resolve(__dirname, '..', '..');
const EXTRACTOR_SRC = fs.readFileSync(path.join(ROOT, 'app', 'src', 'main', 'assets', 'extractor.js'), 'utf8');
const INSTRUMENTED_SRC = EXTRACTOR_SRC.replace(
  /\}\)\(\);\s*$/,
  'window.__test = { collectEmbeds: collectEmbeds }; })();'
);
assert.notStrictEqual(INSTRUMENTED_SRC, EXTRACTOR_SRC, 'no se encontro el cierre `})();` de extractor.js');

function recoger(html) {
  const dom = new JSDOM(`<!doctype html><html><body><div id="m">${html}</div></body></html>`,
    { url: 'https://forocoches.com/foro/showthread.php?t=1' });
  const { window } = dom;
  window.AndroidShell = {};
  new vm.Script(INSTRUMENTED_SRC, { filename: 'extractor.js' }).runInContext(vm.createContext(window));
  const m = window.document.getElementById('m');
  const embeds = window.__test.collectEmbeds(m, window.document);
  return { m, embeds };
}

const marca = (n) => '' + n + '';

// Markup real de Vocaroo (gotcha 25) y del YouTube de FC (verVideo, gotcha 13).
const vocaroo = (id) =>
  `<div style="margin-top: 8px;"><iframe width="300" height="60" src="https://vocaroo.com/embed/${id}" frameborder="0"></iframe></div>` +
  `<div style="margin-bottom: 8px;"><img src="/image/vocaroo_logo2.png" width="16" height="16"> <a href="https://voca.ro/${id}"><span class="smallfont">Vocaroo</span></a></div><br />`;
const youtube = (id, cont) => `<div id="${cont}"></div><script>verVideo('${id}','${cont}')</script>`;

test('cada reproductor deja su marca EN SU SITIO, entre el texto', () => {
  const { m, embeds } = recoger(`A ver...<br>${youtube('aaaaaaaaaa1', '111')}<br>Y de propina<br>${youtube('bbbbbbbbbb2', '222')}`);
  assert.deepStrictEqual(Array.from(embeds, (e) => e.id), ['aaaaaaaaaa1', 'bbbbbbbbbb2']);
  const t = m.textContent;
  assert.ok(t.indexOf('A ver') < t.indexOf(marca(0)));
  assert.ok(t.indexOf(marca(0)) < t.indexOf('Y de propina'));
  assert.ok(t.indexOf('Y de propina') < t.indexOf(marca(1)));
});

test('la marca lleva el indice de la lista aunque los tipos se recojan en otro orden', () => {
  // Los tweets se recogen antes que los YouTube: el índice 0 es el tweet aunque vaya detrás.
  const { m, embeds } = recoger(`${youtube('abcdEFGH', '9')}texto<blockquote class="twitter-tweet"><a href="https://x.com/a/status/1">t</a></blockquote>`);
  assert.deepStrictEqual(Array.from(embeds, (e) => e.kind), ['twitter', 'youtube']);
  const t = m.textContent;
  assert.ok(t.indexOf(marca(1)) < t.indexOf('texto'));
  assert.ok(t.indexOf('texto') < t.indexOf(marca(0)));
});

test('un Vocaroo suelto es un reproductor y se lleva su pie', () => {
  const { m, embeds } = recoger(`hola${vocaroo('BBBBBBBBBBB2')}`);
  assert.strictEqual(embeds.length, 1);
  assert.strictEqual(embeds[0].alto, 60);
  assert.ok(m.textContent.includes(marca(0)));
  assert.ok(!/Vocaroo/.test(m.textContent));
});

test('un Vocaroo DENTRO de una cita es un enlace en su sitio, no un reproductor', () => {
  const { m, embeds } = recoger(
    `<blockquote>Fulano dijo: baila${vocaroo('AAAAAAAAAAA1')}</blockquote>Esta mola más.${vocaroo('BBBBBBBBBBB2')}`
  );
  // Solo el propio es reproductor.
  assert.strictEqual(embeds.length, 1);
  assert.ok(embeds[0].url.includes('BBBBBBBBBBB2'));
  const cita = m.querySelector('blockquote');
  const a = cita.querySelector('a');
  assert.ok(a, 'el enlace se queda dentro de la cita');
  assert.strictEqual(a.getAttribute('href'), 'https://voca.ro/AAAAAAAAAAA1');
  assert.ok(/Vocaroo/.test(a.textContent));
  assert.ok(!cita.querySelector('iframe'));
});
