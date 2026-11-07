import { describe, expect, it } from "vitest";
import enlaces from "../../../app/src/test/resources/contrato/musica-enlaces.json";
import titulos from "../../../app/src/test/resources/contrato/musica-titulos.json";
import {
  claveCancion,
  ETIQUETAS_VERSION,
  ETIQUETAS_VIDEO,
  limpiarCanal,
  limpiarTitulo,
  refDe,
  separar,
} from "../src/normalizar";

describe("contrato compartido con la app", () => {
  it("contratoDeEnlaces: refDe da lo mismo que EnlaceMusica", () => {
    for (const c of enlaces) {
      const r = refDe(c.url);
      expect(r === null ? null : { fuente: r.fuente, id: r.id }, c.url).toEqual(
        c.fuente === null ? null : { fuente: c.fuente, id: c.id },
      );
    }
  });

  it("contratoDeTitulos: separar da lo mismo que TituloCancion", () => {
    for (const c of titulos.titulos) {
      expect(separar(c.titulo, c.canal), c.titulo).toEqual({ cancion: c.cancion, artista: c.artista });
    }
  });

  it("etiquetasIgualesAlContrato: las listas de etiquetas son las del JSON", () => {
    expect([...ETIQUETAS_VIDEO]).toEqual(titulos.etiquetasVideo);
    expect([...ETIQUETAS_VERSION]).toEqual(titulos.etiquetasVersion);
  });

  it("contratoDeClaves: iguales o distintas según el contrato", () => {
    expect(titulos.claves.length).toBeGreaterThanOrEqual(5);
    for (const c of titulos.claves) {
      const a = claveCancion(c.a[0], c.a[1]);
      const b = claveCancion(c.b[0], c.b[1]);
      expect(a === b, `${a} vs ${b}`).toBe(c.iguales);
    }
  });
});

describe("limpieza para mostrar", () => {
  it("quita solo etiquetas de vídeo; las de versión se ven", () => {
    expect(limpiarTitulo("Canción (Remastered 2022) (Official Video)")).toBe("Canción (Remastered 2022)");
  });
  it("no quita una etiqueta que es solo parte del grupo", () => {
    expect(limpiarTitulo("Canción (Official Video en directo)")).toBe("Canción (Official Video en directo)");
  });
  it("ignora mayúsculas y tildes en las etiquetas", () => {
    expect(limpiarTitulo("Canción [VIDEO OFICIAL] (Vídeo Oficial)")).toBe("Canción");
  });
  it("el canal pierde VEVO, - Topic, Oficial y Official, pero no se queda vacío", () => {
    expect(limpiarCanal("EstopaVEVO")).toBe("Estopa");
    expect(limpiarCanal("Estopa - Topic")).toBe("Estopa");
    expect(limpiarCanal("VEVO")).toBe("VEVO");
  });
});

describe("claveCancion", () => {
  it("formato artistas|título en minúsculas y sin tildes", () => {
    expect(claveCancion("Estopa", "Como Camarón")).toBe("estopa|como camaron");
  });
  it("ordena los artistas, separados por coma o &", () => {
    expect(claveCancion("Fito & Fitipaldis", "X")).toBe("fitipaldis,fito|x");
  });
  it("quita feat/ft entre paréntesis, corchetes y al final", () => {
    const k = claveCancion("A", "T");
    expect(claveCancion("A", "T (ft. B)")).toBe(k);
    expect(claveCancion("A", "T [feat. B]")).toBe(k);
    expect(claveCancion("A", "T feat. B")).toBe(k);
    expect(claveCancion("A", "T ft. B & C")).toBe(k);
  });
  it("quita etiquetas de vídeo y de versión, con el año de 4 dígitos", () => {
    const k = claveCancion("A", "T");
    expect(claveCancion("A", "T (Official Video) [HD]")).toBe(k);
    expect(claveCancion("A", "T (2011 Remaster)")).toBe(k);
    expect(claveCancion("A", "T (Remastered)")).toBe(k);
    expect(claveCancion("A", "T (Remastered 22)")).not.toBe(k);
  });
  it("una versión en directo no es la misma canción", () => {
    expect(claveCancion("A", "T (Directo)")).not.toBe(claveCancion("A", "T"));
  });
  it("sin artista la clave empieza por |", () => {
    expect(claveCancion("", "T")).toBe("|t");
  });
});
