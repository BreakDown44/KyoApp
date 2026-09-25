package de.scangolf.test;

import de.scangolf.core.level.CellType;
import de.scangolf.core.level.Grid;
import de.scangolf.core.scan.ScanAnalyzer;
import de.scangolf.core.scan.ScanError;
import de.scangolf.core.scan.ScanResult;
import de.scangolf.core.scan.ScanWarning;
import de.scangolf.core.util.Json;
import de.scangolf.pc.Images;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Ein Testfall je Testbild aus testbilder/out/manifest.json.
 *
 * Positiv: Start/Loch auf 2 mm genau, Par, Wände an den Sollstellen (und keine Wand anderswo),
 * Wasser, Namens-/Bahnfeld. Negativ: erwarteter Fehlercode, kein Absturz.
 * Leer: kein einziges Wand-/Farbfeld (Raster und Rahmen der Vorlage sind nie Wand).
 */
public class ScanImagesTest implements CaseProvider {

    /** Toleranz für Start/Loch in mm. */
    static final double POS_TOL = 2.0;
    /** Wandzelle muss so nah an der Soll-Linie liegen (zusätzlich zur halben Strichbreite). */
    static final double WALL_HIT_RADIUS = 1.0;
    /** Wandzellen weiter weg von jeder Soll-Linie gelten als Phantom. */
    static final double WALL_PHANTOM_DIST = 3.0;

    private final ScanAnalyzer analyzer = Manifest.analyzer();

    @Override
    public List<TestCase> cases() {
        List<TestCase> cases = new ArrayList<>();
        for (Map<String, Object> e : Manifest.images()) {
            String name = e.get("group") + ":" + e.get("file");
            cases.add(new TestCase(name, () -> check(e)));
        }
        return cases;
    }

    private void check(Map<String, Object> e) {
        String file = (String) e.get("file");
        Images.Raw raw = Manifest.load(file);
        ScanResult r = analyzer.analyze(raw.argb, raw.width, raw.height);
        String ctx = file + " [" + e.get("transform") + "]";
        if ("ok".equals(e.get("expect"))) {
            checkPositive(e, r, ctx);
        } else {
            checkNegative(e, r, ctx);
        }
    }

    private void checkNegative(Map<String, Object> e, ScanResult r, String ctx) {
        ScanError expected = ScanError.valueOf((String) e.get("error"));
        Check.isFalse(r.isOk(), ctx + ": Fehler erwartet, aber Level erkannt");
        Check.isTrue(r.level() == null, ctx + ": bei Fehlern darf kein Level entstehen");
        Check.isTrue(r.hasError(expected), ctx + ": erwartet " + expected + ", war " + r.errors());
        Object also = e.get("also");
        if (also != null) {
            for (Object o : (List<?>) also) {
                ScanError a = ScanError.valueOf((String) o);
                Check.isTrue(r.hasError(a), ctx + ": zusätzlich erwartet " + a + ", war " + r.errors());
            }
        }
        if ("leer".equals(e.get("group"))) {
            Grid g = r.grid();
            Check.notNull(g, ctx + ": Raster muss bei erkannter Vorlage vorliegen");
            Check.equal(0, g.count(CellType.WALL), ctx + ": Wandzellen auf leerer Vorlage");
            Check.equal(0, g.count(CellType.WATER), ctx + ": Wasserzellen auf leerer Vorlage");
            Check.equal(0, g.count(CellType.START) + g.count(CellType.HOLE), ctx + ": Farbzellen auf leerer Vorlage");
            Check.isTrue(r.nameImage() == null && r.laneImage() == null, ctx + ": leere Schreibfelder erkannt");
        }
    }

    private void checkPositive(Map<String, Object> e, ScanResult r, String ctx) {
        Check.isTrue(r.isOk(), ctx + ": Level erwartet, Fehler: " + r.errors() + " " + r.detail());
        Object truth = e.get("truth");
        Grid g = r.grid();

        double[] start = Manifest.point(Json.map(truth).get("start"));
        double[] hole = Manifest.point(Json.map(truth).get("hole"));
        double[] sd = r.startDrawn();
        Check.isTrue(dist(start, sd) <= POS_TOL, ctx + ": roter Punkt bei " + fmt(sd) + ", Soll " + fmt(start));
        double startTol = Json.num(e, "start_tolerance", POS_TOL);
        Check.isTrue(dist(start, r.start()) <= startTol, ctx + ": Start bei " + fmt(r.start()) + ", Soll " + fmt(start));
        Check.isTrue(dist(hole, r.hole()) <= POS_TOL, ctx + ": Loch bei " + fmt(r.hole()) + ", Soll " + fmt(hole));
        Check.info(String.format("Abweichung Start %.2f mm, Loch %.2f mm, Block %.2f mm, %d ms",
                dist(start, sd), dist(hole, r.hole()), r.blockResidualMm(), r.millisTotal()));

        int expPar = e.get("expect_par") != null ? (int) Json.num(e, "expect_par") : (int) Json.num(truth, "par");
        Check.equal(expPar, r.level().par(), ctx + ": Par");
        List<String> expWarn = new ArrayList<>();
        if (e.get("expect_warnings") != null) {
            for (Object o : (List<?>) e.get("expect_warnings")) {
                expWarn.add((String) o);
            }
        }
        for (String w : expWarn) {
            Check.isTrue(r.hasWarning(ScanWarning.valueOf(w)), ctx + ": Warnung " + w + " fehlt, war " + r.warnings());
        }
        for (ScanWarning w : r.warnings()) {
            Check.isTrue(expWarn.contains(w.name()), ctx + ": unerwartete Warnung " + w);
        }
        Check.equal(truth(truth, "has_name"), r.level().nameImage() != null, ctx + ": Namensfeld erkannt");
        Check.equal(truth(truth, "has_lane"), r.level().laneImage() != null, ctx + ": Bahnfeld erkannt");

        if (Boolean.FALSE.equals(e.get("walls_detectable"))) {
            // Wände bewusst zu hell: es darf keine einzige Wandzelle entstehen (keine Zufallswände)
            Check.equal(0, g.count(CellType.WALL), ctx + ": hellgraue Linien dürfen keine Wand werden");
        } else {
            checkWalls(truth, g, ctx);
        }
        checkWater(truth, g, ctx);
        checkDotCells(g, CellType.START, start, 7.0, ctx);
        checkDotCells(g, CellType.HOLE, hole, 7.0, ctx);
    }

    private static boolean truth(Object t, String key) {
        return Boolean.TRUE.equals(Json.map(t).get(key));
    }

    // ------------------------------------------------------------ Wände

    private void checkWalls(Object truth, Grid g, String ctx) {
        List<Object> walls = Json.list(truth, "walls");
        List<double[][]> segs = new ArrayList<>();
        List<Double> widths = new ArrayList<>();
        for (Object w : walls) {
            List<double[]> pts = Manifest.points(Json.map(w).get("pts"));
            double width = Json.num(w, "w");
            boolean gaps = Boolean.TRUE.equals(Json.map(w).get("gaps"));
            double hitR = width / 2 + WALL_HIT_RADIUS;
            int total = 0;
            int hit = 0;
            List<String> misses = new ArrayList<>();
            for (int i = 0; i + 1 < pts.size(); i++) {
                double[] a = pts.get(i);
                double[] b = pts.get(i + 1);
                segs.add(new double[][] {a, b});
                widths.add(width);
                double len = dist(a, b);
                int n = (int) Math.floor(len);
                for (int k = 0; k <= n; k++) {
                    double t = len == 0 ? 0 : k / len;
                    double x = a[0] + (b[0] - a[0]) * t;
                    double y = a[1] + (b[1] - a[1]) * t;
                    boolean nearEnd = (i == 0 && k * 1.0 < 1.5) || (i + 2 == pts.size() && len - k < 1.5);
                    if (nearEnd || x < hitR + 2.2 || y < hitR + 2.2 || x > g.widthMm() - hitR - 2.2
                            || y > g.heightMm() - hitR - 2.2) {
                        continue;
                    }
                    total++;
                    if (wallCellWithin(g, x, y, hitR)) {
                        hit++;
                    } else if (misses.size() < 5) {
                        misses.add(String.format("(%.1f/%.1f)", x, y));
                    }
                }
            }
            double need = gaps ? 1.0 : 0.98;
            Check.isTrue(total > 0, ctx + ": keine Prüfpunkte für Wand");
            Check.isTrue(hit >= need * total, ctx + ": Wand " + pts.get(0)[0] + "/" + pts.get(0)[1]
                    + " nur " + hit + "/" + total + " Punkte getroffen" + (gaps ? " (Lücken nicht geschlossen?)" : "")
                    + ", z. B. " + misses);
        }
        // Keine Phantom-Wände: jede Wandzelle liegt nahe einer Soll-Linie
        int phantom = 0;
        String example = "";
        for (int cy = 0; cy < g.rows; cy++) {
            for (int cx = 0; cx < g.cols; cx++) {
                if (g.get(cx, cy) != CellType.WALL) {
                    continue;
                }
                double x = cx + 0.5;
                double y = cy + 0.5;
                boolean ok = false;
                for (int s = 0; s < segs.size() && !ok; s++) {
                    ok = segDist(x, y, segs.get(s)[0], segs.get(s)[1]) <= widths.get(s) / 2 + WALL_PHANTOM_DIST;
                }
                if (!ok) {
                    phantom++;
                    if (example.isEmpty()) {
                        example = String.format("(%.1f/%.1f)", x, y);
                    }
                }
            }
        }
        Check.equal(0, phantom, ctx + ": Wandzellen fern jeder gezeichneten Wand, z. B. " + example);
    }

    private static boolean wallCellWithin(Grid g, double x, double y, double r) {
        for (int cy = (int) Math.floor(y - r); cy <= (int) Math.floor(y + r); cy++) {
            for (int cx = (int) Math.floor(x - r); cx <= (int) Math.floor(x + r); cx++) {
                if (cx < 0 || cy < 0 || cx >= g.cols || cy >= g.rows || g.get(cx, cy) != CellType.WALL) {
                    continue;
                }
                double dx = Math.max(Math.max(cx - x, 0), x - (cx + 1));
                double dy = Math.max(Math.max(cy - y, 0), y - (cy + 1));
                if (dx * dx + dy * dy <= r * r) {
                    return true;
                }
            }
        }
        return false;
    }

    // ------------------------------------------------------------ Wasser

    private void checkWater(Object truth, Grid g, String ctx) {
        List<Object> ponds = Json.list(truth, "water");
        List<List<double[]>> polys = new ArrayList<>();
        for (Object p : ponds) {
            polys.add(Manifest.points(p));
        }
        for (List<double[]> poly : polys) {
            int total = 0;
            int hit = 0;
            for (double y = 0.5; y < g.heightMm(); y += 1.0) {
                for (double x = 0.5; x < g.widthMm(); x += 1.0) {
                    if (!inside(poly, x, y) || boundaryDist(poly, x, y) < 2.0 || x < 3 || y < 3
                            || x > g.widthMm() - 3 || y > g.heightMm() - 3) {
                        continue;
                    }
                    total++;
                    if (g.atMm(x, y) == CellType.WATER) {
                        hit++;
                    }
                }
            }
            Check.isTrue(total > 0, ctx + ": keine Prüfpunkte im Wasser");
            Check.isTrue(hit >= 0.97 * total, ctx + ": Wasser nur " + hit + "/" + total + " erkannt");
        }
        int phantom = 0;
        for (int cy = 0; cy < g.rows; cy++) {
            for (int cx = 0; cx < g.cols; cx++) {
                if (g.get(cx, cy) != CellType.WATER) {
                    continue;
                }
                double x = cx + 0.5;
                double y = cy + 0.5;
                boolean ok = false;
                for (List<double[]> poly : polys) {
                    ok |= inside(poly, x, y) || boundaryDist(poly, x, y) <= 2.5;
                }
                if (!ok) {
                    phantom++;
                }
            }
        }
        Check.equal(0, phantom, ctx + ": Wasserzellen außerhalb der gezeichneten Teiche");
    }

    private static void checkDotCells(Grid g, byte type, double[] center, double maxDist, String ctx) {
        int n = 0;
        for (int cy = 0; cy < g.rows; cy++) {
            for (int cx = 0; cx < g.cols; cx++) {
                if (g.get(cx, cy) == type) {
                    n++;
                    double d = dist(center, new double[] {cx + 0.5, cy + 0.5});
                    Check.isTrue(d <= maxDist, ctx + ": " + CellType.name(type) + "-Zelle " + d + " mm vom Punkt entfernt");
                }
            }
        }
        Check.isTrue(n >= 6, ctx + ": zu wenige " + CellType.name(type) + "-Zellen: " + n);
    }

    // ------------------------------------------------------------ Geometrie

    static double dist(double[] a, double[] b) {
        if (a == null || b == null) {
            return Double.POSITIVE_INFINITY;
        }
        return Math.hypot(a[0] - b[0], a[1] - b[1]);
    }

    static double segDist(double x, double y, double[] a, double[] b) {
        double vx = b[0] - a[0];
        double vy = b[1] - a[1];
        double l2 = vx * vx + vy * vy;
        double t = l2 == 0 ? 0 : Math.max(0, Math.min(1, ((x - a[0]) * vx + (y - a[1]) * vy) / l2));
        return Math.hypot(x - a[0] - t * vx, y - a[1] - t * vy);
    }

    static boolean inside(List<double[]> poly, double x, double y) {
        boolean in = false;
        for (int i = 0, j = poly.size() - 1; i < poly.size(); j = i++) {
            double[] a = poly.get(i);
            double[] b = poly.get(j);
            if ((a[1] > y) != (b[1] > y) && x < (b[0] - a[0]) * (y - a[1]) / (b[1] - a[1]) + a[0]) {
                in = !in;
            }
        }
        return in;
    }

    static double boundaryDist(List<double[]> poly, double x, double y) {
        double best = Double.MAX_VALUE;
        for (int i = 0, j = poly.size() - 1; i < poly.size(); j = i++) {
            best = Math.min(best, segDist(x, y, poly.get(j), poly.get(i)));
        }
        return best;
    }

    static String fmt(double[] p) {
        return p == null ? "null" : String.format("%.2f/%.2f", p[0], p[1]);
    }
}
