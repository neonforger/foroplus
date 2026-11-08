import { describe, expect, it } from "vitest";
import { hashToken, nuevoId, nuevoToken, uidHmac } from "../src/cripto";

describe("uidHmac", () => {
  it("es estable, hex de 64 y no contiene el uid", async () => {
    const a = await uidHmac("pimienta", "123456");
    expect(a).toMatch(/^[0-9a-f]{64}$/);
    expect(await uidHmac("pimienta", "123456")).toBe(a);
    expect(a).not.toContain("123456");
  });
  it("cambia con otro pepper o con otro uid", async () => {
    const a = await uidHmac("pimienta", "123456");
    expect(await uidHmac("otra", "123456")).not.toBe(a);
    expect(await uidHmac("pimienta", "123457")).not.toBe(a);
  });
  it("coincide con un vector conocido de HMAC-SHA256", async () => {
    // RFC 4231 caso 2: clave "Jefe", mensaje "what do ya want for nothing?"
    expect(await uidHmac("Jefe", "what do ya want for nothing?")).toBe(
      "5bdcc146bf60754e6a042426089575c75a003f089d2739839dec58b964ec3843",
    );
  });
});

describe("nuevoToken / hashToken", () => {
  it("32 bytes en base64url sin relleno, distintos cada vez", () => {
    const t = nuevoToken();
    expect(t).toMatch(/^[A-Za-z0-9_-]{43}$/);
    expect(nuevoToken()).not.toBe(t);
  });
  it("SHA-256 hex (vector conocido)", async () => {
    expect(await hashToken("abc")).toBe("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
  });
});

describe("nuevoId", () => {
  it("ULID de 26 caracteres Crockford con la hora al principio", () => {
    const id = nuevoId(new Date(0));
    expect(id).toMatch(/^[0-9A-HJKMNP-TV-Z]{26}$/);
    expect(id.slice(0, 10)).toBe("0000000000");
  });
  it("codifica el tiempo (vector del spec ULID) y ordena por tiempo", () => {
    // 1469918176385 ms -> 01ARYZ6S41 (ejemplo del spec ULID)
    expect(nuevoId(new Date(1469918176385)).slice(0, 10)).toBe("01ARYZ6S41");
    expect(nuevoId(new Date(1000)) < nuevoId(new Date(2000))).toBe(true);
  });
  it("misma milésima, ids distintos", () => {
    const d = new Date(1700000000000);
    expect(nuevoId(d)).not.toBe(nuevoId(d));
  });
});
