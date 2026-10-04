// Orquestador del self-healing: propone candidatos (healer.js) y ACEPTA el primero que
// reproduce EXACTAMENTE el comportamiento del fixture limpio (no un check estrecho).
//
// Criterio de aceptacion = equivalencia de comportamiento: el set de unidades (posts/hilos)
// que content.js oculta en el MUTANTE-curado debe ser identico al que oculta en LIMPIO.
// Si ningun candidato lo logra, o no hay candidato fiable, NO cura (devuelve null) y se
// escala a humano. Mejor no curar que curar mal.

const L = require('./lib.js');
const H = require('./healer.js');

const MIN_SCORE = 4; // umbral minimo de confianza para siquiera intentar generar selectores

// Identidad estable de una unidad de contenido: su indice (orden de documento) entre los
// elementos `anchorSel`. Estable aunque el selector objetivo este roto.
// content.js puede ocultar el propio anchor, un contenedor que lo ENVUELVE (p.ej. el <ul>
// del skin viejo) o un ancestro; mapeamos el elemento oculto a su unidad: self -> dentro -> arriba.
function unitIndexOf(el, anchorSel, doc) {
  const anchor = el.matches(anchorSel) ? el
    : (el.querySelector(anchorSel) || el.closest(anchorSel));
  if (!anchor) return null;
  return [...doc.querySelectorAll(anchorSel)].indexOf(anchor);
}

// Set de unidades que content.js ha ocultado (por indice estable).
function hiddenUnits(beforeSet, win, anchorSel) {
  const newly = L.newlyHidden(beforeSet, L.inlineHiddenSet(win.document));
  const keys = new Set();
  for (const el of newly) { const i = unitIndexOf(el, anchorSel, win.document); if (i != null && i >= 0) keys.add(i); }
  return keys;
}

function setEq(a, b) {
  if (a.size !== b.size) return false;
  for (const x of a) if (!b.has(x)) return false;
  return true;
}

// Corre content.js (con override opcional de FC_CONFIG) y devuelve el set de unidades ocultas.
function behavior(html, url, android, anchorSel, override) {
  const win = L.loadDom(html, url);
  const before = L.inlineHiddenSet(win.document);
  if (override) win.FC_CONFIG = override;
  L.execContentJs(win, android);
  return hiddenUnits(before, win, anchorSel);
}

// Intenta curar un selector. ctx: { cleanDoc, cleanHtml, mutHtml, android }
// Devuelve { selector, reason, candidates, score }. selector=null => no cura.
function heal(target, ctx) {
  const spec = { scope: target.scope, selector: target.default, pick: 'first', shape: target.shape || 'descendant' };
  const fp = H.fingerprint(ctx.cleanDoc, spec);
  const mutDoc = L.loadDom(ctx.mutHtml, target.url).document;
  const best = H.findBest(mutDoc, fp, spec);

  if (!best.el || best.score < MIN_SCORE) {
    return { selector: null, reason: `sin candidato fiable (score ${Number(best.score).toFixed(2)} < ${MIN_SCORE})`, candidates: [], score: best.score };
  }

  const candidates = H.generateSelectors(best.el, best.container, fp, spec);
  const cleanBeh = behavior(ctx.cleanHtml, target.url, ctx.android, target.anchor, null);

  for (const sel of candidates) {
    let mutBeh;
    try { mutBeh = behavior(ctx.mutHtml, target.url, ctx.android, target.anchor, { [target.key]: sel }); }
    catch { continue; } // selector invalido -> siguiente
    if (setEq(mutBeh, cleanBeh)) {
      return { selector: sel, reason: 'equivalencia de comportamiento con el limpio', candidates, score: best.score };
    }
  }
  return { selector: null, reason: 'ningun candidato reproduce el comportamiento limpio', candidates, score: best.score };
}

module.exports = { heal, behavior, setEq, hiddenUnits, unitIndexOf, MIN_SCORE };
