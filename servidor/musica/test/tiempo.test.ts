import { describe, expect, it } from "vitest";
import { finDeSemana, horaMadrid, offsetMadrid, semanaAnterior, semanaDe } from "../src/tiempo";

const d = (s: string) => new Date(s);

describe("offsetMadrid", () => {
  it("cambia a las 01:00 UTC del último domingo de octubre", () => {
    expect(offsetMadrid(d("2026-10-25T00:59:59Z"))).toBe(120);
    expect(offsetMadrid(d("2026-10-25T01:00:00Z"))).toBe(60);
  });
  it("cambia a las 01:00 UTC del último domingo de marzo", () => {
    expect(offsetMadrid(d("2027-03-28T00:59:59Z"))).toBe(60);
    expect(offsetMadrid(d("2027-03-28T01:00:00Z"))).toBe(120);
  });
});

describe("horaMadrid", () => {
  it("devuelve un Date cuyos campos UTC son la hora local", () => {
    const h = horaMadrid(d("2026-07-01T10:30:00Z"));
    expect(h.getUTCHours()).toBe(12);
    expect(h.getUTCMinutes()).toBe(30);
  });
});

describe("semanaDe", () => {
  it("invierno: el domingo a las 23:59 de Madrid aún es la W45", () => {
    expect(semanaDe(d("2026-11-08T22:59:59Z"))).toBe("2026-W45");
    expect(semanaDe(d("2026-11-08T23:00:00Z"))).toBe("2026-W46");
  });
  it("verano", () => {
    expect(semanaDe(d("2026-07-05T21:59:59Z"))).toBe("2026-W27");
    expect(semanaDe(d("2026-07-05T22:00:00Z"))).toBe("2026-W28");
  });
  it("semana 53 y año ISO", () => {
    expect(semanaDe(d("2026-12-31T12:00:00Z"))).toBe("2026-W53");
    expect(semanaDe(d("2027-01-01T12:00:00Z"))).toBe("2026-W53");
    expect(semanaDe(d("2027-01-04T12:00:00Z"))).toBe("2027-W01");
  });
});

describe("finDeSemana", () => {
  it("es el lunes siguiente a las 00:00 de Madrid", () => {
    expect(finDeSemana("2026-W45")).toBe(Date.UTC(2026, 10, 8, 23, 0));
    expect(finDeSemana("2026-W27")).toBe(Date.UTC(2026, 6, 5, 22, 0));
  });
  it("cruza el fin de año", () => {
    expect(finDeSemana("2026-W53")).toBe(Date.UTC(2027, 0, 3, 23, 0));
  });
  it("rechaza ids inválidos", () => {
    expect(() => finDeSemana("2026-45")).toThrow();
    expect(() => finDeSemana("2026-W00")).toThrow();
    expect(() => finDeSemana("2027-W53")).toThrow();
  });
});

describe("semanaAnterior", () => {
  it("cruza el año", () => {
    expect(semanaAnterior("2027-W01")).toBe("2026-W53");
    expect(semanaAnterior("2026-W01")).toBe("2025-W52");
  });
  it("normal y con cero a la izquierda", () => {
    expect(semanaAnterior("2026-W10")).toBe("2026-W09");
    expect(() => semanaAnterior("basura")).toThrow();
  });
});
