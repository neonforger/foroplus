// Coches "realistas" para el reto de verificación: el usuario escribe uno en el campo "coche" de su
// ficha pública y el servidor lo lee. Solo [A-Za-z0-9. ] para que FC lo guarde sin tocarlo.
// Se generan en código a partir de listas pequeñas (marca, modelo, versiones, año inicial) en vez de
// un literal de 300 líneas.

// [marca, modelo, [versiones], año de la primera versión]
const MODELOS: ReadonlyArray<readonly [string, string, readonly string[], number]> = [
  ["Seat", "Ibiza", ["1.4", "1.9 TDI", "1.6 TDI"], 2002],
  ["Seat", "Leon", ["1.6 TDI", "1.8 T", "2.0 TDI"], 2001],
  ["Seat", "Cordoba", ["1.4", "1.9 SDI", "1.6"], 1999],
  ["Seat", "Toledo", ["1.9 TDI", "1.6", "1.8 T"], 1998],
  ["Seat", "Altea", ["1.9 TDI", "1.6", "2.0 TDI"], 2005],
  ["Seat", "Arosa", ["1.0", "1.4", "1.7 SDI"], 1998],
  ["Volkswagen", "Golf", ["1.9 TDI", "1.6", "2.0 GTI"], 1999],
  ["Volkswagen", "Polo", ["1.4", "1.9 TDI", "1.2"], 2001],
  ["Volkswagen", "Passat", ["1.9 TDI", "2.0 TDI", "1.8 T"], 2000],
  ["Volkswagen", "Lupo", ["1.0", "1.4", "1.2 TDI"], 1999],
  ["Volkswagen", "Touran", ["1.9 TDI", "2.0 TDI", "1.6"], 2004],
  ["Volkswagen", "Caddy", ["1.9 TDI", "1.6 TDI", "2.0 TDI"], 2004],
  ["Audi", "A3", ["1.9 TDI", "1.8 T", "2.0 TDI"], 2000],
  ["Audi", "A4", ["1.9 TDI", "2.0 TDI", "1.8 T"], 2001],
  ["Audi", "A6", ["2.5 TDI", "3.0 TDI", "2.0 TDI"], 2000],
  ["Audi", "TT", ["1.8 T", "3.2 V6", "2.0 TFSI"], 1999],
  ["BMW", "Serie 1", ["118d", "120d", "116i"], 2005],
  ["BMW", "Serie 3", ["320d", "318i", "330d"], 2000],
  ["BMW", "Serie 5", ["520d", "530d", "525d"], 2001],
  ["BMW", "X3", ["2.0d", "3.0d", "2.5i"], 2004],
  ["Mercedes", "Clase A", ["A 180", "A 170 CDI", "A 200"], 2001],
  ["Mercedes", "Clase C", ["C 220 CDI", "C 200", "C 250 CDI"], 2001],
  ["Mercedes", "Clase E", ["E 220 CDI", "E 270 CDI", "E 320"], 2000],
  ["Renault", "Clio", ["1.5 dCi", "1.2", "1.9 D"], 1999],
  ["Renault", "Megane", ["1.9 dCi", "1.5 dCi", "1.6"], 2000],
  ["Renault", "Laguna", ["1.9 dCi", "2.2 dCi", "1.8"], 1999],
  ["Renault", "Twingo", ["1.2", "1.1", "1.5 dCi"], 1998],
  ["Renault", "Scenic", ["1.9 dCi", "1.6", "2.0"], 2000],
  ["Peugeot", "206", ["1.4 HDi", "1.6", "2.0 HDi"], 2000],
  ["Peugeot", "307", ["1.6 HDi", "2.0 HDi", "1.4"], 2002],
  ["Peugeot", "106", ["1.1", "1.4", "1.5 D"], 1997],
  ["Peugeot", "407", ["2.0 HDi", "1.6 HDi", "2.2"], 2004],
  ["Citroen", "Saxo", ["1.1", "1.4", "1.5 D"], 1997],
  ["Citroen", "C3", ["1.4 HDi", "1.6", "1.1"], 2002],
  ["Citroen", "Xsara", ["1.9 D", "2.0 HDi", "1.6"], 1998],
  ["Citroen", "C4", ["1.6 HDi", "2.0 HDi", "1.6"], 2005],
  ["Ford", "Fiesta", ["1.25", "1.4 TDCi", "1.6"], 2002],
  ["Ford", "Focus", ["1.8 TDCi", "1.6", "2.0 TDCi"], 2001],
  ["Ford", "Mondeo", ["2.0 TDCi", "1.8", "2.2 TDCi"], 2001],
  ["Ford", "Ka", ["1.3", "1.2", "1.3 Duratec"], 1998],
  ["Opel", "Corsa", ["1.2", "1.3 CDTI", "1.4"], 2001],
  ["Opel", "Astra", ["1.7 CDTI", "1.6", "2.0 Turbo"], 2000],
  ["Opel", "Vectra", ["1.9 CDTI", "2.2 DTI", "1.8"], 2000],
  ["Opel", "Zafira", ["1.9 CDTI", "1.6", "2.2 DTI"], 2001],
  ["Fiat", "Punto", ["1.2", "1.3 Multijet", "1.9 JTD"], 2000],
  ["Fiat", "Panda", ["1.2", "1.1", "1.3 Multijet"], 2003],
  ["Fiat", "Bravo", ["1.9 JTD", "1.4", "1.6 Multijet"], 2007],
  ["Fiat", "Stilo", ["1.9 JTD", "1.6", "2.4 Abarth"], 2002],
  ["Toyota", "Corolla", ["1.4 D4D", "1.6", "2.0 D4D"], 2001],
  ["Toyota", "Yaris", ["1.0", "1.4 D4D", "1.3"], 2000],
  ["Toyota", "Avensis", ["2.0 D4D", "1.8", "2.2 D4D"], 2003],
  ["Toyota", "Celica", ["1.8 VVTi", "1.8 TS", "2.0 GT"], 1999],
  ["Honda", "Civic", ["1.4", "1.6 VTEC", "2.0 Type R"], 2001],
  ["Honda", "Accord", ["2.0", "2.2 CTDi", "2.4"], 2003],
  ["Honda", "CRV", ["2.0", "2.2 CTDi", "2.4"], 2002],
  ["Nissan", "Micra", ["1.2", "1.5 dCi", "1.4"], 2003],
  ["Nissan", "Almera", ["1.5 dCi", "1.8", "2.2 dCi"], 2000],
  ["Nissan", "Primera", ["2.0", "1.9 dCi", "2.2 dCi"], 1999],
  ["Mazda", "MX5", ["1.6", "1.8", "2.0"], 1999],
  ["Mazda", "Mazda3", ["1.6", "2.0 CRTD", "2.0"], 2004],
  ["Hyundai", "Accent", ["1.3", "1.5 CRDi", "1.6"], 2000],
  ["Hyundai", "Coupe", ["2.0 FX", "1.6", "2.7 V6"], 2002],
  ["Kia", "Rio", ["1.4", "1.5 CRDi", "1.6"], 2001],
  ["Kia", "Ceed", ["1.6 CRDi", "2.0", "1.4"], 2007],
  ["Mini", "Cooper", ["1.6", "1.6 S", "1.4 D"], 2002],
  ["Skoda", "Octavia", ["1.9 TDI", "1.6", "2.0 TDI"], 2000],
  ["Skoda", "Fabia", ["1.4", "1.9 TDI", "1.2"], 2001],
  ["Volvo", "S40", ["1.6 D", "2.0", "1.8"], 2001],
  ["Volvo", "V70", ["2.4 D5", "2.5 T", "2.4"], 2000],
  ["Alfa Romeo", "147", ["1.9 JTD", "2.0 TS", "1.6 TS"], 2001],
  ["Alfa Romeo", "156", ["1.9 JTD", "2.0 TS", "2.5 V6"], 1998],
  ["Suzuki", "Swift", ["1.3", "1.5", "1.3 DDiS"], 2005],
  ["Suzuki", "Vitara", ["1.6", "2.0 TD", "1.9 DDiS"], 1998],
];

function generar(): string[] {
  const vistos = new Set<string>();
  const lista: string[] = [];
  for (const [marca, modelo, versiones, inicio] of MODELOS) {
    versiones.forEach((version, j) => {
      // Dos años por versión, escalonados para que no coincidan entre versiones del mismo modelo.
      for (const anio of [inicio + j * 2, inicio + j * 2 + 7]) {
        const coche = `${marca} ${modelo} ${version} ${anio}`;
        const clave = coche.toLowerCase();
        if (!vistos.has(clave)) {
          vistos.add(clave);
          lista.push(coche);
        }
      }
    });
  }
  return lista;
}

export const COCHES: readonly string[] = generar();

function normalizar(s: string): string {
  return s.trim().replace(/\s+/g, " ").toLowerCase();
}

/** Mismo coche sin mirar mayúsculas ni espacios de más (FC puede recolocarlos al guardar). */
export function cochesIguales(a: string, b: string): boolean {
  return normalizar(a) === normalizar(b);
}

/** Un coche al azar (`aleatorio()` en [0,1)) que nunca es `distintoDe`: el del reto anterior. */
export function cocheAleatorio(aleatorio: () => number, distintoDe: string | null): string {
  const n = COCHES.length;
  let i = Math.min(n - 1, Math.floor(aleatorio() * n));
  if (distintoDe !== null) {
    for (let k = 0; k < n && cochesIguales(COCHES[i], distintoDe); k++) i = (i + 1) % n;
  }
  return COCHES[i];
}
