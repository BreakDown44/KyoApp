package de.scangolf.test;

import de.scangolf.core.scan.Affine;
import de.scangolf.core.scan.ScanAnalyzer;
import de.scangolf.core.scan.ScanError;
import de.scangolf.core.scan.ScanResult;
import de.scangolf.core.scan.SheetTemplate;

import java.util.Random;

/** Einzelteile der Scan-Auswertung ohne Testbilder. */
public class ScanUnitTest {

    public void testAffineFromTrianglesAndInverse() {
        Random rnd = new Random(3);
        for (int k = 0; k < 200; k++) {
            double ang = rnd.nextDouble() * 2 * Math.PI;
            double s = 2 + rnd.nextDouble() * 20;
            double tx = rnd.nextDouble() * 1000;
            double ty = rnd.nextDouble() * 1000;
            Affine truth = new Affine(s * Math.cos(ang), -s * Math.sin(ang), tx, s * Math.sin(ang), s * Math.cos(ang), ty);
            double[][] src = {{14.25, 14.25}, {282.75, 14.25}, {14.25, 195.75}};
            Affine a = Affine.fromTriangles(src[0][0], src[0][1], src[1][0], src[1][1], src[2][0], src[2][1],
                    truth.mapX(src[0][0], src[0][1]), truth.mapY(src[0][0], src[0][1]),
                    truth.mapX(src[1][0], src[1][1]), truth.mapY(src[1][0], src[1][1]),
                    truth.mapX(src[2][0], src[2][1]), truth.mapY(src[2][0], src[2][1]));
            double x = rnd.nextDouble() * 297;
            double y = rnd.nextDouble() * 210;
            Check.near(truth.mapX(x, y), a.mapX(x, y), 1e-6, "x");
            Check.near(truth.mapY(x, y), a.mapY(x, y), 1e-6, "y");
            Affine inv = a.inverse();
            Check.near(x, inv.mapX(a.mapX(x, y), a.mapY(x, y)), 1e-6, "Umkehr x");
            Check.near(y, inv.mapY(a.mapX(x, y), a.mapY(x, y)), 1e-6, "Umkehr y");
            Check.near(s, a.scale(), 1e-6, "Maßstab");
        }
    }

    public void testTemplateMatchesGenerator() {
        SheetTemplate t = Manifest.template();
        Check.near(297, t.pageW, 1e-9, "Seitenbreite");
        Check.near(240, t.field.w, 1e-9, "Feldbreite 240 mm");
        Check.near(144, t.field.h, 1e-9, "Feldhöhe 144 mm");
        Check.equal(4, t.parBoxes.length, "vier Par-Kästchen");
        Check.isTrue(t.tlToTr() > t.tlToBl() * 1.3, "Seitenverhältnis unterscheidet tr und bl eindeutig");
        SheetTemplate res = SheetTemplate.loadDefault();
        Check.near(t.tlX, res.tlX, 1e-12, "Ressource im Kern entspricht template.json");
        Check.near(t.parBoxes[3].x, res.parBoxes[3].x, 1e-12, "Ressource im Kern entspricht template.json");
    }

    public void testGarbageInputsDoNotCrash() {
        ScanAnalyzer an = Manifest.analyzer();
        Check.isTrue(an.analyze(null, 0, 0).hasError(ScanError.IMAGE_TOO_SMALL), "null");
        Check.isTrue(an.analyze(new int[10], 5, 2).hasError(ScanError.IMAGE_TOO_SMALL), "winzig");
        Check.isTrue(an.analyze(new int[10], 3000, 2000).hasError(ScanError.IMAGE_TOO_SMALL), "Array zu kurz");
        Random rnd = new Random(9);
        int w = 1200;
        int h = 850;
        int[] px = new int[w * h];
        for (int i = 0; i < px.length; i++) {
            px[i] = 0xFF000000 | rnd.nextInt(0x1000000);
        }
        ScanResult r = an.analyze(px, w, h);
        Check.isTrue(r.hasError(ScanError.SHEET_NOT_FOUND), "Farbrauschen: " + r.errors());
        java.util.Arrays.fill(px, 0xFF000000);
        Check.isTrue(an.analyze(px, w, h).hasError(ScanError.SHEET_NOT_FOUND), "schwarzes Bild");
        // Streifenmuster, das zeilenweise 1:1:3:1:1 ergibt, aber senkrecht nicht
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int m = (x / 6) % 7;
                boolean dark = m == 0 || m == 2 || m == 3 || m == 4 || m == 6;
                px[y * w + x] = dark ? 0xFF000000 : 0xFFFFFFFF;
            }
        }
        Check.isTrue(an.analyze(px, w, h).hasError(ScanError.SHEET_NOT_FOUND), "Streifen");
    }

    public void testErrorMessagesAreGerman() {
        for (ScanError e : ScanError.values()) {
            Check.isTrue(e.message().length() > 15 && e.message().length() < 140, "Meldung lesbar: " + e);
            Check.isTrue(e.message().endsWith(".") || e.message().endsWith("?"), "vollständiger Satz: " + e);
        }
    }
}
