package de.scangolf.pc;

import de.scangolf.core.util.ArgbImage;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import javax.imageio.ImageIO;

/** Bilder laden/speichern (nur PC). */
public final class Images {

    /** Rohbild als ARGB-Array. */
    public static final class Raw {
        public final int[] argb;
        public final int width;
        public final int height;

        Raw(int[] argb, int width, int height) {
            this.argb = argb;
            this.width = width;
            this.height = height;
        }
    }

    private Images() {
    }

    public static Raw load(File f) throws IOException {
        BufferedImage img = ImageIO.read(f);
        if (img == null) {
            throw new IOException("Kein lesbares Bild: " + f);
        }
        int w = img.getWidth();
        int h = img.getHeight();
        int[] px = new int[w * h];
        // zeilenweise, damit keine zweite Vollbild-Kopie entsteht
        for (int y = 0; y < h; y++) {
            img.getRGB(0, y, w, 1, px, y * w, w);
        }
        return new Raw(px, w, h);
    }

    public static BufferedImage toBuffered(ArgbImage a) {
        BufferedImage b = new BufferedImage(a.width(), a.height(), BufferedImage.TYPE_INT_ARGB);
        int[] px = new int[a.width() * a.height()];
        a.copyPixelsTo(px, 0);
        b.setRGB(0, 0, a.width(), a.height(), px, 0, a.width());
        return b;
    }

    public static void savePng(BufferedImage img, File f) throws IOException {
        File dir = f.getAbsoluteFile().getParentFile();
        if (dir != null && !dir.isDirectory() && !dir.mkdirs()) {
            throw new IOException("Ordner nicht anlegbar: " + dir);
        }
        if (!ImageIO.write(img, "png", f)) {
            throw new IOException("PNG-Export nicht möglich");
        }
    }

    /** PNG mit dpi-Angabe (pHYs), damit Druck/Viewer die Größe kennen. */
    public static void savePng(BufferedImage img, File f, double dpi) throws IOException {
        savePng(img, f);
        PngDpi.set(f, dpi);
    }
}
