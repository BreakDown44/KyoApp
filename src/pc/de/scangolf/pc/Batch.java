package de.scangolf.pc;

import de.scangolf.core.scan.ScanAnalyzer;
import de.scangolf.core.scan.ScanError;
import de.scangolf.core.scan.ScanResult;
import de.scangolf.core.util.Json;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Wertet alle Bilder eines Ordners aus und druckt eine Tabelle; schreibt Debug-Bilder nach
 * ordner/debug. Liegt eine manifest.json (Format des Testbild-Generators) daneben, werden
 * Erwartung und Positionsabweichung mit ausgegeben. Gedacht für Grenztests und echte Scans.
 */
public final class Batch {

    private Batch() {
    }

    public static int run(File dir, boolean debug) throws IOException {
        File[] files = dir.listFiles((d, n) -> n.matches("(?i).*\\.(png|jpe?g|bmp|gif)$"));
        if (files == null || files.length == 0) {
            System.err.println("Keine Bilder in " + dir);
            return 2;
        }
        Arrays.sort(files);
        Map<String, Object> manifest = new HashMap<>();
        File mf = new File(dir, "manifest.json");
        if (mf.exists()) {
            Object root = Json.parse(new String(Files.readAllBytes(mf.toPath()), StandardCharsets.UTF_8));
            for (Object o : Json.list(root, "images")) {
                manifest.put(Json.str(o, "file"), o);
            }
        }
        ScanAnalyzer an = new ScanAnalyzer();
        int ok = 0;
        int asExpected = 0;
        int withExpectation = 0;
        System.out.printf("%-36s %-7s %6s %8s %7s %7s %6s %6s  %s%n", "Datei", "Ergebnis", "dpi", "Drehung",
                "ΔStart", "ΔLoch", "Wände", "ms", "Fehler / Hinweise / Erwartung");
        for (File f : files) {
            Images.Raw raw;
            try {
                raw = Images.load(f);
            } catch (IOException e) {
                System.out.printf("%-36s nicht lesbar: %s%n", f.getName(), e.getMessage());
                continue;
            }
            ScanResult r = an.analyze(raw.argb, raw.width, raw.height);
            if (r.isOk()) {
                ok++;
            }
            String dStart = "–";
            String dHole = "–";
            String walls = "–";
            String expect = "";
            Object e = manifest.get(f.getName());
            if (e != null) {
                withExpectation++;
                boolean wantOk = "ok".equals(Json.str(e, "expect"));
                boolean met;
                if (wantOk) {
                    met = r.isOk();
                    Object truth = Json.map(e).get("truth");
                    if (truth != null && r.startDrawn() != null && Json.map(truth).get("start") != null) {
                        dStart = String.format("%.2f", dist(point(Json.map(truth).get("start")), r.startDrawn()));
                    }
                    if (truth != null && r.grid() != null) {
                        walls = String.format("%.0f%%", 100 * wallCoverage(truth, r.grid()));
                    }
                    if (truth != null && r.hole() != null && Json.map(truth).get("hole") != null) {
                        dHole = String.format("%.2f", dist(point(Json.map(truth).get("hole")), r.hole()));
                    }
                } else {
                    met = r.hasError(ScanError.valueOf(Json.str(e, "error")));
                }
                if (met) {
                    asExpected++;
                }
                expect = (met ? "wie erwartet" : "ANDERS ALS ERWARTET") + " [" + Json.map(e).get("transform") + "]";
            }
            StringBuilder info = new StringBuilder();
            for (ScanError err : r.errors()) {
                info.append(err.name()).append(' ');
            }
            r.warnings().forEach(w -> info.append(w.name()).append(' '));
            System.out.printf("%-36s %-7s %6.0f %7.1f° %7s %7s %6s %6d  %s%s%n", f.getName(), r.isOk() ? "OK" : "FEHLER",
                    r.dpi(), r.rotationDegrees(), dStart, dHole, walls, r.millisTotal(), info, expect);
            if (debug) {
                Images.savePng(Main.debugImage(raw, r, an), new File(new File(dir, "debug"),
                        f.getName().replaceFirst("\\.[^.]+$", "") + ".debug.png"));
            }
        }
        System.out.printf("%n%d Bilder, %d erkannt", files.length, ok);
        if (withExpectation > 0) {
            System.out.printf(", %d von %d wie erwartet", asExpected, withExpectation);
        }
        System.out.println();
        return 0;
    }

    /** Anteil der Punkte auf den Soll-Wänden, an denen eine Wandzelle erkannt wurde (Radius Strich/2 + 1 mm). */
    static double wallCoverage(Object truth, de.scangolf.core.level.Grid g) {
        int total = 0;
        int hit = 0;
        for (Object w : Json.list(truth, "walls")) {
            List<?> pts = (List<?>) Json.map(w).get("pts");
            double rad = Json.num(w, "w") / 2 + 1.0;
            for (int i = 0; i + 1 < pts.size(); i++) {
                double[] a = point(pts.get(i));
                double[] b = point(pts.get(i + 1));
                double len = dist(a, b);
                for (double d = 1.5; d < len - 1.5; d += 1.0) {
                    double x = a[0] + (b[0] - a[0]) * d / len;
                    double y = a[1] + (b[1] - a[1]) * d / len;
                    if (x < rad + 2.2 || y < rad + 2.2 || x > g.widthMm() - rad - 2.2 || y > g.heightMm() - rad - 2.2) {
                        continue;
                    }
                    total++;
                    boolean found = false;
                    for (int cy = (int) (y - rad); cy <= (int) (y + rad) && !found; cy++) {
                        for (int cx = (int) (x - rad); cx <= (int) (x + rad) && !found; cx++) {
                            found = g.get(cx, cy) == de.scangolf.core.level.CellType.WALL
                                    && Math.hypot(cx + 0.5 - x, cy + 0.5 - y) <= rad + 0.71;
                        }
                    }
                    if (found) {
                        hit++;
                    }
                }
            }
        }
        return total == 0 ? 1.0 : hit / (double) total;
    }

    private static double[] point(Object o) {
        List<?> l = (List<?>) o;
        return new double[] {(Double) l.get(0), (Double) l.get(1)};
    }

    private static double dist(double[] a, double[] b) {
        return Math.hypot(a[0] - b[0], a[1] - b[1]);
    }
}
