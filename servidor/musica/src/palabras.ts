// Nombres de playlist que no se publican. Lista corta y a propósito obvia: insultos y ofensas
// gruesas en español. No pretende ser un filtro completo (nadie lo es); la denuncia y el ocultar
// cubren el resto. Se compara por PALABRA ENTERA, sin mayúsculas ni tildes: "computadora" lleva
// "puta" dentro y es una palabra inocente.
const PROHIBIDAS = new Set([
  "puta", "putas", "puto", "putos", "hijoputa", "cabron", "cabrones", "gilipollas", "imbecil", "imbeciles",
  "subnormal", "subnormales", "mongolo", "mongola", "maricon", "maricones", "negrata", "zorra", "zorras", "nazi", "nazis",
]);

export function nombreAceptable(n: string): boolean {
  const palabras = n
    .normalize("NFD")
    .replace(/[̀-ͯ]/g, "")
    .toLowerCase()
    .split(/[^a-z0-9ñ]+/)
    .filter((p) => p);
  return !palabras.some((p) => PROHIBIDAS.has(p));
}
