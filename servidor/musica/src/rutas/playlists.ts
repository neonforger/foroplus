// Playlists públicas, denuncias y borrar la cuenta. Quien publica lo hace bajo su nombre de FC.
// Autorización: la cuenta sale SIEMPRE del token y entra en cada consulta de datos propios
// (`cuenta = :yo`); una playlist ajena responde 404, igual que una que no existe, para no confirmar
// que existe (notas de seguridad del Bloque C: IDOR por construcción).
// Las canciones las pone el servidor (`cancionDeEnlace`, la misma identidad que la semana): lo que
// manda la app son enlaces, nunca títulos. Máximo 10 enlaces por petición: con 3 subpeticiones por
// canción nueva son 30 de las 50 que da el plan gratuito.
import type { Contexto, Deps, OpcionesApp, Ruta } from "../app";
import { yo, type Cuenta } from "../auth";
import { cancionDeEnlace, cancionPub, COLUMNAS_CANCION, type FilaCancion } from "../canciones";
import { nuevoId, RE_ULID } from "../cripto";
import { cuerpoJson, error, json } from "../http";
import { permitido, puedeMontarTarjeta } from "../limites";
import { nombreAceptable } from "../palabras";

const MAX_CUERPO = 4096;
const MAX_ENLACES = 10;
const MAX_NOMBRE = 60;
const MAX_CANCIONES = 200;
const MAX_PLAYLISTS = 50;
const POR_PAGINA = 20;
const MAX_MOTIVO = 300;
const DENUNCIAS_POR_DIA = 5;
const DIA_S = 86_400;
const SIN_CACHE = { "Cache-Control": "no-store" };
/** La playlist es de todos mientras no esté oculta ni sea de una cuenta baneada. */
const VISIBLE = "COALESCE(p.oculta, 0) = 0 AND COALESCE(u.baneada, 0) = 0";

function idsDe(texto: string | null): string[] {
  try {
    const v: unknown = JSON.parse(texto ?? "");
    return Array.isArray(v) ? v.filter((x): x is string => typeof x === "string") : [];
  } catch {
    return [];
  }
}

/** Mismas reglas que la app: sin espacios de más y 1-60 caracteres; null = no vale. */
function nombreLimpio(n: unknown): string | null {
  if (typeof n !== "string") return null;
  const limpio = n.trim().replace(/\s+/g, " ");
  const largo = [...limpio].length;
  return largo >= 1 && largo <= MAX_NOMBRE && nombreAceptable(limpio) ? limpio : null;
}

/** Los enlaces del cuerpo: lista de textos de como mucho 10. Response = error ya montado. */
function enlacesDe(cuerpo: Record<string, unknown> | null): string[] | Response {
  const e = cuerpo?.enlaces;
  if (!Array.isArray(e) || e.some((x) => typeof x !== "string")) return error("enlaces", 400);
  if (e.length > MAX_ENLACES) return error("demasiados", 422);
  return e as string[];
}

/**
 * Los ids de canción de una tanda de enlaces, en orden y sin repetir. Los que no son canción o cuya
 * tarjeta no se pudo montar van a `fallidas`. El presupuesto de enlaces nuevos por hora es el mismo
 * que el de proponer; si se agota, 429 y la playlist no se toca.
 */
async function resolver(
  d: Deps, cuenta: Cuenta, ahora: Date, enlaces: string[], tarjetasHora?: number,
): Promise<{ ids: string[]; fallidas: string[] } | Response> {
  const ids: string[] = [];
  const fallidas: string[] = [];
  for (const e of enlaces) {
    const r = await cancionDeEnlace(d, e, () => puedeMontarTarjeta(d.db, cuenta.id, ahora, tarjetasHora));
    if ("error" in r) {
      if (r.error === "limite") return error("limite", 429);
      fallidas.push(e);
    } else if (!ids.includes(r.cancion.id)) {
      ids.push(r.cancion.id);
    }
  }
  return { ids, fallidas };
}

async function publicar(c: Contexto, tarjetasHora?: number): Promise<Response> {
  const cuenta = yo(c);
  const d = c.deps;
  const cuerpo = await cuerpoJson(c.req, MAX_CUERPO);
  if (!cuerpo) return error("cuerpo", 400);
  const nombre = nombreLimpio(cuerpo.nombre);
  if (nombre === null) return error("nombre", 422);
  const enlaces = enlacesDe(cuerpo);
  if (enlaces instanceof Response) return enlaces;
  // Antes de salir a la red: quien ya tiene el tope no gasta peticiones fuera.
  const n = await d.db
    .prepare("SELECT COUNT(*) AS n FROM playlists_pub WHERE cuenta = ?")
    .bind(cuenta.id)
    .first<{ n: number }>();
  if ((n?.n ?? 0) >= MAX_PLAYLISTS) return error("tope", 429);
  const ahora = d.ahora();
  const r = await resolver(d, cuenta, ahora, enlaces, tarjetasHora);
  if (r instanceof Response) return r;
  const id = nuevoId(ahora);
  // El tope va dentro del INSERT: dos publicaciones a la vez no pasan de 50.
  const res = await d.db
    .prepare(
      "INSERT INTO playlists_pub (id, cuenta, nombre, canciones, publicada, guardados, oculta) " +
        "SELECT ?1, ?2, ?3, ?4, ?5, 0, 0 WHERE (SELECT COUNT(*) FROM playlists_pub WHERE cuenta = ?2) < ?6",
    )
    .bind(id, cuenta.id, nombre, JSON.stringify(r.ids), ahora.getTime(), MAX_PLAYLISTS)
    .run();
  if (res.meta.changes !== 1) return error("tope", 429);
  return json({ id, canciones: r.ids.length, fallidas: r.fallidas }, 200, SIN_CACHE);
}

async function ampliar(c: Contexto, tarjetasHora?: number): Promise<Response> {
  const cuenta = yo(c);
  const d = c.deps;
  const id = c.params.id;
  const propia = () =>
    d.db
      .prepare("SELECT canciones FROM playlists_pub WHERE id = ? AND cuenta = ?")
      .bind(id, cuenta.id)
      .first<{ canciones: string }>();
  if (!(await propia())) return error("no_existe", 404);
  const enlaces = enlacesDe(await cuerpoJson(c.req, MAX_CUERPO));
  if (enlaces instanceof Response) return enlaces;
  const r = await resolver(d, cuenta, d.ahora(), enlaces, tarjetasHora);
  if (r instanceof Response) return r;
  // Leer, mezclar y escribir solo si nadie la tocó entre medias (compara el texto viejo): dos
  // ampliaciones a la vez no se pisan; la que pierde relee y repite.
  for (let intento = 0; intento < 3; intento++) {
    const fila = await propia();
    if (!fila) return error("no_existe", 404);
    const actuales = idsDe(fila.canciones);
    const nuevas = r.ids.filter((x) => !actuales.includes(x));
    if (actuales.length + nuevas.length > MAX_CANCIONES) return error("llena", 422);
    if (nuevas.length === 0) return json({ canciones: actuales.length, fallidas: r.fallidas }, 200, SIN_CACHE);
    const res = await d.db
      .prepare("UPDATE playlists_pub SET canciones = ?1 WHERE id = ?2 AND cuenta = ?3 AND canciones = ?4")
      .bind(JSON.stringify([...actuales, ...nuevas]), id, cuenta.id, fila.canciones)
      .run();
    if (res.meta.changes === 1) {
      return json({ canciones: actuales.length + nuevas.length, fallidas: r.fallidas }, 200, SIN_CACHE);
    }
  }
  return error("ocupada", 409);
}

async function borrar(c: Contexto): Promise<Response> {
  const cuenta = yo(c);
  const db = c.deps.db;
  const id = c.params.id;
  const suya = "EXISTS (SELECT 1 FROM playlists_pub WHERE id = ?1 AND cuenta = ?2)";
  // Lo que cuelga de la playlist va antes que ella, y solo si es de esta cuenta.
  const r = await db.batch([
    db.prepare(`DELETE FROM guardados WHERE playlist = ?1 AND ${suya}`).bind(id, cuenta.id),
    db.prepare(`DELETE FROM denuncias WHERE tipo = 'playlist' AND objetivo = ?1 AND ${suya}`).bind(id, cuenta.id),
    db.prepare("DELETE FROM playlists_pub WHERE id = ? AND cuenta = ?").bind(id, cuenta.id),
  ]);
  return r[2].meta.changes === 1 ? json({ ok: true }, 200, SIN_CACHE) : error("no_existe", 404);
}

// ---- Lectura pública ----

interface Cursor {
  /** Solo en "guardadas": los guardados de la última ficha vista. */
  g?: number;
  p: number;
  i: string;
}

function codificar(c: Cursor): string {
  return btoa(JSON.stringify(c)).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
}

function decodificar(texto: string, conGuardados: boolean): Cursor | null {
  try {
    const v: unknown = JSON.parse(atob(texto.replace(/-/g, "+").replace(/_/g, "/")));
    if (!v || typeof v !== "object") return null;
    const { g, p, i } = v as Record<string, unknown>;
    if (typeof p !== "number" || typeof i !== "string" || !RE_ULID.test(i)) return null;
    if (conGuardados && typeof g !== "number") return null;
    return conGuardados ? { g: g as number, p, i } : { p, i };
  } catch {
    return null;
  }
}

interface FilaLista {
  id: string;
  nombre: string;
  autor: string;
  n: number;
  guardados: number;
  publicada: number;
  portada: string | null;
}

const LISTA =
  "SELECT p.id, p.nombre, u.nombre AS autor, json_array_length(p.canciones) AS n, " +
  "COALESCE(p.guardados, 0) AS guardados, p.publicada, c.portada " +
  "FROM playlists_pub p JOIN cuentas u ON u.id = p.cuenta " +
  "LEFT JOIN canciones c ON c.id = json_extract(p.canciones, '$[0]') " +
  `WHERE ${VISIBLE} `;

async function listar(c: Contexto): Promise<Response> {
  const orden = c.url.searchParams.get("orden") ?? "recientes";
  if (orden !== "recientes" && orden !== "guardadas") return error("orden", 400);
  const texto = c.url.searchParams.get("cursor");
  const cursor = texto ? decodificar(texto, orden === "guardadas") : null;
  if (texto && !cursor) return error("cursor", 400);
  // Keyset: nada de OFFSET. Las filas nuevas entre páginas no repiten ni saltan fichas. En
  // "guardadas", un guardado entre páginas puede mover una ficha ya vista (el orden cambia).
  const db = c.deps.db;
  let sentencia: D1PreparedStatement;
  if (orden === "recientes") {
    sentencia = db
      .prepare(
        LISTA +
          "AND (?1 IS NULL OR p.publicada < ?1 OR (p.publicada = ?1 AND p.id < ?2)) " +
          "ORDER BY p.publicada DESC, p.id DESC LIMIT ?3",
      )
      .bind(cursor?.p ?? null, cursor?.i ?? null, POR_PAGINA + 1);
  } else {
    sentencia = db
      .prepare(
        LISTA +
          "AND (?1 IS NULL OR COALESCE(p.guardados, 0) < ?1 OR (COALESCE(p.guardados, 0) = ?1 AND " +
          "(p.publicada < ?2 OR (p.publicada = ?2 AND p.id < ?3)))) " +
          "ORDER BY COALESCE(p.guardados, 0) DESC, p.publicada DESC, p.id DESC LIMIT ?4",
      )
      .bind(cursor?.g ?? null, cursor?.p ?? null, cursor?.i ?? null, POR_PAGINA + 1);
  }
  const filas = (await sentencia.all<FilaLista>()).results;
  const hay = filas.length > POR_PAGINA;
  const pagina = hay ? filas.slice(0, POR_PAGINA) : filas;
  const ultima = pagina[pagina.length - 1];
  const siguiente =
    hay && ultima
      ? codificar(
          orden === "guardadas"
            ? { g: ultima.guardados, p: ultima.publicada, i: ultima.id }
            : { p: ultima.publicada, i: ultima.id },
        )
      : null;
  return json(
    {
      playlists: pagina.map((f) => ({
        id: f.id, nombre: f.nombre, autor: f.autor, canciones: f.n, guardados: f.guardados, portada: f.portada,
      })),
      siguiente,
    },
    200,
    { "Cache-Control": "public, max-age=60" },
  );
}

async function detalle(c: Contexto): Promise<Response> {
  const db = c.deps.db;
  const id = c.params.id;
  const [p, canciones] = await db.batch<Record<string, unknown>>([
    db
      .prepare(
        "SELECT p.id, p.nombre, u.nombre AS autor, COALESCE(p.guardados, 0) AS guardados " +
          `FROM playlists_pub p JOIN cuentas u ON u.id = p.cuenta WHERE p.id = ? AND ${VISIBLE}`,
      )
      .bind(id),
    // json_each y no IN (...): D1 admite 100 parámetros y una playlist llega a 200 canciones.
    db
      .prepare(
        `SELECT ${COLUMNAS_CANCION} FROM playlists_pub p JOIN cuentas u ON u.id = p.cuenta, ` +
          "json_each(p.canciones) j JOIN canciones c ON c.id = j.value " +
          `WHERE p.id = ? AND ${VISIBLE} AND COALESCE(c.oculta, 0) = 0 ORDER BY j.key`,
      )
      .bind(id),
  ]);
  const f = p.results[0] as { id: string; nombre: string; autor: string; guardados: number } | undefined;
  if (!f) return error("no_existe", 404);
  return json({ ...f, canciones: (canciones.results as unknown as FilaCancion[]).map(cancionPub) }, 200, {
    "Cache-Control": "public, max-age=60",
  });
}

async function guardar(c: Contexto): Promise<Response> {
  const cuenta = yo(c);
  const db = c.deps.db;
  const id = c.params.id;
  const p = await db
    .prepare(`SELECT p.cuenta FROM playlists_pub p JOIN cuentas u ON u.id = p.cuenta WHERE p.id = ? AND ${VISIBLE}`)
    .bind(id)
    .first<{ cuenta: string }>();
  if (!p) return error("no_existe", 404);
  // La propia se puede "guardar" pero no cuenta: no se infla el contador con la dueña.
  const propia = p.cuenta === cuenta.id;
  const r = await db.batch<{ guardados: number }>([
    propia
      ? db.prepare("SELECT 1")
      : db
          .prepare("INSERT INTO guardados (playlist, cuenta) VALUES (?, ?) ON CONFLICT (playlist, cuenta) DO NOTHING")
          .bind(id, cuenta.id),
    // Se recuenta en vez de sumar uno: dos guardados a la vez dan el mismo número exacto.
    db.prepare("UPDATE playlists_pub SET guardados = (SELECT COUNT(*) FROM guardados WHERE playlist = ?1) WHERE id = ?1").bind(id),
    db.prepare("SELECT COALESCE(guardados, 0) AS guardados FROM playlists_pub WHERE id = ?").bind(id),
  ]);
  return json({ guardados: r[2].results[0]?.guardados ?? 0 }, 200, SIN_CACHE);
}

// ---- Denuncias ----

async function denunciar(c: Contexto): Promise<Response> {
  const cuenta = yo(c);
  const d = c.deps;
  const cuerpo = await cuerpoJson(c.req, MAX_CUERPO);
  const tipo = cuerpo?.tipo;
  const objetivo = cuerpo?.objetivo;
  const motivo = cuerpo?.motivo;
  if (tipo !== "playlist" && tipo !== "cancion") return error("tipo", 400);
  if (typeof objetivo !== "string") return error("objetivo", 400);
  if (typeof motivo !== "string" || [...motivo].length > MAX_MOTIVO) return error("motivo", 400);
  const existe =
    tipo === "playlist"
      ? await d.db
          .prepare(`SELECT 1 AS x FROM playlists_pub p JOIN cuentas u ON u.id = p.cuenta WHERE p.id = ? AND ${VISIBLE}`)
          .bind(objetivo)
          .first()
      : await d.db.prepare("SELECT 1 AS x FROM canciones WHERE id = ?").bind(objetivo).first();
  if (!existe) return error("no_existe", 404);
  // Repetir la misma denuncia no crea otra fila ni gasta cupo.
  const repetida = await d.db
    .prepare("SELECT 1 AS x FROM denuncias WHERE tipo = ? AND objetivo = ? AND cuenta = ?")
    .bind(tipo, objetivo, cuenta.id)
    .first();
  if (repetida) return json({ ok: true }, 200, SIN_CACHE);
  const ahora = d.ahora();
  if (!(await permitido(d.db, "denuncia:" + cuenta.id, DIA_S, DENUNCIAS_POR_DIA, ahora))) return error("limite", 429);
  await d.db
    .prepare(
      "INSERT INTO denuncias (id, tipo, objetivo, cuenta, motivo, creada, resuelta) " +
        "SELECT ?1, ?2, ?3, ?4, ?5, ?6, 0 WHERE NOT EXISTS " +
        "(SELECT 1 FROM denuncias WHERE tipo = ?2 AND objetivo = ?3 AND cuenta = ?4)",
    )
    .bind(nuevoId(ahora), tipo, objetivo, cuenta.id, motivo.trim(), ahora.getTime())
    .run();
  return json({ ok: true }, 200, SIN_CACHE);
}

// ---- Borrar la cuenta ----

async function borrarCuenta(c: Contexto): Promise<Response> {
  const cuenta = yo(c);
  const db = c.deps.db;
  const yoId = cuenta.id;
  const suyas = "SELECT id FROM playlists_pub WHERE cuenta = ?1";
  // Todo en UN batch (una transacción): o se va todo o no se va nada. El orden importa: lo que
  // depende de la cuenta o de sus playlists se borra antes que ellas.
  await db.batch([
    // Sus guardados en playlists de otros: el contador de esas playlists baja.
    db
      .prepare(
        "UPDATE playlists_pub SET guardados = (SELECT COUNT(*) FROM guardados g WHERE g.playlist = playlists_pub.id AND g.cuenta <> ?1) " +
          "WHERE id IN (SELECT playlist FROM guardados WHERE cuenta = ?1)",
      )
      .bind(yoId),
    db.prepare("DELETE FROM guardados WHERE cuenta = ?1").bind(yoId),
    db.prepare(`DELETE FROM guardados WHERE playlist IN (${suyas})`).bind(yoId),
    db.prepare(`DELETE FROM denuncias WHERE tipo = 'playlist' AND objetivo IN (${suyas})`).bind(yoId),
    db.prepare("DELETE FROM playlists_pub WHERE cuenta = ?1").bind(yoId),
    db.prepare("DELETE FROM denuncias WHERE cuenta = ?1").bind(yoId),
    // En la semana abierta (o pendiente de cerrar) una propuesta con votos de otros se queda sin dueña
    // para que esos votos no se pierdan; las demás se van. Las de semanas cerradas ya son historia.
    db
      .prepare(
        "UPDATE propuestas SET cuenta = 'borrada' WHERE cuenta = ?1 " +
          "AND semana IN (SELECT id FROM semanas WHERE cerrada = 0) " +
          "AND EXISTS (SELECT 1 FROM votos v WHERE v.semana = propuestas.semana AND v.cancion = propuestas.cancion AND v.cuenta <> ?1)",
      )
      .bind(yoId),
    db.prepare("DELETE FROM propuestas WHERE cuenta = ?1").bind(yoId),
    db.prepare("DELETE FROM votos WHERE cuenta = ?1").bind(yoId),
    // Retos vivos de su uid (guardan el uid en claro): fuera.
    db.prepare("DELETE FROM retos WHERE uid_hmac = (SELECT uid_hmac FROM cuentas WHERE id = ?1)").bind(yoId),
    db.prepare("DELETE FROM limites WHERE clave IN ('tarjeta:' || ?1, 'denuncia:' || ?1)").bind(yoId),
    db.prepare("DELETE FROM tokens WHERE cuenta = ?1").bind(yoId),
    // Una cuenta baneada NO desaparece del todo: queda una LÁPIDA con solo id, uid_hmac y baneada = 1
    // (sin nombre, registro ni fechas) para que volver a verificar la misma cuenta de FC siga dando
    // 403 baneada; si no, borrar la cuenta levantaría el baneo. Se avisa en la política de privacidad
    // como "un registro mínimo para hacer cumplir los baneos".
    db.prepare("DELETE FROM cuentas WHERE id = ?1 AND COALESCE(baneada, 0) = 0").bind(yoId),
    db.prepare("UPDATE cuentas SET nombre = '', registro_fc = NULL, creada = NULL, ultima_verif = NULL WHERE id = ?1").bind(yoId),
  ]);
  return json({ ok: true }, 200, SIN_CACHE);
}

export function rutasPlaylists(o: OpcionesApp = {}): Ruta[] {
  return [
    ["POST", "/v1/playlists", (c) => publicar(c, o.tarjetasHora), "cuenta"],
    ["GET", "/v1/playlists", listar, "publica"],
    ["GET", "/v1/playlists/:id", detalle, "publica"],
    ["DELETE", "/v1/playlists/:id", borrar, "cuenta"],
    ["POST", "/v1/playlists/:id/canciones", (c) => ampliar(c, o.tarjetasHora), "cuenta"],
    ["POST", "/v1/playlists/:id/guardar", guardar, "cuenta"],
    ["POST", "/v1/denuncias", denunciar, "cuenta"],
    ["DELETE", "/v1/cuenta", borrarCuenta, "cuenta_o_baneada"],
  ];
}
