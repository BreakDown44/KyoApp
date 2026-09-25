package de.scangolf.test;

import de.scangolf.core.game.Game;
import de.scangolf.core.game.GameState;
import de.scangolf.core.game.Physics;
import de.scangolf.core.level.Level;
import de.scangolf.core.level.Rules;

/** Einzelfälle der Physik. */
public class PhysicsTest {

    /** Simuliert bis zum Ende des Schlags; liefert die Anzahl Schritte. */
    static int roll(Game g) {
        int n = 0;
        while (g.state() == GameState.ROLLING) {
            g.step();
            n++;
            if (n > Physics.MAX_ROLL_STEPS + 10) {
                throw Check.fail("Schlag endet nicht");
            }
        }
        return n;
    }

    public void testStraightPuttIntoHole() {
        Level l = TestLevels.open(40, 72, 140, 72);
        Game g = new Game(l);
        Check.isTrue(g.shoot(1, 0, 0.5), "Schlag angenommen");
        roll(g);
        Check.equal(GameState.HOLED, g.state(), "gerader Schlag mittlerer Stärke landet im Loch");
        Check.equal(1, g.strokes(), "ein Schlag");
        Check.equal("Hole-in-One", g.resultLabel(), "Bezeichnung");
    }

    public void testTooFastRollsOverHole() {
        Level l = TestLevels.open(40, 72, 100, 72);
        Game g = new Game(l);
        g.shoot(1, 0, 1.0);
        double minDist = Double.MAX_VALUE;
        double maxX = 0;
        int n = 0;
        while (g.state() == GameState.ROLLING && n++ < 5000) {
            g.step();
            minDist = Math.min(minDist, Math.hypot(g.ballX() - 100, g.ballY() - 72));
            maxX = Math.max(maxX, g.ballX());
        }
        Check.isTrue(minDist < Rules.HOLE_CAPTURE_RADIUS_MM, "Kugel lief über das Loch (min. Abstand " + minDist + ")");
        Check.isTrue(g.state() != GameState.HOLED, "zu schnell: nicht eingelocht");
        Check.isTrue(maxX > 130, "Kugel rollte weiter (bis x=" + maxX + ")");
    }

    public void testSlowBallNearHoleEdgeFallsIn() {
        Level l = TestLevels.open(40, 72, 100, 76);
        Game g = new Game(l);
        // knapp am Lochrand vorbei, langsam: Mulde zieht die Kugel hinein
        g.shoot(1, 0, 0.47);
        roll(g);
        Check.equal(GameState.HOLED, g.state(), "langsame Kugel am Rand fällt hinein");
    }

    public void testWaterPenaltyAndReset() {
        Level l = TestLevels.waterStrip();
        Game g = new Game(l);
        double sx = g.ballX();
        double sy = g.ballY();
        g.shoot(1, 0, 0.8);
        roll(g);
        Check.equal(GameState.AIMING, g.state(), "nach Wasser wieder zielen");
        Check.equal(2, g.strokes(), "Schlag + Strafschlag");
        Check.equal(1, g.penalties(), "ein Strafschlag");
        Check.equal(sx, g.ballX(), "x zurück auf Position vor dem Schlag");
        Check.equal(sy, g.ballY(), "y zurück auf Position vor dem Schlag");
        Check.isTrue(g.traces().get(0).water(), "Spur als Wasser markiert");
    }

    /** Winkel der aktuellen Geschwindigkeit in Grad. */
    private static double angle(Game g) {
        return Math.toDegrees(Math.atan2(g.ballVY(), g.ballVX()));
    }

    public void testBounceOnVerticalWall() {
        Level l = TestLevels.verticalWall();
        Game g = new Game(l);
        double inAngle = -30;   // nach rechts oben
        g.shoot(Math.cos(Math.toRadians(inAngle)), Math.sin(Math.toRadians(inAngle)), 0.9);
        double speedBefore = 0;
        double outAngle = Double.NaN;
        double speedAfter = 0;
        int n = 0;
        while (g.state() == GameState.ROLLING && n++ < 5000) {
            double s = g.ballSpeed();
            double vxBefore = g.ballVX();
            g.step();
            if (vxBefore > 0 && g.ballVX() < 0) {
                speedBefore = s;
                // nach dem Abprall zwei Schritte weiter messen (keine Wand mehr im Kontakt)
                g.step();
                outAngle = angle(g);
                speedAfter = g.ballSpeed();
                break;
            }
        }
        Check.isFalse(Double.isNaN(outAngle), "Abprall fand statt");
        // Spiegelung an senkrechter Wand mit Dämpfung: vx' = -e*vx, vy' = k*vy
        // (ohne Dämpfung würde -30° zu -150°; mit e=0,7 und k=0,95 wird der Ausfall flacher).
        double ex = -Physics.RESTITUTION * Math.cos(Math.toRadians(inAngle));
        double ey = Physics.TANGENT_KEEP * Math.sin(Math.toRadians(inAngle));
        double expected = Math.toDegrees(Math.atan2(ey, ex));
        Check.info(String.format("senkrechte Wand: ein %.0f°, aus %.2f° (Modell %.2f°)", inAngle, outAngle, expected));
        Check.near(expected, outAngle, 2, "Ausfallswinkel wie Spiegelung mit Dämpfung");
        Check.isTrue(speedAfter < speedBefore * 0.95, "Abprall dämpft (" + speedBefore + " -> " + speedAfter + ")");
        Check.isTrue(speedAfter > speedBefore * 0.6, "aber nicht zu stark");
        Check.isTrue(g.ballX() < 150 - Rules.BALL_RADIUS_MM + 1e-6, "Kugel bleibt vor der Wand");
    }

    public void testBounceOn45DegreeWall() {
        Level l = TestLevels.diagonal45();
        Game g = new Game(l);
        g.shoot(1, 0, 0.9);          // waagerecht nach rechts auf die Wand x + y = 200
        double out = Double.NaN;
        int n = 0;
        while (g.state() == GameState.ROLLING && n++ < 5000) {
            double vyBefore = g.ballVY();
            g.step();
            if (Math.abs(vyBefore) < 1 && Math.abs(g.ballVY()) > 20) {
                for (int k = 0; k < 3 && g.state() == GameState.ROLLING; k++) {
                    g.step();
                }
                out = angle(g);
                break;
            }
        }
        Check.isFalse(Double.isNaN(out), "Abprall fand statt");
        Check.info(String.format("45°-Wand: ein 0°, aus %.2f° (ideal -90°)", out));
        // Wandnormale (-1,-1)/√2: (1,0) wird zu (0,-1), also -90° (nach oben)
        Check.near(-90, out, 12, "45°-Wand lenkt um ~90° um, nicht achsparallel");
    }

    public void testBallRestsAndStateReturnsToAiming() {
        Level l = TestLevels.open(40, 72, 200, 20);
        Game g = new Game(l);
        g.shoot(0, 1, 0.3);
        int steps = roll(g);
        Check.equal(GameState.AIMING, g.state(), "Kugel liegt wieder");
        Check.equal(0.0, g.ballSpeed(), "Geschwindigkeit 0");
        Check.isTrue(steps < 5 * Physics.HZ, "kommt schnell zur Ruhe (" + steps + " Schritte)");
    }

    public void testShotDistances() {
        for (double power : new double[] {0.25, 0.5, 1.0}) {
            Level l = TestLevels.open(8, 72, 235, 5);
            Game g = new Game(l);
            g.shoot(1, 0, power);
            double maxX = g.ballX();
            while (g.state() == GameState.ROLLING) {
                g.step();
                maxX = Math.max(maxX, g.ballX());
            }
            Check.info(String.format("Stärke %.2f: %.0f mm weit (Feld 240 mm)", power, maxX - 8));
            if (power == 1.0) {
                Check.isTrue(maxX >= 240 - Rules.BALL_RADIUS_MM - 0.01, "volle Stärke reicht über das ganze Feld");
            }
            if (power == 0.25) {
                Check.isTrue(maxX - 8 > 20 && maxX - 8 < 120, "kleine Stärke für kurze Putts");
            }
        }
    }
}
