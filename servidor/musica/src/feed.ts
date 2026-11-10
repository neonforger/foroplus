// Constantes del feed de canciones de 24 h. En esta tarea solo constantes: la lógica llega después.

/** Caracteres máximos del texto de una publicación (tras `trim`). */
export const TEXTO_MAX = 100;
/** Publicaciones por cuenta en 24 h; cuentan aunque se borren. */
export const PUBLICACIONES_DIA = 6;
/** Vida de una publicación desde `creada`. */
export const VIDA_MS = 86_400_000;
/**
 * Si la conversión a YouTube no ha terminado pasado este tiempo, la publicación se enseña igualmente
 * (con la tarjeta nativa): un fork caído no puede dejarla "publicando" para siempre.
 */
export const ESPERA_CONVERSION_MS = 8000;
/** Denuncias de cuentas distintas que ocultan una publicación sola. */
export const AUTO_OCULTAR = 3;
/** Tamaño de página del feed. */
export const POR_PAGINA = 20;
