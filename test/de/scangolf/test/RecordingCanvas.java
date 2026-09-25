package de.scangolf.test;

import de.scangolf.core.render.Canvas;
import de.scangolf.core.util.ArgbImage;

import java.util.ArrayList;
import java.util.List;

/**
 * Canvas für Tests: zeichnet optional an ein anderes Canvas weiter und protokolliert jede
 * Operation mit ihrem Begrenzungsrechteck (in logischen Koordinaten, ohne Transformationen).
 */
final class RecordingCanvas implements Canvas {

    static final class Op {
        final String kind;
        final double x0;
        final double y0;
        final double x1;
        final double y1;
        final String text;

        Op(String kind, double x0, double y0, double x1, double y1, String text) {
            this.kind = kind;
            this.x0 = x0;
            this.y0 = y0;
            this.x1 = x1;
            this.y1 = y1;
            this.text = text;
        }

        @Override
        public String toString() {
            return kind + (text != null ? " '" + text + "'" : "") + String.format(" [%.1f,%.1f – %.1f,%.1f]", x0, y0, x1, y1);
        }
    }

    final List<Op> ops = new ArrayList<>();
    private final Canvas delegate;
    private final double w;
    private final double h;
    private int transformDepth;
    boolean nonFinite;

    RecordingCanvas(double w, double h, Canvas delegate) {
        this.w = w;
        this.h = h;
        this.delegate = delegate;
    }

    private void rec(String kind, double x0, double y0, double x1, double y1, String text) {
        for (double v : new double[] {x0, y0, x1, y1}) {
            if (Double.isNaN(v) || Double.isInfinite(v)) {
                nonFinite = true;
            }
        }
        if (transformDepth == 0) {
            ops.add(new Op(kind, Math.min(x0, x1), Math.min(y0, y1), Math.max(x0, x1), Math.max(y0, y1), text));
        }
    }

    @Override
    public double width() {
        return w;
    }

    @Override
    public double height() {
        return h;
    }

    @Override
    public double deviceScale() {
        return delegate != null ? delegate.deviceScale() : 1.0;
    }

    @Override
    public void fillRect(double x, double y, double ww, double hh, int argb) {
        rec("rect", x, y, x + ww, y + hh, null);
        if (delegate != null) {
            delegate.fillRect(x, y, ww, hh, argb);
        }
    }

    @Override
    public void fillRoundRect(double x, double y, double ww, double hh, double r, int argb) {
        rec("roundrect", x, y, x + ww, y + hh, null);
        if (delegate != null) {
            delegate.fillRoundRect(x, y, ww, hh, r, argb);
        }
    }

    @Override
    public void strokeRoundRect(double x, double y, double ww, double hh, double r, double lw, int argb) {
        rec("strokeroundrect", x, y, x + ww, y + hh, null);
        if (delegate != null) {
            delegate.strokeRoundRect(x, y, ww, hh, r, lw, argb);
        }
    }

    @Override
    public void fillCircle(double cx, double cy, double r, int argb) {
        rec("circle", cx - r, cy - r, cx + r, cy + r, null);
        if (delegate != null) {
            delegate.fillCircle(cx, cy, r, argb);
        }
    }

    @Override
    public void strokeCircle(double cx, double cy, double r, double lw, int argb) {
        rec("strokecircle", cx - r, cy - r, cx + r, cy + r, null);
        if (delegate != null) {
            delegate.strokeCircle(cx, cy, r, lw, argb);
        }
    }

    @Override
    public void fillEllipse(double cx, double cy, double rx, double ry, int argb) {
        rec("ellipse", cx - rx, cy - ry, cx + rx, cy + ry, null);
        if (delegate != null) {
            delegate.fillEllipse(cx, cy, rx, ry, argb);
        }
    }

    @Override
    public void line(double x1, double y1, double x2, double y2, double lw, int argb) {
        rec("line", x1, y1, x2, y2, null);
        if (delegate != null) {
            delegate.line(x1, y1, x2, y2, lw, argb);
        }
    }

    @Override
    public void polyline(double[] xs, double[] ys, int n, double lw, int argb) {
        for (int i = 0; i < n; i++) {
            rec("polyline", xs[i], ys[i], xs[i], ys[i], null);
        }
        if (delegate != null) {
            delegate.polyline(xs, ys, n, lw, argb);
        }
    }

    @Override
    public void fillPolygon(double[] xs, double[] ys, int n, int argb) {
        for (int i = 0; i < n; i++) {
            rec("polygon", xs[i], ys[i], xs[i], ys[i], null);
        }
        if (delegate != null) {
            delegate.fillPolygon(xs, ys, n, argb);
        }
    }

    @Override
    public void text(String s, double x, double y, double size, int font, int align, int argb) {
        double tw = textWidth(s, size, font);
        double x0 = align == ALIGN_CENTER ? x - tw / 2 : align == ALIGN_RIGHT ? x - tw : x;
        rec("text", x0, y - size * 0.8, x0 + tw, y + size * 0.2, s);
        if (delegate != null) {
            delegate.text(s, x, y, size, font, align, argb);
        }
    }

    @Override
    public double textWidth(String s, double size, int font) {
        return delegate != null ? delegate.textWidth(s, size, font) : s.length() * size * 0.6;
    }

    @Override
    public void image(ArgbImage img, double x, double y, double ww, double hh) {
        rec("image", x, y, x + ww, y + hh, null);
        if (delegate != null) {
            delegate.image(img, x, y, ww, hh);
        }
    }

    @Override
    public void save() {
        if (delegate != null) {
            delegate.save();
        }
    }

    @Override
    public void restore() {
        if (delegate != null) {
            delegate.restore();
        }
        transformDepth = Math.max(0, transformDepth - 1);
    }

    @Override
    public void translate(double dx, double dy) {
        transformDepth++;
        if (delegate != null) {
            delegate.translate(dx, dy);
        }
    }

    @Override
    public void scale(double sx, double sy) {
        transformDepth++;
        if (delegate != null) {
            delegate.scale(sx, sy);
        }
    }

    @Override
    public void clipRect(double x, double y, double ww, double hh) {
        if (delegate != null) {
            delegate.clipRect(x, y, ww, hh);
        }
    }

    /** Operationen, die aus dem Bereich hinausragen (mit Toleranz). */
    List<Op> outside(String kind, double tol) {
        List<Op> out = new ArrayList<>();
        for (Op o : ops) {
            if ((kind == null || o.kind.equals(kind))
                    && (o.x0 < -tol || o.y0 < -tol || o.x1 > w + tol || o.y1 > h + tol)) {
                out.add(o);
            }
        }
        return out;
    }
}
