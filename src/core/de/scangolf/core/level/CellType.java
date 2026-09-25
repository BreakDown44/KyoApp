package de.scangolf.core.level;

/** Zelltypen des Levelrasters (als byte gespeichert, damit das Raster klein bleibt). */
public final class CellType {

    public static final byte EMPTY = 0;
    public static final byte WALL = 1;
    public static final byte START = 2;
    public static final byte HOLE = 3;
    public static final byte WATER = 4;

    private static final String CHARS = ".#SH~";

    private CellType() {
    }

    public static char toChar(byte t) {
        if (t < 0 || t >= CHARS.length()) {
            throw new IllegalArgumentException("Unbekannter Zelltyp " + t);
        }
        return CHARS.charAt(t);
    }

    public static byte fromChar(char c) {
        int i = CHARS.indexOf(c);
        if (i < 0) {
            throw new IllegalArgumentException("Unbekanntes Rasterzeichen '" + c + "'");
        }
        return (byte) i;
    }

    public static String name(byte t) {
        switch (t) {
            case EMPTY: return "EMPTY";
            case WALL: return "WALL";
            case START: return "START";
            case HOLE: return "HOLE";
            case WATER: return "WATER";
            default: return "?" + t;
        }
    }
}
