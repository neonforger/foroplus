import { crearApp, type Deps } from "./app";

export interface Env {
  DB: D1Database;
  UID_PEPPER: string;
  ADMIN_SECRETO: string;
  LECTOR_SECRETO: string;
  DIAS_MIN_VOTO: string;
  LECTOR_EN_VPS: string;
}

function aleatorio(): number {
  // [0,1) con 32 bits de entropía criptográfica.
  return crypto.getRandomValues(new Uint32Array(1))[0] / 4294967296;
}

function deps(env: Env): Deps {
  return {
    db: env.DB,
    red: (u, i) => fetch(u, i),
    ahora: () => new Date(),
    aleatorio,
    pepper: env.UID_PEPPER,
    adminSecreto: env.ADMIN_SECRETO,
    lectorSecreto: env.LECTOR_SECRETO,
    diasMinVoto: Number(env.DIAS_MIN_VOTO) || 90,
    lectorEnVps: env.LECTOR_EN_VPS === "true",
  };
}

export default {
  fetch(req: Request, env: Env, _ctx: ExecutionContext): Promise<Response> {
    return crearApp(deps(env)).fetch(req);
  },
  scheduled(event: ScheduledController, env: Env, ctx: ExecutionContext): void {
    ctx.waitUntil(crearApp(deps(env)).programado(event.cron));
  },
};
