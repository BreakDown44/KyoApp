package de.scangolf.pc;

import de.scangolf.core.render.Canvas;
import de.scangolf.core.util.ArgbImage;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Canvas-Adapter auf Graphics2D. Logische Einheiten werden per Transformation auf
 * Gerätepixel skaliert; Bilder werden einmal in BufferedImages umgewandelt und gecacht.
 */
public final class Graphics2DCanvas implements Canvas {

    private static final Map<ArgbImage, BufferedImage> IMAGE_CACHE = new WeakHashMap<>();

    private final Graphics2D g;
    private final double width;
    private final double height;
    private final double baseScale;
    private final Deque<Object[]> stack = new ArrayDeque<>();
    private double curScale;

    /**
     * @param g         Ziel
     * @param width     logische Breite
     * @param height    logische Höhe
     * @param scale     Gerätepixel je logischer Einheit (bereits in g gesetzt oder nicht, siehe applyScale)
     * @param applyScale true: Skalierung hier auf g anwenden
     */
    public Graphics2DCanvas(Graphics2D g, double width, double height, double scale, boolean applyScale) {
        this.g = g;
        this.width = width;
        this.height = height;
        this.baseScale = scale;
        this.curScale = scale;
        if (applyScale) {
            g.scale(scale, scale);
        }
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
    }

    public double width() {
        return width;
    }

    public double height() {
        return height;
    }

    public double deviceScale() {
        return curScale;
    }

    private void color(int argb) {
        g.setColor(new Color(argb, true));
    }

    private void fill(Shape s, int argb) {
        color(argb);
        g.fill(s);
    }

    private void stroke(Shape s, double lw, int argb) {
        color(argb);
        g.setStroke(new BasicStroke((float) lw, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(s);
    }

    public void fillRect(double x, double y, double w, double h, int argb) {
        fill(new Rectangle2D.Double(x, y, w, h), argb);
    }

    public void fillRoundRect(double x, double y, double w, double h, double r, int argb) {
        fill(new RoundRectangle2D.Double(x, y, w, h, 2 * r, 2 * r), argb);
    }

    public void strokeRoundRect(double x, double y, double w, double h, double r, double lw, int argb) {
        stroke(new RoundRectangle2D.Double(x, y, w, h, 2 * r, 2 * r), lw, argb);
    }

    public void fillCircle(double cx, double cy, double r, int argb) {
        fill(new Ellipse2D.Double(cx - r, cy - r, 2 * r, 2 * r), argb);
    }

    public void strokeCircle(double cx, double cy, double r, double lw, int argb) {
        stroke(new Ellipse2D.Double(cx - r, cy - r, 2 * r, 2 * r), lw, argb);
    }

    public void fillEllipse(double cx, double cy, double rx, double ry, int argb) {
        fill(new Ellipse2D.Double(cx - rx, cy - ry, 2 * rx, 2 * ry), argb);
    }

    public void line(double x1, double y1, double x2, double y2, double lw, int argb) {
        stroke(new Line2D.Double(x1, y1, x2, y2), lw, argb);
    }

    public void polyline(double[] xs, double[] ys, int n, double lw, int argb) {
        if (n < 2) {
            return;
        }
        Path2D.Double p = new Path2D.Double();
        p.moveTo(xs[0], ys[0]);
        for (int i = 1; i < n; i++) {
            p.lineTo(xs[i], ys[i]);
        }
        stroke(p, lw, argb);
    }

    public void fillPolygon(double[] xs, double[] ys, int n, int argb) {
        if (n < 3) {
            return;
        }
        Path2D.Double p = new Path2D.Double();
        p.moveTo(xs[0], ys[0]);
        for (int i = 1; i < n; i++) {
            p.lineTo(xs[i], ys[i]);
        }
        p.closePath();
        fill(p, argb);
    }

    private Font font(double size, int style) {
        String family = (style & FONT_SERIF) != 0 ? Font.SERIF : Font.SANS_SERIF;
        int s = Font.PLAIN;
        if ((style & FONT_BOLD) != 0) {
            s |= Font.BOLD;
        }
        if ((style & FONT_ITALIC) != 0) {
            s |= Font.ITALIC;
        }
        // Schrift in großer Punktgröße anlegen und per Transformation verkleinern
        // (Graphics2D rundet kleine Punktgrößen sonst grob).
        Font f = new Font(family, s, 100);
        return f.deriveFont(AffineTransform.getScaleInstance(size / 100.0, size / 100.0));
    }

    public void text(String s, double x, double y, double size, int style, int align, int argb) {
        Font f = font(size, style);
        g.setFont(f);
        double w = textWidth(s, size, style);
        double dx = align == ALIGN_CENTER ? -w / 2 : align == ALIGN_RIGHT ? -w : 0;
        color(argb);
        g.drawString(s, (float) (x + dx), (float) y);
    }

    public double textWidth(String s, double size, int style) {
        Font f = new Font((style & FONT_SERIF) != 0 ? Font.SERIF : Font.SANS_SERIF,
                ((style & FONT_BOLD) != 0 ? Font.BOLD : 0) | ((style & FONT_ITALIC) != 0 ? Font.ITALIC : 0), 100);
        FontMetrics fm = g.getFontMetrics(f);
        return fm.getStringBounds(s, g).getWidth() * size / 100.0;
    }

    public void image(ArgbImage img, double x, double y, double w, double h) {
        BufferedImage b;
        synchronized (IMAGE_CACHE) {
            b = IMAGE_CACHE.get(img);
            if (b == null) {
                b = Images.toBuffered(img);
                IMAGE_CACHE.put(img, b);
            }
        }
        AffineTransform t = new AffineTransform();
        t.translate(x, y);
        t.scale(w / img.width(), h / img.height());
        g.setComposite(AlphaComposite.SrcOver);
        g.drawImage(b, t, null);
    }

    public void save() {
        stack.push(new Object[] {g.getTransform(), g.getClip(), curScale});
    }

    public void restore() {
        Object[] s = stack.pop();
        g.setTransform((AffineTransform) s[0]);
        g.setClip((Shape) s[1]);
        curScale = (Double) s[2];
    }

    public void translate(double dx, double dy) {
        g.translate(dx, dy);
    }

    public void scale(double sx, double sy) {
        g.scale(sx, sy);
        curScale *= Math.sqrt(Math.abs(sx * sy));
    }

    public void clipRect(double x, double y, double w, double h) {
        g.clip(new Rectangle2D.Double(x, y, w, h));
    }

    public double baseScale() {
        return baseScale;
    }
}
