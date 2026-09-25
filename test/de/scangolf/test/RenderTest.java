package de.scangolf.test;

import de.scangolf.core.cert.Certificate;
import de.scangolf.core.cert.CertificateRenderer;
import de.scangolf.core.game.Game;
import de.scangolf.core.game.GameState;
import de.scangolf.core.level.Level;
import de.scangolf.core.level.Rules;
import de.scangolf.core.render.GameScreen;
import de.scangolf.core.render.LevelRasterizer;
import de.scangolf.core.render.MessageScreen;
import de.scangolf.core.render.Touch;
import de.scangolf.core.util.ArgbImage;
import de.scangolf.pc.CertificateExport;
import de.scangolf.pc.Offscreen;

import java.awt.image.BufferedImage;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/** Darstellung, Bedienung und Urkunde. */
public class RenderTest {

    private static int distinctColors(BufferedImage img, int step) {
        Set<Integer> s = new HashSet<>();
        for (int y = 0; y < img.getHeight(); y += step) {
            for (int x = 0; x < img.getWidth(); x += step) {
                s.add(img.getRGB(x, y));
            }
        }
        return s.size();
    }

    private static Game playRandom(Level l, long seed, int shots) {
        Game g = new Game(l);
        Random rnd = new Random(seed);
        for (int i = 0; i < shots && !g.isFinished(); i++) {
            double a = rnd.nextDouble() * 2 * Math.PI;
            g.shoot(Math.cos(a), Math.sin(a), 0.3 + rnd.nextDouble() * 0.7);
            while (g.state() == GameState.ROLLING) {
                g.step();
            }
        }
        return g;
    }

    /** Urkunde mit Namensbild, ohne Namensbild, mit vielen Schlägen – alles innerhalb der Seite. */
    public void testCertificateRendersWithAndWithoutName() {
        Level withImg = TestLevels.load("beispiel");
        Level noImg = new Level(withImg.gridCopy(), withImg.startX(), withImg.startY(), withImg.holeX(),
                withImg.holeY(), withImg.par(), null, null);
        Object[][] variants = {
            {"mit Name", Certificate.fromGame(playRandom(withImg, 1, 3), 25, 9, 2026)},
            {"ohne Name", Certificate.fromGame(playRandom(noImg, 2, 12), 1, 1, 2027)},
            {"aufgegeben", Certificate.fromGame(playRandom(noImg, 3, 20), 31, 12, 2026)},
        };
        for (Object[] v : variants) {
            Certificate c = (Certificate) v[1];
            RecordingCanvas rec = new RecordingCanvas(CertificateRenderer.PAGE_W, CertificateRenderer.PAGE_H, null);
            CertificateRenderer.render(rec, c);
            Check.isFalse(rec.nonFinite, v[0] + ": keine NaN-Koordinaten");
            List<RecordingCanvas.Op> out = rec.outside(null, 0.01);
            Check.isTrue(out.isEmpty(), v[0] + ": alles auf der Seite, aber " + out);
            BufferedImage img = CertificateExport.render(c, 100);
            Check.equal(827, img.getWidth(), v[0] + ": Breite bei 100 dpi");
            Check.isTrue(distinctColors(img, 7) > 50, v[0] + ": Urkunde hat Inhalt");
        }
    }

    public void testCertificateTextFitsInsideBorder() {
        Level l = TestLevels.load("gewunden");
        Game g = playRandom(l, 5, 30);
        Certificate c = Certificate.fromGame(g, 28, 2, 2026);
        BufferedImage probe = new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB);
        java.awt.Graphics2D g2 = probe.createGraphics();
        RecordingCanvas rec = new RecordingCanvas(210, 297, new de.scangolf.pc.Graphics2DCanvas(g2, 210, 297, 1, true));
        CertificateRenderer.render(rec, c);
        g2.dispose();
        for (RecordingCanvas.Op o : rec.ops) {
            if (o.kind.equals("text")) {
                Check.isTrue(o.x0 >= 14 && o.x1 <= 196, "Text innerhalb des Rahmens: " + o);
            }
        }
        Check.info("Ergebnis auf Test-Urkunde: " + c.resultLabel() + " nach " + c.strokes() + " Schlägen");
    }

    public void testLevelRasterizerColors() {
        Level l = TestLevels.load("beispiel");
        ArgbImage img = new LevelRasterizer(l).render(3.0);
        Check.equal(720, img.width(), "Breite 240 mm * 3");
        Check.equal(432, img.height(), "Höhe 144 mm * 3");
        // Pixel über einer Wandzelle ist dunkel, über Wasser blau, auf freiem Rasen grün
        int wall = -1;
        int water = -1;
        for (int y = 3; y < l.rows() - 4 && (wall < 0 || water < 0); y++) {
            for (int x = 3; x < l.cols() - 4; x++) {
                if (wall < 0 && l.cell(x, y) == 1 && l.cell(x + 1, y) == 1 && l.cell(x, y + 1) == 1
                        && l.cell(x + 1, y + 1) == 1) {
                    wall = img.pixel(x * 3 + 3, y * 3 + 3);
                }
                if (water < 0 && l.cell(x, y) == 4 && l.cell(x + 3, y + 3) == 4 && l.cell(x - 3, y - 3) == 4) {
                    water = img.pixel(x * 3 + 1, y * 3 + 1);
                }
            }
        }
        int grass = img.pixel(30 * 3, 60 * 3);
        Check.isTrue(bright(wall) < 90, "Wand dunkel: " + Integer.toHexString(wall));
        Check.isTrue(((water) & 0xFF) > ((water >> 16) & 0xFF) + 60, "Wasser blau: " + Integer.toHexString(water));
        Check.isTrue(((grass >> 8) & 0xFF) > ((grass >> 16) & 0xFF) + 40, "Rasen grün: " + Integer.toHexString(grass));
    }

    private static int bright(int c) {
        return (((c >> 16) & 0xFF) + ((c >> 8) & 0xFF) + (c & 0xFF)) / 3;
    }

    // ------------------------------------------------------------ Bedienung

    public void testDragFromBallShootsOppositeDirection() {
        Level l = TestLevels.open(60, 72, 200, 20);
        GameScreen s = new GameScreen(l, 800, 480, null);
        s.update(0);
        double bx = s.toScreenX(60);
        double by = s.toScreenY(72);
        s.touch(Touch.DOWN, bx + 10, by);         // knapp neben dem Ball zählt
        s.touch(Touch.MOVE, bx - 60, by);
        s.touch(Touch.UP, bx - 100, by);          // nach links gezogen
        Check.equal(1, s.game().strokes(), "Schlag ausgelöst");
        Check.equal(GameState.ROLLING, s.game().state(), "Kugel rollt");
        Check.isTrue(s.game().ballVX() > 0 && Math.abs(s.game().ballVY()) < 1e-9, "Schuss nach rechts (entgegen dem Ziehen)");
        double v1 = s.game().ballSpeed();
        GameScreen s2 = new GameScreen(l, 800, 480, null);
        s2.update(0);
        s2.touch(Touch.DOWN, bx, by);
        s2.touch(Touch.UP, bx - 400, by);         // weit gezogen: gedeckelt
        Check.isTrue(s2.game().ballSpeed() > v1, "länger gezogen = stärker");
        Check.near(de.scangolf.core.game.Physics.MAX_SHOT_SPEED, s2.game().ballSpeed(), 1e-9, "Stärke gedeckelt");
    }

    public void testTouchAwayFromBallAndTinyDragIgnored() {
        Level l = TestLevels.open(60, 72, 200, 20);
        GameScreen s = new GameScreen(l, 800, 480, null);
        s.update(0);
        s.touch(Touch.DOWN, 700, 400);
        s.touch(Touch.UP, 600, 300);
        Check.equal(0, s.game().strokes(), "weit weg vom Ball: kein Schlag");
        double bx = s.toScreenX(60);
        double by = s.toScreenY(72);
        s.touch(Touch.DOWN, bx, by);
        s.touch(Touch.UP, bx - 5, by);
        Check.equal(0, s.game().strokes(), "Mini-Zug: kein Schlag");
        s.touch(Touch.DOWN, bx, by);
        s.touch(Touch.MOVE, bx - 80, by);
        s.touch(Touch.CANCEL, bx - 80, by);
        s.touch(Touch.UP, bx - 80, by);
        Check.equal(0, s.game().strokes(), "abgebrochen: kein Schlag");
    }

    public void testResultButtons() {
        Level l = TestLevels.open(60, 72, 200, 20);
        int[] calls = new int[2];
        GameScreen s = new GameScreen(l, 800, 480, new GameScreen.Listener() {
            @Override
            public void onPrintCertificate(Game game) {
                calls[0]++;
                Check.isTrue(game.isFinished(), "Urkunde nur für beendete Runde");
            }

            @Override
            public void onPlayAgain(Game newGame) {
                calls[1]++;
            }
        });
        s.update(0);
        s.game().giveUp();
        s.update(16);
        s.update(1000);
        Check.isTrue(s.isResultVisible(), "Ergebnis sichtbar");
        double[] p = s.printButtonCenter();
        s.touch(Touch.DOWN, p[0], p[1]);
        s.touch(Touch.UP, p[0], p[1]);
        Check.equal(1, calls[0], "Urkunde drucken ausgelöst");
        double[] a = s.againButtonCenter();
        s.touch(Touch.DOWN, a[0], a[1]);
        s.touch(Touch.UP, p[0], p[1]);           // losgelassen auf anderer Schaltfläche: nichts
        Check.equal(0, calls[1], "kein Auslösen beim Wegziehen");
        s.touch(Touch.DOWN, a[0], a[1]);
        s.touch(Touch.UP, a[0], a[1]);
        Check.equal(1, calls[1], "Nochmal ausgelöst");
        Check.equal(GameState.AIMING, s.game().state(), "neue Runde");
        Check.equal(0, s.game().strokes(), "Zähler zurückgesetzt");
    }

    /** Alle Texte bleiben innerhalb des Bildschirms, auch bei anderen logischen Größen. */
    public void testLayoutFitsAtSeveralSizes() {
        Level l = TestLevels.load("beispiel");
        int[][] sizes = {{800, 480}, {1024, 600}, {480, 272}, {1280, 720}, {800, 600}};
        for (int[] sz : sizes) {
            for (int phase = 0; phase < 4; phase++) {
                GameScreen s = new GameScreen(l, sz[0], sz[1], null);
                s.update(0);
                double bx = s.toScreenX(l.startX());
                double by = s.toScreenY(l.startY());
                if (phase == 1) {
                    s.touch(Touch.DOWN, bx, by);
                    s.touch(Touch.MOVE, bx - 50, by + 70);
                } else if (phase >= 2) {
                    s.game().giveUp();
                    s.update(16);
                    s.update(2000);
                }
                final int ph = phase;
                BufferedImage img = Offscreen.render(sz[0], sz[1], 1.0, c -> {
                    RecordingCanvas rec = new RecordingCanvas(sz[0], sz[1], c);
                    s.render(rec);
                    Check.isFalse(rec.nonFinite, "keine NaN-Koordinaten");
                    List<RecordingCanvas.Op> out = rec.outside("text", 1);
                    Check.isTrue(out.isEmpty(), sz[0] + "x" + sz[1] + " Phase " + ph + ": Text außerhalb " + out);
                });
                Check.isTrue(distinctColors(img, 5) > 20, "Bild hat Inhalt");
            }
        }
    }

    public void testBallTouchTargetIsLarge() {
        Level l = TestLevels.load("beispiel");
        GameScreen s = new GameScreen(l, 800, 480, null);
        double ballPx = Rules.BALL_RADIUS_MM * s.fieldScale();
        Check.info(String.format("Kugel %.1f px Radius, Touch-Radius 64 px (logisch 800x480)", ballPx));
        Check.isTrue(64 > ballPx * 4, "Touchfläche deutlich größer als die Kugel");
    }

    public void testMessageScreenButton() {
        boolean[] hit = new boolean[1];
        MessageScreen m = new MessageScreen(800, 480, "Scan nicht erkannt", new String[] {"Test"}, "OK", () -> hit[0] = true);
        Offscreen.render(800, 480, 1.0, m::render);
        m.touch(Touch.DOWN, 400, 400);
        m.touch(Touch.UP, 400, 400);
        Check.isTrue(hit[0], "Schaltfläche ausgelöst");
    }
}
