package de.scangolf.pc;

import de.scangolf.core.cert.Certificate;
import de.scangolf.core.game.Game;
import de.scangolf.core.game.GameState;
import de.scangolf.core.level.Level;
import de.scangolf.core.level.LevelIO;
import de.scangolf.core.render.GameScreen;
import de.scangolf.core.render.MessageScreen;
import de.scangolf.core.render.Screen;
import de.scangolf.core.render.Touch;
import de.scangolf.core.scan.ScanAnalyzer;
import de.scangolf.core.scan.ScanError;
import de.scangolf.core.scan.ScanResult;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/** Erzeugt Kontrollbilder für die Sichtprüfung (Scan-Debug, Spiel-Screenshots, Urkunde). */
public final class Visual {

    private Visual() {
    }

    public static void main(String[] args) throws IOException {
        File out = new File(args.length > 0 ? args[0] : "build/visual");
        File root = new File(System.getProperty("scangolf.root", "."));
        scans(out, root);
        game(out, root);
    }

    static void scans(File out, File root) throws IOException {
        File imgs = new File(root, "testbilder/out");
        String[] scans = {
            "beispiel-300dpi.png", "beispiel-rot90.png", "beispiel-schief+4.png", "beispiel-kombi.jpg",
            "beispiel-150dpi.png", "bahn-schraeg.png", "bahn-duenn.png", "bahn-luecken.png",
            "bahn-gewunden.png", "bahn-wasser.png", "bahn-start-nah-wand.png", "neg-start-in-wand.png",
            "neg-loch-eingemauert.png", "neg-ecke-abgeschnitten.png", "leer-gelb-sehr-dunkel.png",
            "neg-foto.png",
        };
        ScanAnalyzer an = new ScanAnalyzer();
        for (String s : scans) {
            File f = new File(imgs, s);
            if (!f.exists()) {
                System.err.println("fehlt: " + f);
                continue;
            }
            Images.Raw raw = Images.load(f);
            ScanResult r = an.analyze(raw.argb, raw.width, raw.height);
            File o = new File(out, "scan-" + s.replaceFirst("\\.[^.]+$", "") + ".png");
            Images.savePng(Main.debugImage(raw, r, an), o);
            System.out.println(o + "  " + (r.isOk() ? "OK" : r.errors()));
        }
    }

    static Level level(File root, String name) throws IOException {
        return LevelIO.fromJson(new String(Files.readAllBytes(new File(root, "test/levels/" + name + ".json").toPath()),
                StandardCharsets.UTF_8));
    }

    static void shot(Screen s, File out, String name, double scale) throws IOException {
        File f = new File(out, name);
        Images.savePng(Offscreen.render(s.width(), s.height(), scale, s::render), f);
        System.out.println(f);
    }

    /** Level mit anderem Start (gleiches Raster, gleiche Bilder). */
    static Level withStart(Level l, double x, double y) {
        return new Level(l.gridCopy(), x, y, l.holeX(), l.holeY(), l.par(), l.nameImage(), l.laneImage());
    }

    /** Stärke, mit der die Kugel von der aktuellen Position möglichst nah am Ziel liegen bleibt. */
    static double powerFor(Game g, double tx, double ty) {
        Level probe = withStart(g.level(), g.ballX(), g.ballY());
        double best = 0.5;
        double bestErr = Double.MAX_VALUE;
        for (int i = 3; i <= 100; i++) {
            double p = i / 100.0;
            Game t = new Game(probe);
            t.shoot(tx - g.ballX(), ty - g.ballY(), p);
            while (t.state() == GameState.ROLLING) {
                t.step();
            }
            double err = t.state() == GameState.HOLED ? -1 : Math.hypot(t.ballX() - tx, t.ballY() - ty);
            if (err < bestErr) {
                bestErr = err;
                best = p;
            }
        }
        return best;
    }

    /** Spielt eine Folge von Wegpunkten ab (letzter Punkt: Loch). */
    static Game playWaypoints(Level l, double[][] pts) {
        Game g = new Game(l);
        for (double[] p : pts) {
            if (g.isFinished()) {
                break;
            }
            double power = p.length > 2 ? p[2] : powerFor(g, p[0], p[1]);
            g.shoot(p[0] - g.ballX(), p[1] - g.ballY(), power);
            while (g.state() == GameState.ROLLING) {
                g.step();
            }
        }
        return g;
    }

    /** Zeit in 16-ms-Schritten fortschreiben. */
    static long advance(Screen s, long t, long ms) {
        for (long e = 0; e < ms; e += 16) {
            t += 16;
            s.update(t);
        }
        return t;
    }

    static void game(File out, File root) throws IOException {
        Level l = level(root, "beispiel");

        // 1) Zielen mit Hinweis
        GameScreen s = new GameScreen(l, 800, 480, null);
        long t = advance(s, 0, 200);
        shot(s, out, "spiel-01-start.png", 1.0);

        // 2) Ziehen: Finger unten links vom Ball -> Schuss nach rechts oben
        double bx = s.toScreenX(l.startX());
        double by = s.toScreenY(l.startY());
        s.touch(Touch.DOWN, bx + 5, by + 3);
        s.touch(Touch.MOVE, bx - 20, by + 60);
        s.touch(Touch.MOVE, bx - 30, by + 110);
        shot(s, out, "spiel-02-zielen.png", 1.0);
        shot(s, out, "spiel-02-zielen-2x.png", 2.0);
        s.touch(Touch.UP, bx - 30, by + 110);
        t = advance(s, t, 420);
        shot(s, out, "spiel-03-rollen.png", 1.0);

        // 3) Wasser
        GameScreen w = new GameScreen(withStart(l, 95, 88), 800, 480, null);
        long tw = advance(w, 0, 100);
        double wx = w.toScreenX(95);
        double wy = w.toScreenY(88);
        w.touch(Touch.DOWN, wx, wy);
        w.touch(Touch.MOVE, wx, wy - 80);
        w.touch(Touch.UP, wx, wy - 80);
        for (int i = 0; i < 400 && w.game().strokes() < 2; i++) {
            tw = advance(w, tw, 16);
        }
        tw = advance(w, tw, 200);
        shot(w, out, "spiel-04-wasser.png", 1.0);

        // 4) Eingelocht + Ergebnis
        GameScreen h = new GameScreen(withStart(l, l.holeX() - 45, l.holeY() + 8), 800, 480, null);
        long th = advance(h, 0, 50);
        double p = powerFor(h.game(), l.holeX(), l.holeY());
        h.game().shoot(l.holeX() - h.game().ballX(), l.holeY() - h.game().ballY(), p);
        for (int i = 0; i < 1000 && !h.game().isFinished(); i++) {
            th = advance(h, th, 16);
        }
        th = advance(h, th, 150);
        shot(h, out, "spiel-05-einlochen.png", 1.0);
        th = advance(h, th, 1000);
        shot(h, out, "spiel-06-ergebnis.png", 1.0);
        double[] pb = h.printButtonCenter();
        h.touch(Touch.DOWN, pb[0], pb[1]);
        shot(h, out, "spiel-07-ergebnis-gedrueckt.png", 1.0);

        // 5) Aufgegeben
        GameScreen g = new GameScreen(level(root, "wasser"), 800, 480, null);
        long tg = advance(g, 0, 50);
        g.game().giveUp();
        tg = advance(g, tg, 600);
        shot(g, out, "spiel-08-aufgegeben.png", 1.0);

        // 6) Andere logische Größe
        GameScreen big = new GameScreen(level(root, "gewunden"), 1024, 600, null);
        advance(big, 0, 100);
        shot(big, out, "spiel-09-1024x600.png", 1.0);
        GameScreen small = new GameScreen(level(root, "schraeg"), 480, 272, null);
        advance(small, 0, 100);
        shot(small, out, "spiel-10-480x272.png", 1.0);

        // 7) Fehlerbildschirm
        MessageScreen m = new MessageScreen(800, 480, "Scan nicht erkannt",
                new String[] {ScanError.NO_START.message(), ScanError.HOLE_UNREACHABLE.message()}, "Neu scannen", null);
        shot(m, out, "spiel-11-fehler.png", 1.0);

        // 8) Urkunden
        Game full = playWaypoints(l, new double[][] {
            {30, 30}, {100, 25}, {96, 116, 0.75}, {116, 128}, {160, 125}, {214, 76}, {l.holeX(), l.holeY()},
            {l.holeX(), l.holeY()},
        });
        System.out.println("Beispielrunde: " + full.strokes() + " Schläge, " + full.state() + ", " + full.resultLabel());
        saveCert(out, "urkunde-beispiel.png", Certificate.fromGame(full, 25, 9, 2026));

        Level seen = level(root, "wasser");
        Game birdie = playWaypoints(seen, new double[][] {{seen.holeX(), seen.holeY()}});
        if (!birdie.isFinished()) {
            birdie = playWaypoints(seen, new double[][] {{120, 72}, {seen.holeX(), seen.holeY()},
                {seen.holeX(), seen.holeY()}});
        }
        System.out.println("Seenplatte: " + birdie.strokes() + " Schläge, " + birdie.resultLabel());
        saveCert(out, "urkunde-seenplatte.png", Certificate.fromGame(birdie, 1, 3, 2026));

        Level noName = new Level(l.gridCopy(), l.startX(), l.startY(), l.holeX(), l.holeY(), l.par(), null, null);
        Game gaveUp = new Game(noName);
        gaveUp.shoot(0, -1, 0.5);
        while (gaveUp.state() == GameState.ROLLING) {
            gaveUp.step();
        }
        gaveUp.giveUp();
        saveCert(out, "urkunde-ohne-namen.png", Certificate.fromGame(gaveUp, 31, 12, 2026));
    }

    static void saveCert(File out, String name, Certificate c) throws IOException {
        File f = new File(out, name);
        Images.savePng(CertificateExport.render(c, 300), f, 300);
        System.out.println(f);
    }
}
