import { describe, expect, it } from "vitest";
import { cocheAleatorio, cocheEn, cochesIguales, totalCoches } from "../src/coches";

const normal = (c: string) => c.trim().replace(/\s+/g, " ").toLowerCase();

describe("espacio de coches", () => {
  it("al menos 50.000: adivinar el coche de un reto a ciegas no es viable", () => {
    expect(totalCoches()).toBeGreaterThanOrEqual(50_000);
  });

  it("el espacio ENTERO: sin repetidos (ni ignorando mayúsculas/espacios), solo [A-Za-z0-9. ], ≤ 50 y con año al final", () => {
    // En los tests se recorre todo (en producción solo se pide uno por índice).
    const n = totalCoches();
    const vistos = new Set<string>();
    for (let i = 0; i < n; i++) {
      const c = cocheEn(i);
      vistos.add(normal(c));
      if (!/^[A-Za-z][A-Za-z0-9. ]* (19|20)\d\d$/.test(c) || c.length > 50 || c !== c.trim() || c.includes("  ")) {
        throw new Error(`coche mal formado en ${i}: "${c}"`);
      }
    }
    expect(vistos.size).toBe(n);
  });

  it("parece un coche: marca, modelo, versión y año", () => {
    expect(cocheEn(0)).toMatch(/^\S+ \S+ .+ \S+ \d{4}$/);
    const anio = Number(cocheEn(totalCoches() - 1).slice(-4));
    expect(anio).toBeGreaterThanOrEqual(1990);
    expect(anio).toBeLessThanOrEqual(2018);
  });

  it("fuera de rango lanza", () => {
    for (const i of [-1, totalCoches(), 1.5, Number.NaN]) expect(() => cocheEn(i), String(i)).toThrow(RangeError);
  });
});

describe("cochesIguales", () => {
  it("tolera mayúsculas y espacios", () => {
    expect(cochesIguales(" seat  ibiza 1.9 TDI 2004 ", "Seat Ibiza 1.9 TDI 2004")).toBe(true);
  });
  it("otro año no es igual", () => {
    expect(cochesIguales("Seat Ibiza 1.9 TDI 2005", "Seat Ibiza 1.9 TDI 2004")).toBe(false);
  });
});

describe("cocheAleatorio", () => {
  const n = () => totalCoches();
  /** aleatorio() que cae justo en el índice i. */
  const en = (i: number) => () => (i + 0.5) / n();

  it("nunca devuelve el excluido (primero, último y una muestra grande)", () => {
    expect(cocheAleatorio(() => 0, cocheEn(0))).not.toBe(cocheEn(0));
    expect(cocheAleatorio(() => 0.9999999999, cocheEn(n() - 1))).not.toBe(cocheEn(n() - 1));
    for (let i = 0; i < n(); i += 97) {
      const r = cocheAleatorio(en(i), cocheEn(i));
      if (cochesIguales(r, cocheEn(i))) throw new Error(`devolvió el excluido en ${i}`);
    }
  });
  it("excluye aunque difiera en mayúsculas o espacios", () => {
    const r = cocheAleatorio(() => 0, "  " + cocheEn(0).toUpperCase().replace(/ /g, "  ") + " ");
    expect(cochesIguales(r, cocheEn(0))).toBe(false);
  });
  it("sin exclusión usa el azar por índice", () => {
    expect(cocheAleatorio(() => 0, null)).toBe(cocheEn(0));
    expect(cocheAleatorio(() => 0.5, null)).toBe(cocheEn(Math.floor(0.5 * n())));
    expect(cocheAleatorio(() => 0.9999999999, null)).toBe(cocheEn(n() - 1));
    expect(cocheAleatorio(en(12_345), "un coche que no está en la lista")).toBe(cocheEn(12_345));
  });
});
