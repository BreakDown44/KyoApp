package de.scangolf.pc;

import de.scangolf.core.render.Canvas;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

/** Rendert über das Canvas-Interface in ein BufferedImage (headless-fähig). */
public final class Offscreen {

    /** Zeichenauftrag. */
    public interface Painter {
        void paint(Canvas c);
    }

    private Offscreen() {
    }

    /**
     * @param logicalW logische Breite
     * @param logicalH logische Höhe
     * @param scale    Gerätepixel je logischer Einheit
     */
    public static BufferedImage render(double logicalW, double logicalH, double scale, Painter p) {
        int w = (int) Math.round(logicalW * scale);
        int h = (int) Math.round(logicalH * scale);
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        try {
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, w, h);
            p.paint(new Graphics2DCanvas(g, logicalW, logicalH, scale, true));
        } finally {
            g.dispose();
        }
        return img;
    }
}
