import { env } from "cloudflare:workers";
import { beforeEach, describe, expect, it } from "vitest";
import deezerEstopa from "./fixtures/deezer_estopa.json";
import deezerTrack from "./fixtures/deezer_track_92719900.json";
import spEstopaMeta from "./fixtures/sp_estopa_meta.html?raw";
import spEstopaOembed from "./fixtures/sp_estopa_oembed.json";
import { cancionDeOrigen, cancionPub } from "../src/canciones";
import { deps, filas, TABLAS } from "./ayudas-admin";

const json = (o: unknown) => new Response(JSON.stringify(o), { headers: { "content-type": "application/json" } });

/** `red` falsa por prefijo; lanza ante cualquier otra URL y apunta las vistas. */
function redFalsa(r: Record<string, () => Response>) {
  const vistas: string[] = [];
  const red = async (url: string): Promise<Response> => {
    vistas.push(url);
    const k = Object.keys(r).find((p) => url.startsWith(p));
    if (!k) throw new Error("URL inesperada: " + url);
    return r[k]();
  };
  return { red, vistas };
}
const PISTA = "https://api.deezer.com/track/92719900";
const sinLimite = async () => true;

beforeEach(async () => {
  await env.DB.batch(TABLAS.map((t) => env.DB.prepare(`DELETE FROM ${t}`)));
});

describe("cancionDeOrigen", () => {
  it("cancionDeOrigen con deezer crea conversion pendiente", async () => {
    const { red, vistas } = redFalsa({ [PISTA]: () => json(deezerTrack) });
    const r = await cancionDeOrigen(deps({ red }), { deezer: 92719900 }, sinLimite);
    expect(vistas).toEqual([PISTA]);
    expect("cancion" in r).toBe(true);
    const f = (await filas("SELECT * FROM canciones"))[0];
    expect(f).toMatchObject({
      titulo: "Highway to Hell", artista: "AC/DC", deezer: 92719900, isrc: "AUAP07900028",
      spotify: null, conversion: "pendiente", conversion_intentos: 0,
    });
    expect((await filas("SELECT ref FROM enlaces_cancion")).map((x) => x.ref)).toEqual(["deezer:92719900"]);
    if ("cancion" in r) expect(cancionPub(r.cancion)).toMatchObject({ deezer: 92719900, titulo: "Highway to Hell" });
  });

  it("un enlace de Deezer ya conocido se resuelve sin red", async () => {
    const { red } = redFalsa({ [PISTA]: () => json(deezerTrack) });
    await cancionDeOrigen(deps({ red }), { deezer: 92719900 }, sinLimite);
    const r = await cancionDeOrigen(deps(), { enlace: "https://www.deezer.com/es/track/92719900" }, sinLimite);
    expect("cancion" in r).toBe(true);
    expect(await filas("SELECT id FROM canciones")).toHaveLength(1);
  });

  it("errores: no existe, sin cupo (sin red) y enlace que no es canción", async () => {
    const { red } = redFalsa({ "https://api.deezer.com/track/1": () => json({ error: { code: 800 } }) });
    expect(await cancionDeOrigen(deps({ red }), { deezer: 1 }, sinLimite)).toEqual({ error: "no_encontrada" });
    expect(await cancionDeOrigen(deps(), { deezer: 2 }, async () => false)).toEqual({ error: "limite" });
    expect(await cancionDeOrigen(deps(), { enlace: "https://ejemplo.com" }, sinLimite)).toEqual({ error: "no_es_cancion" });
  });

  it("cancionDeOrigen con enlace de Spotify queda hecha con su id", async () => {
    const SP = "0aaaaaaaaaaaaaaaaaaaaa";
    const { red } = redFalsa({
      "https://open.spotify.com/oembed?": () => json(spEstopaOembed),
      "https://open.spotify.com/track/": () => new Response(spEstopaMeta, { headers: { "content-type": "text/html" } }),
      "https://api.deezer.com/search?": () => json(deezerEstopa),
    });
    const r = await cancionDeOrigen(deps({ red }), { enlace: "https://open.spotify.com/track/" + SP }, sinLimite);
    expect("cancion" in r).toBe(true);
    const f = (await filas("SELECT * FROM canciones"))[0];
    expect(f).toMatchObject({ spotify: SP, conversion: "hecha", deezer: null });
    if ("cancion" in r) expect(cancionPub(r.cancion).spotify).toBe("https://open.spotify.com/track/" + SP);
  });

  it("misma clave desde Deezer reutiliza la fila y no reconvierte", async () => {
    // La canción ya está (p. ej. llegó por Spotify), convertida.
    await env.DB.prepare(
      "INSERT INTO canciones (id, clave, titulo, artista, portada, isrc, enlaces, spotify, conversion) " +
        "VALUES ('c1', 'ac/dc|highway to hell', 'Highway to Hell', 'AC/DC', NULL, NULL, '{}', '0aaaaaaaaaaaaaaaaaaaaa', 'hecha')",
    ).run();
    const { red } = redFalsa({ [PISTA]: () => json(deezerTrack) });
    const r = await cancionDeOrigen(deps({ red }), { deezer: 92719900 }, sinLimite);
    expect("cancion" in r && r.cancion.id).toBe("c1");
    const fs = await filas("SELECT * FROM canciones");
    expect(fs).toHaveLength(1);
    expect(fs[0]).toMatchObject({ spotify: "0aaaaaaaaaaaaaaaaaaaaa", conversion: "hecha", deezer: 92719900, isrc: "AUAP07900028" });
    expect((await filas("SELECT ref FROM enlaces_cancion")).map((x) => x.ref)).toEqual(["deezer:92719900"]);
  });
});
