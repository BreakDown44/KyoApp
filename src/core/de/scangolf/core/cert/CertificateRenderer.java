package de.scangolf.core.cert;

import de.scangolf.core.game.ScoreNames;
import de.scangolf.core.game.ShotTrace;
import de.scangolf.core.level.Level;
import de.scangolf.core.level.Rules;
import de.scangolf.core.render.Canvas;
import de.scangolf.core.render.Colors;
import de.scangolf.core.render.LevelRasterizer;
import de.scangolf.core.util.ArgbImage;

/**
 * Zeichnet die Urkunde auf A4 hoch. Logische Einheit des Canvas: Millimeter (210 x 297).
 */
public final class CertificateRenderer {

    public static final double PAGE_W = 210;
    public static final double PAGE_H = 297;

    static final int PAPER = 0xFFFBF6E9;
    static final int GREEN = 0xFF1F4D2C;
    static final int GOLD = 0xFFC49A2C;
    static final int GOLD_LIGHT = 0xFFE8D28E;
    static final int INK = 0xFF2B2A22;
    static final int MUTED = 0xFF7D7662;

    private static final int[] TRACE_COLORS = {
        0xFFFFFFFF, 0xFFFFE066, 0xFFFF9F43, 0xFFFF6B9A, 0xFF9B8CFF, 0xFF6EE7F0, 0xFFB8F26B,
    };

    private CertificateRenderer() {
    }

    public static void render(Canvas c, Certificate cert) {
        double cx = PAGE_W / 2;
        c.fillRect(0, 0, PAGE_W, PAGE_H, PAPER);
        drawBorder(c);

        spaced(c, "SCANGOLF  ·  MINIGOLF AM DRUCKER", cx, 31, 3.6, Canvas.FONT_SANS | Canvas.FONT_BOLD, 0.9, GOLD);
        spaced(c, "URKUNDE", cx, 55, 19, Canvas.FONT_SERIF | Canvas.FONT_BOLD, 3.2, GREEN);
        ornamentLine(c, cx, 63, 70);

        c.text("Hiermit wird feierlich bestätigt, dass", cx, 75, 5.2, Canvas.FONT_SERIF | Canvas.FONT_ITALIC,
                Canvas.ALIGN_CENTER, MUTED);
        Level l = cert.level;
        ArgbImage name = l.nameImage();
        if (name != null) {
            fitImage(c, name, cx, 91, 125, 22);
        } else {
            c.text("Unbekannter Profi", cx, 96, 12, Canvas.FONT_SERIF | Canvas.FONT_BOLD | Canvas.FONT_ITALIC,
                    Canvas.ALIGN_CENTER, INK);
        }
        c.line(cx - 62, 104, cx + 62, 104, 0.35, GOLD);

        c.text("die selbst gezeichnete Bahn", cx, 113, 5.2, Canvas.FONT_SERIF | Canvas.FONT_ITALIC,
                Canvas.ALIGN_CENTER, MUTED);
        ArgbImage lane = l.laneImage();
        if (lane != null) {
            fitImage(c, lane, cx, 122, 120, 13);
        } else {
            c.text("„Ohne Namen“", cx, 125, 8, Canvas.FONT_SERIF | Canvas.FONT_ITALIC, Canvas.ALIGN_CENTER, INK);
        }
        c.text(cert.holed ? "mit Bravour eingelocht hat." : "tapfer bespielt hat.", cx, 137, 5.2,
                Canvas.FONT_SERIF | Canvas.FONT_ITALIC, Canvas.ALIGN_CENTER, MUTED);

        drawLane(c, cert, 30, 143, 150);
        drawResult(c, cert, 247);
        drawFooter(c, cert);
    }

    // ------------------------------------------------------------ Rahmen und Zierrat

    private static void drawBorder(Canvas c) {
        c.strokeRoundRect(8, 8, PAGE_W - 16, PAGE_H - 16, 3, 1.8, GREEN);
        c.strokeRoundRect(11.5, 11.5, PAGE_W - 23, PAGE_H - 23, 2, 0.45, GOLD);
        c.strokeRoundRect(13, 13, PAGE_W - 26, PAGE_H - 26, 1.5, 0.2, GOLD_LIGHT);
        double[][] corners = {{11.5, 11.5}, {PAGE_W - 11.5, 11.5}, {11.5, PAGE_H - 11.5}, {PAGE_W - 11.5, PAGE_H - 11.5}};
        for (int i = 0; i < corners.length; i++) {
            double x = corners[i][0];
            double y = corners[i][1];
            diamond(c, x, y, 3.4, GOLD);
            diamond(c, x, y, 1.8, PAPER);
            c.fillCircle(x, y, 0.8, GREEN);
        }
    }

    private static void diamond(Canvas c, double x, double y, double r, int argb) {
        double[] xs = {x, x + r, x, x - r};
        double[] ys = {y - r, y, y + r, y};
        c.fillPolygon(xs, ys, 4, argb);
    }

    private static void ornamentLine(Canvas c, double cx, double y, double halfW) {
        c.line(cx - halfW, y, cx - 5, y, 0.4, GOLD);
        c.line(cx + 5, y, cx + halfW, y, 0.4, GOLD);
        diamond(c, cx, y, 2.4, GOLD);
        diamond(c, cx, y, 1.1, PAPER);
        c.fillCircle(cx - halfW, y, 0.8, GOLD);
        c.fillCircle(cx + halfW, y, 0.8, GOLD);
    }

    /** Text mit Sperrung (Zeichenabstand in mm), zentriert. */
    static void spaced(Canvas c, String s, double cx, double y, double size, int font, double tracking, int argb) {
        double total = 0;
        for (int i = 0; i < s.length(); i++) {
            total += c.textWidth(s.substring(i, i + 1), size, font);
        }
        total += tracking * (s.length() - 1);
        double x = cx - total / 2;
        for (int i = 0; i < s.length(); i++) {
            String ch = s.substring(i, i + 1);
            c.text(ch, x, y, size, font, Canvas.ALIGN_LEFT, argb);
            x += c.textWidth(ch, size, font) + tracking;
        }
    }

    private static void fitImage(Canvas c, ArgbImage img, double cx, double cy, double maxW, double maxH) {
        double s = Math.min(maxW / img.width(), maxH / img.height());
        double w = img.width() * s;
        double h = img.height() * s;
        c.image(img, cx - w / 2, cy - h / 2, w, h);
    }

    // ------------------------------------------------------------ Bahn mit Spur

    private static void drawLane(Canvas c, Certificate cert, double x, double y, double w) {
        Level l = cert.level;
        double s = w / l.widthMm();
        double h = l.heightMm() * s;
        c.fillRoundRect(x - 3.2, y - 3.2, w + 6.4, h + 6.4, 2.5, 0xFF6B4A2B);
        c.fillRect(x - 0.6, y - 0.6, w + 1.2, h + 1.2, 0xFF3D2A18);
        ArgbImage img = new LevelRasterizer(l).render(Math.max(2.0, s * c.deviceScale()));
        c.image(img, x, y, w, h);

        c.save();
        c.clipRect(x, y, w, h);
        // Spuren
        int n = cert.traces.size();
        for (int i = 0; i < n; i++) {
            ShotTrace t = cert.traces.get(i);
            int pc = t.pointCount();
            double[] xs = new double[pc];
            double[] ys = new double[pc];
            for (int k = 0; k < pc; k++) {
                xs[k] = x + t.x(k) * s;
                ys[k] = y + t.y(k) * s;
            }
            int col = TRACE_COLORS[i % TRACE_COLORS.length];
            c.polyline(xs, ys, pc, 0.9, 0x55000000);
            c.polyline(xs, ys, pc, 0.55, col);
            if (t.water() && pc > 0) {
                double ex = xs[pc - 1];
                double ey = ys[pc - 1];
                c.line(ex - 1.3, ey - 1.3, ex + 1.3, ey + 1.3, 0.5, 0xFFFFFFFF);
                c.line(ex - 1.3, ey + 1.3, ex + 1.3, ey - 1.3, 0.5, 0xFFFFFFFF);
            }
        }
        // Loch mit Fahne
        double hx = x + l.holeX() * s;
        double hy = y + l.holeY() * s;
        c.fillCircle(hx, hy, Rules.HOLE_RADIUS_MM * s + 0.4, 0x66FFFFFF);
        c.fillCircle(hx, hy, Rules.HOLE_RADIUS_MM * s, Colors.HOLE);
        c.line(hx, hy, hx, hy - 8, 0.45, 0xFFF5F5F5);
        double[] fx = {hx + 0.2, hx + 5, hx + 0.2};
        double[] fy = {hy - 8, hy - 6.6, hy - 5.2};
        c.fillPolygon(fx, fy, 3, Colors.FLAG);
        // Schlagnummern an den Startpunkten; gleiche Startpunkte (z. B. nach Wasser) werden
        // zu einem Schild "3·4" zusammengefasst, damit keine Nummer verdeckt wird.
        double[] mx = new double[n];
        double[] my = new double[n];
        String[] labels = new String[n];
        int[] firstShot = new int[n];
        int m = 0;
        for (int i = 0; i < n; i++) {
            ShotTrace t = cert.traces.get(i);
            if (t.pointCount() == 0) {
                continue;
            }
            double sx = x + t.x(0) * s;
            double sy = y + t.y(0) * s;
            int same = -1;
            for (int k = 0; k < m; k++) {
                if (Math.abs(mx[k] - sx) < 2.5 && Math.abs(my[k] - sy) < 2.5) {
                    same = k;
                }
            }
            if (same >= 0) {
                labels[same] = labels[same] + "\u00b7" + (i + 1);
            } else {
                mx[m] = sx;
                my[m] = sy;
                labels[m] = Integer.toString(i + 1);
                firstShot[m] = i;
                m++;
            }
        }
        for (int k = 0; k < m; k++) {
            double tw = c.textWidth(labels[k], 2.6, Canvas.FONT_BOLD);
            double bw = Math.max(3.8, tw + 1.6);
            int col = TRACE_COLORS[firstShot[k] % TRACE_COLORS.length];
            c.fillRoundRect(mx[k] - bw / 2, my[k] - 1.9, bw, 3.8, 1.9, 0xFF1F2A22);
            c.strokeRoundRect(mx[k] - bw / 2, my[k] - 1.9, bw, 3.8, 1.9, 0.3, col);
            c.text(labels[k], mx[k], my[k] + 0.95, 2.6, Canvas.FONT_BOLD, Canvas.ALIGN_CENTER, 0xFFFFFFFF);
        }
        c.restore();
        c.text("Gespielte Bahn mit Ballspur", x + w / 2, y + h + 8, 3.4, Canvas.FONT_SANS, Canvas.ALIGN_CENTER, MUTED);
    }

    // ------------------------------------------------------------ Ergebnis

    private static void drawResult(Canvas c, Certificate cert, double y) {
        double cx = PAGE_W / 2;
        int par = cert.level.par();
        stat(c, 40, y, "SCHLÄGE", Integer.toString(cert.strokes));
        stat(c, PAGE_W - 40, y, "PAR", Integer.toString(par));
        // Band mit Ergebnis
        String label = cert.resultLabel().toUpperCase();
        double size = 8.5;
        double tw = c.textWidth(label, size, Canvas.FONT_SERIF | Canvas.FONT_BOLD);
        while (tw > 84 && size > 4) {
            size -= 0.5;
            tw = c.textWidth(label, size, Canvas.FONT_SERIF | Canvas.FONT_BOLD);
        }
        double bw = Math.max(70, tw + 22);
        double bh = 15;
        double bx = cx - bw / 2;
        double by = y - 10;
        // Bandenden
        ribbonEnd(c, bx + 3, by + 4, -1);
        ribbonEnd(c, bx + bw - 3, by + 4, 1);
        c.fillRect(bx, by, bw, bh, GREEN);
        c.line(bx + 1.5, by + 1.5, bx + bw - 1.5, by + 1.5, 0.3, GOLD_LIGHT);
        c.line(bx + 1.5, by + bh - 1.5, bx + bw - 1.5, by + bh - 1.5, 0.3, GOLD_LIGHT);
        c.text(label, cx, by + bh / 2 + size * 0.36, size, Canvas.FONT_SERIF | Canvas.FONT_BOLD, Canvas.ALIGN_CENTER,
                0xFFFFF4D0);
        String rel = cert.holed ? ScoreNames.relative(cert.strokes, par) + " zum Par" : "nicht eingelocht";
        c.text(rel, cx, by + bh + 6, 4, Canvas.FONT_SANS | Canvas.FONT_BOLD, Canvas.ALIGN_CENTER, GOLD);
    }

    private static void ribbonEnd(Canvas c, double x, double y, int dir) {
        double len = 12 * dir;
        double[] xs = {x, x + len, x + len - 3.5 * dir, x + len, x};
        double[] ys = {y, y, y + 5.5, y + 11, y + 11};
        c.fillPolygon(xs, ys, 5, 0xFF163A21);
    }

    private static void stat(Canvas c, double cx, double y, String label, String value) {
        c.text(label, cx, y - 7, 3.3, Canvas.FONT_SANS | Canvas.FONT_BOLD, Canvas.ALIGN_CENTER, MUTED);
        c.text(value, cx, y + 6, 13, Canvas.FONT_SERIF | Canvas.FONT_BOLD, Canvas.ALIGN_CENTER, GREEN);
        c.line(cx - 12, y + 9, cx + 12, y + 9, 0.35, GOLD);
    }

    // ------------------------------------------------------------ Fußzeile

    private static void drawFooter(Canvas c, Certificate cert) {
        double y = 276;
        c.text(cert.dateText(), 45, y, 4.4, Canvas.FONT_SERIF, Canvas.ALIGN_CENTER, INK);
        c.line(22, y + 2.2, 68, y + 2.2, 0.3, MUTED);
        c.text("Datum", 45, y + 6.5, 3, Canvas.FONT_SANS, Canvas.ALIGN_CENTER, MUTED);

        // Unterschrift (Schnörkel)
        int m = 48;
        double[] xs = new double[m];
        double[] ys = new double[m];
        for (int i = 0; i < m; i++) {
            double t = i / (double) (m - 1);
            xs[i] = 145 + t * 40;
            ys[i] = y - 1.5 + Math.sin(t * 14) * 2.2 * (1 - t * 0.6) - t * 2;
        }
        c.polyline(xs, ys, m, 0.45, 0xFF22306B);
        c.line(142, y + 2.2, 188, y + 2.2, 0.3, MUTED);
        c.text("Platzwart", 165, y + 6.5, 3, Canvas.FONT_SANS, Canvas.ALIGN_CENTER, MUTED);

        seal(c, PAGE_W / 2, y - 1, 12.5);
    }

    private static void seal(Canvas c, double cx, double cy, double r) {
        int n = 48;
        double[] xs = new double[n];
        double[] ys = new double[n];
        for (int i = 0; i < n; i++) {
            double a = 2 * Math.PI * i / n;
            double rr = (i % 2 == 0) ? r : r * 0.9;
            xs[i] = cx + Math.cos(a) * rr;
            ys[i] = cy + Math.sin(a) * rr;
        }
        c.fillPolygon(xs, ys, n, GOLD);
        c.fillCircle(cx, cy, r * 0.8, 0xFFD9B44A);
        c.strokeCircle(cx, cy, r * 0.72, 0.3, 0xFFFFF1C2);
        // Fahne im Siegel
        c.fillEllipse(cx, cy + r * 0.38, r * 0.34, r * 0.1, 0xFF8A6A12);
        c.line(cx - r * 0.05, cy + r * 0.38, cx - r * 0.05, cy - r * 0.45, 0.55, 0xFFFFF7E0);
        double[] fx = {cx, cx + r * 0.42, cx};
        double[] fy = {cy - r * 0.45, cy - r * 0.3, cy - r * 0.15};
        c.fillPolygon(fx, fy, 3, 0xFFB3261E);
        c.text("SCANGOLF", cx, cy + r * 0.64, r * 0.2, Canvas.FONT_SANS | Canvas.FONT_BOLD, Canvas.ALIGN_CENTER,
                0xFF6B4E0A);
    }
}
