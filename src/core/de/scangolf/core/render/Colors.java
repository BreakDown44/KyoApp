package de.scangolf.core.render;

/** Farbhilfen und die Farbpalette von ScanGolf (ARGB). */
public final class Colors {

    public static final int WHITE = 0xFFFFFFFF;
    public static final int BLACK = 0xFF000000;

    // Spiel
    public static final int TURF = 0xFF3E9E4A;
    public static final int TURF_DARK = 0xFF2F7F3A;
    public static final int FRAME = 0xFF1C3A26;
    public static final int WALL = 0xFF2E3138;
    public static final int WALL_TOP = 0xFF4A4F59;
    public static final int WATER = 0xFF2F86D8;
    public static final int WATER_LIGHT = 0xFF8CCBF4;
    public static final int HOLE = 0xFF10150F;
    public static final int FLAG = 0xFFE8412F;
    public static final int GOLD = 0xFFF2C14E;
    public static final int HUD_BG = 0xE0132019;
    public static final int TEXT = 0xFFF7F4EA;

    private Colors() {
    }

    public static int argb(int a, int r, int g, int b) {
        return (clamp(a) << 24) | (clamp(r) << 16) | (clamp(g) << 8) | clamp(b);
    }

    public static int withAlpha(int argb, int a) {
        return (clamp(a) << 24) | (argb & 0xFFFFFF);
    }

    public static int alpha(int argb) {
        return argb >>> 24;
    }

    public static int red(int argb) {
        return (argb >> 16) & 0xFF;
    }

    public static int green(int argb) {
        return (argb >> 8) & 0xFF;
    }

    public static int blue(int argb) {
        return argb & 0xFF;
    }

    /** Lineare Mischung zweier Farben (t = 0: a, t = 1: b), inklusive Alpha. */
    public static int mix(int a, int b, double t) {
        if (t <= 0) {
            return a;
        }
        if (t >= 1) {
            return b;
        }
        return argb((int) (alpha(a) + (alpha(b) - alpha(a)) * t + 0.5),
                (int) (red(a) + (red(b) - red(a)) * t + 0.5),
                (int) (green(a) + (green(b) - green(a)) * t + 0.5),
                (int) (blue(a) + (blue(b) - blue(a)) * t + 0.5));
    }

    /** Helligkeit ändern: f &gt; 1 heller, f &lt; 1 dunkler (Alpha bleibt). */
    public static int shade(int argb, double f) {
        return argb(alpha(argb), (int) (red(argb) * f), (int) (green(argb) * f), (int) (blue(argb) * f));
    }

    /** Farbe b mit ihrem Alpha über die deckende Farbe a legen. */
    public static int over(int a, int b) {
        int ab = alpha(b);
        if (ab == 255) {
            return b;
        }
        if (ab == 0) {
            return a;
        }
        double t = ab / 255.0;
        return argb(255, (int) (red(a) + (red(b) - red(a)) * t + 0.5),
                (int) (green(a) + (green(b) - green(a)) * t + 0.5),
                (int) (blue(a) + (blue(b) - blue(a)) * t + 0.5));
    }

    static int clamp(int v) {
        return v < 0 ? 0 : (v > 255 ? 255 : v);
    }
}
