package de.scangolf.core.scan;

import de.scangolf.core.level.CellType;
import de.scangolf.core.level.Grid;

/**
 * Rasteroperationen für das Aufräumen: Zusammenhangskomponenten, Morphologie (Schließen),
 * Füllen eingeschlossener Flächen.
 */
final class GridOps {

    private GridOps() {
    }

    static boolean[] mask(Grid g, byte type) {
        boolean[] m = new boolean[g.size()];
        for (int i = 0; i < m.length; i++) {
            m[i] = g.getIndex(i) == type;
        }
        return m;
    }

    /** Max-Filter mit Quadrat (2r+1)^2; außerhalb gilt "outside". */
    static boolean[] dilate(boolean[] m, int cols, int rows, int r, boolean outside) {
        return filter(m, cols, rows, r, outside, true);
    }

    /** Min-Filter mit Quadrat (2r+1)^2; außerhalb gilt "outside". */
    static boolean[] erode(boolean[] m, int cols, int rows, int r, boolean outside) {
        return filter(m, cols, rows, r, outside, false);
    }

    private static boolean[] filter(boolean[] m, int cols, int rows, int r, boolean outside, boolean max) {
        int win = 2 * r + 1;
        boolean[] tmp = new boolean[m.length];
        boolean[] out = new boolean[m.length];
        for (int y = 0; y < rows; y++) {
            int row = y * cols;
            for (int x = 0; x < cols; x++) {
                int n = 0;
                for (int k = x - r; k <= x + r; k++) {
                    boolean v = (k < 0 || k >= cols) ? outside : m[row + k];
                    if (v) {
                        n++;
                    }
                }
                tmp[row + x] = max ? n > 0 : n == win;
            }
        }
        for (int x = 0; x < cols; x++) {
            for (int y = 0; y < rows; y++) {
                int n = 0;
                for (int k = y - r; k <= y + r; k++) {
                    boolean v = (k < 0 || k >= rows) ? outside : tmp[k * cols + x];
                    if (v) {
                        n++;
                    }
                }
                out[y * cols + x] = max ? n > 0 : n == win;
            }
        }
        return out;
    }

    /**
     * Zusammenhangskomponenten (8er-Nachbarschaft). Rückgabe: Label je Zelle (-1 = nicht in der Maske);
     * sizes[label] enthält die Größe, sizes.length ist die Anzahl.
     */
    static int[] label(boolean[] m, int cols, int rows, int[][] sizesOut) {
        int[] lab = new int[m.length];
        for (int i = 0; i < lab.length; i++) {
            lab[i] = -1;
        }
        int[] queue = new int[m.length];
        int[] sizes = new int[16];
        int n = 0;
        for (int s = 0; s < m.length; s++) {
            if (!m[s] || lab[s] >= 0) {
                continue;
            }
            if (n == sizes.length) {
                int[] ns = new int[sizes.length * 2];
                System.arraycopy(sizes, 0, ns, 0, n);
                sizes = ns;
            }
            int head = 0;
            int tail = 0;
            queue[tail++] = s;
            lab[s] = n;
            while (head < tail) {
                int i = queue[head++];
                int x = i % cols;
                int y = i / cols;
                for (int dy = -1; dy <= 1; dy++) {
                    int ny = y + dy;
                    if (ny < 0 || ny >= rows) {
                        continue;
                    }
                    for (int dx = -1; dx <= 1; dx++) {
                        int nx = x + dx;
                        if (nx < 0 || nx >= cols) {
                            continue;
                        }
                        int j = ny * cols + nx;
                        if (m[j] && lab[j] < 0) {
                            lab[j] = n;
                            queue[tail++] = j;
                        }
                    }
                }
            }
            sizes[n] = tail;
            n++;
        }
        int[] exact = new int[n];
        System.arraycopy(sizes, 0, exact, 0, n);
        sizesOut[0] = exact;
        return lab;
    }

    /** Komponenten eines Typs mit weniger als minCells Zellen auf EMPTY setzen. */
    static void removeSmall(Grid g, byte type, int minCells) {
        boolean[] m = mask(g, type);
        int[][] sz = new int[1][];
        int[] lab = label(m, g.cols, g.rows, sz);
        for (int i = 0; i < lab.length; i++) {
            if (lab[i] >= 0 && sz[0][lab[i]] < minCells) {
                g.setIndex(i, CellType.EMPTY);
            }
        }
    }

    /**
     * Schließen (Dilatation, dann Erosion) für einen Typ; nur leere Zellen werden umgewandelt.
     * Mit borderAsType zählt der Feldrand als dieser Typ (für Wände).
     */
    static int close(Grid g, byte type, int r, boolean borderAsType) {
        boolean[] m = mask(g, type);
        boolean[] closed = erode(dilate(m, g.cols, g.rows, r, borderAsType), g.cols, g.rows, r, true);
        int changed = 0;
        for (int i = 0; i < m.length; i++) {
            if (closed[i] && !m[i] && g.getIndex(i) == CellType.EMPTY) {
                g.setIndex(i, type);
                changed++;
            }
        }
        return changed;
    }

    /**
     * Leere Flächen, die ausschließlich von Wasser umschlossen sind (z. B. nur umrandete
     * Teiche oder Lücken in Schraffuren), werden zu Wasser.
     */
    static int fillEnclosedByWater(Grid g, int maxCells) {
        int cols = g.cols;
        int rows = g.rows;
        boolean[] seen = new boolean[g.size()];
        int[] queue = new int[g.size()];
        int filled = 0;
        for (int s = 0; s < seen.length; s++) {
            if (seen[s] || g.getIndex(s) != CellType.EMPTY) {
                continue;
            }
            int head = 0;
            int tail = 0;
            queue[tail++] = s;
            seen[s] = true;
            boolean onlyWater = true;
            while (head < tail) {
                int i = queue[head++];
                int x = i % cols;
                int y = i / cols;
                for (int k = 0; k < 4; k++) {
                    int nx = x + (k == 0 ? 1 : k == 1 ? -1 : 0);
                    int ny = y + (k == 2 ? 1 : k == 3 ? -1 : 0);
                    if (nx < 0 || ny < 0 || nx >= cols || ny >= rows) {
                        onlyWater = false;
                        continue;
                    }
                    int j = ny * cols + nx;
                    byte t = g.getIndex(j);
                    if (t == CellType.EMPTY) {
                        if (!seen[j]) {
                            seen[j] = true;
                            queue[tail++] = j;
                        }
                    } else if (t != CellType.WATER) {
                        onlyWater = false;
                    }
                }
            }
            if (onlyWater && tail <= maxCells) {
                for (int q = 0; q < tail; q++) {
                    g.setIndex(queue[q], CellType.WATER);
                }
                filled += tail;
            }
        }
        return filled;
    }
}
