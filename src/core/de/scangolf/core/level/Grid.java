package de.scangolf.core.level;

/**
 * Veränderliches Zellraster (Arbeitsobjekt der Scan-Auswertung). {@link Level} hält eine
 * private Kopie und ist damit unveränderlich.
 *
 * Zelle (cx, cy) deckt [cx*cellMm, (cx+1)*cellMm) x [cy*cellMm, (cy+1)*cellMm) ab.
 * Außerhalb des Rasters gilt immer: Wand.
 */
public final class Grid {

    public final int cols;
    public final int rows;
    public final double cellMm;
    private final byte[] cells;

    public Grid(int cols, int rows, double cellMm) {
        if (cols <= 0 || rows <= 0 || cellMm <= 0) {
            throw new IllegalArgumentException("Rastergröße ungültig");
        }
        this.cols = cols;
        this.rows = rows;
        this.cellMm = cellMm;
        this.cells = new byte[cols * rows];
    }

    public Grid copy() {
        Grid g = new Grid(cols, rows, cellMm);
        System.arraycopy(cells, 0, g.cells, 0, cells.length);
        return g;
    }

    public byte get(int cx, int cy) {
        if (cx < 0 || cy < 0 || cx >= cols || cy >= rows) {
            return CellType.WALL;
        }
        return cells[cy * cols + cx];
    }

    public void set(int cx, int cy, byte t) {
        cells[cy * cols + cx] = t;
    }

    public byte getIndex(int i) {
        return cells[i];
    }

    public void setIndex(int i, byte t) {
        cells[i] = t;
    }

    public int size() {
        return cells.length;
    }

    public double widthMm() {
        return cols * cellMm;
    }

    public double heightMm() {
        return rows * cellMm;
    }

    public int count(byte t) {
        int n = 0;
        for (int i = 0; i < cells.length; i++) {
            if (cells[i] == t) {
                n++;
            }
        }
        return n;
    }

    /** Zelltyp an einer Position in mm (außerhalb: Wand). */
    public byte atMm(double x, double y) {
        if (x < 0 || y < 0) {
            return CellType.WALL;
        }
        return get((int) (x / cellMm), (int) (y / cellMm));
    }

    public boolean sameCells(Grid o) {
        if (o.cols != cols || o.rows != rows || o.cellMm != cellMm) {
            return false;
        }
        for (int i = 0; i < cells.length; i++) {
            if (cells[i] != o.cells[i]) {
                return false;
            }
        }
        return true;
    }

    /** Eine Rasterzeile als Text ('.', '#', 'S', 'H', '~'). */
    public String rowString(int cy) {
        char[] c = new char[cols];
        for (int x = 0; x < cols; x++) {
            c[x] = CellType.toChar(cells[cy * cols + x]);
        }
        return new String(c);
    }
}
