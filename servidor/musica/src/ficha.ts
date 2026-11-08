// Lee la ficha PÚBLICA de un usuario de FC (member.php?u=<uid>, de invitado y con UA de móvil).
// Medido el 2026-11-07: la página pesa ~130 KB y el plan gratuito de Workers da 10 ms de CPU por
// petición, así que se recorre con indexOf: ni DOM, ni regex sobre el documento entero.
// (Las regex de aquí, solo sobre trozos de unos pocos caracteres.)

export type Ficha =
  // `registro` vacío = no se pudo leer: no impide verificar, pero `diasDesde("")` da null (no vota ni propone).
  | { estado: "ok"; nombre: string; registro: string; coche: string | null }
  | { estado: "challenge" }
  | { estado: "no_existe" }
  | { estado: "error" };

const MARCA_PERFIL = "Ver Perfil:";
const MARCA_REGISTRO = 'Registro: <span style="font-weight: normal;">';
const MARCA_BLOQUE = "<!-- about user block -->";
// Etiqueta de apertura literal del icono del coche. El texto de usuario no puede llevar un `<` crudo
// (FC lo escapa), así que esta cadena, a diferencia de "var(--coches)" suelto, no se puede falsificar.
const ICONO_COCHE = '<span style="background-image: var(--coches)';
const CIERRE_ICONO = 'class="user_info_icon"></span>';

const ENTIDADES: Record<string, string> = {
  amp: "&", lt: "<", gt: ">", quot: '"', apos: "'", nbsp: " ",
};

/** Decodifica las entidades HTML habituales (nombradas y numéricas) de un texto corto. */
function decodificar(texto: string): string {
  if (texto.indexOf("&") < 0) return texto;
  return texto.replace(/&(#x[0-9a-fA-F]+|#[0-9]+|[a-zA-Z]+);/g, (entera, e: string) => {
    if (e[0] === "#") {
      const codigo = e[1] === "x" || e[1] === "X" ? parseInt(e.slice(2), 16) : parseInt(e.slice(1), 10);
      return codigo > 0 && codigo <= 0x10ffff ? String.fromCodePoint(codigo) : entera;
    }
    return ENTIDADES[e] ?? entera;
  });
}

/** Texto entre `abre` y `cierra` buscando desde `desde`; null si falta alguno. */
function entre(html: string, abre: string, cierra: string, desde = 0): { texto: string; fin: number } | null {
  const a = html.indexOf(abre, desde);
  if (a < 0) return null;
  const ini = a + abre.length;
  const f = html.indexOf(cierra, ini);
  if (f < 0) return null;
  return { texto: html.slice(ini, f), fin: f };
}

function esTituloDeChallenge(titulo: string): boolean {
  const t = titulo.trim().toLowerCase();
  return t.startsWith("just a moment") || t.startsWith("un momento");
}

/**
 * Coche del bloque "Sobre NOMBRE". Acota el escaneo al propio bloque (contando <div>/</div> desde
 * el primer <div tras el comentario) para que ningún texto de otro campo se lea como coche.
 */
function leerCoche(html: string, desde: number): string | null {
  const m = html.indexOf(MARCA_BLOQUE, desde);
  if (m < 0) return null;
  const ini = html.indexOf("<div", m);
  if (ini < 0) return null;
  let profundidad = 0;
  let pos = ini;
  let fin = -1;
  while (pos < html.length) {
    const abre = html.indexOf("<div", pos);
    const cierra = html.indexOf("</div>", pos);
    if (cierra < 0) break;
    if (abre >= 0 && abre < cierra) {
      profundidad++;
      pos = abre + 4;
    } else {
      profundidad--;
      pos = cierra + 6;
      if (profundidad === 0) { fin = pos; break; }
    }
  }
  if (fin < 0) return null;
  const bloque = html.slice(ini, fin);
  const i = bloque.indexOf(ICONO_COCHE);
  if (i < 0) return null;
  const c = bloque.indexOf(CIERRE_ICONO, i);
  if (c < 0) return null;
  const finFila = bloque.indexOf("</div>", c);
  if (finFila < 0) return null;
  const t = decodificar(bloque.slice(c + CIERRE_ICONO.length, finFila)).trim();
  return t === "" ? null : t;
}

export function leerFicha(status: number, cabeceras: Headers, html: string): Ficha {
  // El challenge de Cloudflare llega con 403 y `cf-mitigated: challenge` (gotcha 66)...
  if (cabeceras.get("cf-mitigated")?.toLowerCase() === "challenge") return { estado: "challenge" };
  const titulo = entre(html, "<title>", "</title>");
  // ...pero a veces se cuela con 200: se reconoce por el título ("Just a moment..." / "Un momento…").
  if (titulo && esTituloDeChallenge(titulo.texto)) return { estado: "challenge" };
  if (status !== 200 || !titulo) return { estado: "error" };

  // Un usuario inexistente da 200 y el título pelado "Forocoches", sin "Ver Perfil:".
  const p = titulo.texto.indexOf(MARCA_PERFIL);
  if (p < 0) return { estado: "no_existe" };
  const nombre = decodificar(titulo.texto.slice(p + MARCA_PERFIL.length).trim());
  if (!nombre) return { estado: "error" };

  // "Registro: <span style="font-weight: normal;">30-may-2026</span>". Se ancla en la adyacencia
  // exacta medida: un "Registro:" en texto libre (título, firma...) no lleva ese <span> pegado.
  // Si no se puede leer, la ficha vale igual (registro ""): solo `diasDesde` dirá null.
  let registro = "";
  const r = html.indexOf(MARCA_REGISTRO, titulo.fin);
  if (r >= 0) {
    const ini = r + MARCA_REGISTRO.length;
    const fin = html.indexOf("</span>", ini);
    if (fin >= 0) registro = html.slice(ini, fin).trim();
  }

  const coche = leerCoche(html, titulo.fin);
  return { estado: "ok", nombre, registro, coche };
}

const MESES = ["ene", "feb", "mar", "abr", "may", "jun", "jul", "ago", "sep", "oct", "nov", "dic"];
const DIA_MS = 86_400_000;

/** Días enteros desde el registro hasta `hoy` (fechas UTC). "Hoy" = 0, "Ayer" = 1, ilegible = null. */
export function diasDesde(registro: string, hoy: Date): number | null {
  const r = registro.trim().toLowerCase();
  if (r === "hoy") return 0;
  if (r === "ayer") return 1;
  const partes = r.split("-");
  if (partes.length !== 3) return null;
  const dia = Number(partes[0]);
  const mes = MESES.indexOf(partes[1]);
  const anio = Number(partes[2]);
  if (!/^\d{1,2}$/.test(partes[0]) || !/^\d{4}$/.test(partes[2]) || mes < 0) return null;
  const f = new Date(Date.UTC(anio, mes, dia));
  if (f.getUTCMonth() !== mes || f.getUTCDate() !== dia) return null; // 31-feb y similares
  const hoy0 = Date.UTC(hoy.getUTCFullYear(), hoy.getUTCMonth(), hoy.getUTCDate());
  return Math.round((hoy0 - f.getTime()) / DIA_MS);
}
