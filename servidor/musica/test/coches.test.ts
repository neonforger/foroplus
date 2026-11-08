import { describe, expect, it } from "vitest";
import { COCHES, cocheAleatorio, cochesIguales } from "../src/coches";

describe("COCHES", () => {
  it("hay al menos 300, sin repetidos y solo [A-Za-z0-9. ]", () => {
    expect(COCHES.length).toBeGreaterThanOrEqual(300);
    expect(new Set(COCHES).size).toBe(COCHES.length);
    for (const c of COCHES) expect(c).toMatch(/^[A-Za-z0-9. ]+$/);
  });
  it("sin repetidos tampoco ignorando mayúsculas y espacios", () => {
    expect(new Set(COCHES.map((c) => c.toLowerCase().replace(/\s+/g, " "))).size).toBe(COCHES.length);
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
  it("nunca devuelve el excluido", () => {
    expect(cocheAleatorio(() => 0, COCHES[0])).not.toBe(COCHES[0]);
    const ultimo = COCHES[COCHES.length - 1];
    expect(cocheAleatorio(() => 0.999999, ultimo)).not.toBe(ultimo);
  });
  it("excluye aunque difiera en mayúsculas o espacios", () => {
    const r = cocheAleatorio(() => 0, "  " + COCHES[0].toUpperCase() + " ");
    expect(cochesIguales(r, COCHES[0])).toBe(false);
  });
  it("sin exclusión usa el azar", () => {
    expect(cocheAleatorio(() => 0, null)).toBe(COCHES[0]);
    expect(COCHES).toContain(cocheAleatorio(() => 0.5, null));
  });
});
