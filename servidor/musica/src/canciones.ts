// Identidad de las canciones: un vídeo de YouTube y una pista de Spotify de la misma canción son UNA
// fila de `canciones` (misma clave), con los dos enlaces. Lo usan la semana y, después, las playlists.
// El título, el artista y la portada los pone siempre el servidor (`tarjetaDe`): nunca el cliente.
import type { Deps } from "./app";
import { nuevoId } from "./cripto";
import { refDe, type RefCancion } from "./normalizar";
import { tarjetaDe, type Tarjeta } from "./tarjeta";

/** Lo que se enseña de una canción: los enlaces canónicos de cada fuente que tiene. */
export interface CancionPub {
  id: string;
  titulo: string;
  /** null si no se pudo leer (p. ej. la página de Spotify falló al montar la tarjeta). */
  artista: string | null;
  portada: string | null;
  youtube?: string;
  spotify?: string;
  /** Id de pista de Deezer: la app pide el fragmento de audio por él (la URL firmada caduca). */
  deezer?: number;
}

export interface FilaCancion {
  id: string;
  titulo: string | null;
  artista: string | null;
  portada: string | null;
  /** JSON {"youtube"?: url, "spotify"?: url}: el PRIMER enlace que llegó de cada fuente. */
  enlaces: string;
  deezer?: number | null;
  /** Id de pista de Spotify (la columna; las filas viejas solo lo tienen dentro de `enlaces`). */
  spotify?: string | null;
}

export const COLUMNAS_CANCION = "c.id, c.titulo, c.artista, c.portada, c.enlaces, c.deezer, c.spotify";

export function cancionPub(f: FilaCancion): CancionPub {
  let e: Record<string, unknown> = {};
  try {
    const v: unknown = JSON.parse(f.enlaces);
    if (v && typeof v === "object") e = v as Record<string, unknown>;
  } catch {
    // sin enlaces legibles: la canción sale igual, sin ellos
  }
  return {
    id: f.id,
    titulo: f.titulo ?? "",
    artista: f.artista,
    portada: f.portada,
    ...(typeof e.youtube === "string" ? { youtube: e.youtube } : {}),
    ...(f.spotify
      ? { spotify: `https://open.spotify.com/track/${f.spotify}` }
      : typeof e.spotify === "string" ? { spotify: e.spotify } : {}),
    ...(typeof f.deezer === "number" ? { deezer: f.deezer } : {}),
  };
}

/** "youtube:ID" / "spotify:ID" / "deezer:ID": la clave de `enlaces_cancion`, la misma venga el enlace como venga. */
export function claveRef(ref: RefCancion): string {
  return `${ref.fuente}:${ref.id}`;
}

const POR_REF = `SELECT ${COLUMNAS_CANCION} FROM enlaces_cancion e JOIN canciones c ON c.id = e.cancion WHERE e.ref = ?`;

/** La canción de un enlace ya visto, sin salir a la red. */
export function cancionPorRef(db: D1Database, ref: RefCancion): Promise<FilaCancion | null> {
  return db.prepare(POR_REF).bind(claveRef(ref)).first<FilaCancion>();
}

/**
 * Guarda la canción de una tarjeta y devuelve su fila. Si ya hay una con esa clave (la misma canción
 * por la otra fuente, u otro vídeo de la misma), se le cuelga este enlace en vez de crear otra.
 * Todo en un batch (una transacción) y sin "leer y luego escribir": dos propuestas a la vez de la
 * misma canción nueva chocan en los UNIQUE (`clave`, `ref`), una no inserta nada y las dos acaban
 * leyendo la misma fila.
 */
/** Lo que una tarjeta aporta además de lo común: sin ello la fila queda como siempre (conversion 'hecha'). */
export interface ExtrasCancion {
  deezer?: number;
  isrc?: string | null;
  /** Id (no URL) de la pista de Spotify, si ya se conoce. */
  spotify?: string;
  conversion?: "pendiente" | "hecha";
}

export async function guardarCancion(d: Deps, t: Tarjeta & ExtrasCancion): Promise<FilaCancion> {
  const ref = claveRef(t.ref);
  const camino = "$." + t.ref.fuente;
  const r = await d.db.batch<FilaCancion>([
    d.db
      .prepare(
        "INSERT INTO canciones (id, clave, titulo, artista, portada, isrc, enlaces, deezer, spotify, conversion) " +
          "VALUES (?1, ?2, ?3, ?4, ?5, ?8, json_object(?6, ?7), ?9, ?10, ?11) ON CONFLICT (clave) DO NOTHING",
      )
      .bind(
        nuevoId(d.ahora()), t.clave, t.titulo, t.artista, t.portada, t.ref.fuente, t.enlace,
        t.isrc ?? null, t.deezer ?? null, t.spotify ?? null, t.conversion ?? "hecha",
      ),
    // Una canción que ya estaba (llegó por otra fuente) aprende su id de Deezer y su ISRC, pero NUNCA
    // cambia spotify ni conversion: si ya está convertida, no se reconvierte.
    d.db
      .prepare("UPDATE canciones SET deezer = COALESCE(deezer, ?1), isrc = COALESCE(isrc, ?2) WHERE clave = ?3 AND (?1 IS NOT NULL OR ?2 IS NOT NULL)")
      .bind(t.deezer ?? null, t.isrc ?? null, t.clave),
    // Si la canción ya existía sin enlace de esta fuente, este pasa a ser el suyo. Si ya tenía uno
    // (otro vídeo de la misma canción), se queda el primero: el nuevo solo entra en enlaces_cancion.
    d.db
      .prepare("UPDATE canciones SET enlaces = json_set(enlaces, ?1, ?2) WHERE clave = ?3 AND json_extract(enlaces, ?1) IS NULL")
      .bind(camino, t.enlace, t.clave),
    d.db
      .prepare("INSERT INTO enlaces_cancion (ref, cancion) SELECT ?1, id FROM canciones WHERE clave = ?2 ON CONFLICT (ref) DO NOTHING")
      .bind(ref, t.clave),
    // Por la ref y no por la clave: si otra petición enganchó antes esta ref, manda la suya.
    d.db.prepare(POR_REF).bind(ref),
  ]);
  const fila = r[4].results[0];
  if (!fila) throw new Error("canción guardada sin fila: " + ref);
  return fila;
}

export type ResultadoEnlace =
  | { cancion: FilaCancion }
  | { error: "no_es_cancion" | "no_encontrada" | "limite" };

/**
 * La canción de un enlace: primero por su ref (sin red); si no se conoce, se monta la tarjeta (≤ 3
 * subpeticiones) y se guarda. `antesDeLaRed` decide si se puede salir a la red (el límite de quien
 * pide): false = "limite", sin ninguna petición fuera.
 */
export async function cancionDeEnlace(
  d: Deps, enlace: string, antesDeLaRed: () => Promise<boolean>,
): Promise<ResultadoEnlace> {
  const ref = refDe(enlace);
  if (!ref) return { error: "no_es_cancion" };
  const conocida = await cancionPorRef(d.db, ref);
  if (conocida) return { cancion: conocida };
  if (!(await antesDeLaRed())) return { error: "limite" };
  const t = await tarjetaDe(ref, d.red);
  if (!t) return { error: "no_encontrada" };
  return { cancion: await guardarCancion(d, t) };
}

/**
 * La canción de una publicación del feed: por un id de Deezer (lo normal: la app busca ahí) o por
 * un enlace. Sin red si la ref ya se conoce; si no, `antesDeLaRed` decide si se puede salir (false =
 * "limite") y se monta la tarjeta. Una canción NUEVA queda `conversion = 'pendiente'` (el VPS le
 * busca su Spotify), salvo que venga de un enlace de Spotify: entonces ya tiene su id y queda
 * 'hecha'. Una existente (misma clave) se reutiliza tal cual.
 */
export async function cancionDeOrigen(
  d: Deps, origen: { deezer: number } | { enlace: string }, antesDeLaRed: () => Promise<boolean>,
): Promise<ResultadoEnlace> {
  const ref: RefCancion | null =
    "deezer" in origen ? { fuente: "deezer", id: String(origen.deezer) } : refDe(origen.enlace);
  if (!ref) return { error: "no_es_cancion" };
  const conocida = await cancionPorRef(d.db, ref);
  if (conocida) return { cancion: conocida };
  if (!(await antesDeLaRed())) return { error: "limite" };
  const t = await tarjetaDe(ref, d.red);
  if (!t) return { error: "no_encontrada" };
  const extras: ExtrasCancion =
    ref.fuente === "spotify" ? { spotify: ref.id, conversion: "hecha" } : { conversion: "pendiente" };
  const fila = await guardarCancion(d, { ...t, ...extras });
  if (ref.fuente !== "spotify") return { cancion: fila };
  // La canción ya existía (llegó por Deezer o por otro enlace) y aún esperaba al conversor, o no se
  // le encontró Spotify: este enlace trae su id de Spotify de primera mano, así que ya no hay nada
  // que convertir. guardarCancion nunca toca spotify/conversion de una fila existente: se hace aquí.
  await d.db
    .prepare("UPDATE canciones SET spotify = COALESCE(spotify, ?1), conversion = 'hecha' WHERE id = ?2 AND conversion <> 'hecha'")
    .bind(ref.id, fila.id)
    .run();
  return { cancion: { ...fila, spotify: fila.spotify ?? ref.id } };
}
