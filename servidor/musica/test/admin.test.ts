import { env } from "cloudflare:workers";
import { beforeEach, describe, expect, it } from "vitest";
import { nuevoId } from "../src/cripto";
import { admin, cancion, conToken, en, filas, llamar, peticion, reiniciarCuentas, shur, T0, TABLAS } from "./ayudas-admin";

beforeEach(async () => {
  reiniciarCuentas();
  await env.DB.batch(TABLAS.map((t) => env.DB.prepare(`DELETE FROM ${t}`)));
});

describe("acceso de administración", () => {
  const rutas: [string, string, unknown][] = [
    ["GET", "/v1/admin/denuncias", undefined],
    ["POST", "/v1/admin/ocultar", { tipo: "cancion", id: "x", oculto: true }],
    ["POST", "/v1/admin/banear", { cuenta: "x", baneada: true }],
    ["POST", "/v1/admin/resolver", { denuncia: "x" }],
    ["POST", "/v1/admin/fusionar", { de: "x", en: "y" }],
  ];

  it("adminSecretoMalo: sin cabecera, con secreto equivocado o con un token de cuenta -> 401 admin", async () => {
    const yo = await shur();
    for (const [m, r, cuerpo] of rutas) {
      for (const cab of [{}, { "X-Admin": "otro" }, { "X-Admin": "" }, conToken(yo)] as Record<string, string>[]) {
        const res = await llamar(en(), peticion(m, r, cuerpo, cab));
        expect(res.status, `${m} ${r} ${JSON.stringify(cab)}`).toBe(401);
        expect(res.cuerpo).toEqual({ error: "admin" });
      }
    }
  });

  it("con el secreto de administración vacío en el servidor no entra nadie, ni con la cabecera vacía", async () => {
    const app = en(0, { adminSecreto: "" });
    for (const cab of [{}, { "X-Admin": "" }] as Record<string, string>[]) {
      const res = await llamar(app, peticion("GET", "/v1/admin/denuncias", undefined, cab));
      expect(res.status).toBe(401);
    }
  });
});

describe("POST /v1/admin/banear", () => {
  it("banear conserva sus tokens (la cuenta solo puede borrarse) y no toca a las demás", async () => {
    const mala = await shur();
    const otra = await shur();
    const r = await admin("POST", "/v1/admin/banear", { cuenta: mala.id, baneada: true });
    expect(r.status).toBe(200);
    expect(r.cuerpo).toEqual({ ok: true });
    expect((await filas("SELECT baneada FROM cuentas WHERE id = ?", mala.id))[0].baneada).toBe(1);
    expect((await filas("SELECT baneada FROM cuentas WHERE id = ?", otra.id))[0].baneada).toBe(0);
    expect(await filas("SELECT 1 FROM tokens WHERE cuenta = ?", mala.id)).toHaveLength(1);
    expect(await filas("SELECT 1 FROM tokens WHERE cuenta = ?", otra.id)).toHaveLength(1);
  });

  it("desbanear solo levanta la marca: no devuelve tokens, y la cuenta puede volver a verificar", async () => {
    const mala = await shur({ baneada: true });
    const r = await admin("POST", "/v1/admin/banear", { cuenta: mala.id, baneada: false });
    expect(r.status).toBe(200);
    expect((await filas("SELECT baneada FROM cuentas WHERE id = ?", mala.id))[0].baneada).toBe(0);
  });

  it("una lápida (nombre vacío) también se puede desbanear", async () => {
    const mala = await shur({ baneada: true });
    await env.DB.prepare("UPDATE cuentas SET nombre = '', registro_fc = NULL, creada = NULL, ultima_verif = NULL WHERE id = ?").bind(mala.id).run();
    const r = await admin("POST", "/v1/admin/banear", { cuenta: mala.id, baneada: false });
    expect(r.status).toBe(200);
    expect((await filas("SELECT baneada FROM cuentas WHERE id = ?", mala.id))[0].baneada).toBe(0);
  });

  it("cuenta inexistente 404 y cuerpo mal formado 400", async () => {
    expect((await admin("POST", "/v1/admin/banear", { cuenta: "nada", baneada: true })).status).toBe(404);
    expect((await admin("POST", "/v1/admin/banear", { cuenta: "nada" })).status).toBe(400);
    expect((await admin("POST", "/v1/admin/banear", { cuenta: 5, baneada: true })).status).toBe(400);
    expect((await admin("POST", "/v1/admin/banear", "no json")).status).toBe(400);
  });
});

describe("POST /v1/admin/ocultar", () => {
  it("una canción se oculta y se vuelve a mostrar", async () => {
    const c = await cancion(1);
    expect((await admin("POST", "/v1/admin/ocultar", { tipo: "cancion", id: c, oculto: true })).status).toBe(200);
    expect((await filas("SELECT oculta FROM canciones WHERE id = ?", c))[0].oculta).toBe(1);
    expect((await admin("POST", "/v1/admin/ocultar", { tipo: "cancion", id: c, oculto: false })).status).toBe(200);
    expect((await filas("SELECT oculta FROM canciones WHERE id = ?", c))[0].oculta).toBe(0);
  });

  it("id o tipo erróneo 400 (ya no hay playlists que ocultar); inexistente 404", async () => {
    expect((await admin("POST", "/v1/admin/ocultar", { tipo: "otro", id: "x", oculto: true })).status).toBe(400);
    expect((await admin("POST", "/v1/admin/ocultar", { tipo: "playlist", id: nuevoId(new Date(T0)), oculto: true })).status).toBe(400);
    expect((await admin("POST", "/v1/admin/ocultar", { tipo: "cancion", id: "x" })).status).toBe(400);
    const ulid = nuevoId(new Date(T0));
    expect((await admin("POST", "/v1/admin/ocultar", { tipo: "cancion", id: ulid, oculto: true })).status).toBe(404);
  });
});

describe("denuncias de administración", () => {
  it("lista las sin resolver, las más antiguas primero, con el contexto; resolver la quita", async () => {
    const denunciante = await shur();
    const c = await cancion(1);
    await env.DB.batch([
      env.DB.prepare("INSERT INTO denuncias (id, tipo, objetivo, cuenta, motivo, creada, resuelta) VALUES ('d2', 'cancion', ?, ?, 'mala', ?, 0)").bind(c, denunciante.id, T0 + 2),
      env.DB.prepare("INSERT INTO denuncias (id, tipo, objetivo, cuenta, motivo, creada, resuelta) VALUES ('d1', 'publicacion', 'pub', ?, 'ofensiva', ?, 0)").bind(denunciante.id, T0 + 1),
      env.DB.prepare("INSERT INTO denuncias (id, tipo, objetivo, cuenta, motivo, creada, resuelta) VALUES ('d0', 'cancion', ?, ?, 'vieja', ?, 1)").bind(c, denunciante.id, T0),
    ]);
    const r = await admin("GET", "/v1/admin/denuncias");
    expect(r.status).toBe(200);
    expect(r.cuerpo.denuncias).toEqual([
      { id: "d1", tipo: "publicacion", objetivo: "pub", cuenta: denunciante.id, motivo: "ofensiva", creada: T0 + 1 },
      { id: "d2", tipo: "cancion", objetivo: c, cuenta: denunciante.id, motivo: "mala", creada: T0 + 2, titulo: "Tema 1", artista: "Grupo 1" },
    ]);
    expect((await admin("POST", "/v1/admin/resolver", { denuncia: "d1" })).status).toBe(200);
    expect((await admin("GET", "/v1/admin/denuncias")).cuerpo.denuncias.map((x: any) => x.id)).toEqual(["d2"]);
    expect((await admin("POST", "/v1/admin/resolver", { denuncia: "nada" })).status).toBe(404);
    expect((await admin("POST", "/v1/admin/resolver", {})).status).toBe(400);
  });
});

describe("POST /v1/admin/fusionar", () => {
  it("mueve enlaces y referencias de la fusionada a la destino y borra la fusionada", async () => {
    const de = await cancion(1);
    const dest = await cancion(2);
    // `de` tiene además un enlace de Spotify que `dest` no tiene.
    await env.DB.batch([
      env.DB.prepare("UPDATE canciones SET enlaces = json_set(enlaces, '$.spotify', 'https://open.spotify.com/track/0aaaaaaaaaaaaaaaaaaaaa') WHERE id = ?").bind(de),
      env.DB.prepare("INSERT INTO enlaces_cancion (ref, cancion) VALUES ('spotify:0aaaaaaaaaaaaaaaaaaaaa', ?)").bind(de),
    ]);
    expect((await admin("POST", "/v1/admin/fusionar", { de, en: dest })).status).toBe(200);
    expect(await filas("SELECT ref, cancion FROM enlaces_cancion ORDER BY ref")).toEqual([
      { ref: "spotify:0aaaaaaaaaaaaaaaaaaaaa", cancion: dest },
      { ref: "youtube:tema0000001", cancion: dest },
      { ref: "youtube:tema0000002", cancion: dest },
    ]);
    const e = JSON.parse((await filas("SELECT enlaces FROM canciones WHERE id = ?", dest))[0].enlaces);
    expect(e).toEqual({
      youtube: "https://www.youtube.com/watch?v=tema0000002",
      spotify: "https://open.spotify.com/track/0aaaaaaaaaaaaaaaaaaaaa",
    });
    expect(await filas("SELECT 1 FROM canciones WHERE id = ?", de)).toHaveLength(0);
  });

  it("las denuncias de canción y de sistema sobre la fusionada pasan a la destino y se ven con su título", async () => {
    const quien = await shur();
    const de = await cancion(1);
    const dest = await cancion(2);
    await env.DB.batch([
      env.DB.prepare("INSERT INTO denuncias (id, tipo, objetivo, cuenta, motivo, creada, resuelta) VALUES ('s1', 'sistema', ?, 'sistema', 'subida rara', ?, 0)").bind(de, T0),
      env.DB.prepare("INSERT INTO denuncias (id, tipo, objetivo, cuenta, motivo, creada, resuelta) VALUES ('c1', 'cancion', ?, ?, 'mala', ?, 0)").bind(de, quien.id, T0 + 1),
    ]);
    expect((await admin("POST", "/v1/admin/fusionar", { de, en: dest })).status).toBe(200);
    expect((await filas("SELECT objetivo FROM denuncias ORDER BY id")).map((f) => f.objetivo)).toEqual([dest, dest]);
    const lista = (await admin("GET", "/v1/admin/denuncias")).cuerpo.denuncias;
    expect(lista.map((x: any) => [x.id, x.titulo])).toEqual([["s1", "Tema 2"], ["c1", "Tema 2"]]);
  });

  it("errores: mismas, inexistentes o mal formadas", async () => {
    const c = await cancion(1);
    const ulid = nuevoId(new Date(T0));
    expect((await admin("POST", "/v1/admin/fusionar", { de: c, en: c })).status).toBe(400);
    expect((await admin("POST", "/v1/admin/fusionar", { de: c })).status).toBe(400);
    expect((await admin("POST", "/v1/admin/fusionar", { de: c, en: ulid })).status).toBe(404);
    expect((await admin("POST", "/v1/admin/fusionar", { de: ulid, en: c })).status).toBe(404);
    expect(await filas("SELECT 1 FROM canciones")).toHaveLength(1);
  });
});
