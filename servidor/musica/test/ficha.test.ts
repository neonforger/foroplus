import { describe, expect, it } from "vitest";
import conCoche from "./fixtures/ficha_con_coche.html?raw";
import sinCoche from "./fixtures/ficha_sin_coche.html?raw";
import noExiste from "./fixtures/ficha_no_existe.html?raw";
import challenge from "./fixtures/ficha_challenge.html?raw";
import { diasDesde, leerFicha } from "../src/ficha";

// ficha_no_existe.html y ficha_challenge.html están hechos a mano a partir del título/cabecera medidos.
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
  it("perfil sin registro legible sigue siendo ok, con registro vacío", () => {
    expect(leerFicha(200, h(), "<title>Forocoches - Ver Perfil: Ana</title><p>nada</p>")).toEqual({
      estado: "ok", nombre: "Ana", registro: "", coche: null,
    });
    expect(diasDesde("", new Date())).toBeNull();
  });
  it("un 'Registro:' en texto libre no gana al real", () => {
    const html = conCoche.replace("<h3", "<p>Registro: 01-ene-2000 <b>x</b></p><h3");
    expect(leerFicha(200, h(), html)).toMatchObject({ registro: "30-may-2026" });
  });
  it("un 'Registro:' falso con span distinto no desplaza al real", () => {
    const html = conCoche.replace("<h3", "<p>Registro: <span>01-ene-2000</span></p><h3");
    expect(leerFicha(200, h(), html)).toMatchObject({ registro: "30-may-2026" });
  });
  it("marcador del coche escrito como texto en otro campo no cuenta", () => {
    const falso = "<p>background-image: var(--coches) &lt;span style=&quot;background-image: var(--coches)&quot;&gt; Fiat Panda 2000</p>";
    const antes = conCoche.replace("<h3", falso + "<h3");
    expect(leerFicha(200, h(), antes)).toMatchObject({ coche: "Seat Ibiza 1.9 TDI 2004" });
    const solo = sinCoche.replace("<h3", falso + "<h3");
    expect(leerFicha(200, h(), solo)).toMatchObject({ coche: null });
  });
  it("un marcador falso fuera del bloque (después) tampoco cuenta", () => {
    const html = sinCoche.replace("</body>", "<div>var(--coches)</span> Fiat Panda 2000</div></body>");
    expect(leerFicha(200, h(), html)).toMatchObject({ coche: null });
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
