package de.scangolf.core.scan;

import de.scangolf.core.level.CellType;
import de.scangolf.core.level.Grid;
import de.scangolf.core.level.Level;
import de.scangolf.core.level.LevelGeometry;
import de.scangolf.core.level.Rules;
import de.scangolf.core.util.ArgbImage;

import java.util.ArrayList;
import java.util.List;

/**
 * Wertet einen Scan der ScanGolf-Vorlage aus.
 *
 * Eingabe ist ein ARGB-Bild (int[], Breite, Höhe) in beliebiger Auflösung und Drehung.
 * Das Bild wird nicht kopiert: Für die Markensuche entsteht ein kleines Graustufenbild,
 * alles andere wird über die affine Abbildung direkt im Original abgetastet.
 */
public final class ScanAnalyzer {

    /** Rasterweite in mm (240 x 144 Zellen bei der Standardvorlage). */
    public static final double CELL_MM = 1.0;
    /** Unterabtastung je Zelle und Achse (4 x 4 Proben je Zelle). */
    static final int SUB = 4;
    /** Zellen am Feldrand, die ignoriert werden (dort liegt der gedruckte Rahmen). */
    static final int MARGIN_CELLS = 2;
    /** Auflösung der Namens-/Bahnausschnitte. */
    static final double CROP_PX_PER_MM = 8.0;
    /** Mindestgröße eines roten/grünen Punkts in Zellen (mm²). */
    static final int MIN_DOT_CELLS = 6;
    /** Füllgrad, ab dem ein Par-Kästchen als angekreuzt gilt. */
    static final double PAR_MARK_FILL = 0.08;

    // Klassen einer Einzelprobe
    static final int PAPER = 0;
    static final int INK = 1;
    static final int RED = 2;
    static final int GREEN = 3;
    static final int BLUE = 4;
    static final int OTHER = 5;

    // Ergebniscodes der Blattsuche
    private static final int FOUND = 0;
    private static final int NOTHING = 1;
    private static final int PAIR_ONLY = 2;
    private static final int MIRRORED = 3;
    private static final int BLOCK_MISSING = 4;
    private static final int BLOCK_WRONG = 5;

    private final SheetTemplate t;

    // Zustand während einer Auswertung
    private int[] px;
    private int imgW;
    private int imgH;
    private Affine map;
    private int kernel;
    private final int[] lutR = new int[256];
    private final int[] lutG = new int[256];
    private final int[] lutB = new int[256];
    private final int[] rgbTmp = new int[3];

    public ScanAnalyzer(SheetTemplate template) {
        this.t = template;
    }

    public ScanAnalyzer() {
        this(SheetTemplate.loadDefault());
    }

    public SheetTemplate template() {
        return t;
    }

    public ScanResult analyze(int[] argb, int width, int height) {
        long t0 = System.currentTimeMillis();
        ScanResult r = new ScanResult();
        r.imageWidth = width;
        r.imageHeight = height;
        try {
            if (argb == null || width <= 0 || height <= 0 || argb.length < width * height
                    || Math.max(width, height) < 600 || Math.min(width, height) < 400) {
                r.error(ScanError.IMAGE_TOO_SMALL);
                return r;
            }
            px = argb;
            imgW = width;
            imgH = height;
            if (!findSheet(r)) {
                r.msMarks = System.currentTimeMillis() - t0;
                return r;
            }
            r.msMarks = System.currentTimeMillis() - t0;
            map = r.pageToImage;
            kernel = Math.max(1, (int) Math.round(map.scale() * CELL_MM / SUB));
            estimatePaper(r);
            Grid g = classifyField(r);
            cleanup(g, r);
            r.msGrid = System.currentTimeMillis() - t0 - r.msMarks;
            findDotsAndCheck(g, r);
            r.grid = g;
            readPar(r);
            r.nameImage = crop(t.nameBox, r);
            r.laneImage = crop(t.laneBox, r);
            if (r.errors.isEmpty()) {
                r.level = new Level(g, r.start[0], r.start[1], r.hole[0], r.hole[1], r.par,
                        r.nameImage, r.laneImage);
            }
            return r;
        } finally {
            px = null;
            map = null;
            r.msTotal = System.currentTimeMillis() - t0;
        }
    }

    // ================================================================ Blatt finden

    private boolean findSheet(ScanResult r) {
        double pxPerMmGuess = Math.max(imgW, imgH) / t.pageW;
        int f = Math.max(1, (int) Math.floor(t.module * pxPerMmGuess / 6.0));
        while (true) {
            WorkImage wi = WorkImage.downsample(px, imgW, imgH, f);
            r.workingBytes += wi.gray.length;
            r.workFactor = f;
            int res = locate(wi, r);
            if (res == FOUND) {
                return true;
            }
            if (res == NOTHING && f > 1) {
                f = f / 2;
                continue;
            }
            switch (res) {
                case PAIR_ONLY:
                case BLOCK_MISSING:
                    r.error(ScanError.MARKS_INCOMPLETE);
                    break;
                case MIRRORED:
                    r.detail = "Blatt ist gespiegelt";
                    r.error(ScanError.SHEET_NOT_FOUND);
                    break;
                case BLOCK_WRONG:
                    r.detail = "Kontrollblock unten rechts passt nicht zur Lage der Marken";
                    r.error(ScanError.SHEET_NOT_FOUND);
                    break;
                default:
                    r.error(ScanError.SHEET_NOT_FOUND);
            }
            return false;
        }
    }

    private int locate(WorkImage wi, ScanResult r) {
        int[] hist = new int[256];
        for (int i = 0; i < wi.gray.length; i++) {
            hist[wi.gray[i] & 0xFF]++;
        }
        int dark = WorkImage.percentile(hist, wi.gray.length, 0.01);
        int paper = WorkImage.percentile(hist, wi.gray.length, 0.90);
        if (paper - dark < 50) {
            return NOTHING;
        }
        List<FinderPattern> all = new FinderDetector(wi, (dark + paper + 1) / 2).detect();
        List<FinderPattern> cands = new ArrayList<FinderPattern>();
        for (int i = 0; i < all.size(); i++) {
            FinderPattern fp = all.get(i);
            if (fp.count >= 2 && fp.module >= 2.0) {
                cands.add(fp);
            }
        }
        sortByCount(cands);
        while (cands.size() > 16) {
            cands.remove(cands.size() - 1);
        }
        r.finderCandidates = cands;

        double expRatio = t.tlToTr() / t.tlToBl();
        double bestScore = Double.MAX_VALUE;
        FinderPattern[] best = null;
        boolean bestMirrored = false;
        int n = cands.size();
        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                for (int k = j + 1; k < n; k++) {
                    FinderPattern[] tri = orderTriple(cands.get(i), cands.get(j), cands.get(k));
                    FinderPattern tl = tri[0];
                    FinderPattern tr = tri[1];
                    FinderPattern bl = tri[2];
                    double mMin = Math.min(tl.module, Math.min(tr.module, bl.module));
                    double mMax = Math.max(tl.module, Math.max(tr.module, bl.module));
                    if (mMax > mMin * 1.4) {
                        continue;
                    }
                    double ax = tr.x - tl.x;
                    double ay = tr.y - tl.y;
                    double bx = bl.x - tl.x;
                    double by = bl.y - tl.y;
                    double la = Math.sqrt(ax * ax + ay * ay);
                    double lb = Math.sqrt(bx * bx + by * by);
                    double ratioErr = Math.abs((la / lb) / expRatio - 1);
                    double cos = Math.abs((ax * bx + ay * by) / (la * lb));
                    double scaleDist = la / t.tlToTr();
                    double scaleMod = (tl.module + tr.module + bl.module) / 3.0 / t.module;
                    double scaleErr = Math.abs(scaleDist / scaleMod - 1);
                    if (ratioErr > 0.06 || cos > 0.08 || scaleErr > 0.35) {
                        continue;
                    }
                    double score = ratioErr + cos + 0.2 * scaleErr;
                    if (score < bestScore) {
                        bestScore = score;
                        best = tri;
                        bestMirrored = ax * by - ay * bx < 0;
                    }
                }
            }
        }
        if (best == null) {
            return hasValidPair(cands) ? PAIR_ONLY : NOTHING;
        }
        if (bestMirrored) {
            return MIRRORED;
        }
        int f = wi.factor;
        r.tl = best[0];
        r.tr = best[1];
        r.bl = best[2];
        r.pageToImage = Affine.fromTriangles(t.tlX, t.tlY, t.trX, t.trY, t.blX, t.blY,
                best[0].x * f, best[0].y * f, best[1].x * f, best[1].y * f, best[2].x * f, best[2].y * f);
        return checkBlock(wi, r);
    }

    /** Ordnet drei Punkte als {tl, tr, bl}: tl liegt gegenüber der längsten Seite, tl-tr ist der längere Schenkel. */
    private static FinderPattern[] orderTriple(FinderPattern p, FinderPattern q, FinderPattern s) {
        double pq = d2(p, q);
        double ps = d2(p, s);
        double qs = d2(q, s);
        FinderPattern tl;
        FinderPattern a;
        FinderPattern b;
        if (qs >= pq && qs >= ps) {
            tl = p;
            a = q;
            b = s;
        } else if (ps >= pq && ps >= qs) {
            tl = q;
            a = p;
            b = s;
        } else {
            tl = s;
            a = p;
            b = q;
        }
        if (d2(tl, a) >= d2(tl, b)) {
            return new FinderPattern[] {tl, a, b};
        }
        return new FinderPattern[] {tl, b, a};
    }

    private static double d2(FinderPattern a, FinderPattern b) {
        double dx = a.x - b.x;
        double dy = a.y - b.y;
        return dx * dx + dy * dy;
    }

    private boolean hasValidPair(List<FinderPattern> c) {
        double[] lens = {t.tlToTr(), t.tlToBl(), t.trToBl()};
        for (int i = 0; i < c.size(); i++) {
            for (int j = i + 1; j < c.size(); j++) {
                FinderPattern a = c.get(i);
                FinderPattern b = c.get(j);
                if (Math.max(a.module, b.module) > Math.min(a.module, b.module) * 1.4) {
                    continue;
                }
                double pxPerMm = (a.module + b.module) / 2.0 / t.module;
                double mm = Math.sqrt(d2(a, b)) / pxPerMm;
                for (int k = 0; k < lens.length; k++) {
                    if (Math.abs(mm / lens[k] - 1) < 0.08) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static void sortByCount(List<FinderPattern> l) {
        for (int i = 1; i < l.size(); i++) {
            FinderPattern v = l.get(i);
            int j = i - 1;
            while (j >= 0 && l.get(j).count < v.count) {
                l.set(j + 1, l.get(j));
                j--;
            }
            l.set(j + 1, v);
        }
    }

    /** Prüft den vollen Block unten rechts an der vorhergesagten Stelle. */
    private int checkBlock(WorkImage wi, ScanResult r) {
        Affine a = r.pageToImage;
        int f = wi.factor;
        double px0 = a.mapX(t.brX, t.brY);
        double py0 = a.mapY(t.brX, t.brY);
        r.blockPredX = px0;
        r.blockPredY = py0;
        double bx = px0 / f;
        double by = py0 / f;
        double size = t.blockSize * a.scale() / f;
        double inner = size * 0.6;
        if (bx - inner < 0 || by - inner < 0 || bx + inner >= wi.w || by + inner >= wi.h) {
            return BLOCK_MISSING;
        }
        int half = (int) Math.ceil(size * 1.25);
        int x0 = Math.max(0, (int) bx - half);
        int y0 = Math.max(0, (int) by - half);
        int x1 = Math.min(wi.w - 1, (int) bx + half);
        int y1 = Math.min(wi.h - 1, (int) by + half);
        int ww = x1 - x0 + 1;
        int hh = y1 - y0 + 1;
        int[] hist = new int[256];
        for (int y = y0; y <= y1; y++) {
            for (int x = x0; x <= x1; x++) {
                hist[wi.get(x, y)]++;
            }
        }
        // Schwelle lokal: Mitte zwischen dunklem und hellem Anteil des Fensters
        int dark = WorkImage.percentile(hist, ww * hh, 0.05);
        int light = WorkImage.percentile(hist, ww * hh, 0.95);
        if (light - dark < 50) {
            return BLOCK_MISSING;
        }
        int thr = (dark + light) / 2;
        // Startpunkt: dunkles Pixel nahe der Vorhersage
        int seed = -1;
        double bestD = size * 0.4;
        int rr = (int) Math.ceil(bestD);
        for (int dy = -rr; dy <= rr; dy++) {
            for (int dx = -rr; dx <= rr; dx++) {
                int x = (int) bx + dx;
                int y = (int) by + dy;
                if (x < x0 || y < y0 || x > x1 || y > y1 || wi.get(x, y) >= thr) {
                    continue;
                }
                double d = Math.sqrt((double) (dx * dx + dy * dy));
                if (d <= bestD) {
                    bestD = d;
                    seed = (y - y0) * ww + (x - x0);
                }
            }
        }
        if (seed < 0) {
            return BLOCK_MISSING;
        }
        boolean[] seen = new boolean[ww * hh];
        int[] stack = new int[ww * hh];
        r.workingBytes += ww * hh * 5L;
        int sp = 0;
        stack[sp++] = seed;
        seen[seed] = true;
        long area = 0;
        double sx = 0;
        double sy = 0;
        boolean touches = false;
        while (sp > 0) {
            int i = stack[--sp];
            int x = i % ww;
            int y = i / ww;
            area++;
            sx += x + 0.5;
            sy += y + 0.5;
            if (x == 0 || y == 0 || x == ww - 1 || y == hh - 1) {
                touches = true;
            }
            for (int k = 0; k < 4; k++) {
                int nx = x + (k == 0 ? 1 : k == 1 ? -1 : 0);
                int ny = y + (k == 2 ? 1 : k == 3 ? -1 : 0);
                if (nx < 0 || ny < 0 || nx >= ww || ny >= hh) {
                    continue;
                }
                int j = ny * ww + nx;
                if (!seen[j] && wi.get(nx + x0, ny + y0) < thr) {
                    seen[j] = true;
                    stack[sp++] = j;
                }
            }
        }
        double expected = size * size;
        if (area < expected * 0.5) {
            return BLOCK_MISSING;
        }
        if (touches || area > expected * 1.8) {
            return BLOCK_WRONG;
        }
        r.blockFoundX = (x0 + sx / area) * f;
        r.blockFoundY = (y0 + sy / area) * f;
        double dx = r.blockFoundX - px0;
        double dy = r.blockFoundY - py0;
        r.blockResidualMm = Math.sqrt(dx * dx + dy * dy) / a.scale();
        if (r.blockResidualMm > 2.5) {
            return BLOCK_WRONG;
        }
        if (r.blockResidualMm > 1.2) {
            r.warn(ScanWarning.BLOCK_OFFSET);
        }
        return FOUND;
    }

    // ================================================================ Abtasten

    /** Mittelwert R,G,B eines k x k-Blocks um die Seitenposition (u, v) in mm. */
    private void sample(double u, double v, int k, int[] out) {
        double fx = map.mapX(u, v);
        double fy = map.mapY(u, v);
        int x0 = (int) Math.floor(fx - k * 0.5 + 0.5);
        int y0 = (int) Math.floor(fy - k * 0.5 + 0.5);
        if (x0 < 0) {
            x0 = 0;
        } else if (x0 + k > imgW) {
            x0 = imgW - k;
        }
        if (y0 < 0) {
            y0 = 0;
        } else if (y0 + k > imgH) {
            y0 = imgH - k;
        }
        int sr = 0;
        int sg = 0;
        int sb = 0;
        for (int y = y0; y < y0 + k; y++) {
            int row = y * imgW;
            for (int x = x0; x < x0 + k; x++) {
                int p = px[row + x];
                sr += (p >> 16) & 0xFF;
                sg += (p >> 8) & 0xFF;
                sb += p & 0xFF;
            }
        }
        int n = k * k;
        out[0] = sr / n;
        out[1] = sg / n;
        out[2] = sb / n;
    }

    /** Papierweiß: mittlere Farbe der hellsten 20 % der Zellmitten im Feld. */
    private void estimatePaper(ScanResult r) {
        int cols = (int) Math.round(t.field.w / CELL_MM);
        int rows = (int) Math.round(t.field.h / CELL_MM);
        int[] rgb = new int[cols * rows];
        r.workingBytes += rgb.length * 4L;
        int[] hist = new int[256];
        for (int cy = 0; cy < rows; cy++) {
            for (int cx = 0; cx < cols; cx++) {
                sample(t.field.x + (cx + 0.5) * CELL_MM, t.field.y + (cy + 0.5) * CELL_MM, kernel, rgbTmp);
                int v = (rgbTmp[0] << 16) | (rgbTmp[1] << 8) | rgbTmp[2];
                rgb[cy * cols + cx] = v;
                hist[WorkImage.luminance(v)]++;
            }
        }
        int l80 = WorkImage.percentile(hist, rgb.length, 0.80);
        long sr = 0;
        long sg = 0;
        long sb = 0;
        int n = 0;
        for (int i = 0; i < rgb.length; i++) {
            if (WorkImage.luminance(rgb[i]) >= l80) {
                sr += (rgb[i] >> 16) & 0xFF;
                sg += (rgb[i] >> 8) & 0xFF;
                sb += rgb[i] & 0xFF;
                n++;
            }
        }
        int wr = Math.max(20, (int) (sr / n));
        int wg = Math.max(20, (int) (sg / n));
        int wb = Math.max(20, (int) (sb / n));
        r.paperRgb = (wr << 16) | (wg << 8) | wb;
        for (int v = 0; v < 256; v++) {
            lutR[v] = Math.min(255, v * 255 / wr);
            lutG[v] = Math.min(255, v * 255 / wg);
            lutB[v] = Math.min(255, v * 255 / wb);
        }
    }

    /** Klassifiziert eine Probe (Rohwerte), relativ zum geschätzten Papierweiß. */
    int classify(int r0, int g0, int b0) {
        int r = lutR[r0];
        int g = lutG[g0];
        int b = lutB[b0];
        int mx = Math.max(r, Math.max(g, b));
        int mn = Math.min(r, Math.min(g, b));
        int c = mx - mn;
        int lum = (77 * r + 150 * g + 29 * b) >> 8;
        if (c >= 40 && c * 4 >= mx) {
            int hue;
            if (mx == r) {
                hue = 60 * (g - b) / c;
                if (hue < 0) {
                    hue += 360;
                }
            } else if (mx == g) {
                hue = 120 + 60 * (b - r) / c;
            } else {
                hue = 240 + 60 * (r - g) / c;
            }
            if (hue < 25 || hue >= 330) {
                return RED;
            }
            if (hue >= 75 && hue < 170) {
                return GREEN;
            }
            if (hue >= 185 && hue < 265) {
                return lum < 60 ? INK : BLUE;
            }
            return lum < 110 ? INK : OTHER;
        }
        return lum < 153 ? INK : PAPER;
    }

    private Grid classifyField(ScanResult r) {
        int cols = (int) Math.round(t.field.w / CELL_MM);
        int rows = (int) Math.round(t.field.h / CELL_MM);
        Grid g = new Grid(cols, rows, CELL_MM);
        r.workingBytes += g.size();
        int[] counts = new int[6];
        double step = CELL_MM / SUB;
        for (int cy = 0; cy < rows; cy++) {
            for (int cx = 0; cx < cols; cx++) {
                if (cx < MARGIN_CELLS || cy < MARGIN_CELLS || cx >= cols - MARGIN_CELLS || cy >= rows - MARGIN_CELLS) {
                    continue;
                }
                for (int k = 0; k < counts.length; k++) {
                    counts[k] = 0;
                }
                double u0 = t.field.x + cx * CELL_MM + step * 0.5;
                double v0 = t.field.y + cy * CELL_MM + step * 0.5;
                for (int sy = 0; sy < SUB; sy++) {
                    for (int sx = 0; sx < SUB; sx++) {
                        sample(u0 + sx * step, v0 + sy * step, kernel, rgbTmp);
                        counts[classify(rgbTmp[0], rgbTmp[1], rgbTmp[2])]++;
                    }
                }
                g.set(cx, cy, decideCell(counts));
            }
        }
        return g;
    }

    static byte decideCell(int[] counts) {
        int ink = counts[INK];
        int best = RED;
        if (counts[GREEN] > counts[best]) {
            best = GREEN;
        }
        if (counts[BLUE] > counts[best]) {
            best = BLUE;
        }
        int col = counts[best];
        byte colorType = best == RED ? CellType.START : best == GREEN ? CellType.HOLE : CellType.WATER;
        if (col >= 4 && col >= ink) {
            return colorType;
        }
        if (ink >= 3) {
            return CellType.WALL;
        }
        if (col >= 4) {
            return colorType;
        }
        return CellType.EMPTY;
    }

    // ================================================================ Aufräumen

    private void cleanup(Grid g, ScanResult r) {
        r.workingBytes += g.size() * 12L;
        GridOps.removeSmall(g, CellType.WALL, 4);
        GridOps.removeSmall(g, CellType.START, 3);
        GridOps.removeSmall(g, CellType.HOLE, 3);
        GridOps.removeSmall(g, CellType.WATER, 4);
        GridOps.close(g, CellType.WATER, 2, false);
        GridOps.fillEnclosedByWater(g, g.size() / 4);
        GridOps.close(g, CellType.WALL, 2, true);
    }

    // ================================================================ Start, Loch, Prüfungen

    /** Rote bzw. grüne Gruppen (nahe Flecken zusammengefasst); Rückgabe je Gruppe {Anzahl, Summe x, Summe y}. */
    private static List<double[]> dotGroups(Grid g, byte type, int[] groupOfCell) {
        boolean[] m = GridOps.mask(g, type);
        boolean[] dil = GridOps.dilate(m, g.cols, g.rows, 3, false);
        int[][] sz = new int[1][];
        int[] lab = GridOps.label(dil, g.cols, g.rows, sz);
        double[][] acc = new double[sz[0].length][3];
        for (int i = 0; i < m.length; i++) {
            groupOfCell[i] = -1;
            if (m[i]) {
                int L = lab[i];
                groupOfCell[i] = L;
                acc[L][0] += 1;
                acc[L][1] += (i % g.cols) + 0.5;
                acc[L][2] += (i / g.cols) + 0.5;
            }
        }
        List<double[]> out = new ArrayList<double[]>();
        for (int k = 0; k < acc.length; k++) {
            if (acc[k][0] >= MIN_DOT_CELLS) {
                out.add(new double[] {acc[k][0], acc[k][1], acc[k][2], k});
            }
        }
        return out;
    }

    /** Wählt die einzige große Gruppe; setzt alle anderen Zellen des Typs auf EMPTY. Rückgabe Mittelpunkt in mm. */
    private static double[] pickDot(Grid g, byte type, ScanResult r, ScanError none, ScanError many) {
        int[] groupOf = new int[g.size()];
        List<double[]> groups = dotGroups(g, type, groupOf);
        if (groups.isEmpty()) {
            r.error(none);
            return null;
        }
        if (groups.size() > 1) {
            r.error(many);
            return null;
        }
        double[] gr = groups.get(0);
        int keep = (int) gr[3];
        for (int i = 0; i < groupOf.length; i++) {
            if (groupOf[i] >= 0 && groupOf[i] != keep) {
                g.setIndex(i, CellType.EMPTY);
            }
        }
        return new double[] {gr[1] / gr[0] * g.cellMm, gr[2] / gr[0] * g.cellMm};
    }

    private void findDotsAndCheck(Grid g, ScanResult r) {
        if (g.count(CellType.WALL) == 0 && g.count(CellType.START) == 0 && g.count(CellType.HOLE) == 0
                && g.count(CellType.WATER) == 0) {
            r.error(ScanError.FIELD_EMPTY);
            return;
        }
        double[] s = pickDot(g, CellType.START, r, ScanError.NO_START, ScanError.MULTIPLE_STARTS);
        double[] h = pickDot(g, CellType.HOLE, r, ScanError.NO_HOLE, ScanError.MULTIPLE_HOLES);
        r.startDrawn = s;
        r.start = s;
        r.hole = h;
        if (s != null) {
            byte under = g.atMm(s[0], s[1]);
            if (under == CellType.WALL) {
                r.error(ScanError.START_IN_WALL);
            } else if (under == CellType.WATER) {
                r.error(ScanError.START_IN_WATER);
            } else {
                double[] fit = LevelGeometry.nearestFit(g, s[0], s[1], Rules.BALL_RADIUS_MM, 5.0);
                if (fit == null) {
                    r.error(ScanError.START_IN_WALL);
                } else if (fit[0] != s[0] || fit[1] != s[1]) {
                    r.warn(ScanWarning.START_MOVED);
                    r.start = fit;
                }
            }
        }
        if (h != null) {
            byte under = g.atMm(h[0], h[1]);
            if (under == CellType.WALL) {
                r.error(ScanError.HOLE_IN_WALL);
            } else if (under == CellType.WATER) {
                r.error(ScanError.HOLE_IN_WATER);
            }
        }
        if (r.errors.isEmpty()) {
            r.workingBytes += g.size() * 9L;
            if (!LevelGeometry.reachable(g, r.start[0], r.start[1], h[0], h[1],
                    Rules.BALL_RADIUS_MM, Rules.HOLE_CAPTURE_RADIUS_MM)) {
                r.error(ScanError.HOLE_UNREACHABLE);
            }
            double dx = r.start[0] - h[0];
            double dy = r.start[1] - h[1];
            if (Math.sqrt(dx * dx + dy * dy) < 15.0) {
                r.warn(ScanWarning.START_NEAR_HOLE);
            }
        }
    }

    // ================================================================ Par

    private void readPar(ScanResult r) {
        SheetTemplate.Box[] boxes = t.parBoxes;
        r.parFill = new double[boxes.length];
        double step = 0.25;
        int k = Math.max(1, (int) Math.round(map.scale() * step));
        int best = -1;
        int marked = 0;
        for (int i = 0; i < boxes.length; i++) {
            SheetTemplate.Box b = boxes[i];
            int ink = 0;
            int total = 0;
            for (double v = b.y + b.inset + step / 2; v < b.y + b.h - b.inset; v += step) {
                for (double u = b.x + b.inset + step / 2; u < b.x + b.w - b.inset; u += step) {
                    sample(u, v, k, rgbTmp);
                    if (classify(rgbTmp[0], rgbTmp[1], rgbTmp[2]) != PAPER) {
                        ink++;
                    }
                    total++;
                }
            }
            r.parFill[i] = total == 0 ? 0 : (double) ink / total;
            if (r.parFill[i] >= PAR_MARK_FILL) {
                marked++;
                if (best < 0 || b.par < boxes[best].par) {
                    best = i;
                }
            }
        }
        if (marked == 0) {
            r.par = t.parDefault;
            r.warn(ScanWarning.PAR_NOT_MARKED);
        } else {
            r.par = boxes[best].par;
            if (marked > 1) {
                r.warn(ScanWarning.PAR_MULTIPLE);
            }
        }
    }

    // ================================================================ Namens-/Bahnfeld

    /**
     * Bildausschnitt eines Schreibfelds als "Tinte mit Alpha" (Papier wird durchsichtig),
     * zugeschnitten auf die Schrift. Leeres Feld: null.
     */
    private ArgbImage crop(SheetTemplate.Box b, ScanResult r) {
        double ppm = CROP_PX_PER_MM;
        double x0 = b.x + b.inset;
        double y0 = b.y + b.inset;
        int ow = (int) Math.round((b.w - 2 * b.inset) * ppm);
        int oh = (int) Math.round((b.h - 2 * b.inset) * ppm);
        int k = Math.max(1, (int) Math.round(map.scale() / ppm));
        int[] out = new int[ow * oh];
        r.workingBytes += out.length * 4L;
        int ink = 0;
        int minX = ow;
        int minY = oh;
        int maxX = -1;
        int maxY = -1;
        for (int oy = 0; oy < oh; oy++) {
            for (int ox = 0; ox < ow; ox++) {
                sample(x0 + (ox + 0.5) / ppm, y0 + (oy + 0.5) / ppm, k, rgbTmp);
                int rr = lutR[rgbTmp[0]];
                int gg = lutG[rgbTmp[1]];
                int bb = lutB[rgbTmp[2]];
                int lum = (77 * rr + 150 * gg + 29 * bb) >> 8;
                int alpha = (230 - lum) * 255 / 140;
                if (alpha <= 0) {
                    continue;
                }
                if (alpha > 255) {
                    alpha = 255;
                }
                out[oy * ow + ox] = (alpha << 24) | (unblend(rr, alpha) << 16) | (unblend(gg, alpha) << 8)
                        | unblend(bb, alpha);
                if (alpha >= 128) {
                    ink++;
                    if (ox < minX) {
                        minX = ox;
                    }
                    if (ox > maxX) {
                        maxX = ox;
                    }
                    if (oy < minY) {
                        minY = oy;
                    }
                    if (oy > maxY) {
                        maxY = oy;
                    }
                }
            }
        }
        double inkMm2 = ink / (ppm * ppm);
        if (inkMm2 < 3.0) {
            return null;
        }
        int margin = (int) Math.round(1.5 * ppm);
        minX = Math.max(0, minX - margin);
        minY = Math.max(0, minY - margin);
        maxX = Math.min(ow - 1, maxX + margin);
        maxY = Math.min(oh - 1, maxY + margin);
        int cw = maxX - minX + 1;
        int ch = maxY - minY + 1;
        int[] c = new int[cw * ch];
        for (int y = 0; y < ch; y++) {
            System.arraycopy(out, (y + minY) * ow + minX, c, y * cw, cw);
        }
        return ArgbImage.adopt(cw, ch, c);
    }

    /** Farbe vor dem Mischen mit Weiß: p = a*c + (1-a)*255  =>  c = (p - (1-a)*255) / a. */
    private static int unblend(int p, int alpha) {
        int c = (p * 255 - (255 - alpha) * 255) / alpha;
        return c < 0 ? 0 : (c > 255 ? 255 : c);
    }
}
