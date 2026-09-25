#!/usr/bin/env python3
"""Programm-Icon (256x256): grüner Kreis mit Fahne und Ball, wie im Vorlagen-Logo."""
import math
import sys

import cairo

out = sys.argv[1]
s = cairo.ImageSurface(cairo.FORMAT_ARGB32, 256, 256)
c = cairo.Context(s)
c.arc(128, 128, 120, 0, 2 * math.pi)
c.set_source_rgb(0.12, 0.45, 0.22)
c.fill()
c.arc(128, 128, 120, 0, 2 * math.pi)
c.set_source_rgb(0.95, 0.76, 0.31)
c.set_line_width(8)
c.stroke()
c.set_source_rgb(0.06, 0.08, 0.06)
c.save()
c.translate(118, 196)
c.scale(1, 0.35)
c.arc(0, 0, 44, 0, 2 * math.pi)
c.restore()
c.fill()
c.set_source_rgb(1, 1, 1)
c.set_line_width(9)
c.set_line_cap(cairo.LINE_CAP_ROUND)
c.move_to(112, 190)
c.line_to(112, 52)
c.stroke()
c.set_source_rgb(0.91, 0.25, 0.18)
c.move_to(116, 50)
c.line_to(196, 80)
c.line_to(116, 110)
c.close_path()
c.fill()
c.arc(170, 170, 18, 0, 2 * math.pi)
c.set_source_rgb(1, 1, 1)
c.fill()
s.write_to_png(out)
