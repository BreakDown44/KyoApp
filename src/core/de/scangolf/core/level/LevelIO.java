package de.scangolf.core.level;

import de.scangolf.core.util.ArgbImage;
import de.scangolf.core.util.Base64;
import de.scangolf.core.util.Json;

import java.util.List;

/**
 * Level als JSON lesen und schreiben (ohne Bibliothek).
 *
 * Das Raster steht zeilenweise als Text ('.' leer, '#' Wand, 'S' Start, 'H' Loch, '~' Wasser),
 * damit man Level auch von Hand schreiben kann. Bildausschnitte sind lauflängenkodiert
 * (je Lauf: 1 Byte Länge-1, 4 Byte ARGB) und Base64-kodiert.
 */
public final class LevelIO {

    public static final String FORMAT = "scangolf-level";
    public static final int VERSION = 1;

    private LevelIO() {
    }

    public static String toJson(Level l) {
        StringBuffer sb = new StringBuffer(l.cols() * l.rows() + 4096);
        sb.append("{\n");
        sb.append("  \"format\": \"").append(FORMAT).append("\",\n");
        sb.append("  \"version\": ").append(VERSION).append(",\n");
        sb.append("  \"cols\": ").append(l.cols()).append(",\n");
        sb.append("  \"rows\": ").append(l.rows()).append(",\n");
        sb.append("  \"cellMm\": ").append(Json.number(l.cellMm())).append(",\n");
        sb.append("  \"par\": ").append(l.par()).append(",\n");
        sb.append("  \"start\": [").append(Json.number(l.startX())).append(", ")
                .append(Json.number(l.startY())).append("],\n");
        sb.append("  \"hole\": [").append(Json.number(l.holeX())).append(", ")
                .append(Json.number(l.holeY())).append("],\n");
        sb.append("  \"legend\": \". leer, # Wand, S Start, H Loch, ~ Wasser\",\n");
        sb.append("  \"grid\": [\n");
        Grid g = l.gridView();
        for (int y = 0; y < g.rows; y++) {
            sb.append("    \"").append(g.rowString(y)).append('"');
            sb.append(y + 1 < g.rows ? ",\n" : "\n");
        }
        sb.append("  ],\n");
        sb.append("  \"nameImage\": ");
        appendImage(sb, l.nameImage());
        sb.append(",\n  \"laneImage\": ");
        appendImage(sb, l.laneImage());
        sb.append("\n}\n");
        return sb.toString();
    }

    private static void appendImage(StringBuffer sb, ArgbImage img) {
        if (img == null) {
            sb.append("null");
            return;
        }
        sb.append("{\"w\": ").append(img.width()).append(", \"h\": ").append(img.height())
                .append(", \"rle\": \"").append(encodeRle(img)).append("\"}");
    }

    static String encodeRle(ArgbImage img) {
        int n = img.width() * img.height();
        int[] px = new int[n];
        img.copyPixelsTo(px, 0);
        byte[] buf = new byte[n * 5];
        int o = 0;
        int i = 0;
        while (i < n) {
            int v = px[i];
            int run = 1;
            while (i + run < n && run < 256 && px[i + run] == v) {
                run++;
            }
            buf[o++] = (byte) (run - 1);
            buf[o++] = (byte) (v >>> 24);
            buf[o++] = (byte) (v >>> 16);
            buf[o++] = (byte) (v >>> 8);
            buf[o++] = (byte) v;
            i += run;
        }
        return Base64.encode(buf, 0, o);
    }

    static ArgbImage decodeRle(int w, int h, String data) {
        byte[] b = Base64.decode(data);
        int[] px = new int[w * h];
        int o = 0;
        int i = 0;
        while (i + 4 < b.length) {
            int run = (b[i] & 0xFF) + 1;
            int v = ((b[i + 1] & 0xFF) << 24) | ((b[i + 2] & 0xFF) << 16) | ((b[i + 3] & 0xFF) << 8) | (b[i + 4] & 0xFF);
            i += 5;
            if (o + run > px.length) {
                throw new IllegalArgumentException("Bilddaten zu lang");
            }
            for (int k = 0; k < run; k++) {
                px[o++] = v;
            }
        }
        if (o != px.length) {
            throw new IllegalArgumentException("Bilddaten unvollständig: " + o + " von " + px.length);
        }
        return ArgbImage.adopt(w, h, px);
    }

    /** Liest ein Level. Wirft IllegalArgumentException mit verständlicher Meldung. */
    public static Level fromJson(String text) {
        Object root = Json.parse(text);
        if (Json.has(root, "format") && !FORMAT.equals(Json.str(root, "format"))) {
            throw new IllegalArgumentException("Keine ScanGolf-Leveldatei");
        }
        int version = (int) Json.num(root, "version", VERSION);
        if (version != VERSION) {
            throw new IllegalArgumentException("Level-Version " + version + " wird nicht unterstützt");
        }
        List<Object> rowsList = Json.list(root, "grid");
        int rows = rowsList.size();
        if (rows == 0) {
            throw new IllegalArgumentException("Raster ist leer");
        }
        int cols = ((String) rowsList.get(0)).length();
        if (Json.has(root, "cols") && (int) Json.num(root, "cols") != cols) {
            throw new IllegalArgumentException("cols passt nicht zum Raster");
        }
        if (Json.has(root, "rows") && (int) Json.num(root, "rows") != rows) {
            throw new IllegalArgumentException("rows passt nicht zum Raster");
        }
        Grid g = new Grid(cols, rows, Json.num(root, "cellMm", 1.0));
        for (int y = 0; y < rows; y++) {
            String line = (String) rowsList.get(y);
            if (line.length() != cols) {
                throw new IllegalArgumentException("Rasterzeile " + y + " hat die falsche Länge");
            }
            for (int x = 0; x < cols; x++) {
                g.set(x, y, CellType.fromChar(line.charAt(x)));
            }
        }
        double[] start = point(root, "start", g, CellType.START);
        double[] hole = point(root, "hole", g, CellType.HOLE);
        int par = (int) Json.num(root, "par", Rules.DEFAULT_PAR);
        return new Level(g, start[0], start[1], hole[0], hole[1], par,
                image(root, "nameImage"), image(root, "laneImage"));
    }

    /** Punkt aus JSON, sonst Schwerpunkt der Zellen des Typs (praktisch für handgeschriebene Level). */
    private static double[] point(Object root, String key, Grid g, byte type) {
        if (Json.has(root, key)) {
            List<Object> p = Json.list(root, key);
            if (p.size() != 2) {
                throw new IllegalArgumentException(key + " braucht zwei Koordinaten");
            }
            return new double[] {((Double) p.get(0)).doubleValue(), ((Double) p.get(1)).doubleValue()};
        }
        double sx = 0;
        double sy = 0;
        int n = 0;
        for (int y = 0; y < g.rows; y++) {
            for (int x = 0; x < g.cols; x++) {
                if (g.get(x, y) == type) {
                    sx += x + 0.5;
                    sy += y + 0.5;
                    n++;
                }
            }
        }
        if (n == 0) {
            throw new IllegalArgumentException("Weder \"" + key + "\" noch passende Zellen im Raster");
        }
        return new double[] {sx / n * g.cellMm, sy / n * g.cellMm};
    }

    private static ArgbImage image(Object root, String key) {
        if (!Json.has(root, key)) {
            return null;
        }
        Object o = Json.obj(root, key);
        return decodeRle((int) Json.num(o, "w"), (int) Json.num(o, "h"), Json.str(o, "rle"));
    }
}
