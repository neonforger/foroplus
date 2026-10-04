// Test de regresion para el detector "no leido" de parseRow (extractor.js).
//
// Contexto (2026-08-04): el brief original asumia que FC seguia sirviendo el markup viejo
// (<img src=".../statusicon/thread_new.gif">, <a id="thread_title_<tid>">) que trae el
// fixture congelado forumdisplay_old.html. Por CDP, contra la sesion real del dueno, se
// confirmo que la plantilla "mobile" que sirve FC HOY (forumdisplay.php Y subscription.php)
// NO tiene ese <img> en ningun sitio: el titulo va envuelto en un ancestro con
// "font-weight" en negrita cuando hay algo sin leer, sin id fijo. parseRow se adapto para
// subir desde el propio anchor del titulo (titleA) buscando ese estilo.
//
// Sin un fixture congelado de la plantilla NUEVA, un cambio futuro que mueva el negrita a
// un <span> INTERIOR (el bucle solo sube, nunca baja) rompe la funcion en silencio: ningun
// test se pone en rojo. Este archivo lo evita fijando el comportamiento contra
// forumdisplay_mobile_unread.html (capturado en vivo, subforo con mezcla real de hilos
// leidos y no leidos en la MISMA pagina — 24 en negrita / 16 normales de 40 — y depurado de
// datos de sesion: securitytoken/logouthash reales, uid y usuario del dueno, hashes de
// email de tracking de anuncios).
//
// Tambien fija el camino viejo (icono) contra forumdisplay_old.html, que SIEMPRE toma esa
// rama (trae el <img> en las 40 filas) y nunca pasa por el bucle de negrita — para que un
// cambio que rompa esa rama tambien salte en rojo aqui, no solo en los tests Kotlin.

const test = require('node:test');
const assert = require('node:assert');
const fs = require('fs');
const path = require('path');
const vm = require('vm');
const { JSDOM } = require('jsdom');

const ROOT = path.resolve(__dirname, '..', '..');
const RESOURCES = path.join(ROOT, 'app', 'src', 'test', 'resources');
const EXTRACTOR_SRC = fs.readFileSync(path.join(ROOT, 'app', 'src', 'main', 'assets', 'extractor.js'), 'utf8');

// extractor.js es un IIFE que no expone nada; se inyecta una salida de depuracion justo
// antes de su cierre para poder llamar a parseListDoc/parseRow desde el test.
const INSTRUMENTED_SRC = EXTRACTOR_SRC.replace(
  /\}\)\(\);\s*$/,
  'window.__test = { parseListDoc: parseListDoc, parseRow: parseRow }; })();'
);
assert.notStrictEqual(INSTRUMENTED_SRC, EXTRACTOR_SRC, 'no se encontro el cierre `})();` de extractor.js para instrumentar');

function parseHtml(html, url) {
  const dom = new JSDOM(html, { url });
  const { window } = dom;
  window.AndroidShell = {}; // extractor.js aborta si falta el puente nativo
  new vm.Script(INSTRUMENTED_SRC, { filename: 'extractor.js' }).runInContext(vm.createContext(window));
  return window.__test.parseListDoc(window.document);
}

function parseFixture(file, url) {
  const html = fs.readFileSync(path.join(RESOURCES, file), 'utf8');
  return parseHtml(html, url);
}

// Fila minima SINTETICA (no capturada), calcada de la estructura real de
// forumdisplay_mobile_unread.html (span envolviendo el <a> del titulo, con `style`
// variable) solo para poder variar el valor de font-weight a voluntad. Un solo hilo por
// pagina para no arrastrar el resto del markup real (avatares, paginacion, etc.) que no
// hace falta para probar la regex.
function syntheticRow(tid, styleValue) {
  return `<!doctype html><html><body>
<div class="threads-list">
  <div style="border:none">
    <div style="display:flex;flex-direction:column">
      <div style="display:flex;flex-direction:row;align-items:center">
        <span style="background-image: var(--message);"> </span>
        <span style="${styleValue}">
          <a href="showthread.php?t=${tid}" style="width:100%">
            <span>Hilo sintetico ${tid}</span>
          </a>
        </span>
      </div>
      <div><span>@autorSintetico</span><span>1</span></div>
    </div>
  </div>
</div>
</body></html>`;
}

// ====================== Plantilla NUEVA (bucle de negrita) ======================
test('forumdisplay_mobile_unread: hilo conocido no leido sale unread=true', () => {
  const threads = parseFixture('forumdisplay_mobile_unread.html', 'https://forocoches.com/foro/forumdisplay.php?f=4');
  const t = threads.find((x) => x.tid === '10766846');
  assert.ok(t, 'el hilo 10766846 deberia seguir en el fixture');
  assert.strictEqual(t.unread, true, `"${t.title}" deberia salir sin leer`);
});

test('forumdisplay_mobile_unread: hilo conocido ya leido sale unread=false', () => {
  const threads = parseFixture('forumdisplay_mobile_unread.html', 'https://forocoches.com/foro/forumdisplay.php?f=4');
  const t = threads.find((x) => x.tid === '10765122');
  assert.ok(t, 'el hilo 10765122 deberia seguir en el fixture');
  assert.strictEqual(t.unread, false, `"${t.title}" deberia salir leido`);
});

test('forumdisplay_mobile_unread: la pagina trae mezcla real, no todo del mismo signo', () => {
  const threads = parseFixture('forumdisplay_mobile_unread.html', 'https://forocoches.com/foro/forumdisplay.php?f=4');
  const unreadCount = threads.filter((t) => t.unread).length;
  const readCount = threads.filter((t) => !t.unread).length;
  assert.strictEqual(threads.length, 40);
  assert.strictEqual(unreadCount, 24);
  assert.strictEqual(readCount, 16);
});

// ====================== Plantilla VIEJA (icono statusicon) ======================
test('forumdisplay_old: sigue tomando la rama del icono, no la del bucle de negrita', () => {
  const threads = parseFixture('forumdisplay_old.html', 'https://forocoches.com/foro/forumdisplay.php?f=2');
  assert.strictEqual(threads.length, 40);
  // Las 40 filas del fixture llevan thread_new.gif / thread_hot_new.gif -> las 40 sin leer.
  assert.ok(threads.every((t) => t.unread === true), 'las 40 filas del fixture viejo son _new: todas deberian salir unread=true');
});

// ====================== SINTETICO — formas aceptadas por la regex de negrita ======================
// Ninguna captura en vivo trajo un titulo marcado con "bolder" ni con un peso numerico (solo
// se vio "font-weight: bold" tal cual en forumdisplay_mobile_unread.html); estas filas estan
// FABRICADAS a mano para el test, calcadas de esa misma estructura, variando solo el valor
// de `font-weight`. NO son HTML capturado de FC — de ahi el prefijo "SINTETICO" en el nombre
// de cada test, para que no se confundan con las fijaciones de arriba.
test('SINTETICO: font-weight: bolder cuenta como no leido', () => {
  const threads = parseHtml(syntheticRow('1', 'font-weight: bolder'), 'https://forocoches.com/foro/forumdisplay.php?f=4');
  const t = threads.find((x) => x.tid === '1');
  assert.ok(t, 'la fila sintetica deberia parsear');
  assert.strictEqual(t.unread, true, '"bolder" deberia marcar el hilo como no leido');
});

test('SINTETICO: font-weight: 700 (peso numerico alto) cuenta como no leido', () => {
  const threads = parseHtml(syntheticRow('2', 'font-weight: 700'), 'https://forocoches.com/foro/forumdisplay.php?f=4');
  const t = threads.find((x) => x.tid === '2');
  assert.ok(t, 'la fila sintetica deberia parsear');
  assert.strictEqual(t.unread, true, '"700" deberia marcar el hilo como no leido');
});

test('SINTETICO: font-weight: 400 (peso normal) NO cuenta como no leido', () => {
  // El caso que mas importa: si la regex alguna vez regresiona a "cualquier numero", esta
  // fila silenciosamente marcaria TODOS los hilos como no leidos (400 es el peso normal de
  // texto habitual en CSS), y ningun otro test de este archivo lo detectaria porque ninguno
  // ejercita un peso normal explicito.
  const threads = parseHtml(syntheticRow('3', 'font-weight: 400'), 'https://forocoches.com/foro/forumdisplay.php?f=4');
  const t = threads.find((x) => x.tid === '3');
  assert.ok(t, 'la fila sintetica deberia parsear');
  assert.strictEqual(t.unread, false, '"400" es peso normal, NO deberia marcar el hilo como no leido');
});
