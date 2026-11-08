// Reconocimiento y normalización de canciones. Espejo de EnlaceMusica.kt / YoutubeId.kt /
// TituloCancion.kt de la app: las listas de etiquetas y los casos se comparten por
// app/src/test/resources/contrato/musica-*.json, y los tests de aquí los leen.

export type Fuente = "youtube" | "spotify";
export interface RefCancion {
  fuente: Fuente;
  id: string;
}

const YT = /(?:youtube[.]com\/(?:watch\?(?:[^ ]*&)?v=|shorts\/|embed\/)|youtu[.]be\/)([A-Za-z0-9_-]{6,})/i;
// Un id de pista de Spotify son exactamente 22 caracteres base62; el (?![A-Za-z0-9]) evita
// aceptar el prefijo de uno más largo. Se admite el segmento de idioma (/intl-es/).
const SPOTIFY =
  /(?:open[.]spotify[.]com\/(?:intl-[A-Za-z-]+\/)?track\/|^spotify:track:)([A-Za-z0-9]{22})(?![A-Za-z0-9])/i;

/** Sin enlaces cortos (spotify.link): resolverlos exige red y no es cosa de esta función. */
export function refDe(url: string): RefCancion | null {
  const u = url.trim();
  const y = YT.exec(u);
  if (y) return { fuente: "youtube", id: y[1] };
  const s = SPOTIFY.exec(u);
  if (s) return { fuente: "spotify", id: s[1] };
  return null;
}

export const ETIQUETAS_VIDEO: readonly string[] = [
  "official video", "official music video", "official audio", "official lyric video",
  "lyric video", "lyrics", "letra", "con letra", "audio", "visualizer", "videoclip oficial",
  "video oficial", "vídeo oficial", "hd", "hq", "4k", "4k remaster",
];

// "{año}" son 4 dígitos. Solo las usa la clave; en pantalla se ve el título tal cual.
export const ETIQUETAS_VERSION: readonly string[] = [
  "remastered", "remaster", "remasterizado", "remasterizada",
  "remastered {año}", "{año} remaster", "{año} remastered",
];

const GRUPO = /\(([^()]*)\)|\[([^\[\]]*)\]/g;
const SEPARADORES = [" - ", " – ", " — "];

const colapsar = (s: string) => s.replace(/\s+/g, " ").trim();
/** Minúsculas, sin tildes, espacios colapsados: la forma en que se comparan etiquetas y claves. */
const norm = (s: string) => colapsar(s.normalize("NFD").replace(/\p{M}+/gu, "").toLowerCase());
const escapar = (s: string) => s.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");

const VIDEO_NORM = new Set(ETIQUETAS_VIDEO.map(norm));
const VERSION_RE = ETIQUETAS_VERSION.map(
  (t) => new RegExp("^" + escapar(norm(t)).replace("\\{ano\\}", "\\d{4}") + "$"),
);

const esEtiquetaVideo = (c: string) => VIDEO_NORM.has(norm(c));
const esEtiquetaVersion = (c: string) => {
  const n = norm(c);
  return VERSION_RE.some((r) => r.test(n));
};
const esFeat = (c: string) => /^(?:feat|ft)\.?\s/.test(norm(c));

function quitarGrupos(t: string, quitar: (contenido: string) => boolean): string {
  return t.replace(GRUPO, (m, a?: string, b?: string) => (quitar(a ?? b ?? "") ? "" : m));
}

/** Quita los (…) y […] cuyo contenido ENTERO es una etiqueta de vídeo; el resto (p. ej. "(Directo)") se queda. */
export function limpiarTitulo(t: string): string {
  return colapsar(quitarGrupos(t, esEtiquetaVideo));
}

export function limpiarCanal(c: string): string {
  let r = colapsar(c);
  const bajo = r.toLowerCase();
  for (const s of ["vevo", " - topic", " oficial", " official"]) {
    if (bajo.endsWith(s) && r.length > s.length) {
      r = r.slice(0, r.length - s.length);
      break;
    }
  }
  return r.trim();
}

export function separar(titulo: string, canal: string | null): { cancion: string; artista: string | null } {
  const t = limpiarTitulo(titulo);
  let pos = -1;
  let sep = "";
  for (const s of SEPARADORES) {
    const i = t.indexOf(s);
    if (i >= 0 && (pos < 0 || i < pos)) {
      pos = i;
      sep = s;
    }
  }
  if (pos >= 0) {
    const artista = colapsar(t.slice(0, pos));
    const cancion = colapsar(t.slice(pos + sep.length));
    if (artista && cancion) return { cancion, artista };
  }
  const a = canal ? limpiarCanal(canal) : "";
  return { cancion: t, artista: a || null };
}

/** " feat. X" / " ft. X" al final (ya normalizado, así que sin mayúsculas ni tildes). */
const FEAT_FINAL = /\s(?:feat|ft)\.?\s.*$/;

function sinFeat(s: string): string {
  return norm(quitarGrupos(s, esFeat)).replace(FEAT_FINAL, "");
}

/**
 * Spotify pone la versión como sufijo con guion ("Song - Remastered 2011"). Solo se quita si lo que
 * hay tras el ÚLTIMO " - " / " – " / " — " es exactamente una etiqueta de versión; "- En Directo" o
 * "- Live" son otra grabación y se quedan.
 */
function sinVersionFinal(t: string): string {
  let pos = -1;
  let largo = 0;
  for (const s of SEPARADORES) {
    const i = t.lastIndexOf(s);
    if (i > pos) {
      pos = i;
      largo = s.length;
    }
  }
  if (pos > 0 && esEtiquetaVersion(t.slice(pos + largo))) return t.slice(0, pos);
  return t;
}

/**
 * La clave con la que votan las canciones: `artistas|título`. Un vídeo de YouTube y una pista de
 * Spotify de la misma canción tienen que dar la misma. No se usa el ISRC: hay uno por edición.
 * Un artista vacío (no se pudo leer) da una clave `|título`, que no casará con la que sí lo tenga.
 */
export function claveCancion(artista: string, titulo: string): string {
  const t = sinFeat(sinVersionFinal(quitarGrupos(quitarGrupos(titulo, esEtiquetaVideo), esEtiquetaVersion)));
  const artistas = sinFeat(artista)
    .split(/[,&]/)
    .map((x) => x.trim())
    .filter((x) => x)
    .sort();
  return artistas.join(",") + "|" + colapsar(t);
}
