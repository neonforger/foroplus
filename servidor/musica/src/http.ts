// Respuestas JSON del servidor. Siempre UTF-8 explícito: los nombres de canciones llevan tildes.
export function json(cuerpo: unknown, status = 200, cabeceras: Record<string, string> = {}): Response {
  return new Response(JSON.stringify(cuerpo), {
    status,
    headers: { "Content-Type": "application/json; charset=utf-8", ...cabeceras },
  });
}

/** Error uniforme: {"error": codigo, ...extra}. El código es lo que lee la app, no el texto. */
export function error(codigo: string, status: number, extra: Record<string, unknown> = {}): Response {
  return json({ error: codigo, ...extra }, status);
}
