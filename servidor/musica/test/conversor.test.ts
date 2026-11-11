import { env } from "cloudflare:workers";
import { beforeEach, describe, expect, it } from "vitest";
import {
  admin, cancion, en, filas, HORA, llamar, MIN, peticion, publicacionDirecta, reiniciarCuentas, shur, T0, TABLAS, type Json,
} from "./ayudas-admin";
import { RECLAMAR } from "../src/rutas/conversor";

beforeEach(async () => {
  reiniciarCuentas();
  await env.DB.batch(TABLAS.map((t) => env.DB.prepare(`DELETE FROM ${t}`)));
});

const CONV = { "X-Conversor": "conversor" };
const SP = "4uLU6hMCjMI75M1A2tKUQC";
const SP2 = "0VjIjW4GlUZAMYd2vXMi3b";

/** Pone una canción de prueba en un estado de conversión. */
const estado = (id: string, conversion: string, enMs: number | null = null, intentos = 0) =>
  env.DB.prepare("UPDATE canciones SET conversion = ?, conversion_en = ?, conversion_intentos = ? WHERE id = ?")
    .bind(conversion, enMs, intentos, id).run();
const fila = async (id: string) =>
  (await filas("SELECT conversion, conversion_en, conversion_intentos, spotify FROM canciones WHERE id = ?", id))[0];
const pendientes = (a = en(), cab: Record<string, string> = CONV) =>
  llamar(a, peticion("GET", "/v1/conversor/pendientes", undefined, cab));
const resultado = (cuerpo: unknown, a = en()) => llamar(a, peticion("POST", "/v1/conversor/resultado", cuerpo, CONV));

describe("GET /v1/conversor/pendientes", () => {
  it("sin secreto → 401 conversor", async () => {
    for (const cab of [{} as Record<string, string>, { "X-Conversor": "otro" }, { "X-Admin": "admin" }, { "X-Lector": "lector" }]) {
      const r = await pendientes(en(), cab);
      expect(r.status).toBe(401);
      expect(r.cuerpo).toEqual({ error: "conversor" });
    }
    // Secreto vacío configurado: nadie entra, ni con la cabecera vacía.
    expect((await pendientes(en(0, { conversorSecreto: "" }), { "X-Conversor": "" })).status).toBe(401);
  });

  it("entrega pendientes y los marca en_curso", async () => {
    const id = await cancion(1);
    await estado(id, "pendiente");
    const r = await pendientes();
    expect(r.status).toBe(200);
    expect(r.cuerpo).toEqual({ canciones: [{ id, titulo: "Tema 1", artista: "Grupo 1", isrc: null }] });
    expect(await fila(id)).toMatchObject({ conversion: "en_curso", conversion_en: T0, conversion_intentos: 1 });
  });

  it("no entrega dos veces lo que está en_curso", async () => {
    const id = await cancion(1);
    await estado(id, "pendiente");
    expect((await pendientes()).cuerpo.canciones).toHaveLength(1);
    expect((await pendientes(en(30_000))).cuerpo.canciones).toEqual([]);
  });

  it("entrega como mucho 10", async () => {
    for (let n = 1; n <= 12; n++) await estado(await cancion(n), "pendiente");
    expect((await pendientes()).cuerpo.canciones).toHaveLength(10);
    expect((await pendientes()).cuerpo.canciones).toHaveLength(2);
  });

  it("en_curso de hace 61 s vuelve a entregarse", async () => {
    const id = await cancion(1);
    await estado(id, "en_curso", T0 - 61_000, 1);
    expect((await pendientes()).cuerpo.canciones.map((c: Json) => c.id)).toEqual([id]);
    expect((await fila(id)).conversion_intentos).toBe(2);
    const otra = await cancion(2);
    await estado(otra, "en_curso", T0 - 30_000, 1);
    expect((await pendientes(en(0))).cuerpo.canciones).toEqual([]);
  });

  it("error de hace 1 h se reintenta y a los 24 intentos no", async () => {
    const reciente = await cancion(1);
    const viejo = await cancion(2);
    const agotado = await cancion(3);
    await estado(reciente, "error", T0 - 59 * MIN, 1);
    await estado(viejo, "error", T0 - HORA - 1000, 3);
    await estado(agotado, "error", T0 - 5 * HORA, 24);
    expect((await pendientes()).cuerpo.canciones.map((c: Json) => c.id)).toEqual([viejo]);
    expect((await fila(agotado)).conversion).toBe("error");
    expect((await fila(reciente)).conversion).toBe("error");
  });

  it("en_curso perdida se reparte solo mientras conversion_intentos < 24", async () => {
    const agotada = await cancion(1);
    const viva = await cancion(2);
    await estado(agotada, "en_curso", T0 - 61_000, 24);
    await estado(viva, "en_curso", T0 - 61_000, 23);
    expect((await pendientes()).cuerpo.canciones.map((c: Json) => c.id)).toEqual([viva]);
    expect(await fila(agotada)).toMatchObject({ conversion: "en_curso", conversion_intentos: 24 });
  });

  it("sin trabajo responde [] al instante (sin petición larga: el VPS vuelve a preguntar)", async () => {
    const id = await cancion(1);
    await estado(id, "hecha");
    const r = await pendientes();
    expect(r.status).toBe(200);
    expect(r.cuerpo).toEqual({ canciones: [] });
    expect(r.cabeceras.get("Cache-Control")).toBe("no-store");
    // Lo que llegue después sale en el siguiente sondeo.
    await estado(id, "pendiente");
    expect((await pendientes()).cuerpo.canciones.map((c: Json) => c.id)).toEqual([id]);
  });

  it("el reclamo usa el índice parcial de la cola, no un recorrido de todas las canciones", async () => {
    // La subconsulta de RECLAMAR, como SELECT (EXPLAIN de un UPDATE ... RETURNING no enseña su plan).
    const sub = RECLAMAR.slice(RECLAMAR.indexOf("SELECT id FROM canciones"), RECLAMAR.lastIndexOf(")"));
    const plan = await filas("EXPLAIN QUERY PLAN " + sub, T0, 60_000, HORA, 24, 10);
    const detalle = plan.map((f) => String(f.detail)).join(" | ");
    expect(detalle).toContain("idx_canciones_conv");
    expect(detalle).not.toMatch(/SCAN canciones(?! USING)/);
  });
});

describe("POST /v1/conversor/resultado", () => {
  it("resultado con id hace la canción visible en el feed", async () => {
    const s = await shur();
    const id = await cancion(1);
    await estado(id, "en_curso", T0, 1);
    const pub = await publicacionDirecta(s.id, id, { creada: T0 });
    const feed = () => llamar(en(), peticion("GET", "/v1/feed"));
    expect((await feed()).cuerpo.items).toEqual([]);
    const r = await resultado({ cancion: id, spotify: SP, motivo: null });
    expect(r.status).toBe(204);
    expect(await fila(id)).toMatchObject({ conversion: "hecha", spotify: SP });
    expect(await filas("SELECT cancion FROM enlaces_cancion WHERE ref = ?", "spotify:" + SP)).toEqual([{ cancion: id }]);
    expect((await feed()).cuerpo.items.map((i: Json) => i.id)).toEqual([pub]);
  });

  it("resultado no_esta deja la publicación visible sin spotify", async () => {
    const s = await shur();
    const id = await cancion(1);
    await estado(id, "en_curso", T0, 1);
    const pub = await publicacionDirecta(s.id, id, { creada: T0 });
    expect((await resultado({ cancion: id, spotify: null, motivo: "no_esta" })).status).toBe(204);
    expect(await fila(id)).toMatchObject({ conversion: "no_esta", spotify: null });
    const f = await llamar(en(), peticion("GET", "/v1/feed"));
    expect(f.cuerpo.items.map((i: Json) => i.id)).toEqual([pub]);
  });

  it("motivo error deja la canción en error con la hora", async () => {
    const id = await cancion(1);
    await estado(id, "en_curso", T0 - 1000, 2);
    expect((await resultado({ cancion: id, spotify: null, motivo: "error" }, en(5000))).status).toBe(204);
    expect(await fila(id)).toMatchObject({ conversion: "error", conversion_en: T0 + 5000, conversion_intentos: 2 });
  });

  it("motivo no_esta o error no baja una canción ya hecha", async () => {
    const id = await cancion(1);
    await estado(id, "en_curso", T0, 1);
    expect((await resultado({ cancion: id, spotify: SP, motivo: null })).status).toBe(204);
    for (const motivo of ["no_esta", "error"]) {
      expect((await resultado({ cancion: id, spotify: null, motivo }, en(9000))).status).toBe(204);
    }
    expect(await fila(id)).toMatchObject({ conversion: "hecha", conversion_en: T0, spotify: SP });
  });

  it("si otra canción se lleva el spotify entre la comprobación y el batch → 409 y no se marca hecha", async () => {
    const a = await cancion(1);
    const b = await cancion(2);
    await estado(b, "en_curso", T0, 1);
    // Un db que, justo antes del batch, hace que otra canción reclame el mismo id (la carrera).
    const db = env.DB;
    const carrera = new Proxy(db, {
      get(t, k) {
        if (k === "batch") {
          return async (...args: Parameters<D1Database["batch"]>) => {
            await db.prepare("INSERT INTO enlaces_cancion (ref, cancion) VALUES (?, ?)").bind("spotify:" + SP, a).run();
            return db.batch(...args);
          };
        }
        const v = (t as any)[k];
        return typeof v === "function" ? v.bind(t) : v;
      },
    });
    const r = await resultado({ cancion: b, spotify: SP, motivo: null }, en(0, { db: carrera }));
    expect(r.status).toBe(409);
    expect(r.cuerpo).toEqual({ error: "ocupada" });
    expect(await fila(b)).toMatchObject({ conversion: "no_esta", spotify: null });
    expect(await filas("SELECT cancion FROM enlaces_cancion WHERE ref = ?", "spotify:" + SP)).toEqual([{ cancion: a }]);
  });

  it("id de spotify ya usado por otra canción → 409 ocupada", async () => {
    const a = await cancion(1);
    const b = await cancion(2);
    await env.DB.prepare("INSERT INTO enlaces_cancion (ref, cancion) VALUES (?, ?)").bind("spotify:" + SP, a).run();
    await estado(b, "en_curso", T0, 1);
    const r = await resultado({ cancion: b, spotify: SP, motivo: null });
    expect(r.status).toBe(409);
    expect(r.cuerpo).toEqual({ error: "ocupada" });
    expect(await filas("SELECT cancion FROM enlaces_cancion WHERE ref = ?", "spotify:" + SP)).toEqual([{ cancion: a }]);
    expect(await fila(b)).toMatchObject({ conversion: "no_esta", spotify: null });
  });

  it("el mismo id para la misma canción es idempotente", async () => {
    const a = await cancion(1);
    await estado(a, "en_curso", T0, 1);
    expect((await resultado({ cancion: a, spotify: SP, motivo: null })).status).toBe(204);
    expect((await resultado({ cancion: a, spotify: SP, motivo: null })).status).toBe(204);
    expect(await fila(a)).toMatchObject({ conversion: "hecha", spotify: SP });
  });

  it("cancion desconocida → 404 y cuerpo malo → 400", async () => {
    expect((await resultado({ cancion: "01ARZ3NDEKTSV4RRFFQ69G5FAV", spotify: SP2, motivo: null })).status).toBe(404);
    const id = await cancion(1);
    for (const mal of [
      "no json", {}, { cancion: id }, { cancion: 5, spotify: null, motivo: "error" },
      { cancion: id, spotify: "corto", motivo: null }, { cancion: id, spotify: null, motivo: null },
      { cancion: id, spotify: null, motivo: "raro" }, { cancion: id, spotify: SP, motivo: "error" },
    ]) {
      const r = await resultado(mal);
      expect([r.status, r.cuerpo]).toEqual([400, { error: "cuerpo" }]);
    }
  });

  it("sin secreto → 401", async () => {
    const r = await llamar(en(), peticion("POST", "/v1/conversor/resultado", { cancion: "x" }));
    expect(r.status).toBe(401);
  });
});

describe("latido y /v1/admin/conversor", () => {
  it("latido aparece en admin/conversor", async () => {
    expect((await admin("GET", "/v1/admin/conversor")).cuerpo).toEqual({ latido: null, pendientes: 0, errores: 0 });
    const r = await llamar(en(), peticion("POST", "/v1/conversor/latido", { ok: true, detalle: "yt-dlp 2026.11 ok" }, CONV));
    expect(r.status).toBe(204);
    await estado(await cancion(1), "pendiente");
    await estado(await cancion(2), "en_curso", T0, 1);
    await estado(await cancion(3), "error", T0, 1);
    await estado(await cancion(4), "no_esta", T0, 1);
    const a = await admin("GET", "/v1/admin/conversor", undefined, en(MIN));
    expect(a.cuerpo).toEqual({ latido: { ok: true, detalle: "yt-dlp 2026.11 ok", en: T0 }, pendientes: 2, errores: 1 });
  });

  it("latido con cuerpo malo → 400 y el admin exige X-Admin", async () => {
    for (const mal of [{}, { ok: "si", detalle: "x" }, { ok: true }, "no json"]) {
      expect((await llamar(en(), peticion("POST", "/v1/conversor/latido", mal, CONV))).status).toBe(400);
    }
    expect((await llamar(en(), peticion("GET", "/v1/admin/conversor", undefined, CONV))).status).toBe(401);
  });
});
