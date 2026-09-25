package de.scangolf.core.level;

import de.scangolf.core.util.ArgbImage;

/**
 * Unveränderliches Level: Raster, Start, Loch, Par, Maße und optional die eingescannten
 * Bildausschnitte von Name und Bahnname. Koordinaten in mm, Ursprung oben links im Feld.
 */
public final class Level {

    private final Grid grid;
    private final double startX;
    private final double startY;
    private final double holeX;
    private final double holeY;
    private final int par;
    private final ArgbImage nameImage;
    private final ArgbImage laneImage;

    public Level(Grid grid, double startX, double startY, double holeX, double holeY, int par,
                 ArgbImage nameImage, ArgbImage laneImage) {
        if (par < 1 || par > 20) {
            throw new IllegalArgumentException("Par ungültig: " + par);
        }
        checkInside(grid, startX, startY, "Start");
        checkInside(grid, holeX, holeY, "Loch");
        this.grid = grid.copy();
        this.startX = startX;
        this.startY = startY;
        this.holeX = holeX;
        this.holeY = holeY;
        this.par = par;
        this.nameImage = nameImage;
        this.laneImage = laneImage;
    }

    private static void checkInside(Grid g, double x, double y, String what) {
        if (!(x >= 0 && y >= 0 && x <= g.widthMm() && y <= g.heightMm())) {
            throw new IllegalArgumentException(what + " liegt außerhalb des Feldes: " + x + "/" + y);
        }
    }

    public int cols() {
        return grid.cols;
    }

    public int rows() {
        return grid.rows;
    }

    public double cellMm() {
        return grid.cellMm;
    }

    public double widthMm() {
        return grid.widthMm();
    }

    public double heightMm() {
        return grid.heightMm();
    }

    /** Zelltyp; außerhalb des Rasters immer WALL. */
    public byte cell(int cx, int cy) {
        return grid.get(cx, cy);
    }

    public byte cellAtMm(double x, double y) {
        return grid.atMm(x, y);
    }

    public boolean isWall(int cx, int cy) {
        return grid.get(cx, cy) == CellType.WALL;
    }

    public int count(byte type) {
        return grid.count(type);
    }

    /** Kopie des Rasters (z. B. für Geometrie-Abfragen oder Tests). */
    public Grid gridCopy() {
        return grid.copy();
    }

    /** Nur lesend verwenden – interne Abkürzung für Geometrie-Berechnungen im Kern. */
    Grid gridView() {
        return grid;
    }

    public double wallDistance(double x, double y, double maxDist) {
        return LevelGeometry.wallDistance(grid, x, y, maxDist);
    }

    public double startX() {
        return startX;
    }

    public double startY() {
        return startY;
    }

    public double holeX() {
        return holeX;
    }

    public double holeY() {
        return holeY;
    }

    public int par() {
        return par;
    }

    /** Eingescannter Name (Tinte mit Alpha), oder null. */
    public ArgbImage nameImage() {
        return nameImage;
    }

    /** Eingescannter Bahnname (Tinte mit Alpha), oder null. */
    public ArgbImage laneImage() {
        return laneImage;
    }

    public Level withPar(int newPar) {
        return new Level(grid, startX, startY, holeX, holeY, newPar, nameImage, laneImage);
    }

    public boolean equals(Object o) {
        if (!(o instanceof Level)) {
            return false;
        }
        Level l = (Level) o;
        return grid.sameCells(l.grid) && startX == l.startX && startY == l.startY
                && holeX == l.holeX && holeY == l.holeY && par == l.par
                && sameImage(nameImage, l.nameImage) && sameImage(laneImage, l.laneImage);
    }

    private static boolean sameImage(ArgbImage a, ArgbImage b) {
        return a == null ? b == null : a.sameContent(b);
    }

    public int hashCode() {
        long h = Double.doubleToLongBits(startX) * 31 + Double.doubleToLongBits(holeY);
        return (int) (h ^ (h >>> 32)) * 31 + par * 7 + grid.count(CellType.WALL);
    }
}
