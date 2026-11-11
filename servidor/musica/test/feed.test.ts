import { env } from "cloudflare:workers";
import { beforeEach, describe, expect, it } from "vitest";
import deezerTrack from "./fixtures/deezer_track_92719900.json";
import { VIDA_MS } from "../src/feed";
import {
  cancion, conToken, corazonDirecto, DIA, en, enlaceDe, filas, HORA, llamar, MIN, peticion, publicacionDirecta,
  reiniciarCuentas, shur, T0, TABLAS, type App, type Json, type Shur,
} from "./ayudas-admin";

beforeEach(async () => {
  reiniciarCuentas();
  await env.DB.batch(TABLAS.map((t) => env.DB.prepare(`DELETE FROM ${t}`)));
});

const publicar = (s: Shur, cuerpo: unknown, a: App = en()) =>
  llamar(a, peticion("POST", "/v1/publicaciones", cuerpo, conToken(s)));
const borrar = (s: Shur, id: string, a: App = en()) =>
  llamar(a, peticion("DELETE", "/v1/publicaciones/" + id, undefined, conToken(s)));
const feed = (query = "", a: App = en(), s?: Shur) =>
  llamar(a, peticion("GET", "/v1/feed" + query, undefined, s ? conToken(s) : {}));
const mias = (s: Shur, a: App = en()) => llamar(a, peticion("GET", "/v1/publicaciones/mias", undefined, conToken(s)));
const ids = (r: { cuerpo: Json }): string[] => r.cuerpo.items.map((i: Json) => i.id);

describe("POST /v1/publicaciones", () => {
  it("publica con deezer y aparece tras convertirse", async () => {
    const a = await shur();
    const red = async (url: string) => {
      if (!url.startsWith("https://api.deezer.com/track/92719900")) throw new Error("URL inesperada: " + url);
      return new Response(JSON.stringify(deezerTrack), { headers: { "content-type": "application/json" } });
    };
    const r = await publicar(a, { texto: "  Para la ruta  ", deezer: 92719900 }, en(0, { red }));
    expect(r.status).toBe(201);
    expect(r.cuerpo.publicacion).toMatchObject({
      autor: "YoMismo1", texto: "Para la ruta", creada: T0, caduca: T0 + VIDA_MS, corazones: 0, mia: true, corazon: false,
      cancion: { titulo: "Highway to Hell", deezer: 92719900 },
    });
    // Recién publicada y sin convertir: aún no sale en el feed.
    expect(ids(await feed())).toEqual([]);
    await env.DB.prepare("UPDATE canciones SET conversion = 'hecha'").run();
    expect(ids(await feed())).toEqual([r.cuerpo.publicacion.id]);
  });

  it("publica con enlace y la canción ya conocida", async () => {
    const a = await shur();
    await cancion(1);
    const r = await publicar(a, { texto: "Hola", enlace: enlaceDe(1) });
    expect(r.status).toBe(201);
    expect(r.cuerpo.publicacion.cancion.titulo).toBe("Tema 1");
  });

  it("cuerpo mal formado → 400 cuerpo", async () => {
    const a = await shur();
    await cancion(1);
    for (const c of [
      "no json", { enlace: enlaceDe(1) }, { texto: 5, enlace: enlaceDe(1) }, { texto: "x" },
      { texto: "x", deezer: 1, enlace: enlaceDe(1) }, { texto: "x", deezer: "1" }, { texto: "x", deezer: 1.5 }, { texto: "x", enlace: 3 },
    ]) {
      const r = await publicar(a, c);
      expect([r.status, r.cuerpo], JSON.stringify(c)).toEqual([400, { error: "cuerpo" }]);
    }
  });

  it("texto de 101 caracteres → 422 texto; 100 vale; vacío no", async () => {
    const a = await shur();
    await cancion(1);
    const e = enlaceDe(1);
    expect(await publicar(a, { texto: "a".repeat(101), enlace: e })).toMatchObject({ status: 422, cuerpo: { error: "texto" } });
    expect(await publicar(a, { texto: "   ", enlace: e })).toMatchObject({ status: 422, cuerpo: { error: "texto" } });
    expect((await publicar(a, { texto: "ñ".repeat(100), enlace: e })).status).toBe(201);
  });

  it("texto con www.algo.com → 422 texto", async () => {
    const a = await shur();
    await cancion(1);
    for (const t of ["mira www.algo.com", "https://x.y", "en tuyo.es mola", "t.me", "Un tema.com sin más"]) {
      expect((await publicar(a, { texto: t, enlace: enlaceDe(1) })).cuerpo, t).toEqual({ error: "texto" });
    }
    expect((await publicar(a, { texto: "Qué temazo, no?", enlace: enlaceDe(1) })).status).toBe(201);
  });

  it("texto con palabra prohibida → 422 texto", async () => {
    const a = await shur();
    await cancion(1);
    const r = await publicar(a, { texto: "esto es de GILIPOLLAS", enlace: enlaceDe(1) });
    expect([r.status, r.cuerpo]).toEqual([422, { error: "texto" }]);
  });

  it("séptima en 24 h → 429 limite", async () => {
    const a = await shur();
    await cancion(1);
    for (let i = 0; i < 6; i++) expect((await publicar(a, { texto: "n" + i, enlace: enlaceDe(1) })).status).toBe(201);
    const r = await publicar(a, { texto: "otra", enlace: enlaceDe(1) });
    expect([r.status, r.cuerpo]).toEqual([429, { error: "limite" }]);
  });

  it("borrar una y publicar no devuelve el cupo", async () => {
    const a = await shur();
    await cancion(1);
    const primera = await publicar(a, { texto: "n0", enlace: enlaceDe(1) });
    for (let i = 1; i < 6; i++) await publicar(a, { texto: "n" + i, enlace: enlaceDe(1) });
    expect((await borrar(a, primera.cuerpo.publicacion.id)).status).toBe(204);
    expect((await publicar(a, { texto: "otra", enlace: enlaceDe(1) })).status).toBe(429);
  });

  it("un intento rechazado no gasta cupo", async () => {
    const a = await shur();
    await cancion(1);
    for (let i = 0; i < 10; i++) await publicar(a, { texto: "x".repeat(101), enlace: enlaceDe(1) });
    for (let i = 0; i < 6; i++) expect((await publicar(a, { texto: "n" + i, enlace: enlaceDe(1) })).status).toBe(201);
  });

  it("cuenta de 30 días → 403 antiguedad", async () => {
    const a = await shur();
    await cancion(1);
    await env.DB.prepare("UPDATE cuentas SET registro_fc = ? WHERE id = ?").bind("05-oct-2026", a.id).run();
    const r = await publicar(a, { texto: "hola", enlace: enlaceDe(1) });
    expect([r.status, r.cuerpo]).toEqual([403, { error: "antiguedad" }]);
  });

  it("canción oculta → 422 oculta; enlace que no es canción → 422", async () => {
    const a = await shur();
    const c = await cancion(1);
    await env.DB.prepare("UPDATE canciones SET oculta = 1 WHERE id = ?").bind(c).run();
    expect((await publicar(a, { texto: "hola", enlace: enlaceDe(1) })).cuerpo).toEqual({ error: "oculta" });
    expect((await publicar(a, { texto: "hola", enlace: "https://ejemplo.com" })).cuerpo).toEqual({ error: "no_es_cancion" });
  });

  it("sin token → 401", async () => {
    const r = await llamar(en(), peticion("POST", "/v1/publicaciones", { texto: "x", deezer: 1 }));
    expect(r.status).toBe(401);
  });
});

describe("DELETE /v1/publicaciones/:id", () => {
  it("borrar ajena → 404 y sigue ahí; inexistente → 404", async () => {
    const a = await shur();
    const b = await shur();
    const c = await cancion(1);
    const p = await publicacionDirecta(a.id, c);
    const r = await borrar(b, p);
    expect([r.status, r.cuerpo]).toEqual([404, { error: "no_existe" }]);
    expect((await borrar(b, "0".repeat(26))).status).toBe(404);
    expect(await filas("SELECT id FROM publicaciones")).toHaveLength(1);
  });

  it("borrar la propia quita también sus corazones", async () => {
    const a = await shur();
    const b = await shur();
    const c = await cancion(1);
    const p = await publicacionDirecta(a.id, c, { corazones: 1 });
    await corazonDirecto(p, b.id);
    expect((await borrar(a, p)).status).toBe(204);
    expect(await filas("SELECT 1 FROM publicaciones")).toHaveLength(0);
    expect(await filas("SELECT 1 FROM corazones")).toHaveLength(0);
  });

  it("borrar la propia deja sus denuncias (rastro de moderación)", async () => {
    const a = await shur();
    const p = await publicacionDirecta(a.id, await cancion(1));
    await env.DB
      .prepare("INSERT INTO denuncias (id, tipo, objetivo, cuenta, motivo, creada) VALUES ('d1', 'publicacion', ?, 'x', 'spam', ?)")
      .bind(p, T0)
      .run();
    expect((await borrar(a, p)).status).toBe(204);
    expect(await filas("SELECT id FROM denuncias")).toHaveLength(1);
  });
});

describe("corazones", () => {
  const dar = (s: Shur, id: string, a: App = en()) =>
    llamar(a, peticion("POST", `/v1/publicaciones/${id}/corazon`, undefined, conToken(s)));
  const quitar = (s: Shur, id: string, a: App = en()) =>
    llamar(a, peticion("DELETE", `/v1/publicaciones/${id}/corazon`, undefined, conToken(s)));
  const nombres = (s: Shur, id: string, a: App = en()) =>
    llamar(a, peticion("GET", `/v1/publicaciones/${id}/corazones`, undefined, conToken(s)));
  const contador = async (id: string) => (await filas("SELECT corazones FROM publicaciones WHERE id = ?", id))[0].corazones;

  it("corazón sube a 1 y repetirlo no sube", async () => {
    const a = await shur();
    const b = await shur();
    const p = await publicacionDirecta(a.id, await cancion(1));
    expect((await dar(b, p)).cuerpo).toEqual({ corazones: 1, corazon: true });
    expect((await dar(b, p)).cuerpo).toEqual({ corazones: 1, corazon: true });
    expect(await contador(p)).toBe(1);
  });

  it("quitar uno que no existe deja 0", async () => {
    const a = await shur();
    const b = await shur();
    const p = await publicacionDirecta(a.id, await cancion(1));
    const r = await quitar(b, p);
    expect([r.status, r.cuerpo]).toEqual([200, { corazones: 0, corazon: false }]);
    await dar(b, p);
    expect((await quitar(b, p)).cuerpo).toEqual({ corazones: 0, corazon: false });
    expect((await quitar(b, p)).cuerpo).toEqual({ corazones: 0, corazon: false });
  });

  it("dos POST en paralelo dejan 1", async () => {
    const a = await shur();
    const b = await shur();
    const p = await publicacionDirecta(a.id, await cancion(1));
    const rs = await Promise.all([dar(b, p), dar(b, p)]);
    expect(rs.map((r) => r.status)).toEqual([200, 200]);
    expect(await contador(p)).toBe(1);
    expect(await filas("SELECT 1 FROM corazones")).toHaveLength(1);
  });

  it("a la propia → 422 propia", async () => {
    const a = await shur();
    const p = await publicacionDirecta(a.id, await cancion(1));
    expect(await dar(a, p)).toMatchObject({ status: 422, cuerpo: { error: "propia" } });
  });

  it("a una borrada → 404", async () => {
    const a = await shur();
    const b = await shur();
    const p = await publicacionDirecta(a.id, await cancion(1));
    await borrar(a, p);
    expect(await dar(b, p)).toMatchObject({ status: 404, cuerpo: { error: "no_existe" } });
    expect((await quitar(b, p)).status).toBe(404);
  });

  it("a una caducada, oculta o inexistente → 404", async () => {
    const a = await shur();
    const b = await shur();
    const c = await cancion(1);
    const vieja = await publicacionDirecta(a.id, c, { creada: T0 - DIA - MIN });
    const oculta = await publicacionDirecta(a.id, c, { oculta: true });
    for (const id of [vieja, oculta, "0".repeat(26)]) {
      expect(await dar(b, id), id).toMatchObject({ status: 404, cuerpo: { error: "no_existe" } });
    }
    expect(await filas("SELECT 1 FROM corazones")).toHaveLength(0);
  });

  it("cuenta nueva → 403 antiguedad", async () => {
    const a = await shur();
    const b = await shur();
    const p = await publicacionDirecta(a.id, await cancion(1));
    await env.DB.prepare("UPDATE cuentas SET registro_fc = ? WHERE id = ?").bind("05-oct-2026", b.id).run();
    expect(await dar(b, p)).toMatchObject({ status: 403, cuerpo: { error: "antiguedad" } });
  });

  it("lista de nombres solo para el autor, más reciente primero y sin cuentas borradas", async () => {
    const a = await shur();
    const b = await shur();
    const c = await shur();
    const d = await shur();
    const p = await publicacionDirecta(a.id, await cancion(1), { corazones: 4 });
    await corazonDirecto(p, b.id, T0 - 3 * MIN);
    await corazonDirecto(p, c.id, T0 - MIN);
    await corazonDirecto(p, d.id, T0 - 2 * MIN);
    await corazonDirecto(p, "fantasma", T0);
    await env.DB.prepare("UPDATE cuentas SET nombre = '' WHERE id = ?").bind(d.id).run();
    const r = await nombres(a, p);
    expect([r.status, r.cuerpo]).toEqual([200, { nombres: ["YoMismo3", "YoMismo2"] }]);
  });

  it("otro pide la lista → 404", async () => {
    const a = await shur();
    const b = await shur();
    const p = await publicacionDirecta(a.id, await cancion(1));
    expect(await nombres(b, p)).toMatchObject({ status: 404, cuerpo: { error: "no_existe" } });
    expect((await nombres(a, "0".repeat(26))).status).toBe(404);
  });
});

describe("GET /v1/feed", () => {
  it("la publicación sin convertir no sale a los 7 s y sí pasados 8 s", async () => {
    const a = await shur();
    const c = await cancion(1);
    await env.DB.prepare("UPDATE canciones SET conversion = 'pendiente'").run();
    const p = await publicacionDirecta(a.id, c);
    expect(ids(await feed("", en(7000)))).toEqual([]);
    expect(ids(await feed("", en(8001)))).toEqual([p]);
  });

  it("el autor ve su publicación aunque aún se convierta; los demás esperan los 8 s", async () => {
    const a = await shur();
    const b = await shur();
    const c = await cancion(1);
    await env.DB.prepare("UPDATE canciones SET conversion = 'pendiente'").run();
    const p = await publicacionDirecta(a.id, c);
    expect(ids(await feed("", en(1000), a))).toEqual([p]);
    expect(ids(await feed("?orden=queridas", en(1000), a))).toEqual([p]);
    expect(ids(await feed("", en(1000), b))).toEqual([]);
    expect(ids(await feed("", en(1000)))).toEqual([]);
  });

  it("las dos ordenaciones leen por sus índices", async () => {
    const plan = async (orden: string) =>
      (await filas("EXPLAIN QUERY PLAN SELECT p.id FROM publicaciones p WHERE p.caduca > ?1 AND p.creada > ?1 - 86400000 " + orden, T0))
        .map((f) => String(f.detail))
        .join(" | ");
    expect(await plan("ORDER BY p.creada DESC, p.id DESC LIMIT 21")).toContain("idx_publicaciones_creada");
    expect(await plan("ORDER BY p.corazones DESC, p.creada DESC, p.id DESC LIMIT 21")).toContain("idx_publicaciones_corazones");
  });

  it("una canción sin conversión posible (no_esta) sale enseguida", async () => {
    const a = await shur();
    const c = await cancion(1);
    await env.DB.prepare("UPDATE canciones SET conversion = 'no_esta'").run();
    const p = await publicacionDirecta(a.id, c);
    expect(ids(await feed())).toEqual([p]);
  });

  it("caduca a las 24 h exactas", async () => {
    const a = await shur();
    const c = await cancion(1);
    const p = await publicacionDirecta(a.id, c);
    expect(ids(await feed("", en(DIA - 1)))).toEqual([p]);
    expect(ids(await feed("", en(DIA)))).toEqual([]);
  });

  it("no enseña ocultas, de cuentas baneadas ni de canciones ocultas", async () => {
    const a = await shur();
    const baneada = await shur({ baneada: true });
    const c1 = await cancion(1);
    const c2 = await cancion(2);
    const buena = await publicacionDirecta(a.id, c1);
    await publicacionDirecta(a.id, c1, { oculta: true });
    await publicacionDirecta(baneada.id, c1);
    await publicacionDirecta(a.id, c2);
    await env.DB.prepare("UPDATE canciones SET oculta = 1 WHERE id = ?").bind(c2).run();
    expect(ids(await feed())).toEqual([buena]);
  });

  it("orden recientes: la más nueva primero; queridas: por corazones", async () => {
    const a = await shur();
    const c = await cancion(1);
    const vieja = await publicacionDirecta(a.id, c, { creada: T0 - 3 * HORA, corazones: 5 });
    const media = await publicacionDirecta(a.id, c, { creada: T0 - 2 * HORA, corazones: 0 });
    const nueva = await publicacionDirecta(a.id, c, { creada: T0 - HORA, corazones: 2 });
    expect(ids(await feed())).toEqual([nueva, media, vieja]);
    expect(ids(await feed("?orden=recientes"))).toEqual([nueva, media, vieja]);
    expect(ids(await feed("?orden=queridas"))).toEqual([vieja, nueva, media]);
    expect((await feed("?orden=otra")).cuerpo).toEqual({ error: "orden" });
  });

  it("paginación de 25 da 20 + cursor + 5", async () => {
    const a = await shur();
    const c = await cancion(1);
    const todas: string[] = [];
    for (let i = 0; i < 25; i++) todas.push(await publicacionDirecta(a.id, c, { creada: T0 - (i + 1) * MIN }));
    const p1 = await feed();
    expect(p1.cuerpo.items).toHaveLength(20);
    expect(typeof p1.cuerpo.cursor).toBe("string");
    const p2 = await feed("?cursor=" + encodeURIComponent(p1.cuerpo.cursor));
    expect(p2.cuerpo.items).toHaveLength(5);
    expect(p2.cuerpo.cursor).toBeNull();
    expect([...ids(p1), ...ids(p2)]).toEqual(todas);
  });

  it("paginación en queridas con empates", async () => {
    const a = await shur();
    const c = await cancion(1);
    for (let i = 0; i < 25; i++) await publicacionDirecta(a.id, c, { creada: T0 - (i + 1) * MIN, corazones: i % 3 });
    const p1 = await feed("?orden=queridas");
    const p2 = await feed("?orden=queridas&cursor=" + encodeURIComponent(p1.cuerpo.cursor));
    expect(p1.cuerpo.items).toHaveLength(20);
    expect(p2.cuerpo.items).toHaveLength(5);
    expect(new Set([...ids(p1), ...ids(p2)]).size).toBe(25);
  });

  it("cursor malo → 400", async () => {
    expect((await feed("?cursor=zzz")).cuerpo).toEqual({ error: "cursor" });
  });

  it("sin token cache pública 15 s y sin corazon/mia", async () => {
    const a = await shur();
    await publicacionDirecta(a.id, await cancion(1));
    const r = await feed();
    expect(r.cabeceras.get("Cache-Control")).toBe("public, max-age=15");
    expect(r.cabeceras.get("Vary")).toBe("Authorization");
    expect(r.cuerpo.items[0]).not.toHaveProperty("corazon");
    expect(r.cuerpo.items[0]).not.toHaveProperty("mia");
    expect(r.cuerpo.items[0]).toMatchObject({ autor: "YoMismo1", corazones: 0, cancion: { titulo: "Tema 1" } });
  });

  it("con token marca corazon y mia, y no se cachea", async () => {
    const a = await shur();
    const b = await shur();
    const c = await cancion(1);
    const deA = await publicacionDirecta(a.id, c, { creada: T0 - MIN, corazones: 1 });
    const deB = await publicacionDirecta(b.id, c, { creada: T0 - 2 * MIN });
    await corazonDirecto(deA, b.id);
    const r = await feed("", en(), b);
    expect(r.cabeceras.get("Cache-Control")).toBe("private, no-store");
    expect(r.cabeceras.get("Vary")).toBe("Authorization");
    const por = Object.fromEntries(r.cuerpo.items.map((i: any) => [i.id, i]));
    expect(por[deA]).toMatchObject({ corazon: true, mia: false });
    expect(por[deB]).toMatchObject({ corazon: false, mia: true });
  });

  it("un token malo se trata como invitado", async () => {
    const r = await llamar(en(), peticion("GET", "/v1/feed", undefined, { Authorization: "Bearer " + "a".repeat(43) }));
    expect(r.status).toBe(200);
    expect(r.cabeceras.get("Cache-Control")).toBe("public, max-age=15");
  });

  it("masQuerida de la semana pasada sale sin cursor", async () => {
    const a = await shur();
    const c = await cancion(1);
    // T0 = miércoles 2026-11-04 (semana 45): la anterior es la 44.
    await env.DB.batch([
      env.DB.prepare("INSERT INTO mas_querida (semana, cancion, autor, texto, corazones) VALUES ('2026-W44', ?, 'Fulano', 'La de siempre', 9)").bind(c),
      env.DB.prepare("INSERT INTO mas_querida (semana, cancion, autor, texto, corazones) VALUES ('2026-W43', ?, 'Mengano', 'Vieja', 20)").bind(c),
    ]);
    for (let i = 0; i < 25; i++) await publicacionDirecta(a.id, c, { creada: T0 - (i + 1) * MIN });
    const p1 = await feed();
    expect(p1.cuerpo.masQuerida).toMatchObject({
      semana: "2026-W44", autor: "Fulano", texto: "La de siempre", corazones: 9, cancion: { titulo: "Tema 1" },
    });
    const p2 = await feed("?cursor=" + encodeURIComponent(p1.cuerpo.cursor));
    expect(p2.cuerpo.masQuerida).toBeNull();
  });

  it("sin masQuerida guardada es null", async () => {
    expect((await feed()).cuerpo).toEqual({ items: [], cursor: null, masQuerida: null });
  });
});

describe("GET /v1/publicaciones/mias", () => {
  it("incluye la que aún se convierte, no las de otros, ni caducadas ni ocultas", async () => {
    const a = await shur();
    const b = await shur();
    const c = await cancion(1);
    await env.DB.prepare("UPDATE canciones SET conversion = 'pendiente'").run();
    const propia = await publicacionDirecta(a.id, c, { creada: T0 - MIN });
    await publicacionDirecta(b.id, c);
    await publicacionDirecta(a.id, c, { creada: T0 - DIA - 1 });
    await publicacionDirecta(a.id, c, { oculta: true });
    const r = await mias(a);
    expect(r.status).toBe(200);
    expect(ids(r)).toEqual([propia]);
    expect(r.cuerpo.items[0]).toMatchObject({ mia: true, corazon: false, autor: "YoMismo1" });
    expect(r.cabeceras.get("Cache-Control")).toBe("no-store");
  });
});

describe("revisión final del feed", () => {
  const dar = (s: Shur, id: string, a: App = en()) =>
    llamar(a, peticion("POST", `/v1/publicaciones/${id}/corazon`, undefined, conToken(s)));
  const quitar = (s: Shur, id: string, a: App = en()) =>
    llamar(a, peticion("DELETE", `/v1/publicaciones/${id}/corazon`, undefined, conToken(s)));

  it("tope de corazones por cuenta y día: dar y quitar cuentan; el 301 da 429 limite", async () => {
    const a = await shur();
    const b = await shur();
    const p = await publicacionDirecta(a.id, await cancion(1));
    // 299 movimientos ya hechos hoy (dar y quitar suman igual).
    const ventana = Math.floor(T0 / 1000 / 86_400) * 86_400;
    await env.DB.prepare("INSERT INTO limites (clave, ventana, n) VALUES (?, ?, 299)").bind("corazon:" + b.id, ventana).run();
    expect((await dar(b, p)).status).toBe(200);
    const r = await quitar(b, p);
    expect([r.status, r.cuerpo]).toEqual([429, { error: "limite" }]);
    // El rechazado no tocó el corazón ni el contador.
    expect((await filas("SELECT corazones FROM publicaciones WHERE id = ?", p))[0].corazones).toBe(1);
    // Otra cuenta no se ve afectada, y al día siguiente se puede otra vez.
    const c = await shur();
    expect((await dar(c, p)).status).toBe(200);
    const manana = await publicacionDirecta(a.id, await cancion(2), { creada: T0 + DIA - HORA });
    expect((await dar(b, manana, en(DIA))).status).toBe(200);
  });

  it("Deezer sin cuota o caído al publicar → 503 deezer (reintentable), nada guardado", async () => {
    const a = await shur();
    const cuota = JSON.stringify({ error: { type: "Exception", message: "Quota limit exceeded", code: 4 } });
    for (const resp of [
      () => new Response(cuota, { headers: { "content-type": "application/json" } }),
      () => new Response("caído", { status: 502 }),
      () => new Response("<html></html>", { headers: { "content-type": "text/html" } }),
    ]) {
      const r = await publicar(a, { texto: "Hola", deezer: 92719900 }, en(0, { red: async () => resp() }));
      expect([r.status, r.cuerpo]).toEqual([503, { error: "deezer" }]);
    }
    expect(await filas("SELECT id FROM publicaciones")).toEqual([]);
    expect(await filas("SELECT id FROM canciones")).toEqual([]);
    // Una pista que no existe sigue siendo no_encontrada.
    const noData = JSON.stringify({ error: { type: "DataException", message: "no data", code: 800 } });
    const r = await publicar(a, { texto: "Hola", deezer: 1 }, en(0, { red: async () => new Response(noData) }));
    expect([r.status, r.cuerpo]).toEqual([422, { error: "no_encontrada" }]);
  });
});
