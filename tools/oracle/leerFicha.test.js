// La cabecera y el "Sobre mí" de una ficha member.php (leerFicha, extractor.js).
//
// FC sirve DOS plantillas según el User-Agent y la app recibe la MÓVIL (styleid 8). La primera
// versión de leerFicha se escribió con la de escritorio (styleid 9) porque la sonda fue un curl
// sin User-Agent, y en el móvil se habría quedado sin título, sin firma y sin "Sobre mí".
// Los cuatro fixtures son recortes del HTML real medido el 2026-10-02 (curl con UA de Android y
// sin él), con los nombres y los textos cambiados. Todos llevan delante una cabecera "de quien
// mira" con sus propios contadores y su "Registro:", que es la trampa de los gotchas 1 y 33.

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
  'window.__test = { leerFicha: leerFicha }; })();'
);

function ficha(fixture, username) {
  const html = fs.readFileSync(path.join(RESOURCES, fixture), 'utf8');
  const dom = new JSDOM(html, { url: 'https://forocoches.com/foro/member.php?u=1' });
  const { window } = dom;
  window.AndroidShell = {};
  new vm.Script(INSTRUMENTED_SRC, { filename: 'extractor.js' }).runInContext(vm.createContext(window));
  // Se pasa por JSON, como en el puente: así se comparan objetos planos y no los del jsdom.
  return JSON.parse(JSON.stringify(window.__test.leerFicha(window.document, username)));
}

test('móvil: título, firma, fecha y contadores del perfil, no los de quien mira', () => {
  const f = ficha('member_mobile_firma.html', 'ShurConFirma');
  assert.strictEqual(f.rango, 'Autobaneado hasta el lunes');
  assert.strictEqual(f.registro, '03-oct-2010');
  assert.strictEqual(f.hilos, '67');
  assert.strictEqual(f.mensajes, '12.345');
  assert.match(f.firmaTexto, /^Texto de la firma con un enlace y/);
  // La firma se queda en texto + enlaces: la imagen fuera.
  assert.match(f.firma, /<a href="https:\/\/forocoches\.com\/foro\/showthread\.php\?t=123">un enlace<\/a>/);
  assert.doesNotMatch(f.firma, /<img/);
  assert.deepStrictEqual(f.sobreMi, []);
});

test('móvil: sin firma no se inventa una, y "Sobre" trae el coche', () => {
  const f = ficha('member_mobile_coche.html', 'ShurConCoche');
  assert.strictEqual(f.rango, 'Miembro');
  assert.strictEqual(f.registro, '30-may-2026');
  assert.strictEqual(f.firmaTexto, '');
  assert.strictEqual(f.firma, '');
  assert.deepStrictEqual(f.sobreMi, [{ icono: 'coches', valor: 'Seat Ibiza 1.9 TDI' }]);
});

test('escritorio: el mismo perfil se lee igual', () => {
  const f = ficha('member_desktop_firma.html', 'ShurConFirma');
  assert.strictEqual(f.rango, 'Autobaneado hasta el lunes');
  assert.strictEqual(f.registro, '03-oct-2010');
  assert.strictEqual(f.hilos, '67');
  assert.strictEqual(f.mensajes, '12.345');
  assert.strictEqual(f.firmaTexto, 'Texto de la firma de escritorio.');
});

test('escritorio: coche desde #collapseobj_aboutme', () => {
  const f = ficha('member_desktop_coche.html', 'ShurConCoche');
  assert.strictEqual(f.rango, 'Miembro');
  assert.strictEqual(f.registro, '30-may-2026');
  assert.strictEqual(f.firmaTexto, '');
  assert.deepStrictEqual(f.sobreMi, [{ icono: 'coches', valor: 'Seat Ibiza 1.9 TDI' }]);
});

test('con otro nombre no se le atribuye nada de la ficha', () => {
  const f = ficha('member_mobile_firma.html', 'OtroShur');
  assert.strictEqual(f.rango, '');
  assert.strictEqual(f.registro, '');
  assert.strictEqual(f.hilos, '');
  assert.strictEqual(f.mensajes, '');
});
