import { env } from "cloudflare:workers";
import { beforeEach, describe, expect, it } from "vitest";
import { uidHmac } from "../src/cripto";
import {
  cancion, conToken, corazonDirecto, DIA, en, filas, HORA, llamar, MIN, peticion, publicacionDirecta, reiniciarCuentas,
  shur, T0, TABLAS, type App, type Shur,
} from "./ayudas-admin";

beforeEach(async () => {
  reiniciarCuentas();
  await env.DB.batch(TABLAS.map((t) => env.DB.prepare(`DELETE FROM ${t}`)));
});

const denunciar = (s: Shur, cuerpo: unknown, a: App = en()) =>
  llamar(a, peticion("POST", "/v1/denuncias", cuerpo, conToken(s)));
const borrarCuenta = (s: Shur, a: App = en()) => llamar(a, peticion("DELETE", "/v1/cuenta", undefined, conToken(s)));
const yo = (s: Shur, a: App = en()) => llamar(a, peticion("GET", "/v1/yo", undefined, conToken(s)));

describe("POST /v1/denuncias", () => {
  it("denuncia a una canción como antes", async () => {
    const b = await shur();
    const c = await cancion(1);
    expect((await denunciar(b, { tipo: "cancion", objetivo: c, motivo: "no es música" })).status).toBe(200);
    expect(await filas("SELECT tipo, objetivo, cuenta, motivo, resuelta FROM denuncias")).toEqual([
      { tipo: "cancion", objetivo: c, cuenta: b.id, motivo: "no es música", resuelta: 0 },
    ]);
    expect((await denunciar(b, { tipo: "cancion", objetivo: "0".repeat(26), motivo: "x" })).status).toBe(404);
  });

  it("denuncia a una publicación visible; caducada, oculta o inexistente dan 404", async () => {
    const a = await shur();
    const b = await shur();
    const c = await cancion(1);
    const viva = await publicacionDirecta(a.id, c);
    const caducada = await publicacionDirecta(a.id, c, { creada: T0 - DIA - 1 });
    const oculta = await publicacionDirecta(a.id, c, { oculta: true });
    expect((await denunciar(b, { tipo: "publicacion", objetivo: viva, motivo: "ofensiva" })).status).toBe(200);
    expect(await filas("SELECT tipo, objetivo, cuenta FROM denuncias")).toEqual([{ tipo: "publicacion", objetivo: viva, cuenta: b.id }]);
    for (const id of [caducada, oculta, "0".repeat(26)]) {
      expect((await denunciar(b, { tipo: "publicacion", objetivo: id, motivo: "x" })).status, id).toBe(404);
    }
    // Una publicación en su último milisegundo de vida todavía se puede denunciar; al siguiente, no.
    const c2 = await shur();
    expect((await denunciar(c2, { tipo: "publicacion", objetivo: viva, motivo: "x" }, en(DIA - 1))).status).toBe(200);
    const d = await shur();
    expect((await denunciar(d, { tipo: "publicacion", objetivo: viva, motivo: "x" }, en(DIA))).status).toBe(404);
  });

  it("el tipo playlist ya no existe; valida tipo, objetivo y motivo (hasta 300)", async () => {
    const a = await shur();
    const c = await cancion(1);
    expect((await denunciar(a, { tipo: "playlist", objetivo: c, motivo: "x" })).status).toBe(400);
    expect((await denunciar(a, { tipo: "usuario", objetivo: c, motivo: "x" })).status).toBe(400);
    expect((await denunciar(a, { tipo: "cancion", objetivo: 5, motivo: "x" })).status).toBe(400);
    expect((await denunciar(a, { tipo: "cancion", objetivo: c, motivo: "x".repeat(301) })).status).toBe(400);
    expect((await denunciar(a, { tipo: "cancion", objetivo: c })).status).toBe(400);
    expect((await denunciar(a, { tipo: "cancion", objetivo: c, motivo: "x".repeat(300) })).status).toBe(200);
  });

  it("denuncia repetida no gasta cupo: 5 al día, la sexta da 429 y al día siguiente vuelve a valer", async () => {
    const a = await shur();
    const ids: string[] = [];
    for (let i = 1; i <= 6; i++) ids.push(await cancion(i));
    for (let i = 0; i < 5; i++) {
      expect((await denunciar(a, { tipo: "cancion", objetivo: ids[i], motivo: "x" })).status).toBe(200);
    }
    const r = await denunciar(a, { tipo: "cancion", objetivo: ids[5], motivo: "x" });
    expect(r.status).toBe(429);
    expect(r.cuerpo.error).toBe("limite");
    expect(await filas("SELECT id FROM denuncias WHERE cuenta = ?", a.id)).toHaveLength(5);
    // Repetir una ya hecha no gasta cupo ni falla ni crea otra fila.
    expect((await denunciar(a, { tipo: "cancion", objetivo: ids[0], motivo: "otra vez" })).status).toBe(200);
    expect(await filas("SELECT id FROM denuncias WHERE cuenta = ?", a.id)).toHaveLength(5);
    expect((await denunciar(a, { tipo: "cancion", objetivo: ids[5], motivo: "x" }, en(DIA))).status).toBe(200);
  });

  it("exige token", async () => {
    expect((await llamar(en(), peticion("POST", "/v1/denuncias", { tipo: "cancion", objetivo: "x", motivo: "x" }))).status).toBe(401);
  });
});

describe("DELETE /v1/cuenta", () => {
  it("borrarCuentaLoBorraTodo: sus publicaciones, corazones dados y recibidos, denuncias, retos y límites; el token da 401", async () => {
    const a = await shur();
    const b = await shur();
    const c = await cancion(1);
    const suya = await publicacionDirecta(a.id, c, { corazones: 1 });
    const deB = await publicacionDirecta(b.id, c, { corazones: 2 });
    const deB2 = await publicacionDirecta(b.id, c, { corazones: 1 });
    await corazonDirecto(suya, b.id); // alguien le dio corazón a la suya
    await corazonDirecto(deB, a.id); // y ella dio a dos de B
    await corazonDirecto(deB, (await shur()).id);
    await corazonDirecto(deB2, a.id);
    await denunciar(a, { tipo: "publicacion", objetivo: deB, motivo: "x" });
    await denunciar(b, { tipo: "publicacion", objetivo: suya, motivo: "x" });
    await env.DB.batch([
      env.DB.prepare("INSERT INTO retos (id, uid, uid_hmac, coche, caduca) VALUES ('r', '123456', 'hmac1', 'x', ?)").bind(T0 + MIN),
      env.DB.prepare("INSERT INTO limites (clave, ventana, n) VALUES (?, 0, 1)").bind("denuncia:" + a.id),
    ]);
    const r = await borrarCuenta(a);
    expect(r.status).toBe(200);
    for (const [tabla, col] of [
      ["cuentas", "id"], ["tokens", "cuenta"], ["publicaciones", "cuenta"], ["corazones", "cuenta"], ["denuncias", "cuenta"],
    ]) {
      expect(await filas(`SELECT 1 FROM ${tabla} WHERE ${col} = ?`, a.id), tabla).toHaveLength(0);
    }
    expect(await filas("SELECT 1 FROM corazones WHERE publicacion = ?", suya)).toHaveLength(0);
    expect(await filas("SELECT 1 FROM denuncias WHERE objetivo = ?", suya)).toHaveLength(0);
    expect(await filas("SELECT 1 FROM retos")).toHaveLength(0);
    expect(await filas("SELECT 1 FROM limites WHERE clave LIKE ?", "%" + a.id + "%")).toHaveLength(0);
    // El token ya no vale.
    expect((await yo(a)).status).toBe(401);
    // Los contadores de las publicaciones de B se recuentan sin sus corazones (2 -> 1, 1 -> 0).
    expect(await filas("SELECT id, corazones FROM publicaciones ORDER BY corazones DESC")).toEqual([
      { id: deB, corazones: 1 },
      { id: deB2, corazones: 0 },
    ]);
    // Lo de los demás sigue.
    expect(await filas("SELECT 1 FROM cuentas WHERE id = ?", b.id)).toHaveLength(1);
    expect(await filas("SELECT 1 FROM denuncias WHERE cuenta = ?", b.id)).toHaveLength(0);
  });

  it("exige token", async () => {
    expect((await llamar(en(), peticion("DELETE", "/v1/cuenta"))).status).toBe(401);
  });

  it("borrar cuenta deja lápida si está baneada, y el baneo sigue", async () => {
    const a = await shur();
    const b = await shur();
    const uid = "123456";
    await env.DB.prepare("UPDATE cuentas SET uid_hmac = ? WHERE id = ?").bind(await uidHmac("pimienta", uid), a.id).run();
    const c = await cancion(1);
    const mia = await publicacionDirecta(a.id, c, { corazones: 1 });
    await corazonDirecto(mia, b.id);
    // Se banea por la ruta REAL de administración: un UPDATE directo no vio que el baneo borraba los tokens
    // y dejaba esta ruta inalcanzable.
    const ban = await llamar(en(), peticion("POST", "/v1/admin/banear", { cuenta: a.id, baneada: true }, { "X-Admin": "admin" }));
    expect(ban.status).toBe(200);
    // El resto de rutas de cuenta siguen cerradas a una baneada.
    expect((await yo(a)).cuerpo).toEqual({ error: "baneada" });
    expect((await borrarCuenta(a)).status).toBe(200);
    expect(await filas("SELECT 1 FROM tokens WHERE cuenta = ?", a.id)).toHaveLength(0);
    expect(await filas("SELECT 1 FROM publicaciones WHERE cuenta = ?", a.id)).toHaveLength(0);
    expect(await filas("SELECT 1 FROM corazones WHERE publicacion = ?", mia)).toHaveLength(0);
    expect((await borrarCuenta(a)).status).toBe(401);
    // Solo queda la lápida.
    expect(await filas("SELECT * FROM cuentas WHERE id = ?", a.id)).toEqual([
      { id: a.id, uid_hmac: await uidHmac("pimienta", uid), nombre: "", registro_fc: null, baneada: 1, creada: null, ultima_verif: null },
    ]);
    // Y volver a verificar la misma cuenta de FC sigue dando 403 baneada.
    const reto = await llamar(en(), peticion("POST", "/v1/verificacion/reto", { uid }));
    expect(reto.status).toBe(403);
    expect(reto.cuerpo).toEqual({ error: "baneada" });
  });
});

describe("GET /v1/yo", () => {
  it("yo devuelve publicacionesRestantes 6 sin publicaciones, y la nueva forma", async () => {
    const a = await shur();
    const r = await yo(a);
    expect(r.status).toBe(200);
    expect(r.cuerpo).toEqual({ nombre: "YoMismo1", diasCuenta: 400, puedePublicar: true, publicacionesRestantes: 6 });
  });

  it("yo con 2 publicaciones en 24 h devuelve 4 restantes; solo cuentan las de esta cuenta", async () => {
    const a = await shur();
    const b = await shur();
    const c = await cancion(1);
    await publicacionDirecta(a.id, c, { creada: T0 - HORA });
    await publicacionDirecta(a.id, c, { creada: T0 - 2 * HORA });
    await publicacionDirecta(b.id, c);
    expect((await yo(a)).cuerpo.publicacionesRestantes).toBe(4);
  });

  it("yo cuenta las de hace 25 h como libres", async () => {
    const a = await shur();
    const c = await cancion(1);
    await publicacionDirecta(a.id, c, { creada: T0 - 25 * HORA });
    await publicacionDirecta(a.id, c, { creada: T0 - 23 * HORA });
    expect((await yo(a)).cuerpo.publicacionesRestantes).toBe(5);
  });

  it("nunca baja de 0", async () => {
    const a = await shur();
    const c = await cancion(1);
    for (let i = 0; i < 8; i++) await publicacionDirecta(a.id, c, { creada: T0 - i * MIN });
    expect((await yo(a)).cuerpo.publicacionesRestantes).toBe(0);
  });

  it("puedePublicar es false con una cuenta de menos de 90 días", async () => {
    const a = await shur();
    await env.DB.prepare("UPDATE cuentas SET registro_fc = ? WHERE id = ?").bind("01-oct-2026", a.id).run();
    expect((await yo(a)).cuerpo).toMatchObject({ puedePublicar: false });
  });
});
