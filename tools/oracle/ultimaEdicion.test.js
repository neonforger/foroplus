// Última edición de un mensaje (ultimaEdicion, extractor.js). Lo pidió un tester hace tiempo
// ("falta el aviso de última fecha y hora de edición") y se reportó de nuevo el 2026-10-15.
//
// La plantilla móvil de FC manda la edición DENTRO DE UN COMENTARIO HTML, con quién y cuándo, y
// al pie un `<span class="postdate old">Editado: … - </span>` sin el quién. La fecha normal de
// la cabecera lleva la MISMA clase, así que el respaldo no puede quedarse con el primer span.
// Se prueba contra las páginas reales capturadas (app/src/test/resources), no contra una réplica.

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
  'window.__test = { ultimaEdicion: ultimaEdicion }; })();'
);
assert.notStrictEqual(INSTRUMENTED_SRC, EXTRACTOR_SRC, 'no se encontro el cierre `})();` de extractor.js');

function cargar(html) {
  const dom = new JSDOM(html, { url: 'https://forocoches.com/foro/showthread.php?t=1' });
  const { window } = dom;
  window.AndroidShell = {};
  new vm.Script(INSTRUMENTED_SRC, { filename: 'extractor.js' }).runInContext(vm.createContext(window));
  return window;
}

/** Las ediciones de todos los mensajes de una página real, por pid. */
function edicionesDe(fixture) {
  const html = fs.readFileSync(path.join(ROOT, 'app', 'src', 'test', 'resources', fixture), 'utf8');
  const window = cargar(html);
  const out = {};
  window.document.querySelectorAll('div.postbit_wrapper').forEach((wrap) => {
    const pid = wrap.id.replace('edit', '');
    out[pid] = window.__test.ultimaEdicion(wrap, window.document);
  });
  return out;
}

test('lee quién y cuándo del comentario, y deja vacíos los no editados', () => {
  const ed = Object.values(edicionesDe('showthread_poll_public.html'));
  const editados = ed.filter((e) => e.cuando);
  assert.deepStrictEqual(
    editados.map((e) => e.por + ' | ' + e.cuando).sort(),
    ['AbsRS4 | 07-nov-2025 12:12', 'AbsRS4 | 14-nov-2025 00:03', 'MoteroEnlatado | 06-nov-2025 21:41']
  );
  assert.ok(ed.length > editados.length, 'los mensajes sin editar no pueden salir como editados');
  for (const e of ed.filter((x) => !x.cuando)) assert.strictEqual(e.por, '');
});

test('"Hoy" y "Ayer" se quedan como los escribe FC', () => {
  const cuandos = Object.values(edicionesDe('showthread_poll.html')).map((e) => e.cuando).filter(Boolean).sort();
  assert.deepStrictEqual(cuandos, ['Ayer 21:32', 'Hoy 07:33']);
});

test('sin comentario, el "Editado:" del pie sirve de respaldo y no se confunde con la fecha', () => {
  const window = cargar(`<html><body>
    <div id="edit1" class="postbit_wrapper">
      <a href="#"><span class="postdate old">Hoy&nbsp;20:31</span></a>
      <div id="post_message_1">texto</div>
      <!-- info ultima edicion del post -->
      <div><span class="postdate old">Editado: Ayer 22:51 - </span></div>
    </div></body></html>`);
  const wrap = window.document.getElementById('edit1');
  assert.deepStrictEqual({ ...window.__test.ultimaEdicion(wrap, window.document) }, { por: '', cuando: 'Ayer 22:51' });
});

test('un mensaje sin editar no lleva nada', () => {
  const window = cargar(`<html><body>
    <div id="edit2" class="postbit_wrapper">
      <a href="#"><span class="postdate old">Hoy&nbsp;20:31</span></a>
      <div id="post_message_2">texto</div>
      <!--
        info ultima edicion del post
      -->
    </div></body></html>`);
  const wrap = window.document.getElementById('edit2');
  assert.deepStrictEqual({ ...window.__test.ultimaEdicion(wrap, window.document) }, { por: '', cuando: '' });
});
