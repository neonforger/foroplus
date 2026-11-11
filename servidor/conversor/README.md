# Conversor de Música

Servicio que corre en el VPS y convierte las canciones del feed de Música (Deezer) a su id de Spotify,
para que la app pueda enseñar la tarjeta con reproductor. Ni la app habla con él ni él abre puertos:
es él quien llama al Worker (`servidor/musica`) con la cabecera `X-Conversor`.

Bucle: `GET /v1/conversor/pendientes` (el Worker contesta al instante; con la cola vacía se vuelve a
preguntar a los 5 s, y una petición que no vuelve en 15 s se da por perdida) -> por canción, búsqueda en Spotify por ISRC y,
si no casa, por título y artista -> `POST /v1/conversor/resultado`. Latido al arrancar y cada 6 h con una
conversión de prueba fija (`POST /v1/conversor/latido`). Como mucho una búsqueda por segundo.

- `emparejar.py`: lógica pura (normalizar títulos, elegir el resultado que casa título y artista). Tests en `test_emparejar.py`.
- `conversor.py`: el bucle.

## Spotify

Usa un **fork de [SpotAPI](https://github.com/Aran404/SpotAPI)** (licencia GPL-3.0) en
`neonforger/SpotAPI`, fijado por commit en `requirements.txt`. Para actualizarlo: cambiar el commit ahí,
reconstruir la imagen y reiniciar el servicio.

## Tests

```bash
docker run --rm -v "$PWD":/w -w /w python:3.12-slim sh -c "pip install -q pytest && python -m pytest -q"
```

## Despliegue

1. Crear `/etc/foroplus/conversor.env` (root, `chmod 600`) con `FOROPLUS_API=<url del Worker>` y
   `CONVERSOR_SECRETO=<el mismo secreto que el Worker>`. Fuera del repo.
2. Copiar esta carpeta al VPS y construir: `docker build -t foroplus-conversor .`
3. Instalar la unidad: `sudo cp foroplus-conversor.service /etc/systemd/system/`, luego
   `sudo systemctl daemon-reload && sudo systemctl enable --now foroplus-conversor`.
4. Comprobar: `journalctl -u foroplus-conversor -n 30` y, con el secreto de admin, `GET /v1/admin/conversor`
   debe mostrar el latido con `ok: true`.

Actualizar código: copiar, `docker build` de nuevo y `sudo systemctl restart foroplus-conversor`.
