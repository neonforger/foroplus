import { describe, expect, it } from "vitest";
import conCoche from "./fixtures/ficha_con_coche.html?raw";
import sinCoche from "./fixtures/ficha_sin_coche.html?raw";
import noExiste from "./fixtures/ficha_no_existe.html?raw";
import challenge from "./fixtures/ficha_challenge.html?raw";
import { diasDesde, leerFicha } from "../src/ficha";

const h = (o: Record<string, string> = {}) => new Headers(o);

describe("leerFicha", () => {
  it("lee nombre, registro y coche", () => {
    expect(leerFicha(200, h(), conCoche)).toEqual({
      estado: "ok", nombre: "YoMismo", registro: "30-may-2026", coche: "Seat Ibiza 1.9 TDI 2004",
    });
  });
  it("sin bloque 'Sobre' el coche es null", () => {
    expect(leerFicha(200, h(), sinCoche)).toEqual({
      estado: "ok", nombre: "YoMismo", registro: "30-may-2026", coche: null,
    });
  });
  it("título sin 'Ver Perfil:' es no_existe", () => {
    expect(leerFicha(200, h(), noExiste)).toEqual({ estado: "no_existe" });
  });
  it("403 con cf-mitigated es challenge", () => {
    expect(leerFicha(403, h({ "cf-mitigated": "challenge" }), challenge)).toEqual({ estado: "challenge" });
  });
  it("200 con 'Just a moment...' es challenge", () => {
    expect(leerFicha(200, h(), challenge)).toEqual({ estado: "challenge" });
  });
  it("variante en español del challenge", () => {
    expect(leerFicha(200, h(), "<html><title>Un momento…</title></html>")).toEqual({ estado: "challenge" });
  });
  it("otros estados HTTP son error", () => {
    expect(leerFicha(500, h(), "x")).toEqual({ estado: "error" });
    expect(leerFicha(403, h(), "<title>Forbidden</title>")).toEqual({ estado: "error" });
  });
  it("perfil sin registro legible es error", () => {
    expect(leerFicha(200, h(), "<title>Forocoches - Ver Perfil: Ana</title><p>nada</p>")).toEqual({ estado: "error" });
  });
  it("decodifica entidades en nombre y coche", () => {
    const html = conCoche
      .replace("Ver Perfil: YoMismo", "Ver Perfil: A&amp;B &#39;x&#39;")
      .replace("Seat Ibiza 1.9 TDI 2004", "Mini &quot;Cooper&quot; &#x41;");
    const f = leerFicha(200, h(), html);
    expect(f).toMatchObject({ estado: "ok", nombre: "A&B 'x'", coche: 'Mini "Cooper" A' });
  });
});

describe("diasDesde", () => {
  const hoy = new Date("2026-11-07T10:00:00Z");
  it("fecha con mes abreviado en español", () => {
    expect(diasDesde("30-may-2026", hoy)).toBe(161);
  });
  it("Hoy y Ayer", () => {
    expect(diasDesde("Hoy", hoy)).toBe(0);
    expect(diasDesde("Ayer", hoy)).toBe(1);
  });
  it("basura es null", () => {
    expect(diasDesde("basura", hoy)).toBeNull();
    expect(diasDesde("30-xyz-2026", hoy)).toBeNull();
    expect(diasDesde("31-feb-2026", hoy)).toBeNull();
  });
  it("todos los meses", () => {
    const ms = ["ene", "feb", "mar", "abr", "may", "jun", "jul", "ago", "sep", "oct", "nov", "dic"];
    ms.forEach((m, i) => {
      const esperado = Math.round((Date.UTC(2026, 10, 7) - Date.UTC(2026, i, 1)) / 86_400_000);
      expect(diasDesde(`1-${m}-2026`, hoy)).toBe(esperado);
    });
  });
});
