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
    static final double CROP_PX_PER_MM = 12.0;
    /** Mindestgröße eines roten/grünen Punkts in Zellen (mm²). */
    static final int MIN_DOT_CELLS = 6;
    /** Füllgrad, ab dem ein Par-Kästchen als angekreuzt gilt. */
    static final double PAR_MARK_FILL = 0.08;
    /** Kantenlänge der Kacheln für das lokale Papierweiß (Schatten, Verläufe). */
    static final double TILE_MM = 12.0;

    // Klassen einer Einzelprobe
    static final int PAPER = 0;
    static final int INK = 1;
    static final int RED = 2;
    static final int GREEN = 3;
    static final int BLUE = 4;
    static final int OTHER = 5;
    /** Unbunt, heller als Wandtinte, aber deutlich dunkler als Papier (Bleistift, blasser Stift). */
    static final int FAINT = 6;

    // Ergebniscodes der Blattsuche
    private static final int FOUND = 0;
    private static final int NOTHING = 1;
    private static final int PAIR_ONLY = 2;
    private static final int MIRRORED = 3;
    private static final int BLOCK_MISSING = 4;
    private static final int BLOCK_WRONG = 5;
    private static final int DISTORTED = 6;

    private final SheetTemplate t;

    // Zustand während einer Auswertung
    private int[] px;
    private int imgW;
    private int imgH;
    private Affine map;
    private int kernel;
    /** Papierweiß je Kachel (RGB), Kacheln à TILE_MM über dem Spielfeld. */
    private int[] tileWhite;
    private int tilesX;
    private int tilesY;
    private final int[] rgbTmp = new int[3];
    private final int[] whiteTmp = new int[3];

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
            wi.flattenIllumination();
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
                case DISTORTED:
                    r.detail = "Suchmuster gefunden, aber verzerrt angeordnet (schräg fotografiert?)";
                    r.error(ScanError.SHEET_NOT_FOUND);
                    break;
                case BLOCK_WRONG:
                    r.detail = "Kontrollblock unten rechts passt nicht zur Lage der Marken (Blatt verzerrt oder schräg fotografiert?)";
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
            if (!hasValidPair(cands)) {
                return NOTHING;
            }
            // Drei oder mehr gleich große Suchmuster, aber keine passende Anordnung:
            // eher ein verzerrtes Blatt als eine fehlende Ecke.
            return consistentCount(cands) >= 3 ? DISTORTED : PAIR_ONLY;
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

    /** Größte Anzahl Kandidaten mit untereinander passender Modulgröße. */
    private static int consistentCount(List<FinderPattern> c) {
        int best = 0;
        for (int i = 0; i < c.size(); i++) {
            int n = 0;
            for (int j = 0; j < c.size(); j++) {
                double a = c.get(i).module;
                double b = c.get(j).module;
                if (Math.max(a, b) <= Math.min(a, b) * 1.25) {
                    n++;
                }
            }
            best = Math.max(best, n);
        }
        return best;
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
        int half = (int) Math.ceil(size * 1.9);
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
        double bestD = size * 1.2;
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

    /** Bilinear interpolierte Farbe an der Seitenposition (u, v) in mm. */
    private void sampleBilinear(double u, double v, int[] out) {
        double fx = map.mapX(u, v) - 0.5;
        double fy = map.mapY(u, v) - 0.5;
        int x0 = (int) Math.floor(fx);
        int y0 = (int) Math.floor(fy);
        double ax = fx - x0;
        double ay = fy - y0;
        x0 = Math.max(0, Math.min(imgW - 2, x0));
        y0 = Math.max(0, Math.min(imgH - 2, y0));
        int p00 = px[y0 * imgW + x0];
        int p10 = px[y0 * imgW + x0 + 1];
        int p01 = px[(y0 + 1) * imgW + x0];
        int p11 = px[(y0 + 1) * imgW + x0 + 1];
        for (int ch = 0; ch < 3; ch++) {
            int sh = 16 - 8 * ch;
            double a = ((p00 >> sh) & 0xFF) * (1 - ax) + ((p10 >> sh) & 0xFF) * ax;
            double b = ((p01 >> sh) & 0xFF) * (1 - ax) + ((p11 >> sh) & 0xFF) * ax;
            out[ch] = (int) (a * (1 - ay) + b * ay + 0.5);
        }
    }

    /**
     * Papierweiß schätzen: zuerst global (hellste 20 % der Zellmitten), dann je 12-mm-Kachel aus den
     * unbunten, hellen Proben. Jede Kachel übernimmt das hellste Weiß ihrer 5x5-Nachbarschaft, damit
     * ganz bemalte Kacheln (z. B. großer Teich) nicht als "Papier" gelten.
     */
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
        int global = brightMean(rgb, 0, 0, cols, rows, cols, hist, 0.80, 0);
        r.paperRgb = global;
        tilesX = (int) Math.ceil(t.field.w / TILE_MM);
        tilesY = (int) Math.ceil(t.field.h / TILE_MM);
        int tc = (int) Math.round(TILE_MM / CELL_MM);
        int[] cand = new int[tilesX * tilesY];
        for (int ty = 0; ty < tilesY; ty++) {
            for (int tx = 0; tx < tilesX; tx++) {
                cand[ty * tilesX + tx] = brightMean(rgb, tx * tc, ty * tc, Math.min(cols, tx * tc + tc),
                        Math.min(rows, ty * tc + tc), cols, null, 0.75, global);
            }
        }
        tileWhite = new int[cand.length];
        for (int ty = 0; ty < tilesY; ty++) {
            for (int tx = 0; tx < tilesX; tx++) {
                int best = -1;
                int bestL = -1;
                for (int dy = -2; dy <= 2; dy++) {
                    for (int dx = -2; dx <= 2; dx++) {
                        int x = tx + dx;
                        int y = ty + dy;
                        if (x < 0 || y < 0 || x >= tilesX || y >= tilesY || cand[y * tilesX + x] < 0) {
                            continue;
                        }
                        int l = WorkImage.luminance(cand[y * tilesX + x]);
                        if (l > bestL) {
                            bestL = l;
                            best = cand[y * tilesX + x];
                        }
                    }
                }
                tileWhite[ty * tilesX + tx] = best < 0 ? global : best;
            }
        }
    }

    /**
     * Mittlere Farbe der hellsten Proben eines Bereichs (Perzentil p der Helligkeit).
     * Mit ref != 0 zählen nur Proben, die relativ zu ref unbunt sind; ohne solche: -1.
     */
    private static int brightMean(int[] rgb, int x0, int y0, int x1, int y1, int stride, int[] histIn,
                                  double p, int ref) {
        int[] hist = histIn;
        int n = 0;
        if (hist == null) {
            hist = new int[256];
            for (int y = y0; y < y1; y++) {
                for (int x = x0; x < x1; x++) {
                    int v = rgb[y * stride + x];
                    if (ref == 0 || achromatic(v, ref)) {
                        hist[WorkImage.luminance(v)]++;
                        n++;
                    }
                }
            }
        } else {
            for (int i = 0; i < 256; i++) {
                n += hist[i];
            }
        }
        if (n == 0) {
            return -1;
        }
        int lp = WorkImage.percentile(hist, n, p);
        long sr = 0;
        long sg = 0;
        long sb = 0;
        int m = 0;
        for (int y = y0; y < y1; y++) {
            for (int x = x0; x < x1; x++) {
                int v = rgb[y * stride + x];
                if (WorkImage.luminance(v) >= lp && (ref == 0 || achromatic(v, ref))) {
                    sr += (v >> 16) & 0xFF;
                    sg += (v >> 8) & 0xFF;
                    sb += v & 0xFF;
                    m++;
                }
            }
        }
        int wr = Math.max(20, (int) (sr / m));
        int wg = Math.max(20, (int) (sg / m));
        int wb = Math.max(20, (int) (sb / m));
        return (wr << 16) | (wg << 8) | wb;
    }

    /** Ist die Farbe v relativ zum Weiß ref (fast) unbunt? */
    private static boolean achromatic(int v, int ref) {
        int r = ((v >> 16) & 0xFF) * 255 / Math.max(1, (ref >> 16) & 0xFF);
        int g = ((v >> 8) & 0xFF) * 255 / Math.max(1, (ref >> 8) & 0xFF);
        int b = (v & 0xFF) * 255 / Math.max(1, ref & 0xFF);
        int mx = Math.max(r, Math.max(g, b));
        int mn = Math.min(r, Math.min(g, b));
        return mx - mn < 30;
    }

    /** Papierweiß an einer Seitenposition (mm), bilinear zwischen den Kachelmitten; out = {r, g, b}. */
    private void whiteAt(double u, double v, int[] out) {
        double gx = (u - t.field.x) / TILE_MM - 0.5;
        double gy = (v - t.field.y) / TILE_MM - 0.5;
        int x0 = (int) Math.floor(gx);
        int y0 = (int) Math.floor(gy);
        double fx = Math.max(0, Math.min(1, gx - x0));
        double fy = Math.max(0, Math.min(1, gy - y0));
        int xa = Math.max(0, Math.min(tilesX - 1, x0));
        int xb = Math.max(0, Math.min(tilesX - 1, x0 + 1));
        int ya = Math.max(0, Math.min(tilesY - 1, y0));
        int yb = Math.max(0, Math.min(tilesY - 1, y0 + 1));
        for (int ch = 0; ch < 3; ch++) {
            int sh = 16 - 8 * ch;
            double a = ((tileWhite[ya * tilesX + xa] >> sh) & 0xFF) * (1 - fx) + ((tileWhite[ya * tilesX + xb] >> sh) & 0xFF) * fx;
            double b = ((tileWhite[yb * tilesX + xa] >> sh) & 0xFF) * (1 - fx) + ((tileWhite[yb * tilesX + xb] >> sh) & 0xFF) * fx;
            out[ch] = Math.max(20, (int) (a * (1 - fy) + b * fy + 0.5));
        }
    }

    /** Skalierfaktoren (16.16) für die Normierung auf Papierweiß. */
    private static void scales(int[] white, int[] k) {
        k[0] = (255 << 16) / white[0];
        k[1] = (255 << 16) / white[1];
        k[2] = (255 << 16) / white[2];
    }

    /** Klassifiziert eine Probe (Rohwerte), relativ zum Papierweiß (Skalen k aus {@link #scales}). */
    static int classify(int r0, int g0, int b0, int[] k) {
        int r = Math.min(255, (r0 * k[0]) >> 16);
        int g = Math.min(255, (g0 * k[1]) >> 16);
        int b = Math.min(255, (b0 * k[2]) >> 16);
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
        if (lum < 153) {
            return INK;
        }
        return lum < 196 ? FAINT : PAPER;
    }

    /** Zellen mit blasser, unbunter Tinte (für die Warnung FAINT_LINES). */
    private boolean[] faint;

    /** Anzahl farbiger Proben im Feld (vor dem Aufräumen) – für die Graustufen-Erkennung. */
    private long colorSamples;

    private Grid classifyField(ScanResult r) {
        colorSamples = 0;
        int cols = (int) Math.round(t.field.w / CELL_MM);
        int rows = (int) Math.round(t.field.h / CELL_MM);
        Grid g = new Grid(cols, rows, CELL_MM);
        r.workingBytes += g.size();
        int[] counts = new int[7];
        int[] k = new int[3];
        faint = new boolean[cols * rows];
        r.workingBytes += faint.length;
        double step = CELL_MM / SUB;
        for (int cy = 0; cy < rows; cy++) {
            for (int cx = 0; cx < cols; cx++) {
                if (cx < MARGIN_CELLS || cy < MARGIN_CELLS || cx >= cols - MARGIN_CELLS || cy >= rows - MARGIN_CELLS) {
                    continue;
                }
                for (int q = 0; q < counts.length; q++) {
                    counts[q] = 0;
                }
                double u0 = t.field.x + cx * CELL_MM + step * 0.5;
                double v0 = t.field.y + cy * CELL_MM + step * 0.5;
                whiteAt(u0 + CELL_MM * 0.5, v0 + CELL_MM * 0.5, whiteTmp);
                scales(whiteTmp, k);
                for (int sy = 0; sy < SUB; sy++) {
                    for (int sx = 0; sx < SUB; sx++) {
                        sample(u0 + sx * step, v0 + sy * step, kernel, rgbTmp);
                        counts[classify(rgbTmp[0], rgbTmp[1], rgbTmp[2], k)]++;
                    }
                }
                g.set(cx, cy, decideCell(counts));
                faint[cy * cols + cx] = counts[FAINT] >= 4;
                colorSamples += counts[RED] + counts[GREEN] + counts[BLUE];
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
        checkFaintLines(g, r);
    }

    /**
     * Blasse Linien (z. B. Bleistift), die nicht als Wand zählen: Ränder echter Striche und das
     * gedruckte Raster ausschließen (Abstand zu erkannten Zellen, Mindestgröße), dann warnen.
     */
    private void checkFaintLines(Grid g, ScanResult r) {
        boolean[] occupied = new boolean[g.size()];
        for (int i = 0; i < occupied.length; i++) {
            occupied[i] = g.getIndex(i) != CellType.EMPTY;
        }
        boolean[] near = GridOps.dilate(occupied, g.cols, g.rows, 2, false);
        for (int i = 0; i < faint.length; i++) {
            faint[i] = faint[i] && !near[i];
        }
        int[][] sz = new int[1][];
        GridOps.label(faint, g.cols, g.rows, sz);
        for (int k = 0; k < sz[0].length; k++) {
            if (sz[0][k] >= 15) {
                r.warn(ScanWarning.FAINT_LINES);
                break;
            }
        }
        faint = null;
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
        if (colorSamples == 0 && !colorReferenceIsColored()) {
            r.error(ScanError.GRAYSCALE_SCAN);
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

    /**
     * Ist das gedruckte Farbkontrollfeld (grüner Kreis im Logo) im Scan farbig? Ohne Kontrollfeld
     * in der Vorlage gilt der Scan als farbig.
     */
    private boolean colorReferenceIsColored() {
        if (Double.isNaN(t.colorRefX)) {
            return true;
        }
        int[] kk = new int[3];
        whiteAt(t.colorRefX, t.colorRefY, whiteTmp);
        scales(whiteTmp, kk);
        double step = 0.3;
        int k = Math.max(1, (int) Math.round(map.scale() * step));
        int colored = 0;
        int total = 0;
        for (double dv = -t.colorRefR; dv <= t.colorRefR; dv += step) {
            for (double du = -t.colorRefR; du <= t.colorRefR; du += step) {
                if (du * du + dv * dv > t.colorRefR * t.colorRefR) {
                    continue;
                }
                sample(t.colorRefX + du, t.colorRefY + dv, k, rgbTmp);
                int c = classify(rgbTmp[0], rgbTmp[1], rgbTmp[2], kk);
                if (c == RED || c == GREEN || c == BLUE || c == OTHER) {
                    colored++;
                }
                total++;
            }
        }
        return colored >= total * 0.3;
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
            int[] kk = new int[3];
            whiteAt(b.x + b.w / 2, b.y + b.h / 2, whiteTmp);
            scales(whiteTmp, kk);
            for (double v = b.y + b.inset + step / 2; v < b.y + b.h - b.inset; v += step) {
                for (double u = b.x + b.inset + step / 2; u < b.x + b.w - b.inset; u += step) {
                    sample(u, v, k, rgbTmp);
                    if (classify(rgbTmp[0], rgbTmp[1], rgbTmp[2], kk) != PAPER) {
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
        int[] kk = new int[3];
        whiteAt(b.x + b.w / 2, b.y + b.h / 2, whiteTmp);
        scales(whiteTmp, kk);
        int ink = 0;
        int minX = ow;
        int minY = oh;
        int maxX = -1;
        int maxY = -1;
        for (int oy = 0; oy < oh; oy++) {
            for (int ox = 0; ox < ow; ox++) {
                if (k == 1) {
                    sampleBilinear(x0 + (ox + 0.5) / ppm, y0 + (oy + 0.5) / ppm, rgbTmp);
                } else {
                    sample(x0 + (ox + 0.5) / ppm, y0 + (oy + 0.5) / ppm, k, rgbTmp);
                }
                int rr = Math.min(255, (rgbTmp[0] * kk[0]) >> 16);
                int gg = Math.min(255, (rgbTmp[1] * kk[1]) >> 16);
                int bb = Math.min(255, (rgbTmp[2] * kk[2]) >> 16);
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
