import { acceder, type Cuenta } from "./auth";
import { error, json } from "./http";
import { rutasVerificacion } from "./rutas/verificacion";

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

/** Ajustes al montar la app que no son dependencias (`Deps` no cambia): los tests bajan los topes. */
export interface OpcionesApp {
  /** Tope global de lecturas de fichas de FC por hora; sin él, `LECTURAS_FC_HORA` (600). */
  lecturasFcHora?: number;
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
  /** La cuenta del token en las rutas "cuenta" (el router ya la comprobó); null en las demás. */
  cuenta: Cuenta | null;
}

export type Manejador = (c: Contexto) => Promise<Response> | Response;

/** [método, patrón con :param, manejador, acceso]. El router comprueba el acceso antes de llamar. */
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

function rutas(o: OpcionesApp): Ruta[] {
  return [["GET", "/v1/salud", () => json({ ok: true }), "publica"], ...rutasVerificacion(o)];
}

export function crearApp(d: Deps, o: OpcionesApp = {}): App {
  const tabla = rutas(o);
  return {
    async fetch(req) {
      const url = new URL(req.url);
      let rutaConOtroMetodo = false;
      for (const [metodo, patron, manejador, acceso] of tabla) {
        const params = casar(patron, url.pathname);
        if (!params) continue;
        if (metodo !== req.method) {
          rutaConOtroMetodo = true;
          continue;
        }
        // Denegar por defecto: ningún manejador corre sin que su acceso se haya cumplido.
        const permiso = await acceder(acceso, req, d);
        if (permiso instanceof Response) return permiso;
        return manejador({ req, url, params, deps: d, cuenta: permiso });
      }
      return rutaConOtroMetodo ? error("metodo", 405) : error("no_existe", 404);
    },
    async programado(_cron) {
      // Las tareas siguientes (cierre de semana, limpieza) cuelgan de aquí.
    },
  };
}
