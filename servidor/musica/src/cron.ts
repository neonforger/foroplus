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

/** Tope de caducadas que se leen (y borran) por ejecución; el resto lo hace la de 15 minutos siguiente. */
const LOTE_FEED = 500;
/** D1 admite 100 parámetros ligados por sentencia: las listas de ids se parten en trozos de esta talla. */
const TROZO_IDS = 100;

/**
 * Caduca el feed: lee como mucho `limite` publicaciones caducadas (las que caducaron antes primero), guarda por cada
 * semana (la de `creada`) la de más corazones si supera a la guardada, y borra EXACTAMENTE las leídas con sus
 * corazones: nada se borra sin contar. Lo que sobre del tope cae en la siguiente ejecución. Las ocultas y las de
 * cuentas baneadas se leen igualmente (para borrarlas) pero no compiten, y sin al menos 1 corazón tampoco.
 * Empate dentro de la tanda: gana la más antigua. Entre tandas basta "estrictamente más corazones" porque lo que
 * caduca después se creó después (invariante de S3: caduca = creada + 24 h).
 */
export async function limpiarFeed(d: Deps, limite = LOTE_FEED): Promise<void> {
  const ahora = d.ahora().getTime();
  const { results } = await d.db
    .prepare(
      `SELECT p.id, p.cancion, p.texto, p.creada, p.corazones, c.nombre AS autor,
              (p.oculta = 0 AND c.baneada = 0 AND p.corazones > 0) AS compite
         FROM publicaciones p LEFT JOIN cuentas c ON c.id = p.cuenta
        WHERE p.caduca <= ?
        ORDER BY p.caduca ASC, p.id ASC LIMIT ?`,
    )
    .bind(ahora, limite)
    .all<{ id: string; cancion: string; texto: string; creada: number; corazones: number; autor: string; compite: number }>();
  if (results.length === 0) return;
  const ganadoras = new Map<string, (typeof results)[number]>();
  for (const p of results) {
    if (!p.compite) continue;
    const semana = semanaDe(new Date(p.creada));
    const g = ganadoras.get(semana);
    if (!g || p.corazones > g.corazones || (p.corazones === g.corazones && p.creada < g.creada)) ganadoras.set(semana, p);
  }
  const ids = results.map((p) => p.id);
  const borrar: D1PreparedStatement[] = [];
  for (let i = 0; i < ids.length; i += TROZO_IDS) {
    const trozo = ids.slice(i, i + TROZO_IDS);
    const marcas = trozo.map(() => "?").join(",");
    borrar.push(d.db.prepare(`DELETE FROM corazones WHERE publicacion IN (${marcas})`).bind(...trozo));
    borrar.push(d.db.prepare(`DELETE FROM publicaciones WHERE id IN (${marcas})`).bind(...trozo));
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
    ...borrar,
  ]);
}

/** Despacha un trigger programado por su cadena cron. Una cadena desconocida no hace nada. */
export async function programado(d: Deps, cron: string): Promise<void> {
  if (cron === CRON_LIMPIEZA) await limpieza(d);
  else if (cron === CRON_FEED) await limpiarFeed(d);
}
