#!/usr/bin/env python3
"""ScanGolf – Grenztests: härtere Varianten, um die Grenzen der Erkennung zu vermessen.

Kein Bestandteil der Pflicht-Tests (die Ergebnisse werden in README "Bekannte Grenzen"
dokumentiert). Ausgabe: testbilder/out/grenzen/ + manifest.json (gleiches Format).

    python3 testbilder/generate_grenzen.py && ./build.sh batch testbilder/out/grenzen
"""

import copy
import json
import math
import os
import sys

from PIL import Image, ImageChops, ImageDraw, ImageFilter

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
sys.path.insert(0, os.path.join(os.path.dirname(HERE), "vorlage"))
import generate as tpl  # noqa: E402
import generate_testbilder as tb  # noqa: E402

OUT = os.path.join(HERE, "out", "grenzen")
ITEMS = []


def put(img, name, desc, drawing=tpl.EXAMPLE, fmt="PNG", error=None, **kw):
    """error: erwarteter Fehlercode, wenn Ablehnung das richtige Verhalten ist."""
    path = os.path.join(OUT, name)
    img.save(path, fmt, **kw)
    e = {"file": name, "expect": "error" if error else "ok", "group": "grenze", "transform": desc,
         "truth": tpl.ground_truth(drawing)}
    if error:
        e["error"] = error
    ITEMS.append(e)
    print("  %-38s %s" % (name, desc))


perspective = tb.perspective
shadow = tb.shadow


def main():
    os.makedirs(OUT, exist_ok=True)
    ex = tpl.EXAMPLE
    base = tb.render(ex, 300)
    print("Grenzvarianten:")
    for dpi in (100, 75, 60):
        put(tb.render(ex, dpi), "dpi-%d.png" % dpi, "%d dpi" % dpi)
    for deg in (8, 15, 30, 45):
        put(tb.skew(base, deg, True), "schief-%d.png" % deg, "%d° schief" % deg)
    for k in (0.02, 0.05, 0.08):
        put(perspective(base, k), "perspektive-%d.png" % int(k * 100), "Trapez %d %% (Foto)" % int(k * 100),
            error="SHEET_NOT_FOUND")
    put(tb.blur(base, 4), "unscharf-4.png", "Gauß r=4 px")
    put(tb.blur(base, 7), "unscharf-7.png", "Gauß r=7 px")
    put(tb.add_noise(base, 35), "rauschen-35.png", "Rauschen sigma 35")
    put(base, "jpeg-15.jpg", "JPEG Qualität 15", fmt="JPEG", quality=15)
    put(shadow(base, 0.45), "schatten-45.png", "Verlauf 55..100 % Helligkeit")
    put(shadow(base, 0.7), "schatten-70.png", "Verlauf 30..100 % Helligkeit")
    put(tb.darken(base, 2.0, 0.55), "sehr-dunkel.png", "Gamma 2, x0,55")
    put(tb.tint(base, (255, 205, 150)), "orange-papier.png", "stark orangefarbenes Papier")
    # Bleistift: Wände grau statt schwarz, dünn
    pen = copy.deepcopy(ex)
    for wl in pen["walls"]:
        wl["rgb"] = (0.45, 0.45, 0.45)
        wl["w"] = 0.5
    put(tb.render(pen), "bleistift.png", "Wände mit Bleistift (grau, 0,5 mm)", pen)
    light = copy.deepcopy(ex)
    for wl in light["walls"]:
        wl["rgb"] = (0.62, 0.62, 0.62)
    put(tb.render(light), "hellgraue-waende.png", "Wände hellgrau (62 %)", light)
    # Buntstift: blasse Farben
    pale = copy.deepcopy(ex)
    pale["water"][0]["rgb"] = (0.62, 0.78, 0.97)
    put(tb.render(pale), "blasses-wasser.png", "Wasser hellblau (Buntstift)", pale)
    # Tonersparen: gesamtes Blatt blass (Marken ~55 % grau)
    faded = base.point(lambda v: int(255 - (255 - v) * 0.45))
    put(faded, "toner-blass.png", "Druck/Scan sehr blass (45 % Kontrast)")
    # Graustufen-Scan: Farben gehen verloren
    put(base.convert("L").convert("RGB"), "graustufen.png", "Graustufen-Scan", error="GRAYSCALE_SCAN")
    with open(os.path.join(OUT, "manifest.json"), "w", encoding="utf-8") as f:
        json.dump({"format": "scangolf-testbilder", "version": 1, "images": ITEMS}, f, indent=1,
                  ensure_ascii=False)
    print("%d Grenzvarianten in %s" % (len(ITEMS), OUT))


if __name__ == "__main__":
    main()
