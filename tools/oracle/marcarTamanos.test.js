// marcarTamanos (extractor.js): el [SIZE] de FC no se veía en la app.
//
// Contexto (2026-10-16, lo reportó Juan en el grupo): FC convierte `[SIZE=N]` en
// `<font size="N">` (medido en la plantilla móvil: showthread_poll_public.html lleva
// `<b><font size="2">V8 - 6.2L - 457cv</font>…`). Html.fromHtml de Android solo lee `color` y
// `face` de un <font>, así que el tamaño se perdía y todo salía a tamaño normal. El extractor
// lo pasa a `face="fcsize-N"`, que fromHtml sí convierte en un span (y cierra bien aunque vaya
// anidado), y Kotlin cambia ese span por uno de tamaño (TamanoLetra.kt).

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
  'window.__test = { marcarTamanos: marcarTamanos }; })();'
);
assert.notStrictEqual(INSTRUMENTED_SRC, EXTRACTOR_SRC, 'no se encontro el cierre `})();` de extractor.js');

function marcar(html) {
  const dom = new JSDOM(`<!doctype html><html><body><div id="m">${html}</div></body></html>`,
    { url: 'https://forocoches.com/foro/showthread.php?t=1' });
  const { window } = dom;
  window.AndroidShell = {};
  new vm.Script(INSTRUMENTED_SRC, { filename: 'extractor.js' }).runInContext(vm.createContext(window));
  const m = window.document.getElementById('m');
  window.__test.marcarTamanos(m);
  return m;
}

test('un [SIZE] pasa de size a face, que es lo que lee fromHtml', () => {
  const m = marcar('<b><font size="2">V8 - 6.2L - 457cv</font></b>');
  const f = m.querySelector('font');
  assert.strictEqual(f.getAttribute('face'), 'fcsize-2');
  assert.strictEqual(f.hasAttribute('size'), false);
  assert.strictEqual(m.textContent, 'V8 - 6.2L - 457cv');
});

test('los anidados se marcan todos, cada uno con su tamaño', () => {
  const m = marcar('<font size="5">grande <font size="1">chico</font></font>');
  const caras = [...m.querySelectorAll('font')].map((f) => f.getAttribute('face'));
  assert.deepStrictEqual(caras, ['fcsize-5', 'fcsize-1']);
});

test('los relativos (+N / -N) cuentan desde el 3, como en HTML, y se quedan entre 1 y 7', () => {
  const m = marcar('<font size="+2">a</font><font size="-1">b</font><font size="+9">c</font><font size="0">d</font>');
  const caras = [...m.querySelectorAll('font')].map((f) => f.getAttribute('face'));
  assert.deepStrictEqual(caras, ['fcsize-5', 'fcsize-2', 'fcsize-7', 'fcsize-1']);
});

test('un size ilegible no se toca, y el color de un <font> se respeta', () => {
  const m = marcar('<font size="enorme" color="red">x</font><font color="blue" size="4">y</font>');
  const [a, b] = m.querySelectorAll('font');
  assert.strictEqual(a.getAttribute('size'), 'enorme');
  assert.strictEqual(a.hasAttribute('face'), false);
  assert.strictEqual(b.getAttribute('face'), 'fcsize-4');
  assert.strictEqual(b.getAttribute('color'), 'blue');
});
