// Quién llama y qué puede hacer. La autorización vive aquí y en el router, no en cada manejador:
// una ruta declara su acceso y el router la deja pasar solo si se cumple (denegar por defecto).
// Las rutas de cuenta reciben la cuenta SACADA DEL TOKEN; ningún id que mande el cliente decide
// de quién son los datos (notas de seguridad del Bloque C, MEMORY.md: el IDOR por construcción).
import type { Acceso, Deps } from "./app";
import { hashToken } from "./cripto";
import { diasDesde } from "./ficha";
import { error } from "./http";

export interface Cuenta {
  id: string;
  nombre: string;
  /** Fecha de registro en FC tal como sale en la ficha ("30-may-2026"); null si no se pudo leer. */
  registro: string | null;
}

// nuevoToken(): 32 bytes en base64url sin relleno = 43 caracteres. Lo demás ni se busca en la base.
const RE_BEARER = /^Bearer ([A-Za-z0-9_-]{43})$/i;

/** La cuenta del `Authorization: Bearer`, o la respuesta de error: 401 token / 403 baneada. */
export async function cuentaDe(req: Request, d: Deps, permitirBaneada = false): Promise<Cuenta | Response> {
  const m = RE_BEARER.exec(req.headers.get("Authorization") ?? "");
  if (!m) return error("token", 401);
  const fila = await d.db
    .prepare(
      "SELECT c.id, c.nombre, c.registro_fc, c.baneada, t.caduca FROM tokens t " +
        "JOIN cuentas c ON c.id = t.cuenta WHERE t.hash = ?",
    )
    .bind(await hashToken(m[1]))
    .first<{ id: string; nombre: string; registro_fc: string | null; baneada: number; caduca: number }>();
  if (!fila || !(d.ahora().getTime() < fila.caduca)) return error("token", 401);
  // Banear no borra los tokens: los deja sin valor mientras dure el baneo.
  if (fila.baneada && !permitirBaneada) return error("baneada", 403);
  return { id: fila.id, nombre: fila.nombre, registro: fila.registro_fc };
}

async function sha256(texto: string): Promise<ArrayBuffer> {
  return crypto.subtle.digest("SHA-256", new TextEncoder().encode(texto));
}

/**
 * Compara un secreto en tiempo constante. Se comparan los SHA-256 (misma longitud siempre) para no
 * filtrar la longitud del secreto. Sin secreto configurado no entra nadie, ni con la cadena vacía.
 */
export async function secretoValido(dado: string | null, esperado: string): Promise<boolean> {
  if (!esperado || dado === null) return false;
  const [a, b] = await Promise.all([sha256(dado), sha256(esperado)]);
  return crypto.subtle.timingSafeEqual(a, b);
}

/**
 * Lo que el router comprueba antes de llamar al manejador: null = pasa sin cuenta, Cuenta = pasa
 * con esa cuenta, Response = denegado. Cualquier acceso que no esté aquí se deniega.
 */
export async function acceder(acceso: Acceso, req: Request, d: Deps): Promise<Cuenta | null | Response> {
  switch (acceso) {
    case "publica":
      return null;
    case "cuenta":
      return cuentaDe(req, d);
    case "cuenta_o_baneada":
      return cuentaDe(req, d, true);
    case "lector":
      return (await secretoValido(req.headers.get("X-Lector"), d.lectorSecreto)) ? null : error("lector", 401);
    case "admin":
      // Nunca acepta un token de cuenta: solo la cabecera X-Admin. Secreto vacío = nadie entra.
      return (await secretoValido(req.headers.get("X-Admin"), d.adminSecreto)) ? null : error("admin", 401);
    default:
      return error("acceso", 401);
  }
}

/** La cuenta de una ruta "cuenta". Si falta, el router no comprobó el acceso: se corta en seco. */
export function yo(c: { cuenta: Cuenta | null }): Cuenta {
  if (!c.cuenta) throw new Error("ruta de cuenta sin cuenta");
  return c.cuenta;
}

/** Días de cuenta en FC y si llega al mínimo para votar y proponer. Registro ilegible: no vota. */
export function permisoVoto(
  registro: string | null, ahora: Date, diasMinVoto: number,
): { diasCuenta: number | null; puedeVotar: boolean } {
  const diasCuenta = registro ? diasDesde(registro, ahora) : null;
  return { diasCuenta, puedeVotar: diasCuenta !== null && diasCuenta >= diasMinVoto };
}
