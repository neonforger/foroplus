import time

import pytest

import conversor

BUENO = [{"id": "2zYzyRzz6pRmhPzyfMEC8s", "titulo": "Highway to Hell", "artistas": ["AC/DC"]}]
OTRO = [{"id": "0Stellar00000000000000", "titulo": "Daredevil", "artistas": ["Stellar"]}]
CANCION = {"id": "01J0000000000000000000000A", "titulo": "Highway to Hell", "artista": "AC/DC", "isrc": "AUAP07900028"}


@pytest.fixture(autouse=True)
def rapido(monkeypatch):
    monkeypatch.setattr(conversor, "ENTRE_BUSQUEDAS_S", 0)
    monkeypatch.setattr(conversor, "_timeouts_seguidos", 0)


def fingir(monkeypatch, respuestas):
    """respuestas: consulta -> lista | Exception | callable. Devuelve las consultas hechas."""
    hechas = []

    def falso(consulta):
        hechas.append(consulta)
        r = respuestas[consulta]
        if isinstance(r, Exception):
            raise r
        return r() if callable(r) else r

    monkeypatch.setattr(conversor, "_buscar_spotify", falso)
    return hechas


def test_isrc_sin_emparejar_cae_a_titulo(monkeypatch):
    hechas = fingir(monkeypatch, {"isrc:AUAP07900028": OTRO, "Highway to Hell AC/DC": BUENO})
    assert conversor.convertir(CANCION) == "2zYzyRzz6pRmhPzyfMEC8s"
    assert hechas == ["isrc:AUAP07900028", "Highway to Hell AC/DC"]


def test_isrc_que_empareja_no_busca_por_titulo(monkeypatch):
    hechas = fingir(monkeypatch, {"isrc:AUAP07900028": BUENO})
    assert conversor.convertir(CANCION) == "2zYzyRzz6pRmhPzyfMEC8s"
    assert hechas == ["isrc:AUAP07900028"]


def test_sin_isrc_busca_directo_por_titulo(monkeypatch):
    hechas = fingir(monkeypatch, {"Highway to Hell AC/DC": BUENO})
    assert conversor.convertir({**CANCION, "isrc": None}) == "2zYzyRzz6pRmhPzyfMEC8s"
    assert hechas == ["Highway to Hell AC/DC"]


def test_excepcion_de_spotapi_manda_motivo_error(monkeypatch):
    fingir(monkeypatch, {"isrc:AUAP07900028": RuntimeError("roto")})
    enviados = []
    monkeypatch.setattr(conversor, "llamar", lambda m, r, c=None, timeout=30: enviados.append((r, c)))
    conversor.atender(CANCION)
    assert enviados == [("/v1/conversor/resultado", {"cancion": CANCION["id"], "spotify": None, "motivo": "error"})]


def test_sin_resultado_manda_no_esta(monkeypatch):
    fingir(monkeypatch, {"isrc:AUAP07900028": [], "Highway to Hell AC/DC": []})
    enviados = []
    monkeypatch.setattr(conversor, "llamar", lambda m, r, c=None, timeout=30: enviados.append(c))
    conversor.atender(CANCION)
    assert enviados == [{"cancion": CANCION["id"], "spotify": None, "motivo": "no_esta"}]


def test_busqueda_colgada_cuenta_como_error(monkeypatch):
    monkeypatch.setattr(conversor, "TIMEOUT_BUSQUEDA_S", 0.1)
    fingir(monkeypatch, {"isrc:AUAP07900028": lambda: time.sleep(2)})
    enviados = []
    monkeypatch.setattr(conversor, "llamar", lambda m, r, c=None, timeout=30: enviados.append(c))
    conversor.atender(CANCION)
    assert enviados[0]["motivo"] == "error"
    assert conversor._timeouts_seguidos == 1


def test_dos_busquedas_colgadas_seguidas_terminan_el_proceso(monkeypatch):
    monkeypatch.setattr(conversor, "TIMEOUT_BUSQUEDA_S", 0.1)
    fingir(monkeypatch, {"isrc:AUAP07900028": lambda: time.sleep(2)})
    monkeypatch.setattr(conversor, "llamar", lambda *a, **k: None)
    conversor.atender(CANCION)
    with pytest.raises(SystemExit) as e:
        conversor.atender(CANCION)
    assert e.value.code == 1


def test_una_busqueda_buena_reinicia_la_cuenta_de_colgadas(monkeypatch):
    monkeypatch.setattr(conversor, "_timeouts_seguidos", 1)
    fingir(monkeypatch, {"isrc:AUAP07900028": BUENO})
    conversor.convertir(CANCION)
    assert conversor._timeouts_seguidos == 0


def test_latido_solo_ok_si_empareja_la_cancion_conocida(monkeypatch):
    enviados = []
    monkeypatch.setattr(conversor, "llamar", lambda m, r, c=None, timeout=30: enviados.append(c))
    fingir(monkeypatch, {"isrc:AUAP07900028": OTRO})
    conversor.latido()
    assert enviados[-1]["ok"] is False
    fingir(monkeypatch, {"isrc:AUAP07900028": BUENO})
    conversor.latido()
    assert enviados[-1]["ok"] is True


def test_respuesta_vacia_rapida_hace_pausa(monkeypatch):
    pausas = []
    monkeypatch.setattr(conversor.time, "sleep", pausas.append)
    conversor.pausa_si_vacia([], time.monotonic())
    assert len(pausas) == 1 and 0.5 < pausas[0] <= 1.0
    conversor.pausa_si_vacia([CANCION], time.monotonic())
    conversor.pausa_si_vacia([], time.monotonic() - 5)
    assert len(pausas) == 1
