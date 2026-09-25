package de.scangolf.test;

import de.scangolf.core.game.FixedStepClock;
import de.scangolf.core.game.Game;
import de.scangolf.core.game.GameState;
import de.scangolf.core.game.Physics;
import de.scangolf.core.game.ScoreNames;
import de.scangolf.core.game.ShotTrace;
import de.scangolf.core.level.Level;
import de.scangolf.core.level.Rules;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Spielregeln und Determinismus. */
public class GameTest {

    public void testScoreNames() {
        Check.equal("Hole-in-One", ScoreNames.label(1, 3, true), "1 Schlag");
        Check.equal("Albatros", ScoreNames.label(2, 5, true), "-3");
        Check.equal("Eagle", ScoreNames.label(2, 4, true), "-2");
        Check.equal("Birdie", ScoreNames.label(2, 3, true), "-1");
        Check.equal("Par", ScoreNames.label(3, 3, true), "0");
        Check.equal("Bogey", ScoreNames.label(4, 3, true), "+1");
        Check.equal("Doppel-Bogey", ScoreNames.label(5, 3, true), "+2");
        Check.equal("Triple-Bogey", ScoreNames.label(6, 3, true), "+3");
        Check.equal("5 über Par", ScoreNames.label(7, 2, true), "+5");
        Check.equal("Aufgegeben", ScoreNames.label(10, 3, false), "nicht eingelocht");
        Check.equal("-1", ScoreNames.relative(2, 3), "relativ");
        Check.equal("+2", ScoreNames.relative(5, 3), "relativ");
    }

    public void testMaxStrokesEndsRound() {
        Level l = TestLevels.open(20, 20, 220, 120);
        Game g = new Game(l);
        for (int i = 0; i < Rules.MAX_STROKES; i++) {
            Check.equal(GameState.AIMING, g.state(), "vor Schlag " + (i + 1));
            // kleine Schläge weg vom Loch
            Check.isTrue(g.shoot(i % 2 == 0 ? 1 : -1, 0, 0.1), "Schlag " + (i + 1));
            PhysicsTest.roll(g);
        }
        Check.equal(GameState.GAVE_UP, g.state(), "nach 10 Schlägen beendet");
        Check.equal(10, g.strokes(), "Schlagzähler");
        Check.isFalse(g.shoot(1, 0, 1), "kein 11. Schlag");
        Check.equal("Aufgegeben", g.resultLabel(), "Ergebnis");
    }

    public void testWaterPenaltyCanEndRound() {
        Level l = TestLevels.waterStrip();
        Game g = new Game(l);
        for (int i = 0; i < 5; i++) {
            g.shoot(1, 0, 0.9);
            PhysicsTest.roll(g);
        }
        Check.equal(10, g.strokes(), "5 Schläge + 5 Strafschläge");
        Check.equal(GameState.GAVE_UP, g.state(), "Runde vorbei");
    }

    public void testRejectsInvalidShots() {
        Game g = new Game(TestLevels.open(40, 40, 200, 100));
        Check.isFalse(g.shoot(0, 0, 1), "ohne Richtung");
        Check.isFalse(g.shoot(1, 0, 0.001), "zu schwach");
        Check.equal(0, g.strokes(), "kein Schlag gezählt");
        Check.isTrue(g.shoot(1, 0, 0.5), "gültig");
        Check.isFalse(g.shoot(1, 0, 0.5), "während des Rollens");
    }

    public void testGiveUp() {
        Game g = new Game(TestLevels.open(40, 40, 200, 100));
        g.giveUp();
        Check.equal(GameState.GAVE_UP, g.state(), "aufgegeben");
    }

    public void testTraceRecorded() {
        Game g = new Game(TestLevels.open(40, 72, 200, 20));
        g.shoot(1, 0, 0.6);
        PhysicsTest.roll(g);
        List<ShotTrace> t = g.traces();
        Check.equal(1, t.size(), "eine Spur");
        ShotTrace s = t.get(0);
        Check.isTrue(s.pointCount() > 20, "Spur hat Punkte: " + s.pointCount());
        Check.near(40, s.x(0), 1e-6, "Spur beginnt am Start");
        Check.near(g.ballX(), s.x(s.pointCount() - 1), 1e-3, "Spur endet an der Kugel");
    }

    /** Protokoll aller Positionen einer Schlagfolge. */
    private static List<double[]> play(Level l, long seed, int shots) {
        Random rnd = new Random(seed);
        Game g = new Game(l);
        List<double[]> log = new ArrayList<>();
        for (int s = 0; s < shots && !g.isFinished(); s++) {
            double a = rnd.nextDouble() * 2 * Math.PI;
            g.shoot(Math.cos(a), Math.sin(a), 0.2 + 0.8 * rnd.nextDouble());
            while (g.state() == GameState.ROLLING) {
                g.step();
                log.add(new double[] {g.ballX(), g.ballY(), g.ballVX(), g.ballVY()});
            }
        }
        log.add(new double[] {g.strokes(), g.state().ordinal(), 0, 0});
        return log;
    }

    public void testDeterministicReplay() {
        for (String name : new String[] {"beispiel", "gewunden"}) {
            Level l = TestLevels.load(name);
            List<double[]> a = play(l, 77, 10);
            List<double[]> b = play(l, 77, 10);
            Check.equal(a.size(), b.size(), name + ": gleiche Schrittzahl");
            for (int i = 0; i < a.size(); i++) {
                for (int k = 0; k < 4; k++) {
                    if (Double.doubleToRawLongBits(a.get(i)[k]) != Double.doubleToRawLongBits(b.get(i)[k])) {
                        throw Check.fail(name + ": Abweichung in Schritt " + i);
                    }
                }
            }
            Check.info(name + ": " + a.size() + " Schritte bitgleich wiederholt");
        }
    }

    /** Gleiche Schläge bei unterschiedlicher Bildrate ergeben exakt denselben Ablauf. */
    public void testFrameRateIndependent() {
        Level l = TestLevels.load("schraeg");
        long[][] frameTimes = {{16}, {33}, {7}, {5, 40, 11, 23, 2, 60}};
        double[] ref = null;
        for (long[] ft : frameTimes) {
            Game g = new Game(l);
            FixedStepClock clock = new FixedStepClock(Physics.HZ, 1000);
            Random shotsRnd = new Random(5);
            int frame = 0;
            int shots = 0;
            while (shots < 8 && !g.isFinished()) {
                if (g.state() == GameState.AIMING) {
                    double a = shotsRnd.nextDouble() * 2 * Math.PI;
                    g.shoot(Math.cos(a), Math.sin(a), 0.3 + 0.7 * shotsRnd.nextDouble());
                    shots++;
                }
                int n = clock.advance(ft[frame++ % ft.length]);
                for (int i = 0; i < n; i++) {
                    g.step();
                }
            }
            while (g.state() == GameState.ROLLING) {
                g.step();
            }
            double[] fin = {g.ballX(), g.ballY(), g.strokes(), g.totalSteps()};
            if (ref == null) {
                ref = fin;
            } else {
                for (int k = 0; k < fin.length; k++) {
                    Check.equal(ref[k], fin[k], "Bildzeiten " + java.util.Arrays.toString(ft) + ", Wert " + k);
                }
            }
        }
    }

    public void testFixedStepClockIsExact() {
        FixedStepClock c = new FixedStepClock(120, 1000);
        long steps = 0;
        for (int i = 0; i < 1000; i++) {
            steps += c.advance(16);
        }
        Check.equal(1920L, steps, "16 s bei 120 Hz ergeben 1920 Schritte");
        FixedStepClock d = new FixedStepClock(120, 30);
        Check.equal(30, d.advance(5000), "lange Pause wird gedeckelt");
        Check.equal(1, d.advance(9), "danach normal weiter");
    }
}
