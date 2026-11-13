// El feed de canciones de 24 h: constantes y la lógica común de sus rutas (rutas/feed.ts).
import { cancionPub, type CancionPub, type FilaCancion } from "./canciones";
import { RE_ULID } from "./cripto";

/** Caracteres máximos del texto de una publicación (tras `trim`). */
export const TEXTO_MAX = 100;
/** Publicaciones por cuenta en 24 h; cuentan aunque se borren. */
export const PUBLICACIONES_DIA = 6;
/** Vida de una publicación desde `creada`. */
export const VIDA_MS = 86_400_000;
/**
 * Si la conversión a YouTube no ha terminado pasado este tiempo, la publicación se enseña igualmente
 * (con la tarjeta nativa): un fork caído no puede dejarla "publicando" para siempre.
 */
export const ESPERA_CONVERSION_MS = 8000;
/** Denuncias de cuentas distintas que ocultan una publicación sola. */
export const AUTO_OCULTAR = 3;
/** Tamaño de página del feed. */
export const POR_PAGINA = 20;

/** Una publicación tal como la ve la app. `corazon` y `mia` solo van si quien pide trae token. */
export interface ItemFeed {
  id: string;
  autor: string;
  texto: string;
  creada: number;
  caduca: number;
  corazones: number;
  corazon?: boolean;
  mia?: boolean;
  cancion: CancionPub;
}

/** La publicación más querida de una semana, guardada al caducar. */
export interface MasQuerida {
  semana: string;
  autor: string;
  texto: string;
  corazones: number;
  cancion: CancionPub;
}

/** Enlace en el texto: con esquema, con www. o un dominio suelto de los habituales. */
export const RE_ENLACE = /(https?:\/\/|www\.|\b[a-z0-9-]+\.(com|es|net|org|io|me|ly|gg|tv)\b)/i;

/**
 * Lo que sale en el feed. ?1 = ahora (ms), ?2 = ahora - ESPERA_CONVERSION_MS. Una publicación cuya
 * canción aún no se ha convertido espera ESPERA_CONVERSION_MS y sale igualmente (tarjeta nativa).
 */
const VIVA =
  // `p.creada > ?1 - VIDA_MS` dice lo mismo que `p.caduca > ?1` (caduca = creada + VIDA_MS), pero acota
  // por `creada`, que es la columna de los índices del feed (0006): así la página se lee por el índice.
  `p.caduca > ?1 AND p.creada > ?1 - ${VIDA_MS} AND p.oculta = 0 AND COALESCE(u.baneada, 0) = 0 AND COALESCE(c.oculta, 0) = 0 `;
export const VISIBLE = VIVA + "AND (c.conversion IN ('hecha', 'no_esta') OR p.creada <= ?2)";

/**
 * VISIBLE para quien lee el feed. ?4 = la cuenta que mira (null = invitado, y `p.cuenta = NULL` no casa):
 * el autor ve su propia publicación aunque aún se esté convirtiendo (si no, al publicar no la veía
 * hasta pasados los 8 s de espera).
 */
export const VISIBLE_FEED = VIVA + "AND (c.conversion IN ('hecha', 'no_esta') OR p.creada <= ?2 OR p.cuenta = ?4)";

/** ?4 = la cuenta que mira (null = invitado: `mia` y `corazon` salen nulos y no se enseñan). */
export const SELECT_FEED =
  "SELECT p.id, u.nombre AS autor, p.texto, p.creada, p.caduca, p.corazones, p.cuenta = ?4 AS mia, " +
  "EXISTS (SELECT 1 FROM corazones h WHERE h.publicacion = p.id AND h.cuenta = ?4) AS corazon, " +
  "c.id AS cancion_id, c.titulo, c.artista, c.portada, c.enlaces, c.deezer, c.spotify " +
  "FROM publicaciones p JOIN cuentas u ON u.id = p.cuenta JOIN canciones c ON c.id = p.cancion ";

export type FilaFeed = Omit<FilaCancion, "id"> & {
  cancion_id: string;
  id: string;
  autor: string;
  texto: string;
  creada: number;
  caduca: number;
  corazones: number;
  mia: number | null;
  corazon: number;
};

export function itemFeed(f: FilaFeed, conCuenta: boolean): ItemFeed {
  return {
    id: f.id,
    autor: f.autor,
    texto: f.texto,
    creada: f.creada,
    caduca: f.caduca,
    corazones: f.corazones,
    ...(conCuenta ? { corazon: !!f.corazon, mia: !!f.mia } : {}),
    // El id de la canción viaja como `cancion_id`: `id` es el de la publicación.
    cancion: cancionPub({ ...f, id: f.cancion_id }),
  };
}

/** Cursor opaco por keyset (nada de OFFSET): g = corazones (solo en "queridas"), p = creada, i = id. */
export interface Cursor {
  g?: number;
  p: number;
  i: string;
}

export function codificar(c: Cursor): string {
  return btoa(JSON.stringify(c)).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
}

export function decodificar(texto: string, conCorazones: boolean): Cursor | null {
  try {
    const v: unknown = JSON.parse(atob(texto.replace(/-/g, "+").replace(/_/g, "/")));
    if (!v || typeof v !== "object") return null;
    const { g, p, i } = v as Record<string, unknown>;
    if (typeof p !== "number" || typeof i !== "string" || !RE_ULID.test(i)) return null;
    if (conCorazones && typeof g !== "number") return null;
    return conCorazones ? { g: g as number, p, i } : { p, i };
  } catch {
    return null;
  }
}

const DIA_S = 86_400;

/** La clave del contador del tope de publicaciones (tabla `limites`) y su ventana, en segundos. */
export const claveTope = (cuentaId: string): string => "pub:" + cuentaId;
export const VENTANA_TOPE_S = DIA_S;

/**
 * Publicaciones que le quedan hoy a la cuenta: 6 menos el contador del tope en la ventana actual
 * (6 si aún no hay fila). Lee el MISMO contador que cuenta al publicar, así que incluye las borradas.
 */
export async function publicacionesRestantes(db: D1Database, cuentaId: string, ahora: Date): Promise<number> {
  const inicio = Math.floor(ahora.getTime() / 1000 / DIA_S) * DIA_S;
  const fila = await db
    .prepare("SELECT n FROM limites WHERE clave = ? AND ventana = ?")
    .bind(claveTope(cuentaId), inicio)
    .first<{ n: number }>();
  return Math.max(0, PUBLICACIONES_DIA - (fila?.n ?? 0));
}

/**
 * Corazones (dar y quitar cuentan igual) por cuenta en 24 h. Cada uno recuenta el contador de la
 * publicación en un batch: sin tope, una cuenta podría martillear dar/quitar sin coste. Nadie que use
 * la app normal llega a 300; no se anuncia en la app (solo lo ve quien lo alcanza).
 */
export const CORAZONES_DIA = 300;
export const claveCorazones = (cuentaId: string): string => "corazon:" + cuentaId;

/**
 * Quita del cartel de "la más querida" lo de una cuenta (al borrarla o banearla): por la columna
 * `cuenta` (desde 0006) o, SOLO en las filas de antes (sin `cuenta`), por el nombre del autor. Va
 * en el batch ANTES de borrar o vaciar la fila de `cuentas`, que es de donde sale el nombre.
 * El `cuenta IS NULL` de la rama por nombre es imprescindible: sin él, una cuenta cuyo nombre de FC
 * coincida con el `autor` de una fila CON dueño (p. ej. un nombre reutilizado en FC) borraría la fila
 * de OTRA cuenta — un borrado entre cuentas gobernado por el nombre, no por el token.
 */
export function borrarMasQuerida(db: D1Database, cuentaId: string): D1PreparedStatement {
  return db
    .prepare(
      "DELETE FROM mas_querida WHERE cuenta = ?1 OR " +
        "(cuenta IS NULL AND autor = (SELECT nombre FROM cuentas WHERE id = ?1 AND nombre <> ''))",
    )
    .bind(cuentaId);
}
