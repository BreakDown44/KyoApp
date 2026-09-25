package de.scangolf.core.app;

import de.scangolf.core.cert.Certificate;
import de.scangolf.core.game.Game;
import de.scangolf.core.level.Level;
import de.scangolf.core.render.Canvas;
import de.scangolf.core.render.GameScreen;
import de.scangolf.core.render.MessageScreen;
import de.scangolf.core.render.Screen;
import de.scangolf.core.scan.ScanAnalyzer;
import de.scangolf.core.scan.ScanError;
import de.scangolf.core.scan.ScanResult;
import de.scangolf.core.scan.ScanWarning;

import java.util.List;

/**
 * Gesamter Ablauf der App, plattformneutral: Startbildschirm, Scannen, Erkennen, Fehler oder
 * Spiel, Urkunde drucken, zurück für die nächste Person (auch nach Inaktivität).
 *
 * Die Plattform liefert nur die Adapter (Scanner, Drucker, Datum) und ruft update/render/touch
 * auf. Alle Rückrufe der Adapter müssen im selben Thread ankommen wie update/render/touch
 * (der Kern hat keine Threads und keine Synchronisation).
 */
public final class ScanGolfApp implements Screen {

    public static final int WELCOME = 0;
    public static final int SCANNING = 1;
    public static final int ANALYZING = 2;
    public static final int ERROR = 3;
    public static final int PLAYING = 4;

    /** Nach so langer Untätigkeit (ohne Berührung) zurück zum Start. */
    public static final long DEFAULT_IDLE_MS = 3 * 60 * 1000;

    private final int width;
    private final int height;
    private final ScanAnalyzer analyzer;
    private final ScanService scanner;
    private final PrintService printer;
    private final DateSource date;
    private long idleMs = DEFAULT_IDLE_MS;

    private int state;
    private Screen current;
    private GameScreen gameScreen;
    private ScanResult lastResult;
    private int[] pending;
    private int pendingW;
    private int pendingH;
    private int framesShown;
    private int scanRequest;
    private long now;
    private long lastInput;

    public ScanGolfApp(int width, int height, ScanAnalyzer analyzer, ScanService scanner, PrintService printer,
                       DateSource date) {
        this.width = width;
        this.height = height;
        this.analyzer = analyzer;
        this.scanner = scanner;
        this.printer = printer;
        this.date = date;
        showWelcome();
    }

    public void setIdleTimeout(long ms) {
        idleMs = ms;
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    public int state() {
        return state;
    }

    /** Ergebnis der letzten Auswertung (oder null). */
    public ScanResult lastResult() {
        return lastResult;
    }

    /** Aktueller Spielbildschirm (nur im Zustand PLAYING, sonst null). */
    public GameScreen gameScreen() {
        return state == PLAYING ? gameScreen : null;
    }

    // ================================================================ Abläufe

    public void showWelcome() {
        state = WELCOME;
        gameScreen = null;
        pending = null;
        current = new MessageScreen(width, height, "ScanGolf", new String[] {
            "1. Bahn auf die Vorlage zeichnen: Wände schwarz, Start rot, Loch grün, Wasser blau.",
            "2. Blatt in den Scanner legen.",
            "3. „Scannen“ drücken – dann mit dem Finger spielen!",
        }, "Scannen", new MessageScreen.Listener() {
            public void onConfirm() {
                startScan();
            }
        });
    }

    public void startScan() {
        state = SCANNING;
        final int request = ++scanRequest;
        current = new MessageScreen(width, height, "Scannen …", new String[] {"Bitte warten, das Blatt wird eingelesen."},
                null, null);
        scanner.requestScan(new ScanService.Callback() {
            public void scanned(int[] argb, int w, int h) {
                if (request != scanRequest || state != SCANNING) {
                    return;
                }
                pending = argb;
                pendingW = w;
                pendingH = h;
                framesShown = 0;
                state = ANALYZING;
                current = new MessageScreen(width, height, "Bahn wird erkannt …",
                        new String[] {"Einen Moment bitte."}, null, null);
            }

            public void failed(String message) {
                if (request != scanRequest || state != SCANNING) {
                    return;
                }
                showError(new String[] {"Der Scan hat nicht geklappt.", message});
            }
        });
    }

    private void showError(String[] lines) {
        state = ERROR;
        current = new MessageScreen(width, height, "Scan nicht erkannt", lines, "Neu scannen",
                new MessageScreen.Listener() {
                    public void onConfirm() {
                        startScan();
                    }
                });
    }

    /** Wertet das anstehende Bild aus (normalerweise automatisch im nächsten update). */
    void analyzePending() {
        int[] px = pending;
        pending = null;
        ScanResult r = analyzer.analyze(px, pendingW, pendingH);
        lastResult = r;
        if (r.isOk()) {
            play(r.level());
            List<ScanWarning> w = r.warnings();
            if (!w.isEmpty()) {
                gameScreen.showToast(w.get(0).message(), 0xE0B26A00, 5000);
            }
        } else {
            List<ScanError> errs = r.errors();
            String[] lines = new String[errs.size()];
            for (int i = 0; i < lines.length; i++) {
                lines[i] = errs.get(i).message();
            }
            showError(lines);
        }
    }

    /** Level direkt spielen (z. B. gespeicherte Bahn). */
    public void play(Level level) {
        state = PLAYING;
        gameScreen = new GameScreen(level, width, height, new GameScreen.Listener() {
            public void onPrintCertificate(Game game) {
                print(game);
            }

            public void onPlayAgain(Game newGame) {
            }

            public void onNewLevel() {
                showWelcome();
            }
        });
        gameScreen.setNewLevelButton(true);
        current = gameScreen;
    }

    private void print(Game game) {
        int[] d = date.today();
        Certificate c = Certificate.fromGame(game, d[2], d[1], d[0]);
        final GameScreen gs = gameScreen;
        printer.print(c, new PrintService.Callback() {
            public void printed(String info) {
                if (gs == gameScreen) {
                    gs.showToast(info == null ? "Urkunde gedruckt" : info, 0xE02E7D32, 4000);
                }
            }

            public void failed(String message) {
                if (gs == gameScreen) {
                    gs.showToast("Drucken fehlgeschlagen: " + message, 0xE0C62828, 5000);
                }
            }
        });
    }

    // ================================================================ Screen

    public void update(long nowMs) {
        now = nowMs;
        if (lastInput == 0) {
            lastInput = nowMs;
        }
        if (state == ANALYZING && pending != null) {
            // erst einen Frame "Bahn wird erkannt" zeigen, dann rechnen
            if (framesShown++ >= 1) {
                analyzePending();
            }
        }
        if ((state == PLAYING || state == ERROR) && idleMs > 0 && nowMs - lastInput > idleMs) {
            showWelcome();
        }
        current.update(nowMs);
    }

    public void render(Canvas c) {
        current.render(c);
    }

    public void touch(int type, double x, double y) {
        lastInput = now;
        current.touch(type, x, y);
    }
}
