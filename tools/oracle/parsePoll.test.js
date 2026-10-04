// Regresion de parsePoll (extractor.js): la encuesta desaparecia en los hilos PROPIOS.
//
// Contexto (2026-09-24): Nacho reporto que en los hilos que abre el no ve el desplegable de la
// encuesta, y en los de los demas si. Medido por CDP creando una encuesta en el subforo de
// Pruebas: al AUTOR, FC le pinta ANTES de la encuesta un formulario de administracion
// (`postings.php`, boton "Borrar tema") que tambien lleva `<input name="pollid">`. parsePoll
// cogia "el primer form con poll.php o con pollid" -> se quedaba con el de borrar, sin
// opciones, y devolvia null.
//
// El HTML de abajo es el capturado en vivo (t=10815008), recortado a los dos formularios y
// con el securitytoken sustituido.

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
  'window.__test = { parsePoll: parsePoll }; })();'
);
assert.notStrictEqual(INSTRUMENTED_SRC, EXTRACTOR_SRC, 'no se encontro el cierre `})();` de extractor.js');

function parsePollHtml(html) {
  const dom = new JSDOM(html, { url: 'https://forocoches.com/foro/showthread.php?t=10815008' });
  const { window } = dom;
  window.AndroidShell = {};
  new vm.Script(INSTRUMENTED_SRC, { filename: 'extractor.js' }).runInContext(vm.createContext(window));
  return window.__test.parsePoll(window.document);
}

const FORM_ADMIN = `
<div style="display: flex; flex:1; justify-content: center; ">
  <form action="/foro/postings.php?t=10815008&amp;pollid=328783" method="post" name="threadadminform">
    <input type="hidden" name="do" value="deletethread">
    <input type="hidden" name="s" value="" />
    <input type="hidden" name="securitytoken" value="TOKEN" />
    <input type="hidden" name="t" value="10815008" />
    <input type="hidden" name="pollid" value="328783" />
    <input type="submit" class="rounded-button rounded-button-publish-comment" value="Borrar tema" />
  </form>
</div>`;

const FORM_VOTO = `
<form action="poll.php?do=pollvote&amp;pollid=328783" method="post">
  <input type="hidden" name="s" value="" />
  <input type="hidden" name="securitytoken" value="TOKEN" />
  <input type="hidden" name="do" value="pollvote" />
  <input type="hidden" name="pollid" value="328783" />
  <div style="margin: 18px 16px 0 16px">
    <strong style="color: var(--coral)">Pregunta de prueba</strong>
    <separator style="margin: 8px 0 8px 0"></separator>
    <div style="margin-top: 22px"> <label for="rb_optionnumber_1"> <input type="radio" name="optionnumber" value="1" id="rb_optionnumber_1" /> Opcion A </label> </div>
    <div style="margin-top: 22px"> <label for="rb_optionnumber_2"> <input type="radio" name="optionnumber" value="2" id="rb_optionnumber_2" /> Opcion B </label> </div>
  </div>
  <table class="tborder"><div>
    <button class="simple-rounded-no-filled-button"><a href="poll.php?do=showresults&amp;pollid=328783">Ver Resultados</a></button>
    <input type="submit" class="button" value="Votar Ahora" />
  </div></table>
</form>`;

test('encuesta en un hilo PROPIO: el form de "Borrar tema" no tapa la encuesta', () => {
  const p = parsePollHtml(`<!doctype html><html><body>${FORM_ADMIN}${FORM_VOTO}</body></html>`);
  assert.ok(p, 'la encuesta no deberia desaparecer para el autor');
  assert.strictEqual(p.id, '328783');
  assert.strictEqual(p.question, 'Pregunta de prueba');
  assert.strictEqual(JSON.stringify(p.options.map((o) => o.text)), JSON.stringify(['Opcion A', 'Opcion B']));
  assert.strictEqual(p.canVote, true);
});

test('encuesta en un hilo AJENO (sin form de admin) sigue igual', () => {
  const p = parsePollHtml(`<!doctype html><html><body>${FORM_VOTO}</body></html>`);
  assert.ok(p);
  assert.strictEqual(p.options.length, 2);
});

test('solo el form de admin (hilo propio SIN encuesta votable) no inventa encuesta', () => {
  const p = parsePollHtml(`<!doctype html><html><body>${FORM_ADMIN}</body></html>`);
  assert.strictEqual(p, null);
});
