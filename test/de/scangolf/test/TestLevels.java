package de.scangolf.test;

import de.scangolf.core.level.CellType;
import de.scangolf.core.level.Grid;
import de.scangolf.core.level.Level;
import de.scangolf.core.level.LevelIO;

import java.util.function.BiPredicate;

/** Feste Level für Physik- und Spieltests (synthetisch oder aus test/levels). */
final class TestLevels {

    static final int COLS = 240;
    static final int ROWS = 144;

    private TestLevels() {
    }

    static Level load(String name) {
        return LevelIO.fromJson(Check.readText(Check.root().resolve("test/levels/" + name + ".json")));
    }

    /** Zellen, deren Mittelpunkt das Prädikat erfüllt, bekommen den Typ. */
    static void paint(Grid g, byte type, BiPredicate<Double, Double> p) {
        for (int y = 0; y < g.rows; y++) {
            for (int x = 0; x < g.cols; x++) {
                if (p.test(x + 0.5, y + 0.5)) {
                    g.set(x, y, type);
                }
            }
        }
    }

    /** 1 Zelle breite, nur 8er-verbundene Linie (Bresenham) – die tunnelanfälligste Wandform. */
    static void thinLine(Grid g, int x0, int y0, int x1, int y1) {
        int dx = Math.abs(x1 - x0);
        int dy = -Math.abs(y1 - y0);
        int sx = x0 < x1 ? 1 : -1;
        int sy = y0 < y1 ? 1 : -1;
        int err = dx + dy;
        while (true) {
            if (x0 >= 0 && y0 >= 0 && x0 < g.cols && y0 < g.rows) {
                g.set(x0, y0, CellType.WALL);
            }
            if (x0 == x1 && y0 == y1) {
                break;
            }
            int e2 = 2 * err;
            if (e2 >= dy) {
                err += dy;
                x0 += sx;
            }
            if (e2 <= dx) {
                err += dx;
                y0 += sy;
            }
        }
    }

    static Level open(double sx, double sy, double hx, double hy) {
        return new Level(new Grid(COLS, ROWS, 1.0), sx, sy, hx, hy, 3, null, null);
    }

    /** Senkrechte Wand bei x = 150..151 über die ganze Höhe. */
    static Level verticalWall() {
        Grid g = new Grid(COLS, ROWS, 1.0);
        paint(g, CellType.WALL, (x, y) -> x >= 150 && x < 152);
        return new Level(g, 60, 100, 20, 20, 3, null, null);
    }

    /** 45°-Wand entlang x + y = 200 (Treppe, ca. 2 Zellen dick). */
    static Level diagonal45() {
        Grid g = new Grid(COLS, ROWS, 1.0);
        paint(g, CellType.WALL, (x, y) -> Math.abs(x + y - 200) <= 1.0);
        return new Level(g, 60, 80, 20, 20, 3, null, null);
    }

    /** Wassergraben quer über das Feld bei x = 120..140. */
    static Level waterStrip() {
        Grid g = new Grid(COLS, ROWS, 1.0);
        paint(g, CellType.WATER, (x, y) -> x >= 120 && x < 140);
        return new Level(g, 60, 72, 200, 72, 3, null, null);
    }

    /**
     * Viele dünne, schräge und spitzwinklige Wände: 1-Zellen-Treppen, Zickzack, enge Keile,
     * kleine Taschen – für das Fuzzing.
     */
    static Level torture() {
        Grid g = new Grid(COLS, ROWS, 1.0);
        thinLine(g, 20, 10, 90, 60);
        thinLine(g, 20, 130, 110, 75);
        thinLine(g, 100, 5, 130, 70);
        thinLine(g, 130, 70, 160, 5);        // spitzer Keil (V) mit ~50°
        thinLine(g, 150, 140, 190, 90);
        thinLine(g, 190, 90, 200, 140);      // sehr spitzer Keil (~15°)
        thinLine(g, 200, 20, 235, 60);
        thinLine(g, 235, 60, 205, 75);
        // Zickzack
        for (int i = 0; i < 6; i++) {
            thinLine(g, 60 + i * 8, 100 + (i % 2) * 12, 68 + i * 8, 100 + ((i + 1) % 2) * 12);
        }
        // kleine Tasche (U-Form, knapp breiter als die Kugel)
        thinLine(g, 170, 30, 170, 55);
        thinLine(g, 178, 30, 178, 55);
        thinLine(g, 170, 55, 178, 55);
        // einzelne Pfosten
        g.set(120, 110, CellType.WALL);
        g.set(40, 72, CellType.WALL);
        paint(g, CellType.WATER, (x, y) -> (x - 110) * (x - 110) + (y - 125) * (y - 125) < 64);
        return new Level(g, 12, 72, 225, 120, 4, null, null);
    }

    /** Labyrinth aus 1 Zelle breiten Wänden mit Durchgängen. */
    static Level maze() {
        Grid g = new Grid(COLS, ROWS, 1.0);
        for (int i = 1; i < 8; i++) {
            int x = i * 30;
            int gapStart = (i % 2 == 0) ? 10 : 110;
            for (int y = 0; y < ROWS; y++) {
                if (y < gapStart || y > gapStart + 20) {
                    g.set(x, y, CellType.WALL);
                }
            }
        }
        for (int i = 1; i < 5; i++) {
            thinLine(g, 5, i * 30, 25, i * 30 + 5);
        }
        return new Level(g, 15, 15, 225, 130, 5, null, null);
    }
}
