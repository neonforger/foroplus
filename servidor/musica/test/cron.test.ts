import { env } from "cloudflare:workers";
import { beforeEach, describe, expect, it } from "vitest";
import { crearApp } from "../src/app";
import { DIA, deps, enT, filas, reiniciarCuentas, shur, T0, TABLAS } from "./ayudas-admin";

const CRON_FEED = "*/15 * * * *";
const LIMPIEZA = "30 3 * * *";

beforeEach(async () => {
  reiniciarCuentas();
  await env.DB.batch(TABLAS.map((t) => env.DB.prepare(`DELETE FROM ${t}`)));
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

describe("programado: feed cada 15 minutos", () => {
  it("el cron del feed no falla (S7 lo rellenará)", async () => {
    await expect(enT(T0).programado(CRON_FEED)).resolves.toBeUndefined();
  });

  it("el cron del domingo ya no existe: una cadena antigua no hace nada", async () => {
    await expect(enT(T0).programado("0 22,23 * * SUN")).resolves.toBeUndefined();
  });
});

describe("programado sin cron conocido", () => {
  it("acepta cualquier cadena sin lanzar", async () => {
    await expect(crearApp(deps()).programado("nada")).resolves.toBeUndefined();
  });
});
