#!/usr/bin/env python3
"""ScanGolf – Druckvorlage (A4 quer) erzeugen.

Einzige Quelle für die Geometrie der Vorlage. Alle Maße in Millimetern,
Ursprung oben links auf der Seite, x nach rechts, y nach unten.

Ausgaben (vorlage/out/):
  template.json              Geometrie für die Scan-Auswertung (nie von Hand ändern)
  scangolf-vorlage.pdf       leere Vorlage zum Ausdrucken
  scangolf-beispiel.pdf      ausgefülltes Beispiel
  beispiel-scan-300dpi.png   Beispiel als idealer Scan (300 dpi)
  beispiel.json              Sollwerte des Beispiels (Feld-mm) für Tests

Das Modul wird auch vom Testbild-Generator importiert (draw_template,
draw_drawing, render_png, EXAMPLE).
"""

import json
import math
import os
import random
import sys

import cairo

# ---------------------------------------------------------------- Geometrie

PAGE_W = 297.0
PAGE_H = 210.0

MODULE = 1.5                 # Kantenlänge eines Moduls der Passmarken
MARK_SIZE = 7 * MODULE       # 7x7 Module wie ein QR-Suchmuster
MARK_INSET = 9.0             # Abstand der Marken vom Blattrand

FIELD_X = 28.5
FIELD_Y = 34.0
FIELD_W = 240.0
FIELD_H = 144.0
FIELD_FRAME_W = 0.6          # Linienbreite des Rahmens (mittig auf der Feldkante)
FIELD_FRAME_GRAY = 0.78      # hellgrau – darf nie als Wand erkannt werden
GRID_STEP = 10.0             # Punktraster
GRID_DOT_R = 0.35
GRID_DOT_GRAY = 0.80

NAME_BOX = (100.0, 14.0, 92.0, 16.0)    # x, y, w, h
LANE_BOX = (198.0, 14.0, 70.5, 16.0)
BOX_LINE_GRAY = 0.62

PAR_VALUES = (2, 3, 4, 5)
PAR_BOX_SIZE = 9.0
PAR_BOX_Y = 184.5
PAR_BOX_X0 = 44.0
PAR_BOX_STEP = 16.0
PAR_BOX_INSET = 1.3          # Innenrand, der beim Auswerten ignoriert wird

NAME_INSET = 1.2


def mark_rects():
    lo = MARK_INSET
    hi_x = PAGE_W - MARK_INSET - MARK_SIZE
    hi_y = PAGE_H - MARK_INSET - MARK_SIZE
    return {
        "tl": (lo, lo),
        "tr": (hi_x, lo),
        "bl": (lo, hi_y),
        "br": (hi_x, hi_y),
    }


def par_boxes():
    boxes = []
    for i, p in enumerate(PAR_VALUES):
        boxes.append((p, PAR_BOX_X0 + i * PAR_BOX_STEP, PAR_BOX_Y, PAR_BOX_SIZE, PAR_BOX_SIZE))
    return boxes


def template_dict():
    marks = {}
    for key, (x, y) in mark_rects().items():
        marks[key] = {
            "type": "block" if key == "br" else "finder",
            "x": x, "y": y, "size": MARK_SIZE,
            "cx": x + MARK_SIZE / 2, "cy": y + MARK_SIZE / 2,
        }
    return {
        "format": "scangolf-template",
        "version": 1,
        "units": "mm",
        "page": {"w": PAGE_W, "h": PAGE_H, "orientation": "landscape"},
        "marks": {
            "module": MODULE,
            "finder_modules": 7,
            "tl": marks["tl"], "tr": marks["tr"], "bl": marks["bl"], "br": marks["br"],
        },
        "field": {
            "x": FIELD_X, "y": FIELD_Y, "w": FIELD_W, "h": FIELD_H,
            "frame_width": FIELD_FRAME_W, "frame_gray": FIELD_FRAME_GRAY,
            "grid_step": GRID_STEP, "grid_dot_r": GRID_DOT_R, "grid_dot_gray": GRID_DOT_GRAY,
        },
        "name_box": {"x": NAME_BOX[0], "y": NAME_BOX[1], "w": NAME_BOX[2], "h": NAME_BOX[3],
                     "inset": NAME_INSET},
        "lane_box": {"x": LANE_BOX[0], "y": LANE_BOX[1], "w": LANE_BOX[2], "h": LANE_BOX[3],
                     "inset": NAME_INSET},
        "par_boxes": [
            {"par": p, "x": x, "y": y, "w": w, "h": h, "inset": PAR_BOX_INSET}
            for (p, x, y, w, h) in par_boxes()
        ],
        "par_default": 3,
    }


# ---------------------------------------------------------------- Vorlage zeichnen

def _gray(ctx, g):
    ctx.set_source_rgb(g, g, g)


def _rounded_rect(ctx, x, y, w, h, r):
    ctx.new_sub_path()
    ctx.arc(x + w - r, y + r, r, -math.pi / 2, 0)
    ctx.arc(x + w - r, y + h - r, r, 0, math.pi / 2)
    ctx.arc(x + r, y + h - r, r, math.pi / 2, math.pi)
    ctx.arc(x + r, y + r, r, math.pi, 3 * math.pi / 2)
    ctx.close_path()


def _text(ctx, s, x, y, size, face="DejaVu Sans", bold=False, italic=False, align="left"):
    ctx.select_font_face(face,
                         cairo.FONT_SLANT_ITALIC if italic else cairo.FONT_SLANT_NORMAL,
                         cairo.FONT_WEIGHT_BOLD if bold else cairo.FONT_WEIGHT_NORMAL)
    ctx.set_font_size(size)
    ext = ctx.text_extents(s)
    if align == "center":
        x -= ext.x_advance / 2
    elif align == "right":
        x -= ext.x_advance
    ctx.move_to(x, y)
    ctx.show_text(s)
    return ext.x_advance


def draw_finder(ctx, x, y):
    m = MODULE
    ctx.set_source_rgb(0, 0, 0)
    ctx.rectangle(x, y, 7 * m, 7 * m)
    ctx.fill()
    ctx.set_source_rgb(1, 1, 1)
    ctx.rectangle(x + m, y + m, 5 * m, 5 * m)
    ctx.fill()
    ctx.set_source_rgb(0, 0, 0)
    ctx.rectangle(x + 2 * m, y + 2 * m, 3 * m, 3 * m)
    ctx.fill()


def draw_block(ctx, x, y):
    ctx.set_source_rgb(0, 0, 0)
    ctx.rectangle(x, y, MARK_SIZE, MARK_SIZE)
    ctx.fill()


def draw_logo(ctx, x, y):
    """Kleines Fahnen-Logo + Schriftzug. (x, y) = linke Grundlinie."""
    # Fahne
    ctx.set_source_rgb(0.12, 0.45, 0.22)
    ctx.new_path()
    ctx.arc(x + 5.0, y - 4.2, 5.0, 0, 2 * math.pi)
    ctx.fill()
    ctx.set_source_rgb(1, 1, 1)
    ctx.set_line_width(0.55)
    ctx.move_to(x + 4.0, y - 0.9)
    ctx.line_to(x + 4.0, y - 8.2)
    ctx.stroke()
    ctx.set_source_rgb(0.93, 0.26, 0.2)
    ctx.move_to(x + 4.2, y - 8.2)
    ctx.line_to(x + 7.6, y - 7.0)
    ctx.line_to(x + 4.2, y - 5.8)
    ctx.close_path()
    ctx.fill()
    ctx.set_source_rgb(0.1, 0.1, 0.1)
    w = _text(ctx, "Scan", x + 12.0, y, 9.0, face="DejaVu Sans", bold=True)
    ctx.set_source_rgb(0.12, 0.45, 0.22)
    _text(ctx, "Golf", x + 12.0 + w, y, 9.0, face="DejaVu Sans", bold=True)


def draw_template(ctx):
    """Leere Vorlage in Seiten-mm zeichnen (ctx ist bereits auf mm skaliert)."""
    ctx.set_source_rgb(1, 1, 1)
    ctx.rectangle(0, 0, PAGE_W, PAGE_H)
    ctx.fill()

    # Passmarken
    for key, (x, y) in mark_rects().items():
        if key == "br":
            draw_block(ctx, x, y)
        else:
            draw_finder(ctx, x, y)

    # Kopfzeile
    draw_logo(ctx, FIELD_X, 26.0)
    ctx.set_source_rgb(0.35, 0.35, 0.35)
    _text(ctx, "Dein Name", NAME_BOX[0] + 1.0, NAME_BOX[1] - 1.6, 3.0)
    _text(ctx, "Name der Bahn", LANE_BOX[0] + 1.0, LANE_BOX[1] - 1.6, 3.0)
    for (bx, by, bw, bh) in (NAME_BOX, LANE_BOX):
        _gray(ctx, BOX_LINE_GRAY)
        ctx.set_line_width(0.35)
        _rounded_rect(ctx, bx, by, bw, bh, 1.5)
        ctx.stroke()

    # Spielfeld: Punktraster + hellgrauer Rahmen
    _gray(ctx, GRID_DOT_GRAY)
    nx = int(FIELD_W / GRID_STEP)
    ny = int(FIELD_H / GRID_STEP)
    for i in range(1, nx):
        for j in range(1, ny + 1):
            gy = j * GRID_STEP
            if gy >= FIELD_H:
                continue
            ctx.new_path()
            ctx.arc(FIELD_X + i * GRID_STEP, FIELD_Y + gy, GRID_DOT_R, 0, 2 * math.pi)
            ctx.fill()
    _gray(ctx, FIELD_FRAME_GRAY)
    ctx.set_line_width(FIELD_FRAME_W)
    ctx.rectangle(FIELD_X, FIELD_Y, FIELD_W, FIELD_H)
    ctx.stroke()

    # Par-Kästchen
    ctx.set_source_rgb(0.15, 0.15, 0.15)
    _text(ctx, "Par", FIELD_X, PAR_BOX_Y + 6.8, 5.2, bold=True)
    for (p, x, y, w, h) in par_boxes():
        _gray(ctx, 0.45)
        ctx.set_line_width(0.35)
        ctx.rectangle(x, y, w, h)
        ctx.stroke()
        ctx.set_source_rgb(0.15, 0.15, 0.15)
        _text(ctx, str(p), x + w + 1.6, y + 6.8, 5.2, bold=True)

    # Legende
    lx = 118.0
    ly = 187.0
    items = [
        ("Wand", (0.05, 0.05, 0.05), "line"),
        ("Start", (0.86, 0.12, 0.14), "dot"),
        ("Loch", (0.10, 0.60, 0.25), "dot"),
        ("Wasser", (0.18, 0.42, 0.90), "rect"),
    ]
    for label, rgb, kind in items:
        ctx.set_source_rgb(*rgb)
        if kind == "line":
            ctx.set_line_width(1.2)
            ctx.move_to(lx, ly - 1.2)
            ctx.line_to(lx + 5.0, ly - 1.2)
            ctx.stroke()
        elif kind == "dot":
            ctx.new_path()
            ctx.arc(lx + 2.5, ly - 1.2, 1.8, 0, 2 * math.pi)
            ctx.fill()
        else:
            ctx.rectangle(lx, ly - 3.0, 5.0, 3.6)
            ctx.fill()
        ctx.set_source_rgb(0.2, 0.2, 0.2)
        w = _text(ctx, label, lx + 6.5, ly, 3.4)
        lx += 6.5 + w + 7.0
    ctx.set_source_rgb(0.35, 0.35, 0.35)
    _text(ctx, "Mit dicken Stiften zeichnen · Wege mind. 1,5 cm breit · "
               "Ecken frei lassen", 118.0, 195.0, 3.0)


# ---------------------------------------------------------------- Zeichnungen (Nutzer-Stifte)

RED = (0.86, 0.12, 0.14)
GREEN = (0.10, 0.60, 0.25)
BLUE = (0.18, 0.42, 0.90)
BLACK = (0.07, 0.07, 0.08)
PEN = (0.10, 0.12, 0.30)


class Wobble:
    """Glatte, deterministische Zitter-Funktion für handgezeichnete Linien."""

    def __init__(self, rnd, amp):
        self.amp = amp
        self.terms = [(rnd.uniform(0.05, 0.25), rnd.uniform(0, 2 * math.pi),
                       rnd.uniform(0.3, 1.0)) for _ in range(3)]
        norm = sum(t[2] for t in self.terms)
        self.terms = [(f, ph, a / norm) for (f, ph, a) in self.terms]

    def __call__(self, s):
        return self.amp * sum(a * math.sin(f * s + ph) for (f, ph, a) in self.terms)


def _resample(pts, step):
    out = [pts[0]]
    for (x0, y0), (x1, y1) in zip(pts, pts[1:]):
        d = math.hypot(x1 - x0, y1 - y0)
        n = max(1, int(d / step))
        for k in range(1, n + 1):
            t = k / n
            out.append((x0 + (x1 - x0) * t, y0 + (y1 - y0) * t))
    return out


def hand_path(ctx, pts, rnd, wobble):
    """Polylinie mit leichtem Zittern als Pfad anlegen (Koordinaten schon in Seiten-mm)."""
    if wobble <= 0 or len(pts) < 2:
        ctx.move_to(*pts[0])
        for p in pts[1:]:
            ctx.line_to(*p)
        return
    w = Wobble(rnd, wobble)
    rs = _resample(pts, 1.5)
    s = 0.0
    prev = rs[0]
    for i, (x, y) in enumerate(rs):
        if i > 0:
            s += math.hypot(x - prev[0], y - prev[1])
        j0 = rs[max(0, i - 1)]
        j1 = rs[min(len(rs) - 1, i + 1)]
        dx, dy = j1[0] - j0[0], j1[1] - j0[1]
        d = math.hypot(dx, dy) or 1.0
        nx, ny = -dy / d, dx / d
        o = w(s)
        q = (x + nx * o, y + ny * o)
        if i == 0:
            ctx.move_to(*q)
        else:
            ctx.line_to(*q)
        prev = (x, y)


def _blob(ctx, cx, cy, r, rnd, irregular=0.08):
    n = 36
    phase = [rnd.uniform(0, 2 * math.pi) for _ in range(3)]
    for k in range(n + 1):
        a = 2 * math.pi * k / n
        rr = r * (1 + irregular * (math.sin(2 * a + phase[0]) * 0.5
                                   + math.sin(3 * a + phase[1]) * 0.3
                                   + math.sin(5 * a + phase[2]) * 0.2))
        x, y = cx + rr * math.cos(a), cy + rr * math.sin(a)
        if k == 0:
            ctx.move_to(x, y)
        else:
            ctx.line_to(x, y)
    ctx.close_path()


def draw_drawing(ctx, d):
    """Nutzerzeichnung (Feldkoordinaten in mm) auf die Vorlage malen."""
    rnd = random.Random(d.get("seed", 1))
    wob = d.get("wobble", 0.35)
    fx, fy = FIELD_X, FIELD_Y

    def page(p):
        return (fx + p[0], fy + p[1])

    ctx.set_line_cap(cairo.LINE_CAP_ROUND)
    ctx.set_line_join(cairo.LINE_JOIN_ROUND)

    # Wasser zuerst (liegt unter den Wänden)
    for wtr in d.get("water", []):
        poly = [page(p) for p in wtr["poly"]]
        style = wtr.get("style", "fill")
        ctx.set_source_rgb(*wtr.get("rgb", BLUE))
        if style == "fill":
            hand_path(ctx, poly + [poly[0]], rnd, wob * 0.5)
            ctx.close_path()
            ctx.fill()
        elif style == "hatch":
            ctx.save()
            hand_path(ctx, poly + [poly[0]], rnd, 0)
            ctx.close_path()
            ctx.clip()
            xs = [p[0] for p in poly]
            ys = [p[1] for p in poly]
            x0, x1, y0, y1 = min(xs), max(xs), min(ys), max(ys)
            span = (x1 - x0) + (y1 - y0)
            ctx.set_line_width(wtr.get("hatch_w", 0.8))
            t = -span
            while t < span:
                ctx.move_to(x0 + t, y0)
                ctx.line_to(x0 + t + (y1 - y0), y1)
                t += wtr.get("hatch_step", 1.8)
            ctx.stroke()
            ctx.restore()
            ctx.set_line_width(1.0)
            hand_path(ctx, poly + [poly[0]], rnd, wob * 0.5)
            ctx.stroke()
        else:  # outline
            ctx.set_line_width(wtr.get("line_w", 1.2))
            hand_path(ctx, poly + [poly[0]], rnd, wob * 0.5)
            ctx.stroke()

    # Wände
    for wall in d.get("walls", []):
        pts = [page(p) for p in wall["pts"]]
        ctx.set_source_rgb(*wall.get("rgb", BLACK))
        ctx.set_line_width(wall.get("w", 1.4))
        gaps = wall.get("gaps")
        if gaps:
            # Linie mit Aussetzern: gezeichnet wird mit Strichmuster
            ctx.set_line_cap(cairo.LINE_CAP_BUTT)
            ctx.set_dash(gaps["pattern"], gaps.get("offset", 0.0))
            hand_path(ctx, pts, rnd, wall.get("wobble", wob))
            ctx.stroke()
            ctx.set_dash([])
            ctx.set_line_cap(cairo.LINE_CAP_ROUND)
        else:
            hand_path(ctx, pts, rnd, wall.get("wobble", wob))
            ctx.stroke()

    # Filled blocks (z. B. dicke schwarze Flächen)
    for blk in d.get("blocks", []):
        poly = [page(p) for p in blk["poly"]]
        ctx.set_source_rgb(*blk.get("rgb", BLACK))
        hand_path(ctx, poly + [poly[0]], rnd, 0)
        ctx.close_path()
        ctx.fill()

    # Start (rot) und Loch (grün)
    for (x, y, r) in d.get("start", []):
        ctx.set_source_rgb(*RED)
        _blob(ctx, fx + x, fy + y, r, rnd)
        ctx.fill()
    for (x, y, r) in d.get("hole", []):
        ctx.set_source_rgb(*GREEN)
        _blob(ctx, fx + x, fy + y, r, rnd)
        ctx.fill()

    # Wände, die ÜBER die Punkte gemalt werden (z. B. Start in Wand)
    for wall in d.get("walls_over", []):
        pts = [page(p) for p in wall["pts"]]
        ctx.set_source_rgb(*wall.get("rgb", BLACK))
        ctx.set_line_width(wall.get("w", 1.4))
        hand_path(ctx, pts, rnd, wall.get("wobble", wob))
        ctx.stroke()

    # Par-Kreuze
    boxes = {p: (x, y, w, h) for (p, x, y, w, h) in par_boxes()}
    for p in d.get("par", []):
        x, y, w, h = boxes[p]
        ctx.set_source_rgb(*PEN)
        ctx.set_line_width(0.7)
        m = 1.6
        hand_path(ctx, [(x + m, y + m), (x + w - m, y + h - m)], rnd, 0.15)
        ctx.stroke()
        hand_path(ctx, [(x + w - m, y + m), (x + m, y + h - m)], rnd, 0.15)
        ctx.stroke()

    # Name und Bahnname (Handschrift-Ersatz)
    for key, box in (("name_text", NAME_BOX), ("lane_text", LANE_BOX)):
        s = d.get(key)
        if not s:
            continue
        ctx.set_source_rgb(*PEN)
        size = 9.0
        ctx.select_font_face("Z003", cairo.FONT_SLANT_ITALIC, cairo.FONT_WEIGHT_NORMAL)
        ctx.set_font_size(size)
        ext = ctx.text_extents(s)
        maxw = box[2] - 6.0
        if ext.x_advance > maxw:
            size *= maxw / ext.x_advance
            ctx.set_font_size(size)
        ctx.move_to(box[0] + 3.0, box[1] + box[3] * 0.5 + size * 0.3)
        ctx.show_text(s)

    # Freie Kritzeleien ausserhalb/innerhalb (für Tests)
    for sc in d.get("scribbles", []):
        pts = [page(p) if sc.get("field", True) else p for p in sc["pts"]]
        ctx.set_source_rgb(*sc.get("rgb", BLACK))
        ctx.set_line_width(sc.get("w", 1.0))
        hand_path(ctx, pts, rnd, sc.get("wobble", wob))
        ctx.stroke()


# ---------------------------------------------------------------- Beispiel

EXAMPLE = {
    "id": "beispiel",
    "seed": 7,
    "wobble": 0.35,
    "name_text": "Max Mustermann",
    "lane_text": "Die Schlange",
    "par": [4],
    "walls": [
        {"pts": [(62.0, 143.0), (62.5, 95.0), (61.5, 44.0)], "w": 1.4},
        {"pts": [(128.0, 1.0), (128.5, 50.0), (127.5, 100.0)], "w": 1.4},
        {"pts": [(160.0, 40.0), (186.0, 66.0)], "w": 1.4},
        {"pts": [(196.0, 120.0), (222.0, 110.0)], "w": 1.4},
    ],
    "water": [
        {"poly": [(95 + 15 * math.cos(a) * (1 + 0.08 * math.sin(3 * a)),
                   118 + 10 * math.sin(a) * (1 + 0.08 * math.cos(2 * a)))
                  for a in [2 * math.pi * k / 40 for k in range(40)]],
         "style": "fill"},
    ],
    "start": [(28.0, 118.0, 4.5)],
    "hole": [(210.0, 30.0, 4.0)],
}


def ground_truth(d):
    """Sollwerte einer Zeichnung (für Tests), alles in Feld-mm."""
    return {
        "id": d.get("id"),
        "start": list(d["start"][0][:2]) if len(d.get("start", [])) == 1 else None,
        "hole": list(d["hole"][0][:2]) if len(d.get("hole", [])) == 1 else None,
        "par": d["par"][0] if len(d.get("par", [])) >= 1 else None,
        "par_marked": list(d.get("par", [])),
        "walls": [{"pts": [list(p) for p in w["pts"]], "w": w.get("w", 1.4),
                   "gaps": w.get("gaps") is not None}
                  for w in d.get("walls", []) + d.get("walls_over", [])],
        "water": [[list(p) for p in w["poly"]] for w in d.get("water", [])],
        "has_name": bool(d.get("name_text")),
        "has_lane": bool(d.get("lane_text")),
    }


# ---------------------------------------------------------------- Ausgabe

MM_TO_PT = 72.0 / 25.4


def render_pdf(path, drawing=None):
    surf = cairo.PDFSurface(path, PAGE_W * MM_TO_PT, PAGE_H * MM_TO_PT)
    # feste Metadaten, damit die PDF bei unveränderter Vorlage bytegleich bleibt
    surf.set_metadata(cairo.PDFMetadata.TITLE, "ScanGolf-Vorlage")
    surf.set_metadata(cairo.PDFMetadata.CREATOR, "vorlage/generate.py")
    surf.set_metadata(cairo.PDFMetadata.CREATE_DATE, "2026-01-01T00:00:00Z")
    ctx = cairo.Context(surf)
    ctx.scale(MM_TO_PT, MM_TO_PT)
    draw_template(ctx)
    if drawing:
        draw_drawing(ctx, drawing)
    surf.show_page()
    surf.finish()


def render_surface(dpi, drawing=None):
    s = dpi / 25.4
    w = int(round(PAGE_W * s))
    h = int(round(PAGE_H * s))
    surf = cairo.ImageSurface(cairo.FORMAT_RGB24, w, h)
    ctx = cairo.Context(surf)
    ctx.scale(s, s)
    draw_template(ctx)
    if drawing:
        draw_drawing(ctx, drawing)
    surf.flush()
    return surf


def render_png(path, dpi, drawing=None):
    surf = render_surface(dpi, drawing)
    surf.write_to_png(path)
    return surf.get_width(), surf.get_height()


def main():
    out = os.path.join(os.path.dirname(os.path.abspath(__file__)), "out")
    os.makedirs(out, exist_ok=True)
    with open(os.path.join(out, "template.json"), "w", encoding="utf-8") as f:
        json.dump(template_dict(), f, indent=2)
        f.write("\n")
    render_pdf(os.path.join(out, "scangolf-vorlage.pdf"))
    render_pdf(os.path.join(out, "scangolf-beispiel.pdf"), EXAMPLE)
    w, h = render_png(os.path.join(out, "beispiel-scan-300dpi.png"), 300, EXAMPLE)
    with open(os.path.join(out, "beispiel.json"), "w", encoding="utf-8") as f:
        json.dump(ground_truth(EXAMPLE), f, indent=2)
        f.write("\n")
    print("Vorlage erzeugt in %s (Beispiel-Scan %dx%d)" % (out, w, h), file=sys.stderr)


if __name__ == "__main__":
    main()
