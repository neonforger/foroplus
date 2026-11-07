import { describe, expect, it } from "vitest";
import deezerEstopa from "./fixtures/deezer_estopa.json";
import deezerVacio from "./fixtures/deezer_vacio.json";
import spEstopaMeta from "./fixtures/sp_estopa_meta.html?raw";
import spEstopaOembed from "./fixtures/sp_estopa_oembed.json";
import spMeta from "./fixtures/sp_track_meta.html?raw";
import spOembed from "./fixtures/sp_oembed.json";
import ytEstopa from "./fixtures/yt_estopa_oembed.json";
import ytOembed from "./fixtures/yt_oembed.json";
import { claveCancion, type RefCancion } from "../src/normalizar";
import { leerMetas, tarjetaDe } from "../src/tarjeta";

const YT_ESTOPA: RefCancion = { fuente: "youtube", id: "aaaaaaaaaaa" };
const SP_ESTOPA: RefCancion = { fuente: "spotify", id: "0aaaaaaaaaaaaaaaaaaaaa" };

type Respuesta = Response | (() => Response);

/** `red` falsa: sirve por prefijo de URL y LANZA ante cualquier otra, para que una petición de más falle. */
function redFalsa(respuestas: Record<string, Respuesta>) {
  const vistas: string[] = [];
  const red = async (url: string): Promise<Response> => {
    vistas.push(url);
    for (const [prefijo, r] of Object.entries(respuestas)) {
      if (url.startsWith(prefijo)) return typeof r === "function" ? r() : r.clone();
    }
    throw new Error("URL inesperada: " + url);
  };
  return { red, vistas };
}
const json = (o: unknown) => new Response(JSON.stringify(o), { headers: { "content-type": "application/json" } });
const html = (s: string) => new Response(s, { headers: { "content-type": "text/html" } });

const YT = "https://www.youtube.com/oembed?";
const SP = "https://open.spotify.com/oembed?";
const SP_PAGINA = "https://open.spotify.com/track/";
const DZ = "https://api.deezer.com/search?";

describe("tarjetaDe de YouTube", () => {
  it("monta la tarjeta con YouTube y Deezer en 2 subpeticiones", async () => {
    const { red, vistas } = redFalsa({ [YT]: json(ytEstopa), [DZ]: json(deezerEstopa) });
    const t = await tarjetaDe(YT_ESTOPA, red);
    expect(t).not.toBeNull();
    expect(t!.artista).toBe("Estopa");
    expect(t!.titulo).toBe("Como Camarón");
    expect(t!.clave).toBe(claveCancion("Estopa", "Como Camarón"));
    expect(t!.enlace).toBe("https://www.youtube.com/watch?v=aaaaaaaaaaa");
    expect(t!.portada).toBe("https://i.ytimg.com/vi/aaaaaaaaaaa/hqdefault.jpg");
    expect(t!.ref).toEqual(YT_ESTOPA);
    expect(vistas).toHaveLength(2);
    expect(decodeURIComponent(vistas[1])).toContain("search?q=Estopa Como Camarón&limit=5");
  });

  it("sin resultado en Deezer usa nuestros nombres", async () => {
    const { red, vistas } = redFalsa({ [YT]: json(ytEstopa), [DZ]: json(deezerVacio) });
    const t = await tarjetaDe(YT_ESTOPA, red);
    expect(t).toMatchObject({ artista: "Estopa", titulo: "Como Camarón", clave: "estopa|como camaron" });
    expect(vistas).toHaveLength(2);
  });

  it("si Deezer falla (500 o lanza) se usan nuestros nombres", async () => {
    const caida = (): Response => {
      throw new Error("red caída");
    };
    for (const dz of [new Response("x", { status: 500 }), caida]) {
      const { red } = redFalsa({ [YT]: json(ytEstopa), [DZ]: dz });
      const t = await tarjetaDe(YT_ESTOPA, red);
      expect(t).toMatchObject({ artista: "Estopa", titulo: "Como Camarón" });
    }
  });

  it("un resultado de Deezer de otra canción no se acepta", async () => {
    const otra = { data: [{ title: "Otra", title_short: "Otra", artist: { name: "Estopa" } }] };
    const { red } = redFalsa({ [YT]: json(ytEstopa), [DZ]: json(otra) });
    const t = await tarjetaDe(YT_ESTOPA, red);
    expect(t!.titulo).toBe("Como Camarón");
  });

  it("oEmbed 404 o ilegible da null", async () => {
    const a = redFalsa({ [YT]: new Response("no", { status: 404 }) });
    expect(await tarjetaDe(YT_ESTOPA, a.red)).toBeNull();
    expect(a.vistas).toHaveLength(1);
    const b = redFalsa({ [YT]: new Response("<html>", { status: 200 }) });
    expect(await tarjetaDe(YT_ESTOPA, b.red)).toBeNull();
  });

  it("la portada solo vale de los hosts permitidos", async () => {
    const o = { ...ytEstopa, thumbnail_url: "https://evil.example/x.jpg" };
    const { red } = redFalsa({ [YT]: json(o), [DZ]: json(deezerVacio) });
    expect((await tarjetaDe(YT_ESTOPA, red))!.portada).toBeNull();
  });

  it("el vídeo de ejemplo (Rick Astley) separa título y artista", async () => {
    const { red } = redFalsa({ [YT]: json(ytOembed), [DZ]: json(deezerVacio) });
    const t = await tarjetaDe({ fuente: "youtube", id: "dQw4w9WgXcQ" }, red);
    expect(t).toMatchObject({ artista: "Rick Astley", titulo: "Never Gonna Give You Up" });
  });
});

describe("tarjetaDe de Spotify", () => {
  it("usa oEmbed, página y Deezer (3 subpeticiones) y da la misma clave que YouTube", async () => {
    const sp = redFalsa({ [SP]: json(spEstopaOembed), [SP_PAGINA]: html(spEstopaMeta), [DZ]: json(deezerEstopa) });
    const t = await tarjetaDe(SP_ESTOPA, sp.red);
    expect(sp.vistas).toHaveLength(3);
    expect(t).toMatchObject({
      artista: "Estopa",
      titulo: "Como Camarón",
      enlace: "https://open.spotify.com/track/0aaaaaaaaaaaaaaaaaaaaa",
      portada: "https://i.scdn.co/image/ab67616d0000b2730000000000000000000000aa",
    });
    const yt = redFalsa({ [YT]: json(ytEstopa), [DZ]: json(deezerEstopa) });
    expect(t!.clave).toBe((await tarjetaDe(YT_ESTOPA, yt.red))!.clave);
  });

  it("si la página falla, artista null, clave con artista vacío y portada del oEmbed", async () => {
    const { red, vistas } = redFalsa({ [SP]: json(spEstopaOembed), [SP_PAGINA]: new Response("x", { status: 503 }) });
    const t = await tarjetaDe(SP_ESTOPA, red);
    expect(t).toMatchObject({
      artista: null,
      titulo: "Como Camarón",
      clave: "|como camaron",
      portada: "https://image-cdn-fa.spotifycdn.com/image/ab67616d00001e020000000000000000000000aa",
    });
    expect(vistas).toHaveLength(2);
  });

  it("oEmbed 404 da null sin pedir la página", async () => {
    const { red, vistas } = redFalsa({ [SP]: new Response("no", { status: 404 }) });
    expect(await tarjetaDe(SP_ESTOPA, red)).toBeNull();
    expect(vistas).toHaveLength(1);
  });

  it("con los fixtures de la app (Rick Astley) lee artista y portada de la página", async () => {
    const { red } = redFalsa({ [SP]: json(spOembed), [SP_PAGINA]: html(spMeta), [DZ]: json(deezerVacio) });
    const t = await tarjetaDe({ fuente: "spotify", id: "4cOdK2wGLETKBW3PvgPWqT" }, red);
    expect(t).toMatchObject({ artista: "Rick Astley", titulo: "Never Gonna Give You Up" });
    expect(t!.portada).toBe("https://i.scdn.co/image/ab67616d0000b273baf89eb11ec7c657805d2da0");
  });
});

describe("leerMetas", () => {
  it("lee property/name y content, con entidades, y se para en </head>", () => {
    const m = leerMetas(
      `<html><head><meta property="og:title" content="Don&#x27;t &amp; Co"/><meta name='a' content='b'></head><body><meta name="tarde" content="x"></body>`,
    );
    expect(m.get("og:title")).toBe("Don't & Co");
    expect(m.get("a")).toBe("b");
    expect(m.has("tarde")).toBe(false);
  });
  it("es lineal sobre una página grande", () => {
    const grande = "<html><head>" + "<script>var x=1;</script>".repeat(15000) + '<meta name="z" content="ok"/></head>';
    const t0 = performance.now();
    expect(leerMetas(grande).get("z")).toBe("ok");
    expect(performance.now() - t0).toBeLessThan(50);
  });
});
