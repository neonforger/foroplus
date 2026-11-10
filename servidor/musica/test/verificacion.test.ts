import { env } from "cloudflare:workers";
import { beforeEach, describe, expect, it } from "vitest";
import conCoche from "./fixtures/ficha_con_coche.html?raw";
import noExiste from "./fixtures/ficha_no_existe.html?raw";
import challengeHtml from "./fixtures/ficha_challenge.html?raw";
import { crearApp, type Deps } from "../src/app";
import { acceder } from "../src/auth";
import { cocheEn, cochesIguales, totalCoches } from "../src/coches";
import { uidHmac } from "../src/cripto";
import { permitido } from "../src/limites";
import { LECTURAS_FC_HORA } from "../src/rutas/verificacion";
import { USER_AGENT } from "../src/tarjeta";

const T0 = new Date("2026-11-08T12:00:00Z").getTime();
const MIN = 60_000;
const DIA = 86_400_000;
const UID = "123456";
const IP = "203.0.113.7";
const PEPPER = "pimienta";
const LECTOR = "secreto-del-lector";
const FICHA = "https://forocoches.com/foro/member.php?u=";
const COCHE_FIXTURE = "Seat Ibiza 1.9 TDI 2004";
const TITULO_FIXTURE = "<title>Forocoches - Ver Perfil: YoMismo</title>";
const TABLAS = [
  "cuentas", "tokens", "retos", "canciones", "enlaces_cancion", "publicaciones", "corazones", "denuncias", "limites",
];

interface Pagina { status: number; cabeceras: Record<string, string>; html: string }

/**
 * FC falso: una página por uid (o "lanza" para simular un fallo de red). Un uid sin página preparada
 * sirve la ficha del fixture tal cual (coche COCHE_FIXTURE, que no está en la lista de coches): es lo
 * que lee `reto` al darlo. Apunta cada petición.
 */
let paginas: Map<string, Pagina | "lanza">;
let vistas: { url: string; ua: string | null; cookie: string | null }[];

async function red(url: string, init?: RequestInit): Promise<Response> {
  const cab = new Headers(init?.headers);
  vistas.push({ url, ua: cab.get("User-Agent"), cookie: cab.get("Cookie") });
  if (!url.startsWith(FICHA)) throw new Error("URL inesperada: " + url);
  const p = paginas.get(url.slice(FICHA.length)) ?? { status: 200, cabeceras: {}, html: conCoche };
  if (p === "lanza") throw new Error("sin red");
  return new Response(p.html, { status: p.status, headers: p.cabeceras });
}
const LECTURA = { url: FICHA + UID, ua: USER_AGENT, cookie: null };

/** La ficha del fixture con otro coche, otro nombre en el título u otro registro. */
function ficha(coche: string, o: { nombre?: string; registro?: string } = {}): Pagina {
  let html = conCoche.replace(COCHE_FIXTURE, coche);
  if (o.nombre) html = html.replace(TITULO_FIXTURE, `<title>Forocoches - Ver Perfil: ${o.nombre}</title>`);
  if (o.registro !== undefined) html = html.replace("30-may-2026", o.registro);
  return { status: 200, cabeceras: { "content-type": "text/html" }, html };
}
const CHALLENGE_403: Pagina = { status: 403, cabeceras: { "cf-mitigated": "challenge" }, html: challengeHtml };

function deps(o: Partial<Deps> = {}): Deps {
  return {
    db: env.DB,
    red,
    ahora: () => new Date(T0),
    aleatorio: () => 0.5,
    pepper: PEPPER,
    adminSecreto: "admin",
    lectorSecreto: LECTOR,
    diasMinVoto: 90,
    lectorEnVps: false,
    ...o,
  };
}
const app = (o: Partial<Deps> = {}) => crearApp(deps(o));
const en = (ms: number, o: Partial<Deps> = {}) => app({ ahora: () => new Date(T0 + ms), ...o });
/** La app con el presupuesto global de lecturas de FC bajado a `tope` (el de verdad es LECTURAS_FC_HORA). */
const conTope = (tope: number, o: Partial<Deps> = {}) => crearApp(deps(o), { lecturasFcHora: tope });

function post(ruta: string, cuerpo: unknown, cab: Record<string, string> = {}): Request {
  return new Request("https://x" + ruta, {
    method: "POST",
    headers: { "Content-Type": "application/json", "CF-Connecting-IP": IP, ...cab },
    body: typeof cuerpo === "string" ? cuerpo : JSON.stringify(cuerpo),
  });
}
const get = (ruta: string, cab: Record<string, string> = {}) => new Request("https://x" + ruta, { headers: cab });
const conToken = (t: string) => ({ Authorization: "Bearer " + t });

type Json = Record<string, any>;
async function llamar(a: ReturnType<typeof app>, req: Request): Promise<{ status: number; cuerpo: Json; texto: string }> {
  const r = await a.fetch(req);
  const texto = await r.text();
  return { status: r.status, cuerpo: texto ? JSON.parse(texto) : null, texto };
}

async function pedirReto(a = app(), uid = UID, ip = IP) {
  return llamar(a, post("/v1/verificacion/reto", { uid }, { "CF-Connecting-IP": ip }));
}
const comprobar = (a: ReturnType<typeof app>, reto: string) =>
  llamar(a, post("/v1/verificacion/comprobar", { reto }));

/** Reto + ficha con el coche del reto + comprobar. Devuelve la respuesta de comprobar. */
async function verificar(a = app(), uid = UID, o: { nombre?: string; registro?: string } = {}) {
  const r = await pedirReto(a, uid);
  expect(r.status).toBe(200);
  paginas.set(uid, ficha(r.cuerpo.coche, o));
  return comprobar(a, r.cuerpo.reto);
}

/** Deja el contador global de lecturas de FC de la hora de T0 en n (la siguiente lectura es la n+1). */
async function llenarPresupuestoFc(n: number) {
  const ventana = Math.floor(T0 / 1000 / 3600) * 3600;
  await env.DB.prepare("INSERT INTO limites (clave, ventana, n) VALUES ('fc:global', ?, ?)").bind(ventana, n).run();
}

async function filaReto(id: string) {
  return env.DB.prepare("SELECT * FROM retos WHERE id = ?").bind(id).first<Json>();
}

beforeEach(async () => {
  paginas = new Map();
  vistas = [];
  await env.DB.batch(TABLAS.map((t) => env.DB.prepare(`DELETE FROM ${t}`)));
});

describe("POST /v1/verificacion/reto", () => {
  it("retoOk: id opaco, coche de la lista y caduca a los 10 minutos", async () => {
    const r = await pedirReto();
    expect(r.status).toBe(200);
    expect(Object.keys(r.cuerpo).sort()).toEqual(["caduca", "coche", "reto"]);
    expect(r.cuerpo.reto).toMatch(/^[0-9A-HJKMNP-TV-Z]{26}$/);
    expect(r.cuerpo.coche).toBe(cocheEn(Math.floor(0.5 * totalCoches())));
    expect(r.cuerpo.caduca).toBe(T0 + 600_000);
    // En el reto el uid va en claro (hace falta para leer la ficha) junto a su HMAC, y el coche
    // que la ficha tenía al darlo (dato público, muere con el reto).
    const fila = await filaReto(r.cuerpo.reto);
    expect(fila).toMatchObject({
      uid: UID, uid_hmac: await uidHmac(PEPPER, UID), coche: r.cuerpo.coche, intentos: 0, coche_antes: COCHE_FIXTURE,
    });
    // Darlo lee la ficha una vez: de invitado, con UA de móvil.
    expect(vistas).toEqual([LECTURA]);
  });

  it("el reto lee la ficha y nunca da el coche que ya tiene puesto (Resolución 16)", async () => {
    // El azar cae justo en el índice del coche que muestra el perfil: sin la lectura, lo daría.
    for (const k of [0, 777, Math.floor(totalCoches() / 3), totalCoches() - 1]) {
      const puesto = cocheEn(k);
      const aleatorio = () => (k + 0.5) / totalCoches();
      expect(cocheEn(Math.floor(aleatorio() * totalCoches()))).toBe(puesto);
      // Tal cual, y como lo podría dejar FC (otras mayúsculas, espacios de más).
      for (const visto of [puesto, "  " + puesto.toLowerCase().replace(/ /g, "   ") + " "]) {
        await env.DB.prepare("DELETE FROM limites").run(); // esta prueba pide más de 5 retos del mismo uid
        paginas.set(UID, ficha(visto));
        const r = await pedirReto(app({ aleatorio }));
        expect(r.status).toBe(200);
        expect(cochesIguales(r.cuerpo.coche, puesto)).toBe(false);
        expect((await filaReto(r.cuerpo.reto))!.coche_antes).toBe(visto.trim());
      }
    }
  });

  it("si FC no deja leer la ficha al dar el reto -> 503 challenge y no se crea reto (sí gasta cupo)", async () => {
    for (const p of [CHALLENGE_403, "lanza" as const, { status: 500, cabeceras: {}, html: "x" }]) {
      paginas.set(UID, p);
      const r = await pedirReto();
      expect(r.status).toBe(503);
      expect(r.cuerpo).toEqual({ error: "challenge" });
    }
    expect(await env.DB.prepare("SELECT COUNT(*) AS n FROM retos").first("n")).toBe(0);
    // El cupo del uid se cuenta ANTES de leer FC: así las lecturas que hace `reto` siguen acotadas
    // (5/h por uid, 20/h por IP) aunque FC esté con challenge.
    paginas.set(UID, CHALLENGE_403);
    await pedirReto();
    await pedirReto();
    paginas.delete(UID);
    const sexto = await pedirReto();
    expect(sexto.status).toBe(429);
    expect(sexto.cuerpo).toEqual({ error: "limite" });
    expect(vistas).toHaveLength(5);
  });

  it("reto de un usuario de FC que no existe -> 404 no_existe y no se crea reto", async () => {
    paginas.set(UID, { status: 200, cabeceras: {}, html: noExiste });
    const r = await pedirReto();
    expect(r.status).toBe(404);
    expect(r.cuerpo).toEqual({ error: "no_existe" });
    expect(await env.DB.prepare("SELECT COUNT(*) AS n FROM retos").first("n")).toBe(0);
  });

  it("una ficha sin coche deja coche_antes a null", async () => {
    paginas.set(UID, ficha(""));
    const r = await pedirReto();
    expect(r.status).toBe(200);
    expect((await filaReto(r.cuerpo.reto))!.coche_antes).toBeNull();
  });

  it("retoUidInvalido: lo que no es un número de usuario canónico -> 400 uid", async () => {
    for (const uid of ["12a", "", "0", "0123456", " 123456", "12345678901", 123456, null, ["1"]]) {
      const r = await llamar(app(), post("/v1/verificacion/reto", { uid }));
      expect(r.status, JSON.stringify(uid)).toBe(400);
      expect(r.cuerpo).toEqual({ error: "uid" });
    }
    for (const cuerpo of ["no es json", "[]", "{}", "null"]) {
      const r = await llamar(app(), post("/v1/verificacion/reto", cuerpo));
      expect(r.status, cuerpo).toBe(400);
      expect(r.cuerpo).toEqual({ error: "uid" });
    }
  });

  it("un cuerpo enorme se corta sin leerlo entero -> 400", async () => {
    const r = await llamar(app(), post("/v1/verificacion/reto", { uid: UID, relleno: "x".repeat(20_000) }));
    expect(r.status).toBe(400);
  });

  it("retoLimite: el 6.º del mismo uid en una hora -> 429 limite; la hora siguiente vuelve a dejar", async () => {
    for (let i = 0; i < 5; i++) {
      // Desde IPs distintas: el límite es del uid.
      expect((await pedirReto(app(), UID, `198.51.100.${i}`)).status).toBe(200);
    }
    const sexto = await pedirReto(app(), UID, "198.51.100.99");
    expect(sexto.status).toBe(429);
    expect(sexto.cuerpo).toEqual({ error: "limite" });
    expect((await pedirReto(en(61 * MIN), UID, "198.51.100.98")).status).toBe(200);
  });

  it("límite por IP: el 21.º desde la misma IP en una hora -> 429 limite", async () => {
    for (let i = 0; i < 20; i++) {
      expect((await pedirReto(app(), String(100000 + i))).status).toBe(200);
    }
    const r = await pedirReto(app(), "200000");
    expect(r.status).toBe(429);
    expect(r.cuerpo).toEqual({ error: "limite" });
    // Sin CF-Connecting-IP también hay límite (clave fija "sin-ip"), separado del de esta IP.
    const sinIp = new Request("https://x/v1/verificacion/reto", { method: "POST", body: JSON.stringify({ uid: "300000" }) });
    expect((await app().fetch(sinIp)).status).toBe(200);
  });

  it("límite por IP con IPv6: todo el /64 comparte cupo; otro /64 no", async () => {
    // Un solo equipo IPv6 puede ir cambiando de dirección dentro de su /64.
    for (let i = 0; i < 20; i++) {
      expect((await pedirReto(app(), String(100000 + i), `2001:db8:1:2::${(i + 1).toString(16)}`)).status).toBe(200);
    }
    const mismo64 = await pedirReto(app(), "200000", "2001:DB8:1:2:ffff:eeee:dddd:cccc");
    expect(mismo64.status).toBe(429);
    expect(mismo64.cuerpo).toEqual({ error: "limite" });
    expect((await pedirReto(app(), "200001", "2001:db8:1:3::1")).status).toBe(200);
  });

  it("límite por IP: la IPv4 mapeada en IPv6 es la misma IPv4", async () => {
    for (let i = 0; i < 20; i++) expect((await pedirReto(app(), String(100000 + i), "198.51.100.7")).status).toBe(200);
    expect((await pedirReto(app(), "200000", "::ffff:198.51.100.7")).status).toBe(429);
  });

  it("las claves de límites llevan HMAC, nunca el uid ni la IP en claro", async () => {
    const r = await pedirReto();
    const { results } = await env.DB.prepare("SELECT clave, n FROM limites").all<{ clave: string; n: number }>();
    // La IP va con el prefijo "ip:" antes del HMAC: nunca puede coincidir con el HMAC de un uid.
    expect(results.map((f) => f.clave).sort()).toEqual(
      [
        `reto:ip:${await uidHmac(PEPPER, "ip:" + IP)}`, `reto:uid:${await uidHmac(PEPPER, UID)}`,
        `lectura:reto:${r.cuerpo.reto}`, "fc:global",
      ].sort(),
    );
    // La lectura de la ficha al dar el reto ya cuenta para el tope de lecturas de ese reto y el global.
    expect(results.find((f) => f.clave.startsWith("lectura:"))!.n).toBe(1);
    expect(results.find((f) => f.clave === "fc:global")!.n).toBe(1);
  });

  it("presupuesto GLOBAL de lecturas de FC: lo comparten uids e IPs distintos; agotado, reto -> 503 sin reto y sin leer FC", async () => {
    // Cada uid y cada IP con su cupo casi entero: lo único que frena es el presupuesto de todos.
    const a = conTope(3);
    for (let i = 0; i < 3; i++) {
      expect((await pedirReto(a, String(100000 + i), `198.51.100.${i}`)).status).toBe(200);
    }
    const r = await pedirReto(a, "200000", "2001:db8:9::1");
    expect(r.status).toBe(503);
    expect(r.cuerpo).toEqual({ error: "challenge" });
    expect(vistas).toHaveLength(3);
    expect(await env.DB.prepare("SELECT COUNT(*) AS n FROM retos").first("n")).toBe(3);
    // A la hora siguiente vuelve a haber presupuesto.
    const luego = conTope(3, { ahora: () => new Date(T0 + 60 * MIN) });
    expect((await pedirReto(luego, "200000", "2001:db8:9::1")).status).toBe(200);
  });

  it("sin opciones, el presupuesto global es LECTURAS_FC_HORA (600 por hora)", async () => {
    expect(LECTURAS_FC_HORA).toBe(600);
    await llenarPresupuestoFc(LECTURAS_FC_HORA - 1);
    expect((await pedirReto()).status).toBe(200); // la lectura 600.ª aún cabe
    const r = await pedirReto(app(), "654321", "198.51.100.1");
    expect(r.status).toBe(503);
    expect(r.cuerpo).toEqual({ error: "challenge" });
    expect(vistas).toHaveLength(1);
  });

  it("sin CF-Connecting-IP o con algo que no es una IP: un mismo cubo fijo, sin-ip", async () => {
    const sinIp = (uid: string) =>
      llamar(app(), new Request("https://x/v1/verificacion/reto", { method: "POST", body: JSON.stringify({ uid }) }));
    for (let i = 0; i < 10; i++) expect((await sinIp(String(100000 + i))).status).toBe(200);
    for (let i = 10; i < 20; i++) expect((await pedirReto(app(), String(100000 + i), "no-es-una-ip")).status).toBe(200);
    const r = await pedirReto(app(), "200000", "1.2.3.999");
    expect(r.status).toBe(429);
    expect(r.cuerpo).toEqual({ error: "limite" });
    const { results } = await env.DB.prepare("SELECT clave FROM limites WHERE clave LIKE 'reto:ip:%'").all<{ clave: string }>();
    expect(results.map((f) => f.clave)).toEqual(["reto:ip:sin-ip"]);
  });

  it("si el perfil aún tiene el coche del reto anterior, el nuevo es otro", async () => {
    const a = await pedirReto();
    paginas.set(UID, ficha(a.cuerpo.coche)); // la app lo puso y no llegó a devolverlo
    const b = await pedirReto();
    expect(b.cuerpo.coche).not.toBe(a.cuerpo.coche); // aleatorio fijo: sin la lectura saldría el mismo
  });
});

describe("POST /v1/verificacion/comprobar", () => {
  it("comprobarOk: 200 con token y GET /v1/yo con ese token es YoMismo", async () => {
    const r = await verificar();
    expect(r.status).toBe(200);
    expect(Object.keys(r.cuerpo).sort()).toEqual(["diasCuenta", "nombre", "puedeVotar", "token"]);
    expect(r.cuerpo.token).toMatch(/^[A-Za-z0-9_-]{43}$/);
    // 30-may-2026 -> 8-nov-2026 = 162 días, más que los 90 que pide votar.
    expect(r.cuerpo).toMatchObject({ nombre: "YoMismo", diasCuenta: 162, puedeVotar: true });
    // Dos lecturas de FC (al dar el reto y al comprobar), a la ficha de ese uid, con UA de móvil y sin cookies.
    expect(vistas).toEqual([LECTURA, LECTURA]);

    const yo = await llamar(app(), get("/v1/yo", conToken(r.cuerpo.token)));
    expect(yo.status).toBe(200);
    expect(yo.cuerpo).toEqual({
      nombre: "YoMismo", diasCuenta: 162, puedePublicar: true, publicacionesRestantes: 6,
    });

    // En la base solo el hash del token, y la cuenta con el HMAC del uid.
    const t = await env.DB.prepare("SELECT * FROM tokens").all<Json>();
    expect(t.results).toHaveLength(1);
    expect(t.results[0].hash).not.toBe(r.cuerpo.token);
    expect(t.results[0].caduca).toBe(T0 + 365 * DIA);
    const c = await env.DB.prepare("SELECT * FROM cuentas").first<Json>();
    expect(c).toMatchObject({ uid_hmac: await uidHmac(PEPPER, UID), nombre: "YoMismo", registro_fc: "30-may-2026", baneada: 0 });
  });

  it("uidNuncaEnClaro: tras verificar, ninguna fila de ninguna tabla contiene el uid", async () => {
    const r = await verificar();
    expect(r.status).toBe(200);
    const { results: tablas } = await env.DB.prepare(
      "SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite%' AND name NOT LIKE '_cf%'",
    ).all<{ name: string }>();
    expect(tablas.length).toBeGreaterThanOrEqual(12);
    let filas = 0;
    for (const { name } of tablas) {
      const { results } = await env.DB.prepare(`SELECT * FROM "${name}"`).all();
      filas += results.length;
      for (const f of results) expect(JSON.stringify(f), name).not.toContain(UID);
    }
    expect(filas).toBeGreaterThan(0);
    expect(await env.DB.prepare("SELECT COUNT(*) AS n FROM retos").first("n")).toBe(0);
  });

  it("uidNuncaEnClaro con varios retos del mismo uid: verificar con uno borra todos los de ese uid", async () => {
    // Un reto anterior (gastado con 429 intentos, app reiniciada, o pedido por otro con ese uid)
    // también lleva el uid en claro.
    const primero = await pedirReto();
    for (let i = 0; i < 3; i++) expect((await comprobar(app(), primero.cuerpo.reto)).status).toBe(409);
    expect((await comprobar(app(), primero.cuerpo.reto)).status).toBe(429);
    const otroUsuario = await pedirReto(app(), "654321");
    const segundo = await pedirReto();
    expect(primero.cuerpo.reto).not.toBe(segundo.cuerpo.reto);
    paginas.set(UID, ficha(segundo.cuerpo.coche));
    expect((await comprobar(app(), segundo.cuerpo.reto)).status).toBe(200);

    const { results: tablas } = await env.DB.prepare(
      "SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite%' AND name NOT LIKE '_cf%'",
    ).all<{ name: string }>();
    for (const { name } of tablas) {
      const { results } = await env.DB.prepare(`SELECT * FROM "${name}"`).all();
      for (const f of results) expect(JSON.stringify(f), name).not.toContain(UID);
    }
    // Los retos de OTRO uid no se tocan.
    const quedan = await env.DB.prepare("SELECT id FROM retos").all<{ id: string }>();
    expect(quedan.results.map((f) => f.id)).toEqual([otroUsuario.cuerpo.reto]);
  });

  it("las respuestas no devuelven ni el uid ni su HMAC", async () => {
    const hmac = await uidHmac(PEPPER, UID);
    const reto = await pedirReto();
    paginas.set(UID, ficha(reto.cuerpo.coche));
    const ok = await comprobar(app(), reto.cuerpo.reto);
    const yo = await llamar(app(), get("/v1/yo", conToken(ok.cuerpo.token)));
    for (const r of [reto, ok, yo]) {
      expect(r.texto).not.toContain(UID);
      expect(r.texto).not.toContain(hmac);
    }
  });

  it("comprobarNoCoincide: 409 con intentosRestantes, y al 4.º 429 intentos", async () => {
    const r = await pedirReto();
    paginas.set(UID, ficha(COCHE_FIXTURE));
    expect(r.cuerpo.coche).not.toBe(COCHE_FIXTURE);
    for (const quedan of [2, 1, 0]) {
      const c = await comprobar(app(), r.cuerpo.reto);
      expect(c.status).toBe(409);
      expect(c.cuerpo).toEqual({ error: "no_coincide", intentosRestantes: quedan });
    }
    const cuarto = await comprobar(app(), r.cuerpo.reto);
    expect(cuarto.status).toBe(429);
    expect(cuarto.cuerpo).toEqual({ error: "intentos" });
    expect(vistas).toHaveLength(1 + 3); // la del reto y las 3 comprobaciones; el 4.º ni pregunta a FC
  });

  it("comprobar rechaza que case si el coche del reto ya estaba en la ficha al darlo (coche_antes)", async () => {
    // Defensa en profundidad: `reto` ya nunca da el coche puesto; aquí se fuerza en la base.
    const r = await pedirReto();
    await env.DB.prepare("UPDATE retos SET coche_antes = coche WHERE id = ?").bind(r.cuerpo.reto).run();
    paginas.set(UID, ficha(r.cuerpo.coche));
    const c = await comprobar(app(), r.cuerpo.reto);
    expect(c.status).toBe(409);
    expect(c.cuerpo).toEqual({ error: "no_coincide", intentosRestantes: 2 });
    expect(await env.DB.prepare("SELECT COUNT(*) AS n FROM cuentas").first("n")).toBe(0);
    expect(await env.DB.prepare("SELECT COUNT(*) AS n FROM tokens").first("n")).toBe(0);
    // También con otras mayúsculas/espacios en coche_antes.
    await env.DB.prepare("UPDATE retos SET coche_antes = ? WHERE id = ?")
      .bind(" " + r.cuerpo.coche.toUpperCase() + "  ", r.cuerpo.reto).run();
    expect((await comprobar(app(), r.cuerpo.reto)).status).toBe(409);
  });

  it("sin coche en la ficha también es no_coincide", async () => {
    const r = await pedirReto();
    paginas.set(UID, ficha("")); // fila del coche vacía
    const c = await comprobar(app(), r.cuerpo.reto);
    expect(c.status).toBe(409);
    expect(c.cuerpo).toEqual({ error: "no_coincide", intentosRestantes: 2 });
  });

  it("el coche casa sin mirar mayúsculas ni espacios de más", async () => {
    const r = await pedirReto();
    paginas.set(UID, ficha("  " + r.cuerpo.coche.toUpperCase().replace(/ /g, "   ") + " "));
    expect((await comprobar(app(), r.cuerpo.reto)).status).toBe(200);
  });

  it("comprobarChallengeNoGastaIntento: 503 challenge y los intentos no se mueven", async () => {
    const r = await pedirReto();
    for (const p of [CHALLENGE_403, { ...CHALLENGE_403, status: 200, cabeceras: {} }, "lanza" as const,
      { status: 500, cabeceras: {}, html: "error" }]) {
      paginas.set(UID, p);
      const c = await comprobar(app(), r.cuerpo.reto);
      expect(c.status).toBe(503);
      expect(c.cuerpo).toEqual({ error: "challenge" });
      expect((await filaReto(r.cuerpo.reto))!.intentos).toBe(0);
    }
    // Y cuando FC deja, el mismo reto sirve.
    paginas.set(UID, ficha(r.cuerpo.coche));
    expect((await comprobar(app(), r.cuerpo.reto)).status).toBe(200);
  });

  it("un reto no puede leer FC sin fin: la 11.ª lectura -> 429 intentos", async () => {
    const r = await pedirReto(); // 1.ª lectura: la del reto
    paginas.set(UID, CHALLENGE_403);
    for (let i = 0; i < 9; i++) expect((await comprobar(app(), r.cuerpo.reto)).status).toBe(503);
    const c = await comprobar(app(), r.cuerpo.reto);
    expect(c.status).toBe(429);
    expect(c.cuerpo).toEqual({ error: "intentos" });
    expect(vistas).toHaveLength(10);
    // Las llamadas rechazadas por el tope del reto no gastan el presupuesto global (solo las 10 lecturas).
    const g = await env.DB.prepare("SELECT n FROM limites WHERE clave = 'fc:global'").first<number>("n");
    expect(g).toBe(10);
  });

  it("presupuesto GLOBAL agotado: comprobar -> 503 challenge, sin leer FC y sin gastar intento", async () => {
    const r = await pedirReto(conTope(1)); // la única lectura de esta hora
    expect(r.status).toBe(200);
    paginas.set(UID, ficha(r.cuerpo.coche));
    const c = await comprobar(conTope(1), r.cuerpo.reto);
    expect(c.status).toBe(503);
    expect(c.cuerpo).toEqual({ error: "challenge" });
    expect(vistas).toHaveLength(1); // solo la del reto
    expect(await filaReto(r.cuerpo.reto)).toMatchObject({ intentos: 0, estado: "nuevo" });
    // El reto sigue valiendo en cuanto vuelve a haber presupuesto.
    expect((await comprobar(conTope(3), r.cuerpo.reto)).status).toBe(200);
  });

  it("comprobarCaducado: a los 11 minutos -> 410 caducado, sin leer FC", async () => {
    const r = await pedirReto();
    paginas.set(UID, ficha(r.cuerpo.coche));
    vistas = [];
    const c = await comprobar(en(11 * MIN), r.cuerpo.reto);
    expect(c.status).toBe(410);
    expect(c.cuerpo).toEqual({ error: "caducado" });
    expect(vistas).toHaveLength(0);
    // Y el uid en claro ya no está.
    expect(await filaReto(r.cuerpo.reto)).toBeNull();
    // Ya borrado, el mismo reto da 404 reto (para la app, 404 reto y 410 caducado son "pide otro").
    const otra = await comprobar(en(12 * MIN), r.cuerpo.reto);
    expect(otra.status).toBe(404);
    expect(otra.cuerpo).toEqual({ error: "reto" });
  });

  it("reto desconocido o mal formado -> 404 reto / 400 reto", async () => {
    const r = await comprobar(app(), "01JBZZZZZZZZZZZZZZZZZZZZZZ");
    expect(r.status).toBe(404);
    expect(r.cuerpo).toEqual({ error: "reto" });
    for (const reto of [123, null, "", "x".repeat(200)]) {
      const m = await llamar(app(), post("/v1/verificacion/comprobar", { reto }));
      expect(m.status, JSON.stringify(reto)).toBe(400);
      expect(m.cuerpo).toEqual({ error: "reto" });
    }
  });

  it("usuario de FC inexistente -> 404 no_existe y gasta intento", async () => {
    const r = await pedirReto();
    paginas.set(UID, { status: 200, cabeceras: {}, html: noExiste });
    const c = await comprobar(app(), r.cuerpo.reto);
    expect(c.status).toBe(404);
    expect(c.cuerpo).toEqual({ error: "no_existe" });
    expect((await filaReto(r.cuerpo.reto))!.intentos).toBe(1);
  });

  it("reverificarActualizaNombre: mismo uid y nombre nuevo -> la misma cuenta, nombre al día", async () => {
    const a = await verificar();
    const id1 = await env.DB.prepare("SELECT id FROM cuentas").first("id");
    const b = await verificar(en(5 * MIN), UID, { nombre: "OtroNombre", registro: "01-ene-2020" });
    expect(b.status).toBe(200);
    expect(b.cuerpo).toMatchObject({ nombre: "OtroNombre", puedeVotar: true });
    expect(b.cuerpo.token).not.toBe(a.cuerpo.token);
    const { results } = await env.DB.prepare("SELECT * FROM cuentas").all<Json>();
    expect(results).toHaveLength(1);
    expect(results[0]).toMatchObject({ id: id1, nombre: "OtroNombre", registro_fc: "01-ene-2020", creada: T0, ultima_verif: T0 + 5 * MIN });
    // El token viejo sigue valiendo hasta que caduque, y los dos son de la misma cuenta.
    for (const t of [a.cuerpo.token, b.cuerpo.token]) {
      const yo = await llamar(app(), get("/v1/yo", conToken(t)));
      expect(yo.cuerpo.nombre).toBe("OtroNombre");
    }
  });

  it("el nombre sale del título de la ficha, nunca del cuerpo de la petición", async () => {
    const r = await pedirReto();
    paginas.set(UID, ficha(r.cuerpo.coche));
    const c = await llamar(app(), post("/v1/verificacion/comprobar", { reto: r.cuerpo.reto, nombre: "Impostor" }));
    expect(c.cuerpo.nombre).toBe("YoMismo");
  });

  it("cuenta reciente o registro ilegible: verifica pero no vota", async () => {
    const reciente = await verificar(app(), UID, { registro: "01-oct-2026" });
    expect(reciente.cuerpo).toMatchObject({ diasCuenta: 38, puedeVotar: false });
    const ilegible = await verificar(app(), "654321", { registro: "" });
    expect(ilegible.status).toBe(200);
    expect(ilegible.cuerpo).toMatchObject({ diasCuenta: null, puedeVotar: false });
    const yo = await llamar(app(), get("/v1/yo", conToken(ilegible.cuerpo.token)));
    expect(yo.cuerpo).toMatchObject({ diasCuenta: null, puedePublicar: false });
  });

  it("baneadaNoVerifica: ni reto, ni comprobar, ni sus tokens", async () => {
    const ok = await verificar();
    const pendiente = await pedirReto(); // reto pedido antes del baneo
    await env.DB.prepare("UPDATE cuentas SET baneada = 1").run();
    vistas = [];

    const reto = await pedirReto();
    expect(reto.status).toBe(403);
    expect(reto.cuerpo).toEqual({ error: "baneada" });

    paginas.set(UID, ficha(pendiente.cuerpo.coche));
    const c = await comprobar(app(), pendiente.cuerpo.reto);
    expect(c.status).toBe(403);
    expect(c.cuerpo).toEqual({ error: "baneada" });
    expect(vistas).toHaveLength(0); // ni el reto ni el comprobar de una cuenta baneada leen FC

    const yo = await llamar(app(), get("/v1/yo", conToken(ok.cuerpo.token)));
    expect(yo.status).toBe(403);
    expect(yo.cuerpo).toEqual({ error: "baneada" });
    expect(await env.DB.prepare("SELECT COUNT(*) AS n FROM tokens").first("n")).toBe(1);
  });
});

describe("token y GET /v1/yo", () => {
  it("tokenCaducado: pasado un año -> 401 token", async () => {
    const ok = await verificar();
    expect((await llamar(en(365 * DIA - 1), get("/v1/yo", conToken(ok.cuerpo.token)))).status).toBe(200);
    const r = await llamar(en(365 * DIA), get("/v1/yo", conToken(ok.cuerpo.token)));
    expect(r.status).toBe(401);
    expect(r.cuerpo).toEqual({ error: "token" });
  });

  it("sin token, token mal formado o desconocido -> 401 token", async () => {
    await verificar();
    for (const cab of [
      {}, { Authorization: "" }, { Authorization: "Bearer" }, { Authorization: "Basic abc" },
      conToken("x".repeat(43)), conToken("corto"), conToken("A".repeat(43) + " extra"),
    ]) {
      const r = await llamar(app(), get("/v1/yo", cab));
      expect(r.status, JSON.stringify(cab)).toBe(401);
      expect(r.cuerpo).toEqual({ error: "token" });
    }
  });
});

describe("modoVps: el lector de fichas en el VPS", () => {
  const vps = (o: Partial<Deps> = {}) => app({ lectorEnVps: true, ...o });
  const lector = (s = LECTOR) => ({ "X-Lector": s });

  it("el reto no lee la ficha (el Worker no puede): coche_antes null y no repite el coche del reto vivo anterior", async () => {
    const a = await pedirReto(vps());
    expect(a.status).toBe(200);
    expect((await filaReto(a.cuerpo.reto))!.coche_antes).toBeNull();
    const b = await pedirReto(vps());
    expect(b.cuerpo.coche).not.toBe(a.cuerpo.coche); // aleatorio fijo: sin distintoDe saldría el mismo
    expect(vistas).toHaveLength(0);
    // Ni cuenta lectura al darlo: solo las claves de reto.
    const { results } = await env.DB.prepare("SELECT clave FROM limites WHERE clave LIKE 'lectura:%'").all();
    expect(results).toHaveLength(0);
  });

  it("202 pendiente, el VPS lee y devuelve la ficha, y el siguiente comprobar da el token", async () => {
    const r = await pedirReto(vps());
    const p = await comprobar(vps(), r.cuerpo.reto);
    expect(p.status).toBe(202);
    expect(p.cuerpo).toEqual({ estado: "pendiente" });
    // Mientras no llega la lectura, sigue pendiente (la app pregunta otra vez).
    expect((await comprobar(vps(), r.cuerpo.reto)).status).toBe(202);

    const pend = await llamar(vps(), get("/v1/lector/pendientes", lector()));
    expect(pend.status).toBe(200);
    expect(pend.cuerpo).toEqual([{ reto: r.cuerpo.reto, uid: UID }]);

    const f = ficha(r.cuerpo.coche);
    const res = await llamar(vps(), post("/v1/lector/resultado",
      { reto: r.cuerpo.reto, status: 200, cfMitigated: null, html: f.html }, lector()));
    expect(res.status).toBe(200);
    expect((await llamar(vps(), get("/v1/lector/pendientes", lector()))).cuerpo).toEqual([]);

    const ok = await comprobar(vps(), r.cuerpo.reto);
    expect(ok.status).toBe(200);
    expect(ok.cuerpo).toMatchObject({ nombre: "YoMismo", diasCuenta: 162, puedeVotar: true });
    expect(vistas).toHaveLength(0); // el Worker no ha tocado FC
  });

  it("X-Lector incorrecta, ausente o sin secreto configurado -> 401 lector", async () => {
    const r = await pedirReto(vps());
    await comprobar(vps(), r.cuerpo.reto);
    const casos: [ReturnType<typeof app>, Record<string, string>][] = [
      [vps(), lector("otro")], [vps(), {}], [vps(), lector(LECTOR + "x")], [vps(), lector("")],
      [vps({ lectorSecreto: "" }), lector("")],
    ];
    for (const [a, cab] of casos) {
      const g = await llamar(a, get("/v1/lector/pendientes", cab));
      expect(g.status).toBe(401);
      expect(g.cuerpo).toEqual({ error: "lector" });
      const p = await llamar(a, post("/v1/lector/resultado",
        { reto: r.cuerpo.reto, status: 200, cfMitigated: null, html: ficha(r.cuerpo.coche).html }, cab));
      expect(p.status).toBe(401);
    }
    expect((await filaReto(r.cuerpo.reto))!.estado).toBe("pendiente");
  });

  it("una lectura con challenge -> 503 sin gastar intento, y el siguiente comprobar vuelve a pedirla", async () => {
    const r = await pedirReto(vps());
    await comprobar(vps(), r.cuerpo.reto);
    await llamar(vps(), post("/v1/lector/resultado",
      { reto: r.cuerpo.reto, status: 403, cfMitigated: "challenge", html: challengeHtml }, lector()));
    const c = await comprobar(vps(), r.cuerpo.reto);
    expect(c.status).toBe(503);
    expect(c.cuerpo).toEqual({ error: "challenge" });
    expect((await filaReto(r.cuerpo.reto))!.intentos).toBe(0);
    expect((await comprobar(vps(), r.cuerpo.reto)).status).toBe(202);
    expect((await llamar(vps(), get("/v1/lector/pendientes", lector()))).cuerpo).toHaveLength(1);
  });

  it("presupuesto GLOBAL agotado: no se pide lectura al VPS -> 503 challenge, sin pendiente", async () => {
    // El VPS lee FC igual que el Worker: lo que FC bloquee por exceso también lo pagaría su IP.
    const a = conTope(1, { lectorEnVps: true });
    const r1 = await pedirReto(a); // en modo VPS dar el reto no lee ni gasta presupuesto
    const r2 = await pedirReto(a, "654321");
    expect((await comprobar(a, r1.cuerpo.reto)).status).toBe(202); // la única lectura de esta hora
    expect((await comprobar(a, r1.cuerpo.reto)).status).toBe(202); // ya pendiente: no pide otra
    const c = await comprobar(a, r2.cuerpo.reto);
    expect(c.status).toBe(503);
    expect(c.cuerpo).toEqual({ error: "challenge" });
    expect(await filaReto(r2.cuerpo.reto)).toMatchObject({ estado: "nuevo", intentos: 0 });
    expect((await llamar(a, get("/v1/lector/pendientes", lector()))).cuerpo).toEqual([{ reto: r1.cuerpo.reto, uid: UID }]);
    expect(vistas).toHaveLength(0);
  });

  it("una lectura con otro coche -> 409 y gasta intento, igual que en el Worker", async () => {
    const r = await pedirReto(vps());
    await comprobar(vps(), r.cuerpo.reto);
    await llamar(vps(), post("/v1/lector/resultado",
      { reto: r.cuerpo.reto, status: 200, cfMitigated: null, html: ficha(COCHE_FIXTURE).html }, lector()));
    const c = await comprobar(vps(), r.cuerpo.reto);
    expect(c.status).toBe(409);
    expect(c.cuerpo).toEqual({ error: "no_coincide", intentosRestantes: 2 });
  });

  it("resultado de un reto que no está pendiente o caducado -> 404 reto; cuerpo malo -> 400", async () => {
    const r = await pedirReto(vps());
    const cuerpo = { reto: r.cuerpo.reto, status: 200, cfMitigated: null, html: ficha(r.cuerpo.coche).html };
    const noPend = await llamar(vps(), post("/v1/lector/resultado", cuerpo, lector()));
    expect(noPend.status).toBe(404);
    expect(noPend.cuerpo).toEqual({ error: "reto" });
    await comprobar(vps(), r.cuerpo.reto);
    expect((await llamar(en(11 * MIN, { lectorEnVps: true }), post("/v1/lector/resultado", cuerpo, lector()))).status).toBe(404);
    expect((await llamar(en(11 * MIN, { lectorEnVps: true }), get("/v1/lector/pendientes", lector()))).cuerpo).toEqual([]);
    for (const malo of [
      { ...cuerpo, status: "200" }, { ...cuerpo, html: 5 }, { ...cuerpo, cfMitigated: true }, { ...cuerpo, reto: 1 },
    ]) {
      const m = await llamar(vps(), post("/v1/lector/resultado", malo, lector()));
      expect(m.status, JSON.stringify(Object.keys(malo))).toBe(400);
    }
    // Y la lectura buena sigue entrando.
    expect((await llamar(vps(), post("/v1/lector/resultado", cuerpo, lector()))).status).toBe(200);
  });
});

describe("acceso por ruta", () => {
  it("admin solo pasa con X-Admin igual al secreto (la comprobación completa está en admin.test.ts)", async () => {
    const con = (h: Record<string, string>) => acceder("admin", new Request("https://x/v1/admin/algo", { headers: h }), deps());
    expect(await con({ "X-Admin": "admin" })).toBeNull();
    const r = await con({ "X-Admin": "otro" });
    expect(r).toBeInstanceOf(Response);
    expect((r as Response).status).toBe(401);
  });
  it("un acceso que el router no conoce se deniega", async () => {
    const r = await acceder("inventado" as never, new Request("https://x/"), deps());
    expect(r).toBeInstanceOf(Response);
    expect((r as Response).status).toBe(401);
  });
});

describe("permitido (límites por ventana)", () => {
  it("deja max veces por ventana y vuelve a dejar en la siguiente", async () => {
    const t = new Date(T0);
    for (let i = 0; i < 3; i++) expect(await permitido(env.DB, "k", 3600, 3, t)).toBe(true);
    expect(await permitido(env.DB, "k", 3600, 3, t)).toBe(false);
    expect(await permitido(env.DB, "otra", 3600, 3, t)).toBe(true);
    expect(await permitido(env.DB, "k", 3600, 3, new Date(T0 + 3600_000))).toBe(true);
  });
});
