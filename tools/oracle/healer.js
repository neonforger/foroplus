// Self-healing DETERMINISTA de selectores (sin LLM). Scope-aware.
//
//   fingerprint(limpio, spec)     -> rasgos del elemento objetivo TAL Y COMO lo usa content.js
//   findBest(roto, fp, spec)      -> el elemento del DOM roto mas parecido, DENTRO del scope
//   generateSelectors(el, cont)   -> selectores candidatos RELATIVOS al contenedor (ROBULA+-lite)
//
// "spec" describe COMO usa content.js el selector:
//   { scope: 'li.postbit'|null, selector: '<default>', pick: 'first'|'all' }
// scope-aware = clave: postAuthorNew se usa como bit.querySelector(...) por cada li.postbit;
// buscar a nivel de documento contamina el fingerprint (coge el autor del h2 de cabecera,
// fuera del li.postbit, y enlaces del submenu).
//
// Quien ACEPTA un candidato es la equivalencia de comportamiento (ver healing.js): el healer
// solo propone, ordenado de mas robusto a menos.

const tagOf = (el) => el.tagName.toLowerCase();
const classesOf = (el) => new Set([...el.classList]);
const hasText = (el) => (el.textContent || '').trim().length > 0;
const hasImg = (el) => !!el.querySelector('img');

function ancestors(el, k = 6) {
  const out = []; let p = el.parentElement;
  while (p && p.tagName !== 'BODY' && out.length < k) { out.push(p); p = p.parentElement; }
  return out;
}
function ancestorsUpTo(el, container) {
  const out = []; let p = el.parentElement;
  while (p && p !== container && p.tagName !== 'BODY') { out.push(p); p = p.parentElement; }
  return out;
}

// Needle estable de un href, DERIVADO del propio enlace (no una lista fija): tolera cambios
// de esquema (member.php -> members/). Prefiere el fichero .php; si no, el primer segmento
// con letras.
function stableNeedle(href) {
  if (!href) return null;
  const h = href.replace(/^https?:\/\/[^/]+/i, '');
  const php = h.match(/[a-z0-9_]+\.php/i);
  if (php) return php[0];
  const seg = h.replace(/^\//, '').split(/[/?#]/).find((s) => /[a-z]{3,}/i.test(s));
  return seg || null;
}
const hrefNeedle = (el) => (el.getAttribute ? stableNeedle(el.getAttribute('href')) : null);

function jaccard(a, b) {
  if (!a.size && !b.size) return 1;
  const inter = [...a].filter((x) => b.has(x)).length;
  return new Set([...a, ...b]).size ? inter / new Set([...a, ...b]).size : 0;
}
function mode(arr) {
  const m = {}; let best = null, bc = -1;
  for (const x of arr) { m[x] = (m[x] || 0) + 1; if (m[x] > bc) { bc = m[x]; best = x; } }
  return best;
}
function ancestorSignals(el) {
  const anc = ancestors(el);
  const tags = new Set(anc.map(tagOf));
  const classes = new Set();
  for (const a of anc) for (const c of a.classList) classes.add(c);
  return { tags, classes };
}

// Elementos objetivo TAL Y COMO los usa content.js (scope + forma de uso).
//   shape 'descendant' -> container.querySelector(sel)  (autor dentro del post)
//   shape 'ancestor'   -> container.closest(sel)        (wrapper que envuelve el post)
function targetsInScope(doc, spec) {
  const { scope, selector, pick = 'first', shape = 'descendant' } = spec;
  const containers = scope ? [...doc.querySelectorAll(scope)] : [doc];
  const out = [];
  for (const c of containers) {
    if (shape === 'ancestor') { const t = c.closest(selector); if (t) out.push(t); }
    else if (pick === 'all') out.push(...c.querySelectorAll(selector));
    else { const e = c.querySelector(selector); if (e) out.push(e); }
  }
  return out;
}

// --- Fingerprint: rasgos comunes de los targets en un DOM LIMPIO, respetando el scope ---
function fingerprint(doc, spec) {
  const els = targetsInScope(doc, spec);
  if (!els.length) throw new Error('el selector no matchea en limpio (scope): ' + spec.selector);
  let classInter = null, ancTagInter = null, ancClassInter = null;
  for (const e of els) {
    const c = classesOf(e);
    classInter = classInter ? new Set([...classInter].filter((x) => c.has(x))) : c;
    const sig = ancestorSignals(e);
    ancTagInter = ancTagInter ? new Set([...ancTagInter].filter((x) => sig.tags.has(x))) : sig.tags;
    ancClassInter = ancClassInter ? new Set([...ancClassInter].filter((x) => sig.classes.has(x))) : sig.classes;
  }
  return {
    spec,
    tag: mode(els.map(tagOf)),
    parentTag: mode(els.map((e) => (e.parentElement ? tagOf(e.parentElement) : ''))),
    needle: mode(els.map((e) => hrefNeedle(e) || '')),
    hasText: els.every(hasText),
    hasImg: els.every(hasImg),
    classes: classInter || new Set(),
    ancestorTags: ancTagInter || new Set(),
    ancestorClasses: ancClassInter || new Set(),
    count: els.length,
  };
}

// --- Scoring de similitud (no penaliza needle distinto: tolera cambio de esquema) ---
function scoreEl(el, fp) {
  let s = 0;
  if (tagOf(el) === fp.tag) s += 2;
  const n = hrefNeedle(el);
  if (n && fp.needle && n === fp.needle) s += 3;
  else if (n && fp.needle) s += 1;            // es un enlace, aunque cambie el esquema
  if (el.parentElement && tagOf(el.parentElement) === fp.parentTag) s += 2;
  if (hasText(el) === fp.hasText) s += 1;
  if (hasImg(el) === fp.hasImg) s += 1;
  s += jaccard(classesOf(el), fp.classes);
  const sig = ancestorSignals(el);
  s += 2 * jaccard(sig.tags, fp.ancestorTags);
  s += jaccard(sig.classes, fp.ancestorClasses);
  return s;
}

// Busca el mejor candidato respetando la forma de uso: dentro del scope (descendant) o
// entre los ANCESTROS de cada unidad del scope (ancestor).
function findBest(doc, fp, spec) {
  const shape = spec.shape || 'descendant';
  const containers = spec.scope ? [...doc.querySelectorAll(spec.scope)] : [doc];
  let best = null, bestC = null, bs = -Infinity;
  for (const c of containers) {
    let cands;
    if (shape === 'ancestor') {
      cands = []; let p = c.parentElement, depth = 0;
      while (p && p.tagName !== 'BODY' && depth < 8) { cands.push(p); depth++; p = p.parentElement; }
    } else {
      cands = c.querySelectorAll('*');
    }
    for (const el of cands) {
      const sc = scoreEl(el, fp);
      if (sc > bs) { bs = sc; best = el; bestC = c; }
    }
  }
  return { el: best, container: bestC, score: bs };
}

// --- ROBULA+-lite: candidatos RELATIVOS al contenedor, mas robusto primero ---
const stableClasses = (el) => [...el.classList].filter((c) => !/\d/.test(c));

function generateSelectors(el, container, fp, spec) {
  const shape = (spec && spec.shape) || 'descendant';
  const needle = hrefNeedle(el) || fp.needle;
  const base = `${tagOf(el)}${needle ? `[href*="${needle}"]` : ''}`;
  const cands = [];

  // Forma 'ancestor' (se usa como X.closest(sel)): basta con matchear al propio wrapper.
  if (shape === 'ancestor') {
    for (const c of stableClasses(el)) cands.push(`${tagOf(el)}.${c}`);
    for (const c of stableClasses(el)) cands.push(`.${c}`);
    const m = (el.id || '').match(/^([a-zA-Z][a-zA-Z_-]*?)[-_]?\d/);
    if (m) cands.push(`[id^="${m[1]}"]`);
    cands.push(tagOf(el));
    return [...new Set(cands)];
  }

  // 1) por tag del padre (estable, sin ids) -- salvo que el padre sea el contenedor
  if (el.parentElement && el.parentElement !== container) cands.push(`${tagOf(el.parentElement)} ${base}`);
  // 2) clase estable propia
  for (const c of stableClasses(el)) cands.push(`${base}.${c}`);
  // 3) clase estable de un ancestro DENTRO del contenedor (cercano primero)
  for (const a of ancestorsUpTo(el, container)) for (const c of stableClasses(a)) cands.push(`.${c} ${base}`);
  // 4) prefijo de id de un ancestro dentro del contenedor (deprioritizado: ids dinamicos)
  for (const a of ancestorsUpTo(el, container)) {
    const m = (a.id || '').match(/^([a-zA-Z][a-zA-Z_-]*?)[-_]?\d/);
    if (m) cands.push(`[id^="${m[1]}"] ${base}`);
  }
  // 5) base sola
  cands.push(base);
  return [...new Set(cands)];
}

module.exports = {
  fingerprint, findBest, generateSelectors, scoreEl, hrefNeedle, stableNeedle, targetsInScope,
};
