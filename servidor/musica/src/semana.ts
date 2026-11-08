// Cierre de la semana: top 10 y canción de la semana. Se cierra por cron el lunes a las 00:00 de
// Madrid (tarea B8) y, además, de forma perezosa: cualquier GET /v1/semana, propuesta o voto cierra
// antes las semanas pasadas que sigan abiertas. Así un cron que falle no deja la semana sin cerrar.
//
// Invariante: toda semana con propuestas tiene su fila en `semanas` (la crea `proponer` en el mismo
// batch que la propuesta). Gracias a eso, buscar lo que falta por cerrar es leer las filas abiertas
// (índice parcial idx_semanas_abiertas) y no recorrer todas las propuestas de la historia.
import type { Deps } from "./app";
import { cancionPub, COLUMNAS_CANCION, type CancionPub, type FilaCancion } from "./canciones";
import { semanaDe } from "./tiempo";

export type CancionTop = CancionPub & { votos: number };

const TAMANO_TOP = 10;

// Desempate: a igualdad de votos, la que llegó ANTES a ese número. El instante en que una canción
// alcanzó su recuento final N es el de su N-ésimo voto vigente = el más reciente (MAX(creado)): si
// alguien quitó un voto y otro volvió a votar, cuenta cuando volvió a llegar. Luego, el id.
// Solo cuentan los votos a canciones que siguen propuestas esa semana (una cuenta borrada se lleva su
// propuesta; los votos de los demás a esa canción ya no tienen dónde verse).
const TOP = `SELECT ${COLUMNAS_CANCION}, COUNT(*) AS votos, MAX(v.creado) AS llegada
  FROM votos v
  JOIN propuestas p ON p.semana = v.semana AND p.cancion = v.cancion
  JOIN canciones c ON c.id = v.cancion
  WHERE v.semana = ?1
  GROUP BY v.cancion
  ORDER BY votos DESC, llegada ASC, c.id ASC
  LIMIT ${TAMANO_TOP}`;

/**
 * Cierra la semana `id` y guarda su top (con los votos) y la canción de la semana. Idempotente: una
 * semana ya cerrada no se vuelve a calcular (su top queda tal cual se guardó). No cierra la semana en
 * curso ni una futura. Una semana sin propuestas no se registra: no hubo concurso y `anterior` sigue
 * enseñando la última que sí lo tuvo.
 */
export async function cerrarSemana(d: Deps, id: string): Promise<void> {
  if (!(id < semanaDe(d.ahora()))) return;
  const { results } = await d.db.prepare(TOP).bind(id).all<FilaCancion & { votos: number }>();
  const top: CancionTop[] = results.map((f) => ({ ...cancionPub(f), votos: f.votos }));
  await d.db.batch([
    // Upsert con la misma condición en los dos caminos: solo si hubo propuestas y no estaba cerrada.
    // Dos cierres a la vez calculan el mismo top (los votos de una semana pasada ya no cambian) y el
    // segundo no toca nada.
    d.db
      .prepare(
        "INSERT INTO semanas (id, cerrada, top, cancion_semana) " +
          "SELECT ?1, 1, ?2, ?3 WHERE EXISTS (SELECT 1 FROM propuestas WHERE semana = ?1) " +
          "ON CONFLICT (id) DO UPDATE SET cerrada = 1, top = excluded.top, cancion_semana = excluded.cancion_semana " +
          "WHERE semanas.cerrada = 0",
      )
      .bind(id, JSON.stringify(top), top[0]?.id ?? null),
    // Una semana abierta que se quedó sin propuestas (se borraron con su cuenta) no es una semana: se
    // quita la fila, o el cierre perezoso volvería a mirarla en cada petición.
    d.db
      .prepare("DELETE FROM semanas WHERE id = ?1 AND cerrada = 0 AND NOT EXISTS (SELECT 1 FROM propuestas WHERE semana = ?1)")
      .bind(id),
  ]);
}

/**
 * Cierra toda semana anterior a la en curso que siga abierta. Sin nada que cerrar (lo normal) es UNA
 * consulta que lee solo las filas abiertas: se llama al principio de cada GET /v1/semana, propuesta y
 * voto. Los ids "AAAA-Www" se ordenan bien como texto.
 */
export async function asegurarCierres(d: Deps): Promise<void> {
  const { results } = await d.db
    .prepare("SELECT id FROM semanas WHERE cerrada = 0 AND id < ? ORDER BY id")
    .bind(semanaDe(d.ahora()))
    .all<{ id: string }>();
  for (const { id } of results) await cerrarSemana(d, id);
}
