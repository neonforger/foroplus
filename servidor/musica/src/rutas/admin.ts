// Administración: las herramientas del dueño, a mano con curl y la cabecera X-Admin (el router ya
// comprobó el secreto en tiempo constante; ninguna de estas rutas acepta un token de cuenta). Todo
// lo que cambia más de una tabla va en UN batch (una transacción): o se hace entero o no se hace.
import type { Contexto, Ruta } from "../app";
import { RE_ULID } from "../cripto";
import { cuerpoJson, error, json } from "../http";

const MAX_CUERPO = 1024;
const MAX_DENUNCIAS = 200;
const SIN_CACHE = { "Cache-Control": "no-store" };
/** Semanas que ya no se tocan: sus votos y propuestas son historia. El resto (abiertas o sin fila) sí. */
const SEMANA_ABIERTA = "semana NOT IN (SELECT id FROM semanas WHERE cerrada = 1)";

const texto = (v: unknown): v is string => typeof v === "string" && v.length > 0 && v.length <= 64;

async function ocultar(c: Contexto): Promise<Response> {
  const cuerpo = await cuerpoJson(c.req, MAX_CUERPO);
  const tipo = cuerpo?.tipo;
  const id = cuerpo?.id;
  const oculto = cuerpo?.oculto;
  if ((tipo !== "playlist" && tipo !== "cancion") || typeof id !== "string" || !RE_ULID.test(id)) return error("cuerpo", 400);
  if (typeof oculto !== "boolean") return error("cuerpo", 400);
  const tabla = tipo === "playlist" ? "playlists_pub" : "canciones";
  const r = await c.deps.db.prepare(`UPDATE ${tabla} SET oculta = ? WHERE id = ?`).bind(oculto ? 1 : 0, id).run();
  return r.meta.changes === 1 ? json({ ok: true }, 200, SIN_CACHE) : error("no_existe", 404);
}

async function banear(c: Contexto): Promise<Response> {
  const db = c.deps.db;
  const cuerpo = await cuerpoJson(c.req, MAX_CUERPO);
  const cuenta = cuerpo?.cuenta;
  const baneada = cuerpo?.baneada;
  if (!texto(cuenta) || typeof baneada !== "boolean") return error("cuerpo", 400);
  if (!baneada) {
    // Desbanear solo levanta la marca: no devuelve tokens ni votos (la cuenta vuelve a verificar y ya).
    const r = await db.prepare("UPDATE cuentas SET baneada = 0 WHERE id = ?").bind(cuenta).run();
    return r.meta.changes === 1 ? json({ ok: true }, 200, SIN_CACHE) : error("no_existe", 404);
  }
  if (!(await db.prepare("SELECT 1 AS x FROM cuentas WHERE id = ?").bind(cuenta).first())) return error("no_existe", 404);
  await db.batch([
    db.prepare("UPDATE cuentas SET baneada = 1 WHERE id = ?1").bind(cuenta),
    // Los tokens NO se borran (ver cuentaDe): una baneada sigue pudiendo borrar su cuenta (RGPD) con el suyo.
    // Lo que ya no cuenta: sus votos en semanas abiertas (el concurso de la semana en curso no puede
    // llevar votos de una cuenta baneada). Sus propuestas con votos de OTROS se quedan sin dueña (los
    // votos de los demás no se pierden, igual que al borrar la cuenta); las demás se van.
    db
      .prepare(
        `UPDATE propuestas SET cuenta = 'borrada' WHERE cuenta = ?1 AND ${SEMANA_ABIERTA} ` +
          "AND EXISTS (SELECT 1 FROM votos v WHERE v.semana = propuestas.semana AND v.cancion = propuestas.cancion AND v.cuenta <> ?1)",
      )
      .bind(cuenta),
    db.prepare(`DELETE FROM propuestas WHERE cuenta = ?1 AND ${SEMANA_ABIERTA}`).bind(cuenta),
    db.prepare(`DELETE FROM votos WHERE cuenta = ?1 AND ${SEMANA_ABIERTA}`).bind(cuenta),
  ]);
  return json({ ok: true }, 200, SIN_CACHE);
}

async function listarDenuncias(c: Contexto): Promise<Response> {
  const { results } = await c.deps.db
    .prepare(
      "SELECT d.id, d.tipo, d.objetivo, d.cuenta, d.motivo, d.creada, " +
        "p.nombre AS p_nombre, u.nombre AS p_autor, p.cuenta AS p_autor_id, s.titulo AS s_titulo, s.artista AS s_artista " +
        "FROM denuncias d " +
        "LEFT JOIN playlists_pub p ON d.tipo = 'playlist' AND p.id = d.objetivo " +
        "LEFT JOIN cuentas u ON u.id = p.cuenta " +
        "LEFT JOIN canciones s ON d.tipo IN ('cancion', 'sistema') AND s.id = d.objetivo " +
        "WHERE COALESCE(d.resuelta, 0) = 0 ORDER BY d.creada ASC, d.id ASC LIMIT ?",
    )
    .bind(MAX_DENUNCIAS)
    .all<Record<string, string | number | null>>();
  const denuncias = results.map((f) => ({
    id: f.id, tipo: f.tipo, objetivo: f.objetivo, cuenta: f.cuenta, motivo: f.motivo, creada: f.creada,
    ...(f.p_nombre !== null ? { nombre: f.p_nombre, autor: f.p_autor, autorId: f.p_autor_id } : {}),
    ...(f.s_titulo !== null ? { titulo: f.s_titulo, artista: f.s_artista } : {}),
  }));
  return json({ denuncias }, 200, SIN_CACHE);
}

async function resolver(c: Contexto): Promise<Response> {
  const cuerpo = await cuerpoJson(c.req, MAX_CUERPO);
  const id = cuerpo?.denuncia;
  if (!texto(id)) return error("cuerpo", 400);
  // Guarda cuándo se resolvió (ms): distinto de 0 = resuelta, como lo lee todo lo demás.
  const r = await c.deps.db.prepare("UPDATE denuncias SET resuelta = ? WHERE id = ?").bind(c.deps.ahora().getTime(), id).run();
  return r.meta.changes === 1 ? json({ ok: true }, 200, SIN_CACHE) : error("no_existe", 404);
}

/** Cambia `de` por `en` dentro de una lista JSON de ids, sin repetir y dejando cada id en su primera posición. */
const LISTA_FUSIONADA =
  "(SELECT json_group_array(v) FROM (SELECT v, MIN(k) AS k FROM (" +
  "SELECT j.key AS k, CASE WHEN j.value = ?1 THEN ?2 ELSE j.value END AS v FROM json_each(playlists_pub.canciones) j" +
  ") GROUP BY v ORDER BY k))";

async function fusionar(c: Contexto): Promise<Response> {
  const db = c.deps.db;
  const cuerpo = await cuerpoJson(c.req, MAX_CUERPO);
  const de = cuerpo?.de;
  const dest = cuerpo?.en;
  if (typeof de !== "string" || typeof dest !== "string" || !RE_ULID.test(de) || !RE_ULID.test(dest) || de === dest) {
    return error("cuerpo", 400);
  }
  const existen = await db
    .prepare("SELECT COUNT(*) AS n FROM canciones WHERE id IN (?1, ?2)")
    .bind(de, dest)
    .first<{ n: number }>();
  if (existen?.n !== 2) return error("no_existe", 404);
  const enlace = (fuente: string) =>
    db
      .prepare(
        "UPDATE canciones SET enlaces = json_set(enlaces, ?3, (SELECT json_extract(enlaces, ?3) FROM canciones WHERE id = ?1)) " +
          "WHERE id = ?2 AND json_extract(enlaces, ?3) IS NULL " +
          "AND (SELECT json_extract(enlaces, ?3) FROM canciones WHERE id = ?1) IS NOT NULL",
      )
      .bind(de, dest, "$." + fuente);
  await db.batch([
    // Enlaces: los de `de` que `en` no tenga pasan a `en` (si ambos tienen uno, manda el de `en`).
    enlace("youtube"),
    enlace("spotify"),
    db.prepare("UPDATE enlaces_cancion SET cancion = ?2 WHERE cancion = ?1").bind(de, dest),
    // Propuestas: si las dos se propusieron la misma semana queda UNA, la más antigua (con su cuenta).
    db
      .prepare(
        "UPDATE propuestas SET creada = (SELECT x.creada FROM propuestas x WHERE x.semana = propuestas.semana AND x.cancion = ?1), " +
          "cuenta = (SELECT x.cuenta FROM propuestas x WHERE x.semana = propuestas.semana AND x.cancion = ?1) " +
          "WHERE cancion = ?2 AND EXISTS (SELECT 1 FROM propuestas x WHERE x.semana = propuestas.semana AND x.cancion = ?1 AND x.creada < propuestas.creada)",
      )
      .bind(de, dest),
    db
      .prepare("DELETE FROM propuestas WHERE cancion = ?1 AND EXISTS (SELECT 1 FROM propuestas x WHERE x.semana = propuestas.semana AND x.cancion = ?2)")
      .bind(de, dest),
    db.prepare("UPDATE propuestas SET cancion = ?2 WHERE cancion = ?1").bind(de, dest),
    // Votos: una cuenta que votó las dos cuenta UNA vez, con su voto más antiguo.
    db
      .prepare(
        "UPDATE votos SET creado = (SELECT x.creado FROM votos x WHERE x.semana = votos.semana AND x.cuenta = votos.cuenta AND x.cancion = ?1) " +
          "WHERE cancion = ?2 AND EXISTS (SELECT 1 FROM votos x WHERE x.semana = votos.semana AND x.cuenta = votos.cuenta AND x.cancion = ?1 AND x.creado < votos.creado)",
      )
      .bind(de, dest),
    db
      .prepare("DELETE FROM votos WHERE cancion = ?1 AND EXISTS (SELECT 1 FROM votos x WHERE x.semana = votos.semana AND x.cuenta = votos.cuenta AND x.cancion = ?2)")
      .bind(de, dest),
    db.prepare("UPDATE votos SET cancion = ?2 WHERE cancion = ?1").bind(de, dest),
    // Playlists: `de` se cambia por `en` y la lista se deduplica (los tops cerrados no se reescriben).
    db
      .prepare(
        `UPDATE playlists_pub SET canciones = ${LISTA_FUSIONADA} ` +
          "WHERE EXISTS (SELECT 1 FROM json_each(playlists_pub.canciones) j WHERE j.value = ?1)",
      )
      .bind(de, dest),
    db.prepare("UPDATE denuncias SET objetivo = ?2 WHERE tipo IN ('cancion', 'sistema') AND objetivo = ?1").bind(de, dest),
    db.prepare("DELETE FROM canciones WHERE id = ?1").bind(de),
  ]);
  return json({ ok: true }, 200, SIN_CACHE);
}

export function rutasAdmin(): Ruta[] {
  return [
    ["GET", "/v1/admin/denuncias", listarDenuncias, "admin"],
    ["POST", "/v1/admin/ocultar", ocultar, "admin"],
    ["POST", "/v1/admin/banear", banear, "admin"],
    ["POST", "/v1/admin/resolver", resolver, "admin"],
    ["POST", "/v1/admin/fusionar", fusionar, "admin"],
  ];
}
