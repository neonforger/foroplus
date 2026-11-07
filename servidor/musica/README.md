# Servidor de Música

Cloudflare Worker (TypeScript) con base de datos D1 para ForoPlus Música: cuentas verificadas, votos semanales y playlists públicas. El código es público para que se vea qué se hace con los datos; los secretos van solo con `wrangler secret`.

- Probar: `npm ci && npm run check && npm test` (todo local, sin cuenta de Cloudflare).
- Desplegar: hace falta la cuenta de Cloudflare de ForoPlus (en `wrangler.jsonc`, `account_id` y `database_id` están como PENDIENTE a propósito) y se hace a mano, nunca desde CI.
