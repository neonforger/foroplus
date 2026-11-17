// Contactos (extractor.js): el formulario de confirmación de FC para añadir/quitar a alguien de
// tus contactos, y el error de página.
//
// Markup medido por CDP el 2026-11-17 (solo GET) en
// profile.php?do=addlist&userlist=buddy&u=UID y do=removelist: una confirmación con un form POST
// a do=doaddlist / do=doremovelist. Aquí va anonimizado (token a ceros, uid y nombre inventados).

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
  'window.__test = { formConfirmarLista: formConfirmarLista, errorDePagina: errorDePagina, ' +
  'copyFormFields: copyFormFields }; })();'
);
assert.notStrictEqual(INSTRUMENTED_SRC, EXTRACTOR_SRC, 'no se encontro el cierre `})();` de extractor.js');

function confirmacion(paso, uid) {
  return `
<form action="profile.php?do=dismissnotice" method="post" id="vbnotices">
  <input type="hidden" name="do" value="dismissnotice" />
  <input type="hidden" name="securitytoken" value="0000000000-0000000000000000000000000000000000000000" />
</form>
<form action="profile.php?do=do${paso}&amp;list=&amp;userid=${uid}" method="post">
  <input type="hidden" name="s" value="" />
  <input type="hidden" name="securitytoken" value="0000000000-0000000000000000000000000000000000000000" />
  <input type="hidden" name="do" value="do${paso}" />
  <input type="hidden" name="userlist" value="buddy" />
  <input type="hidden" name="userid" value="${uid}" />
  <input type="hidden" name="url" value="https://forocoches.com/foro/" />
  <div class="smallfont">Estas seguro que quieres agregar a Fulanito a tus Contactos?</div>
  <input type="submit" class="button" value="Si" name="confirm" accesskey="s" />
  <input type="submit" class="button" value="No" name="deny" />
</form>`;
}

function cargar(cuerpo) {
  const dom = new JSDOM(`<!doctype html><html><body>${cuerpo}</body></html>`,
    { url: 'https://forocoches.com/foro/profile.php' });
  const { window } = dom;
  window.AndroidShell = {};
  new vm.Script(INSTRUMENTED_SRC, { filename: 'extractor.js' }).runInContext(vm.createContext(window));
  return window;
}

test('encuentra el form de añadir para ese uid, no el de los avisos', () => {
  const w = cargar(confirmacion('addlist', '123456'));
  const f = w.__test.formConfirmarLista(w.document, 'doaddlist', '123456', 'buddy');
  assert.ok(f);
  assert.match(f.getAttribute('action'), /do=doaddlist/);
});

test('lo que se envía: los campos ocultos del form real + confirm=Si, sin deny', () => {
  const w = cargar(confirmacion('addlist', '123456'));
  const f = w.__test.formConfirmarLista(w.document, 'doaddlist', '123456', 'buddy');
  const p = w.__test.copyFormFields(f, { confirm: 'Si' }, ['deny']);
  assert.strictEqual(p.get('do'), 'doaddlist');
  assert.strictEqual(p.get('userlist'), 'buddy');
  assert.strictEqual(p.get('userid'), '123456');
  assert.strictEqual(p.get('securitytoken'), '0000000000-0000000000000000000000000000000000000000');
  assert.strictEqual(p.get('confirm'), 'Si');
  assert.strictEqual(p.has('deny'), false);
});

test('el de quitar también, y no se confunde con el de añadir', () => {
  const w = cargar(confirmacion('removelist', '123456'));
  assert.ok(w.__test.formConfirmarLista(w.document, 'doremovelist', '123456', 'buddy'));
  assert.strictEqual(w.__test.formConfirmarLista(w.document, 'doaddlist', '123456', 'buddy'), null);
});

test('un form de OTRO uid no vale (mejor no enviar que enviar otra cosa)', () => {
  const w = cargar(confirmacion('addlist', '999'));
  assert.strictEqual(w.__test.formConfirmarLista(w.document, 'doaddlist', '123456', 'buddy'), null);
});

test('un form de la lista de ignorados no vale para contactos', () => {
  const w = cargar(confirmacion('addlist', '123456').replace('value="buddy"', 'value="ignore"'));
  assert.strictEqual(w.__test.formConfirmarLista(w.document, 'doaddlist', '123456', 'buddy'), null);
});

test('error de página: la primera frase del mensaje de FC', () => {
  const w = cargar('<div id="st-error-main-error-message">El usuario Fulanito ya está en tu lista de contactos. Por favor regresa y vuelve a intentarlo.</div>');
  assert.strictEqual(w.__test.errorDePagina(w.document), 'El usuario Fulanito ya está en tu lista de contactos.');
});

test('sin error de página, vacío', () => {
  const w = cargar('<div>Gracias</div>');
  assert.strictEqual(w.__test.errorDePagina(w.document), '');
});
