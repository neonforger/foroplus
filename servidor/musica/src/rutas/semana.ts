// La canción de la semana: proponer (desde un enlace de YouTube o Spotify), votar con corazones y ver
// la semana en curso y la última cerrada. Votar y proponer exigen la antigüedad de la cuenta de FC con
// la MISMA cuenta que enseña /v1/yo (`permisoVoto`) y los mismos topes (`limites.ts`): la app y el
// servidor no pueden discrepar sobre si alguien puede votar.
// El recuento de la semana en curso no sale nunca (ni a quien vota): solo el orden. Los números se
// ven al cerrar.
import type { Contexto, Deps, Ruta } from "../app";
import { cuentaDe, permisoVoto, yo, type Cuenta } from "../auth";
import { cancionDeEnlace, cancionPub, COLUMNAS_CANCION, type FilaCancion } from "../canciones";
import { RE_ULID } from "../cripto";
import { cuerpoJson, error, json } from "../http";
import { puedeMontarTarjeta, PROPUESTAS_POR_SEMANA, VOTOS_POR_SEMANA } from "../limites";
import { asegurarCierres, type CancionTop } from "../semana";
import { finDeSemana, semanaDe } from "../tiempo";

// Un enlace de Spotify con su "?si=…" no llega a 150 bytes; esto deja margen y corta cuerpos absurdos.
const MAX_CUERPO = 1024;
const SIN_CACHE = { "Cache-Control": "no-store" };

/** Las dependencias con el reloj parado: toda la petición (cierre incluido) ve la misma semana. */
function conReloj(d: Deps): { d: Deps; ahora: Date } {
  const ahora = d.ahora();
  return { d: { ...d, ahora: () => ahora }, ahora };
}

// Orden por votos sin que el recuento salga de la base (va en el ORDER BY, no en el SELECT); a
// igualdad, la que se propuso antes, y luego el id. `votada`: si la cuenta ?2 la votó (null = nadie).
const ACTUAL = `SELECT ${COLUMNAS_CANCION},
    EXISTS (SELECT 1 FROM votos v WHERE v.semana = p.semana AND v.cancion = p.cancion AND v.cuenta = ?2) AS votada
  FROM propuestas p JOIN canciones c ON c.id = p.cancion
  WHERE p.semana = ?1
  ORDER BY (SELECT COUNT(*) FROM votos v WHERE v.semana = p.semana AND v.cancion = p.cancion) DESC,
    p.creada ASC, c.id ASC`;

function leerTop(texto: string | null): CancionTop[] {
  try {
    const v: unknown = JSON.parse(texto ?? "");
    return Array.isArray(v) ? (v as CancionTop[]) : [];
  } catch {
    return [];
  }
}

async function verSemana(c: Contexto): Promise<Response> {
  // Pública, pero con token cada canción dice si esa cuenta la votó. Un token que no vale es un error
  // y no la vista pública: así la app se entera de que el suyo murió (caducado o cuenta baneada).
  let cuenta: Cuenta | null = null;
  const conToken = c.req.headers.has("Authorization");
  if (conToken) {
    const r = await cuentaDe(c.req, c.deps);
    if (r instanceof Response) return r;
    cuenta = r;
  }
  const { d, ahora } = conReloj(c.deps);
  await asegurarCierres(d);
  const semana = semanaDe(ahora);
  const [actual, anterior] = await d.db.batch<Record<string, unknown>>([
    d.db.prepare(ACTUAL).bind(semana, cuenta?.id ?? null),
    d.db.prepare("SELECT id, top, cancion_semana FROM semanas WHERE cerrada = 1 ORDER BY id DESC LIMIT 1"),
  ]);
  const canciones = (actual.results as unknown as (FilaCancion & { votada: number })[]).map((f) =>
    cuenta ? { ...cancionPub(f), votada: f.votada === 1 } : cancionPub(f),
  );
  const fa = anterior.results[0] as { id: string; top: string | null; cancion_semana: string | null } | undefined;
  let ant: { id: string; cancionSemana: CancionTop | null; top: CancionTop[] } | null = null;
  if (fa) {
    const top = leerTop(fa.top);
    ant = { id: fa.id, cancionSemana: top.find((x) => x.id === fa.cancion_semana) ?? null, top };
  }
  // Caché compartida solo sin token: con token la respuesta es de una cuenta (votada). Vary, para que
  // una caché tampoco sirva la pública a quien manda token (le faltaría votada).
  const cabeceras = conToken
    ? { "Cache-Control": "private, no-store", Vary: "Authorization" }
    : { "Cache-Control": "public, max-age=60", Vary: "Authorization" };
  return json({ actual: { id: semana, cierra: finDeSemana(semana), canciones }, anterior: ant }, 200, cabeceras);
}

async function proponer(c: Contexto): Promise<Response> {
  const cuenta = yo(c);
  const { d, ahora } = conReloj(c.deps);
  await asegurarCierres(d);
  // Antes de leer el enlace: quien no puede proponer no gasta peticiones fuera.
  if (!permisoVoto(cuenta.registro, ahora, d.diasMinVoto).puedeVotar) return error("antiguedad", 403);
  const enlace = (await cuerpoJson(c.req, MAX_CUERPO))?.enlace;
  if (typeof enlace !== "string") return error("enlace", 400);
  const r = await cancionDeEnlace(d, enlace, () =>
    puedeMontarTarjeta(d.db, cuenta.id, ahora),
  );
  if ("error" in r) return error(r.error, r.error === "limite" ? 429 : 422);

  const semana = semanaDe(ahora);
  const id = r.cancion.id;
  const [, nueva, esta, mias] = await d.db.batch<{ n?: number }>([
    // La fila de la semana nace con su primera propuesta (invariante de semana.ts).
    d.db.prepare("INSERT INTO semanas (id) VALUES (?) ON CONFLICT (id) DO NOTHING").bind(semana),
    // El tope va DENTRO del INSERT: dos propuestas a la vez de la misma cuenta no pasan de 3. Si la
    // canción ya estaba propuesta (por quien sea), el conflicto no inserta y no gasta propuesta.
    d.db
      .prepare(
        "INSERT INTO propuestas (semana, cancion, cuenta, creada) SELECT ?1, ?2, ?3, ?4 " +
          "WHERE (SELECT COUNT(*) FROM propuestas WHERE semana = ?1 AND cuenta = ?3) < ?5 " +
          "ON CONFLICT (semana, cancion) DO NOTHING",
      )
      .bind(semana, id, cuenta.id, ahora.getTime(), PROPUESTAS_POR_SEMANA),
    d.db.prepare("SELECT 1 FROM propuestas WHERE semana = ? AND cancion = ?").bind(semana, id),
    d.db.prepare("SELECT COUNT(*) AS n FROM propuestas WHERE semana = ? AND cuenta = ?").bind(semana, cuenta.id),
  ]);
  const insertada = nueva.meta.changes === 1;
  // Ni insertada ni estaba: solo puede ser el tope.
  if (!insertada && esta.results.length === 0) return error("tope", 429);
  return json(
    {
      cancion: cancionPub(r.cancion),
      yaEstaba: !insertada,
      propuestasRestantes: Math.max(0, PROPUESTAS_POR_SEMANA - (mias.results[0]?.n ?? 0)),
    },
    200,
    SIN_CACHE,
  );
}

/** POST (poner = true) y DELETE del voto. Los dos son idempotentes y solo tocan la semana en curso. */
async function votar(c: Contexto, poner: boolean): Promise<Response> {
  const cuenta = yo(c);
  const { d, ahora } = conReloj(c.deps);
  await asegurarCierres(d);
  if (!permisoVoto(cuenta.registro, ahora, d.diasMinVoto).puedeVotar) return error("antiguedad", 403);
  const cancion = (await cuerpoJson(c.req, MAX_CUERPO))?.cancion;
  if (typeof cancion !== "string" || !RE_ULID.test(cancion)) return error("cancion", 400);

  const semana = semanaDe(ahora);
  const propuesta = "EXISTS (SELECT 1 FROM propuestas WHERE semana = ?1 AND cancion = ?2)";
  // Siempre con la cuenta del token (?3): no hay forma de tocar votos de otro.
  const cambio = poner
    ? d.db
        .prepare(
          "INSERT INTO votos (semana, cancion, cuenta, creado) SELECT ?1, ?2, ?3, ?4 " +
            `WHERE ${propuesta} AND (SELECT COUNT(*) FROM votos WHERE semana = ?1 AND cuenta = ?3) < ?5 ` +
            "ON CONFLICT (semana, cancion, cuenta) DO NOTHING",
        )
        .bind(semana, cancion, cuenta.id, ahora.getTime(), VOTOS_POR_SEMANA)
    : d.db
        .prepare(`DELETE FROM votos WHERE semana = ?1 AND cancion = ?2 AND cuenta = ?3 AND ${propuesta}`)
        .bind(semana, cancion, cuenta.id);
  const [, esta, mio, mios] = await d.db.batch<{ n?: number }>([
    cambio,
    d.db.prepare("SELECT 1 FROM propuestas WHERE semana = ? AND cancion = ?").bind(semana, cancion),
    d.db.prepare("SELECT 1 FROM votos WHERE semana = ? AND cancion = ? AND cuenta = ?").bind(semana, cancion, cuenta.id),
    d.db.prepare("SELECT COUNT(*) AS n FROM votos WHERE semana = ? AND cuenta = ?").bind(semana, cuenta.id),
  ]);
  if (esta.results.length === 0) return error("no_esta", 409);
  const votada = mio.results.length > 0;
  // Propuesta, se intentó votar y no hay voto: lo frenó el tope (si ya lo tenía, votada es true).
  if (poner && !votada) return error("tope", 429);
  return json({ votada, votosRestantes: Math.max(0, VOTOS_POR_SEMANA - (mios.results[0]?.n ?? 0)) }, 200, SIN_CACHE);
}

export function rutasSemana(): Ruta[] {
  return [
    ["GET", "/v1/semana", verSemana, "publica"],
    ["POST", "/v1/semana/proponer", proponer, "cuenta"],
    ["POST", "/v1/semana/voto", (c) => votar(c, true), "cuenta"],
    ["DELETE", "/v1/semana/voto", (c) => votar(c, false), "cuenta"],
  ];
}
