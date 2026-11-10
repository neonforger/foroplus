import { env } from "cloudflare:workers";
import { beforeEach, describe, expect, it } from "vitest";
import deezerEstopa from "./fixtures/deezer_estopa.json";
import deezerVacio from "./fixtures/deezer_vacio.json";
import spEstopaMeta from "./fixtures/sp_estopa_meta.html?raw";
import spEstopaOembed from "./fixtures/sp_estopa_oembed.json";
import ytEstopa from "./fixtures/yt_estopa_oembed.json";
import { crearApp, type Deps } from "../src/app";
import { hashToken, nuevoId, nuevoToken, uidHmac } from "../src/cripto";

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

async function filas(sql: string, ...args: unknown[]): Promise<Json[]> {
  return (await env.DB.prepare(sql).bind(...args).all<Json>()).results;
}

const publicar = (s: Shur, nombre: unknown, enlaces: unknown, a: App = en()) =>
  llamar(a, peticion("POST", "/v1/playlists", { nombre, enlaces }, conToken(s)));
const ampliar = (s: Shur, id: string, enlaces: unknown, a: App = en()) =>
  llamar(a, peticion("POST", `/v1/playlists/${id}/canciones`, { enlaces }, conToken(s)));
const borrar = (s: Shur, id: string, a: App = en()) =>
  llamar(a, peticion("DELETE", `/v1/playlists/${id}`, undefined, conToken(s)));
const guardar = (s: Shur, id: string, a: App = en()) =>
  llamar(a, peticion("POST", `/v1/playlists/${id}/guardar`, undefined, conToken(s)));
const listar = (consulta = "", a: App = en()) => llamar(a, peticion("GET", "/v1/playlists" + consulta));
const detalle = (id: string, a: App = en()) => llamar(a, peticion("GET", `/v1/playlists/${id}`));
const denunciar = (s: Shur, cuerpo: unknown, a: App = en()) =>
  llamar(a, peticion("POST", "/v1/denuncias", cuerpo, conToken(s)));
const borrarCuenta = (s: Shur, a: App = en()) => llamar(a, peticion("DELETE", "/v1/cuenta", undefined, conToken(s)));
const HORA = 3_600_000;
const temas = (desde: number, n: number) => Array.from({ length: n }, (_, i) => tema(desde + i));

/** Publica una playlist con `n` temas y devuelve su id (falla el test si no da 200). */
async function playlist(s: Shur, nombre: string, n = 2, desde = 1, a: App = en()): Promise<string> {
  const r = await publicar(s, nombre, temas(desde, n), a);
  expect(r.status, JSON.stringify(r.cuerpo)).toBe(200);
  return r.cuerpo.id;
}

async function propuestaDe(s: Shur, enlace: string): Promise<string> {
  const r = await llamar(en(), peticion("POST", "/v1/semana/proponer", { enlace }, conToken(s)));
  expect(r.status, JSON.stringify(r.cuerpo)).toBe(200);
  return r.cuerpo.cancion.id;
}

beforeEach(async () => {
  pedidas = [];
  sinRed = false;
  numCuenta = 0;
  await env.DB.batch(TABLAS.map((t) => env.DB.prepare(`DELETE FROM ${t}`)));
});

describe("POST /v1/playlists", () => {
  it("publica con el nombre de la cuenta como autor y las canciones las pone el servidor", async () => {
    const a = await shur();
    const r = await publicar(a, "  Para   el  coche ", [ESTOPA_YT, tema(1), "https://example.com/x", ESTOPA_SP]);
    expect(r.status).toBe(200);
    // ESTOPA_SP es la misma canción que ESTOPA_YT: una sola entrada. example.com no es una canción.
    expect(r.cuerpo).toEqual({
      id: expect.stringMatching(/^[0-9A-HJKMNP-TV-Z]{26}$/),
      canciones: 2,
      fallidas: ["https://example.com/x"],
    });
    const d = await detalle(r.cuerpo.id);
    expect(d.cuerpo.nombre).toBe("Para el coche");
    expect(d.cuerpo.autor).toBe("YoMismo1");
    expect(d.cuerpo.canciones.map((c: Json) => c.titulo)).toEqual(["Como Camarón", "Tema 1"]);
    expect(d.cuerpo.canciones[0].spotify).toBeDefined();
  });

  it("exige token", async () => {
    const r = await llamar(en(), peticion("POST", "/v1/playlists", { nombre: "x", enlaces: [] }));
    expect(r.status).toBe(401);
  });

  it("publicarEnTandas: 25 enlaces = 10 + 10 + 5 dan 25 canciones; una tanda de 11 da 422 demasiados", async () => {
    const a = await shur();
    const t = temas(1, 25);
    const r1 = await publicar(a, "Grande", t.slice(0, 10));
    expect(r1.status).toBe(200);
    const id = r1.cuerpo.id;
    expect((await ampliar(a, id, t.slice(10, 20))).cuerpo).toEqual({ canciones: 20, fallidas: [] });
    // Las tres tandas caben en la misma hora: el presupuesto de enlaces nuevos es de 200/h.
    const r3 = await ampliar(a, id, t.slice(20, 25));
    expect(r3.cuerpo).toEqual({ canciones: 25, fallidas: [] });
    expect((await detalle(id)).cuerpo.canciones).toHaveLength(25);
    pedidas = [];
    const once = await publicar(a, "Once", temas(100, 11));
    expect(once.status).toBe(422);
    expect(once.cuerpo.error).toBe("demasiados");
    expect(pedidas).toHaveLength(0);
    expect((await ampliar(a, id, temas(100, 11))).cuerpo.error).toBe("demasiados");
  });

  it("nombreProhibido: palabra entera, sin mayúsculas ni tildes; 61 caracteres y vacío también dan 422 nombre", async () => {
    const a = await shur();
    for (const n of ["Cabrón", "los GILIPOLLAS", "hijo de puta", "x".repeat(61), "   ", "", 5, null]) {
      const r = await publicar(a, n, []);
      expect(r.status, String(n)).toBe(422);
      expect(r.cuerpo.error).toBe("nombre");
    }
    expect(await filas("SELECT id FROM playlists_pub")).toHaveLength(0);
    // Palabra ENTERA: "computadora" contiene "puta" y no es un insulto; 60 caracteres exactos valen.
    expect((await publicar(a, "Computadora", [])).status).toBe(200);
    expect((await publicar(a, "x".repeat(60), [])).status).toBe(200);
  });

  it("los enlaces tienen que ser una lista de textos", async () => {
    const a = await shur();
    expect((await publicar(a, "A", "nope")).status).toBe(400);
    expect((await publicar(a, "A", [1])).status).toBe(400);
    expect((await llamar(en(), peticion("POST", "/v1/playlists", "{", conToken(a)))).status).toBe(400);
  });

  it("máximo 50 playlists por cuenta: la 51 da 429 tope", async () => {
    const a = await shur();
    for (let i = 0; i < 50; i++) expect((await publicar(a, "P" + i, [])).status).toBe(200);
    const r = await publicar(a, "P50", []);
    expect(r.status).toBe(429);
    expect(r.cuerpo.error).toBe("tope");
    // Otra cuenta no se ve afectada.
    expect((await publicar(await shur(), "Mía", [])).status).toBe(200);
  });

  it("comparte el presupuesto de enlaces nuevos por hora con proponer (aquí bajado a 20); lo ya conocido no gasta", async () => {
    const a = await shur();
    const app = (ms = 0) => crearApp(deps({ ahora: () => new Date(T0 + ms) }), { tarjetasHora: 20 });
    expect((await publicar(a, "A", temas(1, 10), app())).status).toBe(200);
    expect((await publicar(a, "B", temas(11, 10), app())).status).toBe(200);
    // Conocidas: sin red y sin gastar presupuesto.
    pedidas = [];
    expect((await publicar(a, "C", temas(1, 10), app())).status).toBe(200);
    expect(pedidas).toHaveLength(0);
    // Una nueva más: la 21 de la hora.
    pedidas = [];
    const r = await publicar(a, "D", [tema(99)], app());
    expect(r.status).toBe(429);
    expect(r.cuerpo.error).toBe("limite");
    expect(pedidas).toHaveLength(0);
    // Y proponer tira del mismo saco.
    const p = await llamar(app(), peticion("POST", "/v1/semana/proponer", { enlace: tema(98) }, conToken(a)));
    expect(p.status).toBe(429);
    // A la hora siguiente vuelve el cupo.
    expect((await publicar(a, "E", [tema(99)], app(HORA))).status).toBe(200);
  });

  it("el tope por defecto es de 200 enlaces nuevos por hora", async () => {
    const a = await shur();
    expect((await import("../src/limites")).TARJETAS_POR_HORA).toBe(200);
    for (let i = 0; i < 3; i++) expect((await publicar(a, "P" + i, temas(i * 10 + 1, 10))).status).toBe(200);
  });

  it("un enlace cuya tarjeta no se puede montar va a fallidas y la petición sigue", async () => {
    const a = await shur();
    const malo = "https://www.youtube.com/watch?v=noexiste000";
    const r = await publicar(a, "A", [tema(1), malo]);
    expect(r.status).toBe(200);
    expect(r.cuerpo.canciones).toBe(1);
    expect(r.cuerpo.fallidas).toEqual([malo]);
  });
});

describe("POST /v1/playlists/:id/canciones", () => {
  it("añade sin repetir, y más de 200 da 422 llena sin tocar nada", async () => {
    const a = await shur();
    const id = await playlist(a, "A", 2);
    expect((await ampliar(a, id, [tema(2), tema(3)])).cuerpo).toEqual({ canciones: 3, fallidas: [] });
    const ids = Array.from({ length: 200 }, (_, i) => "c" + i);
    await env.DB.prepare("UPDATE playlists_pub SET canciones = ? WHERE id = ?").bind(JSON.stringify(ids), id).run();
    const r = await ampliar(a, id, [tema(50)]);
    expect(r.status).toBe(422);
    expect(r.cuerpo.error).toBe("llena");
    const [f] = await filas("SELECT canciones FROM playlists_pub WHERE id = ?", id);
    expect(JSON.parse(f.canciones)).toHaveLength(200);
  });

  it("idor: la cuenta B no puede ampliar ni borrar la playlist de A (404) y la de A sigue intacta", async () => {
    const a = await shur();
    const b = await shur();
    const id = await playlist(a, "De A", 2);
    const antes = await filas("SELECT * FROM playlists_pub WHERE id = ?", id);
    const amp = await ampliar(b, id, [tema(7)]);
    expect(amp.status).toBe(404);
    const del = await borrar(b, id);
    expect(del.status).toBe(404);
    // Igual que una que no existe: no se confirma su existencia.
    expect((await borrar(b, "0".repeat(26))).cuerpo).toEqual(del.cuerpo);
    expect(await filas("SELECT * FROM playlists_pub WHERE id = ?", id)).toEqual(antes);
    expect((await detalle(id)).cuerpo.canciones).toHaveLength(2);
  });
});

describe("DELETE /v1/playlists/:id", () => {
  it("la dueña la borra con sus guardados", async () => {
    const a = await shur();
    const b = await shur();
    const id = await playlist(a, "A");
    await guardar(b, id);
    expect((await borrar(a, id)).status).toBe(200);
    expect((await detalle(id)).status).toBe(404);
    expect(await filas("SELECT * FROM guardados WHERE playlist = ?", id)).toHaveLength(0);
    expect((await borrar(a, id)).status).toBe(404);
  });
});

describe("GET /v1/playlists", () => {
  it("lista de 20 en 20 por recientes con cursor estable, y por guardadas", async () => {
    const a = await shur();
    const b = await shur();
    const ids: string[] = [];
    for (let i = 0; i < 25; i++) ids.push(await playlist(a, "P" + i, 0, 1, en(i * 1000)));
    const p1 = await listar("?orden=recientes", en(60_000));
    expect(p1.status).toBe(200);
    expect(p1.cuerpo.playlists).toHaveLength(20);
    expect(p1.cuerpo.playlists[0].id).toBe(ids[24]);
    expect(typeof p1.cuerpo.siguiente).toBe("string");
    // Una nueva a mitad de paginación no mueve la segunda página (keyset).
    await playlist(a, "Nueva", 0, 1, en(50_000));
    const p2 = await listar("?orden=recientes&cursor=" + encodeURIComponent(p1.cuerpo.siguiente), en(60_000));
    expect(p2.cuerpo.playlists.map((x: Json) => x.id)).toEqual(ids.slice(0, 5).reverse());
    expect(p2.cuerpo.siguiente).toBeNull();
    // Por guardadas: la que guardó B va primero.
    await guardar(b, ids[3]);
    const g = await listar("?orden=guardadas");
    expect(g.cuerpo.playlists[0]).toMatchObject({ id: ids[3], guardados: 1, autor: "YoMismo1" });
  });

  it("cada ficha lleva autor, canciones, guardados y la portada de la primera canción", async () => {
    const a = await shur();
    const id = await playlist(a, "Con canciones", 3);
    const r = await listar();
    expect(r.cuerpo.playlists).toEqual([
      {
        id, nombre: "Con canciones", autor: "YoMismo1", canciones: 3, guardados: 0,
        portada: "https://i.ytimg.com/vi/tema0000001/hqdefault.jpg",
      },
    ]);
    expect(JSON.stringify(r.cuerpo)).not.toMatch(/hmac|uid/i);
    const vacia = await publicar(a, "Vacía", []);
    const v = (await listar()).cuerpo.playlists.find((x: Json) => x.id === vacia.cuerpo.id);
    expect(v).toMatchObject({ canciones: 0, portada: null });
  });

  it("ocultaNoSeLista: una playlist oculta no sale en la lista ni en el detalle, ni se guarda", async () => {
    const a = await shur();
    const b = await shur();
    const visible = await playlist(a, "Visible");
    const oculta = await playlist(a, "Oculta");
    await env.DB.prepare("UPDATE playlists_pub SET oculta = 1 WHERE id = ?").bind(oculta).run();
    expect((await listar()).cuerpo.playlists.map((x: Json) => x.id)).toEqual([visible]);
    expect((await listar("?orden=guardadas")).cuerpo.playlists.map((x: Json) => x.id)).toEqual([visible]);
    expect((await detalle(oculta)).status).toBe(404);
    expect((await guardar(b, oculta)).status).toBe(404);
  });

  it("las playlists de una cuenta baneada no se ven", async () => {
    const a = await shur();
    const id = await playlist(a, "De baneada");
    await env.DB.prepare("UPDATE cuentas SET baneada = 1 WHERE id = ?").bind(a.id).run();
    expect((await listar()).cuerpo.playlists).toEqual([]);
    expect((await detalle(id)).status).toBe(404);
  });

  it("orden o cursor mal formados dan 400", async () => {
    expect((await listar("?orden=raro")).status).toBe(400);
    expect((await listar("?cursor=zzz")).status).toBe(400);
  });
});

describe("POST /v1/playlists/:id/guardar", () => {
  it("guardarCuentaUnaVez: guardar dos veces da guardados 1; dos cuentas, 2", async () => {
    const a = await shur();
    const b = await shur();
    const c = await shur();
    const id = await playlist(a, "A");
    expect((await guardar(b, id)).cuerpo).toEqual({ guardados: 1 });
    expect((await guardar(b, id)).cuerpo).toEqual({ guardados: 1 });
    expect((await guardar(c, id)).cuerpo).toEqual({ guardados: 2 });
    expect((await detalle(id)).cuerpo.guardados).toBe(2);
  });

  it("guardar la propia se permite pero no cuenta; una que no existe da 404", async () => {
    const a = await shur();
    const id = await playlist(a, "A");
    expect((await guardar(a, id)).cuerpo).toEqual({ guardados: 0 });
    expect(await filas("SELECT * FROM guardados")).toHaveLength(0);
    expect((await guardar(a, "0".repeat(26))).status).toBe(404);
  });
});

describe("POST /v1/denuncias", () => {
  it("denuncia una playlist y una canción; objetivo que no existe da 404; repetida no crea otra fila", async () => {
    const a = await shur();
    const b = await shur();
    const id = await playlist(a, "A", 1);
    const [{ id: cancion }] = await filas("SELECT id FROM canciones");
    expect((await denunciar(b, { tipo: "playlist", objetivo: id, motivo: "ofensiva" })).status).toBe(200);
    expect((await denunciar(b, { tipo: "cancion", objetivo: cancion, motivo: "no es música" })).status).toBe(200);
    expect((await denunciar(b, { tipo: "playlist", objetivo: id, motivo: "otra vez" })).status).toBe(200);
    const f = await filas("SELECT tipo, objetivo, cuenta, motivo, resuelta FROM denuncias ORDER BY tipo");
    expect(f).toEqual([
      { tipo: "cancion", objetivo: cancion, cuenta: b.id, motivo: "no es música", resuelta: 0 },
      { tipo: "playlist", objetivo: id, cuenta: b.id, motivo: "ofensiva", resuelta: 0 },
    ]);
    expect((await denunciar(b, { tipo: "playlist", objetivo: "0".repeat(26), motivo: "x" })).status).toBe(404);
  });

  it("valida tipo, objetivo y motivo (≤ 300)", async () => {
    const a = await shur();
    const id = await playlist(a, "A", 0);
    expect((await denunciar(a, { tipo: "usuario", objetivo: id, motivo: "x" })).status).toBe(400);
    expect((await denunciar(a, { tipo: "playlist", objetivo: 5, motivo: "x" })).status).toBe(400);
    expect((await denunciar(a, { tipo: "playlist", objetivo: id, motivo: "x".repeat(301) })).status).toBe(400);
    expect((await denunciar(a, { tipo: "playlist", objetivo: id })).status).toBe(400);
    expect((await denunciar(a, { tipo: "playlist", objetivo: id, motivo: "x".repeat(300) })).status).toBe(200);
  });

  it("5 al día por cuenta: la sexta da 429 limite, y al día siguiente vuelve a valer", async () => {
    const a = await shur();
    const b = await shur();
    const ids: string[] = [];
    for (let i = 0; i < 6; i++) ids.push(await playlist(b, "P" + i, 0));
    for (let i = 0; i < 5; i++) {
      expect((await denunciar(a, { tipo: "playlist", objetivo: ids[i], motivo: "x" })).status).toBe(200);
    }
    const r = await denunciar(a, { tipo: "playlist", objetivo: ids[5], motivo: "x" });
    expect(r.status).toBe(429);
    expect(r.cuerpo.error).toBe("limite");
    expect(await filas("SELECT id FROM denuncias WHERE cuenta = ?", a.id)).toHaveLength(5);
    // Repetir una ya hecha no gasta cupo ni falla.
    expect((await denunciar(a, { tipo: "playlist", objetivo: ids[0], motivo: "x" })).status).toBe(200);
    expect((await denunciar(a, { tipo: "playlist", objetivo: ids[5], motivo: "x" }, en(DIA))).status).toBe(200);
  });
});

describe("DELETE /v1/cuenta", () => {
  it("borrarCuentaLoBorraTodo: no queda ninguna fila con su id y su token da 401", async () => {
    const a = await shur();
    const b = await shur();
    const suya = await playlist(a, "Mía", 2);
    const deB = await playlist(b, "De B", 2, 10);
    await guardar(b, suya); // alguien guarda la suya
    await guardar(a, deB); // y ella guarda otra
    await denunciar(a, { tipo: "playlist", objetivo: deB, motivo: "x" });
    await denunciar(b, { tipo: "playlist", objetivo: suya, motivo: "x" });
    const [{ id: cancion }] = await filas("SELECT id FROM canciones ORDER BY id LIMIT 1");
    await env.DB.batch([
      env.DB.prepare("INSERT INTO semanas (id) VALUES (?)").bind(W45),
      env.DB.prepare("INSERT INTO propuestas (semana, cancion, cuenta, creada) VALUES (?, ?, ?, ?)").bind(W45, cancion, a.id, T0),
      env.DB.prepare("INSERT INTO votos (semana, cancion, cuenta, creado) VALUES (?, ?, ?, ?)").bind(W45, cancion, a.id, T0),
      env.DB.prepare("INSERT INTO retos (id, uid, uid_hmac, coche, caduca) VALUES ('r', '123456', 'hmac1', 'x', ?)").bind(T0 + MIN),
    ]);
    const r = await borrarCuenta(a);
    expect(r.status).toBe(200);
    for (const [tabla, col] of [
      ["cuentas", "id"], ["tokens", "cuenta"], ["votos", "cuenta"], ["propuestas", "cuenta"],
      ["playlists_pub", "cuenta"], ["guardados", "cuenta"], ["denuncias", "cuenta"],
    ]) {
      expect(await filas(`SELECT 1 FROM ${tabla} WHERE ${col} = ?`, a.id), tabla).toHaveLength(0);
    }
    expect(await filas("SELECT 1 FROM guardados WHERE playlist = ?", suya)).toHaveLength(0);
    expect(await filas("SELECT 1 FROM denuncias WHERE objetivo = ?", suya)).toHaveLength(0);
    expect(await filas("SELECT 1 FROM retos")).toHaveLength(0);
    expect(await filas("SELECT 1 FROM limites WHERE clave LIKE ?", "%" + a.id + "%")).toHaveLength(0);
    // El token ya no vale.
    const con = await llamar(en(), peticion("POST", "/v1/playlists", { nombre: "x", enlaces: [] }, conToken(a)));
    expect(con.status).toBe(401);
    // Lo de los demás sigue, y su guardado de la playlist de B ya no cuenta.
    expect((await detalle(deB)).cuerpo).toMatchObject({ guardados: 0 });
    expect(await filas("SELECT 1 FROM cuentas WHERE id = ?", b.id)).toHaveLength(1);
  });

  it("en la semana abierta, una propuesta que otros votaron se queda a nombre de 'borrada'; sin votos ajenos se va", async () => {
    const a = await shur();
    const b = await shur();
    const votada = await propuestaDe(a, tema(1));
    const sola = await propuestaDe(a, tema(2));
    const voto = "INSERT INTO votos (semana, cancion, cuenta, creado) VALUES (?, ?, ?, ?)";
    await env.DB.batch([
      env.DB.prepare(voto).bind(W45, votada, b.id, T0),
      env.DB.prepare(voto).bind(W45, votada, a.id, T0),
      env.DB.prepare(voto).bind(W45, sola, a.id, T0),
    ]);
    expect((await borrarCuenta(a)).status).toBe(200);
    expect(await filas("SELECT cancion, cuenta FROM propuestas")).toEqual([{ cancion: votada, cuenta: "borrada" }]);
    expect(await filas("SELECT cuenta FROM votos")).toEqual([{ cuenta: b.id }]);
  });

  it("una semana ya cerrada conserva su top guardado", async () => {
    const a = await shur();
    const c = await propuestaDe(a, tema(1));
    await env.DB.prepare("UPDATE semanas SET cerrada = 1, top = '[{\"id\":\"x\"}]' WHERE id = ?").bind(W45).run();
    expect((await borrarCuenta(a)).status).toBe(200);
    expect(await filas("SELECT top FROM semanas WHERE id = ?", W45)).toEqual([{ top: '[{"id":"x"}]' }]);
    expect(await filas("SELECT 1 FROM propuestas WHERE cancion = ?", c)).toHaveLength(0);
  });

  it("exige token", async () => {
    expect((await llamar(en(), peticion("DELETE", "/v1/cuenta"))).status).toBe(401);
  });

  it("una cuenta baneada puede borrarse, pero queda una lápida y el baneo sigue", async () => {
    const a = await shur();
    const b = await shur();
    const uid = "123456";
    await env.DB.prepare("UPDATE cuentas SET uid_hmac = ? WHERE id = ?").bind(await uidHmac("pimienta", uid), a.id).run();
    const mia = await playlist(a, "Mía", 2);
    await guardar(b, mia);
    const cancion = await propuestaDe(a, tema(9));
    await env.DB.prepare("INSERT INTO votos (semana, cancion, cuenta, creado) VALUES (?, ?, ?, ?)").bind(W45, cancion, a.id, T0).run();
    // Se banea por la ruta REAL de administración: un UPDATE directo no vio que el baneo borraba los tokens
    // y dejaba esta ruta inalcanzable.
    const ban = await llamar(en(), peticion("POST", "/v1/admin/banear", { cuenta: a.id, baneada: true }, { "X-Admin": "admin" }));
    expect(ban.status).toBe(200);
    // El resto de rutas de cuenta siguen cerradas a una baneada.
    expect((await publicar(a, "X", [])).cuerpo).toEqual({ error: "baneada" });
    const r = await borrarCuenta(a);
    expect(r.status).toBe(200);
    // Sin token, sin playlists, sin votos, sin propuestas.
    expect(await filas("SELECT 1 FROM tokens WHERE cuenta = ?", a.id)).toHaveLength(0);
    expect(await filas("SELECT 1 FROM playlists_pub WHERE cuenta = ?", a.id)).toHaveLength(0);
    expect(await filas("SELECT 1 FROM guardados WHERE playlist = ?", mia)).toHaveLength(0);
    expect(await filas("SELECT 1 FROM votos WHERE cuenta = ?", a.id)).toHaveLength(0);
    expect(await filas("SELECT 1 FROM propuestas WHERE cuenta = ?", a.id)).toHaveLength(0);
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
