// Verificación de cuenta de FC por el campo "coche" de la ficha pública: el servidor da un coche al
// azar, la app lo escribe en el perfil con la sesión del usuario (el servidor nunca la ve) y aquí
// se lee la ficha SIN sesión. Si casa, se crea o actualiza la cuenta y se da un token.
// Quien lee la ficha puede ser el Worker o el lector del VPS (`lectorEnVps`): las dos lecturas pasan
// por el mismo `leerFicha` y por las mismas reglas (`evaluar`).
import type { Contexto, Deps, OpcionesApp, Ruta } from "../app";
import { permisoVoto, yo } from "../auth";
import { cocheAleatorio, cochesIguales } from "../coches";
import { hashToken, nuevoId, nuevoToken, RE_ULID, uidHmac } from "../cripto";
import { leerFicha, type Ficha } from "../ficha";
import { cuerpoJson, error, json } from "../http";
import { claveIp } from "../ip";
import { publicacionesRestantes } from "../feed";
import { contarUna, permitido } from "../limites";
import { USER_AGENT } from "../tarjeta";

const RETO_MS = 10 * 60_000;
const INTENTOS = 3;
// Lecturas de FC por reto (contando las que dan challenge, que no gastan intento). Sin tope, un
// solo reto serviría para usar el Worker de pasarela contra member.php durante sus 10 minutos.
const LECTURAS_POR_RETO = 10;
const TOKEN_MS = 365 * 86_400_000;
const HORA_S = 3600;
const DIA_S = 86_400;
const RETOS_POR_UID_HORA = 5;
const RETOS_POR_IP_HORA = 20;
/**
 * Lecturas de fichas de FC por hora entre TODOS los usuarios (las del Worker y las que se piden al
 * VPS). Holgado para el uso real; frena que ForoPlus sirva de pasarela contra member.php aunque
 * alguien reparta las peticiones entre muchas IPs, y que FC acabe poniéndonos el challenge o
 * bloqueándonos. Pasado el tope: 503 challenge, sin crear reto ni gastar intento. Clave `fc:global`
 * en `limites`. Los tests lo bajan con `crearApp(deps, { lecturasFcHora })`.
 */
export const LECTURAS_FC_HORA = 600;
// Canónico: sin ceros delante. FC lee "0123456" como 123456 (intval), y un HMAC distinto haría una
// segunda cuenta para el mismo usuario de FC (doble voto).
const RE_UID = /^[1-9][0-9]{0,9}$/;
const MAX_CUERPO = 1024;
// La ficha pesa ~130 KB (medido 2026-11-07); en JSON, con los escapes, algo más.
const MAX_LECTURA = 1_000_000;
const FICHA_URL = "https://forocoches.com/foro/member.php?u=";
const SIN_CACHE = { "Cache-Control": "no-store" };

interface FilaReto {
  uid: string;
  uid_hmac: string;
  coche: string;
  caduca: number;
  intentos: number;
  estado: string;
  ficha: string | null;
  coche_antes: string | null;
}

/** Cuenta una lectura de FC en el presupuesto global; false = agotado esta hora. */
function hayPresupuestoFc(d: Deps, ahora: Date, tope: number): Promise<boolean> {
  return permitido(d.db, "fc:global", HORA_S, tope, ahora);
}

async function baneada(db: D1Database, hmac: string): Promise<boolean> {
  return (await db.prepare("SELECT baneada FROM cuentas WHERE uid_hmac = ?").bind(hmac).first<number>("baneada")) === 1;
}

async function reto(c: Contexto, lecturasFcHora: number): Promise<Response> {
  const d = c.deps;
  const uid = (await cuerpoJson(c.req, MAX_CUERPO))?.uid;
  if (typeof uid !== "string" || !RE_UID.test(uid)) return error("uid", 400);
  const ahora = d.ahora();
  // Primero la IP: así quien satura la suya no gasta además el cupo del uid de otro. Con IPv6 cuenta
  // el /64 entero (`claveIp`): un solo equipo puede ir cambiando de dirección dentro de él. "ip:"
  // delante, para que nunca coincida con el HMAC de un uid. Sin IP o ilegible: un cubo fijo, "sin-ip".
  const ip = claveIp(c.req.headers.get("CF-Connecting-IP"));
  const ipHmac = ip === null ? "sin-ip" : await uidHmac(d.pepper, "ip:" + ip);
  if (!(await permitido(d.db, "reto:ip:" + ipHmac, HORA_S, RETOS_POR_IP_HORA, ahora))) return error("limite", 429);
  const hmac = await uidHmac(d.pepper, uid);
  if (!(await permitido(d.db, "reto:uid:" + hmac, HORA_S, RETOS_POR_UID_HORA, ahora))) return error("limite", 429);
  // Después de los límites: preguntar en bucle "¿está baneado este uid?" también cuesta cupo.
  // Y antes de leer FC: una cuenta baneada no gasta lecturas.
  if (await baneada(d.db, hmac)) return error("baneada", 403);

  const t = ahora.getTime();
  const id = nuevoId(ahora);
  // El coche que NO se puede dar. Resolución 16: si el perfil ya muestra el coche del reto, comprobar
  // casaría sin que nadie tocara nada (y cualquiera puede pedir retos con el uid de otro).
  let distintoDe: string | null;
  let cocheAntes: string | null = null;
  if (d.lectorEnVps) {
    // El Worker no puede leer FC en este modo: queda el espacio grande de coches y no repetir el
    // del reto vivo anterior de ese uid (el que el perfil puede tener aún puesto).
    distintoDe = await d.db
      .prepare("SELECT coche FROM retos WHERE uid_hmac = ? AND caduca > ? ORDER BY caduca DESC, rowid DESC LIMIT 1")
      .bind(hmac, t)
      .first<string>("coche");
  } else {
    // Se lee la ficha ANTES de dar el reto. Los cupos ya se contaron: un 503 o un 404 también los
    // gastan, y así las lecturas de FC que hace `reto` siguen acotadas aunque FC esté con challenge.
    if (!(await hayPresupuestoFc(d, ahora, lecturasFcHora))) return error("challenge", 503);
    const ficha = await leerFichaDe(d, uid);
    if (ficha.estado === "challenge" || ficha.estado === "error") return error("challenge", 503);
    if (ficha.estado === "no_existe") return error("no_existe", 404);
    cocheAntes = ficha.coche;
    distintoDe = ficha.coche;
  }
  const coche = cocheAleatorio(d.aleatorio, distintoDe);
  const caduca = t + RETO_MS;
  await d.db.batch([
    // El uid en claro no sobrevive a su reto aunque el cron de limpieza vaya con retraso.
    d.db.prepare("DELETE FROM retos WHERE caduca <= ?").bind(t),
    d.db
      .prepare(
        "INSERT INTO retos (id, uid, uid_hmac, coche, caduca, intentos, estado, coche_antes) VALUES (?, ?, ?, ?, ?, 0, 'nuevo', ?)",
      )
      .bind(id, uid, hmac, coche, caduca, cocheAntes),
    // La lectura de ahora es la primera de las LECTURAS_POR_RETO de este reto.
    ...(d.lectorEnVps ? [] : [contarUna(d.db, "lectura:reto:" + id, DIA_S, ahora)]),
  ]);
  return json({ reto: id, coche, caduca }, 200, SIN_CACHE);
}

async function leerFichaDe(d: Deps, uid: string): Promise<Ficha> {
  try {
    // De invitado (sin cookies) y con UA de móvil: la plantilla móvil es la que lee `leerFicha`.
    const r = await d.red(FICHA_URL + uid, { headers: { "User-Agent": USER_AGENT } });
    return leerFicha(r.status, r.headers, await r.text());
  } catch {
    return { estado: "error" };
  }
}

/** La lectura que dejó el VPS. Algo que no tenga forma de Ficha cuenta como error (no gasta intento). */
function fichaGuardada(texto: string | null): Ficha {
  try {
    const f: unknown = JSON.parse(texto ?? "");
    if (f && typeof f === "object") {
      const o = f as Record<string, unknown>;
      if (o.estado === "challenge" || o.estado === "no_existe") return { estado: o.estado };
      if (
        o.estado === "ok" && typeof o.nombre === "string" && typeof o.registro === "string" &&
        (o.coche === null || typeof o.coche === "string")
      ) {
        return { estado: "ok", nombre: o.nombre, registro: o.registro, coche: o.coche };
      }
    }
  } catch {
    // cae a error
  }
  return { estado: "error" };
}

/** Gasta un intento y deja el reto listo para otra lectura. Devuelve los intentos gastados. */
async function gastarIntento(db: D1Database, id: string): Promise<number> {
  const n = await db
    .prepare("UPDATE retos SET intentos = intentos + 1, estado = 'nuevo', ficha = NULL WHERE id = ? RETURNING intentos")
    .bind(id)
    .first<number>("intentos");
  return n ?? INTENTOS;
}

async function evaluar(d: Deps, id: string, r: FilaReto, ficha: Ficha, ahora: Date): Promise<Response> {
  if (ficha.estado === "challenge" || ficha.estado === "error") {
    // FC no deja leer (o falla): no es culpa del usuario, no gasta intento. Un error de FC (500,
    // red caída) se cuenta como challenge: para la app es lo mismo, "prueba en un rato".
    if (r.estado !== "nuevo") {
      await d.db.prepare("UPDATE retos SET estado = 'nuevo', ficha = NULL WHERE id = ?").bind(id).run();
    }
    return error("challenge", 503);
  }
  if (ficha.estado === "no_existe") {
    await gastarIntento(d.db, id);
    return error("no_existe", 404);
  }
  // Defensa en profundidad (Resolución 16): si el coche del reto ya estaba en la ficha al darlo, que
  // case no prueba que nadie tocara el perfil. `reto` ya no da ese coche; si algún día pasa, no vale.
  const yaEstaba = r.coche_antes !== null && cochesIguales(r.coche_antes, r.coche);
  if (ficha.coche === null || !cochesIguales(ficha.coche, r.coche) || yaEstaba) {
    const gastados = await gastarIntento(d.db, id);
    return error("no_coincide", 409, { intentosRestantes: Math.max(0, INTENTOS - gastados) });
  }

  // Casa. La cuenta se identifica por el HMAC del uid y el nombre sale SOLO del título de la ficha.
  const t = ahora.getTime();
  const registro = ficha.registro === "" ? null : ficha.registro;
  const token = nuevoToken();
  const [cuenta] = await d.db.batch([
    // Una cuenta baneada no se toca (el WHERE del DO UPDATE): RETURNING no devuelve fila.
    d.db
      .prepare(
        "INSERT INTO cuentas (id, uid_hmac, nombre, registro_fc, baneada, creada, ultima_verif) " +
          "VALUES (?1, ?2, ?3, ?4, 0, ?5, ?5) ON CONFLICT (uid_hmac) DO UPDATE SET nombre = excluded.nombre, " +
          "registro_fc = excluded.registro_fc, ultima_verif = excluded.ultima_verif WHERE cuentas.baneada = 0 " +
          "RETURNING id",
      )
      .bind(nuevoId(ahora), r.uid_hmac, ficha.nombre, registro, t),
    // En la base solo el hash; el token sale una vez, en esta respuesta.
    d.db
      .prepare("INSERT INTO tokens (hash, cuenta, creado, caduca) SELECT ?1, id, ?2, ?3 FROM cuentas WHERE uid_hmac = ?4 AND baneada = 0")
      .bind(await hashToken(token), t, t + TOKEN_MS, r.uid_hmac),
    // Con los retos se va el último sitio donde estaba el uid en claro: TODOS los de ese uid, no solo
    // este (uno anterior tras un 429, un reintento, la app reiniciada, o uno que pidió otro con ese uid).
    d.db.prepare("DELETE FROM retos WHERE uid_hmac = ?").bind(r.uid_hmac),
  ]);
  if (cuenta.results.length === 0) return error("baneada", 403);
  const { diasCuenta, puedeVotar } = permisoVoto(registro, ahora, d.diasMinVoto);
  return json({ token, nombre: ficha.nombre, diasCuenta, puedeVotar }, 200, SIN_CACHE);
}

async function comprobar(c: Contexto, lecturasFcHora: number): Promise<Response> {
  const d = c.deps;
  const id = (await cuerpoJson(c.req, MAX_CUERPO))?.reto;
  if (typeof id !== "string" || !RE_ULID.test(id)) return error("reto", 400);
  const ahora = d.ahora();
  const r = await d.db
    .prepare("SELECT uid, uid_hmac, coche, caduca, intentos, estado, ficha, coche_antes FROM retos WHERE id = ?")
    .bind(id)
    .first<FilaReto>();
  if (!r) return error("reto", 404);
  if (!(ahora.getTime() < r.caduca)) {
    await d.db.prepare("DELETE FROM retos WHERE id = ?").bind(id).run();
    return error("caducado", 410);
  }
  if (await baneada(d.db, r.uid_hmac)) return error("baneada", 403);
  if (r.intentos >= INTENTOS) return error("intentos", 429);

  // Ya leída por el VPS: se evalúa aunque el modo haya cambiado entretanto.
  if (r.estado === "leida") return evaluar(d, id, r, fichaGuardada(r.ficha), ahora);

  if (d.lectorEnVps) {
    if (r.estado !== "pendiente") {
      if (!(await permitido(d.db, "lectura:reto:" + id, DIA_S, LECTURAS_POR_RETO, ahora))) return error("intentos", 429);
      // El VPS lee FC igual: su lectura también sale del presupuesto global.
      if (!(await hayPresupuestoFc(d, ahora, lecturasFcHora))) return error("challenge", 503);
      // Solo si nadie lo ha cambiado entretanto (una lectura que entra a la vez no se pisa).
      await d.db.prepare("UPDATE retos SET estado = 'pendiente', ficha = NULL WHERE id = ? AND estado = ?").bind(id, r.estado).run();
    }
    return json({ estado: "pendiente" }, 202, SIN_CACHE);
  }

  // Primero el tope del reto: las llamadas que rechaza no deben gastar el presupuesto de todos.
  if (!(await permitido(d.db, "lectura:reto:" + id, DIA_S, LECTURAS_POR_RETO, ahora))) return error("intentos", 429);
  if (!(await hayPresupuestoFc(d, ahora, lecturasFcHora))) return error("challenge", 503);
  return evaluar(d, id, r, await leerFichaDe(d, r.uid), ahora);
}

async function yoRuta(c: Contexto): Promise<Response> {
  const cuenta = yo(c);
  const { db, ahora: reloj, diasMinVoto } = c.deps;
  const ahora = reloj();
  // Siempre con el id de la cuenta del token: no hay otra forma de pedir este número. Lee el mismo
  // contador que cuenta al publicar (con las borradas incluidas).
  const { diasCuenta, puedeVotar } = permisoVoto(cuenta.registro, ahora, diasMinVoto);
  return json(
    {
      nombre: cuenta.nombre,
      diasCuenta,
      puedePublicar: puedeVotar,
      publicacionesRestantes: await publicacionesRestantes(db, cuenta.id, ahora),
    },
    200,
    SIN_CACHE,
  );
}

/** Lo único que ve el VPS de un reto: su id y el uid que tiene que leer (≤ 20 por pasada). */
async function pendientes(c: Contexto): Promise<Response> {
  const { db, ahora } = c.deps;
  const { results } = await db
    .prepare("SELECT id, uid FROM retos WHERE estado = 'pendiente' AND caduca > ? ORDER BY caduca LIMIT 20")
    .bind(ahora().getTime())
    .all<{ id: string; uid: string }>();
  return json(results.map((f) => ({ reto: f.id, uid: f.uid })), 200, SIN_CACHE);
}

/**
 * El VPS devuelve la respuesta de FC tal cual (status, cabecera cf-mitigated o null, HTML) y aquí se
 * lee con el MISMO `leerFicha` que usa el Worker. Se guarda solo la ficha leída (nombre, registro,
 * coche), no el HTML.
 */
async function resultado(c: Contexto): Promise<Response> {
  const { db, ahora } = c.deps;
  const b = await cuerpoJson(c.req, MAX_LECTURA);
  if (!b) return error("cuerpo", 400);
  const { reto: id, status, cfMitigated, html } = b;
  if (
    typeof id !== "string" || !RE_ULID.test(id) ||
    typeof status !== "number" || !Number.isInteger(status) || status < 100 || status > 599 ||
    typeof html !== "string" ||
    !(cfMitigated === null || cfMitigated === undefined || typeof cfMitigated === "string")
  ) {
    return error("cuerpo", 400);
  }
  let cabeceras: Headers;
  try {
    cabeceras = new Headers(cfMitigated ? { "cf-mitigated": cfMitigated } : {});
  } catch {
    return error("cuerpo", 400);
  }
  const ficha = leerFicha(status, cabeceras, html);
  const r = await db
    .prepare("UPDATE retos SET estado = 'leida', ficha = ? WHERE id = ? AND estado = 'pendiente' AND caduca > ?")
    .bind(JSON.stringify(ficha), id, ahora().getTime())
    .run();
  if (r.meta.changes === 0) return error("reto", 404);
  return json({ ok: true });
}

export function rutasVerificacion(o: OpcionesApp = {}): Ruta[] {
  const lecturasFcHora = o.lecturasFcHora ?? LECTURAS_FC_HORA;
  return [
    ["POST", "/v1/verificacion/reto", (c) => reto(c, lecturasFcHora), "publica"],
    ["POST", "/v1/verificacion/comprobar", (c) => comprobar(c, lecturasFcHora), "publica"],
    ["GET", "/v1/yo", yoRuta, "cuenta"],
    ["GET", "/v1/lector/pendientes", pendientes, "lector"],
    ["POST", "/v1/lector/resultado", resultado, "lector"],
  ];
}
