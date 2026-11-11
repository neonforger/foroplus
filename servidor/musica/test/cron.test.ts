import { env } from "cloudflare:workers";
import { beforeEach, describe, expect, it } from "vitest";
import { crearApp } from "../src/app";
import { semanaDe } from "../src/tiempo";
import { cancion, corazonDirecto, DIA, deps, enT, filas, HORA, publicacionDirecta, reiniciarCuentas, shur, T0, TABLAS } from "./ayudas-admin";

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

describe("programado: limpieza diaria (feed)", () => {
  it("borra la más querida de hace más de 8 semanas y deja las recientes", async () => {
    const vieja = semanaDe(new Date(T0 - 9 * 7 * DIA));
    const reciente = semanaDe(new Date(T0 - 2 * 7 * DIA));
    await env.DB.batch([vieja, reciente].map((s) =>
      env.DB.prepare("INSERT INTO mas_querida (semana, cancion, autor, texto, corazones) VALUES (?, 'c', 'a', 't', 3)").bind(s)));
    await enT(T0).programado(LIMPIEZA);
    expect((await filas("SELECT semana FROM mas_querida")).map((f) => f.semana)).toEqual([reciente]);
  });

  it("una conversión en curso con 24 intentos y más de 1 h pasa a error; las demás no", async () => {
    const [a, b, c, d] = [await cancion(1), await cancion(2), await cancion(3), await cancion(4)];
    const poner = (id: string, estado: string, intentos: number, en: number) =>
      env.DB.prepare("UPDATE canciones SET conversion = ?, conversion_intentos = ?, conversion_en = ? WHERE id = ?").bind(estado, intentos, en, id);
    await env.DB.batch([
      poner(a, "en_curso", 24, T0 - 2 * HORA), // atascada
      poner(b, "en_curso", 23, T0 - 2 * HORA), // le quedan intentos
      poner(c, "en_curso", 30, T0 - HORA / 2), // intento reciente
      poner(d, "hecha", 30, T0 - 2 * HORA), // no está en curso
    ]);
    await enT(T0).programado(LIMPIEZA);
    const est = Object.fromEntries((await filas("SELECT id, conversion FROM canciones")).map((f) => [f.id, f.conversion]));
    expect(est).toEqual({ [a]: "error", [b]: "en_curso", [c]: "en_curso", [d]: "hecha" });
  });
});

describe("programado: feed cada 15 minutos", () => {
  const SEMANA = (t: number) => semanaDe(new Date(t));
  const quedan = async () => (await filas("SELECT id FROM publicaciones")).map((f) => f.id);

  it("a las 24 h y 15 min la publicación ya no está en D1; una de 23 h sí", async () => {
    const a = await shur(); const c = await cancion(1);
    await publicacionDirecta(a.id, c, { creada: T0 - DIA - 15 * 60_000 });
    const viva = await publicacionDirecta(a.id, c, { creada: T0 - 23 * HORA });
    await enT(T0).programado(CRON_FEED);
    expect(await quedan()).toEqual([viva]);
  });

  it("sus corazones también se borran, y los de las vivas se quedan", async () => {
    const [a, b] = [await shur(), await shur()]; const c = await cancion(1);
    const vieja = await publicacionDirecta(a.id, c, { creada: T0 - DIA - 1000, corazones: 1 });
    const viva = await publicacionDirecta(a.id, c, { creada: T0 - HORA, corazones: 1 });
    await corazonDirecto(vieja, b.id); await corazonDirecto(viva, b.id);
    await enT(T0).programado(CRON_FEED);
    expect((await filas("SELECT publicacion FROM corazones")).map((f) => f.publicacion)).toEqual([viva]);
  });

  it("guarda la más querida de su semana con el nombre del autor", async () => {
    const [a, b] = [await shur(), await shur()]; const c = await cancion(1);
    const creada = T0 - DIA - HORA;
    await publicacionDirecta(a.id, c, { creada, corazones: 2, texto: "menos" });
    await publicacionDirecta(b.id, c, { creada: creada + 1000, corazones: 5, texto: "la mejor" });
    await enT(T0).programado(CRON_FEED);
    expect(await filas("SELECT * FROM mas_querida")).toEqual([
      { semana: SEMANA(creada), cancion: c, autor: "YoMismo2", texto: "la mejor", corazones: 5 },
    ]);
  });

  it("una con menos corazones no pisa a la guardada; una con más sí", async () => {
    const a = await shur(); const c = await cancion(1);
    const creada = T0 - DIA - HORA;
    await env.DB.prepare("INSERT INTO mas_querida (semana, cancion, autor, texto, corazones) VALUES (?, 'x', 'Otro', 'guardada', 4)").bind(SEMANA(creada)).run();
    await publicacionDirecta(a.id, c, { creada, corazones: 3, texto: "menos" });
    await enT(T0).programado(CRON_FEED);
    expect(await filas("SELECT texto, corazones FROM mas_querida")).toEqual([{ texto: "guardada", corazones: 4 }]);
    await publicacionDirecta(a.id, c, { creada: creada + 60_000, corazones: 6, texto: "más" });
    await enT(T0).programado(CRON_FEED);
    expect(await filas("SELECT texto, corazones FROM mas_querida")).toEqual([{ texto: "más", corazones: 6 }]);
  });

  it("empate: se queda la más antigua, en la misma tanda y entre tandas", async () => {
    const [a, b] = [await shur(), await shur()]; const c = await cancion(1);
    const creada = T0 - 2 * DIA;
    await publicacionDirecta(b.id, c, { creada: creada + 5000, corazones: 3, texto: "nueva" });
    await publicacionDirecta(a.id, c, { creada, corazones: 3, texto: "antigua" });
    await enT(T0).programado(CRON_FEED);
    expect((await filas("SELECT texto FROM mas_querida")).map((f) => f.texto)).toEqual(["antigua"]);
    await publicacionDirecta(b.id, c, { creada: creada + 9000, corazones: 3, texto: "otra" });
    await enT(T0).programado(CRON_FEED);
    expect((await filas("SELECT texto FROM mas_querida")).map((f) => f.texto)).toEqual(["antigua"]);
  });

  it("ocultas y de baneados no compiten, pero se borran igualmente", async () => {
    const [a, malo] = [await shur(), await shur({ baneada: true })]; const c = await cancion(1);
    const creada = T0 - DIA - HORA;
    await publicacionDirecta(a.id, c, { creada, corazones: 1, texto: "normal" });
    await publicacionDirecta(a.id, c, { creada, corazones: 9, oculta: true, texto: "oculta" });
    await publicacionDirecta(malo.id, c, { creada, corazones: 8, texto: "baneado" });
    await enT(T0).programado(CRON_FEED);
    expect((await filas("SELECT texto FROM mas_querida")).map((f) => f.texto)).toEqual(["normal"]);
    expect(await quedan()).toEqual([]);
  });

  it("sin nada caducado no falla ni escribe", async () => {
    await expect(enT(T0).programado(CRON_FEED)).resolves.toBeUndefined();
    expect(await filas("SELECT semana FROM mas_querida")).toHaveLength(0);
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
