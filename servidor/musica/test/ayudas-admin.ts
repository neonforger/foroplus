// Ayudas comunes de admin.test.ts, cuenta.test.ts y cron.test.ts: cuentas, canciones y publicaciones directas en
// la base, sin pasar por la red ni por la app (la app la montan los tests).
import { env } from "cloudflare:workers";
import { crearApp, type Deps } from "../src/app";
import { hashToken, nuevoId, nuevoToken } from "../src/cripto";

export const T0 = new Date("2026-11-04T12:00:00Z").getTime();
export const MIN = 60_000;
export const HORA = 3_600_000;
export const DIA = 86_400_000;
export const TABLAS = [
  "cuentas", "tokens", "retos", "canciones", "enlaces_cancion", "publicaciones", "corazones", "mas_querida",
  "conversor", "denuncias", "limites",
];

export type Json = Record<string, any>;

export function deps(o: Partial<Deps> = {}): Deps {
  return {
    db: env.DB,
    red: async (url) => {
      throw new Error("sin red en estas pruebas: " + url);
    },
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
/** La app con el reloj en el instante `t` (epoch ms). */
export const enT = (t: number, o: Partial<Deps> = {}) => crearApp(deps({ ahora: () => new Date(t), ...o }));
export const en = (ms = 0, o: Partial<Deps> = {}) => enT(T0 + ms, o);
export type App = ReturnType<typeof en>;

export interface Shur { id: string; token: string }
let numCuenta = 0;
export function reiniciarCuentas(): void {
  numCuenta = 0;
}

const MESES = ["ene", "feb", "mar", "abr", "may", "jun", "jul", "ago", "sep", "oct", "nov", "dic"];
function registroHace(dias: number): string {
  const f = new Date(T0 - dias * DIA);
  return `${String(f.getUTCDate()).padStart(2, "0")}-${MESES[f.getUTCMonth()]}-${f.getUTCFullYear()}`;
}

/** Una cuenta verificada con token. `creada` = cuándo se registró en ForoPlus (por defecto, hace 400 días). */
export async function shur(o: { creada?: number; baneada?: boolean } = {}): Promise<Shur> {
  numCuenta++;
  const id = nuevoId(new Date(T0));
  const token = nuevoToken();
  await env.DB.batch([
    env.DB
      .prepare("INSERT INTO cuentas (id, uid_hmac, nombre, registro_fc, baneada, creada, ultima_verif) VALUES (?, ?, ?, ?, ?, ?, ?)")
      .bind(id, "hmac" + numCuenta, "YoMismo" + numCuenta, registroHace(400), o.baneada ? 1 : 0, o.creada ?? T0 - 400 * DIA, T0),
    env.DB.prepare("INSERT INTO tokens (hash, cuenta, creado, caduca) VALUES (?, ?, ?, ?)")
      .bind(await hashToken(token), id, T0, T0 + 365 * DIA),
  ]);
  return { id, token };
}

export const conToken = (s: Shur) => ({ Authorization: "Bearer " + s.token });
export const ADMIN = { "X-Admin": "admin" };

export function peticion(metodo: string, ruta: string, cuerpo?: unknown, cab: Record<string, string> = {}): Request {
  return new Request("https://x" + ruta, {
    method: metodo,
    headers: { "Content-Type": "application/json", ...cab },
    body: cuerpo === undefined ? undefined : typeof cuerpo === "string" ? cuerpo : JSON.stringify(cuerpo),
  });
}

export async function llamar(a: App, req: Request): Promise<{ status: number; cuerpo: Json; cabeceras: Headers }> {
  const r = await a.fetch(req);
  const texto = await r.text();
  return { status: r.status, cuerpo: texto ? JSON.parse(texto) : null, cabeceras: r.headers };
}
export const admin = (metodo: string, ruta: string, cuerpo?: unknown, a: App = en()) =>
  llamar(a, peticion(metodo, ruta, cuerpo, ADMIN));

export async function filas(sql: string, ...args: unknown[]): Promise<Json[]> {
  return (await env.DB.prepare(sql).bind(...args).all<Json>()).results;
}

/** Una canción de prueba `n` ("Grupo n - Tema n") con su enlace de YouTube registrado. */
export async function cancion(n: number): Promise<string> {
  const id = nuevoId(new Date(T0 + n));
  const v = "tema" + String(n).padStart(7, "0");
  await env.DB.batch([
    env.DB
      .prepare("INSERT INTO canciones (id, clave, titulo, artista, portada, isrc, enlaces, oculta) VALUES (?, ?, ?, ?, NULL, NULL, ?, 0)")
      .bind(id, `grupo ${n}|tema ${n}`, `Tema ${n}`, `Grupo ${n}`, JSON.stringify({ youtube: "https://www.youtube.com/watch?v=" + v })),
    env.DB.prepare("INSERT INTO enlaces_cancion (ref, cancion) VALUES (?, ?)").bind("youtube:" + v, id),
  ]);
  return id;
}
export const enlaceDe = (n: number) => "https://www.youtube.com/watch?v=tema" + String(n).padStart(7, "0");

/** Una publicación directa en la base (sin pasar por la app); vive 24 h desde `creada`. */
export async function publicacionDirecta(
  cuenta: string, cancionId: string, o: { creada?: number; oculta?: boolean; corazones?: number; texto?: string } = {},
): Promise<string> {
  const creada = o.creada ?? T0;
  const id = nuevoId(new Date(creada));
  await env.DB
    .prepare("INSERT INTO publicaciones (id, cuenta, cancion, texto, creada, caduca, oculta, corazones) VALUES (?, ?, ?, ?, ?, ?, ?, ?)")
    .bind(id, cuenta, cancionId, o.texto ?? "Una canción", creada, creada + DIA, o.oculta ? 1 : 0, o.corazones ?? 0)
    .run();
  return id;
}
export async function corazonDirecto(publicacion: string, cuenta: string, creado = T0): Promise<void> {
  await env.DB.prepare("INSERT INTO corazones (publicacion, cuenta, creado) VALUES (?, ?, ?)").bind(publicacion, cuenta, creado).run();
}
