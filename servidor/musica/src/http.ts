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

/**
 * Cuerpo JSON de un objeto, leyendo como mucho `maxBytes`: las rutas públicas no pueden gastar
 * memoria ni los 10 ms de CPU del plan gratuito en un cuerpo enorme. Se lee por trozos y se corta
 * en cuanto se pasa (Content-Length puede faltar o mentir). null = vacío, demasiado grande, no
 * JSON o no un objeto.
 */
export async function cuerpoJson(req: Request, maxBytes: number): Promise<Record<string, unknown> | null> {
  if (!req.body) return null;
  const lector = req.body.getReader();
  const trozos: Uint8Array[] = [];
  let total = 0;
  for (;;) {
    const { done, value } = await lector.read();
    if (done) break;
    total += value.byteLength;
    if (total > maxBytes) {
      await lector.cancel();
      return null;
    }
    trozos.push(value);
  }
  const bytes = new Uint8Array(total);
  let pos = 0;
  for (const t of trozos) {
    bytes.set(t, pos);
    pos += t.byteLength;
  }
  try {
    const v: unknown = JSON.parse(new TextDecoder().decode(bytes));
    return v !== null && typeof v === "object" && !Array.isArray(v) ? (v as Record<string, unknown>) : null;
  } catch {
    return null;
  }
}
