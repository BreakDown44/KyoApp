package de.scangolf.test;

import de.scangolf.core.game.Game;
import de.scangolf.core.game.GameState;
import de.scangolf.core.game.Physics;
import de.scangolf.core.level.CellType;
import de.scangolf.core.level.Grid;
import de.scangolf.core.level.Level;
import de.scangolf.core.level.LevelGeometry;
import de.scangolf.core.level.Rules;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Physik-Fuzzing: viele zufällige Schläge (fester Seed) auf mehreren Leveln. Nach jedem
 * Unterschritt wird geprüft: Kugel im Feld, nicht in einer Wand, kein Sprung (kein Tunneln),
 * bleibt im selben freien Bereich; jeder Schlag endet von selbst.
 */
public class PhysicsFuzzTest implements CaseProvider {

    static final int SHOTS_PER_LEVEL = 2200;
    static final double R = Rules.BALL_RADIUS_MM;

    @Override
    public List<TestCase> cases() {
        List<TestCase> c = new ArrayList<>();
        String[] scanned = {"beispiel", "schraeg", "gewunden", "wasser", "duenn"};
        for (String s : scanned) {
            c.add(new TestCase("fuzz " + s, () -> fuzz(s, TestLevels.load(s), s.hashCode())));
        }
        c.add(new TestCase("fuzz torture", () -> fuzz("torture", TestLevels.torture(), 11)));
        c.add(new TestCase("fuzz maze", () -> fuzz("maze", TestLevels.maze(), 12)));
        return c;
    }

    /** Freie Bereiche (Zellmitten mit Abstand >= R-0,8 zur Wand), 8er-Nachbarschaft. */
    static int[] regions(Grid g) {
        float[] clear = LevelGeometry.clearanceMap(g, R + 1);
        boolean[] free = new boolean[g.cols * g.rows];
        for (int i = 0; i < free.length; i++) {
            free[i] = clear[i] >= R - 0.8;
        }
        int[] lab = new int[free.length];
        java.util.Arrays.fill(lab, -1);
        int n = 0;
        int[] q = new int[free.length];
        for (int s = 0; s < free.length; s++) {
            if (!free[s] || lab[s] >= 0) {
                continue;
            }
            int h = 0;
            int t = 0;
            q[t++] = s;
            lab[s] = n;
            while (h < t) {
                int i = q[h++];
                int x = i % g.cols;
                int y = i / g.cols;
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dx = -1; dx <= 1; dx++) {
                        int nx = x + dx;
                        int ny = y + dy;
                        if (nx < 0 || ny < 0 || nx >= g.cols || ny >= g.rows) {
                            continue;
                        }
                        int j = ny * g.cols + nx;
                        if (free[j] && lab[j] < 0) {
                            lab[j] = n;
                            q[t++] = j;
                        }
                    }
                }
            }
            n++;
        }
        return lab;
    }

    static int regionAt(int[] lab, Grid g, double x, double y) {
        int cx = (int) x;
        int cy = (int) y;
        int best = -1;
        double bestD = Double.MAX_VALUE;
        for (int dy = -1; dy <= 1; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                int nx = cx + dx;
                int ny = cy + dy;
                if (nx < 0 || ny < 0 || nx >= g.cols || ny >= g.rows || lab[ny * g.cols + nx] < 0) {
                    continue;
                }
                double d = Math.hypot(nx + 0.5 - x, ny + 0.5 - y);
                if (d < bestD) {
                    bestD = d;
                    best = lab[ny * g.cols + nx];
                }
            }
        }
        return best;
    }

    private static final class Monitor {
        final Grid g;
        final int[] lab;
        double lastX;
        double lastY;
        int region;
        long substeps;
        double minClear = Double.MAX_VALUE;
        double maxJump;
        String violation;

        Monitor(Grid g) {
            this.g = g;
            this.lab = regions(g);
        }

        void startShot(double x, double y) {
            lastX = x;
            lastY = y;
            region = regionAt(lab, g, x, y);
            if (region < 0 && violation == null) {
                violation = String.format("kein freier Bereich an Startposition %.2f/%.2f", x, y);
            }
        }

        void substep(double x, double y) {
            substeps++;
            double clear = LevelGeometry.wallDistance(g, x, y, R + 1);
            minClear = Math.min(minClear, clear);
            double jump = Math.hypot(x - lastX, y - lastY);
            maxJump = Math.max(maxJump, jump);
            if (violation == null) {
                if (x < R - 1e-4 || y < R - 1e-4 || x > g.widthMm() - R + 1e-4 || y > g.heightMm() - R + 1e-4) {
                    violation = String.format("Kugel außerhalb des Feldes bei %.3f/%.3f", x, y);
                } else if (clear < R - 1e-4) {
                    violation = String.format("Kugel steckt in der Wand bei %.3f/%.3f (Abstand %.4f)", x, y, clear);
                } else if (jump > 0.6) {
                    violation = String.format("Sprung um %.3f mm bei %.2f/%.2f", jump, x, y);
                } else if (g.atMm(x, y) != CellType.WATER) {
                    int reg = regionAt(lab, g, x, y);
                    if (reg != region) {
                        violation = String.format("Bereich gewechselt (%d -> %d) bei %.2f/%.2f – durch Wand getunnelt",
                                region, reg, x, y);
                    }
                }
            }
            lastX = x;
            lastY = y;
        }
    }

    private void fuzz(String name, Level base, long seed) {
        Random rnd = new Random(seed);
        Grid grid = base.gridCopy();
        Monitor mon = new Monitor(grid);
        int shots = 0;
        int holed = 0;
        int water = 0;
        int bounces = 0;
        long steps = 0;
        int maxSteps = 0;
        Game game = new Game(base);
        game.setSubstepListener(mon::substep);
        while (shots < SHOTS_PER_LEVEL) {
            if (game.isFinished() || rnd.nextInt(40) == 0) {
                // neue Runde, oft mit zufälliger gültiger Startposition
                Level l = base;
                if (rnd.nextBoolean()) {
                    for (int tries = 0; tries < 1000; tries++) {
                        double x = R + rnd.nextDouble() * (grid.widthMm() - 2 * R);
                        double y = R + rnd.nextDouble() * (grid.heightMm() - 2 * R);
                        if (LevelGeometry.ballFits(grid, x, y, R)
                                && Math.hypot(x - base.holeX(), y - base.holeY()) > Rules.HOLE_RADIUS_MM) {
                            l = new Level(grid, x, y, base.holeX(), base.holeY(), base.par(), null, null);
                            break;
                        }
                    }
                }
                game = new Game(l);
                game.setSubstepListener(mon::substep);
            }
            double ang = rnd.nextDouble() * 2 * Math.PI;
            double power = 0.05 + rnd.nextDouble() * 0.95;
            mon.startShot(game.ballX(), game.ballY());
            Check.isTrue(game.shoot(Math.cos(ang), Math.sin(ang), power), name + ": Schlag abgelehnt");
            shots++;
            int n = 0;
            while (game.state() == GameState.ROLLING) {
                game.step();
                n++;
                if (n > Physics.MAX_ROLL_STEPS + 5) {
                    throw Check.fail(name + ": Schlag " + shots + " endet nicht");
                }
            }
            int ev = game.pollEvents();
            if ((ev & Physics.EV_WALL) != 0) {
                bounces++;
            }
            if ((ev & Physics.EV_WATER) != 0) {
                water++;
            }
            if (game.state() == GameState.HOLED) {
                holed++;
            }
            Check.isFalse(game.hadForcedStop(), name + ": Schlag " + shots + " musste zwangsgestoppt werden (kam nicht zur Ruhe)");
            steps += n;
            maxSteps = Math.max(maxSteps, n);
            if (mon.violation != null) {
                throw Check.fail(name + ", Schlag " + shots + " (Winkel " + Math.round(Math.toDegrees(ang))
                        + "°, Stärke " + Math.round(power * 100) + "%): " + mon.violation);
            }
        }
        Check.info(String.format("%s: %d Schläge, %d Unterschritte, %d mit Wandkontakt, %d eingelocht, %d im Wasser, "
                        + "längster Schlag %.1f s, min. Wandabstand %.4f mm (Radius %.1f), größter Unterschritt %.3f mm",
                name, shots, mon.substeps, bounces, holed, water, maxSteps / (double) Physics.HZ, mon.minClear, R,
                mon.maxJump));
        Check.isTrue(bounces > shots / 4, name + ": zu wenige Wandkontakte, Fuzzing wäre wertlos");
    }

    /** Summe über alle Level muss mindestens 10 000 Schläge sein. */
    public void testShotCountAtLeast10000() {
        Check.isTrue(SHOTS_PER_LEVEL * 7 >= 10000, "mindestens 10 000 Schläge insgesamt");
    }
}
