// Cripto del servidor con Web Crypto (disponible en workerd): HMAC del uid de FC, tokens y ULIDs.

const codificador = new TextEncoder();

function hex(bytes: ArrayBuffer): string {
  return Array.from(new Uint8Array(bytes), (b) => b.toString(16).padStart(2, "0")).join("");
}

/** HMAC-SHA256(pepper, uid) en hexadecimal minúscula: lo único del uid de FC que se guarda en `cuentas`. */
export async function uidHmac(pepper: string, uid: string): Promise<string> {
  const clave = await crypto.subtle.importKey(
    "raw", codificador.encode(pepper), { name: "HMAC", hash: "SHA-256" }, false, ["sign"],
  );
  return hex(await crypto.subtle.sign("HMAC", clave, codificador.encode(uid)));
}

/** 32 bytes aleatorios en base64url sin relleno (43 caracteres). */
export function nuevoToken(): string {
  const bytes = crypto.getRandomValues(new Uint8Array(32));
  let binario = "";
  for (const b of bytes) binario += String.fromCharCode(b);
  return btoa(binario).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
}

/** SHA-256 del token en hexadecimal: en la base solo va el hash, nunca el token. */
export async function hashToken(token: string): Promise<string> {
  return hex(await crypto.subtle.digest("SHA-256", codificador.encode(token)));
}

const CROCKFORD = "0123456789ABCDEFGHJKMNPQRSTVWXYZ";

/** Un id de `nuevoId` (Crockford base32 en mayúsculas). Lo que no casa ni se busca en la base. */
export const RE_ULID = /^[0-9A-HJKMNP-TV-Z]{26}$/;

/** ULID estándar: 10 caracteres de tiempo (ms, 48 bits) + 16 aleatorios (80 bits), Crockford base32. */
export function nuevoId(ahora: Date): string {
  let t = ahora.getTime();
  let tiempo = "";
  for (let i = 0; i < 10; i++) {
    tiempo = CROCKFORD[t % 32] + tiempo;
    t = Math.floor(t / 32);
  }
  const azar = crypto.getRandomValues(new Uint8Array(16));
  let resto = "";
  for (const b of azar) resto += CROCKFORD[b % 32]; // 32 divide a 256: sin sesgo
  return tiempo + resto;
}
