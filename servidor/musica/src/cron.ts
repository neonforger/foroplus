// Tareas programadas (triggers de wrangler.jsonc). Cada ejecución está acotada: consultas con tope y
// sin salir a la red (10 ms de CPU en el plan gratuito).
import type { Deps } from "./app";

export const CRON_LIMPIEZA = "30 3 * * *";
/** El feed (caducar publicaciones, rescatar conversiones): lo rellena la Tarea S7. */
export const CRON_FEED = "*/15 * * * *";

const DIA_MS = 86_400_000;
/** Un `limites` más viejo que esto ya no cuenta para nada (la ventana más larga es de un día). */
const LIMITES_DIAS = 2;

async function limpieza(d: Deps): Promise<void> {
  const ahora = d.ahora().getTime();
  await d.db.batch([
    // Los retos guardan el uid en claro: caducados fuera cuanto antes.
    d.db.prepare("DELETE FROM retos WHERE caduca <= ?").bind(ahora),
    d.db.prepare("DELETE FROM tokens WHERE caduca <= ?").bind(ahora),
    d.db.prepare("DELETE FROM limites WHERE ventana < ?").bind(Math.floor((ahora - LIMITES_DIAS * DIA_MS) / 1000)),
  ]);
}

async function feed(_d: Deps): Promise<void> {
  // Hueco hasta la Tarea S7: de momento el cron de cada 15 minutos no hace nada (y no debe fallar).
}

/** Despacha un trigger programado por su cadena cron. Una cadena desconocida no hace nada. */
export async function programado(d: Deps, cron: string): Promise<void> {
  if (cron === CRON_LIMPIEZA) await limpieza(d);
  else if (cron === CRON_FEED) await feed(d);
}
