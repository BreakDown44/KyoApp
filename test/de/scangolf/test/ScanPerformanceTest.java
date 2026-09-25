package de.scangolf.test;

import de.scangolf.core.scan.ScanAnalyzer;
import de.scangolf.core.scan.ScanResult;
import de.scangolf.pc.Images;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryPoolMXBean;
import java.lang.management.MemoryType;
import java.util.Arrays;

/**
 * Laufzeit und Speicher der Analyse. Ziel: 300-dpi-Scan deutlich unter 2 s, kein Vielfaches
 * des Bildspeichers (die Analyse legt nur kleine Arbeitspuffer an).
 */
public class ScanPerformanceTest {

    private final ScanAnalyzer analyzer = Manifest.analyzer();

    private long[] timeRuns(Images.Raw raw, int warmup, int runs) {
        for (int i = 0; i < warmup; i++) {
            analyzer.analyze(raw.argb, raw.width, raw.height);
        }
        long[] ms = new long[runs];
        for (int i = 0; i < runs; i++) {
            long t0 = System.nanoTime();
            ScanResult r = analyzer.analyze(raw.argb, raw.width, raw.height);
            ms[i] = (System.nanoTime() - t0) / 1_000_000;
            Check.isTrue(r.isOk(), "Analyse muss gelingen");
        }
        Arrays.sort(ms);
        return ms;
    }

    public void testAnalysisTime300dpi() {
        Images.Raw raw = Manifest.load("beispiel-300dpi.png");
        // Erster Lauf dieser Klasse; der JIT ist durch vorherige Tests evtl. schon warm.
        // Einen wirklich kalten Lauf zeigt "./build.sh analyze" (eigene JVM).
        long t0 = System.nanoTime();
        analyzer.analyze(raw.argb, raw.width, raw.height);
        long cold = (System.nanoTime() - t0) / 1_000_000;
        long[] ms = timeRuns(raw, 2, 7);
        long median = ms[ms.length / 2];
        Check.info(String.format("300 dpi (%dx%d, %.1f MPixel): erster Lauf %d ms, warm Median %d ms (min %d, max %d)",
                raw.width, raw.height, raw.width * (double) raw.height / 1e6, cold, median, ms[0], ms[ms.length - 1]));
        Check.isTrue(cold < 2000, "erster Lauf unter 2 s, war " + cold + " ms");
        Check.isTrue(median < 1000, "warmer Median deutlich unter 2 s, war " + median + " ms");
    }

    public void testAnalysisTime600dpi() {
        Images.Raw raw = Manifest.load("beispiel-600dpi.png");
        long[] ms = timeRuns(raw, 1, 3);
        long median = ms[ms.length / 2];
        Check.info(String.format("600 dpi (%dx%d): Median %d ms", raw.width, raw.height, median));
        Check.isTrue(median < 2000, "600 dpi unter 2 s, war " + median + " ms");
    }

    public void testWorkingMemoryIsSmall() {
        Images.Raw raw = Manifest.load("beispiel-300dpi.png");
        long imageBytes = raw.argb.length * 4L;
        ScanResult r = analyzer.analyze(raw.argb, raw.width, raw.height);
        double ratio = r.workingBytes() / (double) imageBytes;
        Check.info(String.format("Arbeitspuffer %.2f MB bei %.1f MB Bild (%.1f %%)",
                r.workingBytes() / 1e6, imageBytes / 1e6, ratio * 100));
        Check.isTrue(ratio < 0.25, "Arbeitspuffer höchstens 25 % des Bildspeichers, war " + ratio);
    }

    /** Gemessener Heap-Spitzenzuwachs während einer Analyse (JVM-Speicherpools). */
    public void testPeakHeapBelowImageSize() {
        Images.Raw raw = Manifest.load("beispiel-300dpi.png");
        long imageBytes = raw.argb.length * 4L;
        analyzer.analyze(raw.argb, raw.width, raw.height);
        System.gc();
        long base = 0;
        for (MemoryPoolMXBean p : ManagementFactory.getMemoryPoolMXBeans()) {
            if (p.getType() == MemoryType.HEAP) {
                base += p.getUsage().getUsed();
                p.resetPeakUsage();
            }
        }
        analyzer.analyze(raw.argb, raw.width, raw.height);
        long peak = 0;
        for (MemoryPoolMXBean p : ManagementFactory.getMemoryPoolMXBeans()) {
            if (p.getType() == MemoryType.HEAP) {
                peak += p.getPeakUsage().getUsed();
            }
        }
        long delta = Math.max(0, peak - base);
        Check.info(String.format("Heap-Zuwachs (Spitze) %.1f MB bei %.1f MB Bild", delta / 1e6, imageBytes / 1e6));
        Check.isTrue(delta < imageBytes, "Analyse darf das Bild nicht kopieren (Zuwachs " + delta / 1e6 + " MB)");
    }
}
