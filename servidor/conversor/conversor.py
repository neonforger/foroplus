"""Conversor de Música: canciones (Deezer) -> id de Spotify, desde el VPS.

La app nunca habla con el VPS y el VPS no abre puertos: es este bucle quien llama al Worker
(GET pendientes con petición larga, POST resultado, POST latido) con la cabecera X-Conversor.
"""
import json
import os
import sys
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
_ultima_busqueda = 0.0


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


def buscar(consulta: str) -> list[dict]:
    """Una búsqueda en Spotify -> [{id, titulo, artistas}] en su orden (respetando el ritmo)."""
    global _ultima_busqueda
    from spotapi import Song

    espera = _ultima_busqueda + ENTRE_BUSQUEDAS_S - time.monotonic()
    if espera > 0:
        time.sleep(espera)
    try:
        r = Song().query_songs(consulta, limit=5)
    finally:
        _ultima_busqueda = time.monotonic()
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
        ok, detalle = (True, "prueba ok") if buscar(f"isrc:{PRUEBA_ISRC}") else (False, "la prueba no devolvio resultados")
    except Exception as e:  # noqa: BLE001
        ok, detalle = False, f"prueba: {type(e).__name__}: {e}"[:200]
    try:
        llamar("POST", "/v1/conversor/latido", {"ok": ok, "detalle": detalle})
    except Exception as e:  # noqa: BLE001
        log("latido no enviado:", e)
    log("latido", ok, detalle)


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
            r = llamar("GET", "/v1/conversor/pendientes", timeout=30)
        except Exception as e:  # noqa: BLE001
            log("pendientes:", type(e).__name__, e)
            time.sleep(30)
            continue
        for c in (r or {}).get("canciones", []):
            atender(c)


if __name__ == "__main__":
    main()
