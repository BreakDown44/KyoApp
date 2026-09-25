package de.scangolf.pc;

import de.scangolf.core.game.Game;
import de.scangolf.core.level.CellType;
import de.scangolf.core.level.Level;
import de.scangolf.core.level.LevelIO;
import de.scangolf.core.render.GameScreen;
import de.scangolf.core.render.MessageScreen;
import de.scangolf.core.util.ArgbImage;
import de.scangolf.core.scan.ScanAnalyzer;
import de.scangolf.core.scan.ScanDebug;
import de.scangolf.core.scan.ScanError;
import de.scangolf.core.scan.ScanResult;
import de.scangolf.core.scan.ScanWarning;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/** Einstiegspunkt der PC-Testumgebung. */
public final class Main {

    private Main() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            usage();
            return;
        }
        switch (args[0]) {
            case "analyze":
                System.exit(analyze(args));
                break;
            case "run":
                run(args);
                break;
            case "visual":
                Visual.main(java.util.Arrays.copyOfRange(args, 1, args.length));
                break;
            default:
                usage();
        }
    }

    private static void usage() {
        System.err.println("Aufruf:");
        System.err.println("  analyze <scan.png> [ausgabeordner]   Scan auswerten, Debug-Bild + Level schreiben");
        System.err.println("  run <scan.png|level.json>           spielen (Swing-Fenster)");
        System.err.println("  visual <ordner>                     Kontrollbilder erzeugen");
        System.exit(2);
    }

    static int analyze(String[] args) throws IOException {
        if (args.length < 2) {
            usage();
        }
        File in = new File(args[1]);
        File outDir = args.length > 2 ? new File(args[2]) : in.getAbsoluteFile().getParentFile();
        Images.Raw raw = Images.load(in);
        ScanAnalyzer an = new ScanAnalyzer();
        ScanResult r = an.analyze(raw.argb, raw.width, raw.height);
        printResult(in.getName(), r);
        String base = in.getName().replaceFirst("\\.[^.]+$", "");
        File dbg = new File(outDir, base + ".debug.png");
        Images.savePng(debugImage(raw, r, an), dbg);
        System.out.println("Debug-Bild: " + dbg);
        if (r.isOk()) {
            File lf = new File(outDir, base + ".level.json");
            Files.write(lf.toPath(), LevelIO.toJson(r.level()).getBytes(StandardCharsets.UTF_8));
            System.out.println("Level:      " + lf);
        }
        return r.isOk() ? 0 : 1;
    }

    static void run(String[] args) throws IOException {
        if (args.length < 2) {
            usage();
        }
        int w = GameScreen.DEFAULT_WIDTH;
        int h = GameScreen.DEFAULT_HEIGHT;
        for (int i = 2; i + 1 < args.length; i++) {
            if (args[i].equals("--size")) {
                String[] p = args[i + 1].split("x");
                w = Integer.parseInt(p[0]);
                h = Integer.parseInt(p[1]);
            }
        }
        File in = new File(args[1]);
        Level level;
        if (in.getName().endsWith(".json")) {
            level = LevelIO.fromJson(new String(Files.readAllBytes(in.toPath()), StandardCharsets.UTF_8));
        } else {
            Images.Raw raw = Images.load(in);
            ScanResult r = new ScanAnalyzer().analyze(raw.argb, raw.width, raw.height);
            printResult(in.getName(), r);
            if (!r.isOk()) {
                String[] lines = new String[r.errors().size()];
                for (int i = 0; i < lines.length; i++) {
                    lines[i] = r.errors().get(i).message();
                }
                GameWindow.open("ScanGolf", new MessageScreen(w, h, "Scan nicht erkannt", lines, "Beenden",
                        () -> System.exit(1)));
                return;
            }
            level = r.level();
        }
        File outDir = new File(System.getProperty("scangolf.root", "."), "urkunden");
        GameScreen[] screen = new GameScreen[1];
        screen[0] = new GameScreen(level, w, h, new GameScreen.Listener() {
            @Override
            public void onPrintCertificate(Game game) {
                try {
                    File f = CertificateExport.save(game, outDir);
                    System.out.println("Urkunde gespeichert: " + f);
                    screen[0].showToast("Urkunde gespeichert: " + f.getName(), 0xE02E7D32, 4000);
                } catch (IOException e) {
                    screen[0].showToast("Fehler beim Speichern: " + e.getMessage(), 0xE0C62828, 4000);
                }
            }

            @Override
            public void onPlayAgain(Game newGame) {
                System.out.println("Neue Runde");
            }
        });
        GameWindow.open("ScanGolf", screen[0]);
    }

    /** Kontrollbild der Auswertung (verkleinerter Scan mit Overlays und Infospalte). */
    static java.awt.image.BufferedImage debugImage(Images.Raw raw, ScanResult r, ScanAnalyzer an) {
        int f = ScanDebug.previewFactor(raw.width, raw.height, 1400);
        ArgbImage prev = ScanDebug.preview(raw.argb, raw.width, raw.height, f);
        return Offscreen.render(ScanDebug.canvasWidth(prev), ScanDebug.canvasHeight(prev), 1.0,
                c -> ScanDebug.draw(c, r, an.template(), prev, f));
    }

    static void printResult(String name, ScanResult r) {
        System.out.println("Datei:      " + name + " (" + r.imageWidth() + "x" + r.imageHeight() + ")");
        if (r.pageToImage() != null) {
            System.out.printf("Blatt:      %.1f dpi, Drehung %.2f°, Block-Abweichung %.2f mm, Arbeitsfaktor %d%n",
                    r.dpi(), r.rotationDegrees(), r.blockResidualMm(), r.workFactor());
            System.out.printf("Papier:     #%06X%n", r.paperRgb());
        }
        System.out.println("Ergebnis:   " + (r.isOk() ? "OK" : "FEHLER"));
        for (ScanError e : r.errors()) {
            System.out.println("  Fehler   " + e + ": " + e.message());
        }
        for (ScanWarning w : r.warnings()) {
            System.out.println("  Hinweis  " + w + ": " + w.message());
        }
        if (!r.detail().isEmpty()) {
            System.out.println("  Detail   " + r.detail());
        }
        if (r.start() != null) {
            System.out.printf("Start:      %.1f / %.1f mm%n", r.start()[0], r.start()[1]);
        }
        if (r.hole() != null) {
            System.out.printf("Loch:       %.1f / %.1f mm%n", r.hole()[0], r.hole()[1]);
        }
        if (r.parFill() != null) {
            StringBuilder sb = new StringBuilder();
            for (double f : r.parFill()) {
                sb.append(String.format(" %.0f%%", f * 100));
            }
            System.out.println("Par:        " + r.par() + "  (Füllgrad:" + sb + ")");
        }
        if (r.grid() != null) {
            System.out.printf("Raster:     %d Wand, %d Wasser, %d Start, %d Loch%n",
                    r.grid().count(CellType.WALL), r.grid().count(CellType.WATER),
                    r.grid().count(CellType.START), r.grid().count(CellType.HOLE));
        }
        System.out.println("Name/Bahn:  " + (r.nameImage() != null ? "Name erkannt" : "kein Name")
                + ", " + (r.laneImage() != null ? "Bahnname erkannt" : "kein Bahnname"));
        System.out.printf("Zeit:       %d ms (Marken %d ms, Raster %d ms), Arbeitsspeicher %.1f MB%n",
                r.millisTotal(), r.millisMarks(), r.millisGrid(), r.workingBytes() / 1e6);
        Level l = r.level();
        if (l != null) {
            System.out.println("Level:      " + l.cols() + "x" + l.rows() + " Zellen, Par " + l.par());
        }
    }
}
