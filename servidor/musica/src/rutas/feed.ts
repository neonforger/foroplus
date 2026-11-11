// Publicar, borrar y leer el feed de canciones de 24 h.
// La cuenta sale SIEMPRE del token. Lo que se lee del feed es público; lo propio (corazón, mía) solo
// se calcula si quien pide trae un token válido.
import type { Contexto, OpcionesApp, Ruta } from "../app";
import { cuentaDe, permisoVoto, yo } from "../auth";
import { cancionDeOrigen, cancionPub, type FilaCancion } from "../canciones";
import { nuevoId } from "../cripto";
import {
  claveTope, codificar, decodificar, ESPERA_CONVERSION_MS, itemFeed, POR_PAGINA, PUBLICACIONES_DIA, RE_ENLACE,
  SELECT_FEED, TEXTO_MAX, VENTANA_TOPE_S, VIDA_MS, VISIBLE, type FilaFeed, type ItemFeed, type MasQuerida,
} from "../feed";
import { cuerpoJson, error, json } from "../http";
import { permitido, puedeMontarTarjeta } from "../limites";
import { nombreAceptable } from "../palabras";
import { semanaAnterior, semanaDe } from "../tiempo";

const MAX_CUERPO = 2048;
const SIN_CACHE = { "Cache-Control": "no-store" };

// ---- Publicar ----

async function publicar(c: Contexto, o: OpcionesApp): Promise<Response> {
  const cuenta = yo(c);
  const d = c.deps;
  const cuerpo = await cuerpoJson(c.req, MAX_CUERPO);
  const { texto: crudo, deezer, enlace } = cuerpo ?? {};
  // Exactamente uno de los dos: un id de Deezer (entero positivo) o un enlace.
  const conDeezer = deezer !== undefined;
  const conEnlace = enlace !== undefined;
  if (typeof crudo !== "string" || conDeezer === conEnlace) return error("cuerpo", 400);
  if (conDeezer && !(typeof deezer === "number" && Number.isSafeInteger(deezer) && deezer > 0)) return error("cuerpo", 400);
  if (conEnlace && typeof enlace !== "string") return error("cuerpo", 400);
  const ahora = d.ahora();
  if (!permisoVoto(cuenta.registro, ahora, d.diasMinVoto).puedeVotar) return error("antiguedad", 403);
  const texto = crudo.trim();
  const largo = [...texto].length;
  if (largo < 1 || largo > TEXTO_MAX || RE_ENLACE.test(texto) || !nombreAceptable(texto)) return error("texto", 422);
  const r = await cancionDeOrigen(d, conDeezer ? { deezer: deezer as number } : { enlace: enlace as string }, () =>
    puedeMontarTarjeta(d.db, cuenta.id, ahora, o.tarjetasHora),
  );
  if ("error" in r) return error(r.error, r.error === "limite" ? 429 : 422);
  // Una canción que el dueño ocultó no se puede publicar.
  if (await d.db.prepare("SELECT 1 AS x FROM canciones WHERE id = ? AND oculta = 1").bind(r.cancion.id).first()) {
    return error("oculta", 422);
  }
  // El tope va el ÚLTIMO, cuando todo lo demás ya está bien: un intento rechazado no gasta cupo. Se
  // cuenta en `limites` (no mirando las publicaciones vivas) para que las borradas también cuenten.
  // Es la ventana fija de 24 h de `permitido` (de 00:00 a 00:00 UTC), una aproximación de "6 en 24 h".
  if (!(await permitido(d.db, claveTope(cuenta.id), VENTANA_TOPE_S, PUBLICACIONES_DIA, ahora))) {
    return error("limite", 429);
  }
  const id = nuevoId(ahora);
  const creada = ahora.getTime();
  await d.db
    .prepare("INSERT INTO publicaciones (id, cuenta, cancion, texto, creada, caduca, oculta, corazones) VALUES (?, ?, ?, ?, ?, ?, 0, 0)")
    .bind(id, cuenta.id, r.cancion.id, texto, creada, creada + VIDA_MS)
    .run();
  const publicacion: ItemFeed = {
    id, autor: cuenta.nombre, texto, creada, caduca: creada + VIDA_MS, corazones: 0, corazon: false, mia: true,
    cancion: cancionPub(r.cancion),
  };
  return json({ publicacion }, 201, SIN_CACHE);
}

// ---- Borrar ----

async function borrar(c: Contexto): Promise<Response> {
  const cuenta = yo(c);
  const db = c.deps.db;
  const id = c.params.id;
  const suya = "SELECT id FROM publicaciones WHERE id = ?1 AND cuenta = ?2";
  const r = await db.batch([
    db.prepare(`DELETE FROM corazones WHERE publicacion IN (${suya})`).bind(id, cuenta.id),
    db.prepare(`DELETE FROM denuncias WHERE tipo = 'publicacion' AND objetivo IN (${suya})`).bind(id, cuenta.id),
    db.prepare("DELETE FROM publicaciones WHERE id = ?1 AND cuenta = ?2").bind(id, cuenta.id),
  ]);
  // Ajena e inexistente dan lo mismo: no se confirma de quién es.
  return r[2].meta.changes === 1 ? new Response(null, { status: 204, headers: SIN_CACHE }) : error("no_existe", 404);
}

// ---- Leer ----

async function leerFeed(c: Contexto): Promise<Response> {
  const d = c.deps;
  const orden = c.url.searchParams.get("orden") ?? "recientes";
  if (orden !== "recientes" && orden !== "queridas") return error("orden", 400);
  const texto = c.url.searchParams.get("cursor");
  const cursor = texto ? decodificar(texto, orden === "queridas") : null;
  if (texto && !cursor) return error("cursor", 400);
  // Ruta pública con token opcional: uno que no vale (caducado, baneada...) se trata como invitado en
  // vez de romper la lectura.
  const quien = c.req.headers.has("Authorization") ? await cuentaDe(c.req, d) : null;
  const cuenta = quien instanceof Response ? null : quien;
  const ahora = d.ahora().getTime();
  const base = SELECT_FEED + `WHERE ${VISIBLE} `;
  const comunes = [ahora, ahora - ESPERA_CONVERSION_MS, POR_PAGINA + 1, cuenta?.id ?? null, cursor?.p ?? null, cursor?.i ?? null];
  const sentencia =
    orden === "recientes"
      ? d.db
          .prepare(
            base + "AND (?5 IS NULL OR p.creada < ?5 OR (p.creada = ?5 AND p.id < ?6)) " +
              "ORDER BY p.creada DESC, p.id DESC LIMIT ?3",
          )
          .bind(...comunes)
      : d.db
          .prepare(
            base + "AND (?7 IS NULL OR p.corazones < ?7 OR (p.corazones = ?7 AND " +
              "(p.creada < ?5 OR (p.creada = ?5 AND p.id < ?6)))) " +
              "ORDER BY p.corazones DESC, p.creada DESC, p.id DESC LIMIT ?3",
          )
          .bind(...comunes, cursor?.g ?? null);
  const filas = (await sentencia.all<FilaFeed>()).results;
  const hay = filas.length > POR_PAGINA;
  const pagina = hay ? filas.slice(0, POR_PAGINA) : filas;
  const ultima = pagina[pagina.length - 1];
  const siguiente =
    hay && ultima
      ? codificar(orden === "queridas" ? { g: ultima.corazones, p: ultima.creada, i: ultima.id } : { p: ultima.creada, i: ultima.id })
      : null;
  const masQuerida = cursor ? null : await leerMasQuerida(d.db, semanaAnterior(semanaDe(d.ahora())));
  return json(
    { items: pagina.map((f) => itemFeed(f, !!cuenta)), cursor: siguiente, masQuerida },
    200,
    cuenta ? { "Cache-Control": "private, no-store", Vary: "Authorization" } : { "Cache-Control": "public, max-age=15" },
  );
}

async function leerMasQuerida(db: D1Database, semana: string): Promise<MasQuerida | null> {
  const f = await db
    .prepare(
      "SELECT m.semana, m.autor, m.texto, m.corazones, c.id, c.titulo, c.artista, c.portada, c.enlaces, c.deezer, c.spotify " +
        "FROM mas_querida m JOIN canciones c ON c.id = m.cancion WHERE m.semana = ? AND COALESCE(c.oculta, 0) = 0",
    )
    .bind(semana)
    .first<FilaCancion & { semana: string; autor: string; texto: string; corazones: number }>();
  return f ? { semana: f.semana, autor: f.autor, texto: f.texto, corazones: f.corazones, cancion: cancionPub(f) } : null;
}

async function mias(c: Contexto): Promise<Response> {
  const cuenta = yo(c);
  const ahora = c.deps.ahora().getTime();
  // Las vivas propias, también las que aún se convierten (la app las enseña como "publicando").
  const filas = (
    await c.deps.db
      .prepare(SELECT_FEED + "WHERE p.cuenta = ?4 AND p.caduca > ?1 AND p.oculta = 0 ORDER BY p.creada DESC, p.id DESC LIMIT ?3")
      .bind(ahora, 0, 50, cuenta.id)
      .all<FilaFeed>()
  ).results;
  return json({ items: filas.map((f) => itemFeed(f, true)) }, 200, SIN_CACHE);
}

export function rutasFeed(o: OpcionesApp = {}): Ruta[] {
  return [
    ["POST", "/v1/publicaciones", (c) => publicar(c, o), "cuenta"],
    ["GET", "/v1/publicaciones/mias", mias, "cuenta"],
    ["DELETE", "/v1/publicaciones/:id", borrar, "cuenta"],
    ["GET", "/v1/feed", leerFeed, "publica"],
  ];
}
