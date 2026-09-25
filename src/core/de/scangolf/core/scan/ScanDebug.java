package de.scangolf.core.scan;

import de.scangolf.core.level.CellType;
import de.scangolf.core.level.Grid;
import de.scangolf.core.level.Rules;
import de.scangolf.core.render.Canvas;
import de.scangolf.core.util.ArgbImage;

import java.util.List;

/**
 * Kontrollbild der Scan-Auswertung: verkleinerter Scan mit gefundenen Marken, Feldrahmen,
 * eingefärbtem Raster, Start/Loch und Par-Kästchen, daneben das entzerrte Raster und die Werte.
 */
public final class ScanDebug {

    public static final int PANEL_W = 460;

    private ScanDebug() {
    }

    /** Verkleinerungsfaktor, damit die lange Seite höchstens maxSide Pixel hat. */
    public static int previewFactor(int w, int h, int maxSide) {
        return Math.max(1, (Math.max(w, h) + maxSide - 1) / maxSide);
    }

    /** Verkleinertes Farbbild (Box-Filter). */
    public static ArgbImage preview(int[] argb, int w, int h, int factor) {
        int ow = w / factor;
        int oh = h / factor;
        int[] out = new int[ow * oh];
        int n = factor * factor;
        for (int oy = 0; oy < oh; oy++) {
            for (int ox = 0; ox < ow; ox++) {
                int sr = 0;
                int sg = 0;
                int sb = 0;
                for (int y = oy * factor; y < oy * factor + factor; y++) {
                    int row = y * w;
                    for (int x = ox * factor; x < ox * factor + factor; x++) {
                        int p = argb[row + x];
                        sr += (p >> 16) & 0xFF;
                        sg += (p >> 8) & 0xFF;
                        sb += p & 0xFF;
                    }
                }
                out[oy * ow + ox] = 0xFF000000 | ((sr / n) << 16) | ((sg / n) << 8) | (sb / n);
            }
        }
        return ArgbImage.adopt(ow, oh, out);
    }

    /** Benötigte Canvas-Größe für {@link #draw}. */
    public static int canvasWidth(ArgbImage preview) {
        return preview.width() + PANEL_W;
    }

    public static int canvasHeight(ArgbImage preview) {
        return Math.max(preview.height(), 760);
    }

    public static void draw(Canvas c, ScanResult r, SheetTemplate t, ArgbImage preview, int factor) {
        double s = 1.0 / factor;
        c.fillRect(0, 0, c.width(), c.height(), 0xFF202326);
        c.image(preview, 0, 0, preview.width(), preview.height());
        c.fillRect(0, 0, preview.width(), preview.height(), 0x55FFFFFF);

        List<double[]> cands = r.finderCandidatesImagePx();
        for (int i = 0; i < cands.size(); i++) {
            double[] f = cands.get(i);
            c.strokeCircle(f[0] * s, f[1] * s, Math.max(6, f[2] * 5 * s), 2, 0xFFFFB000);
        }
        Affine a = r.pageToImage();
        if (a != null) {
            drawOverlay(c, r, t, a, s);
        }
        drawPanel(c, r, preview.width() + 16);
    }

    private static void quad(Canvas c, Affine a, double s, double x, double y, double w, double h,
                             double[] xs, double[] ys) {
        xs[0] = a.mapX(x, y) * s;
        ys[0] = a.mapY(x, y) * s;
        xs[1] = a.mapX(x + w, y) * s;
        ys[1] = a.mapY(x + w, y) * s;
        xs[2] = a.mapX(x + w, y + h) * s;
        ys[2] = a.mapY(x + w, y + h) * s;
        xs[3] = a.mapX(x, y + h) * s;
        ys[3] = a.mapY(x, y + h) * s;
    }

    private static void outline(Canvas c, double[] xs, double[] ys, double lw, int argb) {
        for (int i = 0; i < 4; i++) {
            int j = (i + 1) % 4;
            c.line(xs[i], ys[i], xs[j], ys[j], lw, argb);
        }
    }

    private static void drawOverlay(Canvas c, ScanResult r, SheetTemplate t, Affine a, double s) {
        double[] xs = new double[4];
        double[] ys = new double[4];
        Grid g = r.grid();
        if (g != null) {
            for (int cy = 0; cy < g.rows; cy++) {
                for (int cx = 0; cx < g.cols; cx++) {
                    byte type = g.get(cx, cy);
                    if (type == CellType.EMPTY) {
                        continue;
                    }
                    quad(c, a, s, t.field.x + cx * g.cellMm, t.field.y + cy * g.cellMm, g.cellMm, g.cellMm, xs, ys);
                    c.fillPolygon(xs, ys, 4, overlayColor(type));
                }
            }
        }
        quad(c, a, s, t.field.x, t.field.y, t.field.w, t.field.h, xs, ys);
        outline(c, xs, ys, 2, 0xFF00C853);
        quad(c, a, s, t.nameBox.x, t.nameBox.y, t.nameBox.w, t.nameBox.h, xs, ys);
        outline(c, xs, ys, 2, r.nameImage() != null ? 0xFF2979FF : 0xFF9E9E9E);
        quad(c, a, s, t.laneBox.x, t.laneBox.y, t.laneBox.w, t.laneBox.h, xs, ys);
        outline(c, xs, ys, 2, r.laneImage() != null ? 0xFF2979FF : 0xFF9E9E9E);
        double[] fill = r.parFill();
        for (int i = 0; i < t.parBoxes.length; i++) {
            SheetTemplate.Box b = t.parBoxes[i];
            quad(c, a, s, b.x, b.y, b.w, b.h, xs, ys);
            boolean marked = fill != null && fill[i] >= ScanAnalyzer.PAR_MARK_FILL;
            outline(c, xs, ys, marked ? 3 : 1.5, marked ? 0xFFE91E63 : 0xFF607D8B);
            if (fill != null) {
                c.text(Math.round(fill[i] * 100) + "%", xs[3], ys[3] + 16, 13, Canvas.FONT_BOLD,
                        Canvas.ALIGN_LEFT, 0xFFC2185B);
            }
        }
        // Marken
        markLabel(c, a, s, t.tlX, t.tlY, "TL");
        markLabel(c, a, s, t.trX, t.trY, "TR");
        markLabel(c, a, s, t.blX, t.blY, "BL");
        if (!Double.isNaN(r.blockPredictedX())) {
            double px = r.blockPredictedX() * s;
            double py = r.blockPredictedY() * s;
            c.line(px - 10, py, px + 10, py, 2, 0xFF00E5FF);
            c.line(px, py - 10, px, py + 10, 2, 0xFF00E5FF);
        }
        if (!Double.isNaN(r.blockFoundX())) {
            c.strokeCircle(r.blockFoundX() * s, r.blockFoundY() * s, 9, 2.5, 0xFFD500F9);
        }
        // Start und Loch
        double scale = a.scale() * s;
        double[] st = r.start();
        if (st != null) {
            double px = a.mapX(t.field.x + st[0], t.field.y + st[1]) * s;
            double py = a.mapY(t.field.x + st[0], t.field.y + st[1]) * s;
            c.strokeCircle(px, py, Rules.BALL_RADIUS_MM * scale, 2.5, 0xFFFFFFFF);
            c.line(px - 12, py, px + 12, py, 2, 0xFFD50000);
            c.line(px, py - 12, px, py + 12, 2, 0xFFD50000);
        }
        double[] h = r.hole();
        if (h != null) {
            double px = a.mapX(t.field.x + h[0], t.field.y + h[1]) * s;
            double py = a.mapY(t.field.x + h[0], t.field.y + h[1]) * s;
            c.strokeCircle(px, py, Rules.HOLE_RADIUS_MM * scale, 2.5, 0xFFFFFFFF);
            c.line(px - 12, py, px + 12, py, 2, 0xFF00C853);
            c.line(px, py - 12, px, py + 12, 2, 0xFF00C853);
        }
    }

    private static void markLabel(Canvas c, Affine a, double s, double x, double y, String label) {
        double px = a.mapX(x, y) * s;
        double py = a.mapY(x, y) * s;
        c.strokeCircle(px, py, 14, 3, 0xFFD500F9);
        c.text(label, px + 16, py - 10, 16, Canvas.FONT_BOLD, Canvas.ALIGN_LEFT, 0xFFD500F9);
    }

    static int overlayColor(byte type) {
        switch (type) {
            case CellType.WALL: return 0xB0FF6D00;
            case CellType.WATER: return 0x9000B8D4;
            case CellType.START: return 0xC0FF1744;
            case CellType.HOLE: return 0xC000E676;
            default: return 0;
        }
    }

    static int panelColor(byte type) {
        switch (type) {
            case CellType.WALL: return 0xFF263238;
            case CellType.WATER: return 0xFF1E88E5;
            case CellType.START: return 0xFFE53935;
            case CellType.HOLE: return 0xFF43A047;
            default: return 0xFFFAFAFA;
        }
    }

    private static void drawPanel(Canvas c, ScanResult r, double x0) {
        int fg = 0xFFECEFF1;
        double y = 34;
        c.text("ScanGolf – Kontrollbild", x0, y, 22, Canvas.FONT_BOLD, Canvas.ALIGN_LEFT, fg);
        y += 34;
        boolean ok = r.isOk();
        c.fillRoundRect(x0, y - 22, 150, 30, 8, ok ? 0xFF2E7D32 : 0xFFC62828);
        c.text(ok ? "ERKANNT" : "FEHLER", x0 + 75, y, 18, Canvas.FONT_BOLD, Canvas.ALIGN_CENTER, 0xFFFFFFFF);
        y += 30;
        List<ScanError> errs = r.errors();
        for (int i = 0; i < errs.size(); i++) {
            y = wrap(c, errs.get(i).name() + ": " + errs.get(i).message(), x0, y, 14, 0xFFFF8A80);
        }
        List<ScanWarning> warns = r.warnings();
        for (int i = 0; i < warns.size(); i++) {
            y = wrap(c, "Hinweis: " + warns.get(i).message(), x0, y, 14, 0xFFFFE082);
        }
        if (r.detail().length() > 0) {
            y = wrap(c, r.detail(), x0, y, 14, 0xFFB0BEC5);
        }
        y += 8;
        String[] lines = {
            "Bild: " + r.imageWidth() + " x " + r.imageHeight() + " px, Faktor " + r.workFactor(),
            "Auflösung: " + fmt(r.dpi(), 0) + " dpi, Drehung " + fmt(r.rotationDegrees(), 2) + "°",
            "Block-Abweichung: " + fmt(r.blockResidualMm(), 2) + " mm",
            "Start: " + pt(r.start()) + "   Loch: " + pt(r.hole()),
            "Par: " + r.par() + "   Füllgrade: " + fills(r.parFill()),
            "Zeit: " + r.millisTotal() + " ms, Puffer " + fmt(r.workingBytes() / 1e6, 1) + " MB",
        };
        for (int i = 0; i < lines.length; i++) {
            c.text(lines[i], x0, y, 14, Canvas.FONT_SANS, Canvas.ALIGN_LEFT, fg);
            y += 21;
        }
        c.fillRect(x0 + 330, y - 5 * 21 - 16, 40, 18, 0xFF000000 | r.paperRgb());
        Grid g = r.grid();
        if (g != null) {
            y += 12;
            double cs = Math.min((PANEL_W - 32.0) / g.cols, 1.8);
            c.text("Entzerrtes Raster", x0, y, 14, Canvas.FONT_BOLD, Canvas.ALIGN_LEFT, fg);
            y += 8;
            c.fillRect(x0, y, g.cols * cs, g.rows * cs, 0xFFFAFAFA);
            for (int cy = 0; cy < g.rows; cy++) {
                for (int cx = 0; cx < g.cols; cx++) {
                    byte type = g.get(cx, cy);
                    if (type != CellType.EMPTY) {
                        c.fillRect(x0 + cx * cs, y + cy * cs, cs, cs, panelColor(type));
                    }
                }
            }
            y += g.rows * cs + 22;
        }
        if (r.nameImage() != null) {
            y = crop(c, "Name", r.nameImage(), x0, y);
        }
        if (r.laneImage() != null) {
            crop(c, "Bahn", r.laneImage(), x0, y);
        }
    }

    private static double crop(Canvas c, String label, ArgbImage img, double x0, double y) {
        double maxW = PANEL_W - 90.0;
        double sc = Math.min(maxW / img.width(), 60.0 / img.height());
        c.text(label, x0, y + 20, 14, Canvas.FONT_BOLD, Canvas.ALIGN_LEFT, 0xFFECEFF1);
        c.fillRect(x0 + 50, y, img.width() * sc, img.height() * sc, 0xFFFFF8E1);
        c.image(img, x0 + 50, y, img.width() * sc, img.height() * sc);
        return y + img.height() * sc + 10;
    }

    private static double wrap(Canvas c, String s, double x, double y, double size, int argb) {
        double maxW = PANEL_W - 30.0;
        String rest = s;
        while (rest.length() > 0) {
            int cut = rest.length();
            while (cut > 1 && c.textWidth(rest.substring(0, cut), size, Canvas.FONT_SANS) > maxW) {
                int sp = rest.lastIndexOf(' ', cut - 1);
                cut = sp > 0 ? sp : cut - 1;
            }
            c.text(rest.substring(0, cut), x, y, size, Canvas.FONT_SANS, Canvas.ALIGN_LEFT, argb);
            y += size * 1.4;
            rest = rest.substring(cut).trim();
        }
        return y;
    }

    private static String fmt(double v, int digits) {
        if (Double.isNaN(v)) {
            return "–";
        }
        double p = Math.pow(10, digits);
        double rv = Math.round(v * p) / p;
        return digits == 0 ? Long.toString(Math.round(v)) : Double.toString(rv);
    }

    private static String pt(double[] p) {
        return p == null ? "–" : fmt(p[0], 1) + "/" + fmt(p[1], 1);
    }

    private static String fills(double[] f) {
        if (f == null) {
            return "–";
        }
        StringBuffer sb = new StringBuffer();
        for (int i = 0; i < f.length; i++) {
            if (i > 0) {
                sb.append(' ');
            }
            sb.append(Math.round(f[i] * 100)).append('%');
        }
        return sb.toString();
    }
}
