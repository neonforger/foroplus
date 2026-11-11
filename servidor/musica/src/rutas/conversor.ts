// La cola de conversión de canciones a Spotify/YouTube que atiende el VPS (la app nunca habla con él, y
// él no abre puertos: es quien llama aquí con la cabecera X-Conversor). Más el resumen para el dueño.
import type { Contexto, Ruta } from "../app";
import { RE_ULID } from "../cripto";
import { cuerpoJson, error, json } from "../http";

const MAX_CUERPO = 1024;
const SIN_CACHE = { "Cache-Control": "no-store" };
const POR_TANDA = 10;
/** Una entrega sin resultado en este tiempo se da por perdida (VPS caído a medias) y se reparte otra vez. */
const EN_CURSO_PERDIDA_MS = 60_000;
/** Tras un error se espera esto antes de reintentar, y como mucho este número de intentos. */
const ERROR_ESPERA_MS = 3_600_000;
const INTENTOS_MAX = 24;
/**
 * Petición larga: 21 consultas con 20 pausas de 1 s = 20 s de espera. Cada consulta a D1 es una
 * subpetición (tope gratis: 50) y casi todo el tiempo es esperar, no CPU (tope: 10 ms).
 */
const CONSULTAS_MAX = 21;
const PAUSA_MS = 1000;
const RE_SPOTIFY = /^[A-Za-z0-9]{22}$/;
const DETALLE_MAX = 200;

/**
 * Reclama hasta 10 canciones en UNA sentencia (UPDATE ... RETURNING): dos conversores a la vez no pueden
 * llevarse la misma, porque D1 ejecuta la sentencia entera sin intercalar otra.
 */
const RECLAMAR =
  "UPDATE canciones SET conversion = 'en_curso', conversion_en = ?1, conversion_intentos = conversion_intentos + 1 " +
  "WHERE id IN (SELECT id FROM canciones WHERE conversion = 'pendiente' " +
  "OR (conversion = 'en_curso' AND conversion_en < ?1 - ?2) " +
  "OR (conversion = 'error' AND conversion_en < ?1 - ?3 AND conversion_intentos < ?4) " +
  "ORDER BY COALESCE(conversion_en, 0), id LIMIT ?5) " +
  "RETURNING id, titulo, artista, isrc";

async function pendientes(c: Contexto): Promise<Response> {
  const { db, ahora, dormir } = c.deps;
  for (let i = 0; i < CONSULTAS_MAX; i++) {
    if (i > 0) await dormir(PAUSA_MS);
    const { results } = await db
      .prepare(RECLAMAR)
      .bind(ahora().getTime(), EN_CURSO_PERDIDA_MS, ERROR_ESPERA_MS, INTENTOS_MAX, POR_TANDA)
      .all<{ id: string; titulo: string; artista: string; isrc: string | null }>();
    if (results.length) {
      return json({ canciones: results.map((f) => ({ id: f.id, titulo: f.titulo, artista: f.artista, isrc: f.isrc })) }, 200, SIN_CACHE);
    }
  }
  return json({ canciones: [] }, 200, SIN_CACHE);
}

async function resultado(c: Contexto): Promise<Response> {
  const { db, ahora } = c.deps;
  const cuerpo = await cuerpoJson(c.req, MAX_CUERPO);
  const id = cuerpo?.cancion;
  const spotify = cuerpo?.spotify;
  const motivo = cuerpo?.motivo;
  if (typeof id !== "string" || !RE_ULID.test(id)) return error("cuerpo", 400);
  // O un id de Spotify, o un motivo; nunca los dos ni ninguno.
  const conId = typeof spotify === "string";
  if (!conId && spotify !== null) return error("cuerpo", 400);
  if (conId ? !RE_SPOTIFY.test(spotify) || motivo !== null : motivo !== "no_esta" && motivo !== "error") {
    return error("cuerpo", 400);
  }
  const t = ahora().getTime();
  if (!(await db.prepare("SELECT 1 AS x FROM canciones WHERE id = ?").bind(id).first())) return error("no_existe", 404);

  if (!conId) {
    await db.prepare("UPDATE canciones SET conversion = ?, conversion_en = ? WHERE id = ?").bind(motivo, t, id).run();
    return new Response(null, { status: 204 });
  }
  const ref = "spotify:" + spotify;
  const previa = await db.prepare("SELECT cancion FROM enlaces_cancion WHERE ref = ?").bind(ref).first<{ cancion: string }>();
  if (previa && previa.cancion !== id) {
    // Ese Spotify ya es de otra canción (un duplicado): no se le quita. Esta se queda sin conversión
    // y su publicación sale igualmente con la tarjeta nativa.
    await db.prepare("UPDATE canciones SET conversion = 'no_esta', conversion_en = ? WHERE id = ?").bind(t, id).run();
    return error("ocupada", 409);
  }
  await db.batch([
    db.prepare("UPDATE canciones SET spotify = ?, conversion = 'hecha', conversion_en = ? WHERE id = ?").bind(spotify, t, id),
    db.prepare("INSERT OR IGNORE INTO enlaces_cancion (ref, cancion) VALUES (?, ?)").bind(ref, id),
  ]);
  return new Response(null, { status: 204 });
}

async function latido(c: Contexto): Promise<Response> {
  const cuerpo = await cuerpoJson(c.req, MAX_CUERPO);
  const ok = cuerpo?.ok;
  const detalle = cuerpo?.detalle;
  if (typeof ok !== "boolean" || typeof detalle !== "string") return error("cuerpo", 400);
  await c.deps.db
    .prepare("INSERT OR REPLACE INTO conversor (clave, valor, en) VALUES ('latido', ?, ?)")
    .bind(JSON.stringify({ ok, detalle: detalle.slice(0, DETALLE_MAX) }), c.deps.ahora().getTime())
    .run();
  return new Response(null, { status: 204 });
}

async function resumen(c: Contexto): Promise<Response> {
  const db = c.deps.db;
  const [l, n] = await db.batch<Record<string, unknown>>([
    db.prepare("SELECT valor, en FROM conversor WHERE clave = 'latido'"),
    db.prepare(
      "SELECT COALESCE(SUM(conversion IN ('pendiente', 'en_curso')), 0) AS pendientes, " +
        "COALESCE(SUM(conversion = 'error'), 0) AS errores FROM canciones",
    ),
  ]);
  const fila = l.results[0] as { valor: string; en: number } | undefined;
  const cuentas = n.results[0] as { pendientes: number; errores: number };
  let latido: { ok: boolean; detalle: string; en: number } | null = null;
  if (fila) {
    try {
      latido = { ...JSON.parse(fila.valor), en: fila.en };
    } catch {
      latido = null;
    }
  }
  return json({ latido, pendientes: cuentas.pendientes, errores: cuentas.errores }, 200, SIN_CACHE);
}

export function rutasConversor(): Ruta[] {
  return [
    ["GET", "/v1/conversor/pendientes", pendientes, "conversor"],
    ["POST", "/v1/conversor/resultado", resultado, "conversor"],
    ["POST", "/v1/conversor/latido", latido, "conversor"],
    ["GET", "/v1/admin/conversor", resumen, "admin"],
  ];
}
