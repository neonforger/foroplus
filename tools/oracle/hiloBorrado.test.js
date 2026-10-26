// hiloBorrado (extractor.js): FC dice que el hilo no existe (lo han borrado, "echenique").
//
// Medido el 2026-10-26 con t=99999999: showthread trae "Ningún Tema especificado." y newreply
// "Tema especificado inválido", los dos con 200. La app decía "no se pudo cargar el hilo" /
// "no se pudo enviar la respuesta" sin explicar nada (Juan, 2026-10-25).

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
  'window.__test = { hiloBorrado: hiloBorrado }; })();'
);
assert.notStrictEqual(INSTRUMENTED_SRC, EXTRACTOR_SRC, 'no se encontro el cierre `})();` de extractor.js');

function borrado(cuerpo) {
  const dom = new JSDOM(`<!doctype html><html><body>${cuerpo}</body></html>`,
    { url: 'https://forocoches.com/foro/showthread.php?t=1' });
  const { window } = dom;
  window.AndroidShell = {};
  new vm.Script(INSTRUMENTED_SRC, { filename: 'extractor.js' }).runInContext(vm.createContext(window));
  return window.__test.hiloBorrado(window.document);
}

test('la página de un hilo borrado', () => {
  assert.strictEqual(borrado('<div>Quedan solo 23 días...</div><div>Ningún Tema especificado.</div><a>Regresar</a>'), true);
});

test('el formulario de responder de un hilo borrado', () => {
  assert.strictEqual(borrado('<div> Tema especificado inválido </div>'), true);
});

test('un hilo normal no es un hilo borrado', () => {
  assert.strictEqual(borrado('<div id="post_message_1">Hola, qué tal</div>'), false);
});
