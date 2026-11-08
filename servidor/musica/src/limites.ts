// Límites de peticiones en D1 por ventana fija. Las claves NUNCA llevan un uid ni una IP en claro:
// quien las arma les pasa el HMAC con el pepper (`limites` sobrevive a los retos y no puede ser la
// lista de quién usa ForoPlus).

/** Topes de la semana (spec): los lee /v1/yo y los aplicarán votar y proponer. */
export const VOTOS_POR_SEMANA = 10;
export const PROPUESTAS_POR_SEMANA = 3;

/**
 * Cuenta una petición más para `clave` en la ventana de `ventanaSeg` que contiene `ahora` y dice si
 * sigue dentro de `max`. La fila se suma con un único UPSERT ... RETURNING, así dos peticiones a la
 * vez no leen el mismo n. Las rechazadas también suman: martillear no abre hueco antes de tiempo.
 * `ventana` guarda el INICIO de la ventana en segundos epoch: la limpieza puede borrar lo que tenga
 * más de un día (la ventana más larga que se usa) sin saber de qué límite es cada fila.
 */
export async function permitido(
  db: D1Database, clave: string, ventanaSeg: number, max: number, ahora: Date,
): Promise<boolean> {
  const fila = await contarUna(db, clave, ventanaSeg, ahora).first<{ n: number }>();
  return fila !== null && fila.n <= max;
}

/**
 * La sentencia que suma una a `clave` (la misma de `permitido`), para meterla en un `db.batch` junto
 * a lo que se cuenta cuando no hace falta mirar el resultado.
 */
export function contarUna(db: D1Database, clave: string, ventanaSeg: number, ahora: Date): D1PreparedStatement {
  const inicio = Math.floor(ahora.getTime() / 1000 / ventanaSeg) * ventanaSeg;
  return db
    .prepare(
      "INSERT INTO limites (clave, ventana, n) VALUES (?1, ?2, 1) " +
        "ON CONFLICT (clave, ventana) DO UPDATE SET n = n + 1 RETURNING n",
    )
    .bind(clave, inicio);
}
