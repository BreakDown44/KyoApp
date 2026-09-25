package de.scangolf.core.render;

import de.scangolf.core.level.CellType;
import de.scangolf.core.level.Level;
import de.scangolf.core.util.ArgbImage;

/**
 * Rendert die statische Bahn (Rasen, Wände, Wasser) einmalig in ein ARGB-Bild.
 * Kanten werden aus dem 1-mm-Raster bilinear geglättet (keine Treppen) und 2x2-fach
 * überabgetastet. Reine Pixelrechnung, keine Plattform-API.
 */
public final class LevelRasterizer {

    private final Level level;
    private final int cols;
    private final int rows;
    private final double cell;
    private final float[] wall;
    private final float[] water;
    private final float[] depth;

    public LevelRasterizer(Level level) {
        this.level = level;
        this.cols = level.cols();
        this.rows = level.rows();
        this.cell = level.cellMm();
        wall = new float[cols * rows];
        water = new float[cols * rows];
        for (int y = 0; y < rows; y++) {
            for (int x = 0; x < cols; x++) {
                byte t = level.cell(x, y);
                wall[y * cols + x] = t == CellType.WALL ? 1f : 0f;
                water[y * cols + x] = t == CellType.WATER ? 1f : 0f;
            }
        }
        depth = boxBlur(water, cols, rows, 3);
        // Wandkanten für die Darstellung glätten: halb Zelle, halb 3x3-Mittel. Die sichtbare
        // Kante weicht höchstens ~0,5 mm von der Kollisionskante (Zellquadrate) ab.
        float[] blur = boxBlur(wall, cols, rows, 1);
        float[] wblur = boxBlur(water, cols, rows, 1);
        for (int i = 0; i < wall.length; i++) {
            wall[i] = 0.5f * wall[i] + 0.5f * blur[i];
            water[i] = 0.35f * water[i] + 0.65f * wblur[i];
        }
    }

    private static float[] boxBlur(float[] src, int w, int h, int r) {
        float[] tmp = new float[src.length];
        float[] out = new float[src.length];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                float s = 0;
                int n = 0;
                for (int k = x - r; k <= x + r; k++) {
                    if (k >= 0 && k < w) {
                        s += src[y * w + k];
                        n++;
                    }
                }
                tmp[y * w + x] = s / n;
            }
        }
        for (int x = 0; x < w; x++) {
            for (int y = 0; y < h; y++) {
                float s = 0;
                int n = 0;
                for (int k = y - r; k <= y + r; k++) {
                    if (k >= 0 && k < h) {
                        s += tmp[k * w + x];
                        n++;
                    }
                }
                out[y * w + x] = s / n;
            }
        }
        return out;
    }

    /** Bilineare Interpolation eines Zellwerts (Werte liegen in den Zellmitten), außerhalb 0. */
    private float sample(float[] m, double u, double v) {
        double gx = u / cell - 0.5;
        double gy = v / cell - 0.5;
        int x0 = (int) Math.floor(gx);
        int y0 = (int) Math.floor(gy);
        double fx = gx - x0;
        double fy = gy - y0;
        double a = at(m, x0, y0);
        double b = at(m, x0 + 1, y0);
        double c = at(m, x0, y0 + 1);
        double d = at(m, x0 + 1, y0 + 1);
        return (float) ((a * (1 - fx) + b * fx) * (1 - fy) + (c * (1 - fx) + d * fx) * fy);
    }

    private float at(float[] m, int x, int y) {
        if (x < 0 || y < 0 || x >= cols || y >= rows) {
            return 0f;
        }
        return m[y * cols + x];
    }

    /** Pseudozufall je Pixel (deterministisch). */
    private static int hash(int x, int y) {
        int h = x * 73856093 ^ y * 19349663;
        h ^= h >>> 13;
        h *= 0x5bd1e995;
        h ^= h >>> 15;
        return h;
    }

    /** Farbe an einer Stelle (mm) ohne Kantenglättung. */
    private int colorAt(double u, double v, int px, int py) {
        float w = sample(wall, u, v);
        if (w >= 0.5f) {
            // Wand mit leichter Fase: oben links heller, unten rechts dunkler
            float tl = sample(wall, u - 0.55, v - 0.55);
            float br = sample(wall, u + 0.55, v + 0.55);
            int c = Colors.WALL;
            if (tl < 0.5f) {
                c = Colors.WALL_TOP;
            } else if (br < 0.5f) {
                c = Colors.shade(Colors.WALL, 0.75);
            }
            return c;
        }
        float wt = sample(water, u, v);
        if (wt >= 0.5f) {
            float d = sample(depth, u, v);
            double t = Math.min(1, Math.max(0, (d - 0.35) / 0.55));
            int c = Colors.mix(Colors.WATER_LIGHT, Colors.WATER, t);
            double ripple = Math.sin(u * 0.9 + Math.sin(v * 0.35) * 2.5 + v * 0.15);
            if (ripple > 0.92) {
                c = Colors.mix(c, Colors.WHITE, 0.35 * (ripple - 0.92) / 0.08);
            }
            if (wt < 0.62f) {
                c = Colors.mix(c, Colors.WHITE, 0.45);   // Uferlinie
            }
            return c;
        }
        // Rasen mit Mähstreifen und feinem Rauschen
        boolean stripe = (((int) Math.floor((u + v * 0.55) / 14.0)) & 1) == 0;
        double f = stripe ? 1.0 : 0.92;
        f += ((hash(px, py) & 255) - 128) / 128.0 * 0.025;
        // Schlagschatten der Wände nach unten rechts
        float sh = sample(wall, u - 1.1, v - 1.4);
        if (sh >= 0.5f) {
            f *= 0.70;
        } else if (sh > 0.2f) {
            f *= 0.70 + 0.30 * (0.5 - sh) / 0.3;
        }
        // Wasserrand wirft leichten Schatten (Böschung)
        float ws = sample(water, u - 0.7, v - 0.9);
        if (ws >= 0.5f) {
            f *= 0.85;
        }
        return Colors.shade(Colors.TURF, f);
    }

    /** Rendert die Bahn mit pxPerMm Pixeln je Millimeter. */
    public ArgbImage render(double pxPerMm) {
        int w = Math.max(1, (int) Math.round(level.widthMm() * pxPerMm));
        int h = Math.max(1, (int) Math.round(level.heightMm() * pxPerMm));
        int[] out = new int[w * h];
        double sx = level.widthMm() / w;
        double sy = level.heightMm() / h;
        for (int py = 0; py < h; py++) {
            for (int px = 0; px < w; px++) {
                double u = (px + 0.25) * sx;
                double v = (py + 0.25) * sy;
                int c0 = colorAt(u, v, px, py);
                int c1 = colorAt(u + 0.5 * sx, v, px, py);
                int c2 = colorAt(u, v + 0.5 * sy, px, py);
                int c3 = colorAt(u + 0.5 * sx, v + 0.5 * sy, px, py);
                int r = (Colors.red(c0) + Colors.red(c1) + Colors.red(c2) + Colors.red(c3) + 2) >> 2;
                int g = (Colors.green(c0) + Colors.green(c1) + Colors.green(c2) + Colors.green(c3) + 2) >> 2;
                int b = (Colors.blue(c0) + Colors.blue(c1) + Colors.blue(c2) + Colors.blue(c3) + 2) >> 2;
                out[py * w + px] = 0xFF000000 | (r << 16) | (g << 8) | b;
            }
        }
        return ArgbImage.adopt(w, h, out);
    }
}
