package de.scangolf.core.scan;

import de.scangolf.core.util.Json;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.List;

/**
 * Geometrie der Druckvorlage in mm (Seite, Passmarken, Spielfeld, Namens-/Bahnfeld,
 * Par-Kästchen). Einzige Quelle ist vorlage/out/template.json, erzeugt von generate.py.
 */
public final class SheetTemplate {

    /** Rechteck in Seiten-mm mit Innenrand, der beim Auswerten ignoriert wird. */
    public static final class Box {
        public final double x;
        public final double y;
        public final double w;
        public final double h;
        public final double inset;
        /** Nur bei Par-Kästchen gesetzt. */
        public final int par;

        Box(double x, double y, double w, double h, double inset, int par) {
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
            this.inset = inset;
            this.par = par;
        }
    }

    public final double pageW;
    public final double pageH;
    public final double module;
    /** Mittelpunkte der drei Suchmuster (tl, tr, bl) und des Blocks (br), Seiten-mm. */
    public final double tlX;
    public final double tlY;
    public final double trX;
    public final double trY;
    public final double blX;
    public final double blY;
    public final double brX;
    public final double brY;
    public final double markSize;
    public final double blockSize;
    public final Box field;
    public final Box nameBox;
    public final Box laneBox;
    public final Box[] parBoxes;
    public final int parDefault;
    /** Farbkontrollfeld (gedruckt farbig): Mittelpunkt und Abtastradius in mm; NaN, wenn nicht vorhanden. */
    public final double colorRefX;
    public final double colorRefY;
    public final double colorRefR;

    private SheetTemplate(Object root) {
        Object page = Json.obj(root, "page");
        pageW = Json.num(page, "w");
        pageH = Json.num(page, "h");
        Object marks = Json.obj(root, "marks");
        module = Json.num(marks, "module");
        Object tl = Json.obj(marks, "tl");
        Object tr = Json.obj(marks, "tr");
        Object bl = Json.obj(marks, "bl");
        Object br = Json.obj(marks, "br");
        tlX = Json.num(tl, "cx");
        tlY = Json.num(tl, "cy");
        trX = Json.num(tr, "cx");
        trY = Json.num(tr, "cy");
        blX = Json.num(bl, "cx");
        blY = Json.num(bl, "cy");
        brX = Json.num(br, "cx");
        brY = Json.num(br, "cy");
        markSize = Json.num(tl, "size");
        blockSize = Json.num(br, "size");
        Object f = Json.obj(root, "field");
        field = new Box(Json.num(f, "x"), Json.num(f, "y"), Json.num(f, "w"), Json.num(f, "h"), 0, 0);
        nameBox = box(Json.obj(root, "name_box"));
        laneBox = box(Json.obj(root, "lane_box"));
        List<Object> pb = Json.list(root, "par_boxes");
        parBoxes = new Box[pb.size()];
        for (int i = 0; i < parBoxes.length; i++) {
            Object b = pb.get(i);
            parBoxes[i] = new Box(Json.num(b, "x"), Json.num(b, "y"), Json.num(b, "w"), Json.num(b, "h"),
                    Json.num(b, "inset", 1.0), (int) Json.num(b, "par"));
        }
        parDefault = (int) Json.num(root, "par_default", 3);
        if (Json.has(root, "color_ref")) {
            Object cr = Json.obj(root, "color_ref");
            colorRefX = Json.num(cr, "cx");
            colorRefY = Json.num(cr, "cy");
            colorRefR = Json.num(cr, "sample_r", Json.num(cr, "r") * 0.8);
        } else {
            colorRefX = Double.NaN;
            colorRefY = Double.NaN;
            colorRefR = Double.NaN;
        }
    }

    private static Box box(Object b) {
        return new Box(Json.num(b, "x"), Json.num(b, "y"), Json.num(b, "w"), Json.num(b, "h"),
                Json.num(b, "inset", 1.0), 0);
    }

    public static SheetTemplate parse(String json) {
        return new SheetTemplate(Json.parse(json));
    }

    /** Lädt die mitgelieferte template.json aus dem Klassenpfad (vom Build in den Kern kopiert). */
    public static SheetTemplate loadDefault() {
        InputStream in = SheetTemplate.class.getResourceAsStream("template.json");
        if (in == null) {
            throw new IllegalStateException("template.json fehlt im Klassenpfad");
        }
        try {
            try {
                Reader r = new InputStreamReader(in, "UTF-8");
                StringBuffer sb = new StringBuffer();
                char[] buf = new char[2048];
                int n;
                while ((n = r.read(buf)) > 0) {
                    sb.append(buf, 0, n);
                }
                return parse(sb.toString());
            } finally {
                in.close();
            }
        } catch (IOException e) {
            throw new IllegalStateException("template.json nicht lesbar: " + e.getMessage());
        }
    }

    public double tlToTr() {
        return dist(tlX, tlY, trX, trY);
    }

    public double tlToBl() {
        return dist(tlX, tlY, blX, blY);
    }

    public double trToBl() {
        return dist(trX, trY, blX, blY);
    }

    static double dist(double ax, double ay, double bx, double by) {
        double dx = bx - ax;
        double dy = by - ay;
        return Math.sqrt(dx * dx + dy * dy);
    }
}
