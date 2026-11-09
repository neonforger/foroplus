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
import { leerMetas, metasDe, tarjetaDe, USER_AGENT } from "../src/tarjeta";

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

describe("cabeceras de salida", () => {
  it("toda subpetición (oEmbed, página, Deezer) lleva User-Agent móvil y Accept-Language", async () => {
    const llamadas: Array<[string, RequestInit | undefined]> = [];
    const respuestas: Record<string, () => Response> = {
      [SP]: () => json(spEstopaOembed),
      [SP_PAGINA]: () => html(spEstopaMeta),
      [DZ]: () => json(deezerEstopa),
    };
    const red = async (url: string, init?: RequestInit) => {
      llamadas.push([url, init]);
      return respuestas[Object.keys(respuestas).find((p) => url.startsWith(p))!]();
    };
    await tarjetaDe(SP_ESTOPA, red);
    expect(llamadas).toHaveLength(3);
    for (const [url, init] of llamadas) {
      const h = init?.headers as Record<string, string>;
      expect(h?.["User-Agent"], url).toBe(USER_AGENT);
      expect(h?.["Accept-Language"], url).toBe("en");
    }
    llamadas.length = 0;
    respuestas[YT] = () => json(ytEstopa);
    await tarjetaDe(YT_ESTOPA, red);
    expect(llamadas).toHaveLength(2);
    for (const [url, init] of llamadas) expect((init?.headers as Record<string, string>)?.["User-Agent"], url).toBe(USER_AGENT);
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

/** Respuesta con cuerpo en trozos; `pulls` cuenta cuántos trozos pidió el lector y `cancelado` si se cortó. */
function respuestaEnTrozos(trozos: Uint8Array[]) {
  const estado = { pulls: 0, cancelado: false };
  let i = 0;
  const cuerpo = new ReadableStream<Uint8Array>(
    {
      pull(c) {
        estado.pulls++;
        if (i < trozos.length) c.enqueue(trozos[i++]);
        else c.close();
      },
      cancel() {
        estado.cancelado = true;
      },
    },
    { highWaterMark: 0 },
  );
  return { r: new Response(cuerpo, { headers: { "content-type": "text/html" } }), estado };
}
const bytes = (s: string) => new TextEncoder().encode(s);
const redCon = (r: Response) => async () => r;

describe("metasDe (lectura en streaming)", () => {
  it("parte </head> entre dos trozos y no lee los de después", async () => {
    const { r, estado } = respuestaEnTrozos([
      bytes('<html><head><meta property="og:title" content="Hola"/></he'),
      bytes("ad><body>" + "x".repeat(100)),
      bytes("y".repeat(100)),
      bytes("z".repeat(100)),
    ]);
    const m = await metasDe(redCon(r), "https://x/");
    expect(m?.get("og:title")).toBe("Hola");
    expect(estado.cancelado).toBe(true);
    expect(estado.pulls).toBeLessThanOrEqual(2);
  });

  it("un carácter de 3 bytes partido entre trozos no se corrompe", async () => {
    const todo = bytes('<head><meta name="a" content="€ñ"/></head>');
    const corte = todo.indexOf(0xe2) + 1; // en medio del €
    const { r } = respuestaEnTrozos([todo.slice(0, corte), todo.slice(corte)]);
    expect((await metasDe(redCon(r), "https://x/"))?.get("a")).toBe("€ñ");
  });

  it("sin </head> en el tope, parsea lo leído y corta", async () => {
    const t = 8 * 1024;
    const primero = bytes('<meta name="q" content="v"/>' + "a".repeat(t));
    const trozos = Array.from({ length: 40 }, (_, i) => (i === 0 ? primero : bytes("a".repeat(t))));
    const { r, estado } = respuestaEnTrozos(trozos);
    const m = await metasDe(redCon(r), "https://x/");
    expect(m?.get("q")).toBe("v");
    expect(estado.cancelado).toBe(true);
    expect(estado.pulls).toBeLessThan(40);
    expect(estado.pulls * t).toBeLessThanOrEqual(64 * 1024 + 2 * t);
  });

  it("sin cuerpo o con error de lectura devuelve null", async () => {
    expect(await metasDe(redCon(new Response(null)), "https://x/")).toBeNull();
    const roto = new Response(
      new ReadableStream({
        pull() {
          throw new Error("corte");
        },
      }),
    );
    expect(await metasDe(redCon(roto), "https://x/")).toBeNull();
  });

  it("el fixture real de Spotify da lo mismo leído en streaming", async () => {
    const todo = bytes(spMeta);
    const trozos: Uint8Array[] = [];
    for (let i = 0; i < todo.length; i += 1000) trozos.push(todo.slice(i, i + 1000));
    const { r } = respuestaEnTrozos(trozos);
    const m = await metasDe(redCon(r), "https://x/");
    const ref = leerMetas(spMeta);
    for (const k of ["og:description", "og:image", "music:musician_description"]) expect(m?.get(k)).toBe(ref.get(k));
  });
});
