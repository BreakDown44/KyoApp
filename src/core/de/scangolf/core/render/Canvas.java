package de.scangolf.core.render;

import de.scangolf.core.util.ArgbImage;

/**
 * Minimale Zeichenfläche, über die Spiel, Urkunde und Debug-Bild zeichnen.
 * Plattform-Adapter (PC: Graphics2D, später HyPAS) setzen sie um.
 *
 * Koordinaten sind logische Einheiten (Spiel: Pixel der logischen Auflösung, Urkunde: mm).
 * Farben sind ARGB-int. Linien haben runde Enden.
 */
public interface Canvas {

    int ALIGN_LEFT = 0;
    int ALIGN_CENTER = 1;
    int ALIGN_RIGHT = 2;

    /** Schriftart-Bits für {@link #text}. */
    int FONT_SANS = 0;
    int FONT_SERIF = 1;
    int FONT_BOLD = 2;
    int FONT_ITALIC = 4;

    /** Breite in logischen Einheiten. */
    double width();

    /** Höhe in logischen Einheiten. */
    double height();

    /** Gerätepixel je logischer Einheit (für die Wahl von Bildauflösungen). */
    double deviceScale();

    void fillRect(double x, double y, double w, double h, int argb);

    void fillRoundRect(double x, double y, double w, double h, double radius, int argb);

    void strokeRoundRect(double x, double y, double w, double h, double radius, double lineWidth, int argb);

    void fillCircle(double cx, double cy, double r, int argb);

    void strokeCircle(double cx, double cy, double r, double lineWidth, int argb);

    void fillEllipse(double cx, double cy, double rx, double ry, int argb);

    void line(double x1, double y1, double x2, double y2, double lineWidth, int argb);

    void polyline(double[] xs, double[] ys, int n, double lineWidth, int argb);

    void fillPolygon(double[] xs, double[] ys, int n, int argb);

    /** Text mit Grundlinie bei y; size ist die Schriftgröße (Em) in logischen Einheiten. */
    void text(String s, double x, double y, double size, int font, int align, int argb);

    double textWidth(String s, double size, int font);

    /** Bild in das Rechteck skalieren (mit Alpha). */
    void image(ArgbImage img, double x, double y, double w, double h);

    void save();

    void restore();

    void translate(double dx, double dy);

    void scale(double sx, double sy);

    void clipRect(double x, double y, double w, double h);
}
