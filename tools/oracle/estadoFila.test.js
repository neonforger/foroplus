// El estado de vBulletin de cada fila del listado (parseRow -> `estado`).
//
// FC ya no pinta el statusicon como imagen (gotcha 16), pero sigue dejando su sufijo en un
// COMENTARIO justo antes del icono de la fila: `<!-- _hot_new-->`, `<!-- _dot-->`, `<!-- -->`.
// Sondeado el 2026-09-27: `_new`/`_hot`/`_hot_new` en 420 filas de cinco subforos, y `_dot`
// (has escrito en el hilo) al publicar en Pruebas. Con `_dot` la fila trae ademas OTRO
// comentario, el icono de "participado" que FC deja apagado — y ese no es el estado.

const test = require('node:test');
const assert = require('node:assert');
const fs = require('fs');
const path = require('path');
const vm = require('vm');
const { JSDOM } = require('jsdom');

const ROOT = path.resolve(__dirname, '..', '..');
const RESOURCES = path.join(ROOT, 'app', 'src', 'test', 'resources');
const EXTRACTOR_SRC = fs.readFileSync(path.join(ROOT, 'app', 'src', 'main', 'assets', 'extractor.js'), 'utf8');
const INSTRUMENTED_SRC = EXTRACTOR_SRC.replace(
  /\}\)\(\);\s*$/,
  'window.__test = { parseListDoc: parseListDoc }; })();'
);

function parseHtml(html) {
  const dom = new JSDOM(html, { url: 'https://forocoches.com/foro/forumdisplay.php?f=8' });
  const { window } = dom;
  window.AndroidShell = {};
  new vm.Script(INSTRUMENTED_SRC, { filename: 'extractor.js' }).runInContext(vm.createContext(window));
  return window.__test.parseListDoc(window.document);
}

// Calcada de la fila REAL medida en Pruebas tras publicar (t=10816242), recortada.
function filaReal(tid, comentario, participado) {
  const apagado = participado
    ? '<!-- <span style="background-image: var(--tema-participado); width: 22px;"> </span> -->'
    : '';
  return `
  <div style="display: flex; flex-direction: column; padding: 8px 10px 0">
    <div style="display: flex; flex-direction: row; align-items: center; width: 100%">
      <!--${comentario}-->
      ${apagado}
      <span style="background-image: var(--message); width: 22px; height: 22px;"> </span>
      <a href="showthread.php?t=${tid}" style="width: 100%"><span>Titulo del hilo ${tid}</span></a>
    </div>
  </div>`;
}

test('el fixture real trae el estado de cada fila', () => {
  const html = fs.readFileSync(path.join(RESOURCES, 'forumdisplay_mobile_unread.html'), 'utf8');
  const hilos = parseHtml(html);
  const cuenta = {};
  hilos.forEach((h) => { cuenta[h.estado] = (cuenta[h.estado] || 0) + 1; });
  // El fixture es el de la mezcla conocida 24 sin leer / 16 leidos (ver parseRow.test.js):
  // 23 _hot_new + 1 _new = 24, y 14 _hot + 2 vacios = 16.
  assert.strictEqual(hilos.length, 40);
  assert.strictEqual(cuenta['_hot_new'], 23);
  assert.strictEqual(cuenta['_new'], 1);
  assert.strictEqual(cuenta['_hot'], 14);
  assert.strictEqual(cuenta[''], 2);
  // Y el sufijo _new dice lo mismo que la negrita, fila a fila: son dos lecturas del mismo dato.
  hilos.forEach((h) => assert.strictEqual(/new/.test(h.estado), h.unread, 'tid ' + h.tid));
});

test('has participado: _dot, sin confundirlo con el icono apagado de al lado', () => {
  const html = `<html><body>${filaReal('111', ' _dot', true)}${filaReal('222', ' ', false)}</body></html>`;
  const hilos = parseHtml(html);
  const por = Object.fromEntries(hilos.map((h) => [h.tid, h.estado]));
  assert.strictEqual(por['111'], '_dot');
  assert.strictEqual(por['222'], '');
});
