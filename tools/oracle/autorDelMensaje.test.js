// El autor de UN mensaje en la página `showthread.php?p=N&pp=1` (autorDelMensaje, extractor.js),
// de donde sale el "último que escribe" de la lista. La cabecera del post está calcada de la
// página real medida el 2026-10-02 con la plantilla móvil (curl con UA de Android), recortada y
// con el nombre cambiado. Lo importante: el nombre va dentro de `postmenu_N`, y el MISMO nombre
// sale además en el menú desplegable (`postmenu_N_menu`) y el `<h2>` — no hay que confundirlos
// con otro mensaje ni con la cabecera de quien mira.

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
  'window.__test = { autorDelMensaje: autorDelMensaje }; })();'
);

function autor(html, pid) {
  const dom = new JSDOM(html, { url: 'https://forocoches.com/foro/showthread.php?p=' + pid + '&pp=1' });
  const { window } = dom;
  window.AndroidShell = {};
  new vm.Script(INSTRUMENTED_SRC, { filename: 'extractor.js' }).runInContext(vm.createContext(window));
  return window.__test.autorDelMensaje(window.document, pid);
}

const CABECERA_QUIEN_MIRA = `
  <div id="menu"><a href="member.php?u=0">YoMismo</a></div>`;

function post(pid, nombre) {
  return `
  <li class="postbit postbitim postcontainer">
    <div class="posthead ui-bar-c">
      <a class="fpostuseravatarlink ui-link" href="member.php?u=897981"><img class="thread-profile-image" src="/image/new_icons/avatar.svg"></a>
      <div>
        <div id="postmenu_${pid}">
          <b><a style="color: var(--coral);margin-left: 12px;" href="member.php?u=897981"> ${nombre} </a></b>
          <script type="text/javascript"> vbmenu_register("postmenu_${pid}", true); </script>
        </div>
        <div id="postmenu_${pid}_menu" style="display:none;">
          <h2>${nombre} <a href="member.php?u=897981" target="_blank"></a></h2>
          <div> Registro: Mar 2025 </div><div> Mensajes: 351 </div>
        </div>
        <a href="showthread.php?t=10823271&amp;page=380#post${pid}" id="postcount${pid}" name="380"><span class="postdate old">Hoy&nbsp;20:31</span></a>
      </div>
    </div>
  </li>`;
}

test('saca el autor del mensaje pedido, sin espacios', () => {
  const html = `<html><body>${CABECERA_QUIEN_MIRA}<ol id="posts">${post('520085495', 'ShurUltimo')}</ol></body></html>`;
  assert.strictEqual(autor(html, '520085495'), 'ShurUltimo');
});

test('si el mensaje no está (ignorado, +HD, otra página) no se inventa nada', () => {
  const html = `<html><body>${CABECERA_QUIEN_MIRA}<ol id="posts">${post('111', 'OtroShur')}</ol></body></html>`;
  assert.strictEqual(autor(html, '520085495'), '');
  // Un ignorado llega como muñón SIN postmenu_ (gotcha 23).
  const muñon = `<html><body><ol id="posts"><li class="postbit">Este mensaje está oculto</li></ol></body></html>`;
  assert.strictEqual(autor(muñon, '520085495'), '');
});
