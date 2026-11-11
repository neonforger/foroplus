"""Conversor de Música: canciones (Deezer) -> id de Spotify, desde el VPS.

La app nunca habla con el VPS y el VPS no abre puertos: es este bucle quien llama al Worker
(GET pendientes cada 5 s si no hay trabajo, POST resultado, POST latido) con la cabecera X-Conversor.
"""
import json
import os
import sys
import threading
import time
import urllib.error
import urllib.request

from emparejar import elegir

API = os.environ.get("FOROPLUS_API", "").rstrip("/")
SECRETO = os.environ.get("CONVERSOR_SECRETO", "")
LATIDO_CADA_S = 6 * 3600
# Spotify: como mucho una búsqueda por segundo.
ENTRE_BUSQUEDAS_S = 1.0
PRUEBA_ISRC = "AUAP07900028"
PRUEBA = {"titulo": "Highway to Hell", "artista": "AC/DC"}
TIMEOUT_BUSQUEDA_S = 20
# El Worker contesta pendientes al instante (una consulta): con la cola vacía se vuelve a preguntar a
# los 5 s, y una petición que no vuelve en 15 s se da por perdida.
SONDEO_S = 5.0
TIMEOUT_PENDIENTES_S = 15
_ultima_busqueda = 0.0
_timeouts_seguidos = 0


def log(*a):
    print(time.strftime("%Y-%m-%d %H:%M:%S"), *a, flush=True)


def llamar(metodo: str, ruta: str, cuerpo: dict | None = None, timeout: int = 30):
    datos = json.dumps(cuerpo).encode() if cuerpo is not None else None
    peticion = urllib.request.Request(
        API + ruta, data=datos, method=metodo,
        headers={"X-Conversor": SECRETO, "Content-Type": "application/json", "User-Agent": "foroplus-conversor"},
    )
    with urllib.request.urlopen(peticion, timeout=timeout) as r:
        resp = r.read()
        return json.loads(resp) if resp else None


class TiempoAgotado(Exception):
    pass


def _buscar_spotify(consulta: str) -> list[dict]:
    from spotapi import Song

    r = Song().query_songs(consulta, limit=5)
    salida = []
    for it in r["data"]["searchV2"]["tracksV2"]["items"]:
        d = it["item"]["data"]
        uri = d.get("uri", "")
        if not uri.startswith("spotify:track:"):
            continue
        salida.append({
            "id": uri.split(":")[2],
            "titulo": d.get("name", ""),
            "artistas": [a["profile"]["name"] for a in d.get("artists", {}).get("items", [])],
        })
    return salida


def buscar(consulta: str) -> list[dict]:
    """Una búsqueda en Spotify con ritmo (1/s) y tope de tiempo.

    Sin tope, una búsqueda colgada paraba el bucle en silencio (proceso vivo, systemd sin reiniciarlo).
    Corre en un hilo aparte: si no vuelve a tiempo se abandona (hilo demonio) y cuenta como fallo.
    """
    global _ultima_busqueda, _timeouts_seguidos
    espera = _ultima_busqueda + ENTRE_BUSQUEDAS_S - time.monotonic()
    if espera > 0:
        time.sleep(espera)
    caja: dict = {}

    def trabajo():
        try:
            caja["ok"] = _buscar_spotify(consulta)
        except BaseException as e:  # noqa: BLE001
            caja["error"] = e

    hilo = threading.Thread(target=trabajo, daemon=True)
    hilo.start()
    hilo.join(TIMEOUT_BUSQUEDA_S)
    _ultima_busqueda = time.monotonic()
    if hilo.is_alive():
        _timeouts_seguidos += 1
        raise TiempoAgotado(f"la busqueda tardo mas de {TIMEOUT_BUSQUEDA_S} s")
    _timeouts_seguidos = 0
    if "error" in caja:
        raise caja["error"]
    return caja["ok"]


def comprobar_colgado():
    """Dos búsquedas seguidas colgadas: algo está roto de verdad, que systemd nos levante de nuevo."""
    if _timeouts_seguidos >= 2:
        log("dos busquedas colgadas seguidas: salgo para que systemd reinicie")
        sys.exit(1)


def convertir(c: dict) -> str | None:
    """Lanza excepción si SpotAPI falla (el llamador lo cuenta como motivo "error")."""
    buscado = {"titulo": c["titulo"], "artista": c["artista"]}
    if c.get("isrc"):
        hallado = elegir(buscado, buscar(f"isrc:{c['isrc']}"))
        if hallado:
            return hallado
    return elegir(buscado, buscar(f"{c['titulo']} {c['artista']}"))


def latido():
    try:
        # Solo vale si el emparejado reconoce la canción conocida (no basta con "hay resultados").
        ok, detalle = (True, "prueba ok") if elegir(PRUEBA, buscar(f"isrc:{PRUEBA_ISRC}")) else (False, "la prueba no emparejo")
    except Exception as e:  # noqa: BLE001
        ok, detalle = False, f"prueba: {type(e).__name__}: {e}"[:200]
    try:
        llamar("POST", "/v1/conversor/latido", {"ok": ok, "detalle": detalle})
    except Exception as e:  # noqa: BLE001
        log("latido no enviado:", e)
    log("latido", ok, detalle)
    comprobar_colgado()


def atender(c: dict):
    try:
        spotify = convertir(c)
        cuerpo = {"cancion": c["id"], "spotify": spotify, "motivo": None if spotify else "no_esta"}
    except Exception as e:  # noqa: BLE001
        log("error convirtiendo", c["id"], type(e).__name__, e)
        cuerpo = {"cancion": c["id"], "spotify": None, "motivo": "error"}
    try:
        llamar("POST", "/v1/conversor/resultado", cuerpo)
        log("resultado", c["id"], cuerpo["spotify"] or cuerpo["motivo"])
    except urllib.error.HTTPError as e:
        # 409 (ese Spotify ya es de otra) y 404 (borrada) no se reintentan.
        log("resultado rechazado", c["id"], e.code)
    except Exception as e:  # noqa: BLE001
        log("resultado no enviado", c["id"], type(e).__name__, e)
    comprobar_colgado()


def espera_tras(canciones: list) -> float:
    """Segundos hasta el siguiente GET pendientes: el Worker contesta al instante (sin petición larga),
    así que con la cola vacía se espera SONDEO_S; si vino trabajo, se vuelve a preguntar enseguida."""
    return 0.0 if canciones else SONDEO_S


def espera_tras_error(e: Exception) -> float:
    """Un tiempo agotado (Worker lento o, mientras siga desplegado, el viejo de petición larga de 20 s)
    se reintenta al ritmo normal; cualquier otro fallo (red caída, 5xx, 401) espera 30 s."""
    if isinstance(e, urllib.error.URLError) and isinstance(e.reason, TimeoutError):
        return SONDEO_S  # el tiempo agotado al conectar llega envuelto en URLError
    return SONDEO_S if isinstance(e, TimeoutError) else 30.0


def main():
    if not API or not SECRETO:
        log("faltan FOROPLUS_API o CONVERSOR_SECRETO")
        sys.exit(1)
    proximo_latido = 0.0
    while True:
        if time.monotonic() >= proximo_latido:
            latido()
            proximo_latido = time.monotonic() + LATIDO_CADA_S
        try:
            r = llamar("GET", "/v1/conversor/pendientes", timeout=TIMEOUT_PENDIENTES_S)
        except Exception as e:  # noqa: BLE001
            log("pendientes:", type(e).__name__, e)
            time.sleep(espera_tras_error(e))
            continue
        canciones = (r or {}).get("canciones", [])
        for c in canciones:
            atender(c)
        espera = espera_tras(canciones)
        if espera:
            time.sleep(espera)


if __name__ == "__main__":
    main()
