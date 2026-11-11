"""Emparejar una canción de Deezer con el resultado de Spotify (lógica pura, sin red).

Spotify no escribe los títulos como Deezer: "Highway to Hell - Remastered 2003", "Tema (feat. X)"... Se
compara el título y los artistas ya normalizados; con una búsqueda por ISRC vienen a veces dos ids de la
misma canción (single y álbum) y se coge el primero, que es el orden de relevancia de Spotify.
"""
import re
import unicodedata

_PARENTESIS = re.compile(r"\([^)]*\)|\[[^\]]*\]")
# "- Remastered 2011", "- 2009 Remaster", "- Live ...", "- Radio Edit", "- Single Version"
_COLA = re.compile(r"\s-\s.*(remaster|live|edit|version|mono|stereo|mix).*$")
_FEAT = re.compile(r"\s(feat\.?|ft\.?|featuring)\s.*$")
_FEAT_SEP = re.compile(r"\s(feat\.?|ft\.?|featuring)\s", re.I)
_SEPARADORES = re.compile(r"\s*(?:,|;|&|/|\sy\s|\sand\s|\sx\s)\s*")


def normal(s: str) -> str:
    s = unicodedata.normalize("NFKD", s or "")
    s = "".join(c for c in s if not unicodedata.combining(c)).lower()
    s = _PARENTESIS.sub(" ", s)
    s = _COLA.sub("", s)
    s = _FEAT.sub("", s)
    s = re.sub(r"[^a-z0-9]+", " ", s)
    return s.strip()


def _artistas(texto_o_lista) -> set[str]:
    piezas = [texto_o_lista] if isinstance(texto_o_lista, str) else list(texto_o_lista)
    salida: set[str] = set()
    for p in piezas:
        # El nombre entero cuenta ("AC/DC", "Simon & Garfunkel") y también cada pieza suelta.
        completo = normal(p)
        if completo:
            salida.add(completo)
        for sub in _SEPARADORES.split(_FEAT_SEP.sub(", ", p or "")):
            n = normal(sub)
            if n:
                salida.add(n)
    return salida


def elegir(buscado: dict, resultados: list[dict]) -> str | None:
    """buscado: {titulo, artista}; resultados: [{id, titulo, artistas: [str]}] en orden de Spotify."""
    titulo = normal(buscado.get("titulo", ""))
    if not titulo:
        return None
    nuestros = _artistas(buscado.get("artista", ""))
    for r in resultados:
        if normal(r.get("titulo", "")) == titulo and nuestros & _artistas(r.get("artistas", [])):
            return r["id"]
    return None
