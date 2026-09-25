package de.scangolf.test;

import de.scangolf.core.app.PrintService;
import de.scangolf.core.app.ScanGolfApp;
import de.scangolf.core.app.ScanService;
import de.scangolf.core.cert.Certificate;
import de.scangolf.core.render.Touch;
import de.scangolf.core.scan.ScanError;
import de.scangolf.pc.Images;
import de.scangolf.pc.Offscreen;

import java.util.ArrayList;
import java.util.List;

/** Gesamter App-Ablauf mit Test-Adaptern (Scanner liefert Testbilder, Drucker sammelt Urkunden). */
public class AppFlowTest {

    private static final class FakeScanner implements ScanService {
        final List<String> queue = new ArrayList<>();
        Callback pending;
        boolean deferred;
        int requests;

        @Override
        public void requestScan(Callback cb) {
            requests++;
            if (deferred) {
                pending = cb;
                return;
            }
            String f = queue.remove(0);
            if (f == null) {
                cb.failed("Papierstau");
            } else {
                Images.Raw raw = Manifest.load(f);
                cb.scanned(raw.argb, raw.width, raw.height);
            }
        }
    }

    private static final class FakePrinter implements PrintService {
        final List<Certificate> printed = new ArrayList<>();

        @Override
        public void print(Certificate c, Callback cb) {
            printed.add(c);
            cb.printed("Urkunde gedruckt");
        }
    }

    private FakeScanner scanner;
    private FakePrinter printer;
    private long t;

    /** Jeder Test bekommt frische Adapter (der Runner nutzt eine Instanz je Klasse). */
    private ScanGolfApp app() {
        scanner = new FakeScanner();
        printer = new FakePrinter();
        t = 0;
        return new ScanGolfApp(800, 480, Manifest.analyzer(), scanner, printer, () -> new int[] {2026, 9, 25});
    }

    private void tick(ScanGolfApp a, int frames) {
        for (int i = 0; i < frames; i++) {
            t += 16;
            a.update(t);
        }
    }

    private void tap(ScanGolfApp a, double x, double y) {
        a.touch(Touch.DOWN, x, y);
        a.touch(Touch.UP, x, y);
    }

    /** Große Schaltfläche der Hinweisbildschirme (unten mittig). */
    private void tapMainButton(ScanGolfApp a) {
        tap(a, 400, 405);
    }

    public void testHappyPathScanPlayPrintNextPerson() {
        ScanGolfApp a = app();
        tick(a, 2);
        Check.equal(ScanGolfApp.WELCOME, a.state(), "Start");
        Offscreen.render(800, 480, 1, a::render);
        scanner.queue.add("beispiel-300dpi.png");
        tapMainButton(a);
        Check.equal(ScanGolfApp.ANALYZING, a.state(), "nach dem Scan: Erkennung angekündigt");
        Offscreen.render(800, 480, 1, a::render);
        tick(a, 3);
        Check.equal(ScanGolfApp.PLAYING, a.state(), "Bahn erkannt, Spiel läuft");
        Check.equal(4, a.gameScreen().game().par(), "Par aus dem Scan");
        a.gameScreen().game().giveUp();
        tick(a, 60);
        Check.isTrue(a.gameScreen().isResultVisible(), "Ergebnis sichtbar");
        double[] p = a.gameScreen().printButtonCenter();
        tap(a, p[0], p[1]);
        Check.equal(1, printer.printed.size(), "Urkunde an den Drucker");
        Check.equal("25. September 2026", printer.printed.get(0).dateText(), "Datum von der Plattform");
        Check.notNull(printer.printed.get(0).level().nameImage(), "Name aus dem Scan auf der Urkunde");
        double[] n = a.gameScreen().newLevelButtonCenter();
        tap(a, n[0], n[1]);
        Check.equal(ScanGolfApp.WELCOME, a.state(), "Neue Bahn: zurück zum Start");
    }

    public void testScanErrorShowsMessagesAndRescan() {
        ScanGolfApp a = app();
        tick(a, 1);
        scanner.queue.add("neg-start-fehlt.png");
        scanner.queue.add("beispiel-150dpi.png");
        tapMainButton(a);
        tick(a, 3);
        Check.equal(ScanGolfApp.ERROR, a.state(), "Fehlerbildschirm");
        Check.isTrue(a.lastResult().hasError(ScanError.NO_START), "Grund: kein Start");
        RecordingCanvas rec = new RecordingCanvas(800, 480, null);
        a.render(rec);
        StringBuilder all = new StringBuilder();
        for (RecordingCanvas.Op o : rec.ops) {
            if (o.text != null) {
                all.append(o.text).append(' ');
            }
        }
        Check.isTrue(all.toString().contains(ScanError.NO_START.message()), "Fehlermeldung wird angezeigt: " + all);
        tapMainButton(a);
        tick(a, 3);
        Check.equal(ScanGolfApp.PLAYING, a.state(), "Neu scannen führt zum Spiel");
    }

    public void testScannerFailureAndLateCallback() {
        ScanGolfApp a = app();
        tick(a, 1);
        scanner.queue.add(null);
        tapMainButton(a);
        Check.equal(ScanGolfApp.ERROR, a.state(), "Scannerfehler wird gemeldet");
        scanner.deferred = true;
        tapMainButton(a);
        Check.equal(ScanGolfApp.SCANNING, a.state(), "wartet auf Scanner");
        ScanService.Callback late = scanner.pending;
        a.showWelcome();
        Images.Raw raw = Manifest.load("beispiel-300dpi.png");
        late.scanned(raw.argb, raw.width, raw.height);
        tick(a, 3);
        Check.equal(ScanGolfApp.WELCOME, a.state(), "verspäteter Scan nach Abbruch wird ignoriert");
    }

    public void testWarningIsShownAsToast() {
        ScanGolfApp a = app();
        tick(a, 1);
        scanner.queue.add("bahn-par-keins.png");
        tapMainButton(a);
        tick(a, 5);
        Check.equal(ScanGolfApp.PLAYING, a.state(), "Spiel trotz Warnung");
        RecordingCanvas rec = new RecordingCanvas(800, 480, null);
        a.render(rec);
        boolean shown = false;
        for (RecordingCanvas.Op o : rec.ops) {
            shown |= o.text != null && o.text.contains("Par 3");
        }
        Check.isTrue(shown, "Hinweis 'es gilt Par 3' eingeblendet");
    }

    public void testIdleReturnsToWelcome() {
        ScanGolfApp a = app();
        a.setIdleTimeout(10_000);
        tick(a, 1);
        scanner.queue.add("beispiel-300dpi.png");
        tapMainButton(a);
        tick(a, 3);
        Check.equal(ScanGolfApp.PLAYING, a.state(), "spielt");
        tick(a, 500);
        Check.equal(ScanGolfApp.PLAYING, a.state(), "nach 8 s noch im Spiel");
        tick(a, 200);
        Check.equal(ScanGolfApp.WELCOME, a.state(), "nach >10 s ohne Berührung zurück zum Start");
    }
}
