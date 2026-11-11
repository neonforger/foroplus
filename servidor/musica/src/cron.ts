// Tareas programadas (triggers de wrangler.jsonc). Cada ejecución está acotada: consultas con tope y
// sin salir a la red (10 ms de CPU en el plan gratuito).
import type { Deps } from "./app";
import { semanaDe } from "./tiempo";

export const CRON_LIMPIEZA = "30 3 * * *";
/** El feed: caducar las publicaciones de 24 h y guardar la más querida de cada semana. */
export const CRON_FEED = "*/15 * * * *";

const DIA_MS = 86_400_000;
/** Un `limites` más viejo que esto ya no cuenta para nada (la ventana más larga es de un día). */
const LIMITES_DIAS = 2;
/** La más querida de hace más de tantas semanas ya no la lee nadie (el feed solo enseña la anterior). */
const MAS_QUERIDA_SEMANAS = 8;
/** Intentos tras los que el conversor del VPS se da por vencido con una canción (y una hora de margen). */
const INTENTOS_MAX = 24;
const HORA_MS = 3_600_000;

async function limpieza(d: Deps): Promise<void> {
  const ahora = d.ahora().getTime();
  await d.db.batch([
    // Los retos guardan el uid en claro: caducados fuera cuanto antes.
    d.db.prepare("DELETE FROM retos WHERE caduca <= ?").bind(ahora),
    d.db.prepare("DELETE FROM tokens WHERE caduca <= ?").bind(ahora),
    d.db.prepare("DELETE FROM limites WHERE ventana < ?").bind(Math.floor((ahora - LIMITES_DIAS * DIA_MS) / 1000)),
    // Los ids de semana son AAAA-Www con ceros: comparar como texto es comparar por fecha.
    d.db.prepare("DELETE FROM mas_querida WHERE semana < ?").bind(semanaDe(new Date(ahora - MAS_QUERIDA_SEMANAS * 7 * DIA_MS))),
    // Una conversión que lleva INTENTOS_MAX intentos y una hora sin moverse no se va a hacer: estado limpio y terminal.
    d.db
      .prepare("UPDATE canciones SET conversion = 'error' WHERE conversion = 'en_curso' AND conversion_intentos >= ? AND conversion_en < ?")
      .bind(INTENTOS_MAX, ahora - HORA_MS),
  ]);
}

/**
 * Caduca el feed: por cada semana (la de `creada`) guarda la publicación con más corazones si supera a la guardada
 * y borra las caducadas con sus corazones. Tres consultas en total, sin red. Las ocultas y las de cuentas baneadas
 * se borran pero no compiten. Empate: gana la más antigua; entre tandas basta con "estrictamente más corazones",
 * porque lo que caduca después también se creó después.
 */
async function limpiarFeed(d: Deps): Promise<void> {
  const ahora = d.ahora().getTime();
  const { results } = await d.db
    .prepare(
      `SELECT p.cancion, p.texto, p.creada, p.corazones, c.nombre AS autor
         FROM publicaciones p JOIN cuentas c ON c.id = p.cuenta
        WHERE p.caduca <= ? AND p.oculta = 0 AND c.baneada = 0
        ORDER BY p.corazones DESC, p.creada ASC`,
    )
    .bind(ahora)
    .all<{ cancion: string; texto: string; creada: number; corazones: number; autor: string }>();
  // Ordenadas por corazones y antigüedad: la primera de cada semana es la ganadora.
  const ganadoras = new Map<string, (typeof results)[number]>();
  for (const p of results) {
    const semana = semanaDe(new Date(p.creada));
    if (!ganadoras.has(semana)) ganadoras.set(semana, p);
  }
  await d.db.batch([
    ...[...ganadoras].map(([semana, p]) =>
      d.db
        .prepare(
          `INSERT INTO mas_querida (semana, cancion, autor, texto, corazones) VALUES (?, ?, ?, ?, ?)
           ON CONFLICT (semana) DO UPDATE SET cancion = excluded.cancion, autor = excluded.autor,
             texto = excluded.texto, corazones = excluded.corazones
           WHERE excluded.corazones > mas_querida.corazones`,
        )
        .bind(semana, p.cancion, p.autor, p.texto, p.corazones),
    ),
    d.db.prepare("DELETE FROM corazones WHERE publicacion IN (SELECT id FROM publicaciones WHERE caduca <= ?)").bind(ahora),
    d.db.prepare("DELETE FROM publicaciones WHERE caduca <= ?").bind(ahora),
  ]);
}

/** Despacha un trigger programado por su cadena cron. Una cadena desconocida no hace nada. */
export async function programado(d: Deps, cron: string): Promise<void> {
  if (cron === CRON_LIMPIEZA) await limpieza(d);
  else if (cron === CRON_FEED) await limpiarFeed(d);
}
