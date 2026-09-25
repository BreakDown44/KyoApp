#!/usr/bin/env python3
"""ScanGolf – Testbilder erzeugen.

Alle Bilder gehen nach testbilder/out/ (von git ignoriert), dazu manifest.json mit
den Sollwerten. Die Java-Tests lesen das Manifest.

Varianten:
  * Beispiel bei 150/200/300/600 dpi (pdftoppm aus scangolf-beispiel.pdf)
  * gedreht (90/180/270), leicht schief, verschoben
  * Rauschen, JPEG q60, unscharf, grauer/gelblicher Papierton, zu dunkel, Kombinationen
  * eigene Bahnen (schräge Wände, dünne Linien, Lücken, gewundener Parcours, Wasser)
  * Negativfälle (Start fehlt, zwei Löcher, eingemauert, Start in Wand, leeres Blatt,
    Foto ohne Marken, abgeschnittene Ecke, ...)
  * leere Vorlage in vielen Varianten (Raster/Rahmen dürfen nie Wand werden)
"""

import copy
import json
import math
import os
import random
import shutil
import subprocess
import sys

from PIL import Image, ImageChops, ImageDraw, ImageFilter, ImageOps

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
sys.path.insert(0, os.path.join(ROOT, "vorlage"))
import generate as tpl  # noqa: E402

OUT = os.path.join(HERE, "out")
VORLAGE_OUT = os.path.join(ROOT, "vorlage", "out")

MANIFEST = []


# ---------------------------------------------------------------- Hilfen

def ellipse(cx, cy, rx, ry, n=40, wobble=0.0, seed=0):
    rnd = random.Random(seed)
    ph = rnd.uniform(0, 6.28)
    pts = []
    for k in range(n):
        a = 2 * math.pi * k / n
        f = 1 + wobble * math.sin(3 * a + ph)
        pts.append((cx + rx * math.cos(a) * f, cy + ry * math.sin(a) * f))
    return pts


def cairo_to_pil(surf):
    w, h = surf.get_width(), surf.get_height()
    buf = bytes(surf.get_data())
    img = Image.frombuffer("RGBA", (w, h), buf, "raw", "BGRA", surf.get_stride(), 1)
    return img.convert("RGB")


def render(drawing, dpi=300):
    return cairo_to_pil(tpl.render_surface(dpi, drawing))


def save(img, name, entry, fmt="PNG", **kw):
    path = os.path.join(OUT, name)
    if fmt == "JPEG":
        img.save(path, "JPEG", **kw)
    else:
        img.save(path, "PNG", compress_level=kw.get("compress_level", 3))
    entry = dict(entry)
    entry["file"] = name
    entry["width"], entry["height"] = img.size
    MANIFEST.append(entry)
    print("  %-40s %dx%d" % (name, img.size[0], img.size[1]))


def positive(drawing, transform, **extra):
    e = {"expect": "ok", "group": "positiv", "transform": transform,
         "truth": tpl.ground_truth(drawing)}
    e.update(extra)
    return e


def negative(code, transform, drawing=None, **extra):
    e = {"expect": "error", "error": code, "group": "negativ", "transform": transform,
         "truth": tpl.ground_truth(drawing) if drawing else None}
    e.update(extra)
    return e


def empty_entry(transform):
    return {"expect": "error", "error": "FIELD_EMPTY", "group": "leer", "transform": transform,
            "truth": None}


# ---------------------------------------------------------------- Bildstörungen

def rotate_exact(img, quarter_turns_cw):
    ops = {1: Image.Transpose.ROTATE_270, 2: Image.Transpose.ROTATE_180, 3: Image.Transpose.ROTATE_90}
    return img.transpose(ops[quarter_turns_cw])


def skew(img, deg, expand):
    return img.rotate(deg, resample=Image.Resampling.BICUBIC, expand=expand, fillcolor=(255, 255, 255))


def shift(img, dx, dy):
    out = Image.new("RGB", img.size, (255, 255, 255))
    out.paste(img, (dx, dy))
    return out


def add_noise(img, sigma, seed=1):
    random.seed(seed)
    w, h = img.size
    chans = []
    for c in img.split():
        noise = Image.effect_noise((w, h), sigma)
        chans.append(ImageChops.add(c, noise, 1.0, -128))
    return Image.merge("RGB", chans)


def blur(img, radius):
    return img.filter(ImageFilter.GaussianBlur(radius))


def tint(img, rgb):
    return ImageChops.multiply(img, Image.new("RGB", img.size, rgb))


def darken(img, gamma=1.35, gain=0.78):
    lut = [min(255, int(round(255 * ((v / 255.0) ** gamma) * gain))) for v in range(256)]
    return img.point(lut * 3)


def jpeg_roundtrip(img, quality):
    import io
    b = io.BytesIO()
    img.save(b, "JPEG", quality=quality)
    b.seek(0)
    return Image.open(b).convert("RGB")


def random_photo(w, h, seed):
    """Buntes 'Foto' ohne Passmarken (Verläufe, Formen, Rauschen, ein Fake-Suchmuster)."""
    rnd = random.Random(seed)
    grad = Image.linear_gradient("L").resize((w, h))
    img = Image.merge("RGB", (grad, ImageOps.invert(grad), grad.rotate(90).resize((w, h))))
    d = ImageDraw.Draw(img)
    for _ in range(60):
        x0, y0 = rnd.randrange(w), rnd.randrange(h)
        x1, y1 = x0 + rnd.randrange(40, w // 3), y0 + rnd.randrange(40, h // 3)
        col = tuple(rnd.randrange(256) for _ in range(3))
        if rnd.random() < 0.5:
            d.ellipse([x0, y0, x1, y1], fill=col)
        else:
            d.rectangle([x0, y0, x1, y1], fill=col)
    # ein einzelnes QR-ähnliches Suchmuster (darf allein nicht als Blatt gelten)
    m = 18
    x, y = w // 2, h // 3
    d.rectangle([x, y, x + 7 * m, y + 7 * m], fill=(0, 0, 0))
    d.rectangle([x + m, y + m, x + 6 * m, y + 6 * m], fill=(255, 255, 255))
    d.rectangle([x + 2 * m, y + 2 * m, x + 5 * m, y + 5 * m], fill=(0, 0, 0))
    return add_noise(img, 10, seed)


# ---------------------------------------------------------------- eigene Bahnen

EX = tpl.EXAMPLE


def variant(base, **changes):
    d = copy.deepcopy(base)
    d.update(changes)
    return d


SCHRAEG = {
    "id": "schraeg", "seed": 11, "wobble": 0.3,
    "name_text": "Erika", "lane_text": "Schräglage", "par": [3],
    "walls": [
        {"pts": [(50.0, 1.0), (110.0, 90.0)], "w": 1.4},
        {"pts": [(90.0, 143.0), (160.0, 54.0)], "w": 1.4},
        {"pts": [(150.0, 1.0), (230.0, 80.0)], "w": 1.4},
        {"pts": [(170.0, 143.0), (200.0, 110.0)], "w": 1.4},
    ],
    "start": [(20.0, 20.0, 4.5)],
    "hole": [(220.0, 124.0, 4.0)],
}

DUENN = {
    "id": "duenn", "seed": 12, "wobble": 0.2,
    "name_text": "Fineliner-Fritz", "lane_text": None, "par": [3],
    "walls": [
        {"pts": [(80.0, 1.0), (80.0, 100.0)], "w": 0.3},
        {"pts": [(160.0, 143.0), (160.0, 44.0)], "w": 0.3},
        {"pts": [(100.0, 30.0), (140.0, 30.0)], "w": 0.5},
        {"pts": [(185.0, 20.0), (215.0, 50.0)], "w": 0.3},
    ],
    "start": [(20.0, 72.0, 4.0)],
    "hole": [(220.0, 72.0, 4.0)],
}

LUECKEN = {
    "id": "luecken", "seed": 13, "wobble": 0.25,
    "name_text": "Lücke", "lane_text": "Aussetzer", "par": [4],
    "walls": [
        {"pts": [(70.0, 143.0), (70.0, 40.0)], "w": 1.2,
         "gaps": {"pattern": [10.0, 2.0], "offset": 3.0}},
        {"pts": [(110.0, 80.0), (239.0, 80.0)], "w": 1.2,
         "gaps": {"pattern": [9.0, 1.5], "offset": 1.0}},
        {"pts": [(140.0, 1.0), (180.0, 50.0)], "w": 1.2,
         "gaps": {"pattern": [12.0, 2.5], "offset": 5.0}},
    ],
    "start": [(25.0, 120.0, 4.5)],
    "hole": [(215.0, 25.0, 4.0)],
}


def _serpentine():
    walls = []
    for i in range(5):
        x = 40.0 + 40.0 * i
        if i % 2 == 0:
            walls.append({"pts": [(x, 1.0), (x + 1.5, 55.0), (x - 1.0, 110.0)], "w": 1.1})
        else:
            walls.append({"pts": [(x, 143.0), (x - 1.5, 90.0), (x + 1.0, 34.0)], "w": 1.1})
    return walls


GEWUNDEN = {
    "id": "gewunden", "seed": 14, "wobble": 0.5,
    "name_text": "Schlangenbeschwörer", "lane_text": "Kurvenreich", "par": [5],
    "walls": _serpentine(),
    "water": [{"poly": ellipse(100.0, 62.0, 9.0, 13.0, seed=3), "style": "hatch"}],
    "start": [(20.0, 124.0, 4.5)],
    "hole": [(222.0, 25.0, 4.0)],
}

WASSER = {
    "id": "wasser", "seed": 15, "wobble": 0.3,
    "name_text": "Nemo", "lane_text": "Seenplatte", "par": [2],
    "walls": [],
    "water": [
        {"poly": ellipse(80.0, 40.0, 22.0, 15.0, wobble=0.05, seed=1), "style": "hatch"},
        {"poly": ellipse(120.0, 105.0, 18.0, 14.0, wobble=0.05, seed=2), "style": "outline"},
        {"poly": ellipse(170.0, 50.0, 15.0, 18.0, wobble=0.05, seed=4), "style": "fill"},
    ],
    "start": [(20.0, 72.0, 4.5)],
    "hole": [(220.0, 72.0, 4.0)],
}

START_NAH_WAND = variant(EX, id="start-nah-wand",
                         start=[(58.0, 118.0, 3.0)])   # Mitte nur ~3 mm neben der Wand bei x=62

NEG_START_IN_WAND = variant(EX, id="start-in-wand", start=[(40.0, 75.0, 5.0)],
                            walls_over=[{"pts": [(40.0, 40.0), (40.0, 110.0)], "w": 4.0}])
NEG_LOCH_IN_WAND = variant(EX, id="loch-in-wand", hole=[(210.0, 30.0, 5.0)],
                           walls_over=[{"pts": [(195.0, 30.0), (225.0, 30.0)], "w": 4.0}])
NEG_LOCH_EINGEMAUERT = variant(EX, id="loch-eingemauert")
NEG_LOCH_EINGEMAUERT["walls"] = EX["walls"] + [
    {"pts": [(193.0, 14.0), (227.0, 13.0), (228.0, 46.0), (194.0, 47.0), (193.0, 14.0)], "w": 1.4}]
NEG_LOCH_HINTER_WASSER = variant(EX, id="loch-hinter-wasser")
NEG_LOCH_HINTER_WASSER["water"] = EX["water"] + [
    {"poly": [(168.0, 0.8), (186.0, 0.8), (186.0, 143.2), (168.0, 143.2)], "style": "fill"}]


# ---------------------------------------------------------------- Erzeugung

def pdftoppm(pdf, dpi, name):
    base = os.path.join(OUT, name[:-4])
    subprocess.check_call(["pdftoppm", "-r", str(dpi), "-png", "-singlefile", pdf, base])
    return Image.open(base + ".png").convert("RGB")


def main():
    if os.path.isdir(OUT):
        shutil.rmtree(OUT)
    os.makedirs(OUT)
    tpl.main()
    beispiel_pdf = os.path.join(VORLAGE_OUT, "scangolf-beispiel.pdf")
    vorlage_pdf = os.path.join(VORLAGE_OUT, "scangolf-vorlage.pdf")

    print("Beispiel in verschiedenen Auflösungen (pdftoppm):")
    base300 = None
    for dpi in (150, 200, 300, 600):
        name = "beispiel-%ddpi.png" % dpi
        img = pdftoppm(beispiel_pdf, dpi, name)
        MANIFEST.append(dict(positive(EX, "pdftoppm %d dpi" % dpi, dpi=dpi), file=name,
                             width=img.size[0], height=img.size[1]))
        print("  %-40s %dx%d" % (name, img.size[0], img.size[1]))
        if dpi == 300:
            base300 = img

    print("Beispiel, gestört:")
    b = base300
    save(rotate_exact(b, 2), "beispiel-rot180.png", positive(EX, "180° gedreht", dpi=300))
    save(rotate_exact(b, 1), "beispiel-rot90.png", positive(EX, "90° im Uhrzeigersinn", dpi=300))
    save(rotate_exact(b, 3), "beispiel-rot270.png", positive(EX, "270° im Uhrzeigersinn", dpi=300))
    save(skew(b, 2.0, False), "beispiel-schief+2-beschnitten.png",
         positive(EX, "2° schief, auf A4 beschnitten", dpi=300))
    save(skew(b, -3.0, True), "beispiel-schief-3.png", positive(EX, "-3° schief", dpi=300))
    save(skew(b, 4.0, True), "beispiel-schief+4.png", positive(EX, "4° schief", dpi=300))
    save(shift(b, 55, 40), "beispiel-verschoben.png", positive(EX, "um 4,7/3,4 mm verschoben", dpi=300))
    save(shift(b, -45, -35), "beispiel-verschoben2.png", positive(EX, "um -3,8/-3,0 mm verschoben", dpi=300))
    save(add_noise(b, 14), "beispiel-rauschen.png", positive(EX, "Rauschen sigma 14", dpi=300))
    save(b, "beispiel-jpeg60.jpg", positive(EX, "JPEG Qualität 60", dpi=300), fmt="JPEG", quality=60)
    save(blur(b, 2.0), "beispiel-unscharf.png", positive(EX, "Gauß-Unschärfe r=2 px", dpi=300))
    save(tint(b, (214, 214, 214)), "beispiel-grau.png", positive(EX, "graues Papier", dpi=300))
    save(tint(b, (250, 238, 196)), "beispiel-gelblich.png", positive(EX, "gelbliches Papier", dpi=300))
    save(darken(b), "beispiel-dunkel.png", positive(EX, "zu dunkel (Gamma 1.35, x0.78)", dpi=300))
    harsh = jpeg_roundtrip(add_noise(tint(skew(rotate_exact(b, 1), 2.5, True), (248, 240, 205)), 10), 70)
    save(harsh, "beispiel-kombi.jpg", positive(EX, "90°+2,5° schief, gelblich, Rauschen, JPEG 70", dpi=300),
         fmt="JPEG", quality=85)
    b200 = pdftoppm(beispiel_pdf, 200, "tmp-200.png")
    os.remove(os.path.join(OUT, "tmp-200.png"))
    save(b200, "beispiel-200dpi-jpeg60.jpg", positive(EX, "200 dpi, JPEG 60", dpi=200),
         fmt="JPEG", quality=60)
    save(blur(darken(rotate_exact(b200, 2), 1.2, 0.85), 1.2), "beispiel-200dpi-dunkel-unscharf.png",
         positive(EX, "200 dpi, 180°, dunkel, unscharf", dpi=200))

    print("Eigene Bahnen:")
    for d in (SCHRAEG, DUENN, LUECKEN, GEWUNDEN, WASSER):
        save(render(d), "bahn-%s.png" % d["id"], positive(d, "gezeichnet 300 dpi", dpi=300))
    save(jpeg_roundtrip(skew(render(DUENN), -2.0, True), 60), "bahn-duenn-schief-jpeg.png",
         positive(DUENN, "dünn, -2° schief, JPEG 60", dpi=300))
    save(rotate_exact(render(GEWUNDEN, 200), 3), "bahn-gewunden-200dpi-rot270.png",
         positive(GEWUNDEN, "200 dpi, 270°", dpi=200))
    save(render(variant(EX, id="par-keins", par=[])), "bahn-par-keins.png",
         positive(variant(EX, id="par-keins", par=[]), "kein Par angekreuzt", dpi=300,
                  expect_par=3, expect_warnings=["PAR_NOT_MARKED"]))
    save(render(variant(EX, id="par-mehrere", par=[5, 3])), "bahn-par-mehrere.png",
         positive(variant(EX, id="par-mehrere", par=[5, 3]), "Par 3 und 5 angekreuzt", dpi=300,
                  expect_par=3, expect_warnings=["PAR_MULTIPLE"]))
    ohne = variant(EX, id="ohne-namen", name_text=None, lane_text=None, par=[2])
    save(render(ohne), "bahn-ohne-namen.png", positive(ohne, "Name und Bahn leer", dpi=300))
    save(render(START_NAH_WAND), "bahn-start-nah-wand.png",
         positive(START_NAH_WAND, "Start berührt fast die Wand", dpi=300,
                  start_tolerance=4.0, expect_warnings=["START_MOVED"]))

    print("Negativfälle:")
    save(render(variant(EX, id="start-fehlt", start=[])), "neg-start-fehlt.png",
         negative("NO_START", "kein roter Punkt"))
    save(render(variant(EX, id="zwei-loecher", hole=EX["hole"] + [(150.0, 125.0, 4.0)])),
         "neg-zwei-loecher.png", negative("MULTIPLE_HOLES", "zwei grüne Punkte"))
    save(render(variant(EX, id="zwei-starts", start=EX["start"] + [(100.0, 20.0, 4.5)])),
         "neg-zwei-starts.png", negative("MULTIPLE_STARTS", "zwei rote Punkte"))
    save(render(NEG_LOCH_EINGEMAUERT), "neg-loch-eingemauert.png",
         negative("HOLE_UNREACHABLE", "Loch komplett eingemauert"))
    save(render(NEG_LOCH_HINTER_WASSER), "neg-loch-hinter-wasser.png",
         negative("HOLE_UNREACHABLE", "Wasserstreifen trennt Start und Loch"))
    save(render(NEG_START_IN_WAND), "neg-start-in-wand.png",
         negative("START_IN_WALL", "dicke Wand über dem Startpunkt"))
    save(render(NEG_LOCH_IN_WAND), "neg-loch-in-wand.png",
         negative("HOLE_IN_WALL", "dicke Wand über dem Loch"))
    save(render(variant(EX, id="nur-waende", start=[], hole=[])), "neg-nur-waende.png",
         negative("NO_START", "Wände, aber weder Start noch Loch", also=["NO_HOLE"]))
    save(render(None), "neg-leeres-blatt.png", negative("FIELD_EMPTY", "leere Vorlage"))
    save(random_photo(3000, 2000, 5), "neg-foto.png", negative("SHEET_NOT_FOUND", "Foto ohne Marken"))
    save(random_photo(1600, 1200, 6), "neg-foto2.jpg", negative("SHEET_NOT_FOUND", "Foto ohne Marken, JPEG"),
         fmt="JPEG", quality=80)
    cut = b.copy()
    ImageDraw.Draw(cut).polygon([(0, 0), (430, 0), (0, 430)], fill=(255, 255, 255))
    save(cut, "neg-ecke-abgeschnitten.png", negative("MARKS_INCOMPLETE", "Ecke oben links fehlt"))
    cut2 = b.crop((0, 0, b.size[0] - 420, b.size[1]))
    save(cut2, "neg-rand-abgeschnitten.png", negative("MARKS_INCOMPLETE", "rechte 35 mm fehlen"))
    cut3 = b.copy()
    ImageDraw.Draw(cut3).rectangle([b.size[0] - 330, b.size[1] - 330, b.size[0], b.size[1]], fill=(255, 255, 255))
    save(cut3, "neg-block-fehlt.png", negative("MARKS_INCOMPLETE", "Block unten rechts verdeckt"))
    save(ImageOps.mirror(b), "neg-gespiegelt.png", negative("SHEET_NOT_FOUND", "gespiegelter Scan"))
    save(Image.new("RGB", (2480, 3508), (255, 255, 255)), "neg-weiss.png",
         negative("SHEET_NOT_FOUND", "komplett weiß"))
    save(Image.new("RGB", (120, 80), (200, 200, 200)), "neg-winzig.png",
         negative("IMAGE_TOO_SMALL", "120x80 Pixel"))

    print("Leere Vorlage (Raster/Rahmen dürfen nie Wand werden):")
    for dpi in (150, 300, 600):
        name = "leer-%ddpi.png" % dpi
        img = pdftoppm(vorlage_pdf, dpi, name)
        MANIFEST.append(dict(empty_entry("pdftoppm %d dpi" % dpi), file=name, dpi=dpi,
                             width=img.size[0], height=img.size[1]))
        print("  %-40s %dx%d" % (name, img.size[0], img.size[1]))
        if dpi == 300:
            e300 = img
    save(add_noise(e300, 14), "leer-rauschen.png", dict(empty_entry("Rauschen"), dpi=300))
    save(e300, "leer-jpeg60.jpg", dict(empty_entry("JPEG 60"), dpi=300), fmt="JPEG", quality=60)
    save(blur(e300, 2.5), "leer-unscharf.png", dict(empty_entry("unscharf"), dpi=300))
    save(darken(e300), "leer-dunkel.png", dict(empty_entry("zu dunkel"), dpi=300))
    save(darken(tint(e300, (235, 225, 190)), 1.5, 0.7), "leer-gelb-sehr-dunkel.png",
         dict(empty_entry("gelblich und sehr dunkel"), dpi=300))
    save(tint(e300, (205, 205, 205)), "leer-grau.png", dict(empty_entry("graues Papier"), dpi=300))
    save(skew(rotate_exact(e300, 1), -2.0, True), "leer-rot90-schief.png",
         dict(empty_entry("90° und -2° schief"), dpi=300))

    with open(os.path.join(OUT, "manifest.json"), "w", encoding="utf-8") as f:
        json.dump({"format": "scangolf-testbilder", "version": 1, "images": MANIFEST}, f, indent=1,
                  ensure_ascii=False)
    print("%d Testbilder in %s" % (len(MANIFEST), OUT))


if __name__ == "__main__":
    main()
