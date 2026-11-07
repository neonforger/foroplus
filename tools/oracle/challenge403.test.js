// El challenge de Cloudflare llega con 403 (extractor.js).
//
// El 2026-11-07, tras el ataque de los hilos "Zzb", FC puso el challenge de Cloudflare en
// TODO el foro. Medido con curl: 403 + `cf-mitigated: challenge` + <title>Just a moment...</title>
// en portada, listado e hilo. El motor hacía `if (!r.ok) throw` ANTES de mirar el título, así
// que el challenge se quedaba en un "http 403" genérico: la app pintaba "No se pudo cargar el
// listado" para siempre y nunca enseñaba la verificación (la única que lo resuelve).

const test = require('node:test');
const assert = require('node:assert');
const fs = require('fs');
const path = require('path');
const vm = require('vm');
const { JSDOM } = require('jsdom');

const ROOT = path.resolve(__dirname, '..', '..');
const EXTRACTOR_SRC = fs.readFileSync(path.join(ROOT, 'app', 'src', 'main', 'assets', 'extractor.js'), 'utf8');

const CHALLENGE = '<!doctype html><html><head><title>Just a moment...</title></head>' +
  '<body><div id="challenge-platform"></div></body></html>';

function respuesta(status, cuerpo, cabeceras) {
  const h = Object.assign({}, cabeceras || {});
  return Promise.resolve({
    ok: status >= 200 && status < 300,
    status: status,
    url: 'https://forocoches.com/foro/forumdisplay.php?f=2',
    headers: { get: (k) => h[String(k).toLowerCase()] || null },
    text: () => Promise.resolve(cuerpo)
  });
}

/** Ejecuta el extractor real con un fetch falso y devuelve lo que llegó al puente. */
function motor(res) {
  const dom = new JSDOM('<!doctype html><html><body></body></html>',
    { url: 'https://forocoches.com/foro/' });
  const { window } = dom;
  const llamadas = [];
  window.AndroidShell = new Proxy({}, {
    get: (_, nombre) => (arg) => llamadas.push([nombre, arg])
  });
  window.fetch = () => res;
  new vm.Script(EXTRACTOR_SRC, { filename: 'extractor.js' }).runInContext(vm.createContext(window));
  return { window, llamadas, espera: () => new Promise((r) => setTimeout(r, 30)) };
}

const CF = { 'cf-mitigated': 'challenge' };

test('el listado con el challenge en 403 avisa de cloudflare, no de "http 403"', async () => {
  const m = motor(respuesta(403, CHALLENGE, CF));
  m.window.fcLoadThreadList('https://forocoches.com/foro/forumdisplay.php?f=2');
  await m.espera();
  assert.deepStrictEqual(m.llamadas, [['onListError', 'cloudflare']]);
});

test('el hilo con el challenge en 403 avisa de cloudflare', async () => {
  const m = motor(respuesta(403, CHALLENGE, CF));
  m.window.fcLoadThread('https://forocoches.com/foro/showthread.php?t=1');
  await m.espera();
  assert.deepStrictEqual(m.llamadas, [['onThreadError', 'cloudflare']]);
});

test('citas y menciones con el challenge en 403 avisan de cloudflare', async () => {
  const m = motor(respuesta(403, CHALLENGE, CF));
  m.window.fcLoadNotices('https://forocoches.com/foro/member.php?u=1&tab=quotes', 'quotes');
  await m.espera();
  assert.strictEqual(m.llamadas.length, 1);
  assert.strictEqual(m.llamadas[0][0], 'onNotices');
  assert.strictEqual(JSON.parse(m.llamadas[0][1]).error, 'cloudflare');
});

test('un 403 que NO es el challenge sigue siendo un error http', async () => {
  const m = motor(respuesta(403, '<html><head><title>Forbidden</title></head></html>', {}));
  m.window.fcLoadThreadList('https://forocoches.com/foro/forumdisplay.php?f=2');
  await m.espera();
  assert.deepStrictEqual(m.llamadas, [['onListError', 'Error: http 403']]);
});
