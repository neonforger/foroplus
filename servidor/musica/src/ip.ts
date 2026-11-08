// La IP del cliente reducida a lo que identifica a UN equipo para los límites por IP. Con IPv6 un
// solo equipo suele tener un /64 entero y puede ir cambiando de dirección dentro de él: limitar por
// la dirección completa no limitaría nada. Desde la Resolución 16 cada `reto` lee una ficha de FC,
// y el límite por IP es (con el presupuesto global) lo que acota esas lecturas entre uids distintos.
// Puro y sin regex sobre nada largo: la cabecera es corta y la pone Cloudflare (CF-Connecting-IP).

function ipv4(texto: string): number[] | null {
  const partes = texto.split(".");
  if (partes.length !== 4) return null;
  const bytes: number[] = [];
  for (const p of partes) {
    if (!/^[0-9]{1,3}$/.test(p)) return null;
    const n = Number(p);
    if (n > 255) return null;
    bytes.push(n);
  }
  return bytes;
}

/**
 * Grupos de 16 bits de un lado de la dirección ("2001:db8" → [0x2001, 0xdb8]). Una IPv4 incrustada
 * solo vale como último trozo de la dirección entera (`ipv4AlFinal`) y cuenta como dos grupos.
 */
function grupos(lado: string, ipv4AlFinal: boolean): number[] | null {
  if (lado === "") return [];
  const trozos = lado.split(":");
  const g: number[] = [];
  for (let i = 0; i < trozos.length; i++) {
    const t = trozos[i];
    if (t.includes(".")) {
      const b = ipv4AlFinal && i === trozos.length - 1 ? ipv4(t) : null;
      if (!b) return null;
      g.push((b[0] << 8) | b[1], (b[2] << 8) | b[3]);
    } else {
      // "" = ":" suelto al principio o al final, o ":::".
      if (!/^[0-9a-f]{1,4}$/.test(t)) return null;
      g.push(parseInt(t, 16));
    }
  }
  return g;
}

/** Los 8 grupos de 16 bits de una IPv6 (admite "::", mayúsculas y una IPv4 al final), o null. */
function ipv6(texto: string): number[] | null {
  const dobles = texto.split("::");
  if (dobles.length > 2) return null;
  if (dobles.length === 1) {
    const g = grupos(texto, true);
    return g && g.length === 8 ? g : null;
  }
  const izq = grupos(dobles[0], false);
  const der = grupos(dobles[1], true);
  // "::" tiene que tapar al menos un grupo.
  if (!izq || !der || izq.length + der.length > 7) return null;
  return [...izq, ...new Array<number>(8 - izq.length - der.length).fill(0), ...der];
}

/**
 * Clave estable de la IP para los límites: una IPv4 tal cual (sin ceros delante); una IPv6, su /64
 * ("2001:0db8:0001:0002::/64"); una IPv4 mapeada en IPv6 (::ffff:a.b.c.d), como la IPv4. Sin IP o
 * con algo que no es una IP, null: quien la usa lo manda al cubo fijo "sin-ip" (detrás de Cloudflare
 * no debería pasar nunca). Lo demás lo pasa por el HMAC con "ip:" delante: nunca en claro.
 */
export function claveIp(ip: string | null | undefined): string | null {
  if (typeof ip !== "string") return null;
  let t = ip.trim().toLowerCase();
  if (!t.includes(":")) {
    const b = ipv4(t);
    return b ? b.join(".") : null;
  }
  // La zona ("fe80::1%eth0") solo existe en IPv6 y no cambia de equipo.
  const zona = t.indexOf("%");
  if (zona >= 0) t = t.slice(0, zona);
  const g = ipv6(t);
  if (!g) return null;
  if (g[0] === 0 && g[1] === 0 && g[2] === 0 && g[3] === 0 && g[4] === 0 && g[5] === 0xffff) {
    return [g[6] >> 8, g[6] & 0xff, g[7] >> 8, g[7] & 0xff].join(".");
  }
  return g.slice(0, 4).map((x) => x.toString(16).padStart(4, "0")).join(":") + "::/64";
}
