package de.scangolf.core.level;

/**
 * Geometrische Abfragen auf dem Zellraster: Abstand zur nächsten Wand (Wandzellen als
 * Quadrate plus Feldrand), Kugelfreiheit, Erreichbarkeit per Flood-Fill.
 */
public final class LevelGeometry {

    private LevelGeometry() {
    }

    /**
     * Euklidischer Abstand von (x, y) zur nächsten Wandzelle oder zum Feldrand, höchstens maxDist.
     * Liegt der Punkt in einer Wandzelle, ist das Ergebnis 0.
     */
    public static double wallDistance(Grid g, double x, double y, double maxDist) {
        double best = maxDist;
        double w = g.widthMm();
        double h = g.heightMm();
        if (x < best) {
            best = Math.max(0, x);
        }
        if (y < best) {
            best = Math.max(0, y);
        }
        if (w - x < best) {
            best = Math.max(0, w - x);
        }
        if (h - y < best) {
            best = Math.max(0, h - y);
        }
        double c = g.cellMm;
        int x0 = (int) Math.floor((x - best) / c);
        int x1 = (int) Math.floor((x + best) / c);
        int y0 = (int) Math.floor((y - best) / c);
        int y1 = (int) Math.floor((y + best) / c);
        if (x0 < 0) {
            x0 = 0;
        }
        if (y0 < 0) {
            y0 = 0;
        }
        if (x1 >= g.cols) {
            x1 = g.cols - 1;
        }
        if (y1 >= g.rows) {
            y1 = g.rows - 1;
        }
        double best2 = best * best;
        for (int cy = y0; cy <= y1; cy++) {
            double sy0 = cy * c;
            double dy = y < sy0 ? sy0 - y : (y > sy0 + c ? y - sy0 - c : 0);
            double dy2 = dy * dy;
            if (dy2 >= best2) {
                continue;
            }
            for (int cx = x0; cx <= x1; cx++) {
                if (g.get(cx, cy) != CellType.WALL) {
                    continue;
                }
                double sx0 = cx * c;
                double dx = x < sx0 ? sx0 - x : (x > sx0 + c ? x - sx0 - c : 0);
                double d2 = dx * dx + dy2;
                if (d2 < best2) {
                    best2 = d2;
                }
            }
        }
        return Math.sqrt(best2);
    }

    /** Passt eine Kugel mit Radius r an (x, y), ohne Wand und Rand zu schneiden, und liegt ihr Mittelpunkt nicht im Wasser? */
    public static boolean ballFits(Grid g, double x, double y, double r) {
        if (g.atMm(x, y) == CellType.WATER) {
            return false;
        }
        return wallDistance(g, x, y, r + 0.01) >= r;
    }

    /**
     * Nächste Position im Umkreis searchR um (x, y), an der die Kugel passt (Raster 0,25 mm).
     * Rückgabe {x, y} oder null.
     */
    public static double[] nearestFit(Grid g, double x, double y, double r, double searchR) {
        if (ballFits(g, x, y, r)) {
            return new double[] {x, y};
        }
        double step = 0.25;
        int n = (int) Math.ceil(searchR / step);
        double bestD = Double.MAX_VALUE;
        double bx = 0;
        double by = 0;
        for (int j = -n; j <= n; j++) {
            for (int i = -n; i <= n; i++) {
                double d = Math.sqrt((double) (i * i + j * j)) * step;
                if (d > searchR || d >= bestD) {
                    continue;
                }
                double px = x + i * step;
                double py = y + j * step;
                if (ballFits(g, px, py, r)) {
                    bestD = d;
                    bx = px;
                    by = py;
                }
            }
        }
        return bestD == Double.MAX_VALUE ? null : new double[] {bx, by};
    }

    /**
     * Freiheit (Abstand zur Wand) jeder Zellmitte, gedeckelt bei maxDist.
     */
    public static float[] clearanceMap(Grid g, double maxDist) {
        float[] m = new float[g.cols * g.rows];
        double c = g.cellMm;
        for (int cy = 0; cy < g.rows; cy++) {
            for (int cx = 0; cx < g.cols; cx++) {
                m[cy * g.cols + cx] = (float) wallDistance(g, (cx + 0.5) * c, (cy + 0.5) * c, maxDist);
            }
        }
        return m;
    }

    /**
     * Kann eine Kugel mit Radius ballR vom Start aus das Loch erreichen? Flood-Fill über
     * Zellmitten mit ausreichend Wandabstand, Wasser ist gesperrt. Ziel erreicht, sobald eine
     * erreichte Zellmitte höchstens captureR vom Loch entfernt ist.
     */
    public static boolean reachable(Grid g, double sx, double sy, double hx, double hy,
                                    double ballR, double captureR) {
        double c = g.cellMm;
        double need = ballR - Rules.REACH_SLACK_MM;
        float[] clear = clearanceMap(g, ballR + 1.0);
        int cols = g.cols;
        int rows = g.rows;
        boolean[] seen = new boolean[cols * rows];
        int[] queue = new int[cols * rows];
        int head = 0;
        int tail = 0;
        int scx = (int) (sx / c);
        int scy = (int) (sy / c);
        // Startzelle: die Zelle des Starts oder eine direkte Nachbarzelle, die passt.
        for (int dy = -1; dy <= 1 && tail == 0; dy++) {
            for (int dx = -1; dx <= 1 && tail == 0; dx++) {
                int x = scx + dx;
                int y = scy + dy;
                if (x < 0 || y < 0 || x >= cols || y >= rows) {
                    continue;
                }
                int i = y * cols + x;
                if (clear[i] >= need && g.getIndex(i) != CellType.WATER) {
                    seen[i] = true;
                    queue[tail++] = i;
                }
            }
        }
        double cap2 = captureR * captureR;
        while (head < tail) {
            int i = queue[head++];
            int x = i % cols;
            int y = i / cols;
            double mx = (x + 0.5) * c - hx;
            double my = (y + 0.5) * c - hy;
            if (mx * mx + my * my <= cap2) {
                return true;
            }
            for (int k = 0; k < 4; k++) {
                int nx = x + (k == 0 ? 1 : k == 1 ? -1 : 0);
                int ny = y + (k == 2 ? 1 : k == 3 ? -1 : 0);
                if (nx < 0 || ny < 0 || nx >= cols || ny >= rows) {
                    continue;
                }
                int j = ny * cols + nx;
                if (!seen[j] && clear[j] >= need && g.getIndex(j) != CellType.WATER) {
                    seen[j] = true;
                    queue[tail++] = j;
                }
            }
        }
        return false;
    }
}
