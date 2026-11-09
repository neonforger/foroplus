import type { Deps } from "./app";
import { claveCancion, limpiarTitulo, separar, type RefCancion } from "./normalizar";

export interface Tarjeta {
  titulo: string;
  artista: string | null;
  portada: string | null;
  clave: string;
  enlace: string;
  ref: RefCancion;
}

const HOSTS_PORTADA = new Set(["i.ytimg.com", "i.scdn.co", "image-cdn-fa.spotifycdn.com"]);

function portadaPermitida(u: unknown): string | null {
  if (typeof u !== "string") return null;
  try {
    const url = new URL(u);
    return url.protocol === "https:" && HOSTS_PORTADA.has(url.hostname) ? u : null;
  } catch {
    return null;
  }
}

/** El mismo UA móvil que BuscadorMusica.kt: Spotify puede servir una página vacía o un 403 a UAs de bot. */
export const USER_AGENT =
  "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36";
// Accept-Language en inglés fija el orden de los campos de og:description ("Artista · Álbum · Song · Año").
export const CABECERAS: RequestInit = { headers: { "User-Agent": USER_AGENT, "Accept-Language": "en" } };

export function enlaceDe(ref: RefCancion): string {
  return ref.fuente === "youtube"
    ? `https://www.youtube.com/watch?v=${ref.id}`
    : `https://open.spotify.com/track/${ref.id}`;
}

async function jsonDe(red: Deps["red"], url: string): Promise<Record<string, unknown> | null> {
  try {
    const r = await red(url, CABECERAS);
    if (!r.ok) return null;
    const j: unknown = await r.json();
    return j && typeof j === "object" ? (j as Record<string, unknown>) : null;
  } catch {
    return null;
  }
}

const ENTIDADES: Record<string, string> = { amp: "&", quot: '"', apos: "'", lt: "<", gt: ">" };
function desescapar(s: string): string {
  if (!s.includes("&")) return s;
  return s.replace(/&(?:#x([0-9a-f]+)|#(\d+)|(amp|quot|apos|lt|gt));/gi, (m, h?: string, d?: string, n?: string) => {
    if (n) return ENTIDADES[n.toLowerCase()];
    const cp = h ? parseInt(h, 16) : parseInt(d ?? "", 10);
    return Number.isFinite(cp) && cp > 0 && cp <= 0x10ffff ? String.fromCodePoint(cp) : m;
  });
}

const RE_CLAVE = /(?:property|name)\s*=\s*(?:"([^"]*)"|'([^']*)')/i;
const RE_CONTENIDO = /content\s*=\s*(?:"([^"]*)"|'([^']*)')/i;

/**
 * Las `<meta>` de la cabecera de la página de una pista (unos 300 KB): se recorren con indexOf y
 * solo cada etiqueta suelta pasa por una regex. Con 10 ms de CPU por petición no cabe un parser
 * de DOM ni una regex sobre el documento entero.
 */
export function leerMetas(html: string): Map<string, string> {
  const metas = new Map<string, string>();
  const fin = html.indexOf("</head>");
  const limite = fin >= 0 ? fin : html.length;
  let pos = html.indexOf("<meta");
  while (pos >= 0 && pos < limite) {
    const cierre = html.indexOf(">", pos);
    if (cierre < 0) break;
    const tag = html.slice(pos, cierre);
    const k = RE_CLAVE.exec(tag);
    const c = RE_CONTENIDO.exec(tag);
    if (k && c) {
      const clave = (k[1] ?? k[2]).toLowerCase();
      if (!metas.has(clave)) metas.set(clave, desescapar(c[1] ?? c[2] ?? ""));
    }
    pos = html.indexOf("<meta", cierre);
  }
  return metas;
}

/** Tope de lo que se lee de la página de una pista: `</head>` está hacia el byte 27.400 de ~310 KB. */
const TOPE_CABECERA = 64 * 1024;

/**
 * Lee el cuerpo en streaming hasta `</head>` o TOPE_CABECERA bytes y cancela el resto. Medido el
 * 2026-11-09 en producción: proponer un enlace nuevo de Spotify gastaba 9 ms de CPU de los 10 del
 * plan gratuito, sobre todo por decodificar los ~310 KB de la página con `r.text()` cuando solo
 * hacen falta los primeros ~27 KB. `stream: true` evita partir un carácter UTF-8 entre trozos, y
 * `</head>` se busca en el texto acumulado (no por trozo) por si llega partido.
 */
export async function metasDe(red: Deps["red"], url: string): Promise<Map<string, string> | null> {
  try {
    const r = await red(url, CABECERAS);
    if (!r.ok || !r.body) return null;
    const lector = r.body.getReader();
    const decodificador = new TextDecoder();
    let html = "";
    let bytes = 0;
    let buscadoDesde = 0;
    try {
      for (;;) {
        const { done, value } = await lector.read();
        if (done) {
          html += decodificador.decode();
          break;
        }
        bytes += value.byteLength;
        html += decodificador.decode(value, { stream: true });
        // Se retrocede 6 caracteres: la marca (7) podría haber quedado partida entre dos trozos.
        const i = html.indexOf("</head>", buscadoDesde);
        if (i >= 0 || bytes >= TOPE_CABECERA) break;
        buscadoDesde = Math.max(0, html.length - 6);
      }
    } finally {
      await lector.cancel().catch(() => {});
    }
    return leerMetas(html);
  } catch {
    return null;
  }
}

interface ResultadoDeezer {
  title?: unknown;
  title_short?: unknown;
  artist?: { name?: unknown };
}

/**
 * Deezer: búsqueda SIMPLE (la avanzada `artist:"…"` devuelve 0, medido el 2026-11-07). Se queda
 * con el primer resultado cuya clave casa con la nuestra; si no hay, se usan nuestros nombres.
 */
async function deLaDeezer(
  red: Deps["red"],
  artista: string,
  titulo: string,
): Promise<{ artista: string; titulo: string } | null> {
  const mia = claveCancion(artista, titulo);
  const j = await jsonDe(red, `https://api.deezer.com/search?q=${encodeURIComponent(`${artista} ${titulo}`)}&limit=5`);
  const datos = j?.data;
  if (!Array.isArray(datos)) return null;
  for (const d of datos as ResultadoDeezer[]) {
    const nombre = d?.artist?.name;
    if (typeof nombre !== "string") continue;
    // Se prueba el título corto y el largo: la clave quita "(feat. …)" y versiones de los dos.
    for (const t of [d.title_short, d.title]) {
      if (typeof t === "string" && claveCancion(nombre, t) === mia) {
        return { artista: nombre, titulo: typeof d.title_short === "string" ? d.title_short : t };
      }
    }
  }
  return null;
}

/**
 * Monta la tarjeta de una canción con ≤ 3 subpeticiones externas (plan gratuito: 50 por petición).
 * YouTube: oEmbed + Deezer. Spotify: oEmbed + página + Deezer. `null` si el oEmbed falla.
 * Si solo falla la página de Spotify, el artista queda en null, la clave usa artista "" y no se
 * busca en Deezer (sin artista no habría con qué casar).
 */
export async function tarjetaDe(ref: RefCancion, red: Deps["red"]): Promise<Tarjeta | null> {
  const enlace = enlaceDe(ref);
  let titulo: string;
  let artista: string | null;
  let portada: string | null;

  if (ref.fuente === "youtube") {
    const o = await jsonDe(red, `https://www.youtube.com/oembed?url=${encodeURIComponent(enlace)}&format=json`);
    if (!o || typeof o.title !== "string") return null;
    const s = separar(o.title, typeof o.author_name === "string" ? o.author_name : null);
    titulo = s.cancion;
    artista = s.artista;
    portada = portadaPermitida(o.thumbnail_url);
  } else {
    const o = await jsonDe(red, `https://open.spotify.com/oembed?url=${encodeURIComponent(enlace)}`);
    if (!o || typeof o.title !== "string") return null;
    titulo = limpiarTitulo(o.title);
    const m = await metasDe(red, enlace);
    const desc = m?.get("og:description")?.split(" · ")[0]?.trim();
    artista = m?.get("music:musician_description")?.trim() || desc || null;
    portada = portadaPermitida(m?.get("og:image")) ?? portadaPermitida(o.thumbnail_url);
  }
  if (!titulo) return null;

  if (artista) {
    const d = await deLaDeezer(red, artista, titulo);
    if (d) {
      artista = d.artista;
      titulo = d.titulo;
    }
  }
  return { titulo, artista, portada, clave: claveCancion(artista ?? "", titulo), enlace, ref };
}
