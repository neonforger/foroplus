import { error, json } from "./http";

export interface Deps {
  db: D1Database;
  red: (url: string, init?: RequestInit) => Promise<Response>;
  ahora: () => Date;
  aleatorio: () => number;
  pepper: string;
  adminSecreto: string;
  lectorSecreto: string;
  diasMinVoto: number;
  lectorEnVps: boolean;
}

export interface App {
  fetch(req: Request): Promise<Response>;
  programado(cron: string): Promise<void>;
}

export type Acceso = "publica" | "cuenta" | "admin" | "lector";

export interface Contexto {
  req: Request;
  url: URL;
  params: Record<string, string>;
  deps: Deps;
}

export type Manejador = (c: Contexto) => Promise<Response> | Response;

/** [método, patrón con :param, manejador, acceso]. El acceso lo comprobarán las tareas que lo usen. */
export type Ruta = readonly [string, string, Manejador, Acceso];

function casar(patron: string, ruta: string): Record<string, string> | null {
  const p = patron.split("/");
  const r = ruta.split("/");
  if (p.length !== r.length) return null;
  const params: Record<string, string> = {};
  for (let i = 0; i < p.length; i++) {
    if (p[i].startsWith(":")) {
      if (r[i] === "") return null;
      try {
        params[p[i].slice(1)] = decodeURIComponent(r[i]);
      } catch {
        return null;
      }
    } else if (p[i] !== r[i]) {
      return null;
    }
  }
  return params;
}

function rutas(): Ruta[] {
  return [["GET", "/v1/salud", () => json({ ok: true }), "publica"]];
}

export function crearApp(d: Deps): App {
  const tabla = rutas();
  return {
    async fetch(req) {
      const url = new URL(req.url);
      let rutaConOtroMetodo = false;
      for (const [metodo, patron, manejador] of tabla) {
        const params = casar(patron, url.pathname);
        if (!params) continue;
        if (metodo !== req.method) {
          rutaConOtroMetodo = true;
          continue;
        }
        return manejador({ req, url, params, deps: d });
      }
      return rutaConOtroMetodo ? error("metodo", 405) : error("no_existe", 404);
    },
    async programado(_cron) {
      // Las tareas siguientes (cierre de semana, limpieza) cuelgan de aquí.
    },
  };
}
