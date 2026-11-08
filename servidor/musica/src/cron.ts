// Tareas programadas (triggers de wrangler.jsonc). Cada ejecución está acotada: consultas con tope y
// sin salir a la red (10 ms de CPU en el plan gratuito).
import type { Deps } from "./app";
import { nuevoId } from "./cripto";
import { asegurarCierres, cerrarSemana } from "./semana";
import { horaMadrid, semanaAnterior, semanaDe } from "./tiempo";

export const CRON_CIERRE = "0 22,23 * * SUN";
export const CRON_LIMPIEZA = "30 3 * * *";

const DIA_MS = 86_400_000;
/** Un `limites` más viejo que esto ya no cuenta para nada (la ventana más larga es de un día). */
const LIMITES_DIAS = 2;

// Regla de aviso (solo SEÑALA, nunca castiga: lo revisa una persona). Una canción de la semana en curso
// con muchos votos en 24 h, casi todos de cuentas recién llegadas, es la pinta de una granja de cuentas.
export const SUBIDA_MIN_VOTOS = 10;
export const SUBIDA_PORCENTAJE_NUEVAS = 70;
export const SUBIDA_HORAS = 24;
export const CUENTA_NUEVA_DIAS = 7;
/** Tope de denuncias que crea una pasada: la regla no debe poder inundar la bandeja. */
const SUBIDA_MAX_POR_PASADA = 20;

/**
 * Las semanas cruzan en lunes 00:00 de Madrid, que son DOS instantes UTC según la hora de verano: el
 * cron corre a las 22:00 y a las 23:00 UTC del domingo y solo cierra el que cae en lunes 00:xx de
 * Madrid. Cerrar es idempotente, así que repetir no hace daño.
 */
async function cierreSemanal(d: Deps): Promise<void> {
  const ahora = d.ahora();
  const h = horaMadrid(ahora);
  if (h.getUTCDay() !== 1 || h.getUTCHours() !== 0) return;
  await cerrarSemana(d, semanaAnterior(semanaDe(ahora)));
  // Red de seguridad: si alguna semana más antigua se quedó abierta, también.
  await asegurarCierres(d);
}

async function limpieza(d: Deps): Promise<void> {
  const ahora = d.ahora().getTime();
  await d.db.batch([
    // Los retos guardan el uid en claro: caducados fuera cuanto antes.
    d.db.prepare("DELETE FROM retos WHERE caduca <= ?").bind(ahora),
    d.db.prepare("DELETE FROM tokens WHERE caduca <= ?").bind(ahora),
    d.db.prepare("DELETE FROM limites WHERE ventana < ?").bind(Math.floor((ahora - LIMITES_DIAS * DIA_MS) / 1000)),
  ]);
  await senalarSubidasRaras(d);
}

async function senalarSubidasRaras(d: Deps): Promise<void> {
  const ahora = d.ahora();
  const t = ahora.getTime();
  const { results } = await d.db
    .prepare(
      "SELECT v.cancion AS cancion, COUNT(*) AS n, " +
        "SUM(CASE WHEN c.creada IS NOT NULL AND c.creada > ?3 THEN 1 ELSE 0 END) AS nuevas " +
        "FROM votos v JOIN cuentas c ON c.id = v.cuenta " +
        "WHERE v.semana = ?1 AND v.creado >= ?2 " +
        "GROUP BY v.cancion HAVING n >= ?4 AND nuevas * 100 >= ?5 * n " +
        "ORDER BY n DESC LIMIT ?6",
    )
    .bind(
      semanaDe(ahora), t - SUBIDA_HORAS * 3_600_000, t - CUENTA_NUEVA_DIAS * DIA_MS,
      SUBIDA_MIN_VOTOS, SUBIDA_PORCENTAJE_NUEVAS, SUBIDA_MAX_POR_PASADA,
    )
    .all<{ cancion: string; n: number; nuevas: number }>();
  if (results.length === 0) return;
  await d.db.batch(
    results.map((f) =>
      d.db
        .prepare(
          "INSERT INTO denuncias (id, tipo, objetivo, cuenta, motivo, creada, resuelta) " +
            "SELECT ?1, 'sistema', ?2, 'sistema', ?3, ?4, 0 WHERE NOT EXISTS " +
            "(SELECT 1 FROM denuncias WHERE tipo = 'sistema' AND objetivo = ?2 AND COALESCE(resuelta, 0) = 0)",
        )
        .bind(
          nuevoId(ahora), f.cancion,
          `${f.n} votos en ${SUBIDA_HORAS} h, ${f.nuevas} de ${f.n} de cuentas con menos de ${CUENTA_NUEVA_DIAS} días`,
          t,
        ),
    ),
  );
}

/** Despacha un trigger programado por su cadena cron. Una cadena desconocida no hace nada. */
export async function programado(d: Deps, cron: string): Promise<void> {
  if (cron === CRON_CIERRE) await cierreSemanal(d);
  else if (cron === CRON_LIMPIEZA) await limpieza(d);
}
