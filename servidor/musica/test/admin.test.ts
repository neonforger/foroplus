import { env } from "cloudflare:workers";
import { beforeEach, describe, expect, it } from "vitest";
import { nuevoId } from "../src/cripto";
import {
  admin, cancion, conToken, DIA, en, enlaceDe, filas, llamar, peticion, proponerDirecto, reiniciarCuentas, shur,
  T0, TABLAS, votarDirecto, W45, W46,
} from "./ayudas-admin";

beforeEach(async () => {
  reiniciarCuentas();
  await env.DB.batch(TABLAS.map((t) => env.DB.prepare(`DELETE FROM ${t}`)));
});

/** Una playlist pública con esas canciones, de la cuenta `cuenta`. */
async function playlist(cuenta: string, canciones: string[], nombre = "Mis favoritas"): Promise<string> {
  const id = nuevoId(new Date(T0));
  await env.DB
    .prepare("INSERT INTO playlists_pub (id, cuenta, nombre, canciones, publicada, guardados, oculta) VALUES (?, ?, ?, ?, ?, 0, 0)")
    .bind(id, cuenta, nombre, JSON.stringify(canciones), T0)
    .run();
  return id;
}

describe("acceso de administración", () => {
  const rutas: [string, string, unknown][] = [
    ["GET", "/v1/admin/denuncias", undefined],
    ["POST", "/v1/admin/ocultar", { tipo: "playlist", id: "x", oculto: true }],
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
  it("banear conserva sus tokens (la cuenta solo puede borrarse) y borra sus votos de semanas abiertas; los de semanas cerradas se quedan", async () => {
    const mala = await shur();
    const otra = await shur();
    const c1 = await cancion(1);
    const c2 = await cancion(2);
    await proponerDirecto(W45, c1, otra.id);
    await proponerDirecto("2026-W44", c2, otra.id);
    await env.DB.prepare("UPDATE semanas SET cerrada = 1 WHERE id = '2026-W44'").run();
    await votarDirecto(W45, c1, mala.id);
    await votarDirecto(W45, c1, otra.id);
    await votarDirecto("2026-W44", c2, mala.id);

    const r = await admin("POST", "/v1/admin/banear", { cuenta: mala.id, baneada: true });
    expect(r.status).toBe(200);
    expect(r.cuerpo).toEqual({ ok: true });
    expect((await filas("SELECT baneada FROM cuentas WHERE id = ?", mala.id))[0].baneada).toBe(1);
    expect(await filas("SELECT 1 FROM tokens WHERE cuenta = ?", mala.id)).toHaveLength(1);
    expect(await filas("SELECT 1 FROM tokens WHERE cuenta = ?", otra.id)).toHaveLength(1);
    expect(await filas("SELECT cuenta, semana FROM votos ORDER BY semana, cuenta")).toEqual([
      { cuenta: mala.id, semana: "2026-W44" },
      { cuenta: otra.id, semana: W45 },
    ]);
  });

  it("sus propuestas con votos de otros pasan a 'borrada'; las demás se quitan; las cerradas se quedan", async () => {
    const mala = await shur();
    const otra = await shur();
    const [c1, c2, c3] = [await cancion(1), await cancion(2), await cancion(3)];
    await proponerDirecto(W45, c1, mala.id); // con voto de otra: se queda sin dueña
    await proponerDirecto(W45, c2, mala.id); // solo con voto suyo: fuera
    await proponerDirecto("2026-W44", c3, mala.id); // semana cerrada: historia
    await env.DB.prepare("UPDATE semanas SET cerrada = 1 WHERE id = '2026-W44'").run();
    await votarDirecto(W45, c1, otra.id);
    await votarDirecto(W45, c2, mala.id);

    await admin("POST", "/v1/admin/banear", { cuenta: mala.id, baneada: true });
    expect(await filas("SELECT semana, cancion, cuenta FROM propuestas ORDER BY semana")).toEqual([
      { semana: "2026-W44", cancion: c3, cuenta: mala.id },
      { semana: W45, cancion: c1, cuenta: "borrada" },
    ]);
    expect(await filas("SELECT cancion FROM votos")).toEqual([{ cancion: c1 }]);
  });

  it("también limpia semanas sin fila en `semanas` (futuras aún sin cerrar)", async () => {
    const mala = await shur();
    const c = await cancion(1);
    await env.DB.prepare("INSERT INTO votos (semana, cancion, cuenta, creado) VALUES (?, ?, ?, ?)").bind(W46, c, mala.id, T0).run();
    await admin("POST", "/v1/admin/banear", { cuenta: mala.id, baneada: true });
    expect(await filas("SELECT 1 FROM votos")).toHaveLength(0);
  });

  it("desbanear solo levanta la marca: no devuelve tokens ni votos, y la cuenta puede volver a verificar", async () => {
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
  it("una playlist oculta deja de salir en la lista y el detalle, y se puede volver a mostrar", async () => {
    const dueña = await shur();
    const id = await playlist(dueña.id, [await cancion(1)]);
    expect((await llamar(en(), peticion("GET", "/v1/playlists/" + id))).status).toBe(200);
    expect((await admin("POST", "/v1/admin/ocultar", { tipo: "playlist", id, oculto: true })).status).toBe(200);
    expect((await llamar(en(), peticion("GET", "/v1/playlists/" + id))).status).toBe(404);
    expect((await llamar(en(), peticion("GET", "/v1/playlists"))).cuerpo.playlists).toEqual([]);
    await admin("POST", "/v1/admin/ocultar", { tipo: "playlist", id, oculto: false });
    expect((await llamar(en(), peticion("GET", "/v1/playlists/" + id))).status).toBe(200);
  });

  it("una canción oculta sale de la semana en curso y del detalle de playlists, y no se vota ni se propone", async () => {
    const a = await shur();
    const b = await shur();
    const c1 = await cancion(1);
    const c2 = await cancion(2);
    await proponerDirecto(W45, c1, a.id);
    await proponerDirecto(W45, c2, a.id);
    const pl = await playlist(a.id, [c1, c2]);

    const r = await admin("POST", "/v1/admin/ocultar", { tipo: "cancion", id: c1, oculto: true });
    expect(r.status).toBe(200);

    const semana = await llamar(en(), peticion("GET", "/v1/semana"));
    expect(semana.cuerpo.actual.canciones.map((x: any) => x.id)).toEqual([c2]);
    const det = await llamar(en(), peticion("GET", "/v1/playlists/" + pl));
    expect(det.cuerpo.canciones.map((x: any) => x.id)).toEqual([c2]);
    // Votar una oculta: 409 no_esta. Proponerla de nuevo: 422 oculta (sin salir a la red).
    const voto = await llamar(en(), peticion("POST", "/v1/semana/voto", { cancion: c1 }, conToken(b)));
    expect(voto.status).toBe(409);
    expect(voto.cuerpo.error).toBe("no_esta");
    const prop = await llamar(en(), peticion("POST", "/v1/semana/proponer", { enlace: enlaceDe(1) }, conToken(b)));
    expect(prop.status).toBe(422);
    expect(prop.cuerpo.error).toBe("oculta");
    // La otra sigue votable.
    expect((await llamar(en(), peticion("POST", "/v1/semana/voto", { cancion: c2 }, conToken(b)))).status).toBe(200);
    // Y al mostrarla vuelve a todo.
    await admin("POST", "/v1/admin/ocultar", { tipo: "cancion", id: c1, oculto: false });
    expect((await llamar(en(), peticion("GET", "/v1/semana"))).cuerpo.actual.canciones).toHaveLength(2);
    expect((await llamar(en(), peticion("POST", "/v1/semana/voto", { cancion: c1 }, conToken(b)))).status).toBe(200);
  });

  it("al cerrar la semana, una canción oculta no entra en el top; un top ya cerrado no se reescribe", async () => {
    const a = await shur();
    const c1 = await cancion(1);
    const c2 = await cancion(2);
    await proponerDirecto(W45, c1, a.id);
    await proponerDirecto(W45, c2, a.id);
    await votarDirecto(W45, c1, a.id);
    await votarDirecto(W45, c1, (await shur()).id);
    await votarDirecto(W45, c2, a.id);
    await admin("POST", "/v1/admin/ocultar", { tipo: "cancion", id: c1, oculto: true });
    await en(7 * DIA).programado("0 22,23 * * SUN"); // un miércoles: no cierra, solo comprueba que no lanza
    const lunes = new Date("2026-11-08T23:00:00Z").getTime() - T0;
    await en(lunes).programado("0 22,23 * * SUN");
    const top = JSON.parse((await filas("SELECT top FROM semanas WHERE id = ?", W45))[0].top);
    expect(top.map((x: any) => x.id)).toEqual([c2]);
  });

  it("id o tipo erróneo 400; inexistente 404", async () => {
    expect((await admin("POST", "/v1/admin/ocultar", { tipo: "otro", id: "x", oculto: true })).status).toBe(400);
    expect((await admin("POST", "/v1/admin/ocultar", { tipo: "cancion", id: "x" })).status).toBe(400);
    const ulid = nuevoId(new Date(T0));
    expect((await admin("POST", "/v1/admin/ocultar", { tipo: "cancion", id: ulid, oculto: true })).status).toBe(404);
    expect((await admin("POST", "/v1/admin/ocultar", { tipo: "playlist", id: ulid, oculto: true })).status).toBe(404);
  });
});

describe("denuncias de administración", () => {
  it("lista las sin resolver, las más antiguas primero, con el contexto; resolver la quita", async () => {
    const dueña = await shur();
    const denunciante = await shur();
    const c = await cancion(1);
    const pl = await playlist(dueña.id, [c], "Cañeo");
    await env.DB.batch([
      env.DB.prepare("INSERT INTO denuncias (id, tipo, objetivo, cuenta, motivo, creada, resuelta) VALUES ('d2', 'cancion', ?, ?, 'mala', ?, 0)").bind(c, denunciante.id, T0 + 2),
      env.DB.prepare("INSERT INTO denuncias (id, tipo, objetivo, cuenta, motivo, creada, resuelta) VALUES ('d1', 'playlist', ?, ?, 'ofensiva', ?, 0)").bind(pl, denunciante.id, T0 + 1),
      env.DB.prepare("INSERT INTO denuncias (id, tipo, objetivo, cuenta, motivo, creada, resuelta) VALUES ('d0', 'playlist', ?, ?, 'vieja', ?, 1)").bind(pl, denunciante.id, T0),
    ]);
    const r = await admin("GET", "/v1/admin/denuncias");
    expect(r.status).toBe(200);
    expect(r.cuerpo.denuncias).toEqual([
      { id: "d1", tipo: "playlist", objetivo: pl, cuenta: denunciante.id, motivo: "ofensiva", creada: T0 + 1, nombre: "Cañeo", autor: "YoMismo1", autorId: dueña.id },
      { id: "d2", tipo: "cancion", objetivo: c, cuenta: denunciante.id, motivo: "mala", creada: T0 + 2, titulo: "Tema 1", artista: "Grupo 1" },
    ]);
    expect((await admin("POST", "/v1/admin/resolver", { denuncia: "d1" })).status).toBe(200);
    expect((await admin("GET", "/v1/admin/denuncias")).cuerpo.denuncias.map((x: any) => x.id)).toEqual(["d2"]);
    expect((await admin("POST", "/v1/admin/resolver", { denuncia: "nada" })).status).toBe(404);
    expect((await admin("POST", "/v1/admin/resolver", {})).status).toBe(400);
  });
});

describe("POST /v1/admin/fusionar", () => {
  it("fusionarNoDuplicaVotos: la misma cuenta votó las dos -> 1 voto, con el voto más antiguo", async () => {
    const a = await shur();
    const b = await shur();
    const c = await shur();
    const de = await cancion(1);
    const dest = await cancion(2);
    await proponerDirecto(W45, de, a.id, T0 + 10);
    await proponerDirecto(W45, dest, b.id, T0 + 20);
    await votarDirecto(W45, de, a.id, T0 + 100); // a vota las dos: queda el de T0+100 (más antiguo)
    await votarDirecto(W45, dest, a.id, T0 + 200);
    await votarDirecto(W45, de, b.id, T0 + 300); // solo en la fusionada
    await votarDirecto(W45, dest, c.id, T0 + 400); // solo en la destino
    const r = await admin("POST", "/v1/admin/fusionar", { de, en: dest });
    expect(r.status, JSON.stringify(r.cuerpo)).toBe(200);
    expect(await filas("SELECT cancion, cuenta, creado FROM votos ORDER BY cuenta")).toEqual(
      [
        { cuenta: a.id, creado: T0 + 100 },
        { cuenta: b.id, creado: T0 + 300 },
        { cuenta: c.id, creado: T0 + 400 },
      ].map((v) => ({ cancion: dest, ...v })).sort((x, y) => (x.cuenta < y.cuenta ? -1 : 1)),
    );
    // Las dos propuestas de la semana: queda una, la más antigua (la de `de`, con su cuenta).
    expect(await filas("SELECT cancion, cuenta, creada FROM propuestas")).toEqual([{ cancion: dest, cuenta: a.id, creada: T0 + 10 }]);
    expect(await filas("SELECT 1 FROM canciones WHERE id = ?", de)).toHaveLength(0);
  });

  it("mueve enlaces, referencias y propuestas de semanas distintas; mantiene la fila de la semana", async () => {
    const a = await shur();
    const de = await cancion(1);
    const dest = await cancion(2);
    // `de` tiene además un enlace de Spotify que `dest` no tiene.
    await env.DB.batch([
      env.DB.prepare("UPDATE canciones SET enlaces = json_set(enlaces, '$.spotify', 'https://open.spotify.com/track/0aaaaaaaaaaaaaaaaaaaaa') WHERE id = ?").bind(de),
      env.DB.prepare("INSERT INTO enlaces_cancion (ref, cancion) VALUES ('spotify:0aaaaaaaaaaaaaaaaaaaaa', ?)").bind(de),
    ]);
    await proponerDirecto(W45, de, a.id);
    await proponerDirecto("2026-W44", dest, a.id);
    await env.DB.prepare("UPDATE semanas SET cerrada = 1 WHERE id = '2026-W44'").run();
    await admin("POST", "/v1/admin/fusionar", { de, en: dest });
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
    expect(await filas("SELECT semana, cancion FROM propuestas ORDER BY semana")).toEqual([
      { semana: "2026-W44", cancion: dest },
      { semana: W45, cancion: dest },
    ]);
    expect(await filas("SELECT id FROM semanas WHERE id = ?", W45)).toHaveLength(1);
  });

  it("cambia la fusionada por la destino en todas las playlists, sin duplicar y respetando el orden", async () => {
    const a = await shur();
    const [c1, c2, c3] = [await cancion(1), await cancion(2), await cancion(3)];
    const p1 = await playlist(a.id, [c3, c1, c2]); // tiene las dos: queda c3, c2 (c1 -> c2 en su sitio, c2 ya está después... el primero gana)
    const p2 = await playlist(a.id, [c1, c3]);
    const p3 = await playlist(a.id, [c3]); // no la tiene: no se toca
    await admin("POST", "/v1/admin/fusionar", { de: c1, en: c2 });
    const lista = async (id: string) => JSON.parse((await filas("SELECT canciones FROM playlists_pub WHERE id = ?", id))[0].canciones);
    expect(await lista(p1)).toEqual([c3, c2]);
    expect(await lista(p2)).toEqual([c2, c3]);
    expect(await lista(p3)).toEqual([c3]);
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
