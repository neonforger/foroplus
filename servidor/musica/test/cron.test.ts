import { env } from "cloudflare:workers";
import { beforeEach, describe, expect, it } from "vitest";
import { crearApp } from "../src/app";
import {
  cancion, DIA, deps, enT, filas, HORA, proponerDirecto, reiniciarCuentas, shur, T0, TABLAS, votarDirecto, W45,
} from "./ayudas-admin";

const CIERRE_SEMANAL = "0 22,23 * * SUN";
const LIMPIEZA = "30 3 * * *";
const iso = (texto: string) => enT(new Date(texto).getTime());

/** Una semana con una canción propuesta y un voto, lista para cerrarse. */
async function semanaConConcurso(semana: string): Promise<void> {
  const a = await shur();
  const c = await cancion(1);
  await proponerDirecto(semana, c, a.id);
  await votarDirecto(semana, c, a.id);
}
const cerrada = async (semana: string) =>
  (await filas("SELECT cerrada FROM semanas WHERE id = ?", semana))[0]?.cerrada ?? null;

beforeEach(async () => {
  reiniciarCuentas();
  await env.DB.batch(TABLAS.map((t) => env.DB.prepare(`DELETE FROM ${t}`)));
});

describe("programado: cierre semanal", () => {
  it("cronInvierno: a las 23:00Z del domingo (00:00 del lunes en Madrid) cierra la W45; a las 22:00Z no cierra nada", async () => {
    await semanaConConcurso(W45);
    await iso("2026-11-08T22:00:00Z").programado(CIERRE_SEMANAL);
    expect(await cerrada(W45)).toBe(0);
    await iso("2026-11-08T23:00:00Z").programado(CIERRE_SEMANAL);
    expect(await cerrada(W45)).toBe(1);
    const top = JSON.parse((await filas("SELECT top FROM semanas WHERE id = ?", W45))[0].top);
    expect(top).toHaveLength(1);
    expect(top[0].votos).toBe(1);
  });

  it("cronVerano: a las 22:00Z del domingo (00:00 del lunes en Madrid) cierra la W27; a las 21:00Z no", async () => {
    await semanaConConcurso("2026-W27");
    await iso("2026-07-05T21:00:00Z").programado(CIERRE_SEMANAL);
    expect(await cerrada("2026-W27")).toBe(0);
    await iso("2026-07-05T22:00:00Z").programado(CIERRE_SEMANAL);
    expect(await cerrada("2026-W27")).toBe(1);
  });

  it("en verano la ejecución de las 23:00Z ya es la 01:00 del lunes: no toca lo ya cerrado", async () => {
    await semanaConConcurso("2026-W27");
    await iso("2026-07-05T22:00:00Z").programado(CIERRE_SEMANAL);
    const antes = await filas("SELECT * FROM semanas WHERE id = '2026-W27'");
    await iso("2026-07-05T23:00:00Z").programado(CIERRE_SEMANAL);
    expect(await filas("SELECT * FROM semanas WHERE id = '2026-W27'")).toEqual(antes);
  });

  it("cronIdempotente: lanzarlo dos veces deja el mismo top, aunque entre medias entre un voto", async () => {
    await semanaConConcurso(W45);
    const app = iso("2026-11-08T23:00:00Z");
    await app.programado(CIERRE_SEMANAL);
    const antes = await filas("SELECT * FROM semanas WHERE id = ?", W45);
    const otra = await shur();
    const c = (await filas("SELECT cancion FROM propuestas"))[0].cancion;
    await votarDirecto(W45, c, otra.id);
    await app.programado(CIERRE_SEMANAL);
    expect(await filas("SELECT * FROM semanas WHERE id = ?", W45)).toEqual(antes);
  });

  it("un cron desconocido no hace nada", async () => {
    await semanaConConcurso(W45);
    await iso("2026-11-08T23:00:00Z").programado("* * * * *");
    expect(await cerrada(W45)).toBe(0);
  });
});

describe("programado: limpieza diaria", () => {
  it("limpieza: borra retos y tokens caducados y límites de más de 2 días, y deja lo demás", async () => {
    const a = await shur(); // su token vive hasta T0 + 365 días
    await env.DB.batch([
      env.DB.prepare("INSERT INTO retos (id, uid, uid_hmac, coche, caduca) VALUES ('r1', '123456', 'h', 'Seat Ibiza 1.9 TDI 2004', ?)").bind(T0 - 1),
      env.DB.prepare("INSERT INTO retos (id, uid, uid_hmac, coche, caduca) VALUES ('r2', '123456', 'h', 'Seat Ibiza 1.9 TDI 2004', ?)").bind(T0 + 5 * 60_000),
      env.DB.prepare("INSERT INTO tokens (hash, cuenta, creado, caduca) VALUES ('viejo', ?, ?, ?)").bind(a.id, T0 - 400 * DIA, T0 - 1),
      env.DB.prepare("INSERT INTO limites (clave, ventana, n) VALUES ('x', ?, 3)").bind(Math.floor((T0 - 3 * DIA) / 1000)),
      env.DB.prepare("INSERT INTO limites (clave, ventana, n) VALUES ('y', ?, 3)").bind(Math.floor((T0 - DIA) / 1000)),
    ]);
    await enT(T0).programado(LIMPIEZA);
    expect((await filas("SELECT id FROM retos")).map((f) => f.id)).toEqual(["r2"]);
    expect(await filas("SELECT hash FROM tokens WHERE hash = 'viejo'")).toHaveLength(0);
    expect(await filas("SELECT hash FROM tokens")).toHaveLength(1);
    expect((await filas("SELECT clave FROM limites")).map((f) => f.clave)).toEqual(["y"]);
  });
});

describe("programado: subidas raras de votos", () => {
  /** `total` votos de las últimas horas a una canción de la semana en curso (W45), `nuevas` de cuentas nuevas. */
  async function subida(nuevas: number, total = 10, hace = HORA): Promise<string> {
    const c = await cancion(1);
    const autora = await shur();
    await proponerDirecto(W45, c, autora.id);
    for (let i = 0; i < total; i++) {
      const s = await shur(i < nuevas ? { creada: T0 - 2 * DIA } : {});
      await votarDirecto(W45, c, s.id, T0 - hace);
    }
    return c;
  }
  const sistema = () => filas("SELECT * FROM denuncias WHERE tipo = 'sistema'");

  it("senalaSubidaRara: 8 de 10 votos de cuentas nuevas en 24 h -> denuncia de sistema; no se duplica y no castiga", async () => {
    const c = await subida(8);
    await enT(T0).programado(LIMPIEZA);
    const d = await sistema();
    expect(d).toHaveLength(1);
    expect(d[0]).toMatchObject({ tipo: "sistema", objetivo: c, resuelta: 0 });
    expect(d[0].motivo).toContain("8 de 10");
    // Segunda pasada con la abierta sin resolver: no crea otra.
    await enT(T0 + 1000).programado(LIMPIEZA);
    expect(await sistema()).toHaveLength(1);
    // Solo señala: ni votos ni cuentas tocados.
    expect(await filas("SELECT 1 FROM votos")).toHaveLength(10);
    expect(await filas("SELECT 1 FROM cuentas WHERE baneada = 1")).toHaveLength(0);
    // Resuelta, si el patrón sigue, vuelve a señalarse.
    await env.DB.prepare("UPDATE denuncias SET resuelta = 1").run();
    await enT(T0 + 2000).programado(LIMPIEZA);
    expect(await sistema()).toHaveLength(2);
  });

  it("no señala con 6 de 10 cuentas nuevas (60 %)", async () => {
    await subida(6);
    await enT(T0).programado(LIMPIEZA);
    expect(await sistema()).toHaveLength(0);
  });

  it("no señala con solo 9 votos aunque todos sean de cuentas nuevas", async () => {
    await subida(9, 9);
    await enT(T0).programado(LIMPIEZA);
    expect(await sistema()).toHaveLength(0);
  });

  it("no cuenta los votos de hace más de 24 h", async () => {
    await subida(10, 10, 25 * HORA);
    await enT(T0).programado(LIMPIEZA);
    expect(await sistema()).toHaveLength(0);
  });

  it("solo mira la semana en curso", async () => {
    const c = await cancion(1);
    const autora = await shur();
    await proponerDirecto("2026-W44", c, autora.id);
    for (let i = 0; i < 10; i++) await votarDirecto("2026-W44", c, (await shur({ creada: T0 - DIA })).id, T0 - HORA);
    await enT(T0).programado(LIMPIEZA);
    expect(await sistema()).toHaveLength(0);
  });
});

describe("programado sin cron conocido", () => {
  it("acepta cualquier cadena sin lanzar", async () => {
    await expect(crearApp(deps()).programado("nada")).resolves.toBeUndefined();
  });
});
