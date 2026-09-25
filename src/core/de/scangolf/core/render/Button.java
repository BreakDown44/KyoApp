package de.scangolf.core.render;

/** Große Touch-Schaltfläche mit Text. */
public final class Button {

    public final String label;
    public final boolean primary;
    double x;
    double y;
    double w;
    double h;

    public Button(String label, boolean primary) {
        this.label = label;
        this.primary = primary;
    }

    void layout(double bx, double by, double bw, double bh) {
        this.x = bx;
        this.y = by;
        this.w = bw;
        this.h = bh;
    }

    /** Trefferprüfung mit etwas Toleranz rundherum (Finger sind ungenau). */
    public boolean hit(double px, double py) {
        double tol = 8;
        return px >= x - tol && px <= x + w + tol && py >= y - tol && py <= y + h + tol;
    }

    public double centerX() {
        return x + w / 2;
    }

    public double centerY() {
        return y + h / 2;
    }

    void draw(Canvas c, boolean pressed) {
        double off = pressed ? 2 : 0;
        c.fillRoundRect(x + 2, y + 5, w, h, h / 2, 0x40000000);
        if (primary) {
            c.fillRoundRect(x + off, y + off, w, h, h / 2, pressed ? 0xFF1E6B31 : 0xFF2E8B45);
            c.fillRoundRect(x + off + 3, y + off + 3, w - 6, h * 0.45, h / 2 - 3, 0x22FFFFFF);
        } else {
            c.fillRoundRect(x + off, y + off, w, h, h / 2, pressed ? 0xFFE6E0CC : 0xFFFFFBF0);
            c.strokeRoundRect(x + off, y + off, w, h, h / 2, 3, 0xFF2E8B45);
        }
        int fg = primary ? 0xFFFFFFFF : 0xFF1F5F30;
        double size = Math.min(h * 0.4, 28);
        while (size > 10 && c.textWidth(label, size, Canvas.FONT_BOLD) > w - 24) {
            size -= 1;
        }
        c.text(label, x + off + w / 2, y + off + h / 2 + size * 0.36, size, Canvas.FONT_BOLD, Canvas.ALIGN_CENTER, fg);
    }
}
