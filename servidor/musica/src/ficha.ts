// Lee la ficha PÚBLICA de un usuario de FC (member.php?u=<uid>, de invitado y con UA de móvil).
// Medido el 2026-11-07: la página pesa ~130 KB y el plan gratuito de Workers da 10 ms de CPU por
// petición, así que se recorre con indexOf: ni DOM, ni regex sobre el documento entero.
// (Las regex de aquí, solo sobre trozos de unos pocos caracteres.)

export type Ficha =
  | { estado: "ok"; nombre: string; registro: string; coche: string | null }
  | { estado: "challenge" }
  | { estado: "no_existe" }
  | { estado: "error" };

const MARCA_PERFIL = "Ver Perfil:";
const MARCA_COCHE = "var(--coches)";

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

  // "Registro: <span style="font-weight: normal;">30-may-2026</span>"
  const m = html.indexOf("Registro:", titulo.fin);
  if (m < 0) return { estado: "error" };
  const abre = html.indexOf("<span", m);
  const cierraAbre = abre < 0 ? -1 : html.indexOf(">", abre);
  const cierra = cierraAbre < 0 ? -1 : html.indexOf("</span>", cierraAbre);
  if (cierra < 0) return { estado: "error" };
  const registro = html.slice(cierraAbre + 1, cierra).trim();
  if (!registro) return { estado: "error" };

  // El coche es el texto tras el icono `var(--coches)` hasta el </div>. Sin coche, el bloque
  // "Sobre NOMBRE" entero no existe.
  let coche: string | null = null;
  const c = html.indexOf(MARCA_COCHE, m);
  if (c >= 0) {
    const finIcono = html.indexOf("</span>", c);
    const finFila = finIcono < 0 ? -1 : html.indexOf("</div>", finIcono);
    if (finFila >= 0) {
      const t = decodificar(html.slice(finIcono + "</span>".length, finFila)).trim();
      coche = t === "" ? null : t;
    }
  }
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
