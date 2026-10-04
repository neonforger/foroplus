# -*- coding: utf-8 -*-
"""
Regenera los iconos de lanzador desde el roto2 de 512 transparente.

Dos reglas que son el porqué de todo este fichero:
  · La zona segura de un icono ADAPTATIVO es un CÍRCULO de 66dp sobre 108dp, no un
    cuadrado. Un dibujo del 60% de ancho puede salirse igual por los picos, que es
    justo lo que pasaba: el círculo circunscrito del roto2 medía 75,7dp y en la 41 los
    lanzadores con máscara circular le cortaban la cabeza.
  · Los LEGACY se los come la máscara del lanzador, así que llevan la forma horneada
    (círculo y cuadrado redondeado) y el fondo relleno.

Uso:  python tools/gen_icons.py [ruta_del_png_fuente]

La fuente es el roto2 de 512 con fondo TRANSPARENTE. Vive fuera del repo (en el
Escritorio del dueño, junto a las variantes de color), así que si se ha movido hay que
pasarla por argumento.
"""
from PIL import Image
import numpy as np, sys

SRC = sys.argv[1] if len(sys.argv) > 1 else r"C:/Users/domen/Desktop/icones/icono_roto_512_transparente.png"
RES = "app/src/main/res"
BG = (255, 255, 255, 255)          # mismo blanco que el 512 de la ficha de Play

# 108dp de lienzo adaptativo; 66dp es la zona segura. 64 deja un respiro.
ADAPT = {"mdpi": 108, "hdpi": 162, "xhdpi": 216, "xxhdpi": 324, "xxxhdpi": 432}
LEGACY = {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}
SAFE_DP, CANVAS_DP = 64.0, 108.0


def contenido_cuadrado(path):
    """Recorta el dibujo a su contenido y lo centra en un lienzo cuadrado."""
    im = Image.open(path).convert("RGBA")
    a = np.array(im.split()[3])
    ys, xs = np.nonzero(a > 8)
    caja = (xs.min(), ys.min(), xs.max() + 1, ys.max() + 1)
    rec = im.crop(caja)
    lado = max(rec.size)
    out = Image.new("RGBA", (lado, lado), (0, 0, 0, 0))
    out.alpha_composite(rec, ((lado - rec.width) // 2, (lado - rec.height) // 2))
    return out


def radio_circunscrito(im):
    a = np.array(im.split()[3])
    ys, xs = np.nonzero(a > 8)
    c = (im.width - 1) / 2.0
    return float(np.sqrt((xs - c) ** 2 + (ys - c) ** 2).max())


BASE = contenido_cuadrado(SRC)
R_BASE = radio_circunscrito(BASE)


def escalado(diam_destino):
    """Escala BASE para que su círculo circunscrito mida diam_destino píxeles."""
    lado = max(1, int(round(diam_destino * BASE.width / (2 * R_BASE))))
    return BASE.resize((lado, lado), Image.LANCZOS)


def centrar(fondo, dibujo):
    fondo.alpha_composite(dibujo, ((fondo.width - dibujo.width) // 2,
                                   (fondo.height - dibujo.height) // 2))
    return fondo


hechos = []
for dens, S in ADAPT.items():
    # Foreground adaptativo: fondo transparente (lo pone <background>), dibujo dentro
    # del círculo de 64dp.
    lienzo = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    centrar(lienzo, escalado(S * SAFE_DP / CANVAS_DP))
    p = f"{RES}/mipmap-{dens}/ic_launcher_foreground.png"
    lienzo.save(p)
    hechos.append((p, S, round(radio_circunscrito(lienzo) * 2 / S * CANVAS_DP, 1)))

for dens, S in LEGACY.items():
    from PIL import ImageDraw
    # Cuadrado redondeado.
    m = Image.new("L", (S * 4, S * 4), 0)
    ImageDraw.Draw(m).rounded_rectangle((0, 0, S * 4 - 1, S * 4 - 1),
                                        radius=int(S * 4 * 0.22), fill=255)
    cuad = Image.new("RGBA", (S, S), BG)
    cuad.putalpha(m.resize((S, S), Image.LANCZOS))
    centrar(cuad, escalado(S * 0.76))
    cuad.save(f"{RES}/mipmap-{dens}/ic_launcher.png")

    # Círculo.
    m = Image.new("L", (S * 4, S * 4), 0)
    ImageDraw.Draw(m).ellipse((0, 0, S * 4 - 1, S * 4 - 1), fill=255)
    cir = Image.new("RGBA", (S, S), BG)
    cir.putalpha(m.resize((S, S), Image.LANCZOS))
    centrar(cir, escalado(S * 0.72))
    cir.save(f"{RES}/mipmap-{dens}/ic_launcher_round.png")

for p, S, dp in hechos:
    print(f"{p}  lienzo={S}  circulo circunscrito={dp}dp (zona segura 66dp)")
