package de.scangolf.core.util;

/**
 * Unveränderliches Rasterbild: Breite, Höhe und Pixel als ARGB-int (zeilenweise).
 *
 * Das Pixel-Array wird nie nach außen gegeben; {@link #copyPixelsTo} kopiert es
 * z. B. für die Umwandlung in ein Plattformbild.
 */
public final class ArgbImage {

    private final int width;
    private final int height;
    private final int[] pixels;

    private ArgbImage(int width, int height, int[] pixels) {
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("Bildgröße ungültig: " + width + "x" + height);
        }
        if (pixels.length != width * height) {
            throw new IllegalArgumentException("Pixelanzahl passt nicht zur Größe");
        }
        this.width = width;
        this.height = height;
        this.pixels = pixels;
    }

    /** Übernimmt das Array ohne Kopie. Der Aufrufer darf es danach nicht mehr ändern. */
    public static ArgbImage adopt(int width, int height, int[] pixels) {
        return new ArgbImage(width, height, pixels);
    }

    /** Legt eine Kopie des Arrays an. */
    public static ArgbImage copyOf(int width, int height, int[] pixels) {
        int[] c = new int[pixels.length];
        System.arraycopy(pixels, 0, c, 0, pixels.length);
        return new ArgbImage(width, height, c);
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    public int pixel(int x, int y) {
        return pixels[y * width + x];
    }

    public void copyPixelsTo(int[] dst, int offset) {
        System.arraycopy(pixels, 0, dst, offset, pixels.length);
    }

    /** Anzahl Pixel mit Alpha &gt;= minAlpha. */
    public int countOpaque(int minAlpha) {
        int n = 0;
        for (int i = 0; i < pixels.length; i++) {
            if ((pixels[i] >>> 24) >= minAlpha) {
                n++;
            }
        }
        return n;
    }

    public boolean sameContent(ArgbImage o) {
        if (o == null || o.width != width || o.height != height) {
            return false;
        }
        for (int i = 0; i < pixels.length; i++) {
            if (pixels[i] != o.pixels[i]) {
                return false;
            }
        }
        return true;
    }

    public boolean equals(Object o) {
        return o instanceof ArgbImage && sameContent((ArgbImage) o);
    }

    public int hashCode() {
        int h = width * 31 + height;
        for (int i = 0; i < pixels.length; i += 97) {
            h = h * 31 + pixels[i];
        }
        return h;
    }
}
