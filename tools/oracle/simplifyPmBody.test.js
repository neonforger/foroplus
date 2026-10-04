// Regresion de simplifyPmBody (extractor.js): en un MP, la conversacion anterior se aplanaba.
//
// Contexto (2026-09-24): FC no guarda la conversacion de un MP aparte: cada respuesta lleva
// los mensajes anteriores DENTRO del cuerpo, como citas anidadas
// (`div.squote > div.quote > [cabecera "Cita de <b>X</b>"] + [contenido]`). simplifyPmBody
// convertia las citas de FUERA hacia DENTRO y rehacia la de fuera desde su HTML en texto, asi
// que las de dentro quedaban huerfanas y nunca se convertian: salia "Cita de A / Cita de B /
// Cita de A / texto…" todo al mismo nivel, sin saber quien dijo que.
//
// El HTML es el real de un MP (medido por CDP), con los nombres y el texto cambiados.

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
  'window.__test = { simplifyPmBody: simplifyPmBody }; })();'
);
assert.notStrictEqual(INSTRUMENTED_SRC, EXTRACTOR_SRC, 'no se encontro el cierre `})();` de extractor.js');

function cita(quien, dentro) {
  return `<div class="squote"> <div class="quote"> <div style="display: flex; flex-direction: row; align-items: flex-start; margin-bottom: 8px"> <div style="display: inline-block">Cita de <b>${quien}</b></div> </div> <div style="word-wrap:break-word; word-break:normal;font-size: 0.813rem; margin-bottom: 38px"> <div style="max-height: 410px; overflow-y: auto">${dentro}</div> </div> </div> </div>`;
}

const CUERPO =
  cita('ana', cita('beto', cita('ana', 'primero') + ' segundo') + '<br /> tercero') + ' cuarto ';

function simplificar(html) {
  const dom = new JSDOM(`<!doctype html><html><body><div id="post_message_">${html}</div></body></html>`,
    { url: 'https://forocoches.com/foro/private.php?do=showpm&pmid=1' });
  const { window } = dom;
  window.AndroidShell = {};
  new vm.Script(INSTRUMENTED_SRC, { filename: 'extractor.js' }).runInContext(vm.createContext(window));
  const msg = window.document.getElementById('post_message_');
  const out = window.__test.simplifyPmBody(msg, window.document);
  const res = window.document.createElement('div');
  res.innerHTML = out;
  return res;
}

test('las citas anidadas de un MP se convierten TODAS, no solo la de fuera', () => {
  const r = simplificar(CUERPO);
  assert.strictEqual(r.querySelectorAll('div.quote').length, 0, 'no puede quedar ningún div.quote sin convertir');
  assert.strictEqual(r.querySelectorAll('blockquote').length, 3);
  // Anidadas de verdad: la de dentro vive dentro de la de fuera.
  assert.ok(r.querySelector('blockquote blockquote blockquote'), 'se perdió el anidamiento');
});

test('cada cita dice quién habla UNA vez, sin arrastrar el "Cita de X" de FC', () => {
  const r = simplificar(CUERPO);
  assert.ok(!/Cita de/.test(r.textContent), `sobra el "Cita de": ${r.textContent}`);
  const cabeceras = [...r.querySelectorAll('blockquote > b:first-child')].map((b) => b.textContent);
  assert.strictEqual(JSON.stringify(cabeceras), JSON.stringify(['ana dijo:', 'beto dijo:', 'ana dijo:']));
});

test('la respuesta nueva queda FUERA de todas las citas', () => {
  const r = simplificar(CUERPO);
  const fuera = [...r.childNodes].filter((n) => n.nodeName !== 'BLOCKQUOTE').map((n) => n.textContent).join('');
  assert.strictEqual(fuera.trim(), 'cuarto');
});
