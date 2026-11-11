from emparejar import elegir, normal

# Formas reales medidas en Spotify (búsqueda por ISRC AUAP07900028 y por título).
ISRC = [
    {"id": "1OIYK9Ya6EgwJ4fMngrPcK", "titulo": "Highway to Hell", "artistas": ["AC/DC"]},
    {"id": "2zYzyRzz6pRmhPzyfMEC8s", "titulo": "Highway to Hell", "artistas": ["AC/DC"]},
]
TITULO = [
    {"id": "2zYzyRzz6pRmhPzyfMEC8s", "titulo": "Highway to Hell", "artistas": ["AC/DC"]},
    {"id": "0Stellar00000000000000", "titulo": "Daredevil", "artistas": ["Stellar"]},
]
BUSCADO = {"titulo": "Highway to Hell", "artista": "AC/DC"}


def test_normal_quita_acentos_remaster_y_feat():
    assert normal("Canción (Live)") == "cancion"
    assert normal("Highway to Hell - Remastered 2003") == "highway to hell"
    assert normal("Tema - 2011 Remaster") == "tema"
    assert normal("Tema feat. Otro") == "tema"
    assert normal("Tema (feat. Otro) [Deluxe]") == "tema"
    assert normal("  ÁÉÍ  ") == "aei"


def test_elegir_coge_el_que_casa_titulo_y_artista():
    assert elegir(BUSCADO, TITULO) == "2zYzyRzz6pRmhPzyfMEC8s"
    assert elegir({"titulo": "Highway to Hell (Remastered)", "artista": "ac/dc"}, TITULO) == "2zYzyRzz6pRmhPzyfMEC8s"


def test_elegir_rechaza_mismo_titulo_de_otro_artista():
    otro = [{"id": "0Stellar00000000000000", "titulo": "Highway to Hell", "artistas": ["Stellar"]}]
    assert elegir(BUSCADO, otro) is None


def test_elegir_con_lista_vacia():
    assert elegir(BUSCADO, []) is None


def test_elegir_prefiere_el_primero_si_casan_dos():
    assert elegir(BUSCADO, ISRC) == "1OIYK9Ya6EgwJ4fMngrPcK"


def test_elegir_artista_suelto_entre_varios():
    r = [{"id": "x" * 22, "titulo": "Tema", "artistas": ["Uno", "Otro"]}]
    assert elegir({"titulo": "Tema", "artista": "Otro, Tercero"}, r) == "x" * 22
