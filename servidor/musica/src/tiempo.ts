// Hora de Madrid y semanas ISO-8601, sin depender de los datos de zonas del runtime
// (Intl/timeZone no se garantiza igual en Workers, en Node y en los tests): se aplica la
// regla de la UE, que cambia a las 01:00 UTC del último domingo de marzo y de octubre.

const MINUTO = 60_000;
const DIA = 86_400_000;

/** Epoch ms (UTC) de las 01:00 UTC del último domingo del mes (0 = enero). */
function ultimoDomingo01Utc(anio: number, mes: number): number {
  const ultimo = new Date(Date.UTC(anio, mes + 1, 0)); // último día del mes
  const dia = ultimo.getUTCDate() - ultimo.getUTCDay(); // getUTCDay: domingo = 0
  return Date.UTC(anio, mes, dia, 1);
}

/** Minutos de diferencia de Madrid con UTC en ese instante: 120 en verano, 60 en invierno. */
export function offsetMadrid(utc: Date): 60 | 120 {
  const t = utc.getTime();
  const anio = utc.getUTCFullYear();
  const inicio = ultimoDomingo01Utc(anio, 2);
  const fin = ultimoDomingo01Utc(anio, 9);
  return t >= inicio && t < fin ? 120 : 60;
}

/** Date cuyos campos UTC (getUTC*) son la hora local de Madrid. Leer solo con getUTC*. */
export function horaMadrid(utc: Date): Date {
  return new Date(utc.getTime() + offsetMadrid(utc) * MINUTO);
}

function formatoId(anio: number, semana: number): string {
  return `${anio}-W${String(semana).padStart(2, "0")}`;
}

/** Semana ISO (lunes a domingo; la 1 contiene el primer jueves) de una fecha local en ms "UTC falsos". */
function semanaIsoDeLocal(local: Date): { anio: number; semana: number } {
  const dia = Date.UTC(local.getUTCFullYear(), local.getUTCMonth(), local.getUTCDate());
  const diaSemana = (new Date(dia).getUTCDay() + 6) % 7; // lunes = 0
  const jueves = new Date(dia + (3 - diaSemana) * DIA); // el jueves de esa semana fija el año ISO
  const anio = jueves.getUTCFullYear();
  const dias = Math.round((jueves.getTime() - Date.UTC(anio, 0, 1)) / DIA);
  return { anio, semana: Math.floor(dias / 7) + 1 };
}

function semanasDelAnio(anio: number): number {
  // El 28 de diciembre siempre cae en la última semana ISO del año.
  return semanaIsoDeLocal(new Date(Date.UTC(anio, 11, 28))).semana;
}

/** Id de la semana ISO de la fecha local de Madrid: "2026-W45". */
export function semanaDe(utc: Date): string {
  const { anio, semana } = semanaIsoDeLocal(horaMadrid(utc));
  return formatoId(anio, semana);
}

function leerId(id: string): { anio: number; semana: number } {
  const m = /^(\d{4})-W(\d{2})$/.exec(id);
  if (!m) throw new Error(`Id de semana no válido: ${id}`);
  const anio = Number(m[1]);
  const semana = Number(m[2]);
  if (semana < 1 || semana > semanasDelAnio(anio)) {
    throw new Error(`Id de semana no válido: ${id}`);
  }
  return { anio, semana };
}

/** Epoch ms (UTC) del lunes 00:00 de Madrid que cierra esa semana (= empieza la siguiente). */
export function finDeSemana(id: string): number {
  const { anio, semana } = leerId(id);
  // El 4 de enero está siempre en la semana 1: de ahí sale su lunes.
  const cuatroEne = Date.UTC(anio, 0, 4);
  const lunesSemana1 = cuatroEne - ((new Date(cuatroEne).getUTCDay() + 6) % 7) * DIA;
  const lunesSiguiente = lunesSemana1 + semana * 7 * DIA; // medianoche local, como "UTC falso"
  // Pasar de hora local a UTC: se prueba con verano y se confirma con la regla.
  const conVerano = lunesSiguiente - 120 * MINUTO;
  return offsetMadrid(new Date(conVerano)) === 120 ? conVerano : lunesSiguiente - 60 * MINUTO;
}

/** Id de la semana anterior; en W01 salta a la última del año previo (52 o 53). */
export function semanaAnterior(id: string): string {
  const { anio, semana } = leerId(id);
  if (semana > 1) return formatoId(anio, semana - 1);
  return formatoId(anio - 1, semanasDelAnio(anio - 1));
}
