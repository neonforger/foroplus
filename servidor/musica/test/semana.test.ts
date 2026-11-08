import { env } from "cloudflare:workers";
import { beforeEach, describe, expect, it } from "vitest";
import deezerEstopa from "./fixtures/deezer_estopa.json";
import deezerVacio from "./fixtures/deezer_vacio.json";
import spEstopaMeta from "./fixtures/sp_estopa_meta.html?raw";
import spEstopaOembed from "./fixtures/sp_estopa_oembed.json";
import ytEstopa from "./fixtures/yt_estopa_oembed.json";
import { crearApp, type Deps } from "../src/app";
import { hashToken, nuevoId, nuevoToken } from "../src/cripto";
import { asegurarCierres, cerrarSemana } from "../src/semana";
import { finDeSemana, semanaDe } from "../src/tiempo";

// Miércoles de la semana 45 de 2026 (lunes 2 a domingo 8 de noviembre). La 46 empieza el lunes 9 a
// las 00:00 de Madrid = 2026-11-08T23:00Z (horario de invierno).
const T0 = new Date("2026-11-04T12:00:00Z").getTime();
const W45 = "2026-W45";
const W46 = "2026-W46";
const EN_W46 = new Date("2026-11-08T23:00:00Z").getTime() - T0;
const MIN = 60_000;
const DIA = 86_400_000;
const TABLAS = [
  "cuentas", "tokens", "retos", "canciones", "enlaces_cancion", "semanas", "propuestas", "votos",
  "playlists_pub", "guardados", "denuncias", "limites",
];

// La misma canción por las dos fuentes (fixtures de tarjeta.test.ts): las dos dan "estopa|como camaron".
const ESTOPA_YT = "https://www.youtube.com/watch?v=aaaaaaaaaaa";
const ESTOPA_SP = "https://open.spotify.com/intl-es/track/0aaaaaaaaaaaaaaaaaaaaa?si=abc";
/** Canción de prueba n: un vídeo de YouTube "Grupo n - Tema n" que Deezer no encuentra. */
const tema = (n: number) => "https://www.youtube.com/watch?v=tema" + String(n).padStart(7, "0");

const YT_OEMBED = "https://www.youtube.com/oembed?url=";
const SP_OEMBED = "https://open.spotify.com/oembed?url=";
const SP_PAGINA = "https://open.spotify.com/track/";
const DEEZER = "https://api.deezer.com/search?";

type Json = Record<string, any>;
const respJson = (o: unknown, status = 200) =>
  new Response(JSON.stringify(o), { status, headers: { "content-type": "application/json" } });

/** YouTube, Spotify y Deezer falsos. Apunta cada petición; una URL que no conoce LANZA. */
let pedidas: string[];
let sinRed: boolean;
async function red(url: string): Promise<Response> {
  pedidas.push(url);
  if (sinRed) throw new Error("sin red");
  if (url.startsWith(YT_OEMBED)) {
    const v = new URL(decodeURIComponent(url.slice(YT_OEMBED.length).split("&")[0])).searchParams.get("v") ?? "";
    if (v === "aaaaaaaaaaa") return respJson(ytEstopa);
    const m = /^tema0*(\d+)$/.exec(v);
    if (m) {
      return respJson({
        title: `Grupo ${m[1]} - Tema ${m[1]} (Official Video)`,
        author_name: "Canal",
        thumbnail_url: `https://i.ytimg.com/vi/${v}/hqdefault.jpg`,
      });
    }
    return new Response("Not Found", { status: 404 });
  }
  if (url.startsWith(SP_OEMBED)) return respJson(spEstopaOembed);
  if (url.startsWith(SP_PAGINA)) return new Response(spEstopaMeta, { headers: { "content-type": "text/html" } });
  if (url.startsWith(DEEZER)) return respJson(decodeURIComponent(url).includes("Estopa") ? deezerEstopa : deezerVacio);
  throw new Error("URL inesperada: " + url);
}

function deps(o: Partial<Deps> = {}): Deps {
  return {
    db: env.DB,
    red,
    ahora: () => new Date(T0),
    aleatorio: () => 0.5,
    pepper: "pimienta",
    adminSecreto: "admin",
    lectorSecreto: "lector",
    diasMinVoto: 90,
    lectorEnVps: false,
    ...o,
  };
}
/** La app con el reloj `ms` después de T0. */
const en = (ms = 0, o: Partial<Deps> = {}) => crearApp(deps({ ahora: () => new Date(T0 + ms), ...o }));
type App = ReturnType<typeof en>;

const MESES = ["ene", "feb", "mar", "abr", "may", "jun", "jul", "ago", "sep", "oct", "nov", "dic"];
/** La fecha de registro de FC (como la escribe la ficha) de hace `dias` días contando desde T0. */
function registroHace(dias: number): string {
  const f = new Date(T0 - dias * DIA);
  return `${String(f.getUTCDate()).padStart(2, "0")}-${MESES[f.getUTCMonth()]}-${f.getUTCFullYear()}`;
}

interface Shur { id: string; token: string }
let numCuenta: number;
/** Una cuenta verificada con su token, directamente en la base. `dias` null = registro ilegible. */
async function shur(dias: number | null = 400, o: { baneada?: boolean } = {}): Promise<Shur> {
  numCuenta++;
  const id = nuevoId(new Date(T0));
  const token = nuevoToken();
  await env.DB.batch([
    env.DB
      .prepare(
        "INSERT INTO cuentas (id, uid_hmac, nombre, registro_fc, baneada, creada, ultima_verif) VALUES (?, ?, ?, ?, ?, ?, ?)",
      )
      .bind(id, "hmac" + numCuenta, "YoMismo" + numCuenta, dias === null ? null : registroHace(dias), o.baneada ? 1 : 0, T0, T0),
    env.DB.prepare("INSERT INTO tokens (hash, cuenta, creado, caduca) VALUES (?, ?, ?, ?)")
      .bind(await hashToken(token), id, T0, T0 + 365 * DIA),
  ]);
  return { id, token };
}

const conToken = (s: Shur) => ({ Authorization: "Bearer " + s.token });
function peticion(metodo: string, ruta: string, cuerpo?: unknown, cab: Record<string, string> = {}): Request {
  return new Request("https://x" + ruta, {
    method: metodo,
    headers: { "Content-Type": "application/json", ...cab },
    body: cuerpo === undefined ? undefined : typeof cuerpo === "string" ? cuerpo : JSON.stringify(cuerpo),
  });
}

async function llamar(a: App, req: Request): Promise<{ status: number; cuerpo: Json; cabeceras: Headers }> {
  const r = await a.fetch(req);
  const texto = await r.text();
  return { status: r.status, cuerpo: texto ? JSON.parse(texto) : null, cabeceras: r.headers };
}
const proponer = (s: Shur, enlace: unknown, a: App = en()) =>
  llamar(a, peticion("POST", "/v1/semana/proponer", { enlace }, conToken(s)));
const votar = (s: Shur, cancion: unknown, a: App = en()) =>
  llamar(a, peticion("POST", "/v1/semana/voto", { cancion }, conToken(s)));
const quitarVoto = (s: Shur, cancion: unknown, a: App = en()) =>
  llamar(a, peticion("DELETE", "/v1/semana/voto", { cancion }, conToken(s)));
const semana = (a: App = en(), s?: Shur) => llamar(a, peticion("GET", "/v1/semana", undefined, s ? conToken(s) : {}));

/** Propone `enlace` y devuelve el id de la canción (falla el test si no da 200). */
async function propuesta(s: Shur, enlace: string, a: App = en()): Promise<string> {
  const r = await proponer(s, enlace, a);
  expect(r.status, JSON.stringify(r.cuerpo)).toBe(200);
  return r.cuerpo.cancion.id;
}

async function filas(sql: string, ...args: unknown[]): Promise<Json[]> {
  return (await env.DB.prepare(sql).bind(...args).all<Json>()).results;
}

beforeEach(async () => {
  pedidas = [];
  sinRed = false;
  numCuenta = 0;
  await env.DB.batch(TABLAS.map((t) => env.DB.prepare(`DELETE FROM ${t}`)));
});

describe("las fechas de las pruebas", () => {
  it("T0 está en la W45 y EN_W46 es el primer instante de la W46", () => {
    expect(semanaDe(new Date(T0))).toBe(W45);
    expect(semanaDe(new Date(T0 + EN_W46 - 1))).toBe(W45);
    expect(semanaDe(new Date(T0 + EN_W46))).toBe(W46);
    expect(finDeSemana(W45)).toBe(T0 + EN_W46);
  });
});

describe("POST /v1/semana/proponer", () => {
  it("propone por YouTube: la tarjeta la monta el servidor (2 subpeticiones) y sale en la semana", async () => {
    const yo = await shur();
    const r = await proponer(yo, ESTOPA_YT);
    expect(r.status).toBe(200);
    expect(r.cuerpo).toEqual({
      cancion: {
        id: expect.stringMatching(/^[0-9A-HJKMNP-TV-Z]{26}$/),
        titulo: "Como Camarón",
        artista: "Estopa",
        portada: "https://i.ytimg.com/vi/aaaaaaaaaaa/hqdefault.jpg",
        youtube: ESTOPA_YT,
      },
      yaEstaba: false,
      propuestasRestantes: 2,
    });
    expect(r.cabeceras.get("Cache-Control")).toBe("no-store");
    expect(pedidas).toHaveLength(2);
    const s = await semana();
    expect(s.cuerpo.actual.canciones).toEqual([r.cuerpo.cancion]);
  });

  it("mismaCancionDosFuentes: YouTube y luego Spotify son UNA canción con los dos enlaces y los votos se suman", async () => {
    const a = await shur();
    const b = await shur();
    const porYoutube = await proponer(a, ESTOPA_YT);
    const porSpotify = await proponer(b, ESTOPA_SP);
    expect(porSpotify.status).toBe(200);
    // La de Spotify casa por clave con la de YouTube: ya estaba, y no gasta propuesta de b.
    expect(porSpotify.cuerpo.yaEstaba).toBe(true);
    expect(porSpotify.cuerpo.propuestasRestantes).toBe(3);
    const id = porYoutube.cuerpo.cancion.id;
    expect(porSpotify.cuerpo.cancion).toEqual({
      id,
      titulo: "Como Camarón",
      artista: "Estopa",
      portada: "https://i.ytimg.com/vi/aaaaaaaaaaa/hqdefault.jpg",
      youtube: ESTOPA_YT,
      spotify: "https://open.spotify.com/track/0aaaaaaaaaaaaaaaaaaaaa",
    });
    expect(await filas("SELECT id FROM canciones")).toHaveLength(1);
    expect((await filas("SELECT ref, cancion FROM enlaces_cancion ORDER BY ref"))).toEqual([
      { ref: "spotify:0aaaaaaaaaaaaaaaaaaaaa", cancion: id },
      { ref: "youtube:aaaaaaaaaaa", cancion: id },
    ]);
    // a vota la que propuso por YouTube y b la que le llegó por Spotify: es la misma.
    expect((await votar(a, id)).status).toBe(200);
    expect((await votar(b, porSpotify.cuerpo.cancion.id)).status).toBe(200);
    const s = await semana();
    expect(s.cuerpo.actual.canciones).toHaveLength(1);
    expect(s.cuerpo.actual.canciones[0]).toMatchObject({ id, spotify: expect.any(String), youtube: ESTOPA_YT });
    await cerrarSemana(deps({ ahora: () => new Date(T0 + EN_W46) }), W45);
    const cerrada = await semana(en(EN_W46));
    expect(cerrada.cuerpo.anterior.top).toEqual([{ ...porSpotify.cuerpo.cancion, votos: 2 }]);
  });

  it("un enlace ya conocido no vuelve a pedir nada fuera", async () => {
    const [a, b] = [await shur(), await shur()];
    await proponer(a, ESTOPA_YT);
    pedidas = [];
    const r = await proponer(b, "https://youtu.be/aaaaaaaaaaa");
    expect(r.cuerpo.yaEstaba).toBe(true);
    expect(pedidas).toHaveLength(0);
  });

  it("yaEstaba: proponer lo ya propuesto da yaEstaba y no gasta propuesta (ni del que la propuso ni de otro)", async () => {
    const [a, b] = [await shur(), await shur()];
    const id = await propuesta(a, tema(1));
    const otraVez = await proponer(a, tema(1));
    expect(otraVez.status).toBe(200);
    expect(otraVez.cuerpo).toMatchObject({ cancion: { id }, yaEstaba: true, propuestasRestantes: 2 });
    const deOtro = await proponer(b, tema(1));
    expect(deOtro.cuerpo).toMatchObject({ cancion: { id }, yaEstaba: true, propuestasRestantes: 3 });
    expect(await filas("SELECT cancion, cuenta FROM propuestas")).toEqual([{ cancion: id, cuenta: a.id }]);
    // Con las 3 gastadas, lo que ya está sigue contestando yaEstaba (no tope).
    await propuesta(a, tema(2));
    await propuesta(a, tema(3));
    const lleno = await proponer(a, tema(1));
    expect(lleno.status).toBe(200);
    expect(lleno.cuerpo).toMatchObject({ yaEstaba: true, propuestasRestantes: 0 });
  });

  it("topes: la 4.ª propuesta de la semana -> 429 tope; la semana siguiente vuelve a tener 3", async () => {
    const yo = await shur();
    for (let n = 1; n <= 3; n++) {
      const r = await proponer(yo, tema(n));
      expect(r.cuerpo.propuestasRestantes).toBe(3 - n);
    }
    const cuarta = await proponer(yo, tema(4));
    expect(cuarta.status).toBe(429);
    expect(cuarta.cuerpo).toEqual({ error: "tope" });
    expect(await filas("SELECT 1 FROM propuestas WHERE cuenta = ?", yo.id)).toHaveLength(3);
    const lunes = await proponer(yo, tema(4), en(EN_W46));
    expect(lunes.status).toBe(200);
    expect(lunes.cuerpo.propuestasRestantes).toBe(2);
  });

  it("antiguedad: cuenta de 89 días -> 403 al proponer; de 90 -> ok; registro ilegible -> 403", async () => {
    for (const dias of [89, null]) {
      const r = await proponer(await shur(dias), tema(1));
      expect(r.status, String(dias)).toBe(403);
      expect(r.cuerpo).toEqual({ error: "antiguedad" });
    }
    // Antes de mirar el enlace: una cuenta que no puede proponer no gasta peticiones fuera.
    expect(pedidas).toHaveLength(0);
    expect((await proponer(await shur(90), tema(1))).status).toBe(200);
  });

  it("enlace que no es una canción -> 422 no_es_cancion, sin pedir nada fuera", async () => {
    const yo = await shur();
    for (const enlace of ["https://www.youtube.com/@Estopa", "https://open.spotify.com/album/0aaaaaaaaaaaaaaaaaaaaa", "hola"]) {
      const r = await proponer(yo, enlace);
      expect(r.status, enlace).toBe(422);
      expect(r.cuerpo).toEqual({ error: "no_es_cancion" });
    }
    expect(pedidas).toHaveLength(0);
  });

  it("el vídeo no existe o la red falla -> 422 no_encontrada, sin guardar nada ni gastar propuesta", async () => {
    const yo = await shur();
    const r = await proponer(yo, "https://www.youtube.com/watch?v=noexiste123");
    expect(r.status).toBe(422);
    expect(r.cuerpo).toEqual({ error: "no_encontrada" });
    sinRed = true;
    expect((await proponer(yo, tema(1))).cuerpo).toEqual({ error: "no_encontrada" });
    expect(await filas("SELECT 1 FROM canciones")).toHaveLength(0);
    expect(await filas("SELECT 1 FROM propuestas")).toHaveLength(0);
  });

  it("cuerpo sin enlace de texto -> 400 enlace", async () => {
    const yo = await shur();
    for (const cuerpo of [{}, { enlace: 5 }, { enlace: null }, "no es json", { enlace: "x".repeat(2000) }]) {
      const r = await llamar(en(), peticion("POST", "/v1/semana/proponer", cuerpo, conToken(yo)));
      expect(r.status, JSON.stringify(cuerpo).slice(0, 30)).toBe(400);
      expect(r.cuerpo).toEqual({ error: "enlace" });
    }
  });

  it("sin token -> 401; cuenta baneada -> 403 baneada", async () => {
    const sin = await llamar(en(), peticion("POST", "/v1/semana/proponer", { enlace: tema(1) }));
    expect(sin.status).toBe(401);
    const b = await proponer(await shur(400, { baneada: true }), tema(1));
    expect(b.status).toBe(403);
    expect(b.cuerpo).toEqual({ error: "baneada" });
  });

  it("dos propuestas a la vez de la misma canción nueva dejan UNA canción y UNA propuesta", async () => {
    const [a, b] = [await shur(), await shur()];
    const [ra, rb] = await Promise.all([proponer(a, ESTOPA_YT), proponer(b, ESTOPA_SP)]);
    expect([ra.status, rb.status]).toEqual([200, 200]);
    expect(ra.cuerpo.cancion.id).toBe(rb.cuerpo.cancion.id);
    expect([ra.cuerpo.yaEstaba, rb.cuerpo.yaEstaba].sort()).toEqual([false, true]);
    expect(await filas("SELECT 1 FROM canciones")).toHaveLength(1);
    expect(await filas("SELECT 1 FROM propuestas")).toHaveLength(1);
    expect(await filas("SELECT 1 FROM enlaces_cancion")).toHaveLength(2);
  });

  it("los enlaces nuevos que obligan a pedir fuera tienen un límite por cuenta y hora -> 429 limite", async () => {
    const yo = await shur();
    // Tope bajado a 5 (el real es de 200 por hora) para no hacer 200 propuestas.
    const bajo = (ms = 0) => crearApp(deps({ ahora: () => new Date(T0 + ms) }), { tarjetasHora: 5 });
    // Vídeos que no existen: no gastan propuesta, pero cada uno cuesta peticiones fuera.
    for (let n = 0; n < 5; n++) {
      const r = await proponer(yo, "https://www.youtube.com/watch?v=noexiste" + String(n).padStart(3, "0"), bajo());
      expect(r.status).toBe(422);
    }
    const pedidasAntes = pedidas.length;
    const r = await proponer(yo, tema(1), bajo());
    expect(r.status).toBe(429);
    expect(r.cuerpo).toEqual({ error: "limite" });
    expect(pedidas).toHaveLength(pedidasAntes);
    // Lo que ya está en la base no pide nada fuera y no lo frena el límite.
    const otro = await shur();
    await propuesta(otro, tema(2));
    expect((await proponer(yo, tema(2), bajo())).cuerpo.yaEstaba).toBe(true);
    // A la hora siguiente vuelve a poder.
    expect((await proponer(yo, tema(1), bajo(60 * MIN))).status).toBe(200);
  });
});

describe("POST y DELETE /v1/semana/voto", () => {
  it("votar, votar otra vez la misma (sin duplicar) y quitar el voto (idempotente)", async () => {
    const yo = await shur();
    const id = await propuesta(yo, tema(1));
    const r = await votar(yo, id);
    expect(r.status).toBe(200);
    expect(r.cuerpo).toEqual({ votada: true, votosRestantes: 9 });
    expect(r.cabeceras.get("Cache-Control")).toBe("no-store");
    const otraVez = await votar(yo, id);
    expect(otraVez.status).toBe(200);
    expect(otraVez.cuerpo).toEqual({ votada: true, votosRestantes: 9 });
    expect(await filas("SELECT 1 FROM votos")).toHaveLength(1);
    const quitar = await quitarVoto(yo, id);
    expect(quitar.status).toBe(200);
    expect(quitar.cuerpo).toEqual({ votada: false, votosRestantes: 10 });
    expect((await quitarVoto(yo, id)).cuerpo).toEqual({ votada: false, votosRestantes: 10 });
    expect(await filas("SELECT 1 FROM votos")).toHaveLength(0);
  });

  it("topes: el 11.º voto -> 429 tope; quitar uno devuelve uno", async () => {
    const proponentes = [await shur(), await shur(), await shur(), await shur()];
    const ids: string[] = [];
    for (let n = 1; n <= 12; n++) ids.push(await propuesta(proponentes[(n - 1) % 4], tema(n)));
    const yo = await shur();
    for (let i = 0; i < 10; i++) {
      const r = await votar(yo, ids[i]);
      expect(r.status).toBe(200);
      expect(r.cuerpo.votosRestantes).toBe(9 - i);
    }
    const once = await votar(yo, ids[10]);
    expect(once.status).toBe(429);
    expect(once.cuerpo).toEqual({ error: "tope" });
    // Volver a votar una que ya tiene no es un voto nuevo: 200, aunque esté en el tope.
    expect((await votar(yo, ids[3])).status).toBe(200);
    expect(await filas("SELECT 1 FROM votos WHERE cuenta = ?", yo.id)).toHaveLength(10);
    expect((await quitarVoto(yo, ids[0])).cuerpo.votosRestantes).toBe(1);
    expect((await votar(yo, ids[10])).cuerpo).toEqual({ votada: true, votosRestantes: 0 });
    expect((await votar(yo, ids[11])).status).toBe(429);
    // /v1/yo cuenta lo mismo que el tope.
    const y = await llamar(en(), peticion("GET", "/v1/yo", undefined, conToken(yo)));
    expect(y.cuerpo).toMatchObject({ votosRestantes: 0, puedeVotar: true });
  });

  it("antiguedad: 89 días -> 403 al votar y al quitar; 90 -> ok; y /v1/yo dice lo mismo", async () => {
    const id = await propuesta(await shur(), tema(1));
    for (const dias of [89, null]) {
      const s = await shur(dias);
      for (const r of [await votar(s, id), await quitarVoto(s, id)]) {
        expect(r.status, String(dias)).toBe(403);
        expect(r.cuerpo).toEqual({ error: "antiguedad" });
      }
      const y = await llamar(en(), peticion("GET", "/v1/yo", undefined, conToken(s)));
      expect(y.cuerpo.puedeVotar).toBe(false);
    }
    const noventa = await shur(90);
    expect((await votar(noventa, id)).status).toBe(200);
    expect((await llamar(en(), peticion("GET", "/v1/yo", undefined, conToken(noventa)))).cuerpo.puedeVotar).toBe(true);
    expect(await filas("SELECT cuenta FROM votos")).toEqual([{ cuenta: noventa.id }]);
  });

  it("solo se vota lo propuesto en la semana EN CURSO -> 409 no_esta", async () => {
    const yo = await shur();
    const id = await propuesta(yo, tema(1));
    // Una canción que existe pero no está propuesta esta semana, y un id que no es de nada.
    const otra = (await filas("SELECT id FROM canciones"))[0].id as string;
    await env.DB.prepare("DELETE FROM propuestas").run();
    for (const c of [otra, nuevoId(new Date(T0))]) {
      for (const r of [await votar(yo, c), await quitarVoto(yo, c)]) {
        expect(r.status).toBe(409);
        expect(r.cuerpo).toEqual({ error: "no_esta" });
      }
    }
    // La de la semana pasada tampoco: el lunes ya no se vota.
    const id2 = await propuesta(yo, tema(2));
    expect(id2).not.toBe(id);
    const lunes = await votar(yo, id2, en(EN_W46));
    expect(lunes.status).toBe(409);
    expect(await filas("SELECT 1 FROM votos")).toHaveLength(0);
  });

  it("cuerpo sin un id de canción -> 400 cancion", async () => {
    const yo = await shur();
    for (const c of [undefined, 5, "", "no-es-un-ulid", "0".repeat(26).toLowerCase() + "x"]) {
      const r = await votar(yo, c);
      expect(r.status, String(c)).toBe(400);
      expect(r.cuerpo).toEqual({ error: "cancion" });
    }
  });

  it("solo toca los votos de la cuenta del token", async () => {
    const [a, b] = [await shur(), await shur()];
    const id = await propuesta(a, tema(1));
    await votar(a, id);
    await votar(b, id);
    await quitarVoto(b, id);
    expect(await filas("SELECT cuenta FROM votos")).toEqual([{ cuenta: a.id }]);
  });

  it("sin token -> 401 token", async () => {
    const id = await propuesta(await shur(), tema(1));
    for (const m of ["POST", "DELETE"]) {
      const r = await llamar(en(), peticion(m, "/v1/semana/voto", { cancion: id }));
      expect(r.status).toBe(401);
      expect(r.cuerpo).toEqual({ error: "token" });
    }
  });
});

describe("GET /v1/semana", () => {
  it("actualSinRecuento: ordenada por votos (y luego por quién se propuso antes) sin la clave votos", async () => {
    const [a, b, c] = [await shur(), await shur(), await shur()];
    const uno = await propuesta(a, tema(1), en(0));
    const dos = await propuesta(a, tema(2), en(1 * MIN));
    const tres = await propuesta(a, tema(3), en(2 * MIN));
    const cuatro = await propuesta(b, tema(4), en(3 * MIN));
    await votar(a, tres);
    await votar(b, tres);
    await votar(c, dos);
    const r = await semana();
    expect(r.status).toBe(200);
    expect(Object.keys(r.cuerpo).sort()).toEqual(["actual", "anterior"]);
    expect(r.cuerpo.actual.id).toBe(W45);
    expect(r.cuerpo.actual.cierra).toBe(finDeSemana(W45));
    expect(r.cuerpo.actual.canciones.map((x: Json) => x.id)).toEqual([tres, dos, uno, cuatro]);
    expect(JSON.stringify(r.cuerpo.actual)).not.toContain('"votos"');
    for (const x of r.cuerpo.actual.canciones) {
      expect(Object.keys(x).sort()).toEqual(["artista", "id", "portada", "titulo", "youtube"]);
    }
    expect(r.cuerpo.anterior).toBeNull();
  });

  it("sin token: caché pública de 60 s y sin votada; con token: votada de esa cuenta y sin caché compartida", async () => {
    const [a, b] = [await shur(), await shur()];
    const uno = await propuesta(a, tema(1));
    const dos = await propuesta(a, tema(2));
    await votar(a, dos);
    await votar(b, uno);
    const publica = await semana();
    expect(publica.cabeceras.get("Cache-Control")).toBe("public, max-age=60");
    expect(publica.cabeceras.get("Vary")).toBe("Authorization");
    expect(JSON.stringify(publica.cuerpo)).not.toContain("votada");
    const deA = await semana(en(), a);
    expect(deA.cabeceras.get("Cache-Control")).toBe("private, no-store");
    expect(deA.cuerpo.actual.canciones.map((x: Json) => [x.id, x.votada]).sort()).toEqual(
      [[uno, false], [dos, true]].sort(),
    );
    expect(JSON.stringify(deA.cuerpo.actual)).not.toContain('"votos"');
  });

  it("con un token que no vale -> 401 token (no la vista pública)", async () => {
    const r = await llamar(en(), peticion("GET", "/v1/semana", undefined, { Authorization: "Bearer " + "x".repeat(43) }));
    expect(r.status).toBe(401);
    expect(r.cuerpo).toEqual({ error: "token" });
  });

  it("semana vacía: actual sin canciones y anterior null", async () => {
    const r = await semana();
    expect(r.cuerpo).toEqual({ actual: { id: W45, cierra: finDeSemana(W45), canciones: [] }, anterior: null });
  });
});

describe("cierre de la semana", () => {
  it("cierreDesempate: a igualdad de votos gana la que llegó antes a ese número; top de 10 como máximo", async () => {
    const proponentes = [await shur(), await shur(), await shur(), await shur()];
    const ids: string[] = [];
    for (let n = 1; n <= 12; n++) ids.push(await propuesta(proponentes[(n - 1) % 4], tema(n)));
    const votantes = [await shur(), await shur(), await shur()];
    // tema 1: tres votos, el tercero a los 30 min. tema 2: tres votos, el tercero a los 20 min.
    // tema 3: un voto. Del 4 al 12: un voto cada uno, todos antes. tema 2 llegó antes a 3: gana.
    let t = 0;
    for (const v of votantes) await votar(v, ids[0], en((t += 1) * MIN));
    await votar(votantes[0], ids[1], en(5 * MIN));
    await votar(votantes[1], ids[1], en(6 * MIN));
    await votar(votantes[2], ids[1], en(20 * MIN));
    await quitarVoto(votantes[2], ids[0], en(25 * MIN));
    await votar(votantes[2], ids[0], en(30 * MIN));
    for (let i = 2; i < 12; i++) await votar(proponentes[i % 4], ids[i], en((40 + i) * MIN));
    await cerrarSemana(deps({ ahora: () => new Date(T0 + EN_W46) }), W45);
    const fila = (await filas("SELECT cerrada, top, cancion_semana FROM semanas WHERE id = ?", W45))[0];
    expect(fila.cerrada).toBe(1);
    const top = JSON.parse(fila.top as string) as Json[];
    expect(top).toHaveLength(10);
    expect(top.map((x) => x.id).slice(0, 2)).toEqual([ids[1], ids[0]]);
    expect(top.map((x) => x.votos)).toEqual([3, 3, 1, 1, 1, 1, 1, 1, 1, 1]);
    // A un voto, el desempate es el mismo: antes el que llegó antes (los votos de 3 a 10 en orden).
    expect(top.slice(2).map((x) => x.id)).toEqual(ids.slice(2, 10));
    expect(fila.cancion_semana).toBe(ids[1]);
    expect(top[0]).toEqual({
      id: ids[1], titulo: "Tema 2", artista: "Grupo 2", portada: "https://i.ytimg.com/vi/tema0000002/hqdefault.jpg",
      youtube: tema(2), votos: 3,
    });
    // Y es lo que enseña el GET de la semana siguiente.
    const r = await semana(en(EN_W46));
    expect(r.cuerpo.anterior).toEqual({ id: W45, cancionSemana: top[0], top });
    expect(r.cuerpo.actual).toEqual({ id: W46, cierra: finDeSemana(W46), canciones: [] });
  });

  it("cierrePerezosoUnaVez: el primer GET de la W46 cierra la W45; otro no cambia nada", async () => {
    const yo = await shur();
    const id = await propuesta(yo, tema(1));
    await votar(yo, id);
    expect((await filas("SELECT cerrada FROM semanas WHERE id = ?", W45))[0].cerrada).toBe(0);
    const r = await semana(en(EN_W46));
    expect(r.cuerpo.anterior).toMatchObject({ id: W45, cancionSemana: { id, votos: 1 } });
    const antes = await filas("SELECT * FROM semanas ORDER BY id");
    expect(antes).toEqual([{ id: W45, cerrada: 1, top: expect.any(String), cancion_semana: id }]);
    // Un voto que se colara en la W45 ya cerrada no cambia el top guardado: ni otro GET ni otro cierre.
    const otro = await shur();
    await env.DB.prepare("INSERT INTO votos VALUES (?, ?, ?, ?)").bind(W45, id, otro.id, T0).run();
    await semana(en(EN_W46 + MIN));
    await cerrarSemana(deps({ ahora: () => new Date(T0 + EN_W46 + 2 * MIN) }), W45);
    expect(await filas("SELECT * FROM semanas ORDER BY id")).toEqual(antes);
  });

  it("proponer también cierra la semana pasada", async () => {
    const yo = await shur();
    const id = await propuesta(yo, tema(1));
    await votar(yo, id);
    await propuesta(yo, tema(2), en(EN_W46));
    expect(await filas("SELECT id, cerrada, cancion_semana FROM semanas ORDER BY id")).toEqual([
      { id: W45, cerrada: 1, cancion_semana: id },
      { id: W46, cerrada: 0, cancion_semana: null },
    ]);
  });

  it("votar también cierra, y cierra todas las semanas pendientes de una vez (una sin votos: top vacío)", async () => {
    const yo = await shur();
    await propuesta(yo, tema(1));
    // La W46 tiene una propuesta y nadie la cerró (como si el cron y las peticiones hubieran fallado).
    const id46 = (await filas("SELECT id FROM canciones"))[0].id as string;
    await env.DB.batch([
      env.DB.prepare("INSERT INTO semanas (id) VALUES (?)").bind(W46),
      env.DB.prepare("INSERT INTO propuestas VALUES (?, ?, ?, ?)").bind(W46, id46, yo.id, T0 + EN_W46),
    ]);
    const W47 = "2026-W47";
    const enW47 = en(EN_W46 + 7 * DIA);
    expect(semanaDe(new Date(T0 + EN_W46 + 7 * DIA))).toBe(W47);
    // La canción no está propuesta en la W47: 409, pero el cierre ya se hizo antes.
    expect((await votar(yo, id46, enW47)).status).toBe(409);
    expect(await filas("SELECT id, cerrada, top, cancion_semana FROM semanas ORDER BY id")).toEqual([
      { id: W45, cerrada: 1, top: "[]", cancion_semana: null },
      { id: W46, cerrada: 1, top: "[]", cancion_semana: null },
    ]);
    const g = await semana(enW47);
    expect(g.cuerpo.anterior).toEqual({ id: W46, cancionSemana: null, top: [] });
  });

  it("una semana sin propuestas no se registra: anterior sigue siendo la última con propuestas", async () => {
    const yo = await shur();
    const id = await propuesta(yo, tema(1));
    await votar(yo, id);
    // W46 pasa sin nadie: en la W47 la anterior sigue siendo la W45.
    const enW47 = en(EN_W46 + 7 * DIA);
    const r = await semana(enW47);
    expect(r.cuerpo.anterior.id).toBe(W45);
    await cerrarSemana(deps({ ahora: () => new Date(T0 + EN_W46 + 7 * DIA) }), W46);
    expect(await filas("SELECT id FROM semanas ORDER BY id")).toEqual([{ id: W45 }]);
  });

  it("cerrarSemana no cierra la semana en curso ni una futura", async () => {
    const yo = await shur();
    await propuesta(yo, tema(1));
    await cerrarSemana(deps(), W45);
    await cerrarSemana(deps(), W46);
    expect(await filas("SELECT id, cerrada FROM semanas")).toEqual([{ id: W45, cerrada: 0 }]);
  });

  it("los votos a una canción cuya propuesta desapareció no cuentan en el cierre", async () => {
    const [a, b] = [await shur(), await shur()];
    const uno = await propuesta(a, tema(1));
    const dos = await propuesta(b, tema(2));
    await votar(a, uno);
    await votar(a, dos);
    await votar(b, dos);
    // La cuenta b se borra (tarea B7) y se lleva su propuesta; los votos de a se quedan.
    await env.DB.prepare("DELETE FROM propuestas WHERE cuenta = ?").bind(b.id).run();
    await asegurarCierres(deps({ ahora: () => new Date(T0 + EN_W46) }));
    const top = JSON.parse((await filas("SELECT top FROM semanas WHERE id = ?", W45))[0].top as string);
    expect(top.map((x: Json) => x.id)).toEqual([uno]);
  });

  it("asegurarCierres con nada que cerrar hace UNA consulta", async () => {
    const yo = await shur();
    await propuesta(yo, tema(1));
    let consultas = 0;
    const db = new Proxy(env.DB, {
      get(t, k) {
        if (k === "prepare") {
          return (sql: string) => {
            consultas++;
            return t.prepare(sql);
          };
        }
        const v = Reflect.get(t, k);
        return typeof v === "function" ? v.bind(t) : v;
      },
    });
    await asegurarCierres(deps({ db }));
    expect(consultas).toBe(1);
    // Y la consulta usa el índice de semanas abiertas, no recorre todas las semanas pasadas.
    const plan = await filas("EXPLAIN QUERY PLAN SELECT id FROM semanas WHERE cerrada = 0 AND id < ?", W46);
    expect(JSON.stringify(plan)).toContain("idx_semanas_abiertas");
  });
});
