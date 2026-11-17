// Qué le manda fcSearch a FC cuando la búsqueda va acotada a un usuario (extractor.js).
//
// La lupa de la actividad de un usuario (2026-11-17) reutiliza fcSearch: "@x · mensajes" busca
// por mensajes con `searchuser`, "@x · hilos suyos" por títulos con `starteronly=1` (los que
// abrió, medido el 2026-10-07) y "@x · participa" por títulos con `starteronly=0`.

const test = require('node:test');
const assert = require('node:assert');
const fs = require('fs');
const path = require('path');
const vm = require('vm');
const { JSDOM } = require('jsdom');

const ROOT = path.resolve(__dirname, '..', '..');
const EXTRACTOR_SRC = fs.readFileSync(path.join(ROOT, 'app', 'src', 'main', 'assets', 'extractor.js'), 'utf8');

const FORMULARIO = '<html><body><form><input name="securitytoken" value="0-0"></form></body></html>';

/** Ejecuta fcSearch con un fetch falso y devuelve el cuerpo del POST a do=process. */
async function cuerpoDe(args) {
  const dom = new JSDOM('<!doctype html><html><body></body></html>', { url: 'https://forocoches.com/foro/' });
  const { window } = dom;
  window.AndroidShell = new Proxy({}, { get: () => () => {} });
  let cuerpo = null;
  window.fetch = (url, opts) => {
    if (opts && opts.method === 'POST') cuerpo = new URLSearchParams(opts.body);
    return Promise.resolve({
      ok: true, status: 200, url: 'https://forocoches.com/foro/search.php?searchid=1',
      headers: { get: () => null },
      text: () => Promise.resolve(FORMULARIO)
    });
  };
  new vm.Script(EXTRACTOR_SRC, { filename: 'extractor.js' }).runInContext(vm.createContext(window));
  window.fcSearch.apply(null, args);
  await new Promise((r) => setTimeout(r, 30));
  return cuerpo;
}

test('por títulos con usuario: solo los hilos que abrió (starteronly=1)', async () => {
  const c = await cuerpoDe(['tesla', true, '', 'pepe']);
  assert.strictEqual(c.get('searchuser'), 'pepe');
  assert.strictEqual(c.get('exactname'), '1');
  assert.strictEqual(c.get('titleonly'), '1');
  assert.strictEqual(c.get('showposts'), '0');
  assert.strictEqual(c.get('starteronly'), '1');
});

test('por títulos en los que participa: starteronly=0', async () => {
  const c = await cuerpoDe(['tesla', true, '', 'pepe', true]);
  assert.strictEqual(c.get('titleonly'), '1');
  assert.strictEqual(c.get('starteronly'), '0');
  assert.strictEqual(c.get('searchuser'), 'pepe');
});

test('por mensajes con usuario: sus mensajes, uno a uno', async () => {
  const c = await cuerpoDe(['tesla', false, '', 'pepe']);
  assert.strictEqual(c.get('showposts'), '1');
  assert.strictEqual(c.get('titleonly'), '0');
  assert.strictEqual(c.get('starteronly'), '0');
});

test('sin usuario no se manda searchuser', async () => {
  const c = await cuerpoDe(['tesla', true, '']);
  assert.strictEqual(c.get('searchuser'), null);
  assert.strictEqual(c.get('starteronly'), null);
});
