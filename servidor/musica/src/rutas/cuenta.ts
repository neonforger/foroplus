// Denuncias y borrar la cuenta (antes vivían junto a las playlists públicas, que ya no existen).
// La cuenta sale SIEMPRE del token y entra en cada consulta de datos propios.
import type { Contexto, OpcionesApp, Ruta } from "../app";
import { yo } from "../auth";
import { nuevoId } from "../cripto";
import { cuerpoJson, error, json } from "../http";
import { permitido } from "../limites";

const MAX_CUERPO = 4096;
const MAX_MOTIVO = 300;
const DENUNCIAS_POR_DIA = 5;
const DIA_S = 86_400;
const SIN_CACHE = { "Cache-Control": "no-store" };

// ---- Denuncias ----

async function denunciar(c: Contexto): Promise<Response> {
  const cuenta = yo(c);
  const d = c.deps;
  const cuerpo = await cuerpoJson(c.req, MAX_CUERPO);
  const tipo = cuerpo?.tipo;
  const objetivo = cuerpo?.objetivo;
  const motivo = cuerpo?.motivo;
  if (tipo !== "publicacion" && tipo !== "cancion") return error("tipo", 400);
  if (typeof objetivo !== "string") return error("objetivo", 400);
  if (typeof motivo !== "string" || [...motivo].length > MAX_MOTIVO) return error("motivo", 400);
  const ahora = d.ahora();
  // Una publicación caducada u oculta responde 404 como una que no existe: no se confirma que estuvo.
  const existe =
    tipo === "publicacion"
      ? await d.db
          .prepare("SELECT 1 AS x FROM publicaciones WHERE id = ? AND caduca > ? AND oculta = 0")
          .bind(objetivo, ahora.getTime())
          .first()
      : await d.db.prepare("SELECT 1 AS x FROM canciones WHERE id = ?").bind(objetivo).first();
  if (!existe) return error("no_existe", 404);
  // Repetir la misma denuncia no crea otra fila ni gasta cupo.
  const repetida = await d.db
    .prepare("SELECT 1 AS x FROM denuncias WHERE tipo = ? AND objetivo = ? AND cuenta = ?")
    .bind(tipo, objetivo, cuenta.id)
    .first();
  if (repetida) return json({ ok: true }, 200, SIN_CACHE);
  if (!(await permitido(d.db, "denuncia:" + cuenta.id, DIA_S, DENUNCIAS_POR_DIA, ahora))) return error("limite", 429);
  await d.db
    .prepare(
      "INSERT INTO denuncias (id, tipo, objetivo, cuenta, motivo, creada, resuelta) " +
        "SELECT ?1, ?2, ?3, ?4, ?5, ?6, 0 WHERE NOT EXISTS " +
        "(SELECT 1 FROM denuncias WHERE tipo = ?2 AND objetivo = ?3 AND cuenta = ?4)",
    )
    .bind(nuevoId(ahora), tipo, objetivo, cuenta.id, motivo.trim(), ahora.getTime())
    .run();
  return json({ ok: true }, 200, SIN_CACHE);
}

// ---- Borrar la cuenta ----

async function borrarCuenta(c: Contexto): Promise<Response> {
  const cuenta = yo(c);
  const db = c.deps.db;
  const yoId = cuenta.id;
  const suyas = "SELECT id FROM publicaciones WHERE cuenta = ?1";
  // Todo en UN batch (una transacción): o se va todo o no se va nada. El orden importa: lo que
  // depende de la cuenta o de sus publicaciones se borra antes que ellas.
  await db.batch([
    // Los corazones que dio a publicaciones de otros: el contador de esas publicaciones se recuenta
    // (sin los suyos), no se resta, para que dos borrados a la vez den el mismo número exacto.
    db
      .prepare(
        "UPDATE publicaciones SET corazones = (SELECT COUNT(*) FROM corazones h WHERE h.publicacion = publicaciones.id AND h.cuenta <> ?1) " +
          "WHERE id IN (SELECT publicacion FROM corazones WHERE cuenta = ?1)",
      )
      .bind(yoId),
    db.prepare("DELETE FROM corazones WHERE cuenta = ?1").bind(yoId),
    db.prepare(`DELETE FROM corazones WHERE publicacion IN (${suyas})`).bind(yoId),
    db.prepare(`DELETE FROM denuncias WHERE tipo = 'publicacion' AND objetivo IN (${suyas})`).bind(yoId),
    db.prepare("DELETE FROM publicaciones WHERE cuenta = ?1").bind(yoId),
    db.prepare("DELETE FROM denuncias WHERE cuenta = ?1").bind(yoId),
    // Retos vivos de su uid (guardan el uid en claro): fuera.
    db.prepare("DELETE FROM retos WHERE uid_hmac = (SELECT uid_hmac FROM cuentas WHERE id = ?1)").bind(yoId),
    db.prepare("DELETE FROM limites WHERE clave IN ('tarjeta:' || ?1, 'denuncia:' || ?1)").bind(yoId),
    db.prepare("DELETE FROM tokens WHERE cuenta = ?1").bind(yoId),
    // Una cuenta baneada NO desaparece del todo: queda una LÁPIDA con solo id, uid_hmac y baneada = 1
    // (sin nombre, registro ni fechas) para que volver a verificar la misma cuenta de FC siga dando
    // 403 baneada; si no, borrar la cuenta levantaría el baneo. Se avisa en la política de privacidad
    // como "un registro mínimo para hacer cumplir los baneos".
    db.prepare("DELETE FROM cuentas WHERE id = ?1 AND COALESCE(baneada, 0) = 0").bind(yoId),
    db.prepare("UPDATE cuentas SET nombre = '', registro_fc = NULL, creada = NULL, ultima_verif = NULL WHERE id = ?1").bind(yoId),
  ]);
  return json({ ok: true }, 200, SIN_CACHE);
}

export function rutasCuenta(_o: OpcionesApp = {}): Ruta[] {
  return [
    ["POST", "/v1/denuncias", denunciar, "cuenta"],
    ["DELETE", "/v1/cuenta", borrarCuenta, "cuenta_o_baneada"],
  ];
}
