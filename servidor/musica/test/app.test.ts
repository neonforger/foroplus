import { env } from "cloudflare:workers";
import { describe, expect, it } from "vitest";
import { crearApp, type Deps } from "../src/app";
import { diasMinVoto } from "../src/index";

function deps(): Deps {
  return {
    db: env.DB,
    red: async () => new Response("sin red en los tests", { status: 599 }),
    ahora: () => new Date("2026-11-08T12:00:00Z"),
    aleatorio: () => 0.5,
    pepper: "p",
    adminSecreto: "a",
    lectorSecreto: "l",
    diasMinVoto: 90,
    lectorEnVps: false,
  };
}

const app = () => crearApp(deps());

describe("andamio", () => {
  it("GET /v1/salud -> 200 {ok:true}", async () => {
    const r = await app().fetch(new Request("https://x/v1/salud"));
    expect(r.status).toBe(200);
    expect(r.headers.get("Content-Type")).toBe("application/json; charset=utf-8");
    expect(await r.json()).toEqual({ ok: true });
  });

  it("ruta desconocida -> 404 no_existe", async () => {
    const r = await app().fetch(new Request("https://x/v1/nada"));
    expect(r.status).toBe(404);
    expect(await r.json()).toEqual({ error: "no_existe" });
  });

  it("metodo equivocado -> 405 metodo", async () => {
    const r = await app().fetch(new Request("https://x/v1/salud", { method: "POST" }));
    expect(r.status).toBe(405);
    expect(await r.json()).toEqual({ error: "metodo" });
  });

  it("el esquema trae las 12 tablas", async () => {
    const { results } = await env.DB.prepare(
      "SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite%' AND name NOT LIKE 'd1_%' AND name NOT LIKE '_cf%'",
    ).all<{ name: string }>();
    expect(results.map((f) => f.name).sort()).toEqual(
      [
        "canciones", "cuentas", "denuncias", "enlaces_cancion", "guardados", "limites",
        "playlists_pub", "propuestas", "retos", "semanas", "tokens", "votos",
      ].sort(),
    );
  });
});

describe("DIAS_MIN_VOTO", () => {
  it("solo vale un entero positivo; lo demás cae a 90", () => {
    expect(diasMinVoto("90")).toBe(90);
    expect(diasMinVoto("30")).toBe(30);
    expect(diasMinVoto(" 120 ")).toBe(120);
    // Antes `Number(x) || 90` dejaba pasar negativos y fracciones (B1).
    for (const malo of [undefined, "", "0", "-5", "2.5", "abc", "Infinity", "NaN"]) {
      expect(diasMinVoto(malo), String(malo)).toBe(90);
    }
  });
});
