# Servidor de Música

Cloudflare Worker (TypeScript) con base de datos D1 para ForoPlus Música: el feed de canciones de 24 horas, con cuentas verificadas, corazones y moderación. El código es público para que se vea qué se hace con los datos; los secretos van solo con `wrangler secret`.

- Probar: `npm ci && npm run check && npm test` (todo local, sin cuenta de Cloudflare).
- Desplegar: hace falta la cuenta de Cloudflare de ForoPlus (`account_id` y `database_id` ya están puestos en `wrangler.jsonc`: son identificadores, no secretos) y se hace a mano, nunca desde CI.

## Rutas

Cada ruta declara su acceso y el router lo comprueba antes de llamar al manejador (denegar por defecto).

| Ruta | Acceso | Para qué |
| --- | --- | --- |
| `GET /v1/salud` | pública | Latido del servicio. |
| `GET /v1/feed` | pública | El feed (Recientes o Top) y la más querida de la semana pasada. No lleva datos de quien lo pide. |
| `POST /v1/verificacion/reto`, `POST /v1/verificacion/comprobar` | pública | Verificar la cuenta de ForoCoches con el coche de prueba. |
| `GET /v1/yo` | cuenta | La cuenta del token. |
| `POST /v1/publicaciones`, `GET /v1/publicaciones/mias`, `DELETE /v1/publicaciones/:id` | cuenta | Compartir una canción con un texto de hasta 100 caracteres; ver y borrar las propias. |
| `POST` / `DELETE /v1/publicaciones/:id/corazon` | cuenta | Dar o quitar un corazón. |
| `GET /v1/publicaciones/:id/corazones` | cuenta | Quién dio corazón: solo responde al autor. |
| `POST /v1/denuncias` | cuenta | Denunciar una publicación o una canción. Con 3 cuentas distintas la publicación se oculta sola. |
| `DELETE /v1/cuenta` | cuenta (también baneada) | Borrar la cuenta y todo lo suyo, en una transacción. |
| `/v1/lector/*`, `/v1/conversor/*` | secreto de máquina | Los servicios del VPS (lector de fichas y [conversor](../conversor/README.md)): el VPS llama al Worker, nunca al revés. |
| `/v1/admin/*` | secreto de admin | Denuncias, ocultar, banear, resolver y fusionar canciones, a mano con `curl`. |

## Tareas programadas

- Cada 15 minutos: caduca el feed. Borra las publicaciones de más de 24 horas con sus corazones y guarda, de cada semana, la más querida.
- Cada día a las 03:30 (UTC): borra retos y tokens caducados, contadores de límites de más de 2 días, las más queridas de más de 8 semanas y da por perdidas las conversiones atascadas.

## Qué se guarda

Cuentas (uid de FC solo como huella irreversible), publicaciones y corazones de las últimas 24 horas, la más querida de cada semana (8 semanas), denuncias, catálogo de canciones y contadores de límites con claves en huella. No hay registros de peticiones. Todo el detalle, en la [política de privacidad](../../docs/privacy-policy.html).
